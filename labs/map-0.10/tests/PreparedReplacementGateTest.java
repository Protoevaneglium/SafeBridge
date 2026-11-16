package com.stange.safebridge;
public final class PreparedReplacementGateTest {
 public static void main(String[] args){int checks=0;
  boolean[][] coordinator={{false,false,true,true},{false,true,true,true},{true,true,true,true},{true,false,true,false},{false,true,false,false},{true,true,false,false},{false,false,false,false},{true,false,false,false}};
  for(boolean[] c:coordinator){if(PreparedReplacementGate.coordinator(c[0],c[1],c[2])!=c[3])throw new AssertionError("coordinator scope gate");checks++;}
  boolean[][] fallback={{true,false,false,true},{true,true,true,true},{true,true,false,false},{false,false,false,false},{false,true,true,false},{false,true,false,false}};
  for(boolean[] c:fallback){if(PreparedReplacementGate.storedFallback(c[0],c[1],c[2])!=c[3])throw new AssertionError("stored fallback scope gate");checks++;}
  System.out.println("PASS "+checks+" prepared-replacement cases: matching live/paused scope, startup gap, different/unreadable scope and absent-setup fallback");
 }
}
