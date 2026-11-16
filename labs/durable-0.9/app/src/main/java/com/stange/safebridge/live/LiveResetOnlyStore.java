package com.stange.safebridge.live;

import java.io.IOException;

/**
 * Temporary own-slot reset adapter, never a sender journal. It has no filesystem
 * reference and cannot overwrite/delete an unreadable real management record.
 * New-session creation fails at save(), before the protocol may send creation.
 */
public final class LiveResetOnlyStore implements LiveCoordinator.Store {
    @Override public LiveCoordinator.Management load(){return null;}
    @Override public void save(LiveCoordinator.Management value)throws IOException{throw new IOException("Reset-only recovery cannot persist or create a session.");}
    @Override public void clear(){/* The actual management journal is deliberately untouched. */}
}
