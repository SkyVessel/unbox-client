"""Upload a directory through the short-lived, authenticated R2 upload Worker.

The Worker is removed after release verification. Credentials stay in local cache.
"""
from pathlib import Path
import urllib.request, mimetypes, sys, concurrent.futures

directory = Path(sys.argv[1])
prefix = sys.argv[2].strip('/')
assert prefix == 'unbox' or prefix.startswith('unbox/')
token = Path('.cache/cloudflare/upload-token').read_text().strip()
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def upload(path):
    key = prefix + '/' + path.relative_to(directory).as_posix()
    mime = mimetypes.guess_type(path.name)[0] or 'application/octet-stream'
    if path.suffix == '.glsl': mime = 'text/plain'
    request = urllib.request.Request('https://unbox-release-upload.printoria-studio.workers.dev/' + key,
        data=path.read_bytes(), method='PUT', headers={'Authorization': token, 'Content-Type': mime})
    with opener.open(request, timeout=300) as response:
        assert response.status == 200
    print(f'Uploaded {key} ({path.stat().st_size} bytes)', flush=True)

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(upload, sorted(p for p in directory.rglob('*') if p.is_file())))
