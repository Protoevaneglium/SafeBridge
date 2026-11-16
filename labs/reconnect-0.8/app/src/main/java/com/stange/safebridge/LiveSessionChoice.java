package com.stange.safebridge;

/** Pure conversion shared by the native end-time picker and foreground sender. */
public final class LiveSessionChoice {
    private LiveSessionChoice(){}
    public static int durationSeconds(long endAtMs,long nowMs){
        if(nowMs<0 || endAtMs<0)throw new IllegalArgumentException("Invalid clock or end time.");
        if(endAtMs==0)return 0;
        if(endAtMs<=nowMs)throw new IllegalArgumentException("Choose a future end time.");
        long remaining=endAtMs-nowMs;
        if(remaining>Integer.MAX_VALUE*1000L)throw new IllegalArgumentException("The chosen end time is too far away.");
        return (int)((remaining+999L)/1000L);
    }
}
