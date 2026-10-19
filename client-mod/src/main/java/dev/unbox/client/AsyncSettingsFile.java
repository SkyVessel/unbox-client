package dev.unbox.client;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Coalesced immutable snapshots: live world objects never leave the client thread. */
final class AsyncSettingsFile {
    private final Map<Path,String> pending=new LinkedHashMap<>();
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("unbox-world-settings",0).factory());
    private boolean running;
    synchronized void save(Path file,String value){pending.put(file,value);if(!running){running=true;io.execute(this::drain);}}
    synchronized void finish(){
        long deadline=System.nanoTime()+3_000_000_000L;
        while(!pending.isEmpty()){
            long remaining=deadline-System.nanoTime();if(remaining<=0)break;
            try{TimeUnit.NANOSECONDS.timedWait(this,remaining);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
        if(!pending.isEmpty())System.err.println("[Unbox] Some world settings could not yet be saved");
    }
    private void drain(){
        Path file;String value;
        synchronized(this){if(pending.isEmpty()){running=false;return;}var entry=pending.entrySet().iterator().next();file=entry.getKey();value=entry.getValue();}
        try{
            Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,value);
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            synchronized(this){pending.remove(file,value);if(pending.isEmpty())notifyAll();}
            io.execute(this::drain);
        }catch(IOException e){System.err.println("[Unbox] World settings remain queued; retrying in 5 seconds: "+e.getClass().getSimpleName());io.schedule(this::drain,5,TimeUnit.SECONDS);}
    }
}
