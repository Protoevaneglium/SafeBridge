package com.stange.safebridge;

/** Pure guard shared by prepared-import checks; has no storage/network/session effects. */
public final class PreparedReplacementGate {
    private PreparedReplacementGate(){}
    public static boolean coordinator(boolean localStartupInProgress,boolean knownManagementOrCoordinatorStart,boolean scopeAllowed){
        return scopeAllowed && (!localStartupInProgress || knownManagementOrCoordinatorStart);
    }
    public static boolean storedFallback(boolean recordReadable,boolean existingSetup,boolean sameScope){
        return recordReadable && (!existingSetup || sameScope);
    }
}
