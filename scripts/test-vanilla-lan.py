#!/usr/bin/env python3
"""Mojang's unmodified client joins an ordinary Unbox LAN world; isolated offline test accounts."""
import os,pathlib,subprocess,json,time,uuid,shutil,sys
root=pathlib.Path(__file__).resolve().parents[1];os.chdir(root)
run=sys.argv[2];version=os.environ.get('UNBOX_MINECRAFT_VERSION','26.1.2');game=root/'.cache/benchmarks'/run
host=subprocess.Popen(['python3','scripts/benchmark.py','optimized',sys.argv[1],run],env={**os.environ,'UNBOX_VANILLA_INTEROP':'1','UNBOX_MINECRAFT_VERSION':version},stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);guest=None
try:
 for _ in range(180):
  if (game/'logs/vanilla-port.txt').exists():break
  if host.poll() is not None:raise RuntimeError(host.stdout.read())
  time.sleep(1)
 else:raise RuntimeError('Host LAN port missing')
 port=(game/'logs/vanilla-port.txt').read_text();args=json.loads((game/'test-launch-args.json').read_text());cp=args[args.index('-cp')+1].split(':')
 # Retain only Mojang manifest dependencies plus its verified, unmodified client JAR.
 runtime=pathlib.Path.home()/'Library/Application Support/dev.unbox.client/runtime'
 meta=json.loads((root/f'.cache/{version}.json').read_text());vanilla={str(runtime/'libraries'/v['downloads']['artifact']['path']) for v in meta['libraries'] if 'artifact' in v.get('downloads',{})}
 cp=[p for p in cp if p in vanilla]+[str(root/f'.cache/client-{version}.jar')]
 folder=game/'vanilla-guest';folder.mkdir();(folder/'options.txt').write_text('onboardAccessibility:false\nrenderDistance:6\nenableVsync:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n')
 user='VanillaGuest';import hashlib
 raw=bytearray(hashlib.md5(('OfflinePlayer:'+user).encode()).digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
 args=[args[0],'-XstartOnFirstThread','-Xmx2G','-cp',':'.join(cp),'net.minecraft.client.main.Main','--username',user,'--uuid',uuid.UUID(bytes=bytes(raw)).hex,'--accessToken','0','--version',version,'--gameDir',str(folder),'--assetsDir',str(runtime/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','960','--height','600','--quickPlayMultiplayer','127.0.0.1:'+port]
 with (folder/'launch.log').open('w') as log:
  guest=subprocess.Popen(args,stdout=log,stderr=log,cwd=folder)
  host.wait(timeout=160)
  result=game/'logs/vanilla-interop.json'
  if not result.exists():raise RuntimeError('No interoperability result; inspect host and vanilla guest logs')
  if 'UNBOX_VANILLA_INTEROP_OK' not in (folder/'launch.log').read_text():raise RuntimeError('Vanilla client did not acknowledge world chat')
  print(result.read_text());assert not (folder/'mods').exists()
finally:
 if host.poll() is None:host.terminate();host.wait(timeout=30)
 if guest and guest.poll() is None:guest.terminate();guest.wait(timeout=30)
