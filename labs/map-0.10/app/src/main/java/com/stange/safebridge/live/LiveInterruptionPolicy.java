package com.stange.safebridge.live;

/** User Stop and the chosen end revoke; a system interruption never silently revokes. */
public final class LiveInterruptionPolicy {
    public enum Reason { USER_STOP, CHOSEN_END, SYSTEM_INTERRUPT, PERMISSION_LOSS, NOTIFICATION_FAILURE, PROTOCOL_FAILURE, RUNTIME_FAILURE }
    public enum Action { PAUSE_FOR_RECOVERY, REVOKE }
    private LiveInterruptionPolicy(){}
    public static Action action(Reason reason,boolean chosenEndElapsed){
        if(reason==null)throw new IllegalArgumentException("Missing interruption reason");
        return reason==Reason.USER_STOP || reason==Reason.CHOSEN_END || chosenEndElapsed?Action.REVOKE:Action.PAUSE_FOR_RECOVERY;
    }
}
