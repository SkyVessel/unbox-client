#!/usr/bin/env python3
"""Exercise the shipped network classes on the native OS without launching Minecraft."""
from pathlib import Path
import hashlib, os, subprocess, urllib.request
root = Path(__file__).resolve().parents[1]
cache = root / '.cache/voxlink-bundle-tests'
cache.mkdir(parents=True, exist_ok=True)
# Pinned artifacts and digests from Minecraft's published library manifest.
libraries = [
    ('com/google/code/gson/gson/2.13.2/gson-2.13.2.jar', '48b8230771e573b54ce6e867a9001e75977fe78e'),
    ('org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar', 'd9e58ac9c7779ba3bf8142aff6c830617a7fe60f'),
]
classpath = [root / 'src-tauri/resources/unbox-client.jar']
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
for name, digest in libraries:
    path = cache / Path(name).name
    if not path.exists() or hashlib.sha1(path.read_bytes()).hexdigest() != digest:
        with opener.open('https://libraries.minecraft.net/' + name, timeout=60) as response:
            data = response.read()
        assert hashlib.sha1(data).hexdigest() == digest, name
        path.write_bytes(data)
    classpath.append(path)
cp = os.pathsep.join(map(str, classpath))
subprocess.run(['javac', '--release', '25', '-proc:none', '-cp', cp, '-d', str(cache),
                str(root / 'tests/VoxLinkTransportTest.java')], check=True)
subprocess.run(['java', '-cp', cp + os.pathsep + str(cache),
                'dev.unbox.client.VoxLinkTransportTest'], check=True, timeout=150)
