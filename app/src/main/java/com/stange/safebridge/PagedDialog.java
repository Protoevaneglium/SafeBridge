package com.stange.safebridge;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** In-app sheet: full disclosures paged before Continue, with Cancel and disguise always available. */
final class PagedDialog {
    static final class Builder {
        private final Activity activity;private String title="",message="",positive,negative,neutral;
        private DialogInterface.OnClickListener yes,no,escape;
        Builder(Activity a){activity=a;}
        Builder setTitle(String s){title=s;return this;} Builder setMessage(String s){message=s;return this;}
        Builder setPositiveButton(String s,DialogInterface.OnClickListener l){positive=s;yes=l;return this;}
        Builder setNegativeButton(String s,DialogInterface.OnClickListener l){negative=s;no=l;return this;}
        Builder setNeutralButton(String s,DialogInterface.OnClickListener l){neutral=s;escape=l;return this;}
        private int dp(int v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
        private Button action(Dialog d,String label,int which,DialogInterface.OnClickListener l){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setTextColor(0xff17364c);GradientDrawable pill=new GradientDrawable();pill.setColor(which==-1?0xffbce8f1:0xddffffff);pill.setCornerRadius(dp(24));pill.setStroke(dp(1),0xffffffff);b.setBackground(pill);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setOnClickListener(v->{d.dismiss();if(l!=null)l.onClick(d,which);});return b;}
        Dialog show(){Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(8),dp(14),dp(8),dp(8));GradientDrawable bg=new GradientDrawable();bg.setColor(0xffeef8fb);bg.setCornerRadius(dp(28));root.setBackground(bg);
            PageDeck deck=new PageDeck(activity);LinearLayout source=new LinearLayout(activity);source.setOrientation(1);
            TextView heading=new TextView(activity);heading.setText(title);heading.setTextSize(23);heading.setTextColor(0xff17364c);heading.setTypeface(null,1);heading.setPadding(0,0,0,dp(14));source.addView(heading);
            TextView body=new TextView(activity);body.setText(message);body.setTextSize(17);body.setTextColor(0xff17364c);body.setLineSpacing(dp(4),1);source.addView(body);
            root.addView(deck,new LinearLayout.LayoutParams(-1,0,1));
            if(positive!=null){Button proceed=action(dialog,positive,-1,yes);proceed.setEnabled(false);deck.onPageChanged=(last)->proceed.setEnabled(last);root.addView(proceed,new LinearLayout.LayoutParams(-1,-2));}
            LinearLayout safe=new LinearLayout(activity);if(negative!=null)safe.addView(action(dialog,negative,-2,no),new LinearLayout.LayoutParams(0,-2,1));if(neutral!=null)safe.addView(action(dialog,neutral,-3,escape),new LinearLayout.LayoutParams(0,-2,1));root.addView(safe);
            dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);dialog.show();Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);android.graphics.Rect bounds=new android.graphics.Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(bounds);w.setLayout(Math.min(bounds.width()-dp(24),dp(520)),Math.max(1,(int)(bounds.height()*.88)));deck.setSource(source);return dialog;
        }
    }
}
