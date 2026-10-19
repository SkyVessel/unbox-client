package dev.unbox.client;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
final class TransferChecks {
 static void verify(List<String> checks)throws Exception{
  var legacy=new com.google.gson.JsonObject();var advertised=EnvironmentTransfer.advertise(legacy);
  if(EnvironmentTransfer.batch(legacy)||!EnvironmentTransfer.batch(advertised)||legacy.has("transferBatch"))throw new AssertionError("Capability negotiation changed legacy manifest");
  byte[] source=new byte[2*1024*1024+17];new Random(41).nextBytes(source);String hash="a".repeat(64);
  int[] counts=new int[2];
  for(int op:new int[]{1,3}){
   var received=new java.io.ByteArrayOutputStream();int offset=0;
   while(offset<source.length){int length=Math.min(EnvironmentTransfer.size(op),source.length-offset);var b=new FriendlyByteBuf(Unpooled.buffer());try{
    b.writeUtf(hash,64);b.writeLong(offset);b.writeByteArray(Arrays.copyOfRange(source,offset,offset+length));
    if(b.readableBytes()+1>=1048576)throw new AssertionError("Login payload too large");
    received.write(EnvironmentTransfer.readChunk(b,op,op==3,hash,offset,source.length));offset+=length;counts[op==1?0:1]++;
   }finally{b.release();}}
   if(!Arrays.equals(source,received.toByteArray()))throw new AssertionError("Chunked transfer corrupted bytes");
  }
  if(counts[1]>=counts[0])throw new AssertionError("Batch did not reduce round trips");
  for(int test=0;test<5;test++){var b=new FriendlyByteBuf(Unpooled.buffer());try{
   b.writeUtf(test==0?"b".repeat(64):hash,64);b.writeLong(test==1?1:0);b.writeByteArray(new byte[test==2?EnvironmentTransfer.BATCH+1:10]);if(test==3)b.writeByte(1);
   boolean rejected=false;try{EnvironmentTransfer.readChunk(b,3,test!=4,hash,0,source.length);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Malformed transfer accepted: "+test);
  }finally{b.release();}}
  checks.add("Legacy 256 KiB and negotiated 768 KiB transfer codecs preserve bytes (9 vs 3 replies)");
  checks.add("Transfer rejects wrong hash/offset, oversized/trailing data and unnegotiated batch");
 }
}
