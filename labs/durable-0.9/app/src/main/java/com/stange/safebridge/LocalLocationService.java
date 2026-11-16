package com.stange.safebridge;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Explicit, temporary ON-DEVICE location updates, not a sharing or emergency service.
 * Keeps only one fix in process memory; no app network requests, files, account or history.
 * Android location providers may use their own network services and privacy practices.
 * Start with startForegroundService from a visible Activity after permissions/consent.
 * Requires manifest foregroundServiceType="location", stopWithTask="false", exported="false",
 * FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, coarse/fine location and POST_NOTIFICATIONS.
 * No ACCESS_BACKGROUND_LOCATION, wake lock, boot receiver or exact-alarm permission is needed.
 *
 * Android may delay timers/alarms or terminate this non-sticky service. The elapsed-time
 * deadline is checked before accepting or returning a fix. Cleanup at an exact time,
 * continuous fixes and operation through device sleep are NOT guaranteed.
 * See developer.android.com/develop/background-work/services/fgs/launch and
 * developer.android.com/about/versions/14/changes/fgs-types-required#location .
 */
public final class LocalLocationService extends Service implements LocationListener {
    public static final String ACTION_STATE="com.stange.safebridge.LOCAL_LOCATION_STATE";
    public static final String ACTION_STOP="com.stange.safebridge.LOCAL_LOCATION_STOP";
    public static final String ACTION_START="com.stange.safebridge.LOCAL_LOCATION_START";
    public static final String EXTRA_DURATION_MINUTES="durationMinutes";
    private static final String ACTION_EXPIRE="com.stange.safebridge.LOCAL_LOCATION_EXPIRE";
    private static final String CHANNEL="on_device_location_updates";
    private static final int NOTIFICATION_ID=74101;
    private static final long CHECK_MS=15000L, STALE_MS=120000L;
    private static final Object LOCK=new Object();
    private static LocalLocationService instance;
    private static boolean active, cached;
    private static long deadline;
    private static Location fix;
    private static String problem="";

    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private NotificationManager notifications;
    private AlarmManager alarms;
    private PendingIntent expiryIntent;
    private boolean receiverRegistered, cleanedUp, fineAtStart, preciseFixHeld;
    private boolean gpsSubscribed, networkSubscribed, registrationComplete;

    /** Logical activity: false after expiry/revocation even if OS cleanup is delayed. */
    public static boolean isActive(){
        LocalLocationService service;
        synchronized(LOCK){if(!active || instance==null)return false;service=instance;}
        String issue=service.invalidReason();
        if(issue!=null){service.stopFromAnyThread(issue);return false;}
        synchronized(LOCK){return active && instance==service;}
    }
    /** Elapsed-realtime deadline, or zero when no valid session is active. */
    public static long endsAtElapsed(){if(!isActive())return 0L;synchronized(LOCK){return deadline;}}
    /** Defensive clone; may be cached/old. Inspect timestamps/provider and fixDescription(). */
    public static Location latest(){if(!isActive())return null;synchronized(LOCK){return fix==null?null:new Location(fix);}}
    public static String lastProblem(){
        isActive();
        synchronized(LOCK){
            if(!problem.isEmpty())return problem;
            if(active && fix==null)return "Waiting for a location fix; no current position is available.";
            if(active && (cached || ageMillis(fix)>STALE_MS || ageMillis(fix)<0))return describeFix();
            return "";
        }
    }
    /** Provider, cached/update origin, elapsed age and accuracy; never a live-location claim. */
    public static String fixDescription(){if(!isActive())return "No active on-device location session.";synchronized(LOCK){return describeFix();}}
    private static long ageMillis(Location location){
        if(location==null || location.getElapsedRealtimeNanos()<=0)return -1L;
        long now=SystemClock.elapsedRealtimeNanos(),time=location.getElapsedRealtimeNanos();
        return time>now?-1L:(now-time)/1000000L;
    }
    private static String describeFix(){
        if(fix==null)return "No location fix yet.";
        long age=ageMillis(fix);
        String ageText=age<0?"age unknown":age<60000?((age/1000)+" seconds old"):((age/60000)+" minutes old");
        String provider=fix.getProvider()==null?"unknown provider":fix.getProvider();
        String accuracy=fix.hasAccuracy()?"; reported accuracy about "+Math.round(fix.getAccuracy())+" m":"; accuracy unavailable";
        return (cached?"Cached last-known fix":"Last received fix")+" from "+provider+"; "+ageText+accuracy+
                ((cached || age<0 || age>STALE_MS)?". Not a current-location guarantee.":". Location is an estimate.");
    }

