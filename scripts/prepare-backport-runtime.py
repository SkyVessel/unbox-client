#!/usr/bin/env python3
"""Prepare the checksum-pinned runtime used by the isolated backport tests."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def matches(data, item):
    return all(hashlib.new(a, data).hexdigest() == item[a] for a in ('sha1', 'sha512'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=('1.21.11', '1.20.1'))
    parser.add_argument('--loader', choices=('fabric', 'neoforge', 'forge'), default='fabric')
    args = parser.parse_args()
    lock = ROOT / f'client-mod/runtime-lock-{args.version}-{args.loader}.json'
    if not lock.is_file():
        raise SystemExit('This native runtime has no verified dependency lock yet: ' + str(lock))
    target = json.loads(lock.read_text())
    cache = ROOT / '.cache' / args.version / ('runtime-mods' if args.loader == 'fabric' else args.loader + '/runtime-mods')
    cache.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for item in target['mods']:
        filename = item['filename']
        if Path(filename).name != filename:
            raise ValueError('Invalid dependency filename')
        dest = cache / filename
        if dest.exists() and matches(dest.read_bytes(), item):
            continue
        if item.get('buildProject'):
            subprocess.run([str(ROOT / 'client-mod/gradlew'), '-p', str(ROOT / item['buildProject']), 'build', '--console=plain'] + ['-P' + k + '=' + v for k, v in item.get('buildProperties', {}).items()], cwd=ROOT, check=True)
            data = (ROOT / item['artifact']).read_bytes()
        else:
            request = urllib.request.Request(item['url'], headers={'User-Agent': 'Unbox-backport-build'})
            with opener.open(request, timeout=90) as response:
                data = response.read()
        if not matches(data, item):
            raise ValueError('Dependency checksum mismatch: ' + item['slug'])
        dest.write_bytes(data)
    print('Verified', len(target['mods']), 'native dependencies for', args.version, args.loader)


if __name__ == '__main__':
    main()
