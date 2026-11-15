#!/usr/bin/env python3
"""Two isolated Forge clients, synthetic friend mailboxes; never contacts friend service."""
from pathlib import Path
import json,time,subprocess,sys,socket,struct,os,signal,hashlib
R=Path(__file__).resolve().parents[1];tag=sys.argv[1];base=R/'.cache/unpvp-tests';ps=[]
def write(p,v):
 t=p.with_suffix('.tmp');t.write_text(json.dumps(v));t.replace(p)
def read(p):
 try:return json.loads(p.read_text())
 except (OSError,ValueError):return {}
ids=['10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'];uuids=['00000000000000000000000000000001','00000000000000000000000000000002'];names=['SocialHost','SocialGuest'];invites=[];signals={};seen=set()
try:
 for role in ['host','guest']:
  ps.append(subprocess.Popen([sys.executable,str(R/'scripts/test-unpvp.py'),tag+'-'+role,'--social',role,'--width','960','--height','600','--render-distance','3','--mods',str(base/'native-retina/mods')]+(['--reject'] if '--badsecret' in sys.argv else []),stdout=subprocess.DEVNULL))
 deadline=time.monotonic()+165
 while time.monotonic()<deadline and any(p.poll() is None for p in ps):
  for n,role in enumerate(['host','guest']):
   d=base/(tag+'-'+role)
   if not d.exists():continue
   now=int(time.time()*1000);w=read(d/'unbox-world.json');c=read(d/'unbox-social-command.json')
   if c and c.get('requestId') not in seen:
    seen.add(c['requestId']);op=c.get('op')
    if op=='send':
     invites=[dict(id='20000000-0000-0000-0000-000000000001',sender=ids[0],senderName=names[0],receiver=ids[1],expires=now+300000,room=w['room'],join_secret=w['grants'][ids[1]])]
     if '--p2p' in sys.argv or '--relay' in sys.argv:invites[0]['room']['lan']=[]
     if '--relay' in sys.argv:invites[0]['room']['network']='';invites[0]['relay_requested']=1
     if '--badsecret' in sys.argv:
      # Same routing hint, different secret: exercise mutual proof, not just hint rejection.
      target=hashlib.sha256(bytes.fromhex(invites[0]['join_secret'])).digest()[:2];counter=0
      while True:
       candidate=counter.to_bytes(32,'big');counter+=1
       if hashlib.sha256(candidate).digest()[:2]==target and candidate.hex()!=invites[0]['join_secret']:break
      invites[0]['join_secret']=candidate.hex()
     # Ordinary clients and spoofed host names must not bypass the invitation gate.
     def vi(n):
      b=bytearray()
      while n>127:b.append((n&127)|128);n>>=7
      b.append(n);return bytes(b)
     def text(v):b=v.encode();return vi(len(b))+b
     hint=int.from_bytes(hashlib.sha256(bytes.fromhex(w['grants'][ids[1]])).digest()[:2],'big')
     for wireport,name in [(w['room']['port'], 'Intruder'),(hint, 'SocialHost')]:
      with socket.create_connection(('127.0.0.1',w['room']['port']),timeout=4) as client:
       handshake=b'\x00'+vi(47)+text('localhost\0FML\0')+struct.pack('>H',wireport)+b'\x02'
       login=b'\x00'+text(name);client.sendall(vi(len(handshake))+handshake+vi(len(login))+login)
       def recv(n):
        data=b''
        while len(data)<n:
         more=client.recv(n-len(data))
         if not more:break
         data+=more
        return data
       size=0;shift=0
       while True:
        octet=recv(1)[0];size|=(octet&127)<<shift;shift+=7
        if not octet&128:break
       got=recv(size)
       if b'invitation' not in got:raise AssertionError('Unexpected login response: '+repr(got[:300]))
     print('PASS uninvited client and spoofed host rejected',flush=True)
    write(d/'unbox-social-ack.json',dict(requestId=c['requestId'],ok=True,at=now))
   if n==0 and invites and w.get('room',{}).get('relay'):invites[0]['room']['relay']=w['room']['relay']
   for signal in w.get('routeSignals',[]):
    for i in invites:
     if signal['id']==i['id']:
      t=i['room'].setdefault('transport',{'attempt':signal['attempt'],'created':now})
      if t['attempt']==signal['attempt']:t[signal['side']]=signal['offer']
   v=dict(connected=True,updated=now,self=dict(id=ids[n],uuid=uuids[n],name=names[n],code='0123456789AB'),friends=[dict(id=ids[1-n],uuid=uuids[1-n],name=names[1-n],accepted=1,online=True,state='In world')],invites=invites)
   write(d/'unbox-social-view.json',v)
   if '--badsecret' in sys.argv and role=='guest' and (d/'social-test-result.txt').exists():write(base/(tag+'-host')/'social-test-finish.json',{})
  time.sleep(.15)
 for role in ['host','guest']:
  d=base/(tag+'-'+role);result=(d/'social-test-result.txt').read_text() if (d/'social-test-result.txt').exists() else 'FAIL missing result';print(role,result)
  if not result.startswith('PASS') or ('revoked' not in result and 'revocation' not in result and not ('--badsecret' in sys.argv and 'rejected' in result)):raise SystemExit(1)
finally:
 for p in ps:
  if p.poll() is None:
   p.terminate()
   d=base/(tag+'-'+(['host','guest'][ps.index(p)]));meta=read(d/'test-process.json')
   if meta.get('pid'):
    try:os.kill(meta['pid'],signal.SIGTERM)
    except ProcessLookupError:pass
