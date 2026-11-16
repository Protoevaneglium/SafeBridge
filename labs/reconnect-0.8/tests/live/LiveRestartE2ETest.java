package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Actual Node restart recovery. Owns a loopback-only subprocess; uses synthetic data. */
public final class LiveRestartE2ETest {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static Process server() throws IOException {
        Process process=new ProcessBuilder("node","live/backend/server.mjs","--dev-loopback","--allow-anonymous-test-creation","--port","18789").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String line=new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
        if(line==null||!line.contains("listening")){process.destroy();throw new IOException("Synthetic backend startup failed");}return process;
    }
    private static void stop(Process process) throws Exception {if(process==null)return;process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}}
    public static void main(String[] args) throws Exception {
        Process process=null;
        try{
            process=server();String endpoint="http://127.0.0.1:18789";
            LiveHttpE2ETest.Time time=new LiveHttpE2ETest.Time();HostHttpTransport http=new HostHttpTransport();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();
            LiveCoordinator c=new LiveCoordinator(http,store,time,true);c.start(endpoint,0);Management old=store.value;
            c.send(new Fix(0.125,-0.25,50,time.nowMs(),time.elapsedNowMs(),true,true));
            c.markStoppedPending();stop(process);process=null;process=server();
            try{http.exchange("GET",endpoint,"/v1/shares/"+old.id+"/latest",old.readToken,old.generation,null);throw new AssertionError("Old generation link unexpectedly available");}
            catch(HttpFailure expected){check(expected.status==404,"old recipient link unavailable after actual server restart");}
            c.retryPendingRevocation();
            check(c.status().state==State.STOPPED&&!c.status().pendingRevocation&&store.value==null,"fresh new generation resolves old management");
            check(c.status().serverState.equals("unavailable")&&c.status().failureCode.equals("server_generation_changed"),"generation loss distinguished from explicit revocation");
            check(c.readLink()==null,"recovery cannot issue replacement viewer link");
            c.start(endpoint,0);check(c.status().active&&!store.value.generation.equals(old.generation)&&!store.value.id.equals(old.id),"only new explicit consent creates replacement session");
            c.stop();check(c.status().state==State.STOPPED&&store.value==null,"replacement synthetic session revoked and cleared");
            System.out.println("PASS "+checks+" actual Java-to-Node restart/generation-recovery checks (synthetic data only)");
        }finally{stop(process);}
    }
}
