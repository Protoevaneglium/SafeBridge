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
    final Clock clock;boolean offlineStop,lostCreate,created,offlineConfig,offlineCreate,offlinePut,offlineStatus,admissionRequired,useReceiptRecovery,lostAdmission;
    long configDelayMs,configTransitMs,createDelayMs,statusDelayMs,statusTransitMs;Map<String,Object> lastCreationBody;
    long changedExpiry,serverClockOverride=-1,expiry=1800000900000L,lease=1800000090000L,serverSequence,slotEpoch,issuedEpochOverride=-1;
    int creates,deletes,puts,calls,allocations,putHttpStatus,issues;boolean rejectOldGeneration,missingRemote,rejectCreate;
    String generation="abcdefghijklmnopqrstuv",state="waiting_for_fix",identifier="0000000000000000000001",creationKey;
    final String slotId="0000000000000000000001",preparedToken="P".repeat(43);
    final Map<String,Map<String,Object>> tickets=new HashMap<>();final List<Object> expectedEpochs=new ArrayList<>();
    Runnable afterAdmission;long lastPutSequence;
    CountDownLatch putEntered,putRelease,createEntered,createRelease,statusEntered,statusRelease,resetEntered,resetRelease;
    Transport(Clock c){clock=c;}
    LiveCoordinator.PreparedAccess access(){return new LiveCoordinator.PreparedAccess(slotId,preparedToken);}
    void refresh(){if(!Set.of("stopped","expired","unavailable").contains(state)){if(expiry>0&&clock.wall>=expiry){state="expired";slotEpoch++;}else if(clock.wall>=lease)state="paused";}}
    Map<String,Object> status(){refresh();Map<String,Object> m=new HashMap<>();m.put("id",identifier);m.put("generation",generation);m.put("state",state);m.put("serverTimeMs",clock.wall);m.put("expiresAtMs",expiry+changedExpiry);m.put("leaseUntilMs",lease);m.put("lastSequence",serverSequence);m.put("managementReceipt","synthetic.management.receipt."+identifier);return m;}
    public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String receipt,Map<String,Object> body)throws IOException {
      calls++;
      if(path.equals("/v1/config")){if(offlineConfig)throw new LiveCoordinator.ConnectionFailure();clock.advance(configDelayMs);configDelayMs=0;Map<String,Object> m=new HashMap<>();m.put("generation",this.generation);m.put("createPermit","synthetic-permit-abcdefghijklmnop");m.put("serverTimeMs",serverClockOverride>=0?serverClockOverride:clock.wall);m.put("protocolVersion",3);m.put("durableSessionState",true);m.put("preparedAdmission",true);m.put("admissionRequired",admissionRequired);m.put("untilStopSupported",true);m.put("minDurationSeconds",1);m.put("maxDurationSeconds",Integer.MAX_VALUE);m.put("leaseMs",90000);m.put("recentMs",30000);clock.advance(configTransitMs);configTransitMs=0;return m;}
      if(path.equals("/v1/admission")&&method.equals("POST")){
        issues++;if(!preparedToken.equals(token)||!slotId.equals(body.get("slotId")))throw new LiveCoordinator.HttpFailure(404,"not_found");
        Object expected=body.get("expectedSlotEpoch");expectedEpochs.add(expected);if(expected!=null&&((Number)expected).longValue()!=slotEpoch)throw new LiveCoordinator.HttpFailure(409,"admission_epoch_changed");
        String issued="PREPARED_TICKET_"+issues+"_"+"T".repeat(32);long epoch=issuedEpochOverride<0?slotEpoch:issuedEpochOverride;
        Map<String,Object> ticket=new HashMap<>();ticket.put("key",body.get("creationKey"));ticket.put("epoch",epoch);ticket.put("end",clock.wall+120000);tickets.put(issued,ticket);
        Map<String,Object> result=new HashMap<>();result.put("generation",this.generation);result.put("slotId",slotId);result.put("creationKey",body.get("creationKey"));result.put("slotEpoch",epoch);result.put("admissionTicket",issued);result.put("expiresAtMs",clock.wall+120000);
        if(afterAdmission!=null)afterAdmission.run();if(lostAdmission){lostAdmission=false;throw new LiveCoordinator.ConnectionFailure();}return result;
      }
      if(path.equals("/v1/admission/current")&&method.equals("DELETE")){
        gate(resetEntered,resetRelease);
        if(!preparedToken.equals(token)||!slotId.equals(body.get("slotId")))throw new LiveCoordinator.HttpFailure(404,"not_found");
        boolean active=created&&!Set.of("stopped","expired","unavailable").contains(state);state="stopped";slotEpoch++;
        Map<String,Object> result=new HashMap<>();result.put("generation",this.generation);result.put("slotId",slotId);result.put("slotEpoch",slotEpoch);result.put("serverTimeMs",clock.wall);result.put("state",active?"stopped":"none");return result;
      }
      if(method.equals("POST")){
        creates++;if(offlineCreate)throw new LiveCoordinator.ConnectionFailure();if(rejectCreate)throw new LiveCoordinator.HttpFailure(400,"bad_request");gate(createEntered,createRelease);clock.advance(createDelayMs);createDelayMs=0;lastCreationBody=new HashMap<>(body);
        String requestedKey=(String)body.get("creationKey");
        if(!Objects.equals(creationKey,requestedKey)){
          long target=((Number)body.get("requestedExpiresAtMs")).longValue();if(target>0&&target<=clock.wall)throw new LiveCoordinator.HttpFailure(400,"chosen_end_elapsed");
          String admission=(String)body.get("admissionTicket");if(admissionRequired){Map<String,Object> issued=tickets.get(admission);if(issued==null||!requestedKey.equals(issued.get("key"))||((Number)issued.get("end")).longValue()<=clock.wall)throw new LiveCoordinator.HttpFailure(403,"invalid_admission_ticket");if(((Number)issued.get("epoch")).longValue()!=slotEpoch)throw new LiveCoordinator.HttpFailure(409,"admission_epoch_changed");}
          creationKey=requestedKey;created=true;allocations++;identifier=String.format(Locale.ROOT,"%022d",allocations);state="waiting_for_fix";serverSequence=0;int duration=((Number)body.get("durationSeconds")).intValue();expiry=((Number)body.get("requestedExpiresAtMs")).longValue();if((duration==0)!=(expiry==0))throw new LiveCoordinator.HttpFailure(400,"bad_request");lease=expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000);
        }
        if(lostCreate){lostCreate=false;throw new LiveCoordinator.ConnectionFailure();}return status();
      }
      if(method.equals("DELETE")){
        deletes++;if(missingRemote){if(useReceiptRecovery&&receipt!=null){state="unavailable";lease=0;return status();}throw new LiveCoordinator.HttpFailure(404,"not_found");}
        if(rejectOldGeneration&&!this.generation.equals(generation))throw new LiveCoordinator.HttpFailure(404,"not_found");if(offlineStop)throw new LiveCoordinator.ConnectionFailure();refresh();if(!Set.of("expired","stopped").contains(state)){state="stopped";slotEpoch++;}return status();
      }
      if(method.equals("PUT")){puts++;lastPutSequence=((Number)body.get("sequence")).longValue();gate(putEntered,putRelease);if(offlinePut)throw new LiveCoordinator.ConnectionFailure();if(putHttpStatus!=0)throw new LiveCoordinator.HttpFailure(putHttpStatus,"synthetic_http_failure");refresh();if(Set.of("stopped","expired","unavailable").contains(state))throw new LiveCoordinator.HttpFailure(409,"session_ended");if(lastPutSequence<=serverSequence)throw new LiveCoordinator.HttpFailure(409,"out_of_order");serverSequence=lastPutSequence;state="active";lease=expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000);return status();}
      if(offlineStatus)throw new LiveCoordinator.ConnectionFailure();gate(statusEntered,statusRelease);clock.advance(statusDelayMs);statusDelayMs=0;Map<String,Object> result=status();clock.advance(statusTransitMs);statusTransitMs=0;return result;
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
      check(!r.client.status().active,"creation ack journal failure suppresses ACTIVE");check(r.client.readLink()!=null&&r.client.status().recoveryAvailable,"creation ack journal failure keeps knownlink onlyaspausedrecovery");
      check(!r.client.status().pendingRevocation&&r.transport.deletes==0,"journal interruption neverinvents automaticStop");r.client.markStoppedPending();r.client.retryPendingRevocation();check(r.transport.deletes==1,"creation ack failure can still revoke");
    }
    {
      Rig r=rig();r.client.start("https://test.invalid",900);r.store.failAt=r.store.writes+1;
      expectIOException(()->r.client.send(fix(r.clock)),"update ack journal failure surfaces");check(!r.client.status().active,"update ack journal failure stops future sending");check(r.client.readLink()!=null&&r.client.status().recoveryAvailable,"update ack journal failure preservespausedknownlink");
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
      Rig r=rig();r.transport.admissionRequired=true;LiveCoordinator.PreparedAccess bad=new LiveCoordinator.PreparedAccess(r.transport.slotId,"X".repeat(43));expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,bad,()->true),"invalid preparedgrant rejected");check(r.transport.allocations==0&&!r.client.status().consentActive,"invalid preparedgrant cannot create");r.client.markStoppedPending();r.client.retryPendingRevocation();
      check(r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true).consentActive,"valid preparedgrant can be explicitly started");check(r.store.saved.admissionTicket==null,"boundticket scrubbed from management afterACK");check(!r.client.readLink().contains(r.transport.preparedToken)&&!r.client.readLink().contains(r.store.saved.managementReceipt),"recipientlink excludes preparedgrant and management receipt");
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
      Rig r=rig();long chosenEnd=r.clock.wall+120000;r.transport.createDelayMs=120000;
      String code="VALID_"+"E".repeat(32);expectIOException(()->r.client.startUntil("https://test.invalid",chosenEnd,code,()->true),"chosenend elapses before allocation");
      check(r.transport.allocations==0,"elapsed newcreation cannot allocate or consume admission");
      check(!r.client.status().pendingRevocation&&r.store.saved==null,"definite elapsed rejection clears unallocated journal");
      check(r.client.startUntil("https://test.invalid",r.clock.wall+30000,code,()->true).consentActive,"new explicit chosenend may reuse unspent code");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);r.client.send(fix(r.clock));String link=r.client.readLink();int calls=r.transport.calls,writes=r.store.writes;
      LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);
      check(restored.status().state==LiveCoordinator.State.PAUSED&&restored.status().recoveryAvailable,"unStopped journal restores as explicit recovery pause");check(!restored.status().consentActive&&!restored.status().pendingRevocation&&!restored.status().locallyStopped,"recovery pause is neither consent nor Stop");check(r.transport.calls==calls&&r.store.writes==writes,"constructor restoration does zeroHTTP andno gratuitous rewrite");check(link.equals(restored.readLink()),"paused recovery preserves SAME recipient link");
      restored.retryConnection();check(r.transport.calls==calls,"ordinary retry cannot auto resume restored consent");expectIOException(()->restored.send(fix(r.clock)),"paused restoration cannot send");restored.resumeExisting(()->false);check(r.transport.calls==calls,"canceled explicit resume performs zeroHTTP");
      r.transport.serverSequence=12;r.transport.state="paused";restored.resumeExisting(()->true);check(restored.status().consentActive&&restored.status().lastSequence==12,"explicit resume reconciles durable server sequence");check(!restored.status().latestAcknowledgementFresh&&r.transport.allocations==1,"resume does not invent locationdelivery orreplacement allocation");restored.send(fix(r.clock));check(r.transport.lastPutSequence==13,"first resumed freshfix uses strictly newer sequence");check(link.equals(restored.readLink()),"explicit resume keeps exact link");check(r.store.saved.toMap().size()==19&&!r.store.saved.toMap().containsValue(r.transport.preparedToken),"schema19 management excludes prepared capability");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);int calls=r.transport.calls;String link=r.client.readLink();r.client.pauseForRecovery();
      check(r.transport.calls==calls&&r.transport.deletes==0,"system pause never sends HTTP orremoteStop");check(r.client.status().recoveryAvailable&&!r.store.saved.locallyStopped,"system pause retains unStopped journal");check(link.equals(r.client.readLink()),"system pause retains link");r.client.resumeExisting(()->true);check(r.client.status().consentActive&&r.transport.allocations==1,"explicit same-process resume reuses original session");r.client.markStoppedPending();calls=r.transport.calls;r.client.resumeExisting(()->true);check(r.transport.calls==calls&&!r.client.status().consentActive,"explicitStop journal cannot be resumed");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.lostCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"prepared create ACK lost");String key=r.store.saved.creationKey,writer=r.store.saved.writeToken;LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);int calls=r.transport.calls;
      expectIOException(()->restored.resumeExisting(()->true),"unknownID explicit recovery requires prepared access");check(r.transport.calls==calls,"unknownID missingaccess fails before network");restored.resumeExisting(r.transport.access(),()->true);check(restored.status().consentActive&&r.transport.allocations==1,"unknownID explicit recovery finds originalallocation");check(r.store.saved.creationKey.equals(key)&&r.store.saved.writeToken.equals(writer),"unknownID recovery preserves creationidentity");check(r.transport.issues==1,"unexpired acknowledged ticket neednot be reissued");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.lostAdmission=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"admission ACK lost");check(r.transport.creates==0&&r.store.saved.admissionTicket==null&&r.store.saved.admissionEpoch==null,"lost admission ACK cannot have dispatched create");LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);restored.resumeExisting(r.transport.access(),()->true);check(r.transport.expectedEpochs.size()==2&&r.transport.expectedEpochs.get(0)==null&&r.transport.expectedEpochs.get(1)==null,"fresh null epoch retry only before any acknowledged admission");check(r.transport.allocations==1&&restored.status().consentActive,"lost admission recovery explicitly allocates once");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.store.failAt=2;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"admission journal write fault");check(r.transport.creates==0,"ticket andepoch must persist BEFORE anycreatePOST");check(!r.client.status().consentActive,"admission journal failure failsclosed");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.offlineCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"prepared preallocation outage");String key=r.store.saved.creationKey,writer=r.store.saved.writeToken;r.clock.advance(180000);r.transport.offlineCreate=false;r.client.retryConnection();
      check(r.client.status().consentActive&&r.transport.allocations==1,"expired preparedticket automaticallyrefreshes sameconsent");check(r.transport.expectedEpochs.size()==2&&Long.valueOf(0).equals(r.transport.expectedEpochs.get(1)),"expiredticket refresh keeps pinned integer epoch");check(key.equals(r.store.saved.creationKey)&&writer.equals(r.store.saved.writeToken),"expiredticket refresh preserves creationcredentials");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.offlineCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"mismatched epoch fixture");r.clock.advance(180000);r.transport.offlineCreate=false;r.transport.issuedEpochOverride=1;int before=r.transport.creates;expectIOException(()->r.client.retryConnection(),"pinned issuance reply cannot change epoch");check(r.transport.creates==before+1&&r.transport.allocations==0,"mismatched refreshed epoch cannotdispatch newticketPOST");check(Long.valueOf(0).equals(r.store.saved.admissionEpoch),"mismatched issuance never repins journal");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;java.util.concurrent.atomic.AtomicBoolean guard=new java.util.concurrent.atomic.AtomicBoolean(true);r.transport.afterAdmission=()->guard.set(false);r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),guard::get);
      check(r.transport.creates==0&&r.transport.deletes==0,"guard lost duringadmission prevents createandStopHTTP");check(r.client.status().state==LiveCoordinator.State.PAUSED&&!r.client.status().pendingRevocation,"guard loss pauses for explicit recovery ratherthan Stop");check(r.store.saved.admissionTicket==null&&r.store.saved.admissionEpoch==null,"canceled issuance isnot journaled fordispatch");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.lostCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"old unknowncreate fixture");r.transport.creationKey=null;r.transport.created=false;r.transport.state="stopped";r.transport.slotEpoch++;r.clock.advance(180000);LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);expectIOException(()->restored.resumeExisting(r.transport.access(),()->true),"old uncertain intent cannot cross resetepoch");check(r.transport.allocations==1&&!restored.status().consentActive,"cross-epoch recovery cannot createanother session");check(r.transport.expectedEpochs.stream().skip(1).noneMatch(Objects::isNull),"uncertain create neverfallsback to freshnull epoch");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;long end=r.clock.wall+120000;r.client.startPreparedUntil("https://test.invalid",end,r.transport.access(),()->true);LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);r.transport.configTransitMs=90000;restored.resumeExisting(()->true);check(restored.status().consentActive,"knownresume has remaining30s afterconfigtransit");r.clock.advance(30000);check(!restored.status().consentActive,"knownresume cannotextend chosenend byresponse transit");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;long end=r.clock.wall+120000;r.transport.lostAdmission=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",end,r.transport.access(),()->true),"unknownresume chosenend fixture");LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);r.transport.configTransitMs=90000;restored.resumeExisting(r.transport.access(),()->true);check(restored.status().consentActive,"unknownresume has remaining30s afterconfigtransit");r.clock.advance(30000);check(!restored.status().consentActive,"unknownresume cannotoverwrite conservative chosenend cap");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);r.client.send(fix(r.clock));LiveCoordinator restored=new LiveCoordinator(r.transport,r.store,r.clock,true);r.transport.serverSequence=0;expectIOException(()->restored.resumeExisting(()->true),"durable sequence regression rejected");check(!restored.status().consentActive&&r.transport.puts==1,"regressed serversequence cannotupload");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);r.transport.putEntered=new CountDownLatch(1);r.transport.putRelease=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();Thread worker=new Thread(()->{try{r.client.send(fix(r.clock));}catch(Throwable e){error.set(e);}});worker.start();check(r.transport.putEntered.await(2,TimeUnit.SECONDS),"pause race entered update");r.client.pauseForRecovery();r.transport.putRelease.countDown();worker.join(3000);check(!worker.isAlive()&&error.get()==null,"inflight update mayfinish whilepaused");check(r.client.status().recoveryAvailable&&!r.client.status().consentActive&&r.transport.deletes==0,"late updatecannotundo pauseorcallStop");r.client.resumeExisting(()->true);r.transport.putEntered=null;r.transport.putRelease=null;r.client.send(fix(r.clock));check(r.transport.lastPutSequence==2,"resume reconciles update acceptedwhilepause wasinflight");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);r.client.pauseForRecovery();LiveCoordinator.PreparedAccess other=new LiveCoordinator.PreparedAccess("0000000000000000000002",r.transport.preparedToken);int calls=r.transport.calls;check(r.client.canReplacePrepared("https://test.invalid",r.transport.slotId)&&!r.client.canReplacePrepared("https://test.invalid",other.slotId),"same-slot replacement allowed whilepaused, differentscope blocked");check(!r.client.preparedResetMatches("https://test.invalid",other),"otherpreparedslot cannot match localjournal");expectIOException(()->r.client.resetPreparedCurrent("https://test.invalid",other,()->true),"otherpreparedslot resetblocked locally");check(r.transport.calls==calls,"wrongslot reset sendsnoHTTP");r.client.resetPreparedCurrent("https://test.invalid",r.transport.access(),()->false);check(r.transport.calls==calls&&r.client.status().recoveryAvailable,"canceled ownreset keeps recoverymetadata");r.client.resetPreparedCurrent("https://test.invalid",r.transport.access(),()->true);check(r.store.saved==null&&!r.client.status().recoveryAvailable,"confirmed ownreset clears oldsession management");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.offlineCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"unknown preparedStop preallocationoutage");r.client.markStoppedPending();r.transport.offlineCreate=false;int posts=r.transport.creates;
      expectIOException(()->r.client.retryPendingRevocation(),"unknownpreparedStop withoutprivategrant stayspending");check(r.transport.creates==posts&&r.client.status().pendingRevocation,"missingpreparedgrant cannotcreateemptyorphan orclaimStop");
      r.client.retryPendingRevocation(r.transport.access());check(r.transport.creates==posts&&r.transport.allocations==0,"ownslotreset Stop neverPOSTsunknowncreation");check(!r.client.status().pendingRevocation&&r.store.saved==null&&r.transport.slotEpoch==1,"confirmedreset durablyinvalidatesunknownoldticket");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.lostCreate=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"allocatedunknownStop lostACK");r.client.markStoppedPending();int posts=r.transport.creates;r.client.retryPendingRevocation(r.transport.access());check(r.transport.creates==posts&&r.transport.allocations==1,"allocatedunknownStop resolves viaresetnotidempotentPOST");check(r.transport.state.equals("stopped")&&r.store.saved==null,"unknownallocatedslot reset endsactualsession");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.lostAdmission=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"prePOSTStop proof fixture");r.client.markStoppedPending();int calls=r.transport.calls;r.client.retryPendingRevocation();check(r.transport.calls==calls&&r.store.saved==null,"noacknowledgedticket/epoch provesnoPOSTpossible withoutanynetworkreset");
    }
    {
      Rig r=rig();r.transport.admissionRequired=true;r.transport.resetEntered=new CountDownLatch(1);r.transport.resetRelease=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();Thread worker=new Thread(()->{try{r.client.resetPreparedCurrent("https://test.invalid",r.transport.access(),()->true);}catch(Throwable e){error.set(e);}});worker.start();check(r.transport.resetEntered.await(2,TimeUnit.SECONDS),"emptyengine reset reached controlled in-flightgate");check(!r.client.canReplacePrepared("https://test.invalid",r.transport.slotId),"preparedimport blocked duringemptyengine in-flightreset");boolean refused=false;try{r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true);}catch(IOException expected){refused=true;}finally{r.transport.resetRelease.countDown();}worker.join(3000);check(refused,"freshStartcannotrace in-flightownslotreset onemptyengine");check(!worker.isAlive()&&error.get()==null&&r.transport.allocations==0,"reset doesnotclobbera concurrentlyallocatedfreshsession");
    }
    {
      Rig r=rig();check(r.client.canReplacePrepared("https://test.invalid",r.transport.slotId),"idleengine permits explicitpreparedimport");check(!r.client.canReplacePrepared("https://user:secret@test.invalid",r.transport.slotId),"invalidimportorigin rejectedwithoutHTTP");r.transport.offlineConfig=true;expectIOException(()->r.client.startPreparedUntil("https://test.invalid",0,r.transport.access(),()->true),"unresolvedpreconfigpreparedintent");check(!r.client.canReplacePrepared("https://test.invalid",r.transport.slotId),"preparedimport blocked beforependingintenthasjournaledscope");
    }
    for(int http:new int[]{408,425,429,500,503}){
      Rig r=rig();r.client.start("https://test.invalid",0);r.transport.putHttpStatus=http;expectIOException(()->r.client.send(fix(r.clock)),"transientHTTP fixture");check(r.client.status().consentActive&&!r.client.status().pendingRevocation,"typed transientHTTP keeps consent: "+http);
    }
    for(int http:new int[]{401,403,404,409}){
      Rig r=rig();r.client.start("https://test.invalid",0);r.transport.putHttpStatus=http;expectIOException(()->r.client.send(fix(r.clock)),"permanentHTTP fixture");check(!r.client.status().consentActive&&r.client.status().recoveryAvailable&&!r.client.status().pendingRevocation,"authorization/protocolHTTP pauses for explicitrevalidation withoutStop: "+http);
    }
    System.out.println("PASS "+assertions+" independent sender journal/Stop/expiry/capability assertions (synthetic host only)");
  }
}
