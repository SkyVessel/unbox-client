"""Prepare a self-contained Hobing deployment without touching published releases."""
import argparse, hashlib, json, mimetypes, shutil, subprocess, sys
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--origin', required=True, help='HTTPS origin for this environment')
parser.add_argument('--output', required=True, help='A new output directory')
args = parser.parse_args()
parsed = urlsplit(args.origin)
if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.path not in ('', '/') or parsed.query or parsed.fragment:
    parser.error('origin must be an HTTPS origin without a path, query, or credentials')
origin = args.origin.rstrip('/')
out = Path(args.output).resolve()
out.mkdir(parents=True, exist_ok=False)
site = out / 'objects' / 'unbox'
site.mkdir(parents=True)
subprocess.run([sys.executable, str(ROOT / 'scripts/build-website.py'), str(site)], check=True, cwd=ROOT)
subprocess.run([str(ROOT / 'node_modules/.bin/esbuild'), str(ROOT / 'deploy/hobing/worker.mjs'), '--bundle', '--format=esm', '--platform=browser', '--target=es2022', '--outfile=' + str(out / 'worker.mjs')], check=True, cwd=ROOT)
for name in ('auth.sql', 'feedback.sql'):
    dest = out / 'migrations' / name
    dest.parent.mkdir(exist_ok=True)
    shutil.copy2(ROOT / 'deploy/unbox' / name, dest)
metadata = json.loads((ROOT / '.cache/cloudflare/current-metadata/latest.json').read_text())
version = metadata['version']
if not __import__('re').fullmatch(r'\d+\.\d+\.\d+', version):
    raise ValueError('Invalid release version')
release = site / 'releases' / ('v' + version)
release.mkdir(parents=True)
for platform, folder in [('darwin-aarch64', 'release-upload'), ('windows-x86_64', 'windows-upload')]:
    item = metadata['platforms'][platform]
    old = urlsplit(item['url'])
    name = Path(old.path).name
    if old.path != '/unbox/releases/v' + version + '/' + name or not name or name in ('.', '..'):
        raise ValueError('Unexpected release URL')
    source = ROOT / '.cache/cloudflare' / folder / name
    if source.with_name(name + '.sig').read_text().strip() != item['signature'].strip():
        raise ValueError('Release signature does not match the manifest: ' + name)
    shutil.copy2(source, release / name)
    shutil.copy2(source.with_name(name + '.sig'), release / (name + '.sig'))
    item['url'] = origin + old.path
(site / 'releases/latest.json').write_text(json.dumps(metadata, indent=2) + '\n')
objects = []
for path in sorted((out / 'objects').rglob('*')):
    if path.is_file():
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        content_type = 'text/plain' if path.suffix == '.glsl' else mimetypes.guess_type(path.name)[0] or 'application/octet-stream'
        objects.append({'key': path.relative_to(out / 'objects').as_posix(), 'size': path.stat().st_size, 'sha256': digest.hexdigest(), 'contentType': content_type})
(out / 'objects.json').write_text(json.dumps(objects, indent=2) + '\n')
print(f'Prepared {len(objects)} objects for {origin} in {out}')
