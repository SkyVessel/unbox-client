"""Verify the public website download URLs and updater bytes against local assets."""
from pathlib import Path
import urllib.request, json, hashlib, base64, subprocess

base = 'https://printoria.printoria-studio.workers.dev/unbox/'
out = Path('.cache/cloudflare/public-downloads')
out.mkdir(parents=True, exist_ok=True)
release = Path('.cache/release-0.2.21')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def request(path, method='GET', headers=None):
    return opener.open(urllib.request.Request(path if path.startswith('https://') else base + path,
        method=method, headers={'User-Agent': 'Unbox-Release-Verification/1.0', **(headers or {})}), timeout=300)

with request('releases/latest.json') as response:
    manifest = json.load(response)
assert manifest == json.loads((release / 'latest.json').read_text())
report = []
for os_name, platform in [('macos', 'darwin-aarch64'), ('windows', 'windows-x86_64')]:
    asset = manifest['platforms'][platform]
    name = asset['url'].rsplit('/', 1)[1]
    local = release / name
    with request('download/' + os_name) as response:
        assert response.url == asset['url'], response.url
        assert response.headers.get('Content-Disposition', '').startswith('attachment;')
        with (out / name).open('wb') as target:
            while chunk := response.read(1024 * 1024): target.write(chunk)
    digest = hashlib.sha256((out / name).read_bytes()).hexdigest()
    assert digest == hashlib.sha256(local.read_bytes()).hexdigest()
    with request(asset['url'], 'HEAD') as response:
        assert response.status == 200
    with request(asset['url'], headers={'Range': 'bytes=1024-2047'}) as response:
        assert response.status == 206
        assert response.read() == local.read_bytes()[1024:2048]
    signature = out / (platform + '.signature.txt')
    signature.write_bytes(base64.b64decode(asset['signature']))
    subprocess.run(['.cache/verify-release', str(release / 'public-key.txt'), str(signature), str(out / name)], check=True)
    report.append({'platform': platform, 'url': asset['url'], 'bytes': local.stat().st_size, 'sha256': digest,
        'download_button': 'passed', 'range': 'passed', 'signature_and_tamper_rejection': 'passed'})
    print(f'{platform}: public button, full download, range and signature passed', flush=True)
(out / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
