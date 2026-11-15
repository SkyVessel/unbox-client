#!/usr/bin/env python3
"""Run the remapped Fabric JAR in an isolated, offline backport test profile.

Requires prepare-game-runtime.py and the pinned runtime mods under .cache/VERSION.
This deliberately uses the production intermediary namespace, not Loom's named run.
"""
import argparse
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import struct
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('runtime', ROOT / 'scripts/prepare-game-runtime.py')
runtime_helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime_helper)


def selector_fixture(game, version):
    """Minimal metadata for the world-list test; this fixture is never loaded."""
    dest = game / 'saves/selector-fixture/level.dat'
    if dest.exists():
        return
    with zipfile.ZipFile(ROOT / f'.cache/client-{version}.jar') as jar:
        data_version = json.loads(jar.read('version.json'))['world_version']
    def string(value):
        data = value.encode('utf-8')
        return struct.pack('>H', len(data)) + data
    def tag(kind, name, value):
        return bytes([kind]) + string(name) + value
    def number(name, value):
        return tag(3, name, struct.pack('>i', value))
    version_tag = number('Id', data_version) + tag(8, 'Name', string(version)) + tag(8, 'Series', string('main')) + tag(1, 'Snapshot', b'\0') + b'\0'
    data = number('DataVersion', data_version) + number('version', 19133) + number('GameType', 1)
    data += tag(8, 'LevelName', string('Unbox selector fixture (not playable)')) + tag(4, 'LastPlayed', struct.pack('>q', 0))
    data += tag(1, 'hardcore', b'\0') + tag(1, 'allowCommands', b'\1') + tag(10, 'Version', version_tag) + b'\0'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(gzip.compress(tag(10, '', tag(10, 'Data', data) + b'\0'), mtime=0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=('1.21.11', '1.20.1'))
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--smoke', choices=('home', 'ui', 'feature', 'portUi'), default='home')
    parser.add_argument('--pvp', action='store_true', help='Prepare pinned PvP modules (1.21.11 Fabric only)')
    parser.add_argument('--interactive', action='store_true', help='Open a separate manual test profile without automated checks or automatic exit')
    parser.add_argument('--world')
    parser.add_argument('--create-world', action='store_true')
    parser.add_argument('--toggle-only', action='store_true')
    parser.add_argument('--reset-settings', action='store_true', help='Reset options and Unbox settings in this isolated test profile')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    version = args.version
    if args.pvp and version != '1.21.11':raise ValueError('PvP modules require 1.21.11 Fabric')
    root = ROOT / '.cache' / version
    runtime = ROOT / '.cache' / ('runtime-' + version)
    game = root / ('manual-fabric' if args.interactive else 'production-fabric-' + args.smoke if args.smoke in ('home', 'ui') else 'production-fabric')
    mods = game / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / f'client-mod/runtime-lock-{version}-fabric.json').read_text())
    expected = {item['filename'] for item in lock['mods']} | {'unbox-client.jar'}
    if args.pvp:expected.update(e['filename'] for e in json.loads((ROOT/'client-mod/pvp-lock-1.21.11-fabric.json').read_text())['mods'])
    unexpected = {p.name for p in mods.glob('*.jar')} - expected
    if unexpected:
        raise ValueError('Unpinned mods remain in the isolated test profile: ' + ', '.join(sorted(unexpected)))
    for item in lock['mods']:
        source = root / 'runtime-mods' / item['filename']
        data = source.read_bytes()
        if any(hashlib.new(a, data).hexdigest() != item[a] for a in ('sha1', 'sha512')):
            raise ValueError('Dependency checksum mismatch: ' + item['slug'])
        dest = mods / item['filename']
        if dest.exists() and dest.read_bytes() != data:
            raise ValueError('Preserved different file: ' + str(dest))
        if not dest.exists():
            dest.write_bytes(data)
    if args.pvp:
        spec=importlib.util.spec_from_file_location('pvp_prepare',ROOT/'scripts/prepare-pvp-mods.py');pvp=importlib.util.module_from_spec(spec);spec.loader.exec_module(pvp);pvp.prepare(game)
    jars = list((ROOT / f'client-mod/build/fabric-{version}/libs').glob(f'unbox-client-*-{version}-fabric.jar'))
    if len(jars) != 1:
        raise ValueError('Build exactly one remapped Unbox artifact first')
    shutil.copy2(jars[0], mods / 'unbox-client.jar')
    if args.reset_settings:
        settings = game / 'config/unbox.properties'
        if settings.exists():
            shutil.copy2(settings, settings.with_suffix('.properties.before-test'))
            settings.write_text('# Isolated backport smoke fixture\n')
    if args.reset_settings or not (game / 'options.txt').exists():
        volume = '1.0' if args.interactive else '0.0'
        (game / 'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\nguiScale:2\nrenderDistance:6\nsimulationDistance:5\npauseOnLostFocus:false\nsoundCategory_master:' + volume + '\nmaxFps:60\n')
    if args.smoke == 'home' and not args.interactive:
        selector_fixture(game, version)
    meta = json.loads((runtime / f'{version}.json').read_text())
    fabric = json.loads((runtime / 'fabric-0.19.5.json').read_text())
    classpath = [str(runtime / 'libraries' / lib['downloads']['artifact']['path'])
                 for lib in meta['libraries'] if runtime_helper.allowed(lib) and 'artifact' in lib.get('downloads', {})]
    for lib in fabric['libraries']:
        group, artifact, release = lib['name'].split(':')
        classpath.append(str(runtime / 'libraries' / group.replace('.', '/') / artifact / release / f'{artifact}-{release}.jar'))
    classpath.append(str(ROOT / f'.cache/client-{version}.jar'))
    if any(not Path(p).is_file() for p in classpath):
        raise ValueError('Missing prepared game libraries')
    command = [str(args.java), '-Xmx3G', '-Djava.awt.headless=true']
    if not args.interactive:
        command.append('-Dunbox.startupDiagnostics=true')
        command.append('-Dunbox.portUiTest=true' if args.smoke=='portUi' else '-Dunbox.' + args.smoke + 'Smoke=true')
    if args.create_world:
        command.append('-Dunbox.createPortWorld=true')
        command.append('-Dunbox.portWorld=port-world-' + uuid.uuid4().hex[:12])
    if args.toggle_only:
        command.append('-Dunbox.toggleOnly=true')
    if platform.system() == 'Darwin':
        command.append('-XstartOnFirstThread')
    command += ['-cp', os.pathsep.join(classpath), fabric['mainClass'], '--version', version,
                '--username', 'UnboxPortTest', '--uuid', str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:UnboxPortTest').digest(), version=3)),
                '--accessToken', '0', '--userType', 'legacy', '--gameDir', str(game),
                '--assetsDir', str(runtime / 'assets'), '--assetIndex', meta['assetIndex']['id'],
                '--width', '1280', '--height', '800']
    if args.world:
        command += ['--quickPlaySingleplayer', args.world]
    (game / 'test-launch-args.json').write_text(json.dumps(command, indent=2) + '\n')
    if args.interactive:
        log_path = root / 'manual-fabric.log'
        with log_path.open('a') as log:
            process = subprocess.Popen(command, cwd=game, stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
        (game / 'manual-test.pid').write_text(str(process.pid) + '\n')
        print('Started manual test PID:', process.pid, 'Profile:', game, 'Log:', log_path)
        return
    report = game / 'port-ui-checks.json' if args.smoke=='portUi' else game / 'logs' / (args.smoke + '-smoke.json')
    if report.exists():
        report.unlink()
    with (root / ('production-' + args.smoke + '.log')).open('w') as log:
        result = subprocess.run(command, cwd=game, stdout=log, stderr=log, timeout=args.timeout)
    print('Exit:', result.returncode, 'Log:', root / ('production-' + args.smoke + '.log'))
    if result.returncode:
        raise SystemExit(result.returncode)
    if not report.exists():
        raise SystemExit('No smoke report produced: ' + str(report))
    evidence = json.loads(report.read_text())
    print('Checks:', len(evidence.get('checks', [])), 'Report:', report)
    success = evidence.get('ok') if args.smoke == 'feature' else evidence.get('passed', 0) > 0
    if not success:
        raise SystemExit('Smoke failed: ' + evidence.get('error', 'unknown error'))


if __name__ == '__main__':
    main()
