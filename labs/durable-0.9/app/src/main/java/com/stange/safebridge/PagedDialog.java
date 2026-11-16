package com.stange.safebridge;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** A single in-app disclosure sheet. Back/Cancel never perform the external action. */
final class PagedDialog {
    private static final WeakHashMap<Activity,WeakReference<Dialog>> active=new WeakHashMap<>();
    static boolean isShowing(Activity activity){WeakReference<Dialog> ref=active.get(activity);Dialog dialog=ref==null?null:ref.get();return dialog!=null && dialog.isShowing();}
    static void dismissFor(Activity activity){WeakReference<Dialog> ref=active.remove(activity);Dialog dialog=ref==null?null:ref.get();if(dialog!=null)dialog.dismiss();}
    static final class Builder {
        private final Activity activity;private String title="",message="",positive,negative,neutral;
        private DialogInterface.OnClickListener yes,no,escape;
        private boolean handled=false;private Runnable canceled;
        Builder(Activity a){activity=a;}
        Builder setOnCancel(Runnable action){canceled=action;return this;}
        Builder setTitle(String s){title=s;return this;} Builder setMessage(String s){message=s;return this;}
        Builder setPositiveButton(String s,DialogInterface.OnClickListener l){positive=s;yes=l;return this;}
        Builder setNegativeButton(String s,DialogInterface.OnClickListener l){negative=s;no=l;return this;}
        Builder setNeutralButton(String s,DialogInterface.OnClickListener l){neutral=s;escape=l;return this;}
        private int dp(int v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
        private GradientDrawable pill(int color,int stroke){GradientDrawable p=new GradientDrawable();p.setColor(color);p.setCornerRadius(dp(24));p.setStroke(dp(stroke),stroke>1?0xff11589a:0xffffffff);return p;}
        private Button action(Dialog d,String label,int which,DialogInterface.OnClickListener listener){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setTextColor(0xff17364c);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},pill(0xffbedcf5,3));states.addState(new int[]{android.R.attr.state_focused},pill(0xfff1f8ff,3));states.addState(new int[]{},pill(which==-1?0xffbce8f1:0xddffffff,1));b.setBackground(states);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->{if(handled || !d.isShowing())return;handled=true;b.setEnabled(false);d.dismiss();if(listener!=null)listener.onClick(d,which);});return b;}
        Dialog show(){
            WeakReference<Dialog> ref=active.get(activity);Dialog existing=ref==null?null:ref.get();if(existing!=null && existing.isShowing())return existing;
            Dialog dialog=new Dialog(activity);if(activity.isFinishing() || activity.isDestroyed())return dialog;
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(8),dp(8),dp(8),dp(8));root.setBackground(pill(0xffeef8fb,1));
            PageDeck deck=new PageDeck(activity);LinearLayout source=new LinearLayout(activity);source.setOrientation(1);
            TextView heading=new TextView(activity);heading.setText(title);heading.setTextSize(23);heading.setTextColor(0xff17364c);heading.setTypeface(null,1);heading.setPadding(0,0,0,dp(10));if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);source.addView(heading);
            TextView body=new TextView(activity);body.setText(message);body.setTextSize(17);body.setTextColor(0xff17364c);body.setLineSpacing(dp(3),1);source.addView(body);
            // Escape controls remain outside the paged disclosure and before the forward action.
            LinearLayout safe=new LinearLayout(activity);if(negative!=null)safe.addView(action(dialog,negative,-2,no),new LinearLayout.LayoutParams(0,-2,1));if(neutral!=null)safe.addView(action(dialog,neutral,-3,escape),new LinearLayout.LayoutParams(0,-2,1));if(safe.getChildCount()>0)root.addView(safe);
            root.addView(deck,new LinearLayout.LayoutParams(-1,0,1));
            if(positive!=null){Button proceed=action(dialog,positive,-1,yes);proceed.setEnabled(false);deck.onPageChanged=(last)->{proceed.setEnabled(last && !handled);proceed.setAlpha(last?1f:.5f);proceed.setContentDescription(last?positive:positive+". Read to the last disclosure page first.");};root.addView(proceed,new LinearLayout.LayoutParams(-1,-2));}
            dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{handled=true;if(canceled!=null)canceled.run();});
            View owner=activity.getWindow().getDecorView();
            Runnable resize=()->{if(!dialog.isShowing())return;Rect bounds=new Rect();owner.getWindowVisibleDisplayFrame(bounds);int width=Math.max(1,Math.min(bounds.width()-dp(16),dp(560)));int height=Math.max(1,bounds.height()-dp(16));Window window=dialog.getWindow();if(window.getAttributes().width!=width || window.getAttributes().height!=height)window.setLayout(width,height);};
            View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->resize.run();owner.addOnLayoutChangeListener(layout);
            dialog.setOnDismissListener(d->{owner.removeOnLayoutChangeListener(layout);WeakReference<Dialog> current=active.get(activity);if(current!=null && current.get()==dialog)active.remove(activity);});
            dialog.show();active.put(activity,new WeakReference<>(dialog));Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setTitle(title);resize.run();deck.setSource(source);return dialog;
        }
    }
}
