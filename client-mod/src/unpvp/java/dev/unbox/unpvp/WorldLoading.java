package dev.unbox.unpvp;

import com.google.common.util.concurrent.Futures;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** A local disconnect can stop the server before its second shutdown task is queued. */
public final class WorldLoading {
    private static final Field SERVER_THREAD=ReflectionHelper.findField(MinecraftServer.class,"serverThread","field_175590_aa");
    public static boolean hasTerminated(IntegratedServer server){
        try{Thread thread=(Thread)SERVER_THREAD.get(server);return thread!=null&&thread.getState()==Thread.State.TERMINATED;}
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect integrated server shutdown",e);}
    }
    public static Object awaitShutdown(Future<?> task,IntegratedServer server){
        boolean interrupted=false;
        try{
            while(!task.isDone()){
                // isServerStopped becomes true BEFORE saving. Only a terminated thread
                // proves that no queued task can run and that its shutdown path has ended.
                if(hasTerminated(server)){System.out.println("[unpvp-loading] Server thread terminated; no pending shutdown work to await");return null;}
                try{return task.get(50,TimeUnit.MILLISECONDS);}
                catch(TimeoutException e){/* Check server completion again. */}
                catch(InterruptedException e){interrupted=true;}
                catch(ExecutionException e){return Futures.getUnchecked(task);}
            }
            return Futures.getUnchecked(task);
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
}
