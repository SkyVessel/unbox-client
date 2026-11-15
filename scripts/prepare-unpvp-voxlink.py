#!/usr/bin/env python3
"""Build Java 8 equivalents from the shared, tested VoxLink sources. Modern sources stay unchanged."""
from pathlib import Path
import re,shutil,subprocess,os,zipfile
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'.cache/unpvp-social/voxlink-java8';out.mkdir(parents=True,exist_ok=True)
def records(s):
 def sub(m):
  visibility,name,fields=m.groups();args=[f.strip().rsplit(' ',1) for f in fields.split(',')]
  declarations=''.join('final '+t+' '+n+';' for t,n in args);assign=''.join('this.'+n+'='+n+';' for _,n in args);getters=''.join('public '+t+' '+n+'(){return '+n+';}' for t,n in args)
  eq='&&'.join('java.util.Objects.equals('+n+',v.'+n+')' for _,n in args)
  return visibility+' static final class '+name+'{'+declarations+'public '+name+'('+fields+'){'+assign+'}'+getters+'@Override public boolean equals(Object other){if(!(other instanceof '+name+'))return false;'+name+' v=('+name+')other;return '+eq+';}@Override public int hashCode(){return java.util.Objects.hash('+','.join(n for _,n in args)+');}}'
 return re.sub(r'(public|private) record (\w+)\(([^)]*)\)\s*\{\s*\}',sub,s)
def base(s):
 s=s.replace('is.readAllBytes()','org.apache.commons.io.IOUtils.toByteArray(is)').replace('CompletableFuture.failedFuture(', 'failedFuture(')
 if 'failedFuture(' in s:s=s.replace('public class UdpHolePuncher {','public class UdpHolePuncher {\n   private static <T> CompletableFuture<T> failedFuture(Throwable t){CompletableFuture<T> f=new CompletableFuture<>();f.completeExceptionally(t);return f;}')
 return records(s).replace('List.of(','java.util.Arrays.asList(').replace('.toList()', '.collect(java.util.stream.Collectors.toList())').replace('org.slf4j.LoggerFactory','org.apache.logging.log4j.LogManager').replace('LoggerFactory.getLogger','LogManager.getLogger').replace('org.slf4j.Logger','org.apache.logging.log4j.Logger')
for p in (ROOT/'ports/voxlink/src').rglob('*.java'):
 d=out/p.relative_to(ROOT/'ports/voxlink/src');d.parent.mkdir(parents=True,exist_ok=True);d.write_text(base(p.read_text()))
s=base((ROOT/'client-mod/src/main/java/dev/unbox/client/VoxLinkTransport.java').read_text()).replace('package dev.unbox.client;','package dev.unbox.unpvp;')
s=s.replace('List.copyOf(stunServers)','java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(stunServers))').replace('HexFormat.of().parseHex(secret)','unhex(secret)').replace('var t=new Thread','Thread t=new Thread').replace('var mapping=','CompletableFuture<UPnPManager.UPnPResult> mapping=').replace('var m=mapping.get','UPnPManager.UPnPResult m=mapping.get').replace('for(var value:','for(JsonElement value:').replace('var c=value.getAsJsonObject()','JsonObject c=value.getAsJsonObject()').replace('var transport=new ReliableUdpTransport','ReliableUdpTransport transport=new ReliableUdpTransport').replace('for(var p:punches)','for(UdpHolePuncher p:punches)').replace('var c=new JsonObject()','JsonObject c=new JsonObject()').replace('var out=new ArrayList<String>()','List<String> out=new ArrayList<String>()').replace('for(var ni:','for(NetworkInterface ni:').replace('for(var ip:','for(InetAddress ip:')
s=re.sub(r'Thread.ofVirtual\(\).name\("([^"]+)"\).start\(',r'spawn("\1",',s)
s=s.replace('if(link.owner instanceof Socket s)s.setSoTimeout(0);','if(link.owner instanceof Socket)((Socket)link.owner).setSoTimeout(0);').replace('in.readNBytes(32)','read32(in)').replace('game.getInputStream().transferTo(link.out)','pump(game.getInputStream(),link.out)').replace('link.in.transferTo(game.getOutputStream())','pump(link.in,game.getOutputStream())')
s=s.replace('public final class VoxLinkTransport {','''public final class VoxLinkTransport {
 static Thread spawn(String name,Runnable run){Thread t=new Thread(run,name);t.setDaemon(true);t.start();return t;}
 static byte[] unhex(String s){if(!s.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 static byte[] read32(DataInputStream in)throws IOException{byte[] b=new byte[32];in.readFully(b);return b;}
 static void pump(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)if(n>0)out.write(b,0,n);}
''')
p=out/'dev/unbox/unpvp/VoxLinkTransport.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
# The Minecraft adapter is small and version-specific; compile it with the core, not the dependency archive.
classes=out/'classes';classes.mkdir(exist_ok=True)
cp=os.pathsep.join(str(p) for p in (ROOT/'.cache/unpvp-build/libraries').rglob('*.jar'))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,out.rglob('*.java'))],check=True)
with zipfile.ZipFile(ROOT/'src-tauri/resources/unpvp-network.jar','a',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,str(p.relative_to(classes)))
 for p in out.rglob('*.java'):z.write(p,'META-INF/unbox-network/voxlink-src/'+str(p.relative_to(out)))
 for f in ['LICENSE','PORT.md','upstream.json']:z.write(ROOT/'ports/voxlink'/f,'META-INF/unbox-network/voxlink/'+f)
 z.write(__file__,'META-INF/unbox-network/prepare-unpvp-voxlink.py')
