package com.stange.safebridge;

import java.io.IOException;
import java.util.Base64;

/** Pure validation shared by encrypted prepared setup and its host checks. */
public final class PreparedAccessRules {
    private PreparedAccessRules(){}
    public static String validate(String endpoint,String slotId,String preparedToken,String configuredEndpoint)throws IOException{
        if(configuredEndpoint==null || configuredEndpoint.isEmpty())throw new IOException("Live server is not configured.");
        String expected=LiveConfig.checkedEndpoint(configuredEndpoint),origin=LiveConfig.checkedEndpoint(endpoint);
        if(!expected.equals(origin))throw new IOException("Prepared access belongs to a different server.");
        if(!validSlotId(slotId) || !canonical(preparedToken,32,43))throw new IOException("Invalid prepared-access credentials.");
        return origin;
    }
    public static boolean validSlotId(String slotId){return canonical(slotId,16,22);}
    private static boolean canonical(String value,int bytes,int characters){
        if(value==null || value.length()!=characters || !value.matches("[A-Za-z0-9_-]+"))return false;
        try{byte[] decoded=Base64.getUrlDecoder().decode(value);return decoded.length==bytes && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);}
        catch(IllegalArgumentException invalid){return false;}
    }
}
