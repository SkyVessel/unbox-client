"""Seed and deploy an Unbox package through Hobing's runtime API."""
import argparse, getpass, hashlib, json, re, sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import Request, build_opener, ProxyHandler

parser = argparse.ArgumentParser()
parser.add_argument('--api', required=True, help='Hobing API HTTPS origin')
parser.add_argument('--site-origin', required=True, help='Exact HTTPS preview site origin used to build the package')
parser.add_argument('--project', required=True, help='Owned Hobing project ID')
parser.add_argument('--package', required=True, type=Path, help='Output of package.py for the preview origin')
parser.add_argument('--environment', choices=('preview', 'production'), default='preview')
parser.add_argument('--promote', action='store_true', help='Promote after upload and migrations')
parser.add_argument('--token-stdin', action='store_true', help='Read the token from standard input instead of an interactive prompt')
args = parser.parse_args()
parsed = urlsplit(args.api)
if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.path not in ('', '/') or parsed.query or parsed.fragment:
    parser.error('api must be an HTTPS origin')
site = urlsplit(args.site_origin)
if site.scheme != 'https' or not site.hostname or site.username or site.password or site.path not in ('', '/') or site.query or site.fragment:
    parser.error('site-origin must be an HTTPS origin')
if not re.fullmatch(r'[a-f0-9]{32}', args.project):
    parser.error('project must be a 32-character lowercase hexadecimal ID')
package = args.package.resolve()
objects = json.loads((package / 'objects.json').read_text())
release = json.loads((package / 'objects/unbox/releases/latest.json').read_text())
if any(not item['url'].startswith(args.site_origin.rstrip('/') + '/unbox/releases/v' + release['version'] + '/') for item in release['platforms'].values()):
    parser.error('package release URLs do not match site-origin')
token = sys.stdin.readline().rstrip('\r\n') if args.token_stdin else getpass.getpass('Hobing owner or scoped agent token: ')
if not token or '\n' in token or '\r' in token:
    sys.exit('Invalid token')
base = args.api.rstrip('/') + '/v1/projects/' + args.project + '/runtime'
opener = build_opener(ProxyHandler({}))
environment = args.environment

def request(method, path, data=None, content_type=None, missing_ok=False, raw=False):
    headers = {'Authorization': 'Bearer ' + token, 'User-Agent': 'Hobing-Unbox-Deployer/1.0'}
    if content_type:
        headers['Content-Type'] = content_type
    req = Request(base + path, data=data, headers=headers, method=method)
    try:
        with opener.open(req, timeout=300) as response:
            if raw:
                digest = hashlib.sha256()
                for chunk in iter(lambda: response.read(1024 * 1024), b''):
                    digest.update(chunk)
                return digest.hexdigest()
            body = response.read()
            return json.loads(body) if body else {}
    except HTTPError as error:
        if missing_ok and error.code == 404:
            error.close()
            return None
        try:
            code = json.loads(error.read()).get('error', {}).get('code', 'unknown')
            if not isinstance(code, str) or not re.fullmatch(r'[a-z0-9_]{1,80}', code):
                code = 'unknown'
        except (ValueError, AttributeError):
            code = 'unknown'
        raise RuntimeError(f'Hobing returned HTTP {error.code} ({code}) for {method} {path.split("?")[0]}') from None

def post(path, value):
    return request('POST', path, json.dumps(value).encode(), 'application/json')

def object_path(key):
    return '/files?environment=' + environment + '&key=' + quote(key, safe='')

app = post('/provision', {'environment': environment})['app']
for name in ('auth.sql', 'feedback.sql'):
    post('/migrations', {'environment': environment, 'name': 'unbox-' + name[:-4] + '-001', 'sql': (package / 'migrations' / name).read_text()})
for obj in sorted(objects, key=lambda item: (item['key'].endswith('/latest.json'), item['key'])):
    key = obj['key']
    path = (package / 'objects' / key).resolve()
    if not path.is_relative_to(package / 'objects') or not path.is_file():
        raise ValueError('Invalid package object path')
    digest = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != obj['sha256'] or path.stat().st_size != obj['size']:
        raise ValueError('Package checksum or size mismatch for ' + key)
    remote = request('GET', object_path(key), missing_ok=True, raw=True)
    if remote == obj['sha256']:
        print('Verified existing', key, flush=True)
        continue
    if remote is not None:
        raise RuntimeError('Existing object differs; inspect it before overwriting: ' + key)
    if obj['size'] <= 32 * 1048576:
        request('PUT', object_path(key), path.read_bytes(), obj['contentType'])
    else:
        upload = post('/files/uploads?environment=' + environment, {'environment': environment, 'key': key, 'bytes': obj['size'], 'contentType': obj['contentType']})
        with path.open('rb') as f:
            number = 1
            while chunk := f.read(upload['partBytes']):
                request('PUT', f'/files/uploads/{upload["id"]}/parts/{number}?environment={environment}', chunk, 'application/octet-stream')
                number += 1
        post(f'/files/uploads/{upload["id"]}/complete?environment={environment}', {})
    if request('GET', object_path(key), raw=True) != obj['sha256']:
        raise RuntimeError('Remote checksum mismatch: ' + key)
    print('Uploaded and verified', key, flush=True)
source = (package / 'worker.mjs').read_text()
config = {'compatibility_date': '2026-10-01', 'compatibility_flags': ['nodejs_compat'], 'routes': ['/*'], 'healthcheck': '/unbox/', 'vars': {'PUBLIC_ORIGIN': args.site_origin.rstrip('/')}, 'bindings': [{'name': 'DB', 'type': 'd1', 'resource': 'database'}, {'name': 'FILES', 'type': 'r2_bucket', 'resource': 'files'}]}
fingerprint = hashlib.sha256((source + json.dumps(config, sort_keys=True)).encode()).hexdigest()
deployment = post('/deployments', {'environment': environment, 'idempotencyKey': 'unbox-' + fingerprint[:24], 'entrypoint': 'worker.js', 'modules': [{'name': 'worker.js', 'content': source}], 'config': config, 'secrets': {}})['deployment']
print(environment.capitalize() + ' deployment ready:', deployment['id'], flush=True)
if args.promote:
    result = post('/promote', {'environment': environment, 'deploymentId': deployment['id'], 'expectedRevision': app['revision']})
    print(environment.capitalize() + ' promoted, revision:', result['app']['revision'], flush=True)
