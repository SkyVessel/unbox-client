#!/usr/bin/env python3
import importlib.util,pathlib,socket,threading,struct,json,io,unittest
spec=importlib.util.spec_from_file_location('probe',pathlib.Path(__file__).with_name('network-probe.py'));p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class Tests(unittest.TestCase):
 def test_varint_and_truncation(self):
  for n in [0,127,128,1048576,0xffffffff]:self.assertEqual(p.number(io.BytesIO(p.varint(n))),n)
  with self.assertRaises(EOFError):p.exact(io.BytesIO(b'one'),4)
  with self.assertRaises(ValueError):p.number(io.BytesIO(b'\x80'*5))
  with self.assertRaises(ValueError):p.packet(io.BytesIO(p.varint(1048577)))
 def test_actual_socket_status_pong_and_incorrect_nonce(self):
  for wrong in [False,True]:
   with socket.socket() as server:
    server.bind(('127.0.0.1',0));server.listen();port=server.getsockname()[1]
    def host():
     with server.accept()[0] as s,s.makefile('rb') as stream:
      p.packet(stream);p.packet(stream);body=json.dumps({'version':{'protocol':777}}).encode();response=p.varint(len(body)+1+len(p.varint(len(body))))+b'\0'+p.varint(len(body))+body
      for b in response:s.sendall(bytes([b]))
      ping=p.packet(stream);self.assertEqual(p.number(ping),1);nonce=p.exact(ping,8);p.send(s,b'\1'+(b'xxxxxxxx' if wrong else nonce))
    worker=threading.Thread(target=host);worker.start()
    if wrong:
     with self.assertRaises(ValueError):p.sample('127.0.0.1',port)
    else:
     r=p.sample('127.0.0.1',port);self.assertTrue(r['ok']);self.assertEqual(r['protocol'],777);self.assertGreaterEqual(r['pongMs'],0)
    worker.join(5);self.assertFalse(worker.is_alive())
 def test_metrics_count_failures_without_inventing_packet_loss(self):
  s=p.summarize([{'ok':True,'pongMs':10},{'ok':False},{'ok':True,'pongMs':30}]);self.assertEqual(s['successRate'],2/3);self.assertEqual(s['medianMs'],20);self.assertEqual(s['p95Ms'],30)
if __name__=='__main__':unittest.main()
