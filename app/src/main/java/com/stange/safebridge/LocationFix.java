package com.stange.safebridge;

import java.time.Instant;
import java.util.Locale;

/** Immutable, memory-only coordinates. No Android objects or persistence. */
final class LocationFix {
    enum Source { REQUEST_RESULT, LAST_KNOWN, DEVICE_SESSION }
    static final long RECENT_WINDOW_MILLIS=120_000L;
    final double latitude,longitude;
    final float accuracyMeters;
    final long fixTimeMillis,fixElapsedRealtimeNanos;
    final Source source;
    final boolean approximatePermission,mock;
    LocationFix(double latitude,double longitude,float accuracyMeters,long fixTimeMillis,long fixElapsedRealtimeNanos,Source source,boolean approximatePermission,boolean mock){
        if(!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)throw new IllegalArgumentException("Invalid coordinates");
        if(source==null)throw new IllegalArgumentException("Snapshot source required");
        this.latitude=latitude;this.longitude=longitude;this.accuracyMeters=Float.isFinite(accuracyMeters) && accuracyMeters>=0?accuracyMeters:Float.NaN;
        this.fixTimeMillis=fixTimeMillis;this.fixElapsedRealtimeNanos=fixElapsedRealtimeNanos;this.source=source;this.approximatePermission=approximatePermission;this.mock=mock;
    }
    long ageMillis(long nowElapsedNanos){if(fixElapsedRealtimeNanos<=0 || nowElapsedNanos<fixElapsedRealtimeNanos)return -1;return (nowElapsedNanos-fixElapsedRealtimeNanos)/1_000_000L;}
    boolean isStale(long nowElapsedNanos){long age=ageMillis(nowElapsedNanos);return age<0 || age>RECENT_WINDOW_MILLIS;}
    String ageText(long nowElapsedNanos){long age=ageMillis(nowElapsedNanos);if(age<0)return "unknown (cannot verify freshness)";long seconds=age/1000;if(seconds<1)return "less than 1 second";if(seconds<60)return seconds+" seconds";if(seconds<3600)return seconds/60+" min "+seconds%60+" sec";if(seconds<86400)return seconds/3600+" hr "+(seconds%3600)/60+" min";return seconds/86400+" days "+(seconds%86400)/3600+" hr";}
    String description(long nowElapsedNanos){
        long age=ageMillis(nowElapsedNanos);
        String origin=source==Source.LAST_KNOWN?"Last known cached fix":source==Source.DEVICE_SESSION?"Latest on-device session fix (may be cached)":"Result of current device request (may be cached)";
        String freshness=age<0?"Freshness unknown":age>RECENT_WINDOW_MILLIS?"Older fix — over 2 minutes old":"Recent fix — at most 2 minutes old";
        String accuracy=Float.isNaN(accuracyMeters)?"not available":String.format(Locale.US,"%.1f meters estimated horizontal radius (about 68%% confidence; not a guaranteed boundary)",accuracyMeters);
        String timestamp=fixTimeMillis>0?Instant.ofEpochMilli(fixTimeMillis).toString():"unavailable";
        return origin+"\n"+freshness+"\nLatitude: "+String.format(Locale.US,"%.6f",latitude)+"\nLongitude: "+String.format(Locale.US,"%.6f",longitude)+"\nAccuracy: "+accuracy+"\nFix time (UTC, supplied by device/provider): "+timestamp+"\nFix age when this text was prepared: "+ageText(nowElapsedNanos)+"\nPermission: "+(approximatePermission?"approximate location":"precise location allowed; accuracy varies")+(mock?"\nWarning: Android marks this as a mock/test location.":"");
    }
    String summary(long nowElapsedNanos){
        String accuracy=Float.isNaN(accuracyMeters)?"unknown":String.format(Locale.US,"about %.1f m (estimate)",accuracyMeters);
        return String.format(Locale.US,"%.6f, %.6f",latitude,longitude)+"\nAccuracy: "+accuracy+"\nFix: "+(fixTimeMillis>0?Instant.ofEpochMilli(fixTimeMillis).toString():"time unavailable")+"\nAge: "+ageText(nowElapsedNanos)+(isStale(nowElapsedNanos)?" — stale or freshness unknown":"")+(approximatePermission?"\nApproximate permission":"")+(source==Source.LAST_KNOWN?"\nCached last-known fix":"")+(mock?"\nMock/test fix":"");
    }
    String shareText(long nowElapsedNanos){return "Location snapshot — not live tracking\n"+description(nowElapsedNanos)+"\nThe device may have moved since this fix. This text does not update or dispatch help.";}
}
