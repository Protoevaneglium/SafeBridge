package com.stange.safebridge.live;
import java.io.IOException;
public final class LiveRetryPolicyTest {
 private static int checks;
 private static void check(boolean condition){if(!condition)throw new AssertionError("retry policy check "+(checks+1));checks++;}
 public static void main(String[] args)throws Exception{
  LiveRetryPolicy p=new LiveRetryPolicy();p.begin(1000);check(p.due(1000));
  check(p.claimSample(1000,1000));check(!p.claimSample(1000,16000));check(!p.claimSample(999,16000));
  long now=2000;long[] delays={1000,2000,4000,8000,16000,32000,60000,60000};
  for(long delay:delays){check(p.failed(new LiveCoordinator.ConnectionFailure(),now)==LiveRetryPolicy.Action.RETRY_FRESH_LATEST);check(p.nextAttemptAt()==now+delay);check(!p.due(now+delay-1));check(p.due(now+delay));now+=delay;}
  p.connectionRecovered(now);check(p.failures()==0);check(p.due(now));
  check(p.claimSample(now,now));p.delivered(now);check(!p.due(now+14999));check(p.due(now+15000));
  check(p.failed(new LiveCoordinator.StaleFixFailure(),now+15000)==LiveRetryPolicy.Action.DROP_SAMPLE);check(p.due(now+16000));
  check(!p.hasNewFreshSample(now,now+16000));check(!p.hasNewFreshSample(now+1,now+31002));check(!p.hasNewFreshSample(now+32000,now+31000));check(p.hasNewFreshSample(now+31000,now+31000));
  for(int status:new int[]{400,409}){p.begin(now);check(p.failed(new LiveCoordinator.HttpFailure(status,"stale_point"),now)==LiveRetryPolicy.Action.DROP_SAMPLE);check(p.due(now+1000));}
  for(int status:new int[]{408,425,429,500,503,599}){p.begin(now);check(p.failed(new LiveCoordinator.HttpFailure(status,"temporary"),now)==LiveRetryPolicy.Action.RETRY_FRESH_LATEST);}
  for(IOException error:new IOException[]{new IOException("protocol/store"),new LiveCoordinator.HttpFailure(400,"bad_json"),new LiveCoordinator.HttpFailure(401,"unauthorized"),new LiveCoordinator.HttpFailure(403,"admission_rejected"),new LiveCoordinator.HttpFailure(409,"session_ended")}){p.begin(now);check(p.failed(error,now)==LiveRetryPolicy.Action.STOP_LOCAL);check(!p.due(now+1000000));}
  p.begin(now);p.failed(new LiveCoordinator.ConnectionFailure(),now);p.stop();check(!p.due(Long.MAX_VALUE));check(!p.claimSample(now+1,now+1));check(p.failed(new LiveCoordinator.ConnectionFailure(),now)==LiveRetryPolicy.Action.STOP_LOCAL);
  check(LiveRetryPolicy.fresh(100,30100));check(!LiveRetryPolicy.fresh(100,30101));check(!LiveRetryPolicy.fresh(101,100));
  check(LiveRetryPolicy.backoffMillis(1000000)==60000);p.begin(Long.MAX_VALUE-100);p.failed(new LiveCoordinator.ConnectionFailure(),Long.MAX_VALUE-100);check(p.nextAttemptAt()==Long.MAX_VALUE);
  System.out.println("PASS "+checks+" executed retry-policy cases: transient continuation, bounded backoff, fresh newest-only samples, stale drop, fatal stop, stop invalidation");
 }
}
