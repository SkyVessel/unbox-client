package dev.unbox.client;
public class PerformanceSamplesTest {
 public static void main(String[] args)throws Exception{
  var s=new PerformanceSamples();if(s.snapshot().calls()!=0||s.snapshot().p95Ms()!=0)throw new AssertionError("empty sample");
  for(int i=1;i<=100;i++)s.add(i*1_000_000L);
  var a=s.snapshot();if(a.calls()!=100||a.averageMs()!=50.5||a.p50Ms()!=50||a.p95Ms()!=95||a.p99Ms()!=99)throw new AssertionError(a);
  for(int i=0;i<4096;i++)s.add(2_000_000);
  a=s.snapshot();if(a.calls()!=4196||a.recentSamples()!=4096||a.p99Ms()!=2||a.maxMs()!=100)throw new AssertionError(a);
  Thread one=Thread.ofVirtual().start(()->{for(int i=0;i<10000;i++)s.add(1);});Thread two=Thread.ofVirtual().start(()->{for(int i=0;i<10000;i++)s.add(1);});one.join();two.join();if(s.snapshot().calls()!=24196)throw new AssertionError("concurrent samples lost");
  System.out.println("PASS: exact percentiles, bounded history, lifetime totals and concurrent sampling");
 }
}
