package com.stange.safebridge.live;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import com.stange.safebridge.EndpointOriginRules;

/** Consent-bound protocol engine. Network calls never hold the state lock. */
public final class LiveCoordinator {
    public interface Transport {
        Map<String,Object> exchange(String method,String endpoint,String path,String token,
                String generation,String managementReceipt,Map<String,Object> body) throws IOException;
        default Map<String,Object> exchange(String method,String endpoint,String path,String token,
                String generation,Map<String,Object> body) throws IOException {
            return exchange(method,endpoint,path,token,generation,null,body);
        }
    }
    /** Implement with authenticated encryption and durable atomic replacement. Never log values. */
    public interface Store {
        Management load() throws IOException;
        void save(Management value) throws IOException;
        void clear() throws IOException;
    }
    public interface Clock { long nowMs(); long elapsedNowMs(); }
    public static final class HttpFailure extends IOException {
        public final int status; public final String code;
        public HttpFailure(int status,String code) {
            super("Live service rejected request (HTTP "+status+")");
            this.status=status; this.code=safeCode(code);
        }
    }
    /** Only transport adapters may classify an actual connection/timeout failure this way. */
    public static final class ConnectionFailure extends IOException {
        public ConnectionFailure(){super("Live service connection is unverified");}
    }
    public static final class StaleFixFailure extends IOException {
        public StaleFixFailure(){super("A new location sample is needed");}
    }
    public static final class ProtocolFailure extends IOException {
        public ProtocolFailure(){super("Invalid live-service data");}
    }
    public static boolean isTransientFailure(IOException error){
        if(error instanceof ConnectionFailure)return true;
        if(!(error instanceof HttpFailure))return false;
        int status=((HttpFailure)error).status;
        return status==408||status==425||status==429||(status>=500&&status<=599);
    }
    public enum State { IDLE, STARTING, ACTIVE, PAUSED, STOPPED_PENDING, STOPPED, EXPIRED }
    /** Runtime-only prepared capability; persist separately from session management. */
    public static final class PreparedAccess {
        public final String slotId,token;
        public PreparedAccess(String slotId,String token){this.slotId=slotId;this.token=token;}
    }
    public static final class RecoveryFailure extends IOException {
        public RecoveryFailure(){super("Saved session needs explicit recovery or prepared access");}
    }
    public static final class Fix {
        public final double latitude,longitude,accuracyM;
        public final long sampleTimeMs,sampleElapsedMs;
        public final boolean approximate,mock;
        public Fix(double latitude,double longitude,double accuracyM,long sampleTimeMs,
                long sampleElapsedMs,boolean approximate,boolean mock) {
            this.latitude=latitude;this.longitude=longitude;this.accuracyM=accuracyM;
            this.sampleTimeMs=sampleTimeMs;this.sampleElapsedMs=sampleElapsedMs;
            this.approximate=approximate;this.mock=mock;
        }
    }
    /** Only management capabilities. Coordinates, location history and recipient data are forbidden. */
    public static final class Management {
        public final String endpoint,generation,id,readToken,writeToken,revokeToken,creationKey,createPermit,admissionTicket,managementReceipt,preparedSlotId;
        public final int durationSeconds;
        public final long expiresAtMs,leaseUntilMs,lastSequence;
        public final Long admissionEpoch;
        public final boolean locallyStopped,creationUncertain;
        public Management(String endpoint,String generation,String id,String readToken,String writeToken,
                String revokeToken,String creationKey,String createPermit,String admissionTicket,String managementReceipt,String preparedSlotId,Long admissionEpoch,int durationSeconds,
                long expiresAtMs,long leaseUntilMs,long lastSequence,boolean locallyStopped,boolean creationUncertain) {
            this.endpoint=endpoint;this.generation=generation;this.id=id;this.readToken=readToken;
            this.writeToken=writeToken;this.revokeToken=revokeToken;this.creationKey=creationKey;
            this.createPermit=createPermit;this.admissionTicket=admissionTicket;this.managementReceipt=managementReceipt;this.preparedSlotId=preparedSlotId;this.admissionEpoch=admissionEpoch;this.durationSeconds=durationSeconds;
            this.expiresAtMs=expiresAtMs;this.leaseUntilMs=leaseUntilMs;this.lastSequence=lastSequence;
            this.locallyStopped=locallyStopped;this.creationUncertain=creationUncertain;
        }
        private Management stopped(){return new Management(endpoint,generation,id,readToken,writeToken,
            revokeToken,creationKey,createPermit,admissionTicket,managementReceipt,preparedSlotId,admissionEpoch,durationSeconds,expiresAtMs,leaseUntilMs,lastSequence,true,creationUncertain);}
        private Management response(String newId,long expiry,long lease,String receipt,long serverSequence,boolean uncertain){
            return new Management(endpoint,generation,newId,readToken,writeToken,revokeToken,creationKey,
                createPermit,uncertain?admissionTicket:null,receipt,preparedSlotId,admissionEpoch,durationSeconds,expiry,lease,serverSequence,locallyStopped,uncertain);
        }
        private Management withPermit(String permit){return new Management(endpoint,generation,id,readToken,writeToken,
            revokeToken,creationKey,permit,admissionTicket,managementReceipt,preparedSlotId,admissionEpoch,durationSeconds,expiresAtMs,leaseUntilMs,lastSequence,locallyStopped,creationUncertain);}
        private Management withTicket(String ticket,long epoch){return new Management(endpoint,generation,id,readToken,writeToken,
            revokeToken,creationKey,createPermit,ticket,managementReceipt,preparedSlotId,epoch,durationSeconds,expiresAtMs,leaseUntilMs,lastSequence,locallyStopped,creationUncertain);}
        public Map<String,Object> toMap(){
            Map<String,Object> result=new HashMap<>();result.put("schema",3);
            result.put("endpoint",endpoint);result.put("generation",generation);result.put("id",id);
            result.put("readToken",readToken);result.put("writeToken",writeToken);result.put("revokeToken",revokeToken);
            result.put("creationKey",creationKey);result.put("createPermit",createPermit);result.put("admissionTicket",admissionTicket);result.put("managementReceipt",managementReceipt);result.put("preparedSlotId",preparedSlotId);result.put("admissionEpoch",admissionEpoch);result.put("lastSequence",lastSequence);
            result.put("durationSeconds",durationSeconds);result.put("expiresAtMs",expiresAtMs);
            result.put("leaseUntilMs",leaseUntilMs);result.put("locallyStopped",locallyStopped);
            result.put("creationUncertain",creationUncertain);return result;
        }
        public static Management fromMap(Map<String,Object> map) throws IOException {
            if(number(map,"schema")!=3)throw invalid();
            Object identifier=map.get("id");String id=identifier==null?null:string(map,"id");
            long duration=number(map,"durationSeconds");if(duration<0||duration>Integer.MAX_VALUE)throw invalid();
            Management m=new Management(string(map,"endpoint"),string(map,"generation"),id,
                string(map,"readToken"),string(map,"writeToken"),string(map,"revokeToken"),
                string(map,"creationKey"),string(map,"createPermit"),nullableString(map,"admissionTicket"),nullableString(map,"managementReceipt"),nullableString(map,"preparedSlotId"),nullableNumber(map,"admissionEpoch"),(int)duration,
                number(map,"expiresAtMs"),number(map,"leaseUntilMs"),number(map,"lastSequence"),bool(map,"locallyStopped"),bool(map,"creationUncertain"));
            validateManagement(m);return m;
        }
    }
    public static final class Status {
        public final State state;
        /** active is the confirmed-session state; consentActive also includes unconfirmed creation. */
        public final boolean active,consentActive,locallyStopped,pendingRevocation;
        public final boolean deliveryUnconfirmed,latestAcknowledgementFresh,serverPaused,recoveryAvailable,canResume;
        public final String id,failureCode,serverState;
        public final long expiresAtMs,leaseUntilMs,lastSequence;
        private Status(State state,Management m,String failure,String serverState,long sequence,
                boolean consent,boolean acknowledgementFresh,boolean recovery){
            this.state=state;active=state==State.ACTIVE;consentActive=consent;
            locallyStopped=m==null?!consent:m.locallyStopped;pendingRevocation=m!=null&&m.locallyStopped;recoveryAvailable=recovery;canResume=recovery;
            id=m==null?null:m.id;expiresAtMs=m==null?0:m.expiresAtMs;
            leaseUntilMs=m==null?0:m.leaseUntilMs;failureCode=failure;
            this.serverState=serverState;lastSequence=sequence;
            latestAcknowledgementFresh=acknowledgementFresh;
            deliveryUnconfirmed=consent&&!acknowledgementFresh;serverPaused="paused".equals(serverState);
        }
    }
    private static final class PendingStart {
        final String endpoint,admissionTicket;final PreparedAccess preparedAccess;final int durationSeconds;final long requestedExpiresAtMs;final BooleanSupplier guard;
        PendingStart(String endpoint,int durationSeconds,long requestedEnd,String ticket,PreparedAccess access,BooleanSupplier guard){
            this.endpoint=endpoint;this.durationSeconds=durationSeconds;requestedExpiresAtMs=requestedEnd;admissionTicket=ticket;preparedAccess=access;this.guard=guard;
        }
    }
    private final Object lock=new Object();
    private final Transport transport;private final Store store;private final Clock clock;
    private final SecureRandom random=new SecureRandom();private final boolean allowLoopbackTests;
    private Management management;
    private State emptyState=State.IDLE;
    private boolean starting=false,connecting=false,revoking=false,expired=false,recoveryPaused=false,resuming=false,resetting=false;
    private PreparedAccess preparedAccess;
    private PendingStart pendingStart;
    private String failure="",serverState="";
    private long epoch=0,sequence=0,deadlineElapsedMs=0,leaseDeadlineElapsedMs=0,recentMs=30000,lastDataAckElapsedMs=-1,lastSampleElapsedMs=-1;

