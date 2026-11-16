package com.stange.safebridge;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Measured, non-scrolling pages. Reading anchors survive a same-instance resize. */
final class PageDeck extends LinearLayout {
    private final LinearLayout content;
    private final Button previous, next;
    private final TextView position;
    private final ArrayList<View> originals=new ArrayList<>();
    private final ArrayList<ArrayList<Piece>> sheets=new ArrayList<>();
    private final IdentityHashMap<ViewGroup,ArrayList<View>> rows=new IdentityHashMap<>();
    private final IdentityHashMap<View,ViewGroup.LayoutParams> originalParams=new IdentityHashMap<>();
    interface PageListener {void changed(boolean last);}
    PageListener onPageChanged;
    private int current=0, pendingPage=-1, measuredWidth=-1, measuredHeight=-1;
    private boolean queued=false;
    private int[] pendingAnchor,lastReadingAnchor;
    private static final class Piece {
        final View view;final int source,child,start,end;
        Piece(View v,int s,int c,int a,int b){view=v;source=s;child=c;start=a;end=b;}
        boolean contains(Piece anchor){return source==anchor.source && (child<0 || child==anchor.child || (anchor.child<0 && child==0)) && start<=anchor.start && end>anchor.start;}
    }
    PageDeck(Context context){super(context);setOrientation(VERTICAL);setPadding(dp(16),0,dp(16),0);
        content=new LinearLayout(context);content.setOrientation(VERTICAL);
        addView(content,new LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(context);navigation.setGravity(android.view.Gravity.CENTER_VERTICAL);navigation.setPadding(0,dp(4),0,dp(4));
        previous=button("‹");previous.setContentDescription("Previous page");next=button("›");next.setContentDescription("Next page");
        position=new TextView(context);position.setGravity(17);position.setTextColor(0xff17364c);position.setTextSize(14);position.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        navigation.addView(previous,new LayoutParams(-2,-2));navigation.addView(position,new LayoutParams(0,-2,1));navigation.addView(next,new LayoutParams(-2,-2));addView(navigation);
        previous.setOnClickListener(v->previousPage());next.setOnClickListener(v->{if(current+1<sheets.size()){current++;render(true);}});
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=measuredWidth || b-t!=measuredHeight){measuredWidth=r-l;measuredHeight=b-t;if(onPageChanged!=null)onPageChanged.changed(false);schedule();}});
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable pill(int color,int stroke){GradientDrawable p=new GradientDrawable();p.setColor(color);p.setCornerRadius(dp(24));p.setStroke(dp(stroke),stroke>1?0xff11589a:0xffffffff);return p;}
    private Button button(String title){Button b=new Button(getContext());b.setText(title);b.setAllCaps(false);b.setTextSize(20);b.setMinHeight(dp(48));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setTextColor(0xff17364c);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},pill(0xffbedcf5,3));states.addState(new int[]{android.R.attr.state_focused},pill(0xfff1f8ff,3));states.addState(new int[]{},pill(0xd9ffffff,1));b.setBackground(states);b.setPadding(dp(14),dp(6),dp(14),dp(6));return b;}
    int getPageIndex(){return pendingPage>=0?pendingPage:current;}
    void restorePageIndex(int value){pendingPage=Math.max(0,value);schedule();}
    int[] getReadingAnchor(){
        if(pendingAnchor!=null)return pendingAnchor.clone();
        if(!sheets.isEmpty()){Piece first=sheets.get(Math.min(current,sheets.size()-1)).get(0);return new int[]{first.source,first.child,first.start};}
        return lastReadingAnchor==null?null:lastReadingAnchor.clone();
    }
    void restoreReadingAnchor(int[] value){pendingAnchor=value!=null && value.length==3 && value[0]>=0 && value[1]>=-1 && value[2]>=0?value.clone():null;schedule();}
    boolean previousPage(){
        if(sheets.isEmpty()){if(pendingAnchor!=null){schedule();return true;}if(pendingPage>0){pendingPage--;schedule();return true;}return false;}
        if(current<=0)return false;current--;render(true);return true;
    }
    private void schedule(){if(queued)return;queued=true;post(()->{queued=false;paginate();});}
    void setSource(LinearLayout source){
        content.removeAllViews();originals.clear();sheets.clear();rows.clear();originalParams.clear();current=0;pendingPage=-1;pendingAnchor=null;lastReadingAnchor=null;
        while(source.getChildCount()>0){View v=source.getChildAt(0);source.removeView(v);originals.add(v);originalParams.put(v,v.getLayoutParams());
            if(v instanceof LinearLayout && !v.isClickable() && ((LinearLayout)v).getOrientation()==HORIZONTAL){ViewGroup row=(ViewGroup)v;ArrayList<View> children=new ArrayList<>();for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);children.add(child);originalParams.put(child,child.getLayoutParams());}rows.put(row,children);}
        }
        if(onPageChanged!=null)onPageChanged.changed(false);schedule();
    }
    private void detach(View view){if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);}
    private void restoreRows(){for(Map.Entry<ViewGroup,ArrayList<View>> entry:rows.entrySet()){ViewGroup row=entry.getKey();row.removeAllViews();for(View child:entry.getValue()){detach(child);row.addView(child,originalParams.get(child));}}}
    private int height(View v,int width){int margin=0,horizontal=0;if(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();margin=p.topMargin+p.bottomMargin;horizontal=p.leftMargin+p.rightMargin;}v.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-horizontal),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));return v.getMeasuredHeight()+margin;}
    private TextView copyText(TextView original,String value){
        TextView t=new TextView(getContext());t.setTag(original.getTag());t.setText(value);t.setTextColor(original.getTextColors());t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,original.getTextSize());t.setTypeface(original.getTypeface());t.setGravity(original.getGravity());t.setTextDirection(original.getTextDirection());t.setLineSpacing(original.getLineSpacingExtra(),original.getLineSpacingMultiplier());t.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());
        if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(original.isAccessibilityHeading());
        if(original.getBackground()!=null && original.getBackground().getConstantState()!=null)t.setBackground(original.getBackground().getConstantState().newDrawable().mutate());
        LayoutParams lp=new LayoutParams(-1,-2);if(original.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)original.getLayoutParams();lp.setMargins(p.leftMargin,p.topMargin,p.rightMargin,p.bottomMargin);}t.setLayoutParams(lp);return t;
    }
    private void unavailable(){content.removeAllViews();sheets.clear();previous.setEnabled(false);next.setEnabled(false);position.setText("Expand window");position.setContentDescription("Window too small for readable content and controls. Expand the window or change orientation.");if(onPageChanged!=null)onPageChanged.changed(false);}
    private boolean expand(View view,int source,int child,int width,int available,ArrayList<Piece> result){
        if(height(view,width)<=available){result.add(new Piece(view,source,child,0,Integer.MAX_VALUE));return true;}
        if(view instanceof ViewGroup && view.isClickable()){
            CharSequence label=view.getContentDescription();if(label==null || label.length()==0)return false;
            Button replacement=button(label.toString());replacement.setTextSize(16);replacement.setOnClickListener(v->view.performClick());
            // An action remains a complete action. Never split its label into clickable fragments.
            if(height(replacement,width)>available)return false;result.add(new Piece(replacement,source,child,0,Integer.MAX_VALUE));return true;
        }
        if(!(view instanceof TextView) || view.isClickable())return false;
        TextView text=(TextView)view;String value=text.getText().toString();if(height(copyText(text,"M"),width)>available)return false;
        int start=0;while(start<value.length()){
            int remainingPoints=value.codePointCount(start,value.length()),low=1,high=remainingPoints,best=0;
            while(low<=high){int middle=(low+high)/2;int end=value.offsetByCodePoints(start,middle);if(height(copyText(text,value.substring(start,end)),width)<=available){best=end;low=middle+1;}else high=middle-1;}
            if(best<=start)return false;
            if(best<value.length()){int space=-1;for(int i=best-1;i>start;i--){if(Character.isWhitespace(value.charAt(i))){space=i;break;}}if(space>start)best=space+1;}
            result.add(new Piece(copyText(text,value.substring(start,best)),source,child,start,best));start=best;
        }return true;
    }
    private void paginate(){
        int width=content.getWidth(),available=content.getHeight();if(width<=0||available<=0){unavailable();return;}
        int[] reading=getReadingAnchor();
        Piece anchor=reading==null?null:new Piece(null,reading[0],reading[1],reading[2],Integer.MAX_VALUE);
        content.removeAllViews();restoreRows();sheets.clear();ArrayList<Piece> expanded=new ArrayList<>();
        for(int source=0;source<originals.size();source++){View view=originals.get(source);detach(view);view.setLayoutParams(originalParams.get(view));
            if(rows.containsKey(view) && height(view,width)>available){ArrayList<View> children=rows.get(view);for(int child=0;child<children.size();child++){View item=children.get(child);detach(item);item.setLayoutParams(new LayoutParams(-1,-2));if(!expand(item,source,child,width,available,expanded)){unavailable();return;}}}
            else if(!expand(view,source,-1,width,available,expanded)){unavailable();return;}
        }
        ArrayList<Piece> sheet=new ArrayList<>();int used=0;for(Piece item:expanded){int needed=height(item.view,width);if(!sheet.isEmpty() && used+needed>available){sheets.add(sheet);sheet=new ArrayList<>();used=0;}sheet.add(item);used+=needed;}if(!sheet.isEmpty())sheets.add(sheet);
        if(sheets.isEmpty()){unavailable();return;}
        boolean restored=false;
        if(pendingAnchor!=null){restored=restoreAnchor(pendingAnchor);pendingAnchor=null;if(restored)pendingPage=-1;}
        if(!restored && pendingPage>=0){current=Math.min(pendingPage,sheets.size()-1);pendingPage=-1;restored=true;}
        if(!restored && anchor!=null)restoreAnchor(new int[]{anchor.source,anchor.child,anchor.start});
        current=Math.min(current,sheets.size()-1);render(false);
    }
    private boolean restoreAnchor(int[] value){
        if(value[0]>=originals.size())return false;
        View source=originals.get(value[0]);if(value[1]>=0 && (!rows.containsKey(source) || value[1]>=rows.get(source).size()))return false;
        Piece anchor=new Piece(null,value[0],value[1],value[2],Integer.MAX_VALUE);
        for(int i=0;i<sheets.size();i++){for(Piece piece:sheets.get(i)){if(piece.contains(anchor)){current=i;return true;}}}return false;
    }
    private void render(boolean userNavigation){
        content.removeAllViews();if(sheets.isEmpty())return;Piece firstPiece=sheets.get(current).get(0);lastReadingAnchor=new int[]{firstPiece.source,firstPiece.child,firstPiece.start};for(Piece item:sheets.get(current)){detach(item.view);content.addView(item.view);}
        if(onPageChanged!=null)onPageChanged.changed(current+1==sheets.size());previous.setEnabled(current>0);next.setEnabled(current+1<sheets.size());position.setText((current+1)+" / "+sheets.size());position.setContentDescription("Page "+(current+1)+" of "+sheets.size());
        if(userNavigation && content.getChildCount()>0){View first=content.getChildAt(0);int page=current;content.post(()->{if(current==page && first.getParent()==content){first.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null);first.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);}});}
    }
}
