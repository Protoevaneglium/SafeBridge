package com.stange.safebridge;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

/** Measured pages, rather than a scrolling guidance panel. No text is discarded. */
final class PageDeck extends LinearLayout {
    private final LinearLayout content, navigation;
    private final Button previous, next;
    private final TextView position;
    private final ArrayList<View> originals=new ArrayList<>();
    private final ArrayList<ArrayList<View>> sheets=new ArrayList<>();
    interface PageListener {void changed(boolean last);}
    PageListener onPageChanged;
    private int current=0, measuredWidth=-1, measuredHeight=-1;
    PageDeck(Context context){super(context);setOrientation(VERTICAL);setPadding(dp(18),0,dp(18),0);
        content=new LinearLayout(context);content.setOrientation(VERTICAL);
        addView(content,new LayoutParams(-1,0,1));
        navigation=new LinearLayout(context);navigation.setGravity(android.view.Gravity.CENTER_VERTICAL);navigation.setPadding(0,dp(4),0,dp(4));
        previous=button("‹ Back");next=button("Next ›");position=new TextView(context);position.setGravity(17);position.setTextColor(0xff17364c);position.setTextSize(13);
        navigation.addView(previous,new LayoutParams(-2,-2));navigation.addView(position,new LayoutParams(0,-2,1));navigation.addView(next,new LayoutParams(-2,-2));addView(navigation);
        previous.setOnClickListener(v->{if(current>0){current--;render();}});next.setOnClickListener(v->{if(current+1<sheets.size()){current++;render();}});
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=measuredWidth || b-t!=measuredHeight){measuredWidth=r-l;measuredHeight=b-t;post(this::paginate);}});
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String title){Button b=new Button(getContext());b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumWidth(dp(80));b.setTextColor(0xff17364c);GradientDrawable pill=new GradientDrawable();pill.setColor(0xd9ffffff);pill.setCornerRadius(dp(24));pill.setStroke(dp(1),0xffffffff);b.setBackground(pill);b.setPadding(dp(14),dp(8),dp(14),dp(8));return b;}
    void setSource(LinearLayout source){originals.clear();while(source.getChildCount()>0){View v=source.getChildAt(0);source.removeView(v);originals.add(v);}current=0;post(this::paginate);}
    private int height(View v,int width){v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int margin=0;if(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();margin=p.topMargin+p.bottomMargin;}return v.getMeasuredHeight()+margin;}
    private TextView copyText(TextView original,String value){TextView t=original instanceof Button?new Button(getContext()):new TextView(getContext());if(t instanceof Button){((Button)t).setAllCaps(false);t.setOnClickListener(v->original.performClick());t.setMinHeight(dp(48));}t.setText(value);t.setTextColor(original.getTextColors());t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,original.getTextSize());t.setTypeface(original.getTypeface());t.setLineSpacing(original.getLineSpacingExtra(),original.getLineSpacingMultiplier());t.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());if(original.getBackground()!=null && original.getBackground().getConstantState()!=null)t.setBackground(original.getBackground().getConstantState().newDrawable().mutate());t.setLayoutParams(new LayoutParams(-1,-2));return t;}
    private void paginate(){int w=content.getWidth(),h=content.getHeight();if(w<=0||h<=0){if(onPageChanged!=null)onPageChanged.changed(false);return;}if(originals.isEmpty())return;content.removeAllViews();sheets.clear();ArrayList<View> expanded=new ArrayList<>();
        ArrayList<View> candidates=new ArrayList<>();for(View v:originals){if(height(v,w)>h && v instanceof ViewGroup && v.isClickable()){Button replacement=button(v.getContentDescription().toString());replacement.setOnClickListener(clicked->v.performClick());candidates.add(replacement);}else candidates.add(v);}
        for(View original:candidates){if(original.getParent() instanceof ViewGroup)((ViewGroup)original.getParent()).removeView(original);
            if(original instanceof TextView && height(original,w)>h){TextView t=(TextView)original;if(height(copyText(t,"M"),w)>h){content.removeAllViews();sheets.clear();previous.setEnabled(false);next.setEnabled(false);position.setText("Expand window");position.setContentDescription("Window too small. Expand it or use portrait orientation to read this page.");if(onPageChanged!=null)onPageChanged.changed(false);return;}String remaining=t.getText().toString();while(!remaining.isEmpty()){int low=1,high=remaining.length(),best=1;while(low<=high){int middle=(low+high)/2;TextView part=copyText(t,remaining.substring(0,middle));if(height(part,w)<=h){best=middle;low=middle+1;}else high=middle-1;}if(best<remaining.length()){int space=remaining.lastIndexOf(' ',best-1);if(space>0)best=space+1;}expanded.add(copyText(t,remaining.substring(0,best)));remaining=remaining.substring(best);}}else expanded.add(original);
        }
        ArrayList<View> sheet=new ArrayList<>();int used=0;for(View v:expanded){int need=height(v,w);if(!sheet.isEmpty()&&used+need>h){sheets.add(sheet);sheet=new ArrayList<>();used=0;}sheet.add(v);used+=need;}if(!sheet.isEmpty())sheets.add(sheet);current=Math.min(current,Math.max(0,sheets.size()-1));render();
    }
    private void render(){content.removeAllViews();if(sheets.isEmpty())return;for(View v:sheets.get(current)){if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);content.addView(v);}if(onPageChanged!=null)onPageChanged.changed(current+1==sheets.size());previous.setEnabled(current>0);next.setEnabled(current+1<sheets.size());position.setText((current+1)+" / "+sheets.size());position.setContentDescription("Page "+(current+1)+" of "+sheets.size());content.announceForAccessibility(position.getContentDescription());}
}