    public LiveCoordinator(Transport transport,Store store,Clock clock) throws IOException {this(transport,store,clock,false);}
    /** The test flag permits only explicit loopback HTTP, never arbitrary cleartext hosts. */
    public LiveCoordinator(Transport transport,Store store,Clock clock,boolean allowLoopbackTests) throws IOException {
        if(transport==null||store==null||clock==null)throw new IllegalArgumentException("Missing dependency");
        this.transport=transport;this.store=store;this.clock=clock;this.allowLoopbackTests=allowLoopbackTests;
        Management restored=store.load();
        if(restored!=null){validateManagement(restored);normalizeEndpoint(restored.endpoint,allowLoopbackTests);
            management=restored;sequence=restored.lastSequence;recoveryPaused=!restored.locallyStopped;failure=recoveryPaused?"paused_recovery":"restored_stopped";}
    }
    public Status startPreparedUntil(String endpoint,long originalEnd,PreparedAccess access,BooleanSupplier guard) throws IOException {
        validatePrepared(access);if(originalEnd<0)throw invalid();
        long remaining=originalEnd==0?0:originalEnd-clock.nowMs();
        if(originalEnd!=0&&(remaining<=0||remaining>Integer.MAX_VALUE*1000L))throw invalid();
        int duration=originalEnd==0?0:(int)((remaining+999)/1000);
        return beginStart(endpoint,duration,originalEnd,null,access,guard);
    }
    /** System interruption preserves recovery metadata but never starts collection or network. */
    public Status pauseForRecovery() throws IOException {synchronized(lock){pauseLocked();return snapshotLocked();}}
    private void pauseLocked() throws IOException {
        ++epoch;starting=false;pendingStart=null;preparedAccess=null;lastDataAckElapsedMs=-1;lastSampleElapsedMs=-1;
        if(management!=null&&!management.locallyStopped){recoveryPaused=true;failure="paused_recovery";store.save(management);}
        else if(management==null)emptyState=State.STOPPED;
    }
    public Status resumeExisting(BooleanSupplier guard) throws IOException {return resumeExisting(null,guard);}
    /** User-triggered reconciliation only. No location sample is retained or replayed here. */
    public Status resumeExisting(PreparedAccess access,BooleanSupplier guard) throws IOException {
        if(guard==null)throw new IllegalArgumentException("Missing recovery guard");
        final Management request;final long ticket;
        synchronized(lock){
            if(!guard.getAsBoolean()||management==null||management.locallyStopped||resuming||resetting)return snapshotLocked();
            if(!recoveryPaused)return snapshotLocked();
            if(access!=null){validatePrepared(access);if(management.preparedSlotId!=null&&!management.preparedSlotId.equals(access.slotId))throw invalid();}
            if(management.id==null&&management.preparedSlotId!=null&&access==null){failure="prepared_access_required";throw new RecoveryFailure();}
            request=management;ticket=++epoch;resuming=true;lastDataAckElapsedMs=-1;lastSampleElapsedMs=-1;
        }
        try{
            long configStarted=clock.elapsedNowMs();
            Map<String,Object> config=transport.exchange("GET",request.endpoint,"/v1/config",null,null,null);validateConfig(config);
            if(bool(config,"admissionRequired")&&request.preparedSlotId==null)throw invalid();
            synchronized(lock){if(sameCreation(request)&&epoch==ticket&&request.expiresAtMs>0){
                long cap=configStarted+Math.max(0,request.expiresAtMs-number(config,"serverTimeMs"));
                deadlineElapsedMs=deadlineElapsedMs>0?Math.min(deadlineElapsedMs,cap):cap;}}
            if(!string(config,"generation").equals(request.generation)){
                synchronized(lock){if(!sameCreation(request)||epoch!=ticket)return snapshotLocked();
                    management=management.stopped();recoveryPaused=false;serverState="unavailable";store.save(management);
                    try{store.clear();management=null;emptyState=State.STOPPED;failure="server_generation_changed";}
                    catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}return snapshotLocked();}
            }
            if(request.id==null){
                synchronized(lock){if(!sameCreation(request)||epoch!=ticket||!guard.getAsBoolean())return snapshotLocked();
                    preparedAccess=access;recoveryPaused=false;starting=true;recentMs=number(config,"recentMs");
                    if(request.expiresAtMs==0)deadlineElapsedMs=0;else{long candidate=clock.elapsedNowMs()+Math.max(0,request.expiresAtMs-number(config,"serverTimeMs"));deadlineElapsedMs=deadlineElapsedMs>0?Math.min(deadlineElapsedMs,candidate):candidate;}
                    pendingStart=new PendingStart(request.endpoint,request.durationSeconds,request.expiresAtMs,request.admissionTicket,access,guard);}
                return retryConnection();
            }
            long began=clock.elapsedNowMs();
            Map<String,Object> response=transport.exchange("GET",request.endpoint,"/v1/shares/"+request.id+"/status",request.writeToken,request.generation,request.managementReceipt,null);
            synchronized(lock){if(!sameCreation(request)||epoch!=ticket||!guard.getAsBoolean())return snapshotLocked();
                applyResponseLocked(request,response,false,began);store.save(management);
                if(management.locallyStopped){recoveryPaused=false;String remote=serverState;
                    try{store.clear();management=null;emptyState=remote.equals("expired")?State.EXPIRED:State.STOPPED;failure="";}
                    catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}return snapshotLocked();}
                recoveryPaused=false;starting=false;pendingStart=null;preparedAccess=null;failure="";return snapshotLocked();}
        }catch(IOException error){synchronized(lock){if(sameCreation(request)&&epoch==ticket){
            if(isTransientFailure(error)){recoveryPaused=true;starting=false;pendingStart=null;failure="connection_unverified";}
            else if(!failure.equals("local_cleanup_pending"))failClosedLocked("recovery_failed",error);
        }}throw error;}
        finally{synchronized(lock){resuming=false;}}
    }
    /** Pure presence check: no expiry evaluation, storage write or network request. */
    public boolean hasManagedSession(){synchronized(lock){return management!=null;}}
    /** Local scope check only; this neither verifies a prepared token nor contacts a server. */
    public boolean canReplacePrepared(String endpoint,String slotId) {
        if(slotId==null||!slotId.matches("[A-Za-z0-9_-]{22}"))return false;
        final String origin;try{origin=normalizeEndpoint(endpoint,allowLoopbackTests);}catch(IOException invalid){return false;}
        synchronized(lock){
            if(management!=null)return management.endpoint.equals(origin)&&slotId.equals(management.preparedSlotId);
            return !starting&&pendingStart==null&&!connecting&&!resuming&&!resetting;
        }
    }
    public boolean preparedResetMatches(String endpoint,PreparedAccess access) throws IOException {
        String origin=normalizeEndpoint(endpoint,allowLoopbackTests);validatePrepared(access);
        synchronized(lock){return resetMatchesLocked(origin,access);}
    }
    private boolean resetMatchesLocked(String origin,PreparedAccess access){
        if(management!=null)return management.endpoint.equals(origin)&&access.slotId.equals(management.preparedSlotId);
        return pendingStart==null||(pendingStart.endpoint.equals(origin)&&pendingStart.preparedAccess!=null&&access.slotId.equals(pendingStart.preparedAccess.slotId));
    }
    /** Explicit own-slot reset only; possession of a recipient link cannot authorize it. */
    public Status resetPreparedCurrent(String endpoint,PreparedAccess access,BooleanSupplier guard) throws IOException {
        if(guard==null)throw new IllegalArgumentException("Missing reset guard");
        String origin=normalizeEndpoint(endpoint,allowLoopbackTests);validatePrepared(access);
        synchronized(lock){if(!guard.getAsBoolean())return snapshotLocked();if(resetting||!resetMatchesLocked(origin,access))throw invalid();
            markStoppedLocked();resetting=true;}
        try{
            Map<String,Object> config=transport.exchange("GET",origin,"/v1/config",null,null,null);validateConfig(config);
            Map<String,Object> body=new HashMap<>();body.put("slotId",access.slotId);
            Map<String,Object> response=transport.exchange("DELETE",origin,"/v1/admission/current",access.token,string(config,"generation"),body);
            String state=string(response,"state");
            if(!string(response,"generation").equals(string(config,"generation"))||!string(response,"slotId").equals(access.slotId)||
                (!state.equals("stopped")&&!state.equals("none"))||number(response,"serverTimeMs")<=0)throw invalid();number(response,"slotEpoch");
            synchronized(lock){serverState="prepared_slot_reset";
                try{store.clear();management=null;emptyState=State.STOPPED;failure="";}
                catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}return snapshotLocked();}
        }finally{synchronized(lock){resetting=false;}}
    }
    public Status start(String endpoint,int durationSeconds) throws IOException {return start(endpoint,durationSeconds,null,()->true);}
    public Status start(String endpoint,int durationSeconds,BooleanSupplier guard) throws IOException {return start(endpoint,durationSeconds,null,guard);}
    public Status start(String endpoint,int durationSeconds,String admissionTicket) throws IOException {return start(endpoint,durationSeconds,admissionTicket,()->true);}
    /** Duration zero means until Stop. The guard must be a fast, nonblocking consent check. */
    public Status start(String endpoint,int durationSeconds,String admissionTicket,BooleanSupplier guard) throws IOException {
        return beginStart(endpoint,durationSeconds,durationSeconds==0?0:-1,admissionTicket,null,guard);
    }
    /** Sends the original chosen absolute end unchanged; clock skew is never silently corrected. */
    public Status startUntil(String endpoint,long requestedExpiresAtMs,String admissionTicket,BooleanSupplier guard) throws IOException {
        if(requestedExpiresAtMs<0)throw invalid();
        long remaining=requestedExpiresAtMs==0?0:requestedExpiresAtMs-clock.nowMs();
        if(requestedExpiresAtMs!=0&&(remaining<=0||remaining>Integer.MAX_VALUE*1000L))throw invalid();
        int duration=requestedExpiresAtMs==0?0:(int)((remaining+999)/1000);
        return beginStart(endpoint,duration,requestedExpiresAtMs,admissionTicket,null,guard);
    }
    private Status beginStart(String endpoint,int durationSeconds,long requestedEnd,String admissionTicket,PreparedAccess access,BooleanSupplier guard) throws IOException {
        if(guard==null)throw new IllegalArgumentException("Missing consent guard");
        String origin=normalizeEndpoint(endpoint,allowLoopbackTests);validateAdmission(admissionTicket);
        if(durationSeconds<0)throw invalid();
        synchronized(lock){
            if(!guard.getAsBoolean()){if(management==null&&!starting)emptyState=State.STOPPED;return snapshotLocked();}
            expireLocked();if(starting||management!=null||resetting)throw new IOException("End or revoke the previous session first");
            ++epoch;starting=true;expired=false;recoveryPaused=false;preparedAccess=access;failure="";serverState="";sequence=0;
            lastDataAckElapsedMs=-1;lastSampleElapsedMs=-1;leaseDeadlineElapsedMs=0;
            deadlineElapsedMs=durationSeconds==0?0:clock.elapsedNowMs()+(requestedEnd>0?Math.max(0,requestedEnd-clock.nowMs()):durationSeconds*1000L);
            pendingStart=new PendingStart(origin,durationSeconds,requestedEnd,admissionTicket,access,guard);
        }
        return retryConnection();
    }
    /** Continues only the existing in-memory consent; it never creates replacement credentials. */
    public Status retryConnection() throws IOException {
        final long ticket;final PendingStart plan;Management request;
        synchronized(lock){
            expireLocked();if(!consentLocked()||connecting)return snapshotLocked();
            if(management!=null&&management.id!=null)return snapshotLocked();
            plan=pendingStart;
            if(plan!=null&&!plan.guard.getAsBoolean()){pauseLocked();return snapshotLocked();}
            ticket=epoch;connecting=true;request=management;
        }
        try{
            if(request==null){
                if(plan==null)throw invalid();
                Map<String,Object> config=transport.exchange("GET",plan.endpoint,"/v1/config",null,null,null);
                validateConfig(config);
                boolean admissionRequired=bool(config,"admissionRequired");
                if((admissionRequired&&plan.preparedAccess==null)||(!allowLoopbackTests&&!admissionRequired))throw invalid();
                String generation=string(config,"generation"),permit=string(config,"createPermit");
                long serverTime=number(config,"serverTimeMs");
                String read=token(),write=token(),revoke=token(),key=token();
                if(read.equals(write)||read.equals(revoke)||write.equals(revoke)||key.equals(read)||key.equals(write)||key.equals(revoke))throw invalid();
                long chosenEnd=plan.requestedExpiresAtMs>=0?plan.requestedExpiresAtMs:
                    serverTime+Math.max(0,deadlineElapsedMs-clock.elapsedNowMs());
                request=new Management(plan.endpoint,generation,null,read,write,revoke,key,permit,plan.admissionTicket,null,plan.preparedAccess==null?null:plan.preparedAccess.slotId,null,
                    plan.durationSeconds,chosenEnd,0,0,false,true);
                synchronized(lock){
                    expireLocked();if(epoch!=ticket||!consentLocked())return snapshotLocked();
                    if(!plan.guard.getAsBoolean()){pauseLocked();return snapshotLocked();}
                    management=request;recentMs=number(config,"recentMs");store.save(request);
                }
            }
            if(request.preparedSlotId!=null&&request.admissionTicket==null){request=issuePrepared(request,ticket);if(request==null)return status();}
            synchronized(lock){expireLocked();if(epoch!=ticket||!consentLocked())return snapshotLocked();}
            long requestStartedElapsed=0;Map<String,Object> response=null;boolean permitRefreshed=false,admissionRefreshed=false;
            for(int attempt=0;attempt<3;attempt++){
                synchronized(lock){expireLocked();if(epoch!=ticket||!consentLocked())return snapshotLocked();
                    if(plan!=null&&!plan.guard.getAsBoolean()){pauseLocked();return snapshotLocked();}}
                requestStartedElapsed=clock.elapsedNowMs();
                try{response=transport.exchange("POST",request.endpoint,"/v1/shares",request.writeToken,request.generation,creationBody(request));break;}
                catch(HttpFailure rejected){
                    if(rejected.status==400&&rejected.code.equals("invalid_create_permit")&&!permitRefreshed){
                        Map<String,Object> config=transport.exchange("GET",request.endpoint,"/v1/config",null,null,null);validateConfig(config);
                        if(!bool(config,"admissionRequired")||!string(config,"generation").equals(request.generation))throw rejected;
                        synchronized(lock){expireLocked();if(epoch!=ticket||!consentLocked())return snapshotLocked();
                            if(plan!=null&&!plan.guard.getAsBoolean()){pauseLocked();return snapshotLocked();}
                            management=management.withPermit(string(config,"createPermit"));store.save(management);request=management;}
                        permitRefreshed=true;
                    }else if(rejected.status==403&&rejected.code.equals("invalid_admission_ticket")&&!admissionRefreshed&&
                            request.preparedSlotId!=null&&request.admissionEpoch!=null&&preparedAccess!=null){
                        request=issuePrepared(request,ticket);if(request==null)return status();admissionRefreshed=true;
                    }else throw rejected;
                }
            }
            if(response==null)throw new ConnectionFailure();
            synchronized(lock){
                if(!sameCreation(request))return snapshotLocked();
                applyResponseLocked(request,response,true,requestStartedElapsed);starting=false;pendingStart=null;
                if(epoch!=ticket&&!management.locallyStopped)recoveryPaused=true;
                store.save(management);if(!management.locallyStopped)failure="";return snapshotLocked();
            }
        }catch(IOException error){
            synchronized(lock){
                if(epoch==ticket&&consentLocked()){
                    if(error instanceof RecoveryFailure){recoveryPaused=true;starting=false;pendingStart=null;failure="prepared_access_required";}
                    else if(isTransientFailure(error)){failure="connection_unverified";starting=management==null||management.id==null;}
                    else if(request!=null&&request.id==null&&definiteCreationRejection(error)){
                        markStoppedLocked();serverState="not_created";
                        try{store.clear();management=null;expired=isChosenEndRejection(error);emptyState=expired?State.EXPIRED:State.STOPPED;failure=expired?"chosen_end_elapsed":"admission_rejected";}
                        catch(IOException cleanupError){failure="local_cleanup_pending";error.addSuppressed(cleanupError);}
                    }else failClosedLocked("protocol_or_store_failed",error);
                }
            }
            throw error;
        }finally{synchronized(lock){connecting=false;}}
    }
    private Management issuePrepared(Management request,long ticket) throws IOException {
        PreparedAccess access=preparedAccess;validatePrepared(access);if(!access.slotId.equals(request.preparedSlotId))throw invalid();
        Map<String,Object> body=new HashMap<>();body.put("slotId",access.slotId);body.put("creationKey",request.creationKey);body.put("expectedSlotEpoch",request.admissionEpoch);
        Map<String,Object> issued=transport.exchange("POST",request.endpoint,"/v1/admission",access.token,request.generation,body);validateIssued(request,issued);
        synchronized(lock){if(epoch!=ticket||!consentLocked())return null;if(pendingStart!=null&&!pendingStart.guard.getAsBoolean()){pauseLocked();return null;}management=management.withTicket(string(issued,"admissionTicket"),number(issued,"slotEpoch"));store.save(management);return management;}
    }
    /** Immediately suppresses writes, then durably journals revocation before any HTTP operation. */
    public Status markStoppedPending() throws IOException {
        synchronized(lock){markStoppedLocked();return snapshotLocked();}
    }
    public Status stop() throws IOException {markStoppedPending();return retryPendingRevocation();}
    /** One bounded visible/manual retry; never scheduled by this class or on boot. */
    public Status retryPendingRevocation() throws IOException {return retryPendingRevocation(null);}
    public Status retryPendingRevocation(PreparedAccess access) throws IOException {
        final Management request;
        synchronized(lock){expireLocked();if(management==null)return snapshotLocked();
            if(!management.locallyStopped)throw new IOException("Stop locally before revoking");
            if(revoking)return snapshotLocked();store.save(management);revoking=true;request=management;}
        try{
            Management known=request;
            if(known.id==null&&known.preparedSlotId!=null&&(known.admissionTicket==null||known.admissionEpoch==null)){
                synchronized(lock){if(!sameCreation(request))return snapshotLocked();serverState="not_created";
                    try{store.clear();management=null;emptyState=expired?State.EXPIRED:State.STOPPED;failure="";return snapshotLocked();}
                    catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}}
            }
            if(known.id==null&&known.preparedSlotId!=null){
                validatePrepared(access);if(!known.preparedSlotId.equals(access.slotId))throw new RecoveryFailure();
                return resetPreparedCurrent(known.endpoint,access,()->true);
            }
            if(known.id==null){
                long requestStartedElapsed=clock.elapsedNowMs();
                Map<String,Object> result=transport.exchange("POST",known.endpoint,"/v1/shares",known.writeToken,known.generation,creationBody(known));
                synchronized(lock){if(!sameCreation(request))return snapshotLocked();applyResponseLocked(request,result,true,requestStartedElapsed);management=management.stopped();store.save(management);known=management;}
            }
            Map<String,Object> result=transport.exchange("DELETE",known.endpoint,"/v1/shares/"+known.id,known.revokeToken,known.generation,known.managementReceipt,null);
            synchronized(lock){if(!sameCreation(request))return snapshotLocked();validateResponse(known,result,false);
                String remote=string(result,"state");if(!terminal(remote))throw invalid();
                serverState=remote;
                try{store.clear();}catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}
                management=null;emptyState=expired?State.EXPIRED:State.STOPPED;failure="";return snapshotLocked();}
        }catch(IOException error){
            if(request.id==null&&definiteCreationRejection(error)){
                synchronized(lock){if(sameCreation(request)){serverState="unavailable";
                    try{store.clear();management=null;emptyState=State.STOPPED;failure="creation_rejected";return snapshotLocked();}
                    catch(IOException cleanupError){failure="local_cleanup_pending";error.addSuppressed(cleanupError);}}}
            }
            if(error instanceof HttpFailure && (((HttpFailure)error).status==404 || ((HttpFailure)error).status==400)){
                try{Status recovered=resolveUnavailable(request);if(recovered!=null)return recovered;}
                catch(IOException recoveryError){error.addSuppressed(recoveryError);}
            }
            synchronized(lock){if(sameCreation(request)){if(!failure.equals("local_cleanup_pending"))failure=error instanceof RecoveryFailure?"prepared_access_required":"revocation_pending";management=management.stopped();store.save(management);}}throw error;
        }
        finally{synchronized(lock){revoking=false;}}
    }
    /** A fresh config can prove old links unavailable; this is not a successful revoke claim. */
    private Status resolveUnavailable(Management request) throws IOException {
        Map<String,Object> config=transport.exchange("GET",request.endpoint,"/v1/config",null,null,null);
        validateConfig(config);
        String generation=string(config,"generation");long now=number(config,"serverTimeMs");
        boolean generationChanged=!generation.equals(request.generation);
        boolean expiryConfirmed=!generationChanged && request.id!=null && !request.creationUncertain && request.expiresAtMs>0 && now>=request.expiresAtMs;
        if(!generationChanged&&!expiryConfirmed)return null;
        synchronized(lock){if(!sameCreation(request)||!management.locallyStopped)return null;
            serverState=expiryConfirmed?"expired":"unavailable";
            try{store.clear();}catch(IOException cleanupError){failure="local_cleanup_pending";throw cleanupError;}
            management=null;expired=expiryConfirmed;emptyState=expiryConfirmed?State.EXPIRED:State.STOPPED;
            failure=expiryConfirmed?"duration_ended":"server_generation_changed";return snapshotLocked();}
    }
    public Status send(Fix fix) throws IOException {
        synchronized(lock){expireLocked();if(consentLocked()&&(management==null||management.id==null)){}else if(!consentLocked())throw new IOException("Session is not active");}
        if(status().state==State.STARTING)retryConnection();
        final Management request;final long ticket,next,age;
        synchronized(lock){expireLocked();if(management==null||management.locallyStopped||management.id==null)throw new ConnectionFailure();
            validateFix(fix);age=clock.elapsedNowMs()-fix.sampleElapsedMs;
            if(age<0||age>recentMs)throw new StaleFixFailure();
            request=management;ticket=epoch;next=++sequence;}
        Map<String,Object> body=new HashMap<>();body.put("sequence",next);body.put("latitude",fix.latitude);
        body.put("longitude",fix.longitude);body.put("accuracyM",fix.accuracyM);body.put("sampleTimeMs",fix.sampleTimeMs);
        body.put("sampleAgeMs",age);body.put("approximate",fix.approximate);body.put("mock",fix.mock);
        synchronized(lock){expireLocked();if(epoch!=ticket||!sameCreation(request)||management.locallyStopped)throw new IOException("Session stopped before update");}
        Map<String,Object> result;long requestStartedElapsed=clock.elapsedNowMs();
        try{result=transport.exchange("PUT",request.endpoint,"/v1/shares/"+request.id+"/latest",request.writeToken,request.generation,body);}
        catch(IOException error){
            if(error instanceof HttpFailure&&(((HttpFailure)error).status==400||((HttpFailure)error).status==409)&&((HttpFailure)error).code.equals("stale_point"))throw new StaleFixFailure();
            synchronized(lock){if(epoch==ticket&&sameCreation(request)){
            if(isTransientFailure(error))failure="connection_unverified";
            else failClosedLocked("permanent_connection_failure",error);
        }}throw error;}
        synchronized(lock){if(epoch!=ticket||!sameCreation(request)||management.locallyStopped)return snapshotLocked();
            try{applyResponseLocked(request,result,false,requestStartedElapsed);store.save(management);if(!management.locallyStopped){failure="";lastDataAckElapsedMs=requestStartedElapsed;lastSampleElapsedMs=fix.sampleElapsedMs;}}
            catch(IOException error){failClosedLocked("protocol_or_store_failed",error);throw error;}return snapshotLocked();}
    }
    /** Refreshes management state only. The sender never fetches coordinates from the server. */
    public Status refreshStatus() throws IOException {
        final Management request;final long ticket;
        synchronized(lock){expireLocked();if(management==null||management.id==null)return snapshotLocked();request=management;ticket=epoch;}
        long requestStartedElapsed=clock.elapsedNowMs();
        Map<String,Object> result;
        try{result=transport.exchange("GET",request.endpoint,"/v1/shares/"+request.id+"/status",
                request.locallyStopped?request.revokeToken:request.writeToken,request.generation,request.managementReceipt,null);}
        catch(IOException error){synchronized(lock){if(sameCreation(request)&&epoch==ticket&&!management.locallyStopped){
            if(isTransientFailure(error))failure="connection_unverified";else failClosedLocked("permanent_connection_failure",error);}}throw error;}
        synchronized(lock){if(sameCreation(request)&&epoch==ticket){try{applyResponseLocked(request,result,false,requestStartedElapsed);store.save(management);}
            catch(IOException error){failClosedLocked("protocol_or_store_failed",error);throw error;}}return snapshotLocked();}
    }
    public Status status(){synchronized(lock){expireLocked();return snapshotLocked();}}
    public long endsAtElapsed(){synchronized(lock){expireLocked();return consentLocked()?deadlineElapsedMs:0;}}
    public long consentEndsAtElapsed(){return endsAtElapsed();}
    public String readLink(){synchronized(lock){expireLocked();if(management==null||management.locallyStopped||management.id==null)return null;
        return management.endpoint+"/view#s="+management.id+"&g="+management.generation+"&r="+management.readToken;}}
    private boolean consentLocked(){return !recoveryPaused&&((management!=null&&!management.locallyStopped)||(management==null&&starting&&pendingStart!=null));}
    private Status snapshotLocked(){
        boolean consent=consentLocked();
        State state=management==null?(starting?State.STARTING:emptyState):management.locallyStopped?
            (expired?State.EXPIRED:State.STOPPED_PENDING):recoveryPaused?State.PAUSED:(management.id==null||starting)?State.STARTING:State.ACTIVE;
        long now=clock.elapsedNowMs();boolean fresh=consent&&state==State.ACTIVE&&"active".equals(serverState)&&
            !"connection_unverified".equals(failure)&&lastDataAckElapsedMs>=0&&lastSampleElapsedMs>=0&&
            now>=lastDataAckElapsedMs&&now-lastDataAckElapsedMs<=recentMs&&now>=lastSampleElapsedMs&&
            now-lastSampleElapsedMs<=recentMs&&(leaseDeadlineElapsedMs==0||now<leaseDeadlineElapsedMs);
        return new Status(state,management,failure,serverState,sequence,consent,fresh,management!=null&&!management.locallyStopped&&recoveryPaused);
    }
    private void expireLocked(){
        if(!consentLocked())return;
        if(deadlineElapsedMs>0&&clock.elapsedNowMs()>=deadlineElapsedMs){
            ++epoch;starting=false;pendingStart=null;expired=true;failure="duration_ended";
            if(management!=null){management=management.stopped();try{store.save(management);}catch(IOException error){failure="stop_store_failed";}}
            else emptyState=State.EXPIRED;
        }else if(leaseDeadlineElapsedMs>0&&clock.elapsedNowMs()>=leaseDeadlineElapsedMs){
            // Point erasure/freshness does not withdraw the user's still-active consent.
            failure="connection_unverified";
        }
    }
    private void markStoppedLocked() throws IOException {
        ++epoch;starting=false;pendingStart=null;recoveryPaused=false;preparedAccess=null;
        if(management!=null){management=management.stopped();store.save(management);}else emptyState=State.STOPPED;
    }
    private void failClosedLocked(String code,IOException original){++epoch;starting=false;pendingStart=null;preparedAccess=null;failure=code;
        if(management!=null){recoveryPaused=!management.locallyStopped;try{store.save(management);}catch(IOException secondary){original.addSuppressed(secondary);}}
        else{recoveryPaused=false;emptyState=State.STOPPED;}
    }
    private boolean sameCreation(Management other){return management!=null&&management.creationKey.equals(other.creationKey)&&management.generation.equals(other.generation);}
    private void applyResponseLocked(Management request,Map<String,Object> response,boolean creation,long requestStartedElapsed) throws IOException {
        validateResponse(request,response,creation);String id=string(response,"id"),state=string(response,"state");
        long expiry=number(response,"expiresAtMs"),lease=number(response,"leaseUntilMs"),serverNow=number(response,"serverTimeMs");
        if(management.expiresAtMs!=expiry||(management.id!=null&&!management.id.equals(id)))throw invalid();
        boolean alreadyStopped=management.locallyStopped;
        long serverSequence=number(response,"lastSequence");sequence=Math.max(sequence,serverSequence);
        management=management.response(id,expiry,lease,string(response,"managementReceipt"),serverSequence,false);serverState=state;
        if(alreadyStopped||terminal(state)||(expiry>0&&expiry<=serverNow)){management=management.stopped();if(state.equals("expired")||(expiry>0&&expiry<=serverNow))expired=true;}
        else{
            // Anchor to request start, subtracting the full network round trip conservatively.
            // Server processing time is already reflected in serverNow. Do not subtract it again.
            // The original consent deadline remains the cap; data freshness keeps its stricter anchor.
            if(expiry>0){long candidate=clock.elapsedNowMs()+Math.max(0,expiry-serverNow);deadlineElapsedMs=deadlineElapsedMs>0?Math.min(deadlineElapsedMs,candidate):candidate;}
            leaseDeadlineElapsedMs=requestStartedElapsed+Math.max(0,(expiry>0?Math.min(lease,expiry):lease)-serverNow);
            if(deadlineElapsedMs>0&&clock.elapsedNowMs()>=deadlineElapsedMs){management=management.stopped();expired=true;failure="duration_ended";}
            else if(clock.elapsedNowMs()>=leaseDeadlineElapsedMs||state.equals("paused"))failure="connection_unverified";
        }
    }
    private static void validateResponse(Management request,Map<String,Object> response,boolean creation) throws IOException {
        String id=string(response,"id"),generation=string(response,"generation"),state=string(response,"state"),receipt=string(response,"managementReceipt");
        long expiry=number(response,"expiresAtMs"),lease=number(response,"leaseUntilMs"),now=number(response,"serverTimeMs"),serverSequence=number(response,"lastSequence");
        if(serverSequence<request.lastSequence||!validReceipt(receipt)||!id.matches("[A-Za-z0-9_-]{16,128}")||!generation.equals(request.generation)||
            (!creation&&request.id!=null&&(!id.equals(request.id)||expiry!=request.expiresAtMs))||(!state.equals("waiting_for_fix")&&!state.equals("active")&&!state.equals("paused")&&!terminal(state))||
            (request.durationSeconds==0?expiry!=0:expiry<=0)||lease<0||now<=0||
            (expiry>0&&(expiry!=request.expiresAtMs||expiry>now+Integer.MAX_VALUE*1000L||lease>expiry)))throw invalid();
    }
    private static boolean terminal(String state){return state.equals("stopped")||state.equals("expired")||state.equals("unavailable");}
    private static Map<String,Object> creationBody(Management m){Map<String,Object> body=new HashMap<>();
        body.put("generation",m.generation);body.put("createPermit",m.createPermit);body.put("creationKey",m.creationKey);
        body.put("readToken",m.readToken);body.put("revokeToken",m.revokeToken);body.put("durationSeconds",m.durationSeconds);body.put("admissionTicket",m.admissionTicket);body.put("requestedExpiresAtMs",m.expiresAtMs);return body;}
    private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static void validateFix(Fix fix) throws IOException {if(fix==null||!Double.isFinite(fix.latitude)||!Double.isFinite(fix.longitude)||
        !Double.isFinite(fix.accuracyM)||fix.latitude< -90||fix.latitude>90||fix.longitude< -180||fix.longitude>180||fix.accuracyM<0||fix.accuracyM>100000||fix.sampleTimeMs<=0||fix.sampleElapsedMs<0)throw new IOException("Invalid location sample");}
    private static void validateManagement(Management m) throws IOException {
        if(m==null||m.endpoint==null||m.generation==null||!m.generation.matches("[A-Za-z0-9_-]{22,64}")||
            !secret(m.readToken)||!secret(m.writeToken)||!secret(m.revokeToken)||!secret(m.creationKey)||
            m.readToken.equals(m.writeToken)||m.readToken.equals(m.revokeToken)||m.writeToken.equals(m.revokeToken)||
            m.createPermit==null||m.createPermit.length()<16||m.createPermit.length()>4096||m.lastSequence<0||m.lastSequence>9007199254740991L||(m.admissionEpoch!=null&&(m.admissionEpoch<0||m.admissionEpoch>9007199254740991L))||
            (m.preparedSlotId!=null&&(m.id!=null||m.admissionTicket!=null)&&m.admissionEpoch==null)||(m.preparedSlotId!=null&&!m.preparedSlotId.matches("[A-Za-z0-9_-]{22}"))||m.durationSeconds<0||!validAdmission(m.admissionTicket)||(m.id!=null&&!validReceipt(m.managementReceipt))||
            (m.durationSeconds==0?m.expiresAtMs!=0:m.expiresAtMs<=0)||m.leaseUntilMs<0||(m.id!=null&&!m.id.matches("[A-Za-z0-9_-]{16,128}")))throw invalid();
    }
    private static boolean secret(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{43}");}
    public static String normalizeEndpoint(String endpoint,boolean allowLoopbackTests) throws IOException {
        try{URI uri=new URI(endpoint);String host=uri.getHost(),scheme=uri.getScheme();boolean loopback="127.0.0.1".equals(host)||"localhost".equals(host)||"[::1]".equals(host)||"::1".equals(host);
            if(host==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||
                !(uri.getRawPath()==null||uri.getRawPath().isEmpty()||uri.getRawPath().equals("/"))||
                !("https".equals(scheme)||allowLoopbackTests&&loopback&&"http".equals(scheme))||(!allowLoopbackTests&&loopback))throw invalid();
            if(!allowLoopbackTests)EndpointOriginRules.requireRemoteHost(host);
            return new URI(uri.getScheme(),null,host,uri.getPort(),null,null,null).toASCIIString();
        }catch(URISyntaxException|NullPointerException error){throw invalid();}
    }
    private static void validateConfig(Map<String,Object> config) throws IOException {
        String generation=string(config,"generation"),permit=string(config,"createPermit");
        long now=number(config,"serverTimeMs"),min=number(config,"minDurationSeconds"),max=number(config,"maxDurationSeconds");
        long lease=number(config,"leaseMs"),recent=number(config,"recentMs");
        if(number(config,"protocolVersion")!=3||!bool(config,"durableSessionState")||!bool(config,"preparedAdmission")||!bool(config,"untilStopSupported")||
            !generation.matches("[A-Za-z0-9_-]{22,64}")||permit.length()<16||permit.length()>4096||now<=0||
            min!=1||max!=Integer.MAX_VALUE||lease<1000||lease>300000||recent<1000||recent>30000)throw invalid();
        bool(config,"admissionRequired");
    }
    private static boolean definiteCreationRejection(IOException error){
        if(!(error instanceof HttpFailure))return false;HttpFailure http=(HttpFailure)error;
        return (http.status==400&&(http.code.equals("invalid_create_permit")||http.code.equals("chosen_end_elapsed")))||
            (http.status==403&&(http.code.equals("invalid_admission_ticket")||http.code.equals("admission_ticket_spent")));
    }
    private static void validatePrepared(PreparedAccess access) throws IOException {if(access==null||access.slotId==null||!access.slotId.matches("[A-Za-z0-9_-]{22}")||!secret(access.token))throw new RecoveryFailure();}
    private static void validateIssued(Management request,Map<String,Object> issued) throws IOException {
        if(!string(issued,"generation").equals(request.generation)||!string(issued,"slotId").equals(request.preparedSlotId)||
            !string(issued,"creationKey").equals(request.creationKey)||!validAdmission(string(issued,"admissionTicket"))||number(issued,"expiresAtMs")<=0)throw invalid();
        long issuedEpoch=number(issued,"slotEpoch");if(request.admissionEpoch!=null&&issuedEpoch!=request.admissionEpoch)throw invalid();
    }
    private static boolean isChosenEndRejection(IOException error){return error instanceof HttpFailure&&((HttpFailure)error).status==400&&((HttpFailure)error).code.equals("chosen_end_elapsed");}
    private static boolean validReceipt(String receipt){return receipt!=null&&receipt.length()>=16&&receipt.length()<=1024&&receipt.matches("[A-Za-z0-9_.-]+");}
    private static boolean validAdmission(String ticket){return ticket==null||(ticket.length()>=16&&ticket.length()<=1024&&ticket.matches("[A-Za-z0-9_.-]+"));}
    private static void validateAdmission(String ticket) throws IOException {if(!validAdmission(ticket))throw invalid();}
    private static Long nullableNumber(Map<String,Object> map,String key) throws IOException {return map.get(key)==null?null:number(map,key);}
    private static String nullableString(Map<String,Object> map,String key) throws IOException {Object value=map.get(key);if(value==null)return null;if(!(value instanceof String))throw invalid();return (String)value;}
    private static String string(Map<String,Object> map,String key) throws IOException {Object value=map==null?null:map.get(key);if(!(value instanceof String))throw invalid();return (String)value;}
    private static boolean bool(Map<String,Object> map,String key) throws IOException {Object value=map==null?null:map.get(key);if(!(value instanceof Boolean))throw invalid();return (Boolean)value;}
    private static long number(Map<String,Object> map,String key) throws IOException {Object value=map==null?null:map.get(key);if(!(value instanceof Number))throw invalid();Number n=(Number)value;double d=n.doubleValue();long l=n.longValue();if(!Double.isFinite(d)||d!=l||l<0||l>9007199254740991L)throw invalid();return l;}
    private static IOException invalid(){return new ProtocolFailure();}
    private static String safeCode(String code){return code!=null&&code.matches("[a-z0-9_]{1,64}")?code:"request_failed";}
}
