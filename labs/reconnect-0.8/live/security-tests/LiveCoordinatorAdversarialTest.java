import com.stange.safebridge.live.LiveCoordinator;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Independent synthetic-only safety tests. This class is never packaged in Android. */
public final class LiveCoordinatorAdversarialTest {
  static int assertions;
  static void check(boolean ok,String what){assertions++;if(!ok)throw new AssertionError(what);}
  static final class Clock implements LiveCoordinator.Clock {
    long wall=1800000000000L,elapsed=100000L;
    public long nowMs(){return wall;} public long elapsedNowMs(){return elapsed;}
    void advance(long n){wall+=n;elapsed+=n;}
  }
  static final class Store implements LiveCoordinator.Store {
    LiveCoordinator.Management saved;int writes,failAt=-1;boolean loadFails,failClear;
    List<Map<String,Object>> history=new ArrayList<>();
    public LiveCoordinator.Management load()throws IOException {if(loadFails)throw new IOException("synthetic load fault");return saved;}
    public void save(LiveCoordinator.Management m)throws IOException {writes++;if(writes==failAt)throw new IOException("synthetic atomic write fault");saved=m;history.add(m.toMap());}
    public void clear()throws IOException {if(failClear)throw new IOException("synthetic local cleanup fault");saved=null;}
  }
  static final class Transport implements LiveCoordinator.Transport {
    final Clock clock;boolean offlineStop,lostCreate,created,offlineConfig,offlineCreate,offlinePut,offlineStatus,admissionRequired,useReceiptRecovery;
    long configDelayMs,createDelayMs,statusDelayMs;Map<String,Object> lastCreationBody;
    long changedExpiry,serverClockOverride=-1,expiry=1800000900000L,lease=1800000090000L;
    int creates,deletes,puts,calls,allocations,putHttpStatus;boolean rejectOldGeneration,missingRemote,rejectCreate;
    String generation="abcdefghijklmnopqrstuv",state="waiting_for_fix",identifier="0000000000000000000001",creationKey;
    Set<String> spentTickets=new HashSet<>();
    CountDownLatch putEntered,putRelease,createEntered,createRelease;
    Transport(Clock c){clock=c;}
    void refresh(){if(!Set.of("stopped","expired","unavailable").contains(state)){if(expiry>0&&clock.wall>=expiry)state="expired";else if(clock.wall>=lease)state="paused";}}
    Map<String,Object> status(){refresh();Map<String,Object> m=new HashMap<>();m.put("id",identifier);m.put("generation",generation);m.put("state",state);m.put("serverTimeMs",clock.wall);m.put("expiresAtMs",expiry+changedExpiry);m.put("leaseUntilMs",lease);m.put("managementReceipt","synthetic.management.receipt."+identifier);return m;}
    public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String receipt,Map<String,Object> body)throws IOException {
      calls++;
      if(path.equals("/v1/config")){if(offlineConfig)throw new LiveCoordinator.ConnectionFailure();clock.advance(configDelayMs);configDelayMs=0;Map<String,Object> m=new HashMap<>();m.put("generation",this.generation);m.put("createPermit","synthetic-permit-abcdefghijklmnop");m.put("serverTimeMs",serverClockOverride>=0?serverClockOverride:clock.wall);m.put("protocolVersion",2);m.put("admissionRequired",admissionRequired);m.put("untilStopSupported",true);m.put("minDurationSeconds",1);m.put("maxDurationSeconds",Integer.MAX_VALUE);m.put("leaseMs",90000);m.put("recentMs",30000);return m;}
      if(method.equals("POST")){
        creates++;if(offlineCreate)throw new LiveCoordinator.ConnectionFailure();if(rejectCreate)throw new LiveCoordinator.HttpFailure(400,"bad_request");gate(createEntered,createRelease);clock.advance(createDelayMs);createDelayMs=0;lastCreationBody=new HashMap<>(body);
        String requestedKey=(String)body.get("creationKey");
        if(!Objects.equals(creationKey,requestedKey)){
          long target=((Number)body.get("requestedExpiresAtMs")).longValue();if(target>0&&target<=clock.wall)throw new LiveCoordinator.HttpFailure(400,"chosen_end_elapsed");
          String admission=(String)body.get("admissionTicket");if(admissionRequired){if(admission==null||!admission.startsWith("VALID_"))throw new LiveCoordinator.HttpFailure(403,"invalid_admission_ticket");if(!spentTickets.add(admission))throw new LiveCoordinator.HttpFailure(403,"admission_ticket_spent");}
          creationKey=requestedKey;created=true;allocations++;identifier=String.format(Locale.ROOT,"%022d",allocations);state="waiting_for_fix";int duration=((Number)body.get("durationSeconds")).intValue();expiry=((Number)body.get("requestedExpiresAtMs")).longValue();if((duration==0)!=(expiry==0))throw new LiveCoordinator.HttpFailure(400,"bad_request");lease=expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000);
        }
        if(lostCreate){lostCreate=false;throw new LiveCoordinator.ConnectionFailure();}return status();
      }
      if(method.equals("DELETE")){
        deletes++;if(missingRemote){if(useReceiptRecovery&&receipt!=null){state="unavailable";lease=0;return status();}throw new LiveCoordinator.HttpFailure(404,"not_found");}
        if(rejectOldGeneration&&!this.generation.equals(generation))throw new LiveCoordinator.HttpFailure(404,"not_found");if(offlineStop)throw new LiveCoordinator.ConnectionFailure();refresh();if(!state.equals("expired"))state="stopped";return status();
      }
      if(method.equals("PUT")){puts++;gate(putEntered,putRelease);if(offlinePut)throw new LiveCoordinator.ConnectionFailure();if(putHttpStatus!=0)throw new LiveCoordinator.HttpFailure(putHttpStatus,"synthetic_http_failure");refresh();if(Set.of("stopped","expired","unavailable").contains(state))throw new LiveCoordinator.HttpFailure(409,"session_ended");state="active";lease=expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000);return status();}
      if(offlineStatus)throw new LiveCoordinator.ConnectionFailure();clock.advance(statusDelayMs);statusDelayMs=0;return status();
    }
    void gate(CountDownLatch entered,CountDownLatch release)throws IOException {if(entered!=null){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("synthetic gate timeout");}catch(InterruptedException e){throw new IOException("synthetic interrupt");}}}
  }
  record Rig(Clock clock,Store store,Transport transport,LiveCoordinator client){}
  static Rig rig()throws IOException {Clock c=new Clock();Store s=new Store();Transport t=new Transport(c);return new Rig(c,s,t,new LiveCoordinator(t,s,c,true));}
  static void expectIOException(RunnableIO op,String what)throws Exception {boolean failed=false;try{op.run();}catch(IOException expected){failed=true;}check(failed,what);}
  interface RunnableIO {void run()throws Exception;}
  static LiveCoordinator.Fix fix(Clock c){return new LiveCoordinator.Fix(12.345678,-23.456789,10,c.wall,c.elapsed,false,false);}
  public static void main(String[] args)throws Exception {
    {
      Rig r=rig();r.store.failAt=2;expectIOException(()->r.client.start("https://test.invalid",900),"creation ack journal failure surfaces");
      check(!r.client.status().active,"creation ack journal failure suppresses ACTIVE");check(r.client.readLink()==null,"creation ack journal failure suppresses read link");
      check(r.client.status().pendingRevocation,"creation ack failure retains pending revocation");r.client.retryPendingRevocation();check(r.transport.deletes==1,"creation ack failure can still revoke");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.store.failAt=r.store.writes+1;
      expectIOException(()->r.client.send(fix(r.clock)),"update ack journal failure surfaces");check(!r.client.status().active,"update ack journal failure stops future sending");check(r.client.readLink()==null,"update ack journal failure suppresses read link");
      int sent=r.transport.puts;expectIOException(()->r.client.send(fix(r.clock)),"no subsequent update after journal failure");check(r.transport.puts==sent,"future fix never reaches transport after journal failure");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.store.failAt=r.store.writes+1;
      expectIOException(()->r.client.refreshStatus(),"status journal failure surfaces");check(!r.client.status().active,"status journal failure suppresses ACTIVE");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.transport.offlineStop=true;
      expectIOException(()->r.client.stop(),"offline Stop remains unconfirmed");check(!r.client.status().active&&r.client.status().pendingRevocation,"offline Stop is locally stopped and remotely pending");check(r.store.saved.locallyStopped,"local stop journal persists");
      int sent=r.transport.calls;LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);check(r.transport.calls==sent,"restoration does not contact or resurrect server");check(!restored.status().active&&restored.readLink()==null,"restoration stays stopped");
      r.transport.offlineStop=false;restored.retryPendingRevocation();check(restored.status().state==LiveCoordinator.State.STOPPED,"manual revocation retry acknowledges Stop");check(r.store.saved==null,"acknowledged Stop clears capabilities");
    }
    {
      Rig r=rig();r.transport.lostCreate=true;expectIOException(()->r.client.start("https://test.invalid",900),"lost creation ack remains unconfirmed");
      check(r.client.status().consentActive&&!r.client.status().pendingRevocation&&!r.client.status().active,"lost creation ack preserves only existing consent while unconfirmed");check(r.store.saved.id==null&&r.store.saved.creationUncertain,"creation recovery info persisted without invented ID");
      r.client.markStoppedPending();r.client.retryPendingRevocation();check(r.transport.creates==2&&r.transport.deletes==1,"stopped uncertain creation retried solely to resolve and revoke");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);String link=r.client.readLink();LiveCoordinator.Management m=r.store.saved;
      check(link.contains(m.readToken),"read link contains read capability");check(!link.contains(m.writeToken)&&!link.contains(m.revokeToken)&&!link.contains(m.creationKey),"read link excludes management secrets");
      r.client.send(fix(r.clock));Set<String> banned=Set.of("latitude","longitude","accuracyM","point","points","coordinates","sampleTimeMs","sampleAgeMs","recipient");
      for(Map<String,Object> value:r.store.history)check(Collections.disjoint(value.keySet(),banned),"journal contains no coordinates or recipients");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.transport.changedExpiry=5000;
      expectIOException(()->r.client.send(fix(r.clock)),"noncreation expiry extension rejected");check(!r.client.status().active,"protocol expiry violation fails closed");
    }
    {
      Rig r=rig();r.transport.serverClockOverride=Long.MAX_VALUE;
      expectIOException(()->r.client.start("https://test.invalid",900),"unsafe numeric server clock rejected");check(r.transport.creates==0,"overflowed config cannot create a session");
    }
    {
      Rig r=rig();r.transport.createEntered=new CountDownLatch(1);r.transport.createRelease=new CountDownLatch(1);AtomicReference<Throwable> err=new AtomicReference<>();
      Thread thread=new Thread(()->{try{r.client.start("https://test.invalid",900);}catch(Throwable e){err.set(e);}});thread.start();check(r.transport.createEntered.await(2,TimeUnit.SECONDS),"creation reached controlled in-flight gate");
      r.client.markStoppedPending();r.transport.createRelease.countDown();thread.join(3000);check(!thread.isAlive()&&err.get()==null,"creation callback completes after Stop");check(!r.client.status().active&&r.client.readLink()==null,"late creation ack cannot reactivate sender");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.transport.putEntered=new CountDownLatch(1);r.transport.putRelease=new CountDownLatch(1);AtomicReference<Throwable> err=new AtomicReference<>();
      Thread thread=new Thread(()->{try{r.client.send(fix(r.clock));}catch(Throwable e){err.set(e);}});thread.start();check(r.transport.putEntered.await(2,TimeUnit.SECONDS),"update reached controlled in-flight gate");
      r.client.markStoppedPending();r.client.retryPendingRevocation();r.transport.putRelease.countDown();thread.join(3000);check(!thread.isAlive()&&(err.get()==null||err.get() instanceof IOException),"late in-flight response completes or is safely rejected afterStop");check(!r.client.status().active&&r.client.readLink()==null,"late update response cannot undo Stop");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.clock.advance(900001);check(!r.client.status().active&&r.client.status().pendingRevocation,"elapsed deadline stops locally but does not invent remote revocation");
    }
    {
      Rig r=rig();expectIOException(()->r.client.start("http://attacker.invalid",900),"production client rejects cleartext");expectIOException(()->r.client.start("https://user:secret@test.invalid",900),"credential URL rejected");expectIOException(()->r.client.start("https://test.invalid/redirect",900),"non-origin endpoint rejected");check(r.transport.calls==0,"invalid endpoints never reach transport");
    }
    {
      Clock c=new Clock();Store s=new Store();s.loadFails=true;Transport t=new Transport(c);expectIOException(()->new LiveCoordinator(t,s,c),"store read failure has no plaintext fallback");check(t.calls==0,"failed journal load never reaches network");
    }
    {
      Rig r=rig();LiveCoordinator.Status guarded=r.client.start("https://test.invalid",900,()->false);check(!guarded.active,"lost consent guard blocks activation");check(r.transport.calls==0,"lost consent guard blocks all HTTP");check(r.store.saved==null,"lost consent guard stores no new credentials");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.client.markStoppedPending();r.transport.generation="ZYXWVUTSRQPONMLKJIHGFE";r.transport.rejectOldGeneration=true;
      LiveCoordinator.Status recovered=r.client.retryPendingRevocation();check(!recovered.active&&!recovered.pendingRevocation,"new generation proof resolves old pending record");check("unavailable".equals(recovered.serverState),"restart recovery does not invent explicit server Stop");check(r.transport.creates==1&&r.transport.puts==0,"restart recovery never creates or uploads automatically");
      check(r.client.readLink()==null&&r.store.saved==null,"restart clears old link and capabilities");check(r.client.start("https://test.invalid",900).active,"new session requires and accepts explicit fresh start");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.client.markStoppedPending();r.clock.advance(900001);r.transport.missingRemote=true;
      LiveCoordinator.Status ended=r.client.retryPendingRevocation();check(!ended.pendingRevocation&&"expired".equals(ended.serverState),"known immutable expiry confirmed with fresh server time");
    }
    {
      Rig r=rig();r.transport.lostCreate=true;expectIOException(()->r.client.start("https://test.invalid",900),"uncertain creation fixture");r.client.markStoppedPending();r.transport.offlineCreate=true;r.clock.advance(900001);
      expectIOException(()->r.client.retryPendingRevocation(),"unknown creation expiry is not guessed from pre-ACK timestamp");check(r.client.status().pendingRevocation&&!r.client.status().active,"unknown creation remains stopped and pending without proof");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.store.failClear=true;expectIOException(()->r.client.stop(),"local cleanup failure surfaces after remote acknowledgement");
      check("stopped".equals(r.client.status().serverState)&&"local_cleanup_pending".equals(r.client.status().failureCode),"remote acknowledgement is preserved separately from local cleanup failure");check(!r.client.status().active&&r.client.readLink()==null,"cleanup failure cannot reactivate sender");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",0);r.client.send(fix(r.clock));String link=r.client.readLink(),id=r.client.status().id;r.transport.offlinePut=true;r.clock.advance(120001);
      expectIOException(()->r.client.send(fix(r.clock)),"transient outage is reported");check(r.client.status().consentActive&&!r.client.status().pendingRevocation,"outage longer than90s does not withdraw consent");check(r.client.status().deliveryUnconfirmed&&!r.client.status().latestAcknowledgementFresh,"outage does not claim fresh delivery");check(link.equals(r.client.readLink()),"outage preserves existing recipient link");
      r.transport.offlinePut=false;r.client.refreshStatus();check(r.client.status().serverPaused&&r.client.status().consentActive,"server pause remains resumable consent");r.client.send(fix(r.clock));check(r.client.status().id.equals(id)&&r.client.readLink().equals(link),"fresh resumed update uses SAMEID/link");check(r.transport.allocations==1&&r.transport.creates==1,"reconnect cannot allocate replacement session");check(r.client.status().latestAcknowledgementFresh,"fresh actual acknowledgement restores current delivery label");
      r.clock.advance(24*3600000L);check(r.client.status().consentActive&&r.client.status().expiresAtMs==0,"UntilStop has no hidden onehour/day hardlimit");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",0);r.transport.offlinePut=true;expectIOException(()->r.client.send(fix(r.clock)),"offline sender fixture");r.transport.offlineStop=true;expectIOException(()->r.client.stop(),"Stop while offline remains remote pending");int calls=r.transport.calls,puts=r.transport.puts,creates=r.transport.creates;
      r.client.retryConnection();check(r.transport.calls==calls,"retryConnection afterStop cannot contact or recreate");expectIOException(()->r.client.send(fix(r.clock)),"Stop while offline forbids new fixes");check(r.transport.puts==puts&&r.transport.creates==creates,"stopped offline consent never resumes uploads");r.transport.offlinePut=false;r.transport.offlineStop=false;r.client.retryPendingRevocation();check(!r.client.status().pendingRevocation&&!r.client.status().consentActive,"reconnect resolves Stop only, never collection");
    }
    {
      Rig r=rig();r.transport.lostCreate=true;expectIOException(()->r.client.start("https://test.invalid",0),"uncertain creation before reconnect");String writer=r.store.saved.writeToken,key=r.store.saved.creationKey;r.client.retryConnection();check(r.client.status().consentActive&&r.client.status().active,"uncertain creation can resume sameconsent");check(r.transport.allocations==1&&r.transport.creates==2,"idempotent reconnect allocates once");check(writer.equals(r.store.saved.writeToken)&&key.equals(r.store.saved.creationKey),"reconnect preserves credentials/creation key");
    }
    {
      Rig r=rig();r.transport.offlineConfig=true;expectIOException(()->r.client.start("https://test.invalid",0),"config outage is unconfirmed");check(r.client.status().consentActive&&r.store.saved==null,"config outage retains RAMconsent but no fabricated credentials");r.client.markStoppedPending();r.transport.offlineConfig=false;int calls=r.transport.calls;r.client.retryConnection();check(r.transport.calls==calls,"Stop cancels pending precreation retry");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;String good="VALID_"+"T".repeat(32);expectIOException(()->r.client.start("https://test.invalid",0,"INVALID_"+"T".repeat(32)),"invalid operatorcode rejected");check(r.transport.allocations==0&&!r.client.status().consentActive&&!r.client.status().pendingRevocation&&r.store.saved==null,"specific authenticatednoallocation rejection does not deadlock inpending");
      check(r.client.start("https://test.invalid",0,good).consentActive,"corrected validcode can be explicitly started");check(r.store.saved.admissionTicket==null,"operatorcode scrubbed from management afterACK");check(!r.client.readLink().contains(good)&&!r.client.readLink().contains(r.store.saved.managementReceipt),"recipientlink excludes admission and management receipt");
    }
    {
      Rig r=rig();expectIOException(()->r.client.start("https://test.invalid",0,"bad code with spaces"),"invalid localcode preflight");check(r.transport.calls==0&&r.store.saved==null&&!r.client.status().pendingRevocation,"local codepreflight noallocation ordeadlock");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",0);r.client.markStoppedPending();r.transport.missingRemote=true;r.transport.useReceiptRecovery=true;r.client.retryPendingRevocation();check(!r.client.status().pendingRevocation&&"unavailable".equals(r.client.status().serverState),"authenticated managementreceipt resolves evicted untilStop terminal");check(!r.client.status().consentActive&&r.client.readLink()==null,"receiptcleanup cannot resume sharing");
    }
    {
      Rig r=rig();long chosenEnd=r.clock.wall+120000;r.transport.offlineConfig=true;
      expectIOException(()->r.client.startUntil("https://test.invalid",chosenEnd,null,()->true),"absolute chosenend configoutage");
      r.clock.advance(90000);r.transport.offlineConfig=false;r.client.retryConnection();
      check(((Number)r.transport.lastCreationBody.get("requestedExpiresAtMs")).longValue()==chosenEnd,"lateconfig sends exact original absolute chosenend");
      check(r.transport.expiry==chosenEnd&&r.client.status().consentActive,"lateconfig retains only chosen remaining30s");
      r.clock.advance(30000);check(!r.client.status().consentActive,"lateconfig ends at original chosenend");
    }
    {
      Rig r=rig();long originalEnd=r.clock.wall+120000;r.transport.offlineConfig=true;
      expectIOException(()->r.client.start("https://test.invalid",120),"relative chosenbudget configoutage");
      r.clock.advance(90000);r.transport.offlineConfig=false;r.client.retryConnection();
      check(r.transport.expiry==originalEnd,"relativeAPI does not reset120s budget after90s configoutage");
      check(r.client.status().consentActive,"relativeAPI has remaining30s consent");
    }
    {
      Rig r=rig();long chosenEnd=r.clock.wall+120000;r.transport.createDelayMs=90000;
      r.client.startUntil("https://test.invalid",chosenEnd,null,()->true);
      check(r.transport.expiry==chosenEnd,"latePOST serverexact chosenend immutable");
      check(r.client.status().consentActive,"latePOST must not double subtract90s before serverresponse");
      r.clock.advance(29999);check(r.client.status().consentActive,"latePOST stays consented for actual chosen remainingtime");
      r.clock.advance(1);check(!r.client.status().consentActive,"latePOST does not extend original chosenend");
    }
    {
      Rig r=rig();long chosenEnd=r.clock.wall+120000;r.client.startUntil("https://test.invalid",chosenEnd,null,()->true);
      r.transport.statusDelayMs=90000;r.client.refreshStatus();
      check(r.client.status().consentActive,"late status may mark pointunverified but must not double subtract harddeadline");
      r.clock.advance(30000);check(!r.client.status().consentActive,"late status still respects original hardend");
    }
    {
      Rig r=rig();long chosenEnd=r.clock.wall+120000;r.transport.admissionRequired=true;r.transport.createDelayMs=120000;
      String code="VALID_"+"E".repeat(32);expectIOException(()->r.client.startUntil("https://test.invalid",chosenEnd,code,()->true),"chosenend elapses before allocation");
      check(r.transport.allocations==0&&r.transport.spentTickets.isEmpty(),"elapsed newcreation cannot allocate or consume admission");
      check(!r.client.status().pendingRevocation&&r.store.saved==null,"definite elapsed rejection clears unallocated journal");
      check(r.client.startUntil("https://test.invalid",r.clock.wall+30000,code,()->true).consentActive,"new explicit chosenend may reuse unspent code");
    }
    for(int http:new int[]{408,425,429,500,503}){
      Rig r=rig();r.client.start("https://test.invalid",0);r.transport.putHttpStatus=http;expectIOException(()->r.client.send(fix(r.clock)),"transientHTTP fixture");check(r.client.status().consentActive&&!r.client.status().pendingRevocation,"typed transientHTTP keeps consent: "+http);
    }
    for(int http:new int[]{401,403,404,409}){
      Rig r=rig();r.client.start("https://test.invalid",0);r.transport.putHttpStatus=http;expectIOException(()->r.client.send(fix(r.clock)),"permanentHTTP fixture");check(!r.client.status().consentActive&&r.client.status().pendingRevocation,"authorization/terminalHTTP failsclosed: "+http);
    }
    System.out.println("PASS "+assertions+" independent sender journal/Stop/expiry/capability assertions (synthetic host only)");
  }
}
