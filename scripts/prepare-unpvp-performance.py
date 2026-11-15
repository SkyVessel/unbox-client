#!/usr/bin/env python3
"""Fetch pinned, unmodified upstream artifacts. No expvp or OneConfig inputs.

Build order: build-unpvp.py, this script, build-unpvp-patcher.py, this script.
Third-party jars stay in the local cache, outside bundled application resources.
"""
from pathlib import Path
import concurrent.futures, hashlib, html, json, re, shutil, urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/unpvp-performance/jars'
LOCK = json.loads((ROOT / 'src-tauri/resources/unpvp-performance.json').read_text())

def fetch(entry):
    target = CACHE / entry['path']
    if target.exists() and hashlib.sha512(target.read_bytes()).hexdigest() == entry['sha512']:
        return target.name
    url = entry.get('url')
    if not url:
        with urllib.request.urlopen(entry['downloadPage'], timeout=60) as response:
            page = response.read().decode()
        match = re.search(r'href=[\'"](downloadx\?[^\'"<>]+)', page)
        if not match:
            raise RuntimeError('Official OptiFine download link unavailable')
        url = 'https://optifine.net/' + html.unescape(match[1])
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read()
    if hashlib.sha512(data).hexdigest() != entry['sha512']:
        raise RuntimeError('Upstream checksum mismatch: ' + entry['path'])
    temp = target.with_suffix('.tmp')
    temp.write_bytes(data)
    temp.replace(target)
    return target.name

if __name__ == '__main__':
    CACHE.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for name in pool.map(fetch, LOCK['mods']):
            print('Verified', name)
    patcher = ROOT / 'src-tauri/resources/unpvp-patcher-performance.jar'
    if patcher.exists():
        shutil.copy2(patcher, CACHE / patcher.name)
    else:
        print('Next: python3 scripts/build-unpvp-patcher.py')

    pvp = ROOT / 'src-tauri/resources/unpvp-pvp.jar'
    if pvp.exists():
        shutil.copy2(pvp, CACHE / pvp.name)
    else:
        print('Next: python3 scripts/build-unpvp-pvp.py')
