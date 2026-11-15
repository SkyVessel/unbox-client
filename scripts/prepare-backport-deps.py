#!/usr/bin/env python3
"""Verify pinned backport dependencies and expose nested compile libraries."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def prepare(version, loader='fabric'):
    target = json.loads((ROOT / 'client-mod/port-targets.json').read_text())[version]
    cache = ROOT / '.cache' / version
    if loader != 'fabric':
        cache = cache / loader
    nested = cache / 'compile-libs'
    nested.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def official_file(path, url, digest):
        data = path.read_bytes() if path.exists() else opener.open(urllib.request.Request(url, headers={'User-Agent':'Unbox-backport-build'}), timeout=120).read()
        if hashlib.sha1(data).hexdigest() != digest:
            raise ValueError('Official Minecraft checksum mismatch: ' + str(path))
        if not path.exists():
            path.write_bytes(data)
        return data
    metadata = json.loads(official_file(ROOT / '.cache' / (version + '.json'), target['metadata']['url'], target['metadata']['sha1']))
    if metadata['id'] != version or metadata['javaVersion']['majorVersion'] != target['java']:
        raise ValueError('Minecraft/Java target mismatch')
    client = metadata['downloads']['client']
    official_file(ROOT / '.cache' / ('client-' + version + '.jar'), client['url'], client['sha1'])
    for item in target['dependencies' if loader == 'fabric' else loader + 'Dependencies']:
        path = cache / item['filename']
        if path.name != item['filename']:
            raise ValueError('Invalid dependency filename')
        if path.exists():
            data = path.read_bytes()
        else:
            request = urllib.request.Request(item['url'], headers={'User-Agent': 'Unbox-backport-build'})
            with opener.open(request, timeout=120) as response:
                data = response.read()
        if any(hashlib.new(algorithm, data).hexdigest() != item[algorithm] for algorithm in ('sha1', 'sha512')):
            raise ValueError('Dependency checksum mismatch: ' + item['slug'])
        if not path.exists():
            path.write_bytes(data)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for name in archive.namelist():
                if item['slug'] == 'e4mc' and name.startswith('META-INF/jars/') and name.endswith('.jar'):
                    (nested / Path(name).name).write_bytes(archive.read(name))
                if item['slug'] == 'fabric-api' and name.startswith('META-INF/jars/') and name.endswith('.jar'):
                    api = cache / 'fabric-api'
                    api.mkdir(exist_ok=True)
                    (api / Path(name).name).write_bytes(archive.read(name))
        print('Verified', version, item['slug'], item['version'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=('1.21.11', '1.20.1'))
    parser.add_argument('--loader', choices=('fabric','neoforge','forge'), default='fabric')
    args = parser.parse_args()
    prepare(args.version, args.loader)
