package com.stange.safebridge;
public final class NearbyMapConfig {
 public static final String PROVIDER_DISCLOSURE="";
 private NearbyMapConfig(){}
 public static String origin(){return LiveConfig.endpoint();}
 public static boolean configured(){return !origin().isEmpty()&&!PROVIDER_DISCLOSURE.trim().isEmpty();}
}
