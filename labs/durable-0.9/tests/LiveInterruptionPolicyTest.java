package com.stange.safebridge.live;
public final class LiveInterruptionPolicyTest {
 public static void main(String[] args){int checks=0;
  for(LiveInterruptionPolicy.Reason reason:LiveInterruptionPolicy.Reason.values()){
   LiveInterruptionPolicy.Action expected=reason==LiveInterruptionPolicy.Reason.USER_STOP || reason==LiveInterruptionPolicy.Reason.CHOSEN_END?LiveInterruptionPolicy.Action.REVOKE:LiveInterruptionPolicy.Action.PAUSE_FOR_RECOVERY;
   if(LiveInterruptionPolicy.action(reason,false)!=expected)throw new AssertionError(reason);checks++;
   if(LiveInterruptionPolicy.action(reason,true)!=LiveInterruptionPolicy.Action.REVOKE)throw new AssertionError("chosen end "+reason);checks++;
  }
  try{LiveInterruptionPolicy.action(null,false);throw new AssertionError("null reason");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("PASS "+checks+" interruption-policy cases: system/permission/protocol pause; explicit Stop/chosen end revoke");
 }
}
