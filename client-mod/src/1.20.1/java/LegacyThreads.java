package dev.unbox.client;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/** Java 17 implementation of the existing named background workers. */
public final class LegacyThreads {
    private LegacyThreads() {}

    private static Thread create(String name,Runnable work) {
        Thread thread=new Thread(work,name);
        thread.setDaemon(true);
        return thread;
    }

    public static Thread start(String name,Runnable work) {
        Thread thread=create(name,work);
        thread.start();
        return thread;
    }

    public static ThreadFactory factory(String prefix) {
        AtomicLong sequence=new AtomicLong();
        return work->create(prefix+sequence.getAndIncrement(),work);
    }
}
