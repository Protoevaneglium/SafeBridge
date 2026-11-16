import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveRetryPolicy;
import com.stange.safebridge.LiveSessionChoice;
import com.stange.safebridge.live.LiveInterruptionPolicy;
import com.stange.safebridge.live.LiveResetOnlyStore;
import java.io.IOException;
import java.nio.file.*;
import javax.net.ssl.SSLHandshakeException;

/** Pure policy execution plus explicit source contracts, never Android runtime evidence. */
public final class LiveServicePolicyAdversarialTest {
  static int checks;
  static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static void rejects(Runnable r,String what){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,what);}
  public static void main(String[] args)throws Exception {
    LiveRetryPolicy policy=new LiveRetryPolicy();long now=1000;policy.begin(now);
    check(policy.claimSample(now,now),"first new fresh fix can dispatch");
    for(int i=0;i<10;i++){
      LiveRetryPolicy.Action action=policy.failed(new LiveCoordinator.ConnectionFailure(),now);
      check(action==LiveRetryPolicy.Action.RETRY_FRESH_LATEST,"typed outage retains foreground retry policy");
      check(policy.nextAttemptAt()>now&&policy.nextAttemptAt()<=now+60000,"outage backoff bounded to60s");
      now=policy.nextAttemptAt();check(policy.due(now),"consented retry survives outage beyond90s");
    }
    check(now>91000,"test genuinely spans more than90s monotonic time");
    check(!policy.claimSample(1000,now),"failed older fix is not replayed");check(policy.claimSample(now,now),"latest new fresh fix resumes without re-begin");
    policy.failed(new LiveCoordinator.StaleFixFailure(),now);now+=1000;
    check(!policy.claimSample(now-1000,now),"same stale-dispatched fix cannot be retried");check(policy.claimSample(now,now),"stale drop permits a newer fresh sample");
    check(policy.failed(new LiveCoordinator.HttpFailure(400,"stale_point"),now)==LiveRetryPolicy.Action.DROP_SAMPLE,"server stale rejection drops sample without Stop");
    policy.stop();check(!policy.due(Long.MAX_VALUE),"Stop invalidates scheduled retry");policy.connectionRecovered(now);policy.delivered(now);check(!policy.due(Long.MAX_VALUE),"late recovery or ACK cannot re-enable stopped policy");
    for(IOException error:new IOException[]{new IOException("protocol fixture"),new SSLHandshakeException("TLS fixture"),new LiveCoordinator.HttpFailure(401,"unauthorized"),new LiveCoordinator.HttpFailure(409,"session_ended")}){
      policy.begin(now);check(policy.failed(error,now)==LiveRetryPolicy.Action.STOP_LOCAL,"security/protocol/auth failure failsclosed");check(!policy.due(Long.MAX_VALUE),"fatal failure cannot retry");
    }
    for(int status:new int[]{408,425,429,500,503}){policy.begin(now);check(policy.failed(new LiveCoordinator.HttpFailure(status,"temporarily_unavailable"),now)==LiveRetryPolicy.Action.RETRY_FRESH_LATEST,"transient HTTP keeps consent");}
    long wall=1800000000000L;
    check(LiveSessionChoice.durationSeconds(0,wall)==0,"UntilStop sentinel stayszero");
    check(LiveSessionChoice.durationSeconds(wall+3*3600000L,wall)==10800,"chosen end supports more thanonehour");
    check(LiveSessionChoice.durationSeconds(wall+1,wall)==1,"positive subsecond choice rounds up");
    rejects(()->LiveSessionChoice.durationSeconds(wall,wall),"present chosen deadline rejected");
    rejects(()->LiveSessionChoice.durationSeconds(wall-1,wall),"past chosen deadline rejected");
    rejects(()->LiveSessionChoice.durationSeconds(wall+(Integer.MAX_VALUE*1000L)+1,wall),"unrepresentable selected duration rejected");
    String source=Files.readString(Path.of("app/src/main/java/com/stange/safebridge/LiveLocationService.java"));
    String queue=source.substring(source.indexOf("private void queueSend()"),source.indexOf("private void updateNotice("));
    int caught=queue.indexOf("catch(IOException failure)"),guard=queue.indexOf("if(action==LiveRetryPolicy.Action.STOP_LOCAL)",caught);
    check(caught>=0&&guard>caught&&queue.substring(caught,guard).contains("retryPolicy.failed(failure"),"actual service catch dispatches through tested policy");
    check(!queue.substring(caught,guard).contains("stopLocal(")&&!queue.substring(caught,guard).contains("interruptLocal("),"actual catch has no unconditional interruption before retry classification");
    check(queue.contains("else if(validTicket(ticket))")&&queue.contains("connectionRetrying=true"),"recoverable branch preserves session and labels connection unverified");
    check(queue.indexOf("NETWORK.execute")<queue.indexOf("Location latest=pendingFix")&&queue.indexOf("Location latest=pendingFix")<queue.indexOf("coordinator.send(fix)"),"actual worker selects latestfix atdispatch");
    check(queue.contains("retryPolicy.claimSample")&&queue.contains("coordinator.retryConnection()"),"actual worker uses fresh-only and same-consent retry APIs");
    String schedule=source.substring(source.indexOf("private void scheduleExpiry()"),source.indexOf("private LiveCoordinator.PreparedAccess loadPrepared("));
    check(schedule.contains("if(deadline==0)return"),"UntilStop creates no duration alarm");
    check(source.contains("(deadline==0 || SystemClock.elapsedRealtime()<deadline)")&&source.contains("if(deadline>0 && SystemClock.elapsedRealtime()>=deadline)"),"actual deadline checks distinguishzero fromselected expiry");
    String stop=source.substring(source.indexOf("private void finishLocal(String reason,LiveInterruptionPolicy.Action action)"));
    check(stop.indexOf("GENERATION.incrementAndGet()")<stop.indexOf("retryPolicy.stop()")&&stop.indexOf("retryPolicy.stop()")<stop.indexOf("locations.removeUpdates(this)")&&stop.indexOf("locations.removeUpdates(this)")<stop.indexOf("markStoppedPending()"),"Stop invalidates retry and collection before journal andremote work");
    check(source.contains("START_NOT_STICKY")&&source.contains("if(intent==null){pauseLocal("),"null-intent restart pauses without automatic collection or revocation");
    for(LiveInterruptionPolicy.Reason reason:LiveInterruptionPolicy.Reason.values()){
      boolean explicit=reason==LiveInterruptionPolicy.Reason.USER_STOP||reason==LiveInterruptionPolicy.Reason.CHOSEN_END;
      check(LiveInterruptionPolicy.action(reason,false)==(explicit?LiveInterruptionPolicy.Action.REVOKE:LiveInterruptionPolicy.Action.PAUSE_FOR_RECOVERY),"actual interruptionpolicy distinguishes explicitStop/end: "+reason);
      check(LiveInterruptionPolicy.action(reason,true)==LiveInterruptionPolicy.Action.REVOKE,"elapsed chosenend remains binding despite interruptionreason: "+reason);
    }
    rejects(()->LiveInterruptionPolicy.action(null,false),"missing interruptionreason rejected");
    String foreground=source.substring(source.indexOf("try{Notification notice=notification("),source.indexOf("scheduleExpiry();handler.post(monitor)"));
    check(foreground.contains("catch(RuntimeException denied){interruptLocal(")&&!foreground.contains("stopLocal("),"actual foreground-start denial pauses evenonResume, without implicitStop");
    check(queue.contains("coordinator.resumeExisting(access,()->validTicket(ticket))")&&queue.contains("deadline=coordinator.endsAtElapsed();scheduleExpiry();"),"actual resume reconciles engine andselecteddeadline");
    check(queue.indexOf("coordinator.resumeExisting")<queue.indexOf("if(!registrationComplete)")&&queue.indexOf("scheduleExpiry();")<queue.indexOf("if(!registrationComplete)"),"actual providersubscription isqueued onlyafter explicitreconciliation anddeadline");
    check(source.contains("onDestroy(){if(running && !stopping.get())interruptLocal(")&&stop.contains("if(c!=null && revokeOnEnd)retryRemote("),"system destruction cannotunconditionally request remoteStop");
    check(source.contains("before.pendingRevocation && before.id==null")&&source.contains("c.retryPendingRevocation(access)"),"actual unknownStop loadsprivategrant whileknownIDnarrowrevoke needsno grant");
    String reset=source.substring(source.indexOf("public static void resetPrepared("),source.indexOf("private static void retryRemote("));
    check(reset.indexOf("new PreparedAccessStore(app).load()")<reset.indexOf("try{initialize(app)")&&reset.contains("target=new LiveCoordinator(new HttpsLiveTransport(),new LiveResetOnlyStore()"),"unreadablemanagement fallback usesseparatevalidpreparedaccess andtemporaryresetonlyengine");
    check(!reset.contains("coordinator=target")&&!reset.contains("new LiveSessionStore")&&reset.contains("journal is still unreadable and unchanged"),"resetonlyfallback neitherreplaces norclaimsrecoveryofcorruptmainjournal");
    LiveResetOnlyStore resetStore=new LiveResetOnlyStore();check(resetStore.load()==null,"actual resetonlyStore exposesno mainmanagement");resetStore.clear();check(resetStore.load()==null,"actual resetonlyStore cleardoesnotcreate state");boolean saveRefused=false;try{resetStore.save(null);}catch(IOException expected){saveRefused=true;}check(saveRefused,"actual resetonlyStore cannotpersist sendercredentials");
    String preparedStore=Files.readString(Path.of("app/src/main/java/com/stange/safebridge/PreparedAccessStore.java"));
    check(preparedStore.contains("getNoBackupFilesDir()")&&preparedStore.contains("AES/GCM/NoPadding")&&preparedStore.contains("PreparedAccessStore|1")&&preparedStore.contains("No plaintext fallback"),"preparedcredential storage sourcehasseparateAEAD/noBackup schema boundary");
    check(!preparedStore.contains("HttpURLConnection")&&!preparedStore.contains("resetPreparedCurrent")&&!preparedStore.contains("retryPendingRevocation"),"forgetting privatepreparedstore hasno networking/reset/revoke dependency");
    System.out.println("PASS "+checks+" independent retry/end-choice/source-integration checks (host andsource only, noAndroid runtime)");
  }
}
