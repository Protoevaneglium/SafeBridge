package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** V2 host tests: synthetic points only; no Android/location/network claims. */
public final class LiveCoordinatorTest {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    interface Checked {void run() throws Exception;}
    static void fails(Checked action,String label) throws Exception {try{action.run();throw new AssertionError(label);}catch(IOException expected){checks++;}}
    static final class Time implements Clock {long wall=1800000000000L,elapsed=100000;
        public long nowMs(){return wall;}public long elapsedNowMs(){return elapsed;}
        void advance(long ms){wall+=ms;elapsed+=ms;}}
    static final class MemoryStore implements Store {
        Management value;int saves,clears;boolean failNext,failClearNext;
        public synchronized Management load(){return value;}
        public synchronized void save(Management m) throws IOException {
            if(failNext){failNext=false;throw new IOException("Synthetic store failure");}
            Set<String> allowed=new HashSet<>(Arrays.asList("schema","endpoint","generation","id","readToken","writeToken","revokeToken","creationKey","createPermit","admissionTicket","managementReceipt","preparedSlotId","lastSequence","admissionEpoch","durationSeconds","expiresAtMs","leaseUntilMs","locallyStopped","creationUncertain"));
            check(m.toMap().keySet().equals(allowed),"management store contains only approved non-location metadata");
            value=Management.fromMap(m.toMap());saves++;
        }
        public synchronized void clear() throws IOException {if(failClearNext||failNext){failClearNext=false;failNext=false;throw new IOException("Synthetic clear failure");}value=null;clears++;}
    }
    static final class Slot {
        final String id,token;long epoch=1;String currentKey,currentId;
        Slot(String id,String token){this.id=id;this.token=token;}
    }
    static final class Backend implements Transport {
        final Time clock;final MemoryStore store;final Map<String,Map<String,Object>> sessions=new HashMap<>();
        final Map<String,Slot> slots=new HashMap<>();final Map<String,Map<String,Object>> tickets=new HashMap<>();
        String generation="G".repeat(22);int creates,puts,deletes,gets,admissions,resets;long delayResponseMs,postProcessingDelayMs,statusProcessingDelayMs;
        boolean loseCreateAck,losePutAck,loseAdmissionAck,offlineDelete,invalidPutResponse,failAckStore,changedExpiry,admissionRequired;
        IOException putFailure,configFailure,postFailure;CountDownLatch configEntered,configRelease,putEntered,putRelease;
        Backend(Time c,MemoryStore s){clock=c;store=s;}
        PreparedAccess prepare(String id,String token){slots.put(id,new Slot(id,token));return new PreparedAccess(id,token);}
        static Map<String,Object> map(Object...values){Map<String,Object> result=new HashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
        public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String receipt,Map<String,Object> body) throws IOException {
            if(path.equals("/v1/config")){gets++;waitAt(configEntered,configRelease);if(configFailure!=null)throw configFailure;
                return map("protocolVersion",3,"durableSessionState",true,"preparedAdmission",true,"admissionRequired",admissionRequired,"untilStopSupported",true,"generation",this.generation,"createPermit","P".repeat(32)+"_"+clock.wall,"serverTimeMs",clock.wall,"minDurationSeconds",1,"maxDurationSeconds",Integer.MAX_VALUE,"leaseMs",90000,"recentMs",30000);}
            if(path.equals("/v1/admission")||path.equals("/v1/admission/current")){
                Slot slot=slots.get(body.get("slotId"));if(slot==null||!slot.token.equals(token))throw new HttpFailure(404,"not_found");
                if(method.equals("DELETE")){resets++;boolean had=slot.currentId!=null;if(had)for(Map<String,Object> row:sessions.values())if(row.get("id").equals(slot.currentId))row.put("state","stopped");slot.epoch++;slot.currentId=null;slot.currentKey=null;
                    return map("generation",this.generation,"slotId",slot.id,"state",had?"stopped":"none","serverTimeMs",clock.wall,"slotEpoch",slot.epoch);}
                check(store.value!=null,"management durable before prepared admission issuance");admissions++;
                Object expected=body.get("expectedSlotEpoch");if(expected!=null&&((Number)expected).longValue()!=slot.epoch)throw new HttpFailure(409,"admission_epoch_changed");
                String key=(String)body.get("creationKey");if(slot.currentKey!=null&&!slot.currentKey.equals(key))throw new HttpFailure(409,"prepared_slot_active");
                String ticket="PREP_"+slot.id+"_"+slot.epoch+"_"+key;tickets.put(ticket,map("slotId",slot.id,"creationKey",key,"slotEpoch",slot.epoch));
                if(loseAdmissionAck){loseAdmissionAck=false;throw new ConnectionFailure();}
                return map("generation",this.generation,"slotId",slot.id,"creationKey",key,"admissionTicket",ticket,"expiresAtMs",clock.wall+60000,"slotEpoch",slot.epoch);
            }
            if(method.equals("POST")){
                if(postFailure!=null)throw postFailure;if(!this.generation.equals(generation))throw new HttpFailure(404,"not_found");check(store.value!=null,"capabilities durable before creation");
                String key=(String)body.get("creationKey");Map<String,Object> session=sessions.get(key);
                if(session==null){
                    String permit=(String)body.get("createPermit");long issued=Long.parseLong(permit.substring(permit.lastIndexOf('_')+1));if(clock.wall-issued>=60000&&!permit.startsWith("LONG_"))throw new HttpFailure(400,"invalid_create_permit");
                    if(postProcessingDelayMs>0){clock.advance(postProcessingDelayMs);postProcessingDelayMs=0;}
                    long expiry=((Number)body.get("requestedExpiresAtMs")).longValue();if(expiry>0&&expiry<=clock.wall)throw new HttpFailure(400,"chosen_end_elapsed");
                    Slot slot=null;if(admissionRequired){Map<String,Object> binding=tickets.get(body.get("admissionTicket"));if(binding==null)throw new HttpFailure(403,"invalid_admission_ticket");slot=slots.get(binding.get("slotId"));
                        if(!key.equals(binding.get("creationKey"))||slot.epoch!=((Number)binding.get("slotEpoch")).longValue())throw new HttpFailure(403,"invalid_admission_ticket");if(slot.currentKey!=null&&!slot.currentKey.equals(key))throw new HttpFailure(409,"prepared_slot_active");}
                    creates++;String id="session_"+String.format(Locale.ROOT,"%016d",creates);
                    session=map("id",id,"generation",generation,"state","waiting_for_fix","expiresAtMs",expiry,"leaseUntilMs",expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000),"managementReceipt","R".repeat(43),"lastSequence",0L,"writer",token,"reader",body.get("readToken"),"revoker",body.get("revokeToken"),"slotId",slot==null?null:slot.id);
                    sessions.put(key,session);if(slot!=null){slot.currentKey=key;slot.currentId=id;}
                }
                if(loseCreateAck){loseCreateAck=false;throw new ConnectionFailure();}if(failAckStore){failAckStore=false;store.failNext=true;}return publicState(session);
            }
            Map<String,Object> session=null;for(Map<String,Object> candidate:sessions.values())if(path.contains((String)candidate.get("id")))session=candidate;
            if(session==null||!session.get("generation").equals(generation))throw new HttpFailure(404,"not_found");refresh(session);
            if(method.equals("PUT")){
                if(!session.get("writer").equals(token))throw new HttpFailure(404,"not_found");puts++;waitAt(putEntered,putRelease);if(putFailure!=null)throw putFailure;
                if(session.get("state").equals("stopped")||session.get("state").equals("expired"))throw new HttpFailure(409,"session_ended");
                long next=((Number)body.get("sequence")).longValue();if(next<=((Number)session.get("lastSequence")).longValue())throw new HttpFailure(409,"out_of_order");session.put("lastSequence",next);
                session.put("state","active");long expiry=((Number)session.get("expiresAtMs")).longValue();session.put("leaseUntilMs",expiry==0?clock.wall+90000:Math.min(expiry,clock.wall+90000));
                Map<String,Object> response=publicState(session);if(invalidPutResponse)response.put("generation","wrong_generation");if(changedExpiry)response.put("expiresAtMs",((Number)response.get("expiresAtMs")).longValue()+1);if(delayResponseMs>0){clock.advance(delayResponseMs);delayResponseMs=0;}if(losePutAck){losePutAck=false;throw new ConnectionFailure();}return response;
            }
            if(method.equals("DELETE")){
                check(store.value!=null&&store.value.locallyStopped,"durable local stop precedes remote revoke");if(!session.get("revoker").equals(token))throw new HttpFailure(404,"not_found");check(receipt!=null,"management receipt accompanies revocation");deletes++;if(offlineDelete)throw new ConnectionFailure();
                if(!session.get("state").equals("expired"))session.put("state","stopped");closeSlot(session);return publicState(session);
            }
            if(!session.get("writer").equals(token)&&!session.get("revoker").equals(token))throw new HttpFailure(404,"not_found");check(receipt!=null,"management receipt accompanies status");if(statusProcessingDelayMs>0){clock.advance(statusProcessingDelayMs);statusProcessingDelayMs=0;}return publicState(session);
        }
        void closeSlot(Map<String,Object> row){Object id=row.get("slotId");if(id==null)return;Slot slot=slots.get(id);if(slot.currentId!=null&&slot.currentId.equals(row.get("id"))){slot.epoch++;slot.currentId=null;slot.currentKey=null;}}
        void refresh(Map<String,Object> session){String state=(String)session.get("state");if(state.equals("stopped")||state.equals("expired"))return;long expiry=((Number)session.get("expiresAtMs")).longValue();if(expiry>0&&clock.wall>=expiry){session.put("state","expired");closeSlot(session);}else if(clock.wall>=((Number)session.get("leaseUntilMs")).longValue())session.put("state","paused");}
        Map<String,Object> publicState(Map<String,Object> session){refresh(session);Map<String,Object> result=new HashMap<>();for(String key:Arrays.asList("id","generation","state","expiresAtMs","leaseUntilMs","managementReceipt","lastSequence"))result.put(key,session.get(key));result.put("serverTimeMs",clock.wall);return result;}
        static void waitAt(CountDownLatch entered,CountDownLatch release) throws IOException {if(entered==null)return;entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic wait timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
    }
    static LiveCoordinator create(Backend backend,MemoryStore store,Time time) throws IOException{return new LiveCoordinator(backend,store,time,true);}
    static Fix fix(Time time){return new Fix(0.125,-0.25,50,time.wall,time.elapsed,true,false);}
    public static void main(String[] args) throws Exception {
        Time time=new Time();MemoryStore store=new MemoryStore();Backend backend=new Backend(time,store);LiveCoordinator c=create(backend,store,time);
        check(c.status().state==State.IDLE,"initial idle");check(!c.hasManagedSession(),"idle presence check has no managed record");check(c.canReplacePrepared("https://example.test","S".repeat(22)),"idle engine accepts valid prepared replacement scope");check(!c.canReplacePrepared("http://untrusted.example","S".repeat(22))&&!c.canReplacePrepared("https://example.test","bad"),"invalid replacement endpoint or slot fails closed");c.start("https://example.test",0,()->false);check(backend.gets==0&&c.status().state==State.STOPPED,"withdrawn start guard prevents HTTP");
        fails(()->c.start("http://remote.example",0),"cleartext rejected");fails(()->c.start("https://u:p@example.test",0),"userinfo rejected");fails(()->c.start("https://example.test/path",0),"path rejected");fails(()->c.start("https://example.test",-1),"negative duration rejected");
        check(isTransient(new ConnectionFailure())&&isTransient(new HttpFailure(429,"rate_limited"))&&isTransient(new HttpFailure(503,"capacity_reached")),"typed transport/server transients classified");
        check(!isTransient(new IOException("store"))&&!isTransient(new ProtocolFailure())&&!isTransient(new HttpFailure(403,"not_found")),"storage/protocol/auth never classified transient");
        c.start("https://example.test",0);Management active=store.value;String link=c.readLink();
        check(active.expiresAtMs==0&&c.status().consentActive,"until Stop has no chosen expiry");check(active.readToken.length()==43&&!active.readToken.equals(active.writeToken)&&!active.readToken.equals(active.revokeToken)&&!active.writeToken.equals(active.revokeToken),"independent 256-bit scoped capabilities");
        check(link.contains(active.readToken)&&!link.contains(active.writeToken)&&!link.contains(active.revokeToken)&&!link.contains(active.managementReceipt),"only read capability in fragment URL");
        Map<String,Object> overflow=active.toMap();overflow.put("durationSeconds",4294967356L);fails(()->Management.fromMap(overflow),"persisted duration overflow rejected");
        c.send(fix(time));check(c.status().latestAcknowledgementFresh&&!c.status().deliveryUnconfirmed,"fresh acknowledged synthetic upload distinguished");
        time.advance(91001);backend.putFailure=new ConnectionFailure();fails(()->c.send(fix(time)),"offline upload surfaces typed error");check(c.status().consentActive&&c.status().deliveryUnconfirmed&&!c.status().latestAcknowledgementFresh&&c.status().failureCode.equals("connection_unverified"),"outage beyond data TTL preserves consent with unverified delivery");
        backend.putFailure=null;c.refreshStatus();check(c.status().serverPaused&&c.status().consentActive,"authenticated paused state is nonterminal");c.send(fix(time));check(c.status().latestAcknowledgementFresh&&c.readLink().equals(link)&&c.status().id.equals(active.id),"fresh upload resumes same ID and link");
        time.advance(3700000);check(c.status().consentActive,"until Stop survives old one-hour limit");c.send(fix(time));check(c.readLink().equals(link),"long reconnect does not create replacement ID");
        for(int status:new int[]{408,425,429,500,503}){backend.putFailure=new HttpFailure(status,"synthetic");fails(()->c.send(fix(time)),"transient HTTP surfaced");check(c.status().consentActive&&c.status().failureCode.equals("connection_unverified"),"transient HTTP retains consent");}
        backend.putFailure=null;
        for(int status:new int[]{400,409}){backend.putFailure=new HttpFailure(status,"stale_point");try{c.send(fix(time));throw new AssertionError("Expected stale sample");}catch(StaleFixFailure expected){checks++;}check(c.status().consentActive,"server-aged sample drops without Stop");}backend.putFailure=null;c.send(fix(time));
        fails(()->c.send(new Fix(0,0,10,time.wall,time.elapsed-30001,false,false)),"old local sample rejected");check(c.status().consentActive,"old sample does not withdraw consent");
        c.markStoppedPending();backend.offlineDelete=true;fails(c::retryPendingRevocation,"offline Stop remains pending");int puts=backend.puts;fails(()->c.send(fix(time)),"Stop prevents reconnect upload");check(backend.puts==puts&&!c.status().consentActive&&c.readLink()==null,"offline Stop suppresses all later writes and link sharing");
        backend.offlineDelete=false;c.retryPendingRevocation();check(store.value==null,"acknowledged Stop clears management");
        long originalEnd=time.wall+120000;backend.configFailure=new ConnectionFailure();fails(()->c.startUntil("https://example.test",originalEnd,null,()->true),"absolute chosen end survives pre-config outage");time.advance(90001);backend.configFailure=null;c.retryConnection();check(store.value.expiresAtMs==originalEnd,"absolute remote deadline unchanged after delayed creation");c.stop();
        long relativeEnd=time.wall+120000;backend.configFailure=new ConnectionFailure();fails(()->c.start("https://example.test",120),"relative chosen duration config outage");time.advance(90001);backend.configFailure=null;c.retryConnection();check(store.value.expiresAtMs==relativeEnd,"relative API uses remaining initial elapsed budget instead of postponing expiry");c.stop();
        long delayedPostEnd=time.wall+120000;backend.postProcessingDelayMs=90000;c.startUntil("https://example.test",delayedPostEnd,null,()->true);
        check(c.status().consentActive&&store.value.expiresAtMs==delayedPostEnd,"90-second server processing does not double-subtract hard consent time");time.advance(30001);check(!c.status().consentActive&&c.status().state==State.EXPIRED,"late POST still stops at original chosen end");c.retryPendingRevocation();
        long delayedStatusEnd=time.wall+120000;c.startUntil("https://example.test",delayedStatusEnd,null,()->true);backend.statusProcessingDelayMs=90000;c.refreshStatus();
        check(c.status().consentActive&&store.value.expiresAtMs==delayedStatusEnd,"late status response preserves remaining original consent time");time.advance(30001);check(!c.status().consentActive,"late status never extends original deadline");c.retryPendingRevocation();
        c.start("https://example.test",1);time.advance(1001);int savesBeforePresence=store.saves;check(c.hasManagedSession()&&store.saves==savesBeforePresence&&!store.value.locallyStopped,"presence check does not expire or journal a managed record");check(c.status().state==State.EXPIRED&&!c.status().consentActive,"chosen one-second expiry remains terminal");c.retryPendingRevocation();
        c.start("https://example.test",Integer.MAX_VALUE);check(c.status().expiresAtMs>time.wall+3600000,"chosen positive duration not capped to old hour");c.stop();
        backend.configFailure=new ConnectionFailure();fails(()->c.start("https://example.test",0),"config outage surfaced");check(c.status().consentActive&&store.value==null,"consent retained while configuration unavailable");backend.configFailure=null;c.retryConnection();check(c.status().active,"configuration reconnect continues current consent");c.stop();
        backend.loseCreateAck=true;fails(()->c.start("https://example.test",0),"lost creation acknowledgement surfaced");Management unknown=store.value;int created=backend.creates;
        check(unknown.id==null&&!unknown.locallyStopped&&c.status().consentActive,"ambiguous creation retains current consent and exact capability journal");c.retryConnection();check(backend.creates==created&&c.status().active,"idempotent reconnect resolves same creation");c.stop();
        backend.loseCreateAck=true;fails(()->c.start("https://example.test",0),"second lost creation ack");c.markStoppedPending();c.retryPendingRevocation();check(!c.status().consentActive&&store.value==null,"Stop resolves ambiguous creation only to revoke");
        backend.admissionRequired=true;PreparedAccess prepared=backend.prepare("S".repeat(22),"T".repeat(43));
        backend.postFailure=new HttpFailure(503,"capacity_reached");fails(()->c.startPreparedUntil("https://example.test",0,prepared,()->true),"prepared capacity outage retains consent");
        String pendingKey=store.value.creationKey;String pendingWriter=store.value.writeToken;Long pinned=store.value.admissionEpoch;
        check(pinned!=null&&store.value.preparedSlotId.equals(prepared.slotId),"issued ticket and epoch are durable before creation");check(c.canReplacePrepared("https://example.test/",prepared.slotId),"known starting management permits matching prepared replacement");check(!c.canReplacePrepared("https://other.example",prepared.slotId)&&!c.canReplacePrepared("https://example.test","U".repeat(22)),"known management rejects different endpoint or slot replacement");
        time.advance(61001);backend.postFailure=null;c.retryConnection();check(c.status().active&&store.value.creationKey.equals(pendingKey)&&store.value.writeToken.equals(pendingWriter)&&store.value.admissionEpoch.equals(pinned),"public-permit refresh preserves prepared identity and epoch pin");
        check(store.value.admissionTicket==null&&!store.value.toMap().containsValue(prepared.token),"confirmed management scrubs short ticket and never stores prepared secret");c.stop();
        backend.loseAdmissionAck=true;fails(()->c.startPreparedUntil("https://example.test",0,prepared,()->true),"lost admission acknowledgement surfaced");check(store.value.admissionTicket==null&&store.value.admissionEpoch==null,"no creation attempted before durable ticket and epoch");int beforeAdmissionRetry=backend.creates;c.retryConnection();check(c.status().active&&backend.creates==beforeAdmissionRetry+1,"lost admission ack recovery allocates once");c.stop();
        backend.loseCreateAck=true;fails(()->c.startPreparedUntil("https://example.test",0,prepared,()->true),"unknown prepared Stop setup");c.markStoppedPending();int beforeUnknownStop=backend.creates;
        fails(c::retryPendingRevocation,"unknown prepared Stop requires matching private access");check(c.status().pendingRevocation&&backend.creates==beforeUnknownStop,"missing prepared access never creates merely to revoke");c.retryPendingRevocation(prepared);check(store.value==null&&backend.creates==beforeUnknownStop,"matching own-slot reset resolves unknown Stop without creation POST");
        backend.loseCreateAck=true;fails(()->c.startPreparedUntil("https://example.test",0,prepared,()->true),"prepared lost creation acknowledgement");String unfinishedKey=store.value.creationKey;
        c.pauseForRecovery();LiveCoordinator unfinished=create(backend,store,time);int beforeExplicit=backend.gets;
        check(unfinished.status().recoveryAvailable&&!unfinished.status().consentActive&&!unfinished.status().locallyStopped&&backend.gets==beforeExplicit,"unfinished process restoration is paused without hidden stop or HTTP");
        unfinished.resumeExisting(prepared,()->false);check(backend.gets==beforeExplicit,"false resume guard prevents all recovery HTTP");
        unfinished.resumeExisting(prepared,()->true);check(unfinished.status().active&&store.value.creationKey.equals(unfinishedKey),"explicit prepared resume resolves same unfinished creation");unfinished.stop();
        LiveCoordinator c2=create(backend,store,time);c2.startPreparedUntil("https://example.test",0,prepared,()->true);String recoverId=c2.status().id;String recoverLink=c2.readLink();c2.send(fix(time));backend.losePutAck=true;fails(()->c2.send(fix(time)),"lost point acknowledgement");
        LiveCoordinator recovered=create(backend,store,time);int beforeRecovery=backend.gets;
        check(recovered.status().state==State.PAUSED&&recovered.status().recoveryAvailable&&!recovered.status().pendingRevocation&&!recovered.status().locallyStopped&&recovered.readLink().equals(recoverLink),"known saved link remains paused and viewable, not stopped");
        fails(()->recovered.send(fix(time)),"paused restored sender cannot upload before explicit resume");check(backend.gets==beforeRecovery,"loading and inspecting recovery performs no HTTP");
        recovered.resumeExisting(()->true);check(recovered.status().active&&recovered.status().lastSequence==2&&!recovered.status().latestAcknowledgementFresh,"explicit status reconciliation seeds last sequence without replaying a point");
        recovered.send(fix(time));check(recovered.status().lastSequence==3&&recovered.status().id.equals(recoverId)&&recovered.readLink().equals(recoverLink),"resumed fresh upload uses greater sequence with same ID/link");
        recovered.pauseForRecovery();check(recovered.hasManagedSession(),"paused record remains present without status side effects");check(recovered.status().recoveryAvailable&&!store.value.locallyStopped,"system pause preserves un-stopped metadata");check(recovered.canReplacePrepared("https://example.test",prepared.slotId),"paused saved session permits only matching prepared replacement");
        check(recovered.preparedResetMatches("https://example.test",prepared),"matching prepared reset admitted");PreparedAccess other=backend.prepare("U".repeat(22),"V".repeat(43));
        check(!recovered.preparedResetMatches("https://example.test",other),"unrelated prepared reset refused");int resets=backend.resets;fails(()->recovered.resetPreparedCurrent("https://example.test",other,()->true),"wrong-slot reset fails locally");check(backend.resets==resets&&recovered.status().recoveryAvailable,"wrong-slot reset cannot stop saved session");
        recovered.resetPreparedCurrent("https://example.test",prepared,()->true);check(store.value==null&&!recovered.status().recoveryAvailable&&recovered.status().serverState.equals("prepared_slot_reset"),"explicit own-slot reset ends original and clears only its management");
        LiveCoordinator c3=create(backend,store,time);c3.startPreparedUntil("https://example.test",0,prepared,()->true);c3.markStoppedPending();LiveCoordinator stopped=create(backend,store,time);int stoppedGets=backend.gets;stopped.resumeExisting(prepared,()->true);check(!stopped.status().consentActive&&stopped.status().pendingRevocation&&backend.gets==stoppedGets,"stopped journal can never resume");check(stopped.canReplacePrepared("https://example.test",prepared.slotId),"pending Stop journal permits matching prepared replacement");stopped.retryPendingRevocation();
        backend.admissionRequired=false;
        LiveCoordinator c4=create(backend,store,time);c4.start("https://example.test",0);int requests=backend.gets;LiveCoordinator restored=create(backend,store,time);check(!restored.status().consentActive&&restored.status().recoveryAvailable&&backend.gets==requests,"process recreation never silently resumes or contacts server");restored.resumeExisting(()->true);restored.stop();
        restored.start("https://example.test",0);backend.invalidPutResponse=true;fails(()->restored.send(fix(time)),"wrong response generation rejected");check(!restored.status().consentActive&&restored.status().recoveryAvailable&&!store.value.locallyStopped,"protocol fault pauses collection without implicit Stop");backend.invalidPutResponse=false;restored.resumeExisting(()->true);restored.stop();
        restored.start("https://example.test",60);backend.changedExpiry=true;fails(()->restored.send(fix(time)),"immutable expiry cannot extend");check(!restored.status().consentActive&&restored.status().recoveryAvailable,"expiry tampering safely pauses collection");backend.changedExpiry=false;restored.resumeExisting(()->true);restored.stop();
        backend.failAckStore=true;fails(()->restored.start("https://example.test",0),"ack journal failure surfaced");check(!restored.status().consentActive&&restored.status().recoveryAvailable,"storage fault safely pauses despite remote create ack");restored.resumeExisting(()->true);restored.stop();
        restored.start("https://example.test",0);restored.markStoppedPending();store.failClearNext=true;fails(restored::retryPendingRevocation,"local cleanup failure surfaced");check(restored.status().serverState.equals("stopped")&&restored.status().failureCode.equals("local_cleanup_pending"),"remote-ended fact retained across local cleanup error");restored.retryPendingRevocation();
        restored.start("https://example.test",0);restored.markStoppedPending();backend.sessions.clear();backend.generation="H".repeat(22);restored.retryPendingRevocation();check(store.value==null&&restored.status().serverState.equals("unavailable"),"fresh generation confirms old link unavailable without claiming revoke");
        Time raceTime=new Time();MemoryStore raceStore=new MemoryStore();Backend raceBackend=new Backend(raceTime,raceStore);LiveCoordinator race=create(raceBackend,raceStore,raceTime);ExecutorService executor=Executors.newSingleThreadExecutor();
        try{raceBackend.configEntered=new CountDownLatch(1);raceBackend.configRelease=new CountDownLatch(1);Future<Status> pending=executor.submit(()->race.start("https://example.test",0));check(raceBackend.configEntered.await(2,TimeUnit.SECONDS),"config race barrier reached");check(!race.canReplacePrepared("https://example.test","S".repeat(22)),"unknown in-flight start blocks prepared replacement");race.markStoppedPending();raceBackend.configRelease.countDown();pending.get(3,TimeUnit.SECONDS);check(raceBackend.creates==0,"Stop during config prevents creation");raceBackend.configEntered=null;race.start("https://example.test",0);raceBackend.putEntered=new CountDownLatch(1);raceBackend.putRelease=new CountDownLatch(1);Future<Status> update=executor.submit(()->race.send(fix(raceTime)));check(raceBackend.putEntered.await(2,TimeUnit.SECONDS),"upload race barrier reached");race.markStoppedPending();raceBackend.putRelease.countDown();update.get(3,TimeUnit.SECONDS);check(!race.status().consentActive&&race.readLink()==null,"late upload ack never reactivates stopped sender");race.retryPendingRevocation();}finally{executor.shutdownNow();}
        System.out.println("PASS "+checks+" V3 coordinator consent/reconnect/admission/receipt/stop/race/expiry checks");
    }
    static boolean isTransient(IOException error){return LiveCoordinator.isTransientFailure(error);}
}
