package dev.unbox.client;

import java.util.Arrays;

/** Bounded recent percentiles plus whole-recording totals. Nanoseconds in, milliseconds out. */
final class PerformanceSamples {
    private final long[] recent=new long[4096];
    private int cursor,size;
    private long count,total,max;
    synchronized void add(long nanos){if(nanos<0)return;recent[cursor++]=nanos;cursor%=recent.length;size=Math.min(size+1,recent.length);count++;total+=nanos;max=Math.max(max,nanos);}
    synchronized Summary snapshot(){long[] values=Arrays.copyOf(recent,size);Arrays.sort(values);return new Summary(count,total/1e6,count==0?0:total/1e6/count,percentile(values,.50),percentile(values,.95),percentile(values,.99),max/1e6,size);}
    private static double percentile(long[] values,double fraction){return values.length==0?0:values[Math.max(0,(int)Math.ceil(values.length*fraction)-1)]/1e6;}
    record Summary(long calls,double totalMs,double averageMs,double p50Ms,double p95Ms,double p99Ms,double maxMs,int recentSamples){}
}
