import contextlib
import hashlib
import io
import json
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit

SCRIPT = Path(__file__).resolve().parents[1] / 'deploy/hobing/deploy-preview.py'


class TestProductionDeployment(unittest.TestCase):
    def test_production_mapping_and_no_implicit_promotion(self):
        with tempfile.TemporaryDirectory() as tmp:
            package = Path(tmp)
            (package / 'objects/unbox/releases').mkdir(parents=True)
            (package / 'migrations').mkdir()
            release = {'version': '1.0.0', 'platforms': {'test': {'url': 'https://unbox.example/unbox/releases/v1.0.0/file'}}}
            (package / 'objects/unbox/releases/latest.json').write_text(json.dumps(release))
            objects = {'unbox/index.html': b'page', 'unbox/releases/v1.0.0/file': b'x' * (33 * 1048576)}
            for key, content in objects.items():
                path = package / 'objects' / key
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            (package / 'objects.json').write_text(json.dumps([{'key': k, 'size': len(v), 'sha256': hashlib.sha256(v).hexdigest(), 'contentType': 'application/octet-stream'} for k, v in objects.items()]))
            for name in ('auth.sql', 'feedback.sql'):
                (package / 'migrations' / name).write_text('SELECT 1;')
            (package / 'worker.mjs').write_text('export default {}')
            stored, parts, calls = {}, {}, []

            class Opener:
                def open(self, request, timeout=None):
                    url = urlsplit(request.full_url)
                    path = url.path.split('/runtime', 1)[1]
                    query = parse_qs(url.query)
                    method = request.get_method()
                    calls.append((method, path, query))
                    if path.startswith('/files'):
                        self.assert_environment(query)
                    if path in ('/provision', '/migrations', '/deployments'):
                        body = json.loads(request.data)
                        assert body['environment'] == 'production'
                        if path == '/provision': return io.BytesIO(b'{"app":{"revision":0}}')
                        if path == '/migrations': return io.BytesIO(b'{}')
                        assert body['config']['vars']['PUBLIC_ORIGIN'] == 'https://unbox.example'
                        return io.BytesIO(b'{"deployment":{"id":"candidate"}}')
                    if path == '/files':
                        key = query['key'][0]
                        if method == 'GET':
                            if key not in stored: raise HTTPError(request.full_url, 404, 'missing', {}, None)
                            return io.BytesIO(stored[key])
                        stored[key] = request.data
                        return io.BytesIO(b'{}')
                    if path == '/files/uploads':
                        body = json.loads(request.data)
                        assert body['environment'] == 'production'
                        parts['key'] = body['key']
                        return io.BytesIO(json.dumps({'id': 'a' * 32, 'partBytes': 32 * 1048576}).encode())
                    if '/parts/' in path:
                        parts[int(path.rsplit('/', 1)[1])] = request.data
                        return io.BytesIO(b'{}')
                    if path.endswith('/complete'):
                        stored[parts['key']] = b''.join(parts[i] for i in sorted(k for k in parts if isinstance(k, int)))
                        return io.BytesIO(b'{}')
                    raise AssertionError((method, path))

                @staticmethod
                def assert_environment(query):
                    assert query.get('environment') == ['production']

            argv = [str(SCRIPT), '--api', 'https://api.example', '--site-origin', 'https://unbox.example', '--project', 'a' * 32, '--package', str(package), '--environment', 'production', '--token-stdin']
            with patch('urllib.request.build_opener', return_value=Opener()), patch.object(sys, 'argv', argv), patch.object(sys, 'stdin', io.StringIO('test-token\n')), contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(SCRIPT), run_name='__main__')
            self.assertEqual(stored, objects)
            self.assertTrue(any('/parts/' in path for _, path, _ in calls))
            self.assertFalse(any(path == '/promote' for _, path, _ in calls))


if __name__ == '__main__':
    unittest.main()
