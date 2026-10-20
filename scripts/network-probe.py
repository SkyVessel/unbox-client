#!/usr/bin/env python3
"""Measure a consenting host's Minecraft status/pong path, not login or P2P success.
Run on another network: python3 scripts/network-probe.py host.e4mc.link --count 30
No accounts, passwords or join secrets are needed or written. Python 3.10+, stdlib only.
"""
import argparse, io, json, math, pathlib, socket, statistics, struct, time

def varint(n):
    n &= 0xffffffff
    data=bytearray()
    while True:
        b=n&127;n>>=7;data.append(b|(128 if n else 0))
        if not n:return bytes(data)

def exact(stream,n):
    data=bytearray()
    while len(data)<n:
        chunk=stream.read(n-len(data))
        if not chunk:raise EOFError('Peer closed before a complete packet')
        data.extend(chunk)
    return bytes(data)

def number(stream):
    n=0
    for shift in range(0,35,7):
        b=exact(stream,1)[0];n|=(b&127)<<shift
        if b<128:return n
    raise ValueError('Invalid VarInt')

def packet(stream):
    n=number(stream)
    if n>1048576:raise ValueError('Status packet exceeds 1 MiB')
    return io.BytesIO(exact(stream,n))

def send(sock,payload):sock.sendall(varint(len(payload))+payload)

def sample(host,port,timeout=5,protocol=775):
    start=time.perf_counter()
    with socket.create_connection((host,port),timeout) as sock:
        tcp=(time.perf_counter()-start)*1000
        sock.settimeout(timeout)
        name=host.encode('utf8')
        send(sock,varint(0)+varint(protocol)+varint(len(name))+name+struct.pack('>H',port)+varint(1))
        send(sock,b'\0')
        with sock.makefile('rb') as incoming:
            reply=packet(incoming)
            if number(reply)!=0:raise ValueError('Not a Minecraft status response')
            status=json.loads(exact(reply,number(reply)))
            if reply.read(1):raise ValueError('Trailing status bytes')
            status_ms=(time.perf_counter()-start)*1000
            payload=struct.pack('>q',time.monotonic_ns());ping=time.perf_counter();send(sock,b'\1'+payload)
            pong=packet(incoming)
            if number(pong)!=1 or exact(pong,8)!=payload or pong.read(1):raise ValueError('Pong does not match this probe')
            return {'ok':True,'tcpConnectMs':round(tcp,3),'statusReadyMs':round(status_ms,3),'pongMs':round((time.perf_counter()-ping)*1000,3),'protocol':status.get('version',{}).get('protocol')}

def summarize(samples):
    values=sorted(s['pongMs'] for s in samples if s['ok'])
    return {'attempts':len(samples),'successfulStatusPongs':len(values),'successRate':len(values)/len(samples) if samples else 0,'medianMs':statistics.median(values) if values else None,'p95Ms':values[max(0,math.ceil(len(values)*.95)-1)] if values else None,'maxMs':max(values) if values else None}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('host');p.add_argument('--port',type=int,default=25565);p.add_argument('--count',type=int,default=30);p.add_argument('--protocol',type=int,default=775,help='Minecraft protocol version (26.1 defaults to 775)');p.add_argument('--interval',type=float,default=1);p.add_argument('--output',type=pathlib.Path,default=pathlib.Path('unbox-network-probe.json'));p.add_argument('--label',default='remote-network');a=p.parse_args()
    if not 1<=a.count<=300 or not .2<=a.interval<=60 or not 1<=a.port<=65535:p.error('Count 1–300, interval 0.2–60s, port 1–65535')
    samples=[]
    for i in range(a.count):
        try:r=sample(a.host,a.port,protocol=a.protocol)
        except (OSError,EOFError,ValueError) as e:r={'ok':False,'error':type(e).__name__}
        samples.append(r);print(f'{i+1}/{a.count}: '+(str(r["pongMs"])+" ms" if r['ok'] else r['error']),flush=True)
        if i+1<a.count:time.sleep(a.interval)
    report={'schema':1,'label':a.label,'measuredAt':int(time.time()*1000),'scope':'TCP status/pong reachability; not authenticated join, packet loss, or P2P rate','summary':summarize(samples),'samples':samples}
    a.output.write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report['summary'],indent=2));print(f'Saved {a.output}')
if __name__=='__main__':main()
