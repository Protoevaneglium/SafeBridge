package com.stange.safebridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/** Lexical host checks only: no DNS, sockets or network probing. */
public final class EndpointOriginRules {
    private EndpointOriginRules(){}
    public static void requireRemoteHost(String value)throws IOException{
        if(value==null)throw invalid();String host=value.toLowerCase(Locale.ROOT);
        if(host.startsWith("[") && host.endsWith("]"))host=host.substring(1,host.length()-1);
        while(host.endsWith("."))host=host.substring(0,host.length()-1);
        if(host.isEmpty() || host.equals("localhost") || host.endsWith(".localhost"))throw invalid();
        if(host.contains(":")){
            int zone=host.indexOf('%');if(zone>=0)host=host.substring(0,zone);
            int[] words=ipv6(host);if(words==null)throw invalid();
            boolean firstFive=true,firstSix=true,firstSeven=true,allZero=true;
            for(int i=0;i<8;i++){if(words[i]!=0){allZero=false;if(i<5)firstFive=false;if(i<6)firstSix=false;if(i<7)firstSeven=false;}}
            if(allZero || (firstSeven && words[7]==1) || (firstFive && words[5]==0xffff) || (firstSix && (words[6]>>>8)==127))throw invalid();
        }else if(host.matches("[0-9.]+")){
            String[] parts=host.split("\\.",-1);if(parts.length!=4)throw invalid();int[] bytes=new int[4];
            for(int i=0;i<4;i++){if(!parts[i].matches("0|[1-9][0-9]{0,2}"))throw invalid();bytes[i]=Integer.parseInt(parts[i]);if(bytes[i]>255)throw invalid();}
            if(bytes[0]==127 || (bytes[0]==0 && bytes[1]==0 && bytes[2]==0 && bytes[3]==0))throw invalid();
        }
    }
    /** Canonical URL-origin host spelling, using numeric parsing only. */
    public static String canonicalHost(String value)throws IOException{
        requireRemoteHost(value);String host=value.toLowerCase(Locale.ROOT);
        if(host.startsWith("[") && host.endsWith("]"))host=host.substring(1,host.length()-1);
        if(host.contains("%"))throw new IOException("Scoped IPv6 origins are unsupported; use a canonical DNS origin.");
        if(!host.contains(":"))return host;
        int[] words=ipv6(host);if(words==null)throw invalid();int best=-1,bestLength=1;
        for(int i=0;i<8;){if(words[i]!=0){i++;continue;}int begin=i;while(i<8 && words[i]==0)i++;if(i-begin>bestLength){best=begin;bestLength=i-begin;}}
        StringBuilder result=new StringBuilder();
        for(int i=0;i<8;){if(i==best){result.append("::");i+=bestLength;continue;}if(result.length()>0 && result.charAt(result.length()-1)!=':')result.append(':');result.append(Integer.toHexString(words[i++]));}
        return result.toString();
    }
    private static int[] ipv6(String host){
        try{
            int compressed=host.indexOf("::");if(compressed>=0 && host.indexOf("::",compressed+2)>=0)return null;
            ArrayList<Integer> left=parts(compressed<0?host:host.substring(0,compressed));
            ArrayList<Integer> right=compressed<0?new ArrayList<>():parts(host.substring(compressed+2));
            int missing=8-left.size()-right.size();if((compressed<0 && missing!=0) || (compressed>=0 && missing<1))return null;
            int[] result=new int[8];int index=0;for(int item:left)result[index++]=item;index+=missing;for(int item:right)result[index++]=item;return result;
        }catch(IllegalArgumentException invalid){return null;}
    }
    private static ArrayList<Integer> parts(String side){
        ArrayList<Integer> result=new ArrayList<>();if(side.isEmpty())return result;
        for(String part:side.split(":",-1)){
            if(part.contains(".")){String[] pieces=part.split("\\.",-1);if(pieces.length!=4)throw new IllegalArgumentException();int[] bytes=new int[4];for(int i=0;i<4;i++){if(!pieces[i].matches("[0-9]{1,3}"))throw new IllegalArgumentException();bytes[i]=Integer.parseInt(pieces[i]);if(bytes[i]>255)throw new IllegalArgumentException();}result.add(bytes[0]*256+bytes[1]);result.add(bytes[2]*256+bytes[3]);}
            else{if(!part.matches("[0-9a-f]{1,4}"))throw new IllegalArgumentException();result.add(Integer.parseInt(part,16));}
        }return result;
    }
    private static IOException invalid(){return new IOException("Loopback, unspecified or ambiguous local HTTPS origins are not permitted.");}
}
