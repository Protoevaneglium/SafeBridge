package com.stange.safebridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** User-initiated, bounded location reads. No storage, geocoding, direct network or sending. Call on main thread. */
final class LocationSnapshot {
    interface Callback {void onSnapshot(LocationFix fix);void onFailure(String message);}
    private static final long TIMEOUT_MILLIS=20_000L;
    private final Context context;
    private final LocationManager manager;
    private final Callback callback;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<CancellationSignal> signals=new ArrayList<>();
    private LocationListener legacyListener;
    private boolean running=false,fineAtStart=false;
    private int generation=0,pending=0;
    private final Runnable timeout=()->fail("No location snapshot was obtained within 20 seconds. Nothing was sent. You can try again if safe.");
    private final Runnable healthCheck=new Runnable(){@Override public void run(){if(!running)return;if(checkActiveState())main.postDelayed(this,1000L);}};
    LocationSnapshot(Context context,Callback callback){this.context=context.getApplicationContext();this.manager=(LocationManager)this.context.getSystemService(Context.LOCATION_SERVICE);this.callback=callback;}
    static boolean hasPermission(Context context){return hasFinePermission(context) || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private static boolean hasFinePermission(Context context){return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static LocationFix fromLocation(Location location,boolean approximatePermission,LocationFix.Source source){
        if(location==null)return null;
        try{return new LocationFix(location.getLatitude(),location.getLongitude(),location.hasAccuracy()?location.getAccuracy():Float.NaN,location.getTime(),location.getElapsedRealtimeNanos(),source,approximatePermission,Build.VERSION.SDK_INT>=31?location.isMock():location.isFromMockProvider());}
        catch(IllegalArgumentException e){return null;}
    }
    boolean isRequesting(){return running;}
    private void requireMain(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Use location controls on the main thread");}
    void cancel(){requireMain();stop();}
    private void stop(){running=false;generation++;main.removeCallbacks(timeout);main.removeCallbacks(healthCheck);for(CancellationSignal signal:signals){try{signal.cancel();}catch(RuntimeException ignored){}}signals.clear();if(manager!=null && legacyListener!=null){try{manager.removeUpdates(legacyListener);}catch(RuntimeException ignored){}}legacyListener=null;pending=0;}
    private void fail(String message){if(!running)return;stop();callback.onFailure(message);}
    private List<String> enabledProviders(){ArrayList<String> providers=new ArrayList<>();if(manager==null)return providers;List<String> enabled=manager.getProviders(true);if(enabled.contains(LocationManager.GPS_PROVIDER) && (hasFinePermission(context) || Build.VERSION.SDK_INT>=31))providers.add(LocationManager.GPS_PROVIDER);if(enabled.contains(LocationManager.NETWORK_PROVIDER))providers.add(LocationManager.NETWORK_PROVIDER);return providers;}
    private boolean checkActiveState(){
        if(!running)return false;
        if(!hasPermission(context) || (fineAtStart && !hasFinePermission(context))){fail("Location permission was removed or changed. No snapshot was obtained. Nothing was sent.");return false;}
        try{if(enabledProviders().isEmpty()){fail("Location is off, or no enabled location provider is available for this permission. Nothing was sent.");return false;}}
        catch(SecurityException e){fail("Location permission is unavailable. Nothing was sent.");return false;}
        return true;
    }
    void request(){
        requireMain();stop();running=true;fineAtStart=hasFinePermission(context);
        if(!checkActiveState())return;
        int token=generation;List<String> providers;
        try{providers=enabledProviders();}catch(SecurityException e){fail("Location permission is unavailable. Nothing was sent.");return;}
        main.postDelayed(timeout,TIMEOUT_MILLIS);main.postDelayed(healthCheck,1000L);
        if(Build.VERSION.SDK_INT>=30){
            for(String provider:providers){if(!running)break;CancellationSignal signal=new CancellationSignal();signals.add(signal);pending++;
                try{manager.getCurrentLocation(provider,signal,command->main.post(command),location->{if(!running || token!=generation)return;pending--;receive(location,token);if(running && pending==0)fail("The device could not obtain a location snapshot. Nothing was sent. You can try again if safe.");});}
                catch(SecurityException e){pending--;if(!hasPermission(context)){fail("Location permission is unavailable. Nothing was sent.");return;}}
                catch(IllegalArgumentException e){pending--;}
            }
            if(running && pending==0)fail("No compatible location provider is available. Nothing was sent.");
        }else{
            legacyListener=new LocationListener(){
                @Override public void onLocationChanged(Location location){receive(location,token);}
                @Override public void onProviderDisabled(String provider){if(running && token==generation)checkActiveState();}
                @Override public void onProviderEnabled(String provider){}
                @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            };
            boolean started=false;for(String provider:providers){try{manager.requestLocationUpdates(provider,0L,0f,legacyListener,Looper.getMainLooper());started=true;}catch(SecurityException e){if(!hasPermission(context)){fail("Location permission is unavailable. Nothing was sent.");return;}}catch(IllegalArgumentException ignored){}}
            if(!started)fail("No compatible location provider is available. Nothing was sent.");
        }
    }
    private void receive(Location location,int token){
        if(!running || token!=generation || !checkActiveState())return;
        LocationFix fix=fromLocation(location,!hasFinePermission(context),LocationFix.Source.REQUEST_RESULT);
        if(fix==null)return;
        stop();callback.onSnapshot(fix);
    }
    /** Explicit cached lookup; never a silent substitute for a timed-out current request. */
    void lastKnown(){
        requireMain();stop();running=true;fineAtStart=hasFinePermission(context);if(!checkActiveState())return;
        LocationFix best=null;
        try{for(String provider:enabledProviders()){LocationFix candidate=fromLocation(manager.getLastKnownLocation(provider),!hasFinePermission(context),LocationFix.Source.LAST_KNOWN);if(candidate!=null && (best==null || candidate.fixElapsedRealtimeNanos>best.fixElapsedRealtimeNanos))best=candidate;}}
        catch(SecurityException e){fail("Location permission is unavailable. Nothing was sent.");return;}
        catch(IllegalArgumentException e){fail("A location provider is unavailable. Nothing was sent.");return;}
        if(!checkActiveState())return;
        if(best==null){fail("The device has no last known location available. Nothing was sent.");return;}
        stop();callback.onSnapshot(best);
    }
}
