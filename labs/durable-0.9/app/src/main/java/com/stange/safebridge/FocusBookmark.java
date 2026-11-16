package com.stange.safebridge;

import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

/** Best-effort, memory-only focus identity across an automatic feature refresh. */
final class FocusBookmark {
    private final String keyboard,accessibility;
    private final int keyboardOccurrence,accessibilityOccurrence;
    private FocusBookmark(View root){
        View k=focused(root,false),a=focused(root,true);
        keyboard=key(k);accessibility=key(a);
        keyboardOccurrence=occurrence(root,k,keyboard,new int[]{0});
        accessibilityOccurrence=occurrence(root,a,accessibility,new int[]{0});
    }
    static FocusBookmark capture(View root){return new FocusBookmark(root);}
    void restore(View root){
        View k=find(root,keyboard,keyboardOccurrence,new int[]{0});if(k!=null && k.isEnabled())k.requestFocus();
        View a=find(root,accessibility,accessibilityOccurrence,new int[]{0});if(a!=null)a.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null);
    }
    private static View focused(View view,boolean accessibility){
        if(view==null || !view.isShown())return null;
        if(accessibility?view.isAccessibilityFocused():view.isFocused())return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=focused(group.getChildAt(i),accessibility);if(child!=null)return child;}}
        return null;
    }
    private static String key(View view){
        if(view==null)return null;
        Object tag=view.getTag();if(tag instanceof String && ((String)tag).startsWith("focus:"))return (String)tag;
        CharSequence description=view.getContentDescription();
        if(description!=null && description.length()>0)return "description:"+description;
        if(view instanceof TextView){String text=((TextView)view).getText().toString();int end=text.indexOf('\n');if(end>=0)text=text.substring(0,end);if(text.length()>96)text=text.substring(0,96);return view.getClass().getName()+":"+text;}
        return null;
    }
    private static int occurrence(View view,View target,String identity,int[] count){
        if(identity==null || view==null || !view.isShown())return -1;
        if(identity.equals(key(view))){if(view==target)return count[0];count[0]++;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){int result=occurrence(group.getChildAt(i),target,identity,count);if(result>=0)return result;}}
        return -1;
    }
    private static View find(View view,String identity,int wanted,int[] count){
        if(identity==null || wanted<0 || view==null || !view.isShown())return null;
        if(identity.equals(key(view)) && count[0]++==wanted)return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),identity,wanted,count);if(result!=null)return result;}}
        return null;
    }
}
