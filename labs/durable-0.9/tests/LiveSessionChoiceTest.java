package com.stange.safebridge;
public final class LiveSessionChoiceTest {
 static int checks;
 static void accepted(long end,long now,int expected){if(LiveSessionChoice.durationSeconds(end,now)!=expected)throw new AssertionError("end-choice conversion");checks++;}
 static void rejected(long end,long now){try{LiveSessionChoice.durationSeconds(end,now);throw new AssertionError("expected invalid end choice");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  long now=1700000000000L;
  accepted(0,now,0);accepted(0,0,0);accepted(now+1,now,1);accepted(now+999,now,1);accepted(now+1000,now,1);accepted(now+1001,now,2);
  accepted(now+7200000,now,7200);accepted(now+86400000,now,86400);accepted(now+Integer.MAX_VALUE*1000L,now,Integer.MAX_VALUE);accepted(Long.MAX_VALUE,Long.MAX_VALUE-1000,1);
  rejected(now,now);rejected(now-1,now);rejected(-1,now);rejected(0,-1);rejected(Long.MAX_VALUE,now);rejected(now+Integer.MAX_VALUE*1000L+1,now);
  System.out.println("PASS "+checks+" executed end-choice cases: Until Stop, rounding, selected ends beyond 60 minutes, past/negative clocks and INTMAX bounds");
 }
}
