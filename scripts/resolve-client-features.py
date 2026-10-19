#!/usr/bin/env python3
"""Resolve candidate native artifacts for review; never install into a player's profile.

Writes .cache/client-features/{matrix,candidates}.json. Existing performance dependencies
remain pinned. Each downloaded JAR is verified against both upstream hashes.
"""
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import tomllib
import threading
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/client-features'
PROJECTS = {
    'mousetweaks': 'mouse-tweaks', 'shulker': 'easy-shulker-boxes',
    'effectbars': 'status-effect-bars', 'bookshelf': 'chiseled-bookshelf-visualizer',
    'resources': 'resourcify', 'spatial': 'spatial-gui', 'building': 'effortless-building',
}
API_LOCK = threading.RLock()

def fetch(url):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(urllib.request.Request(url, headers={'User-Agent': 'UnboxClient native integration'}), timeout=90) as r:
        return r.read()

def api(path):
    dest = CACHE / 'api' / (path.replace('/', '_') + '.json')
    with API_LOCK:
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(fetch('https://api.modrinth.com/v2/' + path))
        return json.loads(dest.read_bytes())

def choose(project, version, loader):
    versions = api(f'project/{project}/version')
    return next((v for v in versions if version in v['game_versions'] and loader in v['loaders'] and v['version_type'] == 'release'), None)

def artifact(v, project, loader, version, role):
    with API_LOCK:
        return artifact_locked(v, project, loader, version, role)

def artifact_locked(v, project, loader, version, role):
    file = next((f for f in v['files'] if f['primary']), v['files'][0])
    path = ROOT / '.cache/survival-mods' / file['filename']
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or hashlib.sha512(path.read_bytes()).hexdigest() != file['hashes']['sha512']:
        data = fetch(file['url'])
        for algo in ('sha1', 'sha512'):
            if hashlib.new(algo, data).hexdigest() != file['hashes'][algo]:
                raise ValueError('Checksum mismatch: ' + file['filename'])
        path.write_bytes(data)
    with zipfile.ZipFile(path) as z:
        if 'fabric.mod.json' in z.namelist() and loader == 'fabric':
            meta = json.loads(z.read('fabric.mod.json'), strict=False)
            ids = [meta['id']]
            requirements = meta.get('depends', {})
        else:
            name = 'META-INF/neoforge.mods.toml' if 'META-INF/neoforge.mods.toml' in z.namelist() else 'META-INF/mods.toml'
            archives = [z]
            if name not in z.namelist():
                archives = [zipfile.ZipFile(io.BytesIO(z.read(n))) for n in z.namelist() if n.endswith('.jar')]
            ids, requirements = [], {}
            for archive in archives:
                for metadata in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
                    if metadata in archive.namelist():
                        meta = tomllib.loads(archive.read(metadata).decode())
                        ids.extend(m['modId'] for m in meta['mods'])
                        requirements.update(meta.get('dependencies', {}))
                        break
            if not ids:
                raise ValueError('No native metadata in ' + file['filename'])
    return dict(slug=project['slug'], projectId=project['id'], id=v['id'], version=v['version_number'],
        loader=loader, gameVersions=[version], upstreamGameVersions=v['game_versions'], upstreamLoaders=v['loaders'],
        filename=file['filename'], url=file['url'], sha1=file['hashes']['sha1'], sha512=file['hashes']['sha512'],
        size=file['size'], source=project['source_url'], license=project['license'],
        projectUrl='https://modrinth.com/mod/' + project['slug'], role=role, providedBy='survival-mods-lock',
        dependencies=[dict(projectId=d['project_id'], id=d['version_id'], type=d['dependency_type']) for d in v['dependencies']],
        modIds=ids, artifactRequirements=requirements)

def resolve(target):
    loader, version = target['loader'], target['minecraft']
    entries = {e['projectId']: e for e in target['mods']}
    status = {}
    def add(v, role):
        pid = v['project_id']
        if pid in entries:
            return entries[pid]
        project = api('project/' + pid)
        entry = artifact(v, project, loader, version, role)
        entries[pid] = entry
        for d in v['dependencies']:
            if d['dependency_type'] != 'required' or d['project_id'] in entries:
                continue
            dep = api('version/' + d['version_id']) if d['version_id'] else choose(d['project_id'], version, loader)
            if dep is None:
                raise ValueError('Missing dependency ' + str(d['project_id']))
            add(dep, 'dependency')
        return entry
    projects = dict(PROJECTS)
    projects['config-dependency'] = 'cloth-config'
    for module, slug in projects.items():
        previous = dict(entries)
        try:
            v = choose(slug, version, loader)
            if v is None:
                status[module] = {'state': 'port-required'}
                continue
            entry = add(v, 'host-feature' if module == 'building' else 'client-feature')
            status[module] = {'state': 'resolved', 'version': entry['version'], 'modIds': entry['modIds']}
        except Exception as e:
            entries = previous
            status[module] = {'state': 'blocked-dependency', 'error': str(e)}
    return ({**target, 'mods': list(entries.values())}, {'loader': loader, 'minecraft': version, 'features': status})

def main():
    lock = json.loads((ROOT / 'src-tauri/resources/survival-mods-lock.json').read_text())
    CACHE.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(resolve, lock['targets']))
    (CACHE / 'candidates.json').write_text(json.dumps({**lock, 'targets': [r[0] for r in results]}, indent=2) + '\n')
    (CACHE / 'matrix.json').write_text(json.dumps([r[1] for r in results], indent=2) + '\n')
    for _, matrix in results:
        print(json.dumps(matrix), flush=True)

if __name__ == '__main__':
    main()
