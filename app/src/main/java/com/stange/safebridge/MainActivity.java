package com.stange.safebridge;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Static, permission-free guidance. No network, storage, analytics or reporting backend. */
public class MainActivity extends Activity {
    private final int ink=Color.rgb(27,52,55), teal=Color.rgb(20,125,128);
    private static final String HOTLINE_POLICY="Your name and identifying details are optional. The hotline generally requires explicit consent to disclose identifying information, but reports suspected abuse of a minor, potential harm to you or others, and situations legally requiring a report. Read its policy before sharing.";
    private static final String CYBERTIP_POLICY="If you submit a CyberTipline report, NCMEC analysts process it and make it available to the appropriate law enforcement agency. Opening this page does not submit a report. You decide whether to complete and submit the official form.";
    private LinearLayout page; private String section="home";
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(243,247,246));getWindow().setNavigationBarColor(Color.rgb(243,247,246));show("home");}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(5),0,dp(8));return t;}
    private void add(String s){page.addView(text(s,16,false));}
    private void title(String s){page.addView(text(s,28,true));}
    private GradientDrawable box(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(18));return g;}
    private void note(String s){TextView t=text(s,14,false);t.setPadding(dp(16),dp(12),dp(16),dp(12));t.setBackground(box(Color.rgb(231,240,237)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(14));page.addView(t,p);}
    private void button(String s,Runnable action,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(20),dp(14),dp(20),dp(14));b.setMinHeight(dp(56));b.setTextColor(primary?Color.WHITE:ink);b.setBackground(box(primary?teal:Color.WHITE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));page.addView(b,p);b.setOnClickListener(v->action.run());}
    private void route(String label,String dest){button(label,()->show(dest),false);}
    private void link(String label,String url){button(label,()->external(label,url),true);}
    private void external(String label,String url){
        boolean dial=url.startsWith("tel:");
        String disclosure="";
        if(url.equals("tel:18883737888") || url.startsWith("https://humantraffickinghotline.org/")) disclosure=HOTLINE_POLICY+"\n\n";
        if(url.startsWith("https://report.cybertip.org/") || url.startsWith("https://www.missingkids.org/gethelpnow/cybertipline")) disclosure=CYBERTIP_POLICY+"\n\n";
        new AlertDialog.Builder(this).setTitle(dial?"Open phone dialer?":"Leave Safe Bridge?")
        .setMessage(disclosure+(dial?"The dialer opens with this number. You choose whether to place the call. Calls may appear in phone or carrier records.\n\n":"This opens your browser. The website has its own privacy practices. Browser history, device monitoring or network records may reveal activity. No report is sent by this app.\n\n")+url)
        .setNegativeButton("Cancel",null).setPositiveButton(dial?"Open dialer":"Open website",(d,w)->{
            try{startActivity(new Intent(dial?Intent.ACTION_DIAL:Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("No compatible app found").setMessage("Use another safe device or compatible browser/phone app. Address or number:\n"+url).setPositiveButton("Close",null).show();}
        }).show();
    }
    private void show(String name){section=name;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(243,247,246));page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(18),dp(22),dp(28));scroll.addView(page);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text("SAFE BRIDGE",13,true);brand.setLetterSpacing(.14f);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));Button close=new Button(this);close.setText("Close");close.setAllCaps(false);close.setOnClickListener(v->finishAndRemoveTask());top.addView(close);page.addView(top);
        if(!name.equals("home"))button("‹  Back to start",()->show("home"),false);
        switch(name){
        case "home":
            title("A safer next step.");add("Support, information and ways to reach trained people. You choose what to open.");
            note("U.S. RESOURCE PROTOTYPE • Not an emergency or monitored service. No one receives messages here. In immediate danger in the U.S., call 911 if safe to do so.");
            route("I need help  →","help");route("I’m worried about a child  →","child");route("Someone has an image of me  →","images");route("Is this device safe to use?  →","safety");
            link("Open emergency dialer • 911","tel:911");
            note("Installed apps, browser history and call records can be discovered. Close does not erase traces or hide this app. Consider another device if yours may be monitored.");route("About this preview & sources","about");break;
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
            title("Your device may leave traces.");add("Someone with access to this phone, accounts or network may see installed apps, activity, calls or browsing. Safe Bridge cannot detect monitoring or make a compromised device safe.");
            note("If using this phone could put you at risk, consider a device and account the other person cannot access, and support from a trusted advocate. Changing settings or deleting history can be noticed. Choose what is safest in your situation.");
            add("Safe Bridge itself has no accounts, analytics, location tracking, image uploads, notifications or saved notes. This does not describe the privacy practices of external websites, phone apps or the operating system. Guidance works offline. External websites and phone calls need their usual connectivity and may keep their own records.");add("Close removes this app’s task from recent apps, but does not uninstall it, clear browser/call history or erase device records. Uninstalling may still leave download history, backups or synced account records. It is not a disguised or invisible app.");link("Read NNEDV guidance on safer app use","https://www.techsafety.org/choosingapps");link("Read hotline digital safety warning","https://humantraffickinghotline.org/en/chat");break;
        default:
            title("An early, independent preview.");add("Safe Bridge 0.1 • U.S.-focused\nA lightweight guide to existing services. Not affiliated with or endorsed by NCMEC or the National Human Trafficking Hotline.");note("Not for public deployment yet. Survivor, child-safety, accessibility, privacy and legal review are still needed. No live support, rescue service, AI advice, report submission or case tracking is provided.");add("Resource links checked November 14, 2026. Services and policies can change. Outside the U.S., seek appropriate local emergency and support services; this preview does not provide a verified international directory.");link("National Human Trafficking Hotline","https://humantraffickinghotline.org/en/get-help");link("NCMEC CyberTipline","https://www.missingkids.org/gethelpnow/cybertipline");link("NCMEC Take It Down","https://takeitdown.ncmec.org/");break;
        }
    }
    @Override public void onBackPressed(){if(!section.equals("home"))show("home");else super.onBackPressed();}
}
