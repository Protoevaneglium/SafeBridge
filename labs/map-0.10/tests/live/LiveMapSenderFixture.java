package com.stange.safebridge.live.test;
import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveCoordinator.*;
import java.io.*;import java.util.*;
/** Synthetic host sender. All capabilities stay in private test IPC, never console test output. */
public final class LiveMapSenderFixture {
 public static void main(String[] args)throws Exception {
  BufferedReader input=new BufferedReader(new InputStreamReader(System.in));Map<String,Object> setup=HostHttpTransport.Json.object(input.readLine());String endpoint=(String)setup.get("endpoint");
  LiveCoordinatorTest.Time time=new LiveCoordinatorTest.Time();LiveCoordinatorTest.MemoryStore store=new LiveCoordinatorTest.MemoryStore();LiveCoordinator c=new LiveCoordinator(new HostHttpTransport(),store,time,true);
  c.startPreparedUntil(endpoint,0,new PreparedAccess((String)setup.get("slotId"),(String)setup.get("preparedToken")),()->true);
  System.out.println(HostHttpTransport.Json.encode(Collections.singletonMap("link",c.readLink())));System.out.flush();
  String line;while((line=input.readLine())!=null){if(line.equals("first"))c.send(new Fix(0.125,-0.25,50,time.wall,time.elapsed,true,true));else if(line.equals("second")){time.advance(1);c.send(new Fix(0.135,-0.24,40,time.wall,time.elapsed,true,true));}else if(line.equals("pause"))c.pauseForRecovery();else if(line.equals("resume"))c.resumeExisting(()->true);else if(line.equals("stop"))c.stop();else if(line.equals("exit"))break;else throw new IOException("Unknown synthetic command");System.out.println("{\"ok\":true}");System.out.flush();}
 }
}
