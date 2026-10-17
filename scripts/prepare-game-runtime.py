#!/usr/bin/env python3
"""Prepare a hash-verified development runtime without touching player profiles.

Usage: python3 scripts/prepare-game-runtime.py 26.3 [--neoforge]
Requires the official metadata and client at .cache/VERSION.json and
.cache/client-VERSION.jar. Downloads are isolated under .cache/runtime-VERSION.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NEOFORGE = {
    '26.1': ('26.1.0.19-beta', '7d9f928b8d34fcbb1ea7bad30f6c4849bab4aef7'),
    '26.1.2': ('26.1.2.116', '3ba299fd382b6257a6a84f291077518844046bde'),
    '26.3': ('26.3.0.86', '059fef6105e0cd57dd0fcd2bac07a38c2267b18d'),
}
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def sha1(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha1').hexdigest()


def fetch(url):
    return OPENER.open(urllib.request.Request(url, headers={'User-Agent': 'Unbox-development-runtime'}), timeout=120).read()


def verified(target, url, expected, cached=None):
    if target.is_file() and sha1(target) == expected:
        return 'existing'
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + '.download')
    if cached and cached.is_file() and sha1(cached) == expected:
        shutil.copy2(cached, temporary)
        source = 'reused'
    else:
        data = fetch(url)
        if hashlib.sha1(data).hexdigest() != expected:
            raise ValueError(f'SHA-1 mismatch: {url}')
        temporary.write_bytes(data)
        source = 'downloaded'
    temporary.replace(target)
    return source


def allowed(library):
    current_os = {'Darwin': 'osx', 'Windows': 'windows', 'Linux': 'linux'}[platform.system()]
    result = 'rules' not in library
    for rule in library.get('rules', []):
        os_rule = rule.get('os', {})
        if 'features' in rule or os_rule.get('name', current_os) != current_os:
            continue
        if 'arch' in os_rule and not re.fullmatch(os_rule['arch'], platform.machine()):
            continue
        if 'version' in os_rule and not re.search(os_rule['version'], platform.release()):
            continue
        result = rule['action'] == 'allow'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=NEOFORGE)
    parser.add_argument('--neoforge', action='store_true')
    parser.add_argument('--reuse-runtime', type=Path, default=Path.home() / 'Library/Application Support/dev.unbox.client/runtime')
    args = parser.parse_args()
    version = args.version
    runtime = ROOT / f'.cache/runtime-{version}'
    runtime.mkdir(parents=True, exist_ok=True)
    meta_path = ROOT / f'.cache/{version}.json'
    meta = json.loads(meta_path.read_text())
    if meta['id'] != version:
        raise ValueError('Unexpected Minecraft metadata version')
    client = ROOT / f'.cache/client-{version}.jar'
    if sha1(client) != meta['downloads']['client']['sha1']:
        raise ValueError('Minecraft client SHA-1 mismatch')
    shutil.copy2(meta_path, runtime / f'{version}.json')
    jobs = []
    for lib in meta['libraries']:
        artifact = lib.get('downloads', {}).get('artifact')
        if allowed(lib) and artifact:
            rel = Path('libraries') / artifact['path']
            jobs.append((runtime / rel, artifact['url'], artifact['sha1'], args.reuse_runtime / rel))
    fabric_path = runtime / 'fabric-0.19.5.json'
    if not fabric_path.exists():
        fabric_path.write_bytes(fetch(f'https://meta.fabricmc.net/v2/versions/loader/{version}/0.19.5/profile/json'))
    fabric = json.loads(fabric_path.read_text())
    if fabric['inheritsFrom'] != version:
        raise ValueError('Unexpected Fabric metadata version')
    for lib in fabric['libraries']:
        group, artifact, release = lib['name'].split(':')
        rel = f'{group.replace(".", "/")}/{artifact}/{release}/{artifact}-{release}.jar'
        url = lib['url'].rstrip('/') + '/' + rel
        digest = lib.get('sha1') or fetch(url + '.sha1').decode().strip().split()[0]
        jobs.append((runtime / 'libraries' / rel, url, digest, args.reuse_runtime / 'libraries' / rel))
    index = meta['assetIndex']
    index_rel = Path('assets/indexes') / f'{index["id"]}.json'
    verified(runtime / index_rel, index['url'], index['sha1'], args.reuse_runtime / index_rel)
    assets = json.loads((runtime / index_rel).read_text())['objects']
    for digest in sorted({obj['hash'] for obj in assets.values()}):
        rel = Path('assets/objects') / digest[:2] / digest
        jobs.append((runtime / rel, f'https://resources.download.minecraft.net/{digest[:2]}/{digest}', digest, args.reuse_runtime / rel))
    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(pool.map(lambda job: verified(*job), jobs))
    report = {'minecraft': version, 'clientSha1': sha1(client), 'runtime': str(runtime), 'assetIndex': index['id'], 'assetCount': len(assets), 'verifiedFiles': len(jobs), 'files': {key: results.count(key) for key in ('existing', 'reused', 'downloaded')}}
    print(json.dumps(report), flush=True)
    if args.neoforge:
        neo_version, installer_sha1 = NEOFORGE[version]
        neo_root = ROOT / ('.cache/neoforge/runtime' if version == '26.1' else f'.cache/neoforge-{version}/runtime')
        vanilla = neo_root / 'versions' / version
        vanilla.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client, vanilla / f'{version}.jar')
        shutil.copy2(meta_path, vanilla / f'{version}.json')
        (neo_root / 'launcher_profiles.json').write_text('{"profiles":{}}')
        installer = neo_root / 'installer.jar'
        verified(installer, f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo_version}/neoforge-{neo_version}-installer.jar', installer_sha1)
        with zipfile.ZipFile(installer) as archive:
            install_meta = json.loads(archive.read('install_profile.json'))
            neo = json.loads(archive.read('version.json'))
            # The official installer verifies and processes its embedded artifacts.
            for lib in install_meta['libraries'] + neo['libraries']:
                artifact = lib['downloads']['artifact']
                if artifact.get('url'):
                    verified(neo_root / 'libraries' / artifact['path'], artifact['url'], artifact['sha1'])
        java_home = os.environ.get('JAVA_HOME')
        if not java_home and platform.system() == 'Darwin':
            java_home = subprocess.check_output(['/usr/libexec/java_home', '-v', '25'], text=True).strip()
        java = str(Path(java_home) / 'bin/java') if java_home else 'java'
        with (neo_root / 'install.log').open('w') as log:
            subprocess.run([java, '-jar', str(installer), '--installClient', str(neo_root)], cwd=neo_root, stdout=log, stderr=log, timeout=600, check=True)
        output = neo_root / f'versions/neoforge-{neo_version}/neoforge-{neo_version}.json'
        installed = json.loads(output.read_text())
        if installed['inheritsFrom'] != version or installed['id'] != f'neoforge-{neo_version}':
            raise ValueError('Unexpected installed NeoForge metadata')
        for lib in installed['libraries']:
            artifact = lib['downloads']['artifact']
            path = neo_root / 'libraries' / artifact['path']
            if not path.is_file() or (artifact.get('sha1') and sha1(path) != artifact['sha1']):
                raise ValueError(f'NeoForge library verification failed: {path}')
        patched = neo_root / f'libraries/net/neoforged/minecraft-client-patched/{neo_version}/minecraft-client-patched-{neo_version}.jar'
        report['neoforge'] = {'version': neo_version, 'installerSha1': installer_sha1, 'patchedSha1': sha1(patched), 'libraries': len(installed['libraries']), 'runtime': str(neo_root)}
    (runtime / 'preparation-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