    @Override public void onCreate(){
        super.onCreate();
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
        synchronized(LOCK){instance=this;active=false;deadline=0L;fix=null;cached=false;problem="";}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){endSession("Session was not restarted automatically.");return START_NOT_STICKY;}
        if(ACTION_STOP.equals(intent.getAction())){endSession("On-device location session stopped.");return START_NOT_STICKY;}
        if(intent.getAction()!=null && !ACTION_START.equals(intent.getAction())){
            if(!isActive())endSession("No on-device location session was started.");
            return START_NOT_STICKY;
        }
        // Repeated starts never silently extend an existing session. Finish an expired
        // instance instead of stacking receivers/listeners on a session awaiting cleanup.
        boolean alreadyRunning; synchronized(LOCK){alreadyRunning=active;}
        if(alreadyRunning){isActive();return START_NOT_STICKY;}
        if(cleanedUp){stopSelf();return START_NOT_STICKY;}
        if(notifications==null || locations==null){endSession("Location or notification service is unavailable.");return START_NOT_STICKY;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,"On-device location updates",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Visible controls for a temporary on-device location session. No automatic sharing.");
        channel.setShowBadge(false);
        try{notifications.createNotificationChannel(channel);}
        catch(RuntimeException unavailable){endSession("Visible session controls could not be created. Session not started.");return START_NOT_STICKY;}
        String issue=permissionOrSettingsProblem();
        if(issue!=null){endSession(issue);return START_NOT_STICKY;}
        int minutes=intent.getIntExtra(EXTRA_DURATION_MINUTES,15)==60?60:15;
        boolean startFine=has(Manifest.permission.ACCESS_FINE_LOCATION);
        synchronized(LOCK){fineAtStart=startFine;preciseFixHeld=false;gpsSubscribed=false;networkSubscribed=false;registrationComplete=false;active=true;deadline=SystemClock.elapsedRealtime()+minutes*60000L;fix=null;cached=false;problem="";}
        try{
            Notification notification=makeNotification();
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION_ID,notification);
        }catch(RuntimeException denied){
            endSession("Android did not allow the location session to start. Reopen Safe Bridge and review permissions.");
            return START_NOT_STICKY;
        }
        scheduleExpiry();
        int requested=0;
        if(has(Manifest.permission.ACCESS_FINE_LOCATION) || Build.VERSION.SDK_INT>=31)requested+=requestProvider(LocationManager.GPS_PROVIDER);
        requested+=requestProvider(LocationManager.NETWORK_PROVIDER);
        synchronized(LOCK){registrationComplete=true;}
        if(!isActive())return START_NOT_STICKY;
        if(requested==0){endSession("No location provider is available with the current permission. No position is being updated.");return START_NOT_STICKY;}
        handler.post(checkSession);notifyState();
        return START_NOT_STICKY;
    }
    private Notification makeNotification(){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stop=PendingIntent.getService(this,74102,new Intent(this,LocalLocationService.class).setAction(ACTION_STOP),flags);
        PendingIntent open=PendingIntent.getActivity(this,74103,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),flags);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("On-device location updates")
                .setContentText("No live link or automatic sharing. Stop anytime.")
                .setStyle(new Notification.BigTextStyle().bigText("No live link or automatic sharing. Stop anytime."))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setLocalOnly(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop).build());
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    private boolean has(String permission){return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private String permissionOrSettingsProblem(){
        if(!has(Manifest.permission.ACCESS_COARSE_LOCATION) && !has(Manifest.permission.ACCESS_FINE_LOCATION))return "Location permission is unavailable or was revoked. Session stopped.";
        if(Build.VERSION.SDK_INT>=33 && !has(Manifest.permission.POST_NOTIFICATIONS))return "Notification permission is required for the visible session controls. Session stopped.";
        if(notifications==null || !notifications.areNotificationsEnabled())return "App notifications are disabled. Enable them before starting an on-device session.";
        NotificationChannel channel=notifications.getNotificationChannel(CHANNEL);
        if(channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return "The on-device location notification channel is disabled. Session stopped.";
        try{
            if(locations==null || (Build.VERSION.SDK_INT>=28 && !locations.isLocationEnabled()))return "Device location services are off. Session stopped.";
            if(!eligibleEnabledProvider(LocationManager.GPS_PROVIDER) && !eligibleEnabledProvider(LocationManager.NETWORK_PROVIDER))
                return "No enabled location provider is available with the current permission. Session stopped.";
        }catch(RuntimeException unavailable){return "Device location settings are unavailable. Session stopped.";}
        return null;
    }
    private String invalidReason(){
        boolean heldPrecise;
        synchronized(LOCK){if(!active || instance!=this)return "On-device location session is inactive.";if(SystemClock.elapsedRealtime()>=deadline)return "On-device location session expired.";heldPrecise=fineAtStart || preciseFixHeld;}
        // Never return a previously precise fix under a newly approximate-only grant.
        if(heldPrecise && !has(Manifest.permission.ACCESS_FINE_LOCATION))
            return "Precise location access changed. Session stopped and previous fix cleared. Start a new approximate session if you choose.";
        String issue=permissionOrSettingsProblem();
        if(issue!=null)return issue;
        boolean complete,gps,network;
        synchronized(LOCK){complete=registrationComplete;gps=gpsSubscribed;network=networkSubscribed;}
        try{
            if(complete && !(gps && eligibleEnabledProvider(LocationManager.GPS_PROVIDER)) &&
                    !(network && eligibleEnabledProvider(LocationManager.NETWORK_PROVIDER)))
                return "Subscribed location providers are unavailable. Session stopped. Start a new session to use the currently enabled providers.";
        }catch(RuntimeException unavailable){return "Location providers are unavailable. Session stopped.";}
        return null;
    }
    private boolean eligibleEnabledProvider(String provider){
        boolean fine=has(Manifest.permission.ACCESS_FINE_LOCATION),coarse=has(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(!fine && !coarse)return false;
        if(LocationManager.GPS_PROVIDER.equals(provider) && !fine && Build.VERSION.SDK_INT<31)return false;
        if(!LocationManager.GPS_PROVIDER.equals(provider) && !LocationManager.NETWORK_PROVIDER.equals(provider))return false;
        return locations!=null && locations.getAllProviders().contains(provider) && locations.isProviderEnabled(provider);
    }
    private int requestProvider(String provider){
        try{
            if(!eligibleEnabledProvider(provider))return 0;
            Location last=locations.getLastKnownLocation(provider);
            if(last!=null)acceptFix(last,true);
            if(!isActive() || !eligibleEnabledProvider(provider))return 0;
            locations.requestLocationUpdates(provider,10000L,0f,this,Looper.getMainLooper());
            synchronized(LOCK){if(LocationManager.GPS_PROVIDER.equals(provider))gpsSubscribed=true;else networkSubscribed=true;}
            return 1;
        }catch(SecurityException revoked){
            if(!has(Manifest.permission.ACCESS_COARSE_LOCATION) && !has(Manifest.permission.ACCESS_FINE_LOCATION))endSession("Location permission was revoked. Session stopped.");
            return 0;
        }catch(IllegalArgumentException unavailable){return 0;}
    }
    @Override public void onLocationChanged(Location location){acceptFix(location,false);notifyState();}
    private void acceptFix(Location location,boolean fromCache){
        if(location==null || !isActive())return;
        boolean precise=has(Manifest.permission.ACCESS_FINE_LOCATION);
        synchronized(LOCK){
            if(!active || SystemClock.elapsedRealtime()>=deadline)return;
            if(fix==null || location.getElapsedRealtimeNanos()>=fix.getElapsedRealtimeNanos()){
                fix=new Location(location);cached=fromCache;preciseFixHeld|=precise;
            }
        }
    }
    private void notifyState(){sendBroadcast(new Intent(ACTION_STATE).setPackage(getPackageName()));}
    @Override public void onProviderEnabled(String provider){if(!isActive())return;}
    @Override public void onProviderDisabled(String provider){if(!isActive())return;}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){if(!isActive())return;}

    private final Runnable checkSession=new Runnable(){@Override public void run(){
        if(!isActive())return;
        long remaining; synchronized(LOCK){remaining=deadline-SystemClock.elapsedRealtime();}
        handler.postDelayed(this,Math.max(1L,Math.min(CHECK_MS,remaining)));
    }};
    private final BroadcastReceiver expiryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
        if(ACTION_EXPIRE.equals(intent.getAction())){
            long end; synchronized(LOCK){end=deadline;}
            if(end>0 && SystemClock.elapsedRealtime()>=end)endSession("On-device location session expired.");
        }
    }};
    private void scheduleExpiry(){
        // An inexact idle-capable alarm supplements Handler's uptime timer. There is no
        // exact deadline promise. This dynamically registered receiver cannot restart
        // a killed process/service and needs no additional manifest receiver.
        try{
            IntentFilter filter=new IntentFilter(ACTION_EXPIRE);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(expiryReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(expiryReceiver,filter);
            receiverRegistered=true;
            if(alarms!=null){
                expiryIntent=PendingIntent.getBroadcast(this,74104,new Intent(ACTION_EXPIRE).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                long end; synchronized(LOCK){end=deadline;}
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,end,expiryIntent);
            }
        }catch(RuntimeException restricted){
            synchronized(LOCK){problem="Device restrictions may delay session cleanup during sleep. No exact stop-time guarantee.";}
        }
    }
    private void stopFromAnyThread(String reason){
        synchronized(LOCK){if(instance!=this)return;active=false;deadline=0L;fix=null;cached=false;problem=reason;}
        if(Looper.myLooper()==Looper.getMainLooper())endSession(reason);else handler.post(()->endSession(reason));
    }
    private void endSession(String reason){
        synchronized(LOCK){if(instance==this){active=false;deadline=0L;fix=null;cached=false;problem=reason;instance=null;}}
        if(cleanedUp)return;
        cleanedUp=true;
        handler.removeCallbacksAndMessages(null);
        if(locations!=null){try{locations.removeUpdates(this);}catch(RuntimeException ignored){}}
        if(alarms!=null && expiryIntent!=null){try{alarms.cancel(expiryIntent);}catch(RuntimeException ignored){}}
        if(expiryIntent!=null)expiryIntent.cancel();
        if(receiverRegistered){try{unregisterReceiver(expiryReceiver);}catch(IllegalArgumentException ignored){}receiverRegistered=false;}
        stopForeground(STOP_FOREGROUND_REMOVE);notifyState();
        stopSelf();
    }
    @Override public void onDestroy(){endSession("On-device location session ended.");super.onDestroy();}
}
