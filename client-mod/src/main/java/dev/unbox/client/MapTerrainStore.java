package dev.unbox.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Client-thread tile cache; disk work is serialized, bounded, and never touches the live world. */
final class MapTerrainStore {
    private static final int LIMIT=16384, MAGIC=0x55424d31;
    private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("unbox-terrain-io",0).factory());
    private static final Map<Path,int[]> latestWrites=new ConcurrentHashMap<>();
    private static final Set<MapTerrainStore> pendingStores=ConcurrentHashMap.newKeySet();
    private record Read(long key,int[] pixels) {}
    static final class Tile { final int[] pixels=new int[256]; boolean loaded,dirty; long revision; }
    private long revision;
    private final Path directory;
    private final LinkedHashMap<Long,Tile> tiles=new LinkedHashMap<>(256,.75f,true);
    private final Set<Long> reading=new HashSet<>();
    private final ConcurrentLinkedQueue<Read> completed=new ConcurrentLinkedQueue<>();
    private final Map<Long,int[]> writes=new LinkedHashMap<>();
    private boolean writing,closed;
    private int readBudget;
    private volatile int pendingWrites;

    MapTerrainStore(String world,String dimension){this(storageRoot(),world,dimension);}
    MapTerrainStore(Path root,String world,String dimension){directory=root.resolve(hash(world)).resolve(hash(dimension));}
    private static Path storageRoot(){
        String shared=System.getProperty("unbox.sharedCache","");
        return shared.isEmpty()?Platform.gameDir().resolve("unbox-maps"):Path.of(shared).resolveSibling("maps");
    }
    private static String hash(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();}
    static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private Path file(long key){return directory.resolve((int)(key>>32)+"_"+(int)key+".tile");}
    void tick(){
        readBudget=32;
        for(int n=0;n<64;n++){
            Read result=completed.poll();if(result==null)break;reading.remove(result.key);
            Tile tile=tiles.get(result.key);if(tile==null)continue;
            // Captured cells win over an older disk snapshot returned asynchronously.
            if(result.pixels!=null)for(int i=0;i<256;i++)if(tile.pixels[i]==0)tile.pixels[i]=result.pixels[i];
            tile.loaded=true;tile.revision=++revision;
        }
    }
    private Tile tile(long key){
        Tile tile=tiles.get(key);
        if(tile==null){tile=new Tile();tiles.put(key,tile);}
        if(!tile.loaded&&!reading.contains(key)&&readBudget>0&&reading.size()<64){
            readBudget--;reading.add(key);
            IO.execute(()->{int[] pixels=null;try{Path file=file(key);if(Files.size(file)==1032)try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){if(in.readInt()==MAGIC&&in.readInt()==256){pixels=new int[256];for(int i=0;i<256;i++)pixels[i]=in.readInt();}}}catch(IOException ignored){}completed.add(new Read(key,pixels));});
        }
        while(tiles.size()>LIMIT){var iterator=tiles.entrySet().iterator();var eldest=iterator.next();if(eldest.getValue().dirty)save(eldest.getKey(),eldest.getValue());iterator.remove();}
        return tile;
    }
    Tile chunk(int x,int z){return tile(key(x,z));}
    int color(int x,int z){return tile(key(x>>4,z>>4)).pixels[(z&15)*16+(x&15)];}
    void capture(int x,int z,int color){if(pendingWrites>=LIMIT)return;Tile tile=tile(key(x>>4,z>>4));int cell=(z&15)*16+(x&15);if(tile.pixels[cell]!=color){tile.pixels[cell]=color;tile.dirty=true;tile.revision=++revision;}}
    void flush(int budget){for(var entry:tiles.entrySet())if(entry.getValue().dirty){save(entry.getKey(),entry.getValue());if(--budget==0)break;}}
    void close(){if(closed)return;closed=true;flush(LIMIT);tiles.clear();}
    static void finishWrites(){
        try{IO.submit(()->{}).get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException e){System.err.println("[Unbox] Some explored map tiles are still waiting to save");}
        if(!pendingStores.isEmpty())System.err.println("[Unbox] Explored map writes remain pending (disk error or timeout)");
    }
    private void save(long key,Tile tile){
        // Do not overwrite unseen cells before the asynchronous read has merged them.
        // The writer also merges on disk, covering disconnects and cache eviction mid-read.
        synchronized(writes){
            int[] snapshot=tile.pixels.clone();
            latestWrites.compute(file(key),(path,pending)->{if(pending!=null)for(int i=0;i<256;i++)if(snapshot[i]==0)snapshot[i]=pending[i];return snapshot;});
            writes.put(key,snapshot);pendingWrites=writes.size();tile.dirty=false;if(writing)return;writing=true;pendingStores.add(this);}
        IO.execute(new Runnable(){public void run(){
            for(;;){long id;int[] pixels;synchronized(writes){if(writes.isEmpty()){writing=false;pendingStores.remove(MapTerrainStore.this);return;}var iterator=writes.entrySet().iterator();var next=iterator.next();id=next.getKey();pixels=latestWrites.get(file(id));
                    // A later store for the same world may already have saved a newer snapshot.
                    if(pixels==null){iterator.remove();pendingWrites=writes.size();continue;}}
                int[] snapshot=pixels;pixels=pixels.clone();
                try{Files.createDirectories(directory);Path target=file(id),tmp=target.resolveSibling(target.getFileName()+".tmp");
                    if(Files.exists(target)&&Files.size(target)==1032)try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(target)))){if(in.readInt()==MAGIC&&in.readInt()==256)for(int i=0;i<256;i++){int old=in.readInt();if(pixels[i]==0)pixels[i]=old;}}
                    try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){out.writeInt(MAGIC);out.writeInt(256);for(int pixel:pixels)out.writeInt(pixel);}
                    try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
                    latestWrites.remove(file(id),snapshot);
                    synchronized(writes){if(!latestWrites.containsKey(file(id)))writes.remove(id);pendingWrites=writes.size();}
                }catch(IOException e){
                    // Keep the failed snapshot (or a newer replacement) queued, including after close.
                    System.err.println("[Unbox] Explored map tile remains queued; retrying in 5 seconds");
                    // A single damaged target must not indefinitely block other tiles in this store.
                    synchronized(writes){int[] failed=writes.remove(id);if(failed!=null)writes.put(id,failed);}
                    IO.schedule(this,5,TimeUnit.SECONDS);return;
                }
            }
        }});
    }
}
