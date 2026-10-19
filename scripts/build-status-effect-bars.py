#!/usr/bin/env python3
"""Build the LGPL Status Effect Bars port from source against native NeoForge."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PORT = ROOT / 'ports/status-effect-bars'
NEO = {'26.1': '26.1.0.19-beta', '26.1.2': '26.1.2.116', '26.3': '26.3.0.86'}

def build(version):
    neo = NEO[version]
    runtime = ROOT / ('.cache/neoforge/runtime' if version == '26.1' else f'.cache/neoforge-{version}/runtime')
    libs = runtime / 'libraries'
    patched = libs / f'net/neoforged/minecraft-client-patched/{neo}/minecraft-client-patched-{neo}.jar'
    if not patched.exists():
        raise SystemExit('Prepare the native NeoForge runtime first: ' + str(patched))
    lock = json.loads((ROOT / 'src-tauri/resources/survival-mods-lock.json').read_text())
    target = next(t for t in lock['targets'] if t['minecraft'] == version and t['loader'] == 'neoforge')
    cloth = next(e for e in target['mods'] if e['slug'] == 'cloth-config')
    build = ROOT / f'.cache/ports/status-effect-bars-{version}'
    shutil.rmtree(build, ignore_errors=True)
    source, classes = build / 'src', build / 'classes'
    source.mkdir(parents=True)
    classes.mkdir()
    for file in (PORT / 'src/main/java').rglob('*.java'):
        if file.name == 'ModMenuIntegration.java':
            continue
        text = file.read_text()
        text = text.replace('import org.jetbrains.annotations.Nullable;', '').replace('@Nullable ', '')
        if version != '26.3':
            text = text.replace('net.minecraft.client.gui.Hud', 'net.minecraft.client.gui.Gui').replace('@Mixin(Hud.class)', '@Mixin(Gui.class)').replace('client/gui/Hud;', 'client/gui/Gui;')
        (source / file.name).write_text(text)
    cp = [patched, ROOT / '.cache/survival-mods' / cloth['filename']]
    cp += [p for p in libs.rglob('*.jar') if p != patched]
    cp += list((ROOT / f'.cache/runtime-{version}/libraries').rglob('*.jar'))
    cp += list((ROOT / '.cache/libs').glob('*.jar'))
    universal = libs / f'net/neoforged/neoforge/{neo}/neoforge-{neo}-universal.jar'
    with zipfile.ZipFile(universal) as z:
        for name in z.namelist():
            if name.endswith('.jar') and 'mixinextras' in name:
                dep = build / Path(name).name
                dep.write_bytes(z.read(name))
                cp.append(dep)
    command = ['javac', '--release', '25', '-proc:none', '-cp', os.pathsep.join(map(str, cp)), '-d', str(classes), *map(str, source.glob('*.java'))]
    if subprocess.run(command).returncode:
        raise SystemExit('Status Effect Bars compilation failed for ' + version)
    shutil.copytree(PORT / 'src/main/resources', classes, dirs_exist_ok=True)
    (classes / 'fabric.mod.json').unlink()
    (classes / 'META-INF').mkdir(exist_ok=True)
    (classes / 'META-INF/neoforge.mods.toml').write_text(f'''modLoader="javafml"
loaderVersion="[1,)"
license="LGPL-3.0"
[[mods]]
modId="status_effect_bars"
version="1.0.12-unbox.1"
displayName="Status Effect Bars (Unbox NeoForge port)"
authors="A5b84 and contributors; NeoForge port by Unbox"
description="Unofficial native NeoForge port. Corresponding source is included with the distribution."
[[mixins]]
config="status-effect-bars.mixins.json"
[[dependencies.status_effect_bars]]
modId="minecraft"
type="required"
versionRange="[{version}]"
side="CLIENT"
[[dependencies.status_effect_bars]]
modId="cloth_config"
type="required"
versionRange="[1,)"
side="CLIENT"
''')
    for name in ['COPYING', 'COPYING.LESSER', 'UPSTREAM', 'PORT.md']:
        shutil.copy2(PORT / name, classes / name)
    dest = ROOT / f'src-tauri/resources/status-effect-bars-neoforge-{version}.jar'
    subprocess.run(['jar', '--create', '--file', str(dest), '-C', str(classes), '.'], check=True)
    print('Built', dest, flush=True)

if __name__ == '__main__':
    for version in sys.argv[1:] or NEO:
        build(version)
    with zipfile.ZipFile(ROOT / 'src-tauri/resources/status-effect-bars-sources.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for path in PORT.rglob('*'):
            if path.is_file() and not any(part in {"build",".gradle",".git",".kotlin"} for part in path.relative_to(PORT).parts):
                z.write(path, 'ports/status-effect-bars/' + str(path.relative_to(PORT)))
        z.write(__file__, 'scripts/build-status-effect-bars.py')
