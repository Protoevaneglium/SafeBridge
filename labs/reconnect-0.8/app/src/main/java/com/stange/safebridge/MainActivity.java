package com.stange.safebridge;

import android.app.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.Manifest;
import android.location.Location;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.*;
import android.widget.*;

/** Separate live-location laboratory. Guidance is offline; live sharing is disabled until an HTTPS service is approved. */
public class MainActivity extends Activity {
    private final int ink=Color.rgb(19,46,70), teal=Color.rgb(17,88,154);
    private static final String HOTLINE_POLICY="Your name and identifying details are optional. The hotline generally needs consent to share identifying information, with exceptions for child abuse, potential harm and legal reporting duties. Its policy applies.";
    private static final String CYBERTIP_POLICY="Opening this page does not submit a report. If you submit, NCMEC makes every report available to law enforcement. Submitted reports cannot be canceled. Your name and contact details are optional.";
    private static final String PEER_POLICY="Peer groups are not emergency response. Other participants may copy what you share. Browser, email and call activity may be monitored or leave traces. Review the service rules and privacy terms.";
    private LinearLayout page; private String section="home";
    private PageDeck deck;
    private LinearLayout sessionStrip;
    private String sessionSignature;
    private boolean stateReceiverRegistered;
    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){refreshLocation();}};
    private int[] restoredAnchor;
    private final NavigationState navigation=new NavigationState();
    private LocationSnapshot locationRequest;
    private LocationFix locationFix;
    private String locationMessage="No location requested. Nothing is being sent.";
    private String liveMessage="";
    private int permissionPurpose=0, sessionMinutes=15;
    private long liveEndAtMs=0;
    private String liveAdmissionTicket="";
    private Dialog setupDialog;
    private final Calculator calculator=new Calculator();
    private TextView calculation;
    private boolean modalShowing(){return PagedDialog.isShowing(this) || (setupDialog!=null && setupDialog.isShowing());}
    private void setup(Dialog dialog){if(setupDialog!=null && setupDialog.isShowing())setupDialog.dismiss();setupDialog=dialog;dialog.setOnDismissListener(d->{if(setupDialog==d)setupDialog=null;});dialog.show();}
    private boolean isCloaked(){return getPackageManager().getComponentEnabledSetting(new ComponentName(this,"com.stange.safebridge.CalculatorLauncher"))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    private void cloak(boolean enabled){if(enabled){liveAdmissionTicket="";if(setupDialog!=null)setupDialog.dismiss();}
        PackageManager pm=getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this,enabled?"com.stange.safebridge.CalculatorLauncher":"com.stange.safebridge.SafeLauncher"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this,enabled?"com.stange.safebridge.SafeLauncher":"com.stange.safebridge.CalculatorLauncher"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        calculator.reset();navigation.reset();render(enabled?"calculator":"home",0);
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(230,245,246));getWindow().setNavigationBarColor(Color.rgb(233,239,250));
        if(state!=null){navigation.restore(state.getStringArray("routes"),state.getIntArray("pages"));restoredAnchor=state.getIntArray("anchor");}
        render(isCloaked()?"calculator":navigation.route(),navigation.page());
    }
    @Override protected void onResume(){super.onResume();if(!LocationSnapshot.hasPermission(this))locationFix=null;
        try{LiveLocationService.initialize(this);}catch(java.io.IOException unavailable){locationMessage="Live session management could not be read. Remote revocation is not confirmed.";}
        IntentFilter filter=new IntentFilter(LocalLocationService.ACTION_STATE);filter.addAction(LiveLocationService.ACTION_STATE);
        if(!stateReceiverRegistered){if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(stateReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(stateReceiver,filter);stateReceiverRegistered=true;}
        getWindow().getDecorView().post(this::refreshLocation);
    }
    @Override protected void onPause(){if(stateReceiverRegistered){unregisterReceiver(stateReceiver);stateReceiverRegistered=false;}if(locationRequest!=null){if(locationRequest.isRequesting())locationMessage="The pending location request was canceled on leaving this screen. Tap Get a location fix to try again.";locationRequest.cancel();}super.onPause();}
    @Override protected void onDestroy(){liveAdmissionTicket="";if(setupDialog!=null)setupDialog.dismiss();if(locationRequest!=null)locationRequest.cancel();PagedDialog.dismissFor(this);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){if(!section.equals("calculator")){navigation.updatePage(deck.getPageIndex());state.putIntArray("anchor",deck.getReadingAnchor());}state.putStringArray("routes",navigation.routes());state.putIntArray("pages",navigation.pages());super.onSaveInstanceState(state);}
    private void show(String name){if(modalShowing())return;navigation.go(name,deck==null?0:deck.getPageIndex());render(navigation.route(),navigation.page());}

    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(5),0,dp(8));return t;}
    private TextView add(String s){TextView view=text(s,16,false);page.addView(view);return view;}
    private void title(String s){TextView t=text(s,24,true);if(android.os.Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);page.addView(t);}
    private GradientDrawable box(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(24));g.setStroke(dp(1),Color.argb(210,255,255,255));return g;}
    private int choiceColor(String route){switch(route){case "help":case "urgent":case "peers":return 0xD99EE1EF;case "findme":case "tools":return 0xD9A3EED9;case "quiet":return 0xD9DCD8F5;case "staying":return 0xD9F5CBE6;case "signs":return 0xD9FFE6BA;default:return 0xD9FFFFFF;}}
    private GradientDrawable tintedGlass(int color){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,0xD9F8FCFF});g.setCornerRadius(dp(24));g.setStroke(dp(1),Color.WHITE);return g;}
    private GradientDrawable glass(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xCBFFFFFF,0xAFEFF9FC});g.setCornerRadius(dp(26));g.setStroke(dp(1),0xFFFFFFFF);return g;}
    private void surface(View v){v.setBackground(glass());v.setElevation(dp(2));}
    private void interactive(View view,Drawable normal,int color,boolean dark){
        StateListDrawable states=new StateListDrawable();
        GradientDrawable pressed=box(dark?Color.rgb(9,48,83):Color.rgb(190,220,245));pressed.setStroke(dp(3),dark?Color.WHITE:teal);
        GradientDrawable focused=box(color);focused.setStroke(dp(3),dark?Color.WHITE:teal);
        states.addState(new int[]{android.R.attr.state_pressed},pressed);
        states.addState(new int[]{android.R.attr.state_focused},focused);
        states.addState(new int[]{},normal);view.setBackground(states);
    }
    private TextView note(String s){TextView t=text(s,14,false);t.setPadding(dp(18),dp(16),dp(18),dp(16));surface(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,dp(14));page.addView(t,p);return t;}
    private Button control(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ink);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(10),dp(16),dp(10));surface(b);interactive(b,glass(),0xFFF1F8FF,false);b.setOnClickListener(v->{if(!modalShowing())action.run();});return b;}
    private void button(String s,Runnable action,boolean primary){Button b=control(s,action);b.setTextSize(16);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(18),dp(13),dp(18),dp(13));b.setMinHeight(dp(52));if(primary){b.setTextColor(Color.WHITE);interactive(b,box(teal),teal,true);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));page.addView(b,p);}
    private void route(String label,String dest){button(label,()->show(dest),false);}
    private void choices(String a,String targetA,String b,String targetB){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        Button left=control(a,()->show(targetA)),right=control(b,()->show(targetB));left.setTextSize(16);right.setTextSize(16);left.setMinHeight(dp(52));right.setMinHeight(dp(52));
        interactive(left,tintedGlass(choiceColor(targetA)),choiceColor(targetA),false);interactive(right,tintedGlass(choiceColor(targetB)),choiceColor(targetB),false);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1);l.setMargins(0,dp(4),dp(4),dp(4));row.addView(left,l);
        LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,-2,1);r.setMargins(dp(4),dp(4),0,dp(4));row.addView(right,r);page.addView(row);
    }
    private void detail(String label,String message){button(label,()->new PagedDialog.Builder(this).setTitle(label).setMessage(message).setNegativeButton("Close",null).setNeutralButton("Calculator",(d,w)->cloak(true)).show(),false);}
    private void link(String label,String url){button(label,()->external(label,url),true);}
    private void external(String label,String url){
        if(modalShowing())return;
        boolean dial=url.startsWith("tel:");
        if(url.equals("tel:911")){emergency();return;}
        String disclosure="";
        if(url.equals("tel:18883737888") || url.startsWith("https://humantraffickinghotline.org/")) disclosure=HOTLINE_POLICY+"\n\n";
        if(url.startsWith("https://report.cybertip.org/") || url.startsWith("https://www.missingkids.org/gethelpnow/cybertipline")) disclosure=CYBERTIP_POLICY+"\n\n";
        if(url.startsWith("https://rainn.org/") || url.startsWith("https://1in6.org/") || url.startsWith("https://www.aftersilence.org/") || url.contains("/teamhope"))disclosure=PEER_POLICY+"\n\n";
        if(url.startsWith("https://rainn.org/"))disclosure+="RAINN Youth HelpRoom is for ages 14–24. Current sessions are unverified. Moderators are mandatory reporters; identifying details may lead to a report to authorities.\n\n";
        if(url.startsWith("https://www.aftersilence.org/"))disclosure+="After Silence is 18+. Joining needs birth date, email validation and moderator approval. Communications are not private.\n\n";
        if(url.startsWith("https://1in6.org/"))disclosure+="1in6 groups are for male-identifying survivors 18+. Third-party JotCast terms apply; chats are not fully confidential.\n\n";
        if(url.startsWith("https://takeitdown.ncmec.org/"))disclosure="Do not download or ask someone to send an image to use this tool. Use the device where it was originally taken. Images remain on your device; matching is limited to participating public or unencrypted services.\n\n";
        new PagedDialog.Builder(this).setTitle(dial?"Open phone dialer?":"Leave Safe Bridge?")
        .setMessage(disclosure+(dial?"The dialer opens with this number. You choose whether to place the call. Calls may appear in phone or carrier records.\n\n":"Your browser opens an independent service with its own privacy rules. Device activity, links and browsing may be monitored or leave records. Safe Bridge sends no report.\n\n")+url)
        .setNeutralButton("Calculator",(d,w)->cloak(true)).setNegativeButton("Cancel",null).setPositiveButton(dial?"Open dialer":"Open website",(d,w)->{
            try{startActivity(new Intent(dial?Intent.ACTION_DIAL:Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(ActivityNotFoundException e){new PagedDialog.Builder(this).setTitle("No compatible app found").setMessage("Use another safe device or compatible browser/phone app. Address or number:\n"+url).setNeutralButton("Calculator",(dialog,which)->cloak(true)).setNegativeButton("Close",null).show();}
        }).show();
    }
    private void emergency(){
        if(modalShowing())return;
        new PagedDialog.Builder(this).setTitle("Emergency • U.S. 911")
        .setMessage("Open 911 in your phone dialer? You must then tap Call in the phone app. Safe Bridge does not call automatically, dispatch help or send your location. Calls can leave records. Do not test with a real emergency call.")
        .setNeutralButton("Calculator",(d,w)->cloak(true)).setNegativeButton("Cancel",null).setPositiveButton("Open 911 dialer",(d,w)->{
            try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:911")));}
            catch(ActivityNotFoundException e){new PagedDialog.Builder(this).setTitle("Phone dialer unavailable").setMessage("This device cannot open a phone dialer. Use a phone to call 911 in the U.S. if safe.").setNeutralButton("Calculator",(dialog,which)->cloak(true)).setNegativeButton("Close",null).show();}
        }).show();
    }
    private LocationFix availableFix(){
        if(!LocationSnapshot.hasPermission(this)){locationFix=null;return null;}
        boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(locationFix!=null && !locationFix.approximatePermission && !fine)locationFix=null;
        Location recent=LocalLocationService.latest();
        LocationFix updating=recent==null?null:LocationSnapshot.fromLocation(recent,checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED,LocationFix.Source.DEVICE_SESSION);
        if(updating!=null && (locationFix==null || updating.fixElapsedRealtimeNanos>locationFix.fixElapsedRealtimeNanos))return updating;
        return locationFix;
    }
    private void updateSessionStrip(){
        if(sessionStrip==null)return;
        boolean live=LiveLocationService.isRunning(),ack=LiveLocationService.isActive(),unverified=LiveLocationService.connectionUnverified(),local=LocalLocationService.isActive();
        com.stange.safebridge.live.LiveCoordinator.Status status=LiveLocationService.status();
        String signature=live+":"+ack+":"+unverified+":"+local+":"+(status!=null && status.pendingRevocation)+":"+(status==null?"":status.failureCode);
        if(signature.equals(sessionSignature))return;sessionSignature=signature;sessionStrip.removeAllViews();
        if(live)sessionRow(unverified?"Connection unverified · retrying":ack?"Sharing update confirmed":"Waiting for a location","Stop sharing",this::stopLive);
        if(local)sessionRow("On-device updates active","Stop updates",this::stopLocation);
        if(status!=null && status.pendingRevocation){boolean cleanup="local_cleanup_pending".equals(status.failureCode);sessionRow(cleanup?"Server ended; cleanup pending":"Stopped here; revocation pending",cleanup?"Retry cleanup":"Retry revoke",this::retryRevocation);}
        sessionStrip.setVisibility(sessionStrip.getChildCount()==0?View.GONE:View.VISIBLE);
    }
    private void sessionRow(String label,String action,Runnable run){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView state=text(label,13,true);row.addView(state,new LinearLayout.LayoutParams(0,-2,1));row.addView(control(action,run));sessionStrip.addView(row);}
    private void refreshLocation(){
        if(isFinishing() || isDestroyed())return;updateSessionStrip();
        if((section.equals("findme") || section.equals("live")) && !modalShowing()){FocusBookmark focus=FocusBookmark.capture(getWindow().getDecorView());int[] anchor=deck.getReadingAnchor();render(section,deck.getPageIndex());if(anchor!=null)deck.restoreReadingAnchor(anchor);getWindow().getDecorView().post(()->{if(!isFinishing() && !isDestroyed())focus.restore(getWindow().getDecorView());});}
    }
    private void obtainFix(boolean cached){
        if(locationRequest!=null)locationRequest.cancel();
        locationMessage=cached?"Checking last-known location; it may be old.":"Requesting a location fix. This may take up to 20 seconds.";
        locationRequest=new LocationSnapshot(this,new LocationSnapshot.Callback(){
            public void onSnapshot(LocationFix fix){locationFix=fix;locationMessage="Snapshot available. Nobody has received it from Safe Bridge.";refreshLocation();}
            public void onFailure(String reason){locationMessage=reason;refreshLocation();}
        });
        if(cached)locationRequest.lastKnown();else locationRequest.request();refreshLocation();
    }
    private void featureMessage(String value){if(section.equals("live"))liveMessage=value;else locationMessage=value;}
    private void locationPermission(int purpose){
        if(permissionPurpose!=0)return;
        if(!LocationSnapshot.hasPermission(this)){permissionPurpose=purpose;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},101);return;}
        if(purpose==1 || purpose==3){obtainFix(purpose==3);return;}
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){permissionPurpose=purpose;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);return;}
        if(purpose==4)launchLiveSession();else launchLocalSession();
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){super.onRequestPermissionsResult(request,names,results);if(request!=101 && request!=102)return;int purpose=permissionPurpose;permissionPurpose=0;
        if(purpose<1 || purpose>4){featureMessage("No active location request was restored. Choose a Find Me action to continue.");refreshLocation();return;}
        if(!section.equals("findme") && !section.equals("live")){liveAdmissionTicket="";locationMessage="Permission returned after leaving Find Me. No session started.";return;}
        if(!LocationSnapshot.hasPermission(this)){liveAdmissionTicket="";featureMessage("Location permission was not granted. Guidance still works. You can change permission in Android app settings if safe.");refreshLocation();return;}
        if(request==102 && android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){liveAdmissionTicket="";featureMessage("Notifications were not allowed. Location updating or sharing was not started; one-time snapshots remain available.");refreshLocation();return;}
        locationPermission(purpose);
    }
    private void confirmLocalSession(int minutes){sessionMinutes=minutes;
        new PagedDialog.Builder(this).setTitle("Keep location updated on this device?").setMessage("This does not share live location. It keeps the latest fix on this device only, for up to "+minutes+" minutes. A visible location notification and system indicators may reveal use. Calculator and Close do not stop it; use Stop updates. Android can stop the app, and fixes or expiry cleanup can be delayed. No background-location permission or automatic restart is used.").setNegativeButton("Cancel",null).setNeutralButton("Calculator",(d,w)->cloak(true)).setPositiveButton("Start "+minutes+" minutes",(d,w)->locationPermission(2)).show();
    }
    private void launchLocalSession(){
        if(!section.equals("findme"))return;
        if(LiveLocationService.isRunning()){locationMessage="Stop live sharing before starting an on-device-only session.";refreshLocation();return;}
        try{Intent service=new Intent(this,LocalLocationService.class).setAction(LocalLocationService.ACTION_START);service.putExtra(LocalLocationService.EXTRA_DURATION_MINUTES,sessionMinutes);startForegroundService(service);locationMessage="Starting on-device updates. No live link and no automatic sharing.";}
        catch(RuntimeException e){locationMessage="Android could not start on-device updates. Nothing is being shared. Check location and notification settings if safe.";}
        refreshLocation();getWindow().getDecorView().postDelayed(this::refreshLocation,500);
    }
    private void stopLocation(){stopService(new Intent(this,LocalLocationService.class));locationFix=null;locationMessage="On-device updates stopped. Already copied or shared snapshots cannot be recalled.";
        getWindow().getDecorView().postDelayed(()->{if(isFinishing() || isDestroyed())return;if(section.equals("calculator"))render("calculator",0);else refreshLocation();},100);
    }
    private void chooseLiveEnd(){
        java.util.Calendar now=java.util.Calendar.getInstance();
        DatePickerDialog date=new DatePickerDialog(this,(picker,year,month,day)->{
            TimePickerDialog time=new TimePickerDialog(this,(clock,hour,minute)->{
                try{java.util.Calendar chosen=java.util.Calendar.getInstance();chosen.clear();chosen.setLenient(false);chosen.set(year,month,day,hour,minute,0);long end=chosen.getTimeInMillis();LiveSessionChoice.durationSeconds(end,System.currentTimeMillis());prepareLiveStart(end);}
                catch(IllegalArgumentException invalid){liveMessage="Choose a valid future end time using this device’s clock.";refreshLocation();}
            },now.get(java.util.Calendar.HOUR_OF_DAY),now.get(java.util.Calendar.MINUTE),android.text.format.DateFormat.is24HourFormat(this));
            time.setButton(DialogInterface.BUTTON_NEUTRAL,"Calculator",(d,w)->{d.dismiss();cloak(true);});setup(time);
        },now.get(java.util.Calendar.YEAR),now.get(java.util.Calendar.MONTH),now.get(java.util.Calendar.DAY_OF_MONTH));
        date.getDatePicker().setMinDate(System.currentTimeMillis());date.setButton(DialogInterface.BUTTON_NEUTRAL,"Calculator",(d,w)->{d.dismiss();cloak(true);});setup(date);
    }
    private void prepareLiveStart(long endAt){
        if(!LiveConfig.configured()){liveMessage="No live service is configured in this test build.";refreshLocation();return;}
        if(LocalLocationService.isActive()){liveMessage="Stop on-device-only updates before starting live sharing.";refreshLocation();return;}
        liveEndAtMs=endAt;
        final EditText code=new EditText(this);code.setSingleLine(true);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1024)});code.setSaveEnabled(false);code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);code.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        code.setHint("One-use service access code");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Service access code").setMessage("Use a one-use code issued by the configured service operator. It protects creation capacity; recipients do not need a code or account. Clipboard and device activity may be monitored. Safe Bridge does not supply or verify an operator here.").setView(code).setNegativeButton("Cancel",(d,w)->liveAdmissionTicket="").setNeutralButton("Calculator",(d,w)->{liveAdmissionTicket="";d.dismiss();cloak(true);}).setPositiveButton("Continue",(d,w)->{liveAdmissionTicket=code.getText().toString().trim();code.setText("");confirmLiveChoice();}).create();
        dialog.setOnCancelListener(d->{liveAdmissionTicket="";code.setText("");});setup(dialog);
    }
    private void confirmLiveChoice(){
        String duration=liveEndAtMs==0?"until you stop it":"until "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.LONG).format(new java.util.Date(liveEndAtMs));
        new PagedDialog.Builder(this).setTitle("Start live location sharing?").setOnCancel(()->liveAdmissionTicket="").setMessage("Share estimates with "+LiveConfig.endpoint()+" "+duration+". A connection loss will retry while this foreground session is still consented; Android can still end it. A visible location notification and system indicators may reveal use. Chosen times rely on device and server clocks. The operator processes coordinates. Anyone with the read link can forward it, and offline recipients may retain a last-known position and timestamp. Calculator and Close keep sharing running. Stop stops collection first; remote revocation may be pending offline. Copies and dispatched requests cannot be recalled. This does not dispatch help.").setNegativeButton("Cancel",(d,w)->liveAdmissionTicket="").setNeutralButton("Calculator",(d,w)->cloak(true)).setPositiveButton("Start sharing",(d,w)->locationPermission(4)).show();
    }
    private void liveStartButton(String label,Runnable run){Button b=control(label,run);boolean enabled=LiveConfig.configured() && !LiveLocationService.isRunning();b.setEnabled(enabled);b.setAlpha(enabled?1f:.5f);page.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private void launchLiveSession(){
        if(!section.equals("live") || !LiveConfig.configured())return;
        if(LocalLocationService.isActive()){liveMessage="Stop the on-device-only session before live sharing.";refreshLocation();return;}
        try{Intent intent=new Intent(this,LiveLocationService.class).setAction(LiveLocationService.ACTION_START).putExtra(LiveLocationService.EXTRA_END_AT_MS,liveEndAtMs).putExtra(LiveLocationService.EXTRA_ADMISSION_TICKET,liveAdmissionTicket);startForegroundService(intent);liveAdmissionTicket="";liveMessage="Start requested. The service status above shows whether the server acknowledged it. No recipient has been contacted.";}
        catch(RuntimeException denied){liveAdmissionTicket="";liveMessage="Android did not allow live sharing to start. No active session is confirmed.";}
        refreshLocation();getWindow().getDecorView().postDelayed(this::refreshLocation,500);
    }
    private void stopLive(){try{LiveLocationService.stopSession(this);}catch(Exception unavailable){liveMessage="Could not confirm local stop or remote revocation. Use the notification Stop or Android Active apps Stop; remote expiry remains separate.";}refreshLocation();}
    private void retryRevocation(){try{LiveLocationService.retryPending(this);}catch(Exception unavailable){liveMessage="Remote revocation is not confirmed. No new location collection was requested.";}refreshLocation();}
    private void exportLiveLink(boolean copy){
        String candidate=LiveLocationService.readLink();if(candidate==null){liveMessage="No active recipient link is available.";refreshLocation();return;}
        new PagedDialog.Builder(this).setTitle(copy?"Copy recipient link?":"Share recipient link?").setMessage("Anyone with this link may view location estimates while the session remains active and may forward it. Browser, clipboard and message records can reveal it. Selecting an app or opening the sharing screen does not prove sending or receipt. Stops and expiry cannot recall positions already copied. Only the read link is exported; sender and revocation credentials stay private.").setNegativeButton("Cancel",null).setNeutralButton("Calculator",(d,w)->cloak(true)).setPositiveButton(copy?"Copy link":"Choose app & recipient",(d,w)->{
            String link=LiveLocationService.readLink();if(link==null || !candidate.equals(link)){liveMessage="The sharing session changed. No link exported.";refreshLocation();return;}
            String value="Temporary location link — device estimates, not emergency response. Anyone with this link can view or forward it while available.\n"+link;
            if(copy){ClipData clip=ClipData.newPlainText("Temporary recipient link",value);if(android.os.Build.VERSION.SDK_INT>=33){android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);liveMessage="Recipient link copied. Clipboard and synced records may retain it.";}
            else try{Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(intent,"Choose app and recipient"));liveMessage="Sharing screen opened; sending and receipt are not confirmed.";}catch(ActivityNotFoundException unavailable){liveMessage="No compatible sharing app opened. No receipt is confirmed.";}
            refreshLocation();
        }).show();
    }
    private void liveLinkButton(String label,boolean copy){Button b=control(label,()->exportLiveLink(copy));boolean ready=LiveLocationService.readLink()!=null;b.setEnabled(ready);b.setAlpha(ready?1f:.5f);page.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private void exportSnapshot(boolean copy){
        final LocationFix fix=availableFix();if(fix==null){locationMessage="Get a fix before copying or sharing.";refreshLocation();return;}
        String warning="This exports one snapshot, not live tracking. "+(fix.isStale(SystemClock.elapsedRealtimeNanos())?"This fix is stale. ":"")+"The receiving app, clipboard or recipient may retain or forward it. Opening a sharing screen does not mean anyone received it. Safe Bridge cannot recall copies.";
        new PagedDialog.Builder(this).setTitle(copy?"Copy location snapshot?":"Share location snapshot?").setMessage(fix.summary(SystemClock.elapsedRealtimeNanos())+"\n\n"+warning).setNegativeButton("Cancel",null).setNeutralButton("Calculator",(d,w)->cloak(true)).setPositiveButton(copy?"Copy snapshot":"Choose app & recipient",(d,w)->{
            if(!LocationSnapshot.hasPermission(this) || (!fix.approximatePermission && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)){locationFix=null;locationMessage="Location permission changed. No snapshot exported.";refreshLocation();return;}
            String value=fix.shareText(SystemClock.elapsedRealtimeNanos());
            if(copy){android.content.ClipData clip=android.content.ClipData.newPlainText("Location snapshot",value);if(android.os.Build.VERSION.SDK_INT>=33){android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);locationMessage="Snapshot copied to Android clipboard. Clipboard/sync records may retain it.";}
            else try{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(send,"Choose app and recipient"));locationMessage="Android sharing screen opened. Sending and receipt are not confirmed.";}catch(ActivityNotFoundException e){locationMessage="No compatible sharing app opened. No receipt is confirmed.";}
            refreshLocation();
        }).show();
    }
    private void snapshotButton(String label,boolean copy){Button b=control(label,()->exportSnapshot(copy));boolean ready=availableFix()!=null;b.setEnabled(ready);b.setAlpha(ready?1f:.5f);page.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private void calculatorPage(){
        
        calculation=text(calculator.display(),28,true);calculation.setGravity(Gravity.END);calculation.setPadding(dp(12),dp(8),dp(12),dp(8));calculation.setBackground(box(Color.WHITE));page.addView(calculation);
        String[][] rows=getResources().getConfiguration().screenWidthDp>getResources().getConfiguration().screenHeightDp?new String[][]{{"AC","±","%","⌫","÷"},{"7","8","9",".","×"},{"4","5","6","0","−"},{"1","2","3","=","+"}}:new String[][]{{"AC","±","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"⌫","0",".","="}};
        int[] colors={Color.rgb(255,215,228),Color.rgb(211,225,255),Color.rgb(211,245,226),Color.rgb(255,230,186),Color.rgb(225,210,255)};
        for(int r=0;r<rows.length;r++){
            LinearLayout row=new LinearLayout(this);
            for(String key:rows[r]){Button b=new Button(this);b.setText(key);b.setTextSize(23);b.setTextColor(ink);b.setAllCaps(false);b.setMinHeight(dp(48));interactive(b,box(colors[r]),colors[r],false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(5),dp(3),dp(5));row.addView(b,lp);
                b.setOnClickListener(v->{calculator.key(key);calculation.setText(calculator.display());});
                if(key.equals("=")){b.setOnLongClickListener(v->{new PagedDialog.Builder(this).setTitle("Change view?").setMessage("Return to Safe Bridge? Its name and icon will become visible again.").setNegativeButton("Cancel",null).setPositiveButton("Return",(d,w)->cloak(false)).show();return true;});}
            }page.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void render(String name,int pageIndex){if(section.equals("live") && !name.equals("live")){liveAdmissionTicket="";}if(section.equals("findme") && !name.equals("findme") && locationRequest!=null){if(locationRequest.isRequesting())locationMessage="Location request canceled on leaving Find Me.";locationRequest.cancel();}section=name;
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        deck=new PageDeck(this);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFE6F5F6,0xFFD9F1F4,0xFFE9EFFA}));setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text(name.equals("calculator")?"Calculator":"Safe Bridge",20,true);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));top.addView(control("Close",()->finishAndRemoveTask()));top.setPadding(dp(18),dp(4),dp(18),dp(4));frame.addView(top);if(getResources().getConfiguration().screenHeightDp<500)top.setVisibility(name.equals("calculator")?View.VISIBLE:View.GONE);
        if(!name.equals("calculator")){LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(16),0,dp(16),dp(4));Button panic=control("911",()->emergency());panic.setContentDescription("Emergency · U.S. 911");panic.setTextColor(Color.WHITE);interactive(panic,box(Color.rgb(163,32,56)),Color.rgb(163,32,56),true);controls.addView(panic,new LinearLayout.LayoutParams(0,-2,1));Button find=control("Find Me",()->show("findme"));LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(0,-2,1);fl.setMargins(dp(6),0,0,0);controls.addView(find,fl);Button mask=control("◈",()->cloak(true));mask.setTextSize(24);mask.setContentDescription("Switch to calculator");mask.setMinWidth(dp(56));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-2,-2);ml.setMargins(dp(6),0,0,0);controls.addView(mask,ml);frame.addView(controls);}
        sessionSignature=null;sessionStrip=new LinearLayout(this);sessionStrip.setOrientation(LinearLayout.VERTICAL);sessionStrip.setPadding(dp(16),0,dp(16),0);frame.addView(sessionStrip);updateSessionStrip();
        frame.addView(deck,new LinearLayout.LayoutParams(-1,0,1));
        if(!name.equals("calculator")){
            LinearLayout dock=new LinearLayout(this);dock.setPadding(dp(16),dp(6),dp(16),dp(8));
            String[] labels={"Home","Privacy","More"}, targets={"home","safety","supportmenu"};
            for(int k=0;k<labels.length;k++){final String target=targets[k];Button nav=control(labels[k],()->show(target));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);dock.addView(nav,lp);}frame.addView(dock);
        }
        if(name.equals("calculator")){calculatorPage();int index=frame.indexOfChild(deck);page.removeView(calculation);frame.addView(calculation,index,new LinearLayout.LayoutParams(-1,-2));deck.setSource(page);return;}
        
        switch(name){
        case "home":
            TextView homeTitle=text("Choose your next step",20,true);if(android.os.Build.VERSION.SDK_INT>=28)homeTitle.setAccessibilityHeading(true);page.addView(homeTitle);
            TextView caution=text("Device activity may be monitored.",13,false);page.addView(caution);
            choices("Support now","help","Find Me","findme");
            choices("Can’t speak","quiet","Can’t leave","staying");
            choices("Warning signs","signs","Peer groups","peers");
            choices("Safety tools","tools","For someone","someone");
            TextView scope=text("U.S. guide • Independent services\nLinks and calls can leave traces.",13,false);page.addView(scope);
            break;
        case "urgent":
            title("Choose what fits right now");
            route("I cannot speak or I am hiding","quiet");route("I cannot leave right now","staying");route("I feel overwhelmed","distress");route("Find Me · location snapshot","findme");route("Reach a trained advocate","help");break;
        case "quiet":
            title("If speaking is not safe");
            link("Open hotline text/chat options","https://humantraffickinghotline.org/en/get-help");route("Find Me · copy/share a snapshot","findme");button("Switch to calculator",()->cloak(true),false);
            add("Text 233733 for the U.S. trafficking hotline. Chat can be unavailable. Texts, browser activity and notifications can be seen or heard; choose only an option that fits your situation. This app cannot silence your phone or contact anyone silently.");
            add("If you are hiding, you do not have to reveal where you are in this app. Consider whether another device or a safe person's help is possible. In immediate danger in the U.S., use 911 if safe; this guide cannot dispatch help.");route("Options while unable to leave","staying");break;
        case "staying":
            title("You can seek support while staying");
            route("Text/chat or advocate options","help");route("Consider a safe contact or signal","plan");route("Find Me · location snapshot","findme");
            add("Leaving or confronting someone may increase danger. You do not need to leave, report, or decide everything now to ask for support. You know your situation best.");
            add("If safe, consider a trusted contact, a signal and the response you want. Think about communication, medication and important numbers only if doing so will not increase risk. No plan details are collected here.");break;
        case "distress":
            title("One small choice is enough");
            route("Reach a trained advocate","help");route("Connect with peers","peers");button("Switch to calculator",()->cloak(true),false);
            add("You do not have to explain everything or decide now. If it feels comfortable, pause and notice something around you. Skip this if it makes you feel worse. A safe person or advocate may help you choose your next step.");
            add("This app is not crisis care or a monitored service. If someone is in immediate danger in the U.S., use 911 if safe.");break;
        case "supportmenu":
            title("More support choices");
            route("Trained advocate","help");route("I feel overwhelmed","distress");route("Find local services","resources");route("Concerned family or friend","someone");route("Child exploitation reporting","child");route("Intimate-image tools","images");route("Learn prevention & warning signs","signs");route("About, sources & close","about");break;
        case "resources":
            title("Find practical support");
            link("Search official referral directory","https://humantraffickinghotline.org/en/service-provider-search");route("Ask an advocate for referrals","help");
            add("The hotline directory can be searched by city, state or service. Ask about shelter, medical care, legal support or other needs that fit your situation. Listings are a selection; they do not guarantee openings, eligibility or a safe placement.");
            add("Searching or contacting a provider can leave records. Safe Bridge does not send your location to this directory or arrange services.");break;
        case "findme":
            title("Find Me · snapshots");
            choices("Snapshots","findme","Live links · lab","live");
            if(LocalLocationService.isActive())add("On-device-only updates are active. Stop is above this page. No recipient receives updates.");
            button("Get a location fix",()->locationPermission(1),true);snapshotButton("Share snapshot · choose recipient",false);snapshotButton("Copy snapshot",true);
            if(availableFix()==null)add("Get a fix to enable copy/share. Location access is optional; guidance works without it.");
            add(locationMessage);
            LocationFix fix=availableFix();if(fix!=null)note(fix.description(SystemClock.elapsedRealtimeNanos())).setTag("focus:fix-readout");
            button("Refresh location status",this::refreshLocation,false);button("Try last-known location · may be old",()->locationPermission(3),false);
            if(LocalLocationService.isActive()){add(LocalLocationService.fixDescription());note("On-device updates active. No live recipient feed. Requested time remaining: "+Math.max(0,(LocalLocationService.endsAtElapsed()-SystemClock.elapsedRealtime())/60000)+" minutes. Fixes/cleanup can be delayed. Calculator and Close keep this session running.");button("Stop on-device updates",this::stopLocation,true);}
            else{add(LocalLocationService.lastProblem());button("Keep updated on device · 15 minutes",()->confirmLocalSession(15),false);button("Keep updated on device · 60 minutes",()->confirmLocalSession(60),false);}
            note(LiveConfig.configured()?"Snapshots and live recipient links are different. Sharing a snapshot never starts live tracking.":"No hosted live service is configured. Snapshot sharing still works independently; it never becomes live tracking. Recipients can read plain text without Safe Bridge.");
            add("Location can be approximate, old, inaccurate or unavailable indoors/offline. Accuracy is an estimate, not a guaranteed boundary. Permissions, battery, Android Stop or device conditions can end updates. A visible notification and system location indicators may reveal use, including in calculator mode.");break;
        case "live":
            title("Live recipient links · lab");
            add(LiveLocationService.statusText()).setTag("focus:live-status");
            if(LiveLocationService.isRunning())button("Stop sharing on this phone",this::stopLive,true);
            if(!LiveConfig.configured()){note("Live sharing is not set up in this test build. No location is sent to a server. Snapshots remain available.");}
            if(!LiveLocationService.isRunning()){liveStartButton("Share until I stop",()->prepareLiveStart(0));liveStartButton("Choose an end date & time",this::chooseLiveEnd);}
            liveLinkButton("Share recipient link",false);liveLinkButton("Copy recipient link",true);
            button("Retry pending remote revocation",this::retryRevocation,false);button("Refresh sharing status",this::refreshLocation,false);
            if(!liveMessage.isEmpty())add(liveMessage);add(LiveLocationService.lastProblem());
            note("Calculator and Close do not stop an active sharing session. Stop clears local collection first; if offline, remote revocation remains pending. Previously dispatched updates may arrive before server Stop is acknowledged. Old server points are removed after their data deadline; a recipient may retain a labeled last-known copy in tab memory. Copies already exported cannot be recalled.");
            add("Location estimates can be old or unavailable. A foreground notification and system location indicators remain discoverable. Android Stop, permissions, battery, network or device conditions may interrupt the session. This is not emergency monitoring or guaranteed tracking. The recipient needs only a supported browser, but actual public availability requires deployment and independent testing.");break;
        case "help":
            title("Support at your pace");
            link("Call trafficking hotline · U.S.","tel:18883737888");
            link("Hotline chat, text & referrals","https://humantraffickinghotline.org/en/get-help");
            choices("Quiet options","quiet","Overwhelmed","distress");route("Connect with peers","peers");
            add("National Human Trafficking Hotline: 1-888-373-7888, advertised 24/7. Text 233733; see the official site for options. Chat may be unavailable; response time is not guaranteed.");
            detail("Privacy before contacting",HOTLINE_POLICY);
            add("You deserve support even if you are unsure what to call your experience. A trained advocate can discuss options; you do not have to decide everything now.");
            link("Official confidentiality policy","https://humantraffickinghotline.org/en/confidentiality-policy");break;
        case "signs":
            title("Recognize pressure & control");
            route("Work, relationships & recruitment","control");route("Online pressure & warning signs","online");route("Support someone safely","someone");
            note("Warning signs are not proof or a diagnosis. Exploitation can involve a partner, family member, employer or stranger. Responsibility belongs to the person causing harm.");
            link("Official trafficking warning signs","https://humantraffickinghotline.org/en/human-trafficking/recognizing-signs");break;
        case "control":
            title("Notice control, not stereotypes");
            choices("Get support","help","Safety plan","plan");
            add("Threats, violence, withheld pay, controlled identity documents, isolation or constant monitoring can be warning signs. Someone may feel unable to leave work or sexual activity because of threats, debt or dependence.");
            add("Recruitment may begin with trust, romance, a job or help with housing. Pressure to decide immediately, unclear pay or duties, and changing conditions deserve attention.");
            add("You do not need proof to ask for support. For a job offer, if safe, ask about duties, pay, location and conditions and review the offer with someone you trust.");
            link("Read official warning signs","https://humantraffickinghotline.org/en/human-trafficking/recognizing-signs");break;
        case "online":
            title("Online pressure is a warning");
            choices("Image tools","images","Child report","child");
            add("Someone may build trust in a game, social feed or messaging app, then push sexual conversations, images or an in-person meeting. Gifts, secrecy, threats or blackmail can be used to gain control, even by someone familiar.");
            add("If someone threatens to share an image, it is not your fault. You can ask a safe adult or advocate for help. You do not need to investigate or confront the person.");
            link("NCMEC online-enticement guidance","https://www.missingkids.org/netsmartz/topics/online-enticement");break;
        case "someone":
            title("Listen. Offer choices.");
            choices("Find support","help","Child reporting","child");
            add("Listen calmly, believe their experience and avoid blame. Ask what feels safe and what support they want. Avoid pressuring someone to leave, confront a person or share details they do not want to share.");
            add("If a child shares exploitation, let them know it is not their fault. A safe adult or trained advocate can help. Do not investigate, lure a suspect or search for abuse material.");
            link("NCMEC guidance for safe conversations","https://www.missingkids.org/netsmartz/topics/online-enticement");break;
        case "tools":
            title("Useful choices, at your pace");
            route("Find Me · location snapshot","findme");route("Think through a safety plan","plan");route("Find local services","resources");route("Device safety & traces","safety");route("Online pressure & prevention","online");route("Intimate-image tools","images");route("Disguise & exit controls","intro");break;
        case "plan":
            title("A plan that fits your situation");
            choices("Get support","help","Device safety","safety");
            add("You know your situation best. Leaving or confronting someone may increase danger. You can consider options with an advocate without committing to a decision.");
            add("Who feels safe to contact? If useful and safe, agree on a signal and what response you want. Consider whether someone else could see your messages or overhear a call.");
            add("If safe, think about access to medication, identification, important numbers and communication. Choose only steps that fit your circumstances. This app asks for and saves no planning details.");
            link("Official safety-planning guidance","https://humantraffickinghotline.org/en/safety-planning-information");break;
        case "peers":
            title("Connect with people who understand");
            route("RAINN Youth HelpRoom · 14–24","rainn");route("After Silence forum · 18+","forum");route("1in6 groups · male-identifying 18+","oneinsix");route("Team HOPE · phone peer support","hope");
            note(PEER_POLICY);link("NNEDV safer online-group guidance","https://www.techsafety.org/online-groups");break;
        case "rainn":
            title("RAINN Youth HelpRoom");add("The provider describes moderated text groups for survivors aged 14–24, with separate rooms for 14–17 and 18–24. Current session availability and schedule are unverified; check with RAINN. This is not emergency response.");
            link("View HelpRoom rules & schedule","https://rainn.org/help-and-healing/hotline/youth-helproom/");
            note("Moderators are mandatory reporters. Sharing identifying details may require a report to authorities. No online safety or confidentiality guarantee is possible.");route("Other peer options","peers");break;
        case "forum":
            title("After Silence forum");add("For survivors of sexual violence aged 18+. Moderated peer forums; public sections are visible without membership.");
            link("View current registration guidance","https://www.aftersilence.org/forum/index.php?/topic/151788-attention-new-survivors-registering-at-after-silence/");
            note("Joining requires birth date, a valid non-disposable email, email validation and moderator approval. Approval is not immediate. Communications are not private; this is not professional or emergency help.");
            link("Read forum terms","https://www.aftersilence.org/forum/index.php?/topic/2390-terms-of-service/");break;
        case "oneinsix":
            title("1in6 online groups");add("For male-identifying survivors aged 18+ with unwanted sexual experiences. Free, facilitated text groups run on a schedule with limited places; sessions can be canceled.");
            link("View 1in6 groups & schedule","https://1in6.org/support-group-chat");
            note("Not crisis response or therapy. Eligibility cannot be fully verified, and participants may copy conversations. Chats are not fully anonymous or confidential. Third-party JotCast privacy terms apply.");
            link("Read group terms (PDF)","https://1in6.org/1in6/documents/OSGs-terms-of-service.pdf");break;
        case "hope":
            title("NCMEC Team HOPE");add("Free phone peer support with trained volunteers for families of missing or exploited children and eligible adult survivors of childhood exploitation. This is matched support, not an online forum.");
            link("Read Team HOPE eligibility & contact","https://www.missingkids.org/gethelpnow/support/teamhope");
            note("Check eligibility with the service. No response time or availability is promised here. Calls can leave records. NCMEC privacy practices apply.");
            link("Read NCMEC privacy policy","https://www.missingkids.org/footer/privacypolicy");break;
        case "child":
            title("Child exploitation reporting");
            link("Open official CyberTipline","https://report.cybertip.org/");
            detail("Before submitting a report",CYBERTIP_POLICY);
            route("Image support for under-18 images","images");
            add("Anyone can report suspected child sexual exploitation, including experiences from childhood if you are now an adult. You do not need to investigate or prove a crime. Describe what you know and separate observations from guesses.");
            note("Do not download or forward sexual images of children, search for abuse material or confront a suspected exploiter. Safe Bridge collects no reports, identifying details or images.");
            link("Read CyberTipline FAQ","https://report.cybertip.org/faqs");break;
        case "images":
            title("Image threats are not your fault");
            route("Taken before age 18 · Take It Down","takeitdown");route("Adult images · StopNCII","stopncii");route("Report child exploitation","child");
            add("A safe adult or advocate can help you consider options. Safe Bridge never accepts images. These independent tools cannot guarantee removal everywhere.");break;
        case "takeitdown":
            title("NCMEC Take It Down");add("For qualifying intimate images of you taken before age 18, even if you are an adult now. Available beyond the U.S. Read eligibility before using it.");
            link("Read Take It Down eligibility & FAQ","https://takeitdown.ncmec.org/faq/");link("Open Take It Down","https://takeitdown.ncmec.org/");
            note("Do not download or ask someone to forward an image to submit it. Use the device where the image was originally taken. If you no longer have that device, NCMEC directs you to CyberTipline for assistance.");
            add("Images stay on your device during hashing. Hashes are shared with participating services for matching on public or unencrypted areas. It does not cover all websites or encrypted messaging; removal is not guaranteed.");break;
        case "stopncii":
            title("StopNCII adult-image tool");add("For adult intimate images of yourself that you already have, review StopNCII eligibility. The tool is available worldwide; the official service decides eligibility.");
            link("Read StopNCII eligibility & FAQ","https://stopncii.org/faq/");link("Open StopNCII information","https://stopncii.org/");
            add("The image remains on your device while a hash is created. Participating platforms use hashes under their policies; this does not remove content from the whole internet or encrypted messages. Privacy terms apply, including technical records.");break;
        case "safety":
            title("Your device may leave traces");
            route("Calculator disguise & exit limits","intro");
            add("Someone with access to this device, accounts or network may see installed apps, links, calls or browsing. Safe Bridge cannot detect monitoring or make a compromised device safe.");
            note("Changing passwords, tracking settings or account access may alert an abusive person and increase danger. Consider planning changes with an advocate. If possible, seek help on a device and account they cannot access.");
            detail("What Safe Bridge stores", "Offline guidance has no accounts or analytics. Snapshots/latest fixes stay in memory unless you copy or share them. If live sharing is configured and explicitly started, coordinates go to its server and minimal management credentials are encrypted in private, backup-excluded storage for Stop/recovery. No coordinate history or planning answers are stored by the app. The server operator can process coordinates. Browser, destination apps, OS records and device monitoring are outside this app’s control. This lab has no hosted service configured.");
            link("NNEDV technology safety planning","https://www.techsafety.org/resources-survivors/technology-safety-plan");
            link("NNEDV safer app guidance","https://www.techsafety.org/choosingapps");break;
        case "intro":
            title("Disguise has limits");
            add("The diamond button switches to a calculator and changes the launcher name/icon. Long-press equals to request a return to Safe Bridge. Accessibility services can perform a long-click. Back in the calculator closes the app.");
            note("Calculator and Close do not stop active location updating or sharing; use the visible Stop control or notification Stop action. Its notification and system indicators stay visible. This is a visual disguise, not protection from discovery. Settings, app info, download/account records, monitoring and launcher caches can reveal Safe Bridge. Try controls only when safe.");
            add("Close removes this task from Recents. It does not erase traces, uninstall the app or clear browser/call records. Uninstalling may still leave account or download records. Calculator mode stays selected after reopening; calculations are not saved.");break;
        default:
            title("Independent resource guide");
            route("Disguise & exit controls","intro");route("Device privacy & traces","safety");
            detail("Close this app", "Close removes the task from Recents but does not erase activity, browser/call records or installation records.");button("Close Safe Bridge",()->finishAndRemoveTask(),false);
            add("Safe Bridge 0.8 reconnect lab • U.S.-focused. No one receives messages here. Support providers are independent links: no partnerships, provider integrations, hotline operation, case tracking or report submission. Optional Find Me provides snapshots. This lab adds a gated live client and backend/recipient source; no public live service is configured or deployed.");
            note("Not for public deployment yet. Survivor, child-safety, privacy and accessibility review are still needed. Emergency 911 is U.S.-only. Outside the U.S., seek appropriate local services; no verified international emergency directory is included.");
            add("Primary-source information checked November 16, 2026. Links, capacity and policies can change. Source details and limits are included in the accompanying source artifact.");
            link("Hotline safety planning source","https://humantraffickinghotline.org/en/safety-planning-information");link("NCMEC online-safety source","https://www.missingkids.org/netsmartz/topics/online-enticement");break;
        }
        deck.setSource(page);deck.restorePageIndex(pageIndex);if(restoredAnchor!=null){deck.restoreReadingAnchor(restoredAnchor);restoredAnchor=null;}
    }
    @Override public void onBackPressed(){if(section.equals("calculator")){finishAndRemoveTask();return;}if(deck.previousPage())return;if(navigation.back(deck.getPageIndex())){render(navigation.route(),navigation.page());}else finishAndRemoveTask();}
}
