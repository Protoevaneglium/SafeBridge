package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actual V2 Node HTTP with synthetic clock/points and a private operator test pipe. */
public final class LiveReconnectE2ETest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static final class Fixture implements AutoCloseable {
        final Process process;final BufferedReader input;final PrintWriter control;final String endpoint;
        Fixture(boolean ticketed,int port) throws IOException {
            process=new ProcessBuilder("node","tests/live/LoopbackV2Fixture.mjs",ticketed?"ticketed":"anonymous",Integer.toString(port)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            input=new BufferedReader(new InputStreamReader(process.getInputStream()));control=new PrintWriter(process.getOutputStream(),true);endpoint="http://127.0.0.1:"+port;
            if(!Boolean.TRUE.equals(read().get("ready")))throw new IOException("Synthetic fixture startup failed");
        }
        Map<String,Object> read() throws IOException {String line=input.readLine();if(line==null)throw new IOException("Synthetic fixture ended");return HostHttpTransport.Json.object(line);}
        void advance(long millis,LiveCoordinatorTest.Time time) throws IOException {control.println("advance "+millis);if(!Boolean.TRUE.equals(read().get("advanced")))throw new IOException("Synthetic fixture clock failed");time.advance(millis);}
        String ticket() throws IOException {return ticket(false);}
        String ticket(boolean longer) throws IOException {control.println(longer?"ticketlong":"ticket");Object ticket=read().get("ticket");if(!(ticket instanceof String))throw new IOException("Synthetic operator issuance failed");return (String)ticket;}
        void advanceServerOnly(long millis) throws IOException {control.println("advance "+millis);if(!Boolean.TRUE.equals(read().get("advanced")))throw new IOException("Synthetic server clock failed");}
        public void close() throws Exception {control.println("close");if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}input.close();control.close();}
    }
    static final class Faults implements Transport {
        final HostHttpTransport http=new HostHttpTransport();boolean offlinePut,offlineDelete,loseCreateAck,loseDeleteAck,offlineConfig;IOException postFailure;int puts;
        public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String receipt,Map<String,Object> body) throws IOException {
            if(path.equals("/v1/config")&&offlineConfig)throw new ConnectionFailure();if(method.equals("POST")&&postFailure!=null)throw postFailure;
            if(method.equals("PUT")){puts++;if(offlinePut)throw new ConnectionFailure();}if(method.equals("DELETE")&&offlineDelete)throw new ConnectionFailure();
            Map<String,Object> result=http.exchange(method,endpoint,path,token,generation,receipt,body);
            if(method.equals("POST")&&loseCreateAck){loseCreateAck=false;throw new ConnectionFailure();}if(method.equals("DELETE")&&loseDeleteAck){loseDeleteAck=false;throw new ConnectionFailure();}return result;
        }
    }
    static Map<String,Object> latest(Faults t,Fixture f,Management m) throws IOException {return t.http.exchange("GET",f.endpoint,"/v1/shares/"+m.id+"/latest",m.readToken,m.generation,null);}
    public static void main(String[] args) throws Exception {
        try(Fixture fixture=new Fixture(false,18790)){
            LiveCoordinatorTest.Time time=new LiveCoordinatorTest.Time();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();Faults transport=new Faults();
            LiveCoordinator c=new LiveCoordinator(transport,store,time,true);c.start(fixture.endpoint,0);Management first=store.value;String link=c.readLink();c.send(LiveCoordinatorTest.fix(time));
            check(c.status().latestAcknowledgementFresh&&latest(transport,fixture,first).get("state").equals("active"),"actual initial point acknowledged");
            fixture.advance(91001,time);transport.offlinePut=true;try{c.send(LiveCoordinatorTest.fix(time));throw new AssertionError("Expected simulated outage");}catch(ConnectionFailure expected){checks++;}
            Map<String,Object> paused=latest(transport,fixture,first);check(paused.get("state").equals("paused")&&paused.get("point")==null,"actual backend erases point after TTL but preserves paused session");
            check(c.status().consentActive&&c.status().deliveryUnconfirmed&&c.readLink().equals(link),"outage over90s retains consent and identical recipient link");
            transport.offlinePut=false;c.send(LiveCoordinatorTest.fix(time));check(c.status().id.equals(first.id)&&latest(transport,fixture,first).get("state").equals("active"),"fresh reconnect resumes actual same ID");
            fixture.advance(3700000,time);check(c.status().consentActive&&c.status().expiresAtMs==0,"until Stop remains consented beyond prior one-hour cap");c.send(LiveCoordinatorTest.fix(time));check(c.readLink().equals(link),"long-outage upload preserves link");
            c.markStoppedPending();transport.offlineDelete=true;try{c.retryPendingRevocation();throw new AssertionError("Expected offline revoke");}catch(ConnectionFailure expected){checks++;}
            int uploads=transport.puts;try{c.send(LiveCoordinatorTest.fix(time));throw new AssertionError("Stopped sender resumed");}catch(IOException expected){checks++;}check(uploads==transport.puts&&!c.status().consentActive,"offline Stop prevents all future uploads");transport.offlineDelete=false;c.retryPendingRevocation();
            long originalEnd=time.wall+120000;transport.offlineConfig=true;
            try{c.startUntil(fixture.endpoint,originalEnd,null,()->true);throw new AssertionError("Expected config outage");}catch(ConnectionFailure expected){checks++;}
            fixture.advance(61001,time);transport.offlineConfig=false;c.retryConnection();check(store.value.expiresAtMs==originalEnd,"actual delayed creation preserves original absolute chosen end");c.stop();
            long relativeEnd=time.wall+120000;transport.offlineConfig=true;
            try{c.start(fixture.endpoint,120);throw new AssertionError("Expected relative config outage");}catch(ConnectionFailure expected){checks++;}
            fixture.advance(61001,time);transport.offlineConfig=false;c.retryConnection();check(store.value.expiresAtMs==relativeEnd,"actual duration API derives remaining original budget after outage");c.stop();
            c.start(fixture.endpoint,2);Management chosen=store.value;fixture.advance(2001,time);check(c.status().state==State.EXPIRED&&!c.status().consentActive,"chosen positive end expires locally");check(latest(transport,fixture,chosen).get("state").equals("expired"),"actual backend independently enforces chosen expiry");c.retryPendingRevocation();
            c.start(fixture.endpoint,86400);check(c.status().expiresAtMs-time.wall==86400000,"actual chosen duration can exceed an hour");c.stop();
            c.start(fixture.endpoint,0);Management evicted=store.value;c.markStoppedPending();transport.loseDeleteAck=true;
            try{c.retryPendingRevocation();throw new AssertionError("Expected lost Stop acknowledgement");}catch(ConnectionFailure expected){checks++;}check(c.status().pendingRevocation,"lost Stop acknowledgement retained conservatively");
            for(int i=0;i<1001;i++){LiveCoordinatorTest.MemoryStore temporaryStore=new LiveCoordinatorTest.MemoryStore();LiveCoordinator temporary=new LiveCoordinator(transport,temporaryStore,time,true);temporary.start(fixture.endpoint,0);temporary.stop();}
            Map<String,Object> proof=transport.http.exchange("GET",fixture.endpoint,"/v1/shares/"+evicted.id+"/status",evicted.revokeToken,evicted.generation,evicted.managementReceipt,null);
            check(proof.get("state").equals("unavailable"),"signed management receipt proves unavailable after1000-terminal-cache eviction");
            c.retryPendingRevocation();check(store.value==null&&c.status().serverState.equals("unavailable")&&!c.status().pendingRevocation,"receipt resolves UntilStop lost-ACK journal without claiming revocation");
        }
        try(Fixture fixture=new Fixture(true,18791)){
            LiveCoordinatorTest.Time time=new LiveCoordinatorTest.Time();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();Faults transport=new Faults();LiveCoordinator c=new LiveCoordinator(transport,store,time,true);
            try{c.start(fixture.endpoint,0);throw new AssertionError("Ticketless admission unexpectedly accepted");}catch(ProtocolFailure expected){checks++;}check(store.value==null&&!c.status().consentActive,"required admission cannot become anonymous");
            try{c.start(fixture.endpoint,0,"invalid."+"T".repeat(43));throw new AssertionError("Invalid ticket unexpectedly accepted");}catch(HttpFailure expected){check(expected.status==403,"bad operator ticket definitively rejected");}
            check(store.value==null&&!c.status().consentActive,"bad admission does not strand UntilStop management");
            long capacityEnd=time.wall+120000;String capacityTicket=fixture.ticket(true);transport.postFailure=new HttpFailure(503,"capacity_reached");
            try{c.startUntil(fixture.endpoint,capacityEnd,capacityTicket,()->true);throw new AssertionError("Expected capacity failure");}catch(HttpFailure expected){check(expected.status==503,"capacity rejection is transient");}
            String pendingKey=store.value.creationKey;fixture.advance(61001,time);transport.postFailure=null;c.retryConnection();
            check(c.status().active&&store.value.creationKey.equals(pendingKey)&&store.value.expiresAtMs==capacityEnd,"actual expired-permit retry preserves identity and original absolute remote end");c.stop();
            String ticket=fixture.ticket();transport.loseCreateAck=true;try{c.start(fixture.endpoint,0,ticket);throw new AssertionError("Expected lost create acknowledgement");}catch(ConnectionFailure expected){checks++;}
            check(c.status().consentActive&&store.value.admissionTicket.equals(ticket),"operator ticket retained encrypted for ambiguous creation retry");fixture.advance(61001,time);c.retryConnection();
            check(c.status().active&&store.value.admissionTicket==null,"actual idempotent retry succeeds after ticket and permit expiry and scrubs ticket");c.stop();
            try{c.start(fixture.endpoint,0,ticket);throw new AssertionError("Expired/spent ticket unexpectedly reused");}catch(HttpFailure expected){check(expected.status==403,"actual HMAC admission ticket cannot create another session");}
            check(store.value==null&&!c.status().consentActive&&c.status().failureCode.equals("admission_rejected"),"definitively rejected ticket does not strand management or claim remote revocation");
            c.start(fixture.endpoint,0,fixture.ticket());check(c.status().active,"new valid operator ticket can start after definitive rejection");c.stop();
            fixture.advanceServerOnly(20000);String skewTicket=fixture.ticket();long alreadyPastOnServer=time.wall+10000;
            try{c.startUntil(fixture.endpoint,alreadyPastOnServer,skewTicket,()->true);throw new AssertionError("Past server end unexpectedly allocated");}catch(HttpFailure expected){check(expected.status==400&&expected.code.equals("chosen_end_elapsed"),"server rejects past original end without shifting it");}
            check(store.value==null&&!c.status().pendingRevocation&&c.status().state==State.EXPIRED,"definitive elapsed-end rejection clears unknown management");
            c.startUntil(fixture.endpoint,time.wall+120000,skewTicket,()->true);check(c.status().active,"past-end rejection did not consume the one-use operator ticket");c.stop();
        }
        System.out.println("PASS "+checks+" actual V2 Java-to-Node outage/reconnect/untilStop/admission/1000-terminal-eviction checks (synthetic data only)");
    }
}
