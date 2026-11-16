from pathlib import Path
root = Path(__file__).resolve().parents[1]
s = (root / 'app/src/main/java/com/stange/safebridge/LiveLocationService.java').read_text()
start = s.index('public static boolean canReplacePrepared(')
end = s.index('public static long endsAtElapsed(', start)
body = s[start:end]
for forbidden in ['.status(', 'initialize(', 'stopLocal(', 'stopSession(', 'pauseLocal(', 'pauseForRecovery(', 'markStoppedPending(', 'retryRemote(', 'resumeExisting(', 'startPreparedUntil(', 'resetPreparedCurrent(', '.clear(', '.save(', 'sendBroadcast(', 'new HttpsLiveTransport', 'preparedToken']:
    assert forbidden not in body, forbidden
assert 'hasManagedSession()' in body and 'client.canReplacePrepared(origin,slotId)' in body
assert 'new PreparedAccessStore(app).load()' in body
assert 'origin.equals(existing.endpoint) && slotId.equals(existing.slotId)' in body
assert 'origin.equals(LiveConfig.endpoint())' in body
assert 'PreparedReplacementGate.storedFallback(false,true,false)' in body
print('PASS source-only replacement contract: canonical configured scope, pure coordinator access, bounded local fallback, no token exposure or session/network writes')
