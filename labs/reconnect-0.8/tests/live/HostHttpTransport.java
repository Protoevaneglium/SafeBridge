package com.stange.safebridge.live.test;

import com.stange.safebridge.live.LiveCoordinator;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** HOST TEST ONLY: explicit loopback transport and bounded JSON codec; not an Android transport. */
public final class HostHttpTransport implements LiveCoordinator.Transport {
    public Map<String,Object> exchange(String method,String endpoint,String path,String token,
            String generation,String managementReceipt,Map<String,Object> body) throws IOException {
        URI uri=URI.create(endpoint+path);
        if(!"http".equals(uri.getScheme())||!"127.0.0.1".equals(uri.getHost()))throw new IOException("Host test transport is loopback-only");
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(3000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        if(generation!=null)connection.setRequestProperty("X-SafeBridge-Generation",generation);
        if(managementReceipt!=null)connection.setRequestProperty("X-SafeBridge-Management-Receipt",managementReceipt);
        try{
            if(body!=null){byte[] data=Json.encode(body).getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(data.length);
                try(OutputStream out=connection.getOutputStream()){out.write(data);}}
            int code=connection.getResponseCode();InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();
            if(input==null)throw new IOException("Empty host test response");
            byte[] data;try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>65536)throw new IOException("Host test response too large");out.write(buffer,0,n);}data=out.toByteArray();}
            Map<String,Object> result=Json.object(new String(data,StandardCharsets.UTF_8));
            if(code<200||code>=300)throw new LiveCoordinator.HttpFailure(code,result.get("error") instanceof String?(String)result.get("error"):"request_failed");
            return result;
        }finally{connection.disconnect();}
    }
    static final class Json {
        final String text;int index;
        Json(String text){this.text=text;}
        static Map<String,Object> object(String text) throws IOException {Json parser=new Json(text);Object v=parser.value(0);parser.space();if(!(v instanceof Map)||parser.index!=text.length())throw bad();return (Map<String,Object>)v;}
        static String encode(Object value) throws IOException {
            if(value==null)return "null";
            if(value instanceof String){StringBuilder out=new StringBuilder("\"");for(char c:((String)value).toCharArray()){
                if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<' ')out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
                return out.append('"').toString();}
            if(value instanceof Boolean)return value.toString();
            if(value instanceof Number){if(!Double.isFinite(((Number)value).doubleValue()))throw bad();return value.toString();}
            if(value instanceof Map){StringJoiner join=new StringJoiner(",","{","}");for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet()){
                if(!(e.getKey() instanceof String))throw bad();join.add(encode(e.getKey())+":"+encode(e.getValue()));}return join.toString();}
            throw bad();
        }
        Object value(int depth) throws IOException {if(depth>16)throw bad();space();if(index>=text.length())throw bad();char c=text.charAt(index);
            if(c=='{'){index++;Map<String,Object> map=new HashMap<>();space();if(take('}'))return map;do{space();String key=string();space();if(!take(':')||map.containsKey(key))throw bad();map.put(key,value(depth+1));space();if(take('}'))return map;}while(take(','));throw bad();}
            if(c=='"')return string();if(text.startsWith("true",index)){index+=4;return true;}if(text.startsWith("false",index)){index+=5;return false;}if(text.startsWith("null",index)){index+=4;return null;}
            int start=index;while(index<text.length()&&"-+0123456789.eE".indexOf(text.charAt(index))>=0)index++;
            String n=text.substring(start,index);if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw bad();
            try{if(n.indexOf('.')<0&&n.indexOf('e')<0&&n.indexOf('E')<0)return Long.parseLong(n);double d=Double.parseDouble(n);if(!Double.isFinite(d))throw bad();return d;}catch(NumberFormatException e){throw bad();}}
        String string() throws IOException {if(!take('"'))throw bad();StringBuilder s=new StringBuilder();while(index<text.length()){
            char c=text.charAt(index++);if(c=='"')return s.toString();if(c<' ')throw bad();if(c!='\\'){s.append(c);continue;}if(index>=text.length())throw bad();char e=text.charAt(index++);
            switch(e){case '"':case '\\':case '/':s.append(e);break;case 'b':s.append('\b');break;case 'f':s.append('\f');break;case 'n':s.append('\n');break;case 'r':s.append('\r');break;case 't':s.append('\t');break;
                case 'u':if(index+4>text.length())throw bad();try{s.append((char)Integer.parseInt(text.substring(index,index+4),16));}catch(NumberFormatException ex){throw bad();}index+=4;break;default:throw bad();}}
            throw bad();}
        void space(){while(index<text.length()&&Character.isWhitespace(text.charAt(index)))index++;}
        boolean take(char c){if(index<text.length()&&text.charAt(index)==c){index++;return true;}return false;}
        static IOException bad(){return new IOException("Invalid host test JSON");}
    }
}
