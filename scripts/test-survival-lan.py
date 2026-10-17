#!/usr/bin/env python3
"""Optional same-machine TCP packet checks with two isolated, modded clients.
Usage: python3 scripts/test-survival-lan.py WORLD_DIRECTORY RUN_NAME [CLIENT_JAR]
The source world is copied by benchmark.py. No accounts, cloud rooms or paid services are used.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import uuid

ROOT=Path(__file__).resolve().parents[1]


def stop(process):
    if process is None or process.poll() is not None:
        return
    try: os.killpg(process.pid,signal.SIGTERM)
    except ProcessLookupError: return
    try: process.wait(timeout=25)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid,signal.SIGKILL)
        process.wait(timeout=10)
    # benchmark.py is a wrapper; also reap its Java process group after a bounded shutdown.
    deadline=time.monotonic()+5
    while time.monotonic()<deadline:
        try: os.killpg(process.pid,0)
        except ProcessLookupError: return
        time.sleep(.1)
    try: os.killpg(process.pid,signal.SIGKILL)
    except ProcessLookupError: pass


def main():
    if len(sys.argv) not in (3,4):
        raise SystemExit(__doc__)
    source=Path(sys.argv[1]).resolve()
    name=sys.argv[2]
    if not name.replace('-','').isalnum():
        raise SystemExit('Use a fresh alphanumeric/hyphen run name')
    game=ROOT/'.cache/benchmarks'/name
    if game.exists():
        raise SystemExit('Choose a fresh run name; existing fixtures and reports are preserved')
    if not (source/'level.dat').is_file():
        raise SystemExit('A saved source world with level.dat is required')
    command=[sys.executable,str(ROOT/'scripts/benchmark.py'),'optimized',str(source),name]
    if len(sys.argv)==4: command.append(str(Path(sys.argv[3]).resolve()))
    env={**os.environ,'UNBOX_SURVIVAL_LAN':'host','UNBOX_SURVIVAL_MODS':'1','UNBOX_MAP_BENCH':'1'}
    # This test always gets an isolated map cache as well as an isolated world and configs.
    env['UNBOX_BENCH_GAME_DIR']=str(game)
    env['UNBOX_SHARED_CACHE']=str(game/'shared-cache')
    game.parent.mkdir(parents=True,exist_ok=True)
    host=None
    guest=None
    host_log=game.parent/(name+'-driver.log')
    with host_log.open('x') as log:
        try:
            host=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            port=game/'logs/survival-lan-port.txt'
            deadline=time.monotonic()+160
            while not port.exists():
                if host.poll() is not None:
                    raise RuntimeError('Host stopped before publishing LAN; inspect '+str(host_log))
                if time.monotonic()>deadline:
                    raise RuntimeError('Host LAN publication timed out; inspect '+str(host_log))
                time.sleep(.5)
            args=json.loads((game/'test-launch-args.json').read_text())
            folder=game/'lan-guest'
            folder.mkdir()
            shutil.copytree(game/'mods',folder/'mods')
            shutil.copytree(game/'config',folder/'config')
            (folder/'logs').mkdir()
            (folder/'options.txt').write_text('onboardAccessibility:false\nrenderDistance:6\nsimulationDistance:5\nenableVsync:false\npauseOnLostFocus:false\nmaxFps:60\nsoundCategory_master:0.0\n')
            args=[arg for arg in args if not arg.startswith(('-Dunbox.benchmark=','-Dunbox.survivalLan=','-Dunbox.sharedCache='))]
            if '--quickPlaySingleplayer' in args:
                index=args.index('--quickPlaySingleplayer')
                del args[index:index+2]
            args[1:1]=['-Dunbox.survivalLan=guest','-Dunbox.survivalLanRoot='+str(game),'-Dunbox.sharedCache='+str(folder/'shared-cache')]
            user='UnboxLanGuest'
            raw=bytearray(hashlib.md5(('OfflinePlayer:'+user).encode()).digest())
            raw[6]=(raw[6]&15)|48
            raw[8]=(raw[8]&63)|128
            replacements={'--username':user,'--uuid':uuid.UUID(bytes=bytes(raw)).hex,'--gameDir':str(folder),'--width':'960','--height':'600'}
            for key,value in replacements.items(): args[args.index(key)+1]=value
            args=['-Xmx2048M' if arg=='-Xmx4096M' else arg for arg in args]
            (folder/'test-launch-args.json').write_text(json.dumps(args,indent=2))
            with (folder/'logs/launch.log').open('w') as guest_log:
                guest=subprocess.Popen(args,cwd=folder,stdout=guest_log,stderr=subprocess.STDOUT,start_new_session=True)
                host.wait(timeout=240)
                guest.wait(timeout=30)
            reports={}
            for role,path in [('host',game/'logs/survival-lan-host.json'),('guest',folder/'logs/survival-lan-guest.json')]:
                if not path.exists(): raise RuntimeError('Missing '+role+' report; inspect '+str(path.parent/'launch.log'))
                reports[role]=json.loads(path.read_text())
            report={'ok':host.returncode==0 and guest.returncode==0 and all(value.get('ok') is True for value in reports.values()),'loader':env.get('UNBOX_TEST_LOADER','fabric'),'version':env.get('UNBOX_MINECRAFT_VERSION','26.1'),'hostExit':host.returncode,'guestExit':guest.returncode,'reports':reports}
            (game/'logs/survival-lan.json').write_text(json.dumps(report,indent=2))
            print(json.dumps(report,indent=2),flush=True)
            if not report['ok']: raise SystemExit(1)
        finally:
            stop(guest)
            stop(host)


if __name__=='__main__':
    main()
