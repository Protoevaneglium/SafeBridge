package com.stange.safebridge;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.*;
import android.widget.*;

/** Static, permission-free guidance. No network, storage, analytics or reporting backend. */
public class MainActivity extends Activity {
    private final int ink=Color.rgb(19,46,70), teal=Color.rgb(17,88,154);
    private static final String HOTLINE_POLICY="Your name and identifying details are optional. The hotline generally requires explicit consent to disclose identifying information, but reports suspected abuse of a minor, potential harm to you or others, and situations legally requiring a report. Read its policy before sharing.";
    private static final String CYBERTIP_POLICY="If you submit a CyberTipline report, NCMEC analysts process it and make it available to the appropriate law enforcement agency. Opening this page does not submit a report. You decide whether to complete and submit the official form.";
    private LinearLayout page; private String section="home";
    private PageDeck deck;
    private final Calculator calculator=new Calculator();
    private TextView calculation;
    private boolean isCloaked(){return getPackageManager().getComponentEnabledSetting(new ComponentName(this,"com.stange.safebridge.CalculatorLauncher"))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    private void cloak(boolean enabled){
        PackageManager pm=getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this,enabled?"com.stange.safebridge.CalculatorLauncher":"com.stange.safebridge.SafeLauncher"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this,enabled?"com.stange.safebridge.SafeLauncher":"com.stange.safebridge.CalculatorLauncher"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        calculator.reset();show(enabled?"calculator":"home");
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(230,245,246));getWindow().setNavigationBarColor(Color.rgb(233,239,250));show(isCloaked()?"calculator":"home");}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(5),0,dp(8));return t;}
    private void add(String s){page.addView(text(s,16,false));}
    private void title(String s){page.addView(text(s,28,true));}
    private GradientDrawable box(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(24));g.setStroke(dp(1),Color.argb(210,255,255,255));return g;}
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
    private void note(String s){TextView t=text(s,14,false);t.setPadding(dp(18),dp(16),dp(18),dp(16));surface(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,dp(14));page.addView(t,p);}
    private Button control(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ink);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(10),dp(16),dp(10));surface(b);interactive(b,glass(),0xFFF1F8FF,false);b.setOnClickListener(v->action.run());return b;}
    private void button(String s,Runnable action,boolean primary){Button b=control(s,action);b.setTextSize(16);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(20),dp(18),dp(20),dp(18));b.setMinHeight(dp(60));if(primary){b.setTextColor(Color.WHITE);interactive(b,box(teal),teal,true);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));page.addView(b,p);}
    private void route(String label,String dest){button(label,()->show(dest),false);}
    private void resource(String symbol,String heading,String detail,String dest,int accent){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(14),dp(18),dp(14));surface(card);interactive(card,glass(),0xFFF1F8FF,false);
        TextView icon=text(symbol,23,true);icon.setGravity(Gravity.CENTER);icon.setPadding(0,0,0,0);icon.setBackground(box(accent));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);labels.setPadding(dp(14),0,dp(8),0);TextView h=text(heading,18,true);h.setPadding(0,0,0,dp(4));labels.addView(h);TextView d=text(detail,14,false);d.setPadding(0,0,0,0);labels.addView(d);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",26,false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(arrow);card.setClickable(true);card.setFocusable(true);card.setContentDescription(heading+". "+detail);card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);card.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());info.setClickable(true);}});card.setOnClickListener(v->show(dest));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));page.addView(card,lp);
    }
    /** Original typographic hero; no reference illustrations or mood data. */
    private void welcome(){
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(22),dp(22),dp(22),dp(24));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xE6DEFFF4,0xE6D9F2FC});
        background.setCornerRadius(dp(30));background.setStroke(dp(1),Color.WHITE);hero.setBackground(background);
        TextView eyebrow=text("SUPPORT AT YOUR PACE",12,true);eyebrow.setLetterSpacing(.08f);hero.addView(eyebrow);
        TextView heading=text("Your next step.\nYour choice.",30,true);hero.addView(heading);
        hero.addView(text("Find support and understand your options, one step at a time.",16,false));
        TextView badge=text("Independent • Links to existing services",12,true);badge.setPadding(dp(12),dp(9),dp(12),dp(9));badge.setBackground(box(0xDAFFFFFF));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMargins(0,dp(12),0,0);hero.addView(badge,bp);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(6),0,dp(4));page.addView(hero,hp);
    }
    private void link(String label,String url){button(label,()->external(label,url),true);}
    private void external(String label,String url){
        boolean dial=url.startsWith("tel:");
        if(url.equals("tel:911")){emergency();return;}
        String disclosure="";
        if(url.equals("tel:18883737888") || url.startsWith("https://humantraffickinghotline.org/")) disclosure=HOTLINE_POLICY+"\n\n";
        if(url.startsWith("https://report.cybertip.org/") || url.startsWith("https://www.missingkids.org/gethelpnow/cybertipline")) disclosure=CYBERTIP_POLICY+"\n\n";
        new PagedDialog.Builder(this).setTitle(dial?"Open phone dialer?":"Leave Safe Bridge?")
        .setMessage(disclosure+(dial?"The dialer opens with this number. You choose whether to place the call. Calls may appear in phone or carrier records.\n\n":"This opens your browser. The website has its own privacy practices. Browser history, device monitoring or network records may reveal activity. No report is sent by this app.\n\n")+url)
        .setNeutralButton("Calculator",(d,w)->cloak(true)).setNegativeButton("Cancel",null).setPositiveButton(dial?"Open dialer":"Open website",(d,w)->{
            try{startActivity(new Intent(dial?Intent.ACTION_DIAL:Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(ActivityNotFoundException e){new PagedDialog.Builder(this).setTitle("No compatible app found").setMessage("Use another safe device or compatible browser/phone app. Address or number:\n"+url).setNeutralButton("Calculator",(dialog,which)->cloak(true)).setPositiveButton("Close",null).show();}
        }).show();
    }
    private void emergency(){
        new PagedDialog.Builder(this).setTitle("Emergency • U.S. 911")
        .setMessage("Open 911 in your phone dialer? You must then tap Call in the phone app. Safe Bridge does not call automatically, dispatch help or send your location. Calls can leave records. Do not test with a real emergency call.")
        .setNeutralButton("Calculator",(d,w)->cloak(true)).setNegativeButton("Cancel",null).setPositiveButton("Open 911 dialer",(d,w)->{
            try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:911")));}
            catch(ActivityNotFoundException e){new PagedDialog.Builder(this).setTitle("Phone dialer unavailable").setMessage("This device cannot open a phone dialer. Use a phone to call 911 in the U.S. if safe.").setNeutralButton("Calculator",(dialog,which)->cloak(true)).setPositiveButton("Close",null).show();}
        }).show();
    }
    private void calculatorPage(){
        
        calculation=text(calculator.display(),28,true);calculation.setGravity(Gravity.END);calculation.setPadding(dp(12),dp(8),dp(12),dp(8));calculation.setBackground(box(Color.WHITE));page.addView(calculation);
        String[][] rows=getResources().getConfiguration().screenWidthDp>getResources().getConfiguration().screenHeightDp?new String[][]{{"AC","±","%","⌫","÷"},{"7","8","9",".","×"},{"4","5","6","0","−"},{"1","2","3","=","+"}}:new String[][]{{"AC","±","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"⌫","0",".","="}};
        int[] colors={Color.rgb(255,215,228),Color.rgb(211,225,255),Color.rgb(211,245,226),Color.rgb(255,230,186),Color.rgb(225,210,255)};
        for(int r=0;r<rows.length;r++){
            LinearLayout row=new LinearLayout(this);
            for(String key:rows[r]){Button b=new Button(this);b.setText(key);b.setTextSize(23);b.setTextColor(ink);b.setAllCaps(false);b.setMinHeight(dp(48));interactive(b,box(colors[r]),colors[r],false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(5),dp(3),dp(5));row.addView(b,lp);
                b.setOnClickListener(v->{calculator.key(key);calculation.setText(calculator.display());});
                if(key.equals("=")){b.setOnLongClickListener(v->{new PagedDialog.Builder(this).setTitle("Change view?").setMessage("Return to Safe Bridge? Its name and icon will become visible again.").setNegativeButton("Cancel",null).setPositiveButton("Return",(d,w)->cloak(false)).show();return true;});}
            }page.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        }
    }
    private void show(String name){section=name;
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        deck=new PageDeck(this);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFE6F5F6,0xFFD9F1F4,0xFFE9EFFA}));setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text(name.equals("calculator")?"Calculator":"Safe Bridge",21,true);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));top.addView(control("Close",()->finishAndRemoveTask()));top.setPadding(dp(22),dp(12),dp(20),dp(12));frame.addView(top);if(getResources().getConfiguration().screenHeightDp<500)top.setVisibility(View.GONE);
        if(!name.equals("calculator")){LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(20),0,dp(20),dp(8));Button panic=control("Emergency • 911",()->emergency());panic.setTextColor(Color.WHITE);interactive(panic,box(Color.rgb(163,32,56)),Color.rgb(163,32,56),true);panic.setMinHeight(dp(52));controls.addView(panic,new LinearLayout.LayoutParams(0,-2,1));Button mask=control("◈",()->cloak(true));mask.setTextSize(24);mask.setContentDescription("Switch to calculator");mask.setMinHeight(dp(52));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(64),-2);ml.setMargins(dp(10),0,0,0);controls.addView(mask,ml);frame.addView(controls);}
        frame.addView(deck,new LinearLayout.LayoutParams(-1,0,1));
        if(!name.equals("calculator")){
            LinearLayout dock=new LinearLayout(this);dock.setPadding(dp(16),dp(6),dp(16),dp(8));
            String[] labels={"Home","Privacy","About"}, targets={"home","safety","about"};
            for(int k=0;k<labels.length;k++){final String target=targets[k];Button nav=control(labels[k],()->show(target));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);dock.addView(nav,lp);}frame.addView(dock);if(getResources().getConfiguration().screenHeightDp<500)dock.setVisibility(View.GONE);
        }
        if(name.equals("calculator")){calculatorPage();int index=frame.indexOfChild(deck);frame.removeView(deck);page.setPadding(dp(16),0,dp(16),dp(8));frame.addView(page,index,new LinearLayout.LayoutParams(-1,0,1));return;}
        
        if(!name.equals("home") && getResources().getConfiguration().screenHeightDp<500)route("‹ Home","home");
        switch(name){
        case "home":
            TextView greeting=text("A little support.\nA next step.",30,true);greeting.setPadding(dp(6),dp(4),0,dp(12));page.addView(greeting);
            TextView intro=text("U.S. resource guide • Not a monitored service",13,false);page.addView(intro);
            resource("♡","Find support","Connect with a trained advocate","help",0xFF9EE1EF);resource("◇","Concerned about a child","Understand official reporting options","child",0xFFA3EED9);resource("▧","Intimate-image support","Find tools and eligibility guidance","images",0xFFF5CBE6);resource("⊙","Device safety","Understand privacy and digital traces","safety",0xFFDCD8F5);
            route("Before you begin · Privacy & disguise","intro");route("About & sources","about");break;
        case "intro":
            title("Before you begin");
            note("U.S. RESOURCE PROTOTYPE • Not an emergency or monitored service. No one receives messages here. In immediate danger in the U.S., call 911 if safe to do so.");
            note("The ◈ button instantly opens a colorful calculator and changes the launcher name/icon. Long-press = in the calculator to return. Android accessibility services can also perform a long-click. This is a visual disguise, not protection from discovery: Settings, app info, account/download records, monitoring and launcher caches can reveal Safe Bridge. Try the controls only when safe.");
            note("Installed apps, browser history and call records can be discovered. Close does not erase traces or hide this app. Consider another device if yours may be monitored.");break;
        case "help":
            title("You deserve support.");add("If someone is forcing or threatening you into work or sexual activity, a trained advocate can help you consider options. You do not have to decide everything now.");
            note("Choose a way to reach help only if it feels safe. Do not assume leaving or confronting someone is the safest immediate step. An advocate can help with an individualized plan.");
            add("National Human Trafficking Hotline\n1-888-373-7888 • U.S.");note(HOTLINE_POLICY);link("Read hotline confidentiality policy","https://humantraffickinghotline.org/en/confidentiality-policy");link("Open hotline phone dialer","tel:18883737888");link("Hotline help, chat & referrals","https://humantraffickinghotline.org/en/get-help");link("Read official safety planning guidance","https://humantraffickinghotline.org/en/safety-planning-information");add("The hotline’s own privacy and reporting policies apply. This app is independent and cannot guarantee confidentiality, response time or availability.");break;
        case "child":
            title("Concerned about a child?");add("NCMEC’s CyberTipline accepts reports of suspected child sexual exploitation. NCMEC analysts process reports and make them available to the appropriate law enforcement agency.");
            note("You do not need to investigate or prove a crime yourself. Do not confront a suspected exploiter, lure them, or search for abuse material. Do not download or forward sexual images of children.");
            add("On the official reporting site, describe what you directly know. Keep observations separate from guesses. This app does not collect names, reports, photos or evidence.");link("Open NCMEC CyberTipline","https://report.cybertip.org/");link("Read how CyberTipline works","https://www.missingkids.org/gethelpnow/cybertipline");add("If you are a child, you can ask a safe, trusted adult for help. You can also contact the CyberTipline yourself. If someone is in immediate danger in the U.S., call 911 if safe.");break;
        case "images":
            title("It is not your fault.");add("If someone is threatening to share an intimate image, you can seek support. A safe, trusted adult or trained advocate may help you consider your next step.");
            note("Taken before you were 18? NCMEC’s Take It Down may help limit sharing on participating public or unencrypted services. You can use it even if you are an adult now. It does not cover all websites or encrypted messaging, and removal is not guaranteed.");
            add("Do not download, ask someone to send, or share an image to submit it. NCMEC’s FAQ says to use the device where the image was originally taken. If you no longer have access to that device, seek help through the CyberTipline instead. Safe Bridge never accepts images.");link("Open Take It Down","https://takeitdown.ncmec.org/");link("Read Take It Down eligibility & FAQ","https://takeitdown.ncmec.org/faq/");link("Report child exploitation to NCMEC","https://report.cybertip.org/");add("For images taken when you were 18 or older, Take It Down points to StopNCII. Read that service’s eligibility and privacy information before continuing.");link("Open StopNCII information","https://stopncii.org/");break;
        case "safety":
            route("Calculator disguise & getting started","intro");title("Your device may leave traces.");add("Someone with access to this phone, accounts or network may see installed apps, activity, calls or browsing. Safe Bridge cannot detect monitoring or make a compromised device safe.");
            note("If using this phone could put you at risk, consider a device and account the other person cannot access, and support from a trusted advocate. Changing settings or deleting history can be noticed. Choose what is safest in your situation.");
            add("Safe Bridge itself has no accounts, analytics, location tracking, image uploads, notifications or saved notes. This does not describe the privacy practices of external websites, phone apps or the operating system. Guidance works offline. External websites and phone calls need their usual connectivity and may keep their own records.");add("Close removes this app’s task from recent apps, but does not uninstall it, clear browser/call history or erase device records. Uninstalling may still leave download history, backups or synced account records. Calculator mode changes the visible screen and launcher shortcut, but cannot make the app invisible. Settings/app info, installed-package records and launcher caches may still reveal Safe Bridge. Calculator mode stays selected after reopening; calculations are not saved.");link("Read NNEDV guidance on safer app use","https://www.techsafety.org/choosingapps");link("Read hotline digital safety warning","https://humantraffickinghotline.org/en/chat");break;
        default:
            title("An early, independent preview.");add("Safe Bridge 0.5 • U.S.-focused\nA lightweight guide to existing services. Not affiliated with or endorsed by NCMEC or the National Human Trafficking Hotline.");note("Not for public deployment yet. Survivor, child-safety, accessibility, privacy and legal review are still needed. No live support, rescue service, AI advice, report submission or case tracking is provided.");add("Resource links checked November 14, 2026. Services and policies can change. Outside the U.S., seek appropriate local emergency and support services; this preview does not provide a verified international directory.");link("National Human Trafficking Hotline","https://humantraffickinghotline.org/en/get-help");link("NCMEC CyberTipline","https://www.missingkids.org/gethelpnow/cybertipline");link("NCMEC Take It Down","https://takeitdown.ncmec.org/");break;
        }
        deck.setSource(page);
    }
    @Override public void onBackPressed(){if(section.equals("calculator")){finishAndRemoveTask();return;}if(!section.equals("home"))show("home");else super.onBackPressed();}
}
