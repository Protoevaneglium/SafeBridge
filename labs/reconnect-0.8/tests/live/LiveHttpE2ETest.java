package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.IOException;
import java.util.*;

/** Runs against the actual local Node server with synthetic points, never real movement. */
public final class LiveHttpE2ETest {
    static int checks;
    static void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;}
    static final class Time implements Clock {final long origin=System.nanoTime();public long nowMs(){return System.currentTimeMillis();}public long elapsedNowMs(){return 100000+(System.nanoTime()-origin)/1000000;}}
    static final class Faults implements Transport {
        final HostHttpTransport http=new HostHttpTransport();boolean loseCreateAck,offlineRevoke;
        public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String managementReceipt,Map<String,Object> body) throws IOException {
            if(method.equals("DELETE")&&offlineRevoke)throw new ConnectionFailure();
            Map<String,Object> result=http.exchange(method,endpoint,path,token,generation,managementReceipt,body);
            if(method.equals("POST")&&loseCreateAck){loseCreateAck=false;throw new ConnectionFailure();}return result;
        }
    }
    static void rejected(int code,LiveCoordinatorTest.Checked call,String label) throws Exception {try{call.run();throw new AssertionError(label);}catch(HttpFailure e){check(e.status==code,label);}}
    public static void main(String[] args) throws Exception {
        String endpoint=args.length==0?"http://127.0.0.1:18787":args[0];
        Time time=new Time();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();Faults transport=new Faults();
        LiveCoordinator c=new LiveCoordinator(transport,store,time,true);c.start(endpoint,0);Management m=store.value;
        check(c.status().active&&m.id!=null,"actual create response integrated");
        String base="/v1/shares/"+m.id;
        Map<String,Object> waiting=transport.http.exchange("GET",endpoint,base+"/latest",m.readToken,m.generation,null);
        check(waiting.get("point")==null&&waiting.get("state").equals("waiting_for_fix"),"viewer initially sees waiting, no invented coordinates");
        rejected(404,()->transport.http.exchange("GET",endpoint,base+"/latest",m.writeToken,m.generation,null),"writer cannot read recipient coordinates");
        rejected(404,()->transport.http.exchange("GET",endpoint,base+"/status",m.readToken,m.generation,null),"reader cannot use management status");
        rejected(404,()->transport.http.exchange("DELETE",endpoint,base,m.writeToken,m.generation,null),"writer cannot revoke");
        c.send(new Fix(0.125,-0.25,50,time.nowMs(),time.elapsedNowMs(),true,true));
        Map<String,Object> update=transport.http.exchange("GET",endpoint,base+"/latest",m.readToken,m.generation,null);
        Map<String,Object> point=(Map<String,Object>)update.get("point");
        check(update.get("state").equals("active")&&point!=null,"actual upload reaches read-only recipient path");
        check(((Number)point.get("latitude")).doubleValue()==0.125&&point.get("approximate").equals(true)&&point.get("mock").equals(true),"synthetic coordinates/precision/mock survive HTTP codec");
        Map<String,Object> status=transport.http.exchange("GET",endpoint,base+"/status",m.writeToken,m.generation,null);
        check(!status.containsKey("point")&&!status.containsKey("latitude"),"management response has no coordinates");
        rejected(404,()->transport.http.exchange("PUT",endpoint,base+"/latest",m.readToken,m.generation,LiveCoordinatorTest.Backend.map(
            "sequence",2,"latitude",0,"longitude",0,"accuracyM",10,"sampleTimeMs",time.nowMs(),"sampleAgeMs",0,"approximate",true,"mock",true)),"reader cannot write");
        transport.offlineRevoke=true;c.markStoppedPending();
        try{c.retryPendingRevocation();throw new AssertionError("Offline revoke should remain pending");}catch(IOException expected){checks++;}
        check(c.status().pendingRevocation&&store.value.locallyStopped,"offline local stop durable, remote not falsely acknowledged");
        transport.offlineRevoke=false;c.retryPendingRevocation();
        Map<String,Object> ended=transport.http.exchange("GET",endpoint,base+"/latest",m.readToken,m.generation,null);
        check(ended.get("state").equals("stopped")&&ended.get("point")==null,"actual revoke clears recipient coordinate");
        check(store.value==null&&!c.status().pendingRevocation,"acknowledged revoke erases encrypted-store capabilities");
        transport.loseCreateAck=true;
        try{c.start(endpoint,0);throw new AssertionError("Expected synthetic lost acknowledgement");}catch(IOException expected){checks++;}
        Management uncertain=store.value;check(uncertain!=null&&uncertain.id==null&&!uncertain.locallyStopped&&c.status().consentActive,"ambiguous actual creation retains current consent and same management");
        c.retryConnection();check(c.status().active&&store.value.id!=null,"actual idempotent replay resolves lost create ack under same consent");c.stop();
        c.start(endpoint,0);Management beforeRestore=store.value;
        LiveCoordinator restored=new LiveCoordinator(transport,store,time,true);
        check(restored.status().pendingRevocation&&!restored.status().active&&restored.readLink()==null,"management restoration cannot resume upload");
        restored.retryPendingRevocation();Map<String,Object> restoredEnd=transport.http.exchange("GET",endpoint,"/v1/shares/"+beforeRestore.id+"/latest",beforeRestore.readToken,beforeRestore.generation,null);
        check(restoredEnd.get("point")==null&&restoredEnd.get("state").equals("stopped"),"restored pending session revoked against actual backend");
        System.out.println("PASS "+checks+" actual Java HTTP-to-Node create/upload/read-scope/offline-stop/lost-ack/revocation checks (synthetic data only)");
    }
}
