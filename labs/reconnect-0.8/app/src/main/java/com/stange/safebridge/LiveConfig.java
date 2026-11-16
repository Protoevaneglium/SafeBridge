package com.stange.safebridge;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Private lab configuration. No production endpoint has been approved or deployed. */
public final class LiveConfig {
    /** Configure only after a separately reviewed HTTPS server deployment; blank disables sharing. */
    public static final String ENDPOINT="";
    private LiveConfig(){}
    public static boolean configured(){return !endpoint().isEmpty();}
    public static String endpoint(){
        if(ENDPOINT.trim().isEmpty())return "";
        try{return checkedEndpoint(ENDPOINT);}catch(IOException invalid){return "";}
    }
    /** HTTPS only; no credential-bearing URLs, queries, fragments, or test/TLS bypass. */
    public static String checkedEndpoint(String endpoint)throws IOException{
        try{
            if(endpoint==null || endpoint.length()>2048 || !endpoint.equals(endpoint.trim()))throw new IOException("Invalid HTTPS server configuration.");
            URI uri=new URI(endpoint);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getHost().isEmpty() ||
                    uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null ||
                    (uri.getPort()!=-1 && (uri.getPort()<1 || uri.getPort()>65535)))throw new IOException("A valid HTTPS server is required.");
            String path=uri.getRawPath();
            if(path!=null && !path.isEmpty() && !path.equals("/"))throw new IOException("The HTTPS server must be an origin without a path.");
            String host=uri.getHost();
            if("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host))throw new IOException("Loopback is not a production HTTPS endpoint.");
            String value=uri.toASCIIString();
            while(value.endsWith("/"))value=value.substring(0,value.length()-1);
            return value;
        }catch(URISyntaxException invalid){throw new IOException("Invalid HTTPS server configuration.");}
    }
}
