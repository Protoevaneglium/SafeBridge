package com.stange.safebridge;

import java.util.Locale;

public final class LocationFixTest {
    private static int cases=0;
    private static void check(boolean condition,String name){cases++;if(!condition)throw new AssertionError(name);}
    public static void main(String[] args){
        LocationFix fix=new LocationFix(37.5,-122.25,15.5f,1_700_000_000_000L,1_000_000_000L,LocationFix.Source.REQUEST_RESULT,false,false);
        check(fix.ageMillis(1_000_000_000L)==0,"age zero");
        check(fix.ageMillis(3_000_000_000L)==2000,"monotonic age");
        check(fix.ageMillis(999_999_999L)==-1,"future monotonic time unknown");
        check(!fix.isStale(121_000_000_000L),"two minute inclusive threshold");
        check(fix.isStale(121_001_000_000L),"older than two minute threshold");
        check(fix.isStale(0),"unknown cannot be called fresh");
        String shared=fix.shareText(3_000_000_000L);
        check(shared.contains("not live tracking") && shared.contains("does not update or dispatch help"),"snapshot limits travel with coordinates");
        check(shared.contains("2023-11-14T22:13:20Z") && shared.contains("2 seconds"),"timestamp and fix age travel with coordinates");
        check(shared.contains("15.5 meters") && shared.contains("68% confidence"),"accuracy is estimate");
        Locale previous=Locale.getDefault();Locale.setDefault(Locale.GERMANY);try{check(fix.shareText(3_000_000_000L).contains("37.500000"),"coordinate decimal remains portable");}finally{Locale.setDefault(previous);}
        LocationFix cached=new LocationFix(-90,180,Float.NaN,0,0,LocationFix.Source.LAST_KNOWN,true,true);
        String text=cached.shareText(50_000_000_000L);
        check(text.contains("Last known cached fix") && text.contains("Freshness unknown"),"unknown cached age honest");
        check(text.contains("Accuracy: not available") && text.contains("Fix time (UTC, supplied by device/provider): unavailable"),"missing metadata explicit");
        check(text.contains("approximate location") && text.contains("mock/test location"),"permission and mock labels");
        check(!new LocationFix(0,0,0,1,1,LocationFix.Source.REQUEST_RESULT,false,false).isStale(2),"zero coordinates valid");
        for(double[] coords:new double[][]{{91,0},{0,181},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}}){boolean rejected=false;try{new LocationFix(coords[0],coords[1],1,1,1,LocationFix.Source.REQUEST_RESULT,false,false);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid coordinates rejected");}
        check(Float.isNaN(new LocationFix(0,0,-1,1,1,LocationFix.Source.REQUEST_RESULT,false,false).accuracyMeters),"negative accuracy unknown");
        LocationFix session=new LocationFix(0,0,5,1,1,LocationFix.Source.DEVICE_SESSION,true,false);
        check(session.shareText(2).contains("on-device session fix"),"session origin explicit");
        check(cached.summary(500).contains("stale or freshness unknown") && cached.summary(500).contains("Cached last-known"),"compact export disclosure preserves cache/freshness");
        System.out.println("PASS: "+cases+" location metadata, freshness, and share-text cases (host Java only)");
    }
}
