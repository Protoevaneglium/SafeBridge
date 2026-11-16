package com.stange.safebridge.live;
import java.io.IOException;
import java.util.*;
public final class LiveResetOnlyStoreTest {
 static int checks;
 static void check(boolean value){if(!value)throw new AssertionError("reset-only "+(checks+1));checks++;}
 static Map<String,Object> map(Object... pairs){Map<String,Object> result=new HashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
 public static void main(String[] args)throws Exception{
  LiveResetOnlyStore store=new LiveResetOnlyStore();check(store.load()==null);try{store.save(null);throw new AssertionError();}catch(IOException expected){checks++;}store.clear();checks++;
  String generation="G".repeat(22),slot="A".repeat(22),token="A".repeat(43);List<String> calls=new ArrayList<>();
  LiveCoordinator.Transport transport=(method,endpoint,path,secret,gen,receipt,body)->{
   calls.add(method+" "+path);
   if(path.equals("/v1/config")){check(method.equals("GET"));return map("protocolVersion",3,"durableSessionState",true,"preparedAdmission",true,"admissionRequired",true,"untilStopSupported",true,"generation",generation,"createPermit","P".repeat(32),"serverTimeMs",1800000000000L,"minDurationSeconds",1,"maxDurationSeconds",Integer.MAX_VALUE,"leaseMs",90000,"recentMs",30000);}
   check(method.equals("DELETE")&&path.equals("/v1/admission/current"));check(secret.equals(token));check(body.keySet().equals(Collections.singleton("slotId"))&&slot.equals(body.get("slotId")));
   return map("generation",generation,"slotId",slot,"state","none","serverTimeMs",1800000000000L,"slotEpoch",2);
  };
  LiveCoordinator client=new LiveCoordinator(transport,store,new LiveCoordinator.Clock(){public long nowMs(){return 1800000000000L;}public long elapsedNowMs(){return 100000;}});
  check(calls.isEmpty());LiveCoordinator.PreparedAccess access=new LiveCoordinator.PreparedAccess(slot,token);
  client.resetPreparedCurrent("https://example.org",access,()->false);check(calls.isEmpty());
  LiveCoordinator.Status state=client.resetPreparedCurrent("https://example.org",access,()->true);
  check(calls.equals(Arrays.asList("GET /v1/config","DELETE /v1/admission/current")));check(state.serverState.equals("prepared_slot_reset"));check(!state.consentActive&&state.id==null&&client.readLink()==null);
  System.out.println("PASS "+checks+" reset-only recovery cases: explicit own-slot scope, no session POST, no real-journal access or deletion");
 }
}
