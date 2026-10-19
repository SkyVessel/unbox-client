package dev.unbox.client;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import java.io.IOException;

/** V1 remains the fallback; op 3 is advertised only by hosts with bounded batch support. */
final class EnvironmentTransfer {
    static final int CHUNK=262144, BATCH=3*CHUNK;
    static int size(int op)throws IOException{if(op==1)return CHUNK;if(op==3)return BATCH;throw new IOException("Invalid transfer operation");}
    static boolean batch(JsonObject manifest){return manifest.has("transferBatch")&&manifest.get("transferBatch").getAsInt()==BATCH;}
    static JsonObject advertise(JsonObject manifest){JsonObject copy=manifest.deepCopy();copy.addProperty("transferBatch",BATCH);return copy;}
    static byte[] readChunk(FriendlyByteBuf in,int op,boolean batch,String expectedHash,long expectedOffset,long fileSize)throws IOException{
        if(op==3&&!batch)throw new IOException("Unnegotiated batch");
        String hash=in.readUtf(64);long offset=in.readLong();byte[] bytes=in.readByteArray(size(op));
        if(!hash.equals(expectedHash)||offset!=expectedOffset||bytes.length==0||offset+bytes.length>fileSize||in.isReadable())throw new IOException("Invalid transfer chunk");
        return bytes;
    }
}
