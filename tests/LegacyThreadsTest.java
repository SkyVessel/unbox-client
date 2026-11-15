package dev.unbox.client;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyThreadsTest {
    public static void main(String[] args)throws Exception {
        var factory=LegacyThreads.factory("unbox-test-");
        Thread first=factory.newThread(()->{}),second=factory.newThread(()->{});
        check(first.isDaemon()&&second.isDaemon(),"workers do not keep the client process alive");
        check(first.getName().equals("unbox-test-0")&&second.getName().equals("unbox-test-1"),"executor workers retain distinct names");
        var started=new CountDownLatch(1);
        var interrupted=new AtomicBoolean();
        Thread worker=LegacyThreads.start("unbox-test-cancel",()->{
            started.countDown();
            try {new CountDownLatch(1).await();}
            catch(InterruptedException expected){interrupted.set(true);}
        });
        check(started.await(2,TimeUnit.SECONDS)&&worker.isDaemon(),"direct background tasks start as daemon threads");
        worker.interrupt();worker.join(2000);
        check(interrupted.get()&&!worker.isAlive(),"interrupt and join still cancel direct tasks");
        var executor=Executors.newSingleThreadExecutor(factory);
        try {
            check(executor.submit(()->Thread.currentThread().getName()).get(2,TimeUnit.SECONDS).equals("unbox-test-2"),"existing executors run through the Java 17 factory");
        }finally{executor.shutdownNow();}
        check(executor.awaitTermination(2,TimeUnit.SECONDS),"executor shutdown releases its worker");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
}
