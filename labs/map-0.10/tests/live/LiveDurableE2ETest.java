package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual Java->Node->encrypted SQLite integration. Synthetic data and private loopback only. */
public final class LiveDurableE2ETest {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static final class Fixture implements AutoCloseable {
        final Path directory;final int port;final String endpoint;PreparedAccess a,b;
        Process process;BufferedReader input;PrintWriter control;
        Fixture(int port)throws Exception {this.port=port;endpoint="http://127.0.0.1:"+port;directory=Files.createTempDirectory("safebridge09-synthetic-");Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));try{open();}catch(Exception error){try{close();}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}}
        void open()throws Exception {
            process=new ProcessBuilder("node","tests/live/LoopbackV3Fixture.mjs",directory.toString(),Integer.toString(port)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            input=new BufferedReader(new InputStreamReader(process.getInputStream()));control=new PrintWriter(process.getOutputStream(),true);Map<String,Object> ready=read();if(!Boolean.TRUE.equals(ready.get("ready")))throw new IOException("Synthetic durable fixture startup failed");
            if(ready.get("accesses") instanceof List){List<?> list=(List<?>)ready.get("accesses");a=access((Map<String,Object>)list.get(0));b=access((Map<String,Object>)list.get(1));}
        }
        static PreparedAccess access(Map<String,Object> value){return new PreparedAccess((String)value.get("slotId"),(String)value.get("preparedToken"));}
        Map<String,Object> read()throws IOException {String line=input.readLine();if(line==null)throw new IOException("Synthetic durable fixture ended");return HostHttpTransport.Json.object(line);}
        void advance(long millis,LiveCoordinatorTest.Time time)throws IOException {control.println("advance "+millis);if(!Boolean.TRUE.equals(read().get("advanced")))throw new IOException("Synthetic clock failed");time.advance(millis);}
        Map<String,Object> inspect()throws IOException {control.println("inspect");return read();}
        void restart(boolean abrupt)throws Exception {if(abrupt){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}else{control.println("close");if(!process.waitFor(5,TimeUnit.SECONDS))throw new IOException("Synthetic fixture did not close");}input.close();control.close();open();}
        public void close()throws Exception {if(process!=null&&process.isAlive()){control.println("close");if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}if(input!=null)input.close();if(control!=null)control.close();try(java.util.stream.Stream<Path> files=Files.walk(directory)){for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}}
    }
    static final class Faults implements Transport {
        final HostHttpTransport http=new HostHttpTransport();boolean offlinePut,offlineDelete,loseCreateAck,losePutAck,loseAdmissionAck,loseDeleteAck;IOException createFailure;
        int puts,creates,admissions,gets;Runnable afterAdmission,afterCreate;
        public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String receipt,Map<String,Object> body)throws IOException {
            if(method.equals("GET"))gets++;
            if(method.equals("PUT")){puts++;if(offlinePut)throw new ConnectionFailure();}
            if(method.equals("DELETE")&&offlineDelete)throw new ConnectionFailure();
            boolean create=path.equals("/v1/shares")&&method.equals("POST"),admission=path.equals("/v1/admission")&&method.equals("POST");
            if(create){creates++;if(createFailure!=null)throw createFailure;}
            if(admission)admissions++;
            Map<String,Object> result=http.exchange(method,endpoint,path,token,generation,receipt,body);
            if(admission&&afterAdmission!=null){Runnable once=afterAdmission;afterAdmission=null;once.run();}
            if(create&&afterCreate!=null){Runnable once=afterCreate;afterCreate=null;once.run();}
            if(create&&loseCreateAck){loseCreateAck=false;throw new ConnectionFailure();}
            if(admission&&loseAdmissionAck){loseAdmissionAck=false;throw new ConnectionFailure();}
            if(method.equals("PUT")&&losePutAck){losePutAck=false;throw new ConnectionFailure();}
            if(method.equals("DELETE")&&loseDeleteAck){loseDeleteAck=false;throw new ConnectionFailure();}
            return result;
        }
    }
    static LiveCoordinator coordinator(Faults transport,LiveCoordinatorTest.MemoryStore store,LiveCoordinatorTest.Time time)throws IOException {return new LiveCoordinator(transport,store,time,true);}
    static Map<String,Object> latest(Faults t,Fixture f,Management m)throws IOException {return t.http.exchange("GET",f.endpoint,"/v1/shares/"+m.id+"/latest",m.readToken,m.generation,null);}
    static void rejected(int status,LiveCoordinatorTest.Checked action,String label)throws Exception {try{action.run();throw new AssertionError(label);}catch(HttpFailure expected){check(expected.status==status,label);}}
    public static void main(String[] args)throws Exception {
        try(Fixture fixture=new Fixture(18796)){
            LiveCoordinatorTest.Time time=new LiveCoordinatorTest.Time();Faults transport=new Faults();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();LiveCoordinator c=coordinator(transport,store,time);
            c.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);Management first=store.value;String link=c.readLink();String generation=first.generation;
            check(c.status().active&&first.preparedSlotId.equals(fixture.a.slotId)&&first.admissionEpoch!=null,"prepared issuance and encrypted management admission complete without manual session code");
            check(!store.value.toMap().containsValue(fixture.a.token)&&!link.contains(fixture.a.token),"prepared secret excluded from session journal and recipient link");
            c.send(LiveCoordinatorTest.fix(time));transport.losePutAck=true;time.advance(1);try{c.send(LiveCoordinatorTest.fix(time));throw new AssertionError("Expected lost upload acknowledgement");}catch(ConnectionFailure expected){checks++;}
            check(store.value.lastSequence==1,"lost upload acknowledgement leaves only prior confirmed sequence locally");
            check(Boolean.TRUE.equals(fixture.inspect().get("coordinateFree")),"actual encrypted SQLite snapshot has coordinate-free schema");
            fixture.restart(true);Map<String,Object> afterCrash=latest(transport,fixture,first);
            check(afterCrash.get("generation").equals(generation)&&afterCrash.get("state").equals("paused")&&afterCrash.get("point")==null,"abrupt actual Node process restart preserves generation and same link but no coordinates");
            LiveCoordinator recovered=coordinator(transport,store,time);int gets=transport.gets,puts=transport.puts;
            check(recovered.status().state==State.PAUSED&&recovered.status().recoveryAvailable&&!recovered.status().consentActive&&!recovered.status().locallyStopped&&!recovered.status().pendingRevocation&&recovered.readLink().equals(link),"sender process restoration is paused with original link, not silently stopped");
            recovered.resumeExisting(()->false);check(transport.gets==gets&&transport.puts==puts,"false recovery guard performs no HTTP or collection");
            try{recovered.send(LiveCoordinatorTest.fix(time));throw new AssertionError("Paused sender uploaded");}catch(IOException expected){checks++;}
            recovered.resumeExisting(()->true);check(recovered.status().active&&recovered.status().lastSequence==2&&!recovered.status().latestAcknowledgementFresh&&transport.puts==puts,"explicit status-only recovery reconciles durable server sequence without replay");
            time.advance(1);recovered.send(LiveCoordinatorTest.fix(time));Map<String,Object> fresh=latest(transport,fixture,first);Map<String,Object> freshPoint=(Map<String,Object>)fresh.get("point");
            check(recovered.status().lastSequence==3&&((Number)freshPoint.get("sequence")).longValue()==3&&recovered.readLink().equals(link),"new fresh point uses higher sequence and exact original ID/link");
            fixture.advance(91001,time);transport.offlinePut=true;try{recovered.send(LiveCoordinatorTest.fix(time));throw new AssertionError("Expected outage");}catch(ConnectionFailure expected){checks++;}
            check(recovered.status().consentActive&&latest(transport,fixture,first).get("point")==null,"long connection outage pauses data but not current explicit consent");transport.offlinePut=false;recovered.send(LiveCoordinatorTest.fix(time));
            recovered.pauseForRecovery();check(recovered.status().recoveryAvailable&&!store.value.locallyStopped,"system interruption pauses without Stop or remote revoke");recovered.resumeExisting(()->true);
            LiveCoordinatorTest.MemoryStore secondStore=new LiveCoordinatorTest.MemoryStore();LiveCoordinator second=coordinator(transport,secondStore,time);second.startPreparedUntil(fixture.endpoint,0,fixture.b,()->true);Management secondMeta=secondStore.value;
            check(((Number)fixture.inspect().get("activeSessions")).intValue()==2,"independent prepared users retain separately reserved active slots");
            rejected(404,()->transport.http.exchange("DELETE",fixture.endpoint,"/v1/admission/current",fixture.a.token,generation,LiveCoordinatorTest.Backend.map("slotId",fixture.b.slotId)),"one prepared token cannot reset another installation slot");
            rejected(404,()->transport.http.exchange("GET",fixture.endpoint,"/v1/shares/"+first.id+"/latest",fixture.a.token,generation,null),"prepared token grants no recipient read access");
            check(!recovered.preparedResetMatches(fixture.endpoint,fixture.b),"client mismatch precheck prevents stopping unrelated local sharing");
            recovered.resetPreparedCurrent(fixture.endpoint,fixture.a,()->true);check(latest(transport,fixture,first).get("state").equals("stopped")&&latest(transport,fixture,secondMeta).get("state").equals("waiting_for_fix"),"explicit own-slot reset ends only the prior own link");second.stop();
            LiveCoordinator finite=coordinator(transport,store,time);long chosenEnd=time.wall+120000;finite.startPreparedUntil(fixture.endpoint,chosenEnd,fixture.a,()->true);Management finiteMeta=store.value;finite.send(LiveCoordinatorTest.fix(time));finite.pauseForRecovery();fixture.advance(30000,time);transport.http.exchange("GET",fixture.endpoint,"/v1/config",null,null,null);fixture.restart(true);
            LiveCoordinator finiteRecovered=coordinator(transport,store,time);Map<String,Object> serverTiming=transport.http.exchange("GET",fixture.endpoint,"/v1/config",null,null,null);long verifiedRemaining=chosenEnd-((Number)serverTiming.get("serverTimeMs")).longValue();time.wall+=3600000;finiteRecovered.resumeExisting(()->true);
            check(finiteRecovered.status().active&&finiteRecovered.status().expiresAtMs==chosenEnd&&finiteRecovered.endsAtElapsed()>time.elapsed&&finiteRecovered.endsAtElapsed()<=time.elapsed+verifiedRemaining,"chosen end remains immutable and local resume timer uses verified server time despite changed device clock");time.wall-=3600000;
            fixture.advance(verifiedRemaining+1,time);check(!finiteRecovered.status().consentActive&&finiteRecovered.status().state==State.EXPIRED,"original finite end remains terminal after recovery");finiteRecovered.retryPendingRevocation();fixture.restart(true);check(latest(transport,fixture,finiteMeta).get("state").equals("expired"),"selected expiry remains terminal across another real process restart");
            LiveCoordinator guardCase=coordinator(transport,store,time);AtomicBoolean consent=new AtomicBoolean(true);int beforeCreates=transport.creates;transport.afterAdmission=()->consent.set(false);guardCase.startPreparedUntil(fixture.endpoint,0,fixture.a,consent::get);
            check(guardCase.status().recoveryAvailable&&!guardCase.status().locallyStopped&&transport.creates==beforeCreates,"guard loss during admission pauses before any create POST");guardCase.resumeExisting(fixture.a,()->true);guardCase.stop();
            LiveCoordinator late=coordinator(transport,store,time);transport.afterCreate=()->{try{late.pauseForRecovery();}catch(IOException error){throw new RuntimeException(error);}};late.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);
            check(late.status().state==State.PAUSED&&late.status().recoveryAvailable&&!late.status().locallyStopped&&late.readLink()!=null,"late create acknowledgement after system pause preserves recovery, not Stop");late.resumeExisting(()->true);late.stop();
            LiveCoordinator admissionLost=coordinator(transport,store,time);transport.loseAdmissionAck=true;try{admissionLost.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);throw new AssertionError("Expected lost admission ack");}catch(ConnectionFailure expected){checks++;}
            Management beforeGrant=store.value;check(beforeGrant.id==null&&beforeGrant.admissionEpoch==null&&beforeGrant.admissionTicket==null,"lost initial admission acknowledgement cannot have escaped a create POST");admissionLost.pauseForRecovery();LiveCoordinator admissionRecovered=coordinator(transport,store,time);admissionRecovered.resumeExisting(fixture.a,()->true);check(admissionRecovered.status().active&&store.value.creationKey.equals(beforeGrant.creationKey),"explicit recovery of unallocated admission intent preserves creation identity");admissionRecovered.stop();
            LiveCoordinator ticketExpiry=coordinator(transport,store,time);transport.createFailure=new HttpFailure(503,"capacity_reached");try{ticketExpiry.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);throw new AssertionError("Expected precreate failure");}catch(HttpFailure expected){checks++;}String pinnedKey=store.value.creationKey;Long pinnedEpoch=store.value.admissionEpoch;
            fixture.advance(130001,time);transport.createFailure=null;ticketExpiry.retryConnection();check(ticketExpiry.status().active&&store.value.creationKey.equals(pinnedKey)&&store.value.admissionEpoch.equals(pinnedEpoch),"expired permit and prepared ticket renew once with same identity and pinned epoch");ticketExpiry.stop();
            LiveCoordinator ambiguous=coordinator(transport,store,time);transport.loseCreateAck=true;try{ambiguous.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);throw new AssertionError("Expected lost create ack");}catch(ConnectionFailure expected){checks++;}Management oldUnknown=store.value;ambiguous.pauseForRecovery();store.clear();
            LiveCoordinator noManagement=coordinator(transport,store,time);noManagement.resetPreparedCurrent(fixture.endpoint,fixture.a,()->true);fixture.advance(86400001,time);transport.http.exchange("GET",fixture.endpoint,"/v1/config",null,null,null);store.save(oldUnknown);LiveCoordinator oldIntent=coordinator(transport,store,time);int oldCreates=transport.creates;
            try{oldIntent.resumeExisting(fixture.a,()->true);throw new AssertionError("Old epoch revived after reset");}catch(HttpFailure expected){check(expected.status==409,"old pinned epoch is rejected after reset and terminal eviction");}
            check(!oldIntent.status().consentActive&&transport.creates<=oldCreates+2&&store.value.admissionEpoch.equals(oldUnknown.admissionEpoch),"old unknown creation never falls back to a new epoch or creates a replacement ID");oldIntent.markStoppedPending();oldIntent.retryPendingRevocation(fixture.a);
            LiveCoordinator neverAllocated=coordinator(transport,store,time);transport.createFailure=new HttpFailure(503,"capacity_reached");
            try{neverAllocated.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);throw new AssertionError("Expected no-allocation capacity response");}catch(HttpFailure expected){checks++;}
            neverAllocated.markStoppedPending();int createsBeforeStop=transport.creates;long actualAllocations=((Number)fixture.inspect().get("created")).longValue();
            try{neverAllocated.retryPendingRevocation();throw new AssertionError("Unknown Stop without private grant should remain pending");}catch(RecoveryFailure expected){checks++;}
            check(neverAllocated.status().pendingRevocation&&transport.creates==createsBeforeStop,"unknown Stop with missing grant cannot POST a new empty session");transport.createFailure=null;transport.offlineDelete=true;
            try{neverAllocated.retryPendingRevocation(fixture.a);throw new AssertionError("Expected offline own-slot reset");}catch(ConnectionFailure expected){checks++;}
            check(neverAllocated.status().pendingRevocation&&transport.creates==createsBeforeStop,"offline own-slot Stop remains pending without create-to-revoke");transport.offlineDelete=false;neverAllocated.retryPendingRevocation(fixture.a);
            check(store.value==null&&transport.creates==createsBeforeStop&&((Number)fixture.inspect().get("created")).longValue()==actualAllocations,"authenticated own-slot reset resolves unknown Stop with zero new server allocations");
            LiveCoordinator stopped=coordinator(transport,store,time);stopped.startPreparedUntil(fixture.endpoint,0,fixture.a,()->true);Management stoppedMeta=store.value;stopped.markStoppedPending();transport.loseDeleteAck=true;try{stopped.retryPendingRevocation();throw new AssertionError("Expected lost Stop ack");}catch(ConnectionFailure expected){checks++;}fixture.restart(true);LiveCoordinator stoppedRestored=coordinator(transport,store,time);int beforeResume=transport.gets;stoppedRestored.resumeExisting(fixture.a,()->true);
            check(stoppedRestored.status().pendingRevocation&&!stoppedRestored.status().recoveryAvailable&&!stoppedRestored.status().consentActive&&transport.gets==beforeResume,"explicitly stopped journal cannot resume after sender and server restart");stoppedRestored.retryPendingRevocation();check(latest(transport,fixture,stoppedMeta).get("state").equals("stopped"),"durable Stop remains terminal with no stored coordinate");
            check(Boolean.TRUE.equals(fixture.inspect().get("coordinateFree")),"final durable state schema remains coordinate-free after recovery/reset/expiry");
        }
        System.out.println("PASS "+checks+" actual V3 Java-to-Node encrypted-SQLite/prepared/recovery/sequence/reset checks (synthetic data only)");
    }
}
