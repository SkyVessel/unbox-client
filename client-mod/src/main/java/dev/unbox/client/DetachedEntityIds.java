package dev.unbox.client;

/** Render-only entities never enter the world/network entity registry. 26.3 requires a nonzero ID. */
final class DetachedEntityIds {
    private static final java.util.concurrent.atomic.AtomicInteger NEXT=new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);
    static int next(){return NEXT.getAndIncrement();}
}
