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
import com.stange.safebridge.live.LiveCoordinator;
import com.stange.safebridge.live.LiveRetryPolicy;
import com.stange.safebridge.live.LiveInterruptionPolicy;
import com.stange.safebridge.live.LiveResetOnlyStore;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Separate, disabled-by-default live-sharing lab. Visible consent and foreground start
 * are the Activity's responsibility. No boot/sticky restart, recipients or messages.
 * A remote session is acknowledged before collecting fixes. Only recent samples are
 * sent. Stop invalidates queued work and clears updates/coordinates immediately,
 * journals explicit Stop, then attempts revocation. System interruption instead pauses
 * for explicit same-link recovery and never silently revokes. Pending revocation is never represented as server-stopped.
 * Transient delivery failures preserve consent and retry only new fresh fixes. A point's
 * 90-second freshness/erasure lease does not end consent. Duration zero is Until Stop;
 * Android can still kill/delay this non-sticky service and there is no forever guarantee.
 * The original device-chosen UTC end is sent unchanged; retries never shift it. Device
 * and server wall-clock disagreement can cause rejection or an unintended real-time end.
 */
public final class LiveLocationService extends Service implements LocationListener {
    public static final String ACTION_START="com.stange.safebridge.LIVE_START";
    public static final String ACTION_STOP="com.stange.safebridge.LIVE_STOP";
    public static final String ACTION_RESUME="com.stange.safebridge.LIVE_RESUME";
    public static final String ACTION_STATE="com.stange.safebridge.LIVE_STATE";
    /** Legacy name retained for compilation only; an explicit endAtMs extra is required. */
    @Deprecated public static final String EXTRA_DURATION_MINUTES="durationMinutes";
    public static final String EXTRA_END_AT_MS="endAtMs";
    @Deprecated public static final String EXTRA_ADMISSION_TICKET="admissionTicket"; // Not consumed: prepared access is separate encrypted setup.
    private static final String ACTION_EXPIRE="com.stange.safebridge.LIVE_EXPIRE";
    private static final String CHANNEL="live_location_sharing";
    private static final int NOTICE=74201;
    private static final long SEND_INTERVAL_MS=15000L,FRESH_MS=30000L;
    private static final Object INIT_LOCK=new Object();
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"safe-bridge-live");t.setDaemon(true);return t;});
    private static final ExecutorService REVOCATION=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"safe-bridge-revoke");t.setDaemon(true);return t;});
    private static final AtomicBoolean revocationQueued=new AtomicBoolean(),resetQueued=new AtomicBoolean();
    private static final ExecutorService RESET=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"safe-bridge-prepared-reset");t.setDaemon(true);return t;});
    private static volatile Context applicationContext;
    private static volatile LiveCoordinator coordinator;
    private static volatile LiveLocationService instance;
    private static volatile String problem="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopping=new AtomicBoolean(),sending=new AtomicBoolean();
    private final LiveRetryPolicy retryPolicy=new LiveRetryPolicy();
    private volatile boolean running,awaitingCreation,initialStartPending,connectionRetrying,fineAtStart,preciseFixHeld,noticeUnverified=true,resumeRequested,revokeOnEnd;
    private volatile long deadline,sessionGeneration;
    private volatile long requestedEndAtMs;
    private volatile Location pendingFix;
    private LocationManager locations;
    private NotificationManager notifications;
    private AlarmManager alarms;
    private PendingIntent expiryIntent;
    private boolean receiverRegistered,gpsSubscribed,networkSubscribed,registrationComplete;

    /** No HTTP or collection: an un-stopped saved session is restored as PAUSED for explicit Resume. */
    public static void initialize(Context context)throws IOException{
        applicationContext=context.getApplicationContext();
        synchronized(INIT_LOCK){
            if(coordinator!=null)return;
            try{coordinator=new LiveCoordinator(new HttpsLiveTransport(),new LiveSessionStore(context),new LiveCoordinator.Clock(){
                public long nowMs(){return System.currentTimeMillis();}
                public long elapsedNowMs(){return SystemClock.elapsedRealtime();}
            });problem="";}
            catch(IOException|RuntimeException unavailable){problem="Secure session management is unavailable. Live sharing is disabled; remote stop status may be unknown.";throw new IOException("Secure session management unavailable.");}
        }
        emit(context);
    }
    /** May journal expiry, but never performs HTTP or returns credentials. */
    public static LiveCoordinator.Status status(){LiveCoordinator c=coordinator;return c==null?null:c.status();}
    public static boolean isRunning(){LiveLocationService service=instance;if(service==null || !service.running)return false;String issue=service.invalidReason();if(issue!=null){service.interruptLocal(issue);return false;}return service.running;}
    public static boolean isActive(){if(!isRunning())return false;LiveCoordinator.Status state=status();return state!=null && state.consentActive && state.id!=null && state.latestAcknowledgementFresh && !state.deliveryUnconfirmed && !state.serverPaused;}
    public static boolean connectionUnverified(){LiveLocationService service=instance;LiveCoordinator.Status state=status();return service!=null && service.running && (service.connectionRetrying || state==null || state.deliveryUnconfirmed || state.serverPaused || !state.latestAcknowledgementFresh);}
    public static boolean resumeAvailable(){LiveCoordinator.Status state=status();return state!=null && state.recoveryAvailable && !isRunning();}
    public static boolean resetInProgress(){return resetQueued.get();}
    /** Read-only import guard. Never grants consent, stops a session or contacts the server. */
    public static boolean canReplacePrepared(String endpoint,String slotId){
        final String origin;
        try{origin=LiveConfig.checkedEndpoint(endpoint);if(!origin.equals(LiveConfig.endpoint()) || !PreparedAccessRules.validSlotId(slotId))return false;}
        catch(IOException|RuntimeException invalid){return false;}
        LiveCoordinator client=coordinator;
        if(client!=null){
            LiveLocationService service=instance;
            boolean localStarting=service!=null && service.running && service.awaitingCreation;
            return PreparedReplacementGate.coordinator(localStarting,client.hasManagedSession(),client.canReplacePrepared(origin,slotId));
        }
        // An unreadable main journal is not proof of an empty session. Comparing the
        // separate setup can still permit the same slot, or first setup if none exists.
        Context app=applicationContext;if(app==null)return false;
        try{PreparedAccessStore.Descriptor existing=new PreparedAccessStore(app).load();return PreparedReplacementGate.storedFallback(true,existing!=null,existing!=null && origin.equals(existing.endpoint) && slotId.equals(existing.slotId));}
        catch(IOException|RuntimeException unreadable){return PreparedReplacementGate.storedFallback(false,true,false);}
    }

    public static long endsAtElapsed(){LiveLocationService service=instance;return isRunning() && service!=null?service.deadline:0L;}
    /** A known read-only link remains available while PAUSED; this does not restart collection. */
    public static String readLink(){LiveCoordinator c=coordinator;if(c==null)return null;LiveCoordinator.Status state=c.status();return !state.locallyStopped && state.id!=null?c.readLink():null;}
    public static String lastProblem(){return problem;}
    public static String statusText(){
        LiveCoordinator.Status state=status();LiveLocationService service=instance;
        if(state==null)return problem.isEmpty()?"Live server not configured. Live sharing is unavailable.":problem;
        if(cleanupPending(state))return cleanupText(state);
        if(state.pendingRevocation)return "Sending stopped locally. Remote Stop is unconfirmed. An update already dispatched may still arrive; the recipient's last known location may be old. Until Stop sessions have no automatic session end. Previously copied locations cannot be recalled.";
        if(service!=null && service.running && service.connectionRetrying)return "Connection unverified / retrying. Your foreground consent is still on; only new fresh fixes are retried. The recipient's last known location may be old or unavailable. Android can interrupt this session.";
        if(service!=null && service.running && service.awaitingCreation)return service.resumeRequested?"Reconciling the earlier session. No new location is collected until the same link and sequence are verified.":"Connecting using saved prepared access. No recipient link is ready yet. Stop remains available.";
        if(state.recoveryAvailable)return "Paused on this phone. No new location is collected or sent. The earlier link is preserved and may show an old or unavailable point. Resume needs your confirmation; Android does not automatically resume after Active apps Stop.";
        if(state.consentActive && isRunning())return state.latestAcknowledgementFresh && !state.deliveryUnconfirmed && !state.serverPaused?
                "A recent location update was acknowledged by the server. Recipient delivery and your current position are not guaranteed.":
                "Connection or fresh delivery is unverified. Waiting for a new fresh fix; the same session and link remain in use. The recipient's last known location may be old or unavailable.";
        if(state.state==LiveCoordinator.State.STOPPED || state.state==LiveCoordinator.State.EXPIRED){String confirmed=confirmedEnd(state);return confirmed.isEmpty()?"Local sending stopped. Remote termination is not confirmed here. Previously copied locations cannot be recalled.":confirmed+" Local sending is off. Previously copied locations cannot be recalled.";}
        return LiveConfig.configured()?"Live sharing is off. Choose Until Stop or a future end time, then decide who should receive the link.":"Live server not configured. Live sharing is unavailable.";
    }
    private static boolean cleanupPending(LiveCoordinator.Status state){return state!=null && "local_cleanup_pending".equals(state.failureCode);}
    private static String confirmedEnd(LiveCoordinator.Status state){
        if(state==null)return "";
        if("stopped".equals(state.serverState))return "The server confirmed this session is stopped.";
        if("expired".equals(state.serverState))return "The server confirmed this session has expired.";
        if("interrupted".equals(state.serverState))return "The server confirmed this session was interrupted and ended.";
        if("unavailable".equals(state.serverState))return "The server confirmed the old session and link are unavailable. This is not a revocation acknowledgement.";
        if("prepared_slot_reset".equals(state.serverState))return "The server confirmed the prepared slot's current session is cleared. Saved prepared access remains available for a new session.";
        if("not_created".equals(state.serverState))return "The server rejected creation; no new session was created. This is not a revocation acknowledgement.";
        return "";
    }
    private static String cleanupText(LiveCoordinator.Status state){
        String confirmed=confirmedEnd(state);
        return (confirmed.isEmpty()?"The remote session has ended.":confirmed)+" Local sending is off. Encrypted local management cleanup is pending; retry cleanup. Previously copied locations cannot be recalled.";
    }
    public static void stopSession(Context context){
        LiveLocationService service=instance;
        if(service!=null){service.stopLocal("Local sending stopped. Remote stop is pending.");return;}
        try{initialize(context);coordinator.markStoppedPending();}
        catch(IOException|RuntimeException unavailable){problem="Sending is off; the secure stop record could not be confirmed. Remote stop may remain pending.";emit(context);return;}
        emit(context);retryRemote(context.getApplicationContext());
    }
    /** Explicit visible/manual management retry only; never resumes collection or sending. */
    public static void retryPending(Context context){
        if(instance!=null && instance.running){stopSession(context);return;}
        try{initialize(context);}
        catch(IOException unavailable){emit(context);return;}
        retryRemote(context.getApplicationContext());
    }
    /** Explicit own-slot reset only. Forgetting PreparedAccessStore never calls this method. */
    public static void resetPrepared(Context context){
        if(!resetQueued.compareAndSet(false,true)){emit(context);return;}
        Context app=context.getApplicationContext();
        RESET.execute(()->{
            boolean managementUnreadable=false;
            try{
                PreparedAccessStore.Descriptor setup=new PreparedAccessStore(app).load();
                if(setup==null)throw new IOException("Prepared access is not set up.");
                LiveCoordinator target;
                try{initialize(app);target=coordinator;}
                catch(IOException unavailable){
                    managementUnreadable=true;
                    // Explicit own-slot reset remains possible with the separate grant.
                    // This temporary coordinator never becomes the sender and cannot
                    // read, overwrite or discard the unreadable real journal.
                    target=new LiveCoordinator(new HttpsLiveTransport(),new LiveResetOnlyStore(),new LiveCoordinator.Clock(){public long nowMs(){return System.currentTimeMillis();}public long elapsedNowMs(){return SystemClock.elapsedRealtime();}});
                }
                LiveCoordinator.PreparedAccess access=new LiveCoordinator.PreparedAccess(setup.slotId,setup.preparedToken);
                if(!target.preparedResetMatches(LiveConfig.endpoint(),access))throw new IOException("A different local session is preserved.");
                LiveLocationService service=instance;if(service!=null && service.running)service.pauseLocal("Explicit prepared reset requested. Local collection paused; server confirmation is pending.");
                target.resetPreparedCurrent(LiveConfig.endpoint(),access,()->true);
                problem=managementUnreadable?"The server confirmed this prepared slot is clear. The local management journal is still unreadable and unchanged; other unknown links are not confirmed ended. Starting or resuming remains blocked.":"Prepared slot reset confirmed. Saved prepared access was not forgotten.";
            }catch(IOException|RuntimeException unconfirmed){LiveCoordinator.Status state=status();problem=cleanupPending(state)?cleanupText(state):managementUnreadable?"Prepared slot reset is unconfirmed. The unreadable local management journal was left unchanged; other unknown links are not confirmed ended.":"Prepared reset is not confirmed. Check the current sharing status before choosing to retry.";}
            finally{resetQueued.set(false);emit(app);}
        });
    }
    private static void retryRemote(Context context){
        if(!revocationQueued.compareAndSet(false,true)){emit(context);return;}
        REVOCATION.execute(()->{
            try{
                LiveCoordinator c=coordinator;if(c==null)return;
                LiveCoordinator.PreparedAccess access=null;
                LiveCoordinator.Status before=c.status();
                if(before.pendingRevocation && before.id==null){PreparedAccessStore.Descriptor setup=new PreparedAccessStore(context).load();if(setup!=null)access=new LiveCoordinator.PreparedAccess(setup.slotId,setup.preparedToken);}
                c.retryPendingRevocation(access);LiveCoordinator.Status state=c.status();problem=cleanupPending(state)?cleanupText(state):(state.pendingRevocation?"Remote stop is still pending.":"");
            }catch(IOException|RuntimeException unconfirmed){
                LiveCoordinator.Status state=status();
                problem=cleanupPending(state)?cleanupText(state):"Local sending is stopped. Remote revocation is not confirmed; retry when connected. An Until Stop session can remain open until remote Stop is confirmed.";
            }
            finally{revocationQueued.set(false);emit(context);}
        });
    }
    private static void emit(Context context){context.getApplicationContext().sendBroadcast(new Intent(ACTION_STATE).setPackage(context.getPackageName()));}

    @Override public void onCreate(){
        super.onCreate();instance=this;
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){pauseLocal("Sender remains paused. Reopen the app to explicitly Resume the earlier link.");return START_NOT_STICKY;}
        if(ACTION_STOP.equals(intent.getAction())){
            // An explicit notification Stop may recreate this process. Restore only
            // stopped management so its remote capability can be revoked, never sent.
            try{initialize(this);}catch(IOException unavailable){}
            stopLocal("Local sending stopped. Remote stop is pending.");return START_NOT_STICKY;
        }
        boolean resume=ACTION_RESUME.equals(intent.getAction());
        if(!ACTION_START.equals(intent.getAction()) && !resume){stopSelf();return START_NOT_STICKY;}
        if(running || stopping.get())return START_NOT_STICKY;
        if(!LiveConfig.configured()){problem="Live server not configured. No live sharing was started.";emit(this);stopSelf();return START_NOT_STICKY;}
        try{initialize(this);}catch(IOException unavailable){emit(this);stopSelf();return START_NOT_STICKY;}
        LiveCoordinator.Status existing=coordinator.status();
        if(existing.pendingRevocation || existing.active || existing.state==LiveCoordinator.State.STARTING || (!resume && existing.recoveryAvailable)){problem="Resume or explicitly Stop the earlier session before creating another.";emit(this);stopSelf();return START_NOT_STICKY;}
        if(resume && !existing.recoveryAvailable){problem="No paused session is available to Resume.";emit(this);stopSelf();return START_NOT_STICKY;}
        if(notifications==null || locations==null){problem="Location or notification service unavailable. Live sharing was not started.";emit(this);stopSelf();return START_NOT_STICKY;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Live location sharing",NotificationManager.IMPORTANCE_LOW);channel.setShowBadge(false);channel.setDescription("Visible Stop control for explicitly started live location sharing.");
        try{notifications.createNotificationChannel(channel);}catch(RuntimeException unavailable){problem="Visible live-sharing controls could not be created.";emit(this);stopSelf();return START_NOT_STICKY;}
        String issue=permissionProblem();if(issue!=null){problem=issue;emit(this);stopSelf();return START_NOT_STICKY;}
        long now=System.currentTimeMillis(),endAt;
        if(resume){endAt=existing.expiresAtMs;resumeRequested=true;}
        else{
            if(!intent.hasExtra(EXTRA_END_AT_MS)){problem="Choose Until Stop or a future end time explicitly. Nothing started.";emit(this);stopSelf();return START_NOT_STICKY;}
            endAt=intent.getLongExtra(EXTRA_END_AT_MS,-1L);
            try{LiveSessionChoice.durationSeconds(endAt,now);}catch(IllegalArgumentException invalid){problem="Choose a valid future end time or Until Stop. Nothing started.";emit(this);stopSelf();return START_NOT_STICKY;}
            resumeRequested=false;
        }
        requestedEndAtMs=endAt;fineAtStart=has(Manifest.permission.ACCESS_FINE_LOCATION);running=true;awaitingCreation=true;initialStartPending=!resume;connectionRetrying=false;revokeOnEnd=false;
        long elapsed=SystemClock.elapsedRealtime();deadline=resume?0:endAt==0?0:elapsed+(endAt-now);retryPolicy.begin(elapsed);sessionGeneration=GENERATION.incrementAndGet();
        try{Notification notice=notification("Connecting to server. No location sent yet. Stop anytime.");if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(NOTICE,notice);}
        catch(RuntimeException denied){interruptLocal("Android did not allow the foreground sender to start. The earlier session remains paused; review permissions before explicitly resuming.");return START_NOT_STICKY;}
        scheduleExpiry();handler.post(monitor);handler.post(sendTick);queueSend();emit(this);return START_NOT_STICKY;
    }
    private boolean validTicket(long ticket){return running && !stopping.get() && sessionGeneration==ticket && GENERATION.get()==ticket && (deadline==0 || SystemClock.elapsedRealtime()<deadline);}
    private boolean has(String permission){return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean eligibleEnabled(String provider){
        boolean fine=has(Manifest.permission.ACCESS_FINE_LOCATION),coarse=has(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(!fine && !coarse)return false;
        if(LocationManager.GPS_PROVIDER.equals(provider) && !fine && Build.VERSION.SDK_INT<31)return false;
        return locations!=null && locations.getAllProviders().contains(provider) && locations.isProviderEnabled(provider);
    }
    private String permissionProblem(){
        if(!has(Manifest.permission.ACCESS_COARSE_LOCATION) && !has(Manifest.permission.ACCESS_FINE_LOCATION))return "Location permission unavailable. Live sending stopped.";
        if(Build.VERSION.SDK_INT>=33 && !has(Manifest.permission.POST_NOTIFICATIONS))return "Notification permission is required for visible live-sharing controls.";
        if(notifications==null || !notifications.areNotificationsEnabled())return "Notifications are disabled. Live sharing cannot run without visible controls.";
        NotificationChannel channel=notifications.getNotificationChannel(CHANNEL);if(channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return "The live-sharing notification channel is disabled.";
        try{if(locations==null || (Build.VERSION.SDK_INT>=28 && !locations.isLocationEnabled()) || (!eligibleEnabled(LocationManager.GPS_PROVIDER) && !eligibleEnabled(LocationManager.NETWORK_PROVIDER)))return "No eligible enabled location provider. Live sending stopped.";}
        catch(RuntimeException unavailable){return "Location providers unavailable. Live sending stopped.";}
        return null;
    }
    private String invalidReason(){
        if(!running)return "Local live sending is off.";
        if(deadline>0 && SystemClock.elapsedRealtime()>=deadline)return "Chosen sharing duration ended. Local sending stopped; remote stop is pending.";
        if((fineAtStart || preciseFixHeld) && !has(Manifest.permission.ACCESS_FINE_LOCATION))return "Precise permission changed. Local sending stopped and previous fix cleared.";
        String issue=permissionProblem();if(issue!=null)return issue;
        try{if(registrationComplete && !(gpsSubscribed && eligibleEnabled(LocationManager.GPS_PROVIDER)) && !(networkSubscribed && eligibleEnabled(LocationManager.NETWORK_PROVIDER)))return "Subscribed location providers unavailable. Local live sending stopped.";}
        catch(RuntimeException unavailable){return "Location providers unavailable. Local sending stopped.";}
        LiveCoordinator.Status state=status();
        if(!awaitingCreation && state!=null && (state.locallyStopped || state.pendingRevocation) && state.state!=LiveCoordinator.State.STARTING && state.state!=LiveCoordinator.State.IDLE)return "Session is no longer active. Local sending stopped; remote status may be pending.";
        return null;
    }
    private void beginLocation(){
        if(!running || stopping.get() || registrationComplete)return;
        String issue=invalidReason();if(issue!=null){interruptLocal(issue);return;}
        int count=0;count+=requestProvider(LocationManager.GPS_PROVIDER);count+=requestProvider(LocationManager.NETWORK_PROVIDER);registrationComplete=true;
        if(count==0){interruptLocal("No usable location provider. Local collection paused; review location access before explicitly resuming.");return;}
        try{notifications.notify(NOTICE,notification("Server session acknowledged. Anyone with the link may view. Stop anytime."));}
        catch(RuntimeException unavailable){interruptLocal("Visible sharing controls unavailable. Local collection paused; restore notifications before explicitly resuming.");return;}
        emit(this);queueSend();
    }
    private int requestProvider(String provider){
        try{if(!eligibleEnabled(provider))return 0;locations.requestLocationUpdates(provider,SEND_INTERVAL_MS,0f,this,Looper.getMainLooper());if(LocationManager.GPS_PROVIDER.equals(provider))gpsSubscribed=true;else networkSubscribed=true;return 1;}
        catch(RuntimeException unavailable){return 0;}
    }
    @Override public void onLocationChanged(Location location){
        if(location==null || !validTicket(sessionGeneration))return;
        String issue=invalidReason();if(issue!=null){interruptLocal(issue);return;}
        long sampleMs=location.getElapsedRealtimeNanos()/1000000L,age=SystemClock.elapsedRealtime()-sampleMs;
        if(sampleMs<=0 || age<0 || age>FRESH_MS || !location.hasAccuracy() || !Float.isFinite(location.getAccuracy()))return;
        Location previous=pendingFix;if(previous==null || location.getElapsedRealtimeNanos()>=previous.getElapsedRealtimeNanos()){
            pendingFix=new Location(location);preciseFixHeld|=has(Manifest.permission.ACCESS_FINE_LOCATION);emit(this);queueSend();
        }
    }
    /** A coalesced worker selects the latest fix at dispatch; failed snapshots are never queued. */
    private void queueSend(){
        final long ticket=sessionGeneration;long now=SystemClock.elapsedRealtime();
        if(!validTicket(ticket) || !retryPolicy.due(now) || !sending.compareAndSet(false,true))return;
        NETWORK.execute(()->{
            try{
                if(!validTicket(ticket))return;
                String issue=invalidReason();if(issue!=null){interruptLocal(issue);return;}
                LiveCoordinator.Status state;
                if(initialStartPending){
                    initialStartPending=false;LiveCoordinator.PreparedAccess access=loadPrepared(true);
                    state=coordinator.startPreparedUntil(LiveConfig.endpoint(),requestedEndAtMs,access,()->validTicket(ticket));
                }else if(resumeRequested && awaitingCreation){
                    LiveCoordinator.PreparedAccess access=coordinator.status().id==null?loadPrepared(false):null;
                    state=coordinator.resumeExisting(access,()->validTicket(ticket));
                }else if(awaitingCreation || coordinator.status().id==null)state=coordinator.retryConnection();
                else state=coordinator.status();
                if(!validTicket(ticket)){
                    reconcileLateCompletion();return;
                }
                if(!state.consentActive){interruptLocal("Local collection is off. Check the server session status before resuming; an ended session cannot resume.");return;}
                if(state.id==null)throw new LiveCoordinator.ConnectionFailure();
                if(awaitingCreation){
                    if(resumeRequested){deadline=coordinator.endsAtElapsed();scheduleExpiry();}
                    awaitingCreation=false;retryPolicy.connectionRecovered(SystemClock.elapsedRealtime());
                }
                if(!registrationComplete){handler.post(()->{if(validTicket(ticket))beginLocation();});retryPolicy.waitForFreshSample(SystemClock.elapsedRealtime());return;}
                // Read after reconnection so an older captured fix cannot displace a newer one.
                Location latest=pendingFix;long dispatchAt=SystemClock.elapsedRealtime();
                if(latest==null || !retryPolicy.claimSample(latest.getElapsedRealtimeNanos()/1000000L,dispatchAt)){retryPolicy.waitForFreshSample(dispatchAt);return;}
                Location sample=new Location(latest);
                LiveCoordinator.Fix fix=new LiveCoordinator.Fix(sample.getLatitude(),sample.getLongitude(),sample.getAccuracy(),sample.getTime(),sample.getElapsedRealtimeNanos()/1000000L,!has(Manifest.permission.ACCESS_FINE_LOCATION),Build.VERSION.SDK_INT>=31?sample.isMock():sample.isFromMockProvider());
                state=coordinator.send(fix);
                if(!validTicket(ticket))return;
                if(!state.consentActive){interruptLocal("The server session ended. Local sending stopped.");return;}
                retryPolicy.delivered(SystemClock.elapsedRealtime());connectionRetrying=false;noticeUnverified=false;problem="";
                updateNotice(ticket,"Recent update acknowledged. Recipients may still see an older point. Stop anytime.");
            }catch(IOException failure){
                LiveRetryPolicy.Action action=retryPolicy.failed(failure,SystemClock.elapsedRealtime());
                if(action==LiveRetryPolicy.Action.STOP_LOCAL){interruptLocal("Local collection paused after an authorization, protocol, security or storage failure. Check the session status before resuming.");}
                else if(validTicket(ticket)){
                    connectionRetrying=true;noticeUnverified=true;
                    problem=action==LiveRetryPolicy.Action.DROP_SAMPLE?"That sample became old. Waiting for a new fresh fix; old samples are not replayed.":"Connection unverified / retrying. Foreground consent stays on; the recipient's last known location may be old or unavailable.";
                    updateNotice(ticket,"Connection unverified / retrying. Recipient's last known location may be old. Stop anytime.");
                    handler.post(()->{if(validTicket(ticket)){handler.removeCallbacks(sendTick);handler.postDelayed(sendTick,retryPolicy.nextDelayMillis(SystemClock.elapsedRealtime()));}});
                }
            }catch(RuntimeException permanent){interruptLocal("Local collection paused after an unexpected failure. Check the session status before resuming.");}
            finally{sending.set(false);emit(this);}
        });
    }
    private void updateNotice(long ticket,String message){handler.post(()->{if(validTicket(ticket))try{notifications.notify(NOTICE,notification(message));}catch(RuntimeException unavailable){interruptLocal("Visible sharing controls unavailable. Local collection paused; restore notifications before explicitly resuming.");}});}
    @Override public void onProviderEnabled(String provider){checkProviderChange();}
    @Override public void onProviderDisabled(String provider){checkProviderChange();}
    @Override public void onStatusChanged(String provider,int state,Bundle extras){checkProviderChange();}
    private void checkProviderChange(){if(running){String issue=invalidReason();if(issue!=null)interruptLocal(issue);}}
    private final Runnable sendTick=new Runnable(){public void run(){if(!running)return;queueSend();handler.postDelayed(this,retryPolicy.nextDelayMillis(SystemClock.elapsedRealtime()));}};
    private final Runnable monitor=new Runnable(){public void run(){
        if(!running)return;String issue=invalidReason();if(issue!=null){interruptLocal(issue);return;}
        if(!awaitingCreation){boolean unverified=connectionUnverified();if(unverified!=noticeUnverified){noticeUnverified=unverified;updateNotice(sessionGeneration,unverified?"Connection unverified / retrying. Recipient's last known location may be old. Stop anytime.":"Recent update acknowledged. Recipients may still see an older point. Stop anytime.");emit(LiveLocationService.this);}}
        handler.postDelayed(this,5000L);
    }};
    private Notification notification(String message){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stop=PendingIntent.getService(this,74202,new Intent(this,LiveLocationService.class).setAction(ACTION_STOP),flags);
        PendingIntent open=PendingIntent.getActivity(this,74203,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),flags);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Live location sharing").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setLocalOnly(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop).build());
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    private final BroadcastReceiver expiryReceiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(ACTION_EXPIRE.equals(intent.getAction()) && deadline>0 && SystemClock.elapsedRealtime()>=deadline)stopLocal("Chosen sharing duration ended. Local sending stopped; remote revocation is pending.");}};
    private void scheduleExpiry(){
        if(deadline==0)return; // Until Stop has no local duration alarm.
        try{IntentFilter filter=new IntentFilter(ACTION_EXPIRE);if(Build.VERSION.SDK_INT>=33)registerReceiver(expiryReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(expiryReceiver,filter);receiverRegistered=true;
            if(alarms!=null){expiryIntent=PendingIntent.getBroadcast(this,74204,new Intent(ACTION_EXPIRE).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,deadline,expiryIntent);}}
        catch(RuntimeException restricted){/* Handler/elapsed checks remain; no exact OS timer guarantee. */}
    }
    private LiveCoordinator.PreparedAccess loadPrepared(boolean required)throws IOException{
        PreparedAccessStore.Descriptor setup;
        try{setup=new PreparedAccessStore(this).load();}catch(IOException unavailable){if(required)throw unavailable;return null;}
        if(setup==null){if(required)throw new IOException("Prepared access is not set up.");return null;}
        return new LiveCoordinator.PreparedAccess(setup.slotId,setup.preparedToken);
    }
    private boolean chosenEndElapsed(){
        if(deadline>0 && SystemClock.elapsedRealtime()>=deadline)return true;
        LiveCoordinator.Status state=requestedEndAtMs>0?status():null;
        return state!=null && (state.state==LiveCoordinator.State.EXPIRED || "expired".equals(state.serverState));
    }
    private void interruptLocal(String reason){
        LiveInterruptionPolicy.Action action=LiveInterruptionPolicy.action(LiveInterruptionPolicy.Reason.SYSTEM_INTERRUPT,chosenEndElapsed());
        finishLocal(reason,action);
    }
    private void pauseLocal(String reason){finishLocal(reason,LiveInterruptionPolicy.Action.PAUSE_FOR_RECOVERY);}
    private void stopLocal(String reason){finishLocal(reason,LiveInterruptionPolicy.Action.REVOKE);}
    private void reconcileLateCompletion(){
        LiveCoordinator c=coordinator;if(c==null)return;
        boolean revoke=revokeOnEnd || chosenEndElapsed();
        try{if(revoke)c.markStoppedPending();else c.pauseForRecovery();}
        catch(IOException|RuntimeException unavailable){problem=revoke?"Late response; remote Stop and secure cleanup remain unconfirmed.":"Late response; local collection remains paused and recovery storage needs review.";}
        if(revoke)retryRemote(getApplicationContext());
    }
    private void finishLocal(String reason,LiveInterruptionPolicy.Action action){
        boolean revoke=action==LiveInterruptionPolicy.Action.REVOKE;
        if(revoke)revokeOnEnd=true;
        boolean first=stopping.compareAndSet(false,true);
        if(!first && !revoke)return;
        if(first){
            GENERATION.incrementAndGet();running=false;awaitingCreation=false;initialStartPending=false;connectionRetrying=false;retryPolicy.stop();pendingFix=null;handler.removeCallbacksAndMessages(null);
            if(locations!=null)try{locations.removeUpdates(this);}catch(RuntimeException ignored){}
            if(alarms!=null && expiryIntent!=null)try{alarms.cancel(expiryIntent);}catch(RuntimeException ignored){}
            if(expiryIntent!=null)expiryIntent.cancel();
            if(receiverRegistered){try{unregisterReceiver(expiryReceiver);}catch(IllegalArgumentException ignored){}receiverRegistered=false;}
        }
        LiveCoordinator c=coordinator;boolean durable=true;
        try{if(c!=null){if(revokeOnEnd)c.markStoppedPending();else c.pauseForRecovery();}}
        catch(IOException|RuntimeException unavailable){durable=false;}
        problem=durable?reason:revokeOnEnd?"Local collection stopped; secure Stop storage and remote revocation are unconfirmed.":"Local collection paused; recovery storage could not be confirmed. No automatic collection or revocation was requested.";
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();emit(this);
        if(c!=null && revokeOnEnd)retryRemote(getApplicationContext());
    }
    @Override public void onDestroy(){if(running && !stopping.get())interruptLocal("Android interrupted local collection. Earlier-link recovery is paused until you explicitly Resume.");if(instance==this)instance=null;super.onDestroy();}
}
