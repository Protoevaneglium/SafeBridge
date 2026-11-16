package com.stange.safebridge.live;

import java.io.IOException;

/** Android-free sender policy. Keeps consent separate from delivery and stores no position. */
public final class LiveRetryPolicy {
    public enum Action { RETRY_FRESH_LATEST, DROP_SAMPLE, STOP_LOCAL }
    public static final long MAX_SAMPLE_AGE_MS=30000L;
    private boolean enabled;
    private int failures;
    private long nextAttemptAt,lastDispatchedSample=-1L;
    public synchronized void begin(long now){enabled=true;failures=0;nextAttemptAt=now;lastDispatchedSample=-1L;}
    public synchronized void stop(){enabled=false;failures=0;nextAttemptAt=Long.MAX_VALUE;lastDispatchedSample=-1L;}
    public synchronized boolean due(long now){return enabled && now>=nextAttemptAt;}
    public synchronized boolean hasNewFreshSample(long sampleElapsed,long now){return enabled && sampleElapsed>lastDispatchedSample && fresh(sampleElapsed,now);}
    public synchronized boolean claimSample(long sampleElapsed,long now){
        if(!due(now) || !hasNewFreshSample(sampleElapsed,now))return false;
        lastDispatchedSample=sampleElapsed;nextAttemptAt=plus(now,15000L);return true;
    }
    public synchronized Action failed(IOException error,long now){
        if(!enabled)return Action.STOP_LOCAL;
        boolean stale=error instanceof LiveCoordinator.StaleFixFailure;
        if(error instanceof LiveCoordinator.HttpFailure){LiveCoordinator.HttpFailure http=(LiveCoordinator.HttpFailure)error;stale|=(http.status==400 || http.status==409) && "stale_point".equals(http.code);}
        if(stale){nextAttemptAt=plus(now,1000L);return Action.DROP_SAMPLE;}
        if(LiveCoordinator.isTransientFailure(error)){
            failures=Math.min(7,failures+1);nextAttemptAt=plus(now,backoffMillis(failures));return Action.RETRY_FRESH_LATEST;
        }
        stop();return Action.STOP_LOCAL;
    }
    public synchronized void connectionRecovered(long now){if(enabled){failures=0;nextAttemptAt=now;}}
    public synchronized void delivered(long now){if(enabled){failures=0;nextAttemptAt=plus(now,15000L);}}
    public synchronized void waitForFreshSample(long now){if(enabled)nextAttemptAt=Math.max(nextAttemptAt,plus(now,5000L));}
    public synchronized long nextDelayMillis(long now){if(!enabled)return 15000L;return Math.max(1000L,Math.min(15000L,nextAttemptAt>now?nextAttemptAt-now:1000L));}
    public synchronized long nextAttemptAt(){return nextAttemptAt;}
    public synchronized int failures(){return failures;}
    public static boolean fresh(long sampleElapsed,long now){return sampleElapsed>0 && now>=sampleElapsed && now-sampleElapsed<=MAX_SAMPLE_AGE_MS;}
    public static long backoffMillis(int failures){if(failures<=1)return 1000L;if(failures>=7)return 60000L;return Math.min(60000L,1000L<<(failures-1));}
    private static long plus(long now,long delay){return now>Long.MAX_VALUE-delay?Long.MAX_VALUE:now+delay;}
}
