package com.stange.safebridge;

import com.stange.safebridge.live.LiveCoordinator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Platform HTTPS trust/hostname validation; bounded JSON; no redirects or sensitive logging. */
public final class HttpsLiveTransport implements LiveCoordinator.Transport {
    private static final int CONNECT_MS=5000,READ_MS=5000,MAX_BODY=32768,MAX_REQUEST=16384;
    @Override public Map<String,Object> exchange(String method,String endpoint,String path,String token,String generation,String managementReceipt,Map<String,Object> body)throws IOException{
        String base=LiveConfig.checkedEndpoint(endpoint);
        if(path==null || !path.matches("/[A-Za-z0-9_/-]{1,512}") || path.contains("..") || path.startsWith("//"))throw new IOException("Invalid server request path.");
        if(!"GET".equals(method) && !"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method))throw new IOException("Unsupported request method.");
        if(token!=null && !token.matches("[A-Za-z0-9_-]{22,128}"))throw new IOException("Invalid session credential.");
        if(generation!=null && !generation.matches("[A-Za-z0-9_-]{16,128}"))throw new IOException("Invalid server generation.");
        if(managementReceipt!=null && !managementReceipt.matches("[A-Za-z0-9_.-]{16,1024}"))throw new IOException("Invalid management receipt.");
        byte[] request=null;
        if(body!=null){
            try{JSONObject json=new JSONObject();for(Map.Entry<String,Object> item:body.entrySet())json.put(item.getKey(),item.getValue()==null?JSONObject.NULL:item.getValue());request=json.toString().getBytes(StandardCharsets.UTF_8);}catch(JSONException|RuntimeException invalid){throw new IOException("Invalid request body.");}
            if(request.length>MAX_REQUEST)throw new IOException("Request body is too large.");
        }
        final long deadlineNanos=System.nanoTime()+15000000000L;
        HttpsURLConnection connection=network(()->(HttpsURLConnection)new URL(base+path).openConnection());
        // Deliberately use Android's default certificate trust and hostname verifier.
        // There is no trust-all code, custom verifier or insecure test transport here.
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_MS);connection.setReadTimeout(READ_MS);
        connection.setRequestMethod(method);connection.setUseCaches(false);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Cache-Control","no-store");
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        if(generation!=null)connection.setRequestProperty("X-SafeBridge-Generation",generation);
        if(managementReceipt!=null)connection.setRequestProperty("X-SafeBridge-Management-Receipt",managementReceipt);
        try{
            if(request!=null){
                connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(request.length);
                final byte[] payload=request;
                network(()->{try(OutputStream output=connection.getOutputStream()){output.write(payload);}return null;});
            }
            int status=network(()->connection.getResponseCode());
            if(status>=300 && status<400)throw new IOException("Server redirects are not permitted.");
            if(status==408 || status==425 || status==429 || (status>=500 && status<=599))throw new LiveCoordinator.HttpFailure(status,"temporary");
            long length=connection.getContentLengthLong();
            if(length>MAX_BODY)throw new IOException("Server response is too large.");
            String contentType=connection.getContentType();
            if(contentType==null || !contentType.split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new IOException("Server returned an unexpected content type.");
            // HTTP temporary failures remain retryable even when an overloaded server
            // has no JSON error body. Authentication/protocol errors never take this path.
            InputStream stream=status>=400?connection.getErrorStream():network(()->connection.getInputStream());
            byte[] response;
            if(stream==null)response=new byte[0];else try{response=readBounded(stream,deadlineNanos);}finally{try{stream.close();}catch(IOException ignored){}}
            checkJsonDepth(response);
            Map<String,Object> result;
            try{result=objectToMap(new JSONObject(new String(response,StandardCharsets.UTF_8)),0);}
            catch(JSONException invalid){throw new IOException("Server returned invalid JSON.");}
            if(status<200 || status>=300){
                Object code=result.get("error");
                String safeCode=code instanceof String && ((String)code).matches("[a-z0-9_]{1,64}")?(String)code:"http_error";
                throw new LiveCoordinator.HttpFailure(status,safeCode);
            }
            return result;
        }finally{if(request!=null)java.util.Arrays.fill(request,(byte)0);connection.disconnect();}
    }
    private interface NetworkCall<T>{T call()throws IOException;}
    private <T>T network(NetworkCall<T> operation)throws IOException{
        try{return operation.call();}
        catch(IOException failure){
            Throwable cause=failure;
            for(int i=0;cause!=null && i<12;i++,cause=cause.getCause())
                if(cause instanceof SSLException || cause instanceof CertificateException)throw new IOException("TLS security validation failed.");
            throw new LiveCoordinator.ConnectionFailure();
        }
    }
    private void checkJsonDepth(byte[] bytes)throws IOException{
        int depth=0;boolean quoted=false,escaped=false;
        for(byte value:bytes){char c=(char)(value&255);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{' || c=='['){if(++depth>8)throw new IOException("Server JSON is nested too deeply.");}
            else if(c=='}' || c==']'){if(--depth<0)throw new IOException("Invalid server JSON.");}
        }
        if(quoted || escaped || depth!=0)throw new IOException("Invalid server JSON.");
    }
    private byte[] readBounded(InputStream input,long deadlineNanos)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;
        while(true){if(System.nanoTime()>=deadlineNanos)throw new LiveCoordinator.ConnectionFailure();count=network(()->input.read(buffer));if(count==-1)break;if(bytes.size()+count>MAX_BODY)throw new IOException("Server response is too large.");bytes.write(buffer,0,count);}
        return bytes.toByteArray();
    }
    private Map<String,Object> objectToMap(JSONObject json,int depth)throws JSONException,IOException{
        if(depth>8)throw new IOException("Server JSON is nested too deeply.");
        Map<String,Object> result=new LinkedHashMap<>();Iterator<String> keys=json.keys();int count=0;
        while(keys.hasNext()){if(++count>128)throw new IOException("Server JSON has too many fields.");String key=keys.next();result.put(key,value(json.get(key),depth+1));}
        return result;
    }
    private Object value(Object item,int depth)throws JSONException,IOException{
        if(item==JSONObject.NULL)return null;
        if(item instanceof JSONObject)return objectToMap((JSONObject)item,depth);
        if(item instanceof JSONArray){JSONArray array=(JSONArray)item;if(depth>8 || array.length()>128)throw new IOException("Server JSON array is too large.");ArrayList<Object> out=new ArrayList<>();for(int i=0;i<array.length();i++)out.add(value(array.get(i),depth+1));return out;}
        if(item instanceof String || item instanceof Number || item instanceof Boolean)return item;
        throw new IOException("Unexpected server JSON value.");
    }
}
