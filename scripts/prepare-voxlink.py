#!/usr/bin/env python3
"""Build the pinned VoxLink networking subset without its UI, accounts or telemetry."""
import pathlib, subprocess, shutil, zipfile
root = pathlib.Path(__file__).resolve().parents[1]
source = root / 'ports/voxlink'
classes = root / '.cache/voxlink/classes'
shutil.rmtree(classes, ignore_errors=True)
classes.mkdir(parents=True)
subprocess.run(['javac', '--release', '25', '-proc:none', '-cp', str(root / '.cache/libs/*'),
                '-d', str(classes), *map(str, (source / 'src').rglob('*.java'))], check=True)
licenses = classes / 'META-INF/licenses/voxlink'
licenses.mkdir(parents=True)
for name in ('LICENSE', 'README.md', 'upstream.json'):
    shutil.copy2(source / name, licenses / name)
# Ship the corresponding modified LGPL sources with every local module build.
with zipfile.ZipFile(licenses / 'sources.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in (source / 'src').rglob('*.java'):
        archive.write(path, path.relative_to(source))

resources = root / 'src-tauri/resources'
resources.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(resources / 'voxlink-sources.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(source.rglob('*')):
        if path.is_file():
            archive.write(path, path.relative_to(root))
    archive.write(pathlib.Path(__file__), 'scripts/prepare-voxlink.py')
