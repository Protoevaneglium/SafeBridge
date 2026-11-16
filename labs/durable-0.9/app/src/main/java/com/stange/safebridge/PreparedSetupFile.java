package com.stange.safebridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Bounded, strict, flat setup-file reader. It neither stores keys nor performs network requests. */
public final class PreparedSetupFile {
    public static final int MAX_BYTES=4096;
    public static final class Details {
        public final String endpoint,slotId,preparedToken;
        private Details(String endpoint,String slotId,String preparedToken){this.endpoint=endpoint;this.slotId=slotId;this.preparedToken=preparedToken;}
    }
    private PreparedSetupFile(){}
    public static Details read(InputStream input,String expectedEndpoint)throws IOException {
        if(input==null)throw invalid();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<=MAX_BYTES;i++){int value=input.read();if(value<0)break;if(i==MAX_BYTES)throw invalid();bytes.write(value);}
        final String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
        catch(CharacterCodingException malformed){throw invalid();}
        return parse(text,expectedEndpoint);
    }
    public static Details parse(String text,String expectedEndpoint)throws IOException {
        if(text==null||text.length()>MAX_BYTES||expectedEndpoint==null||expectedEndpoint.isEmpty())throw invalid();
        Cursor cursor=new Cursor(text);Map<String,Object> fields=cursor.object();
        if(fields.size()!=4||!Integer.valueOf(1).equals(fields.get("v"))||!(fields.get("endpoint") instanceof String)||!(fields.get("slotId") instanceof String)||!(fields.get("preparedToken") instanceof String))throw invalid();
        String endpoint=(String)fields.get("endpoint"),slot=(String)fields.get("slotId"),token=(String)fields.get("preparedToken");
        if(!endpoint.equals(expectedEndpoint)||!LiveConfig.checkedEndpoint(endpoint).equals(endpoint)||!canonical(slot,16)||!canonical(token,32))throw invalid();
        return new Details(endpoint,slot,token);
    }
    private static boolean canonical(String value,int count){
        if(value==null||!value.matches("[A-Za-z0-9_-]+")||value.length()!=(count==16?22:43))return false;
        try{byte[] decoded=Base64.getUrlDecoder().decode(value);return decoded.length==count&&Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);}catch(IllegalArgumentException malformed){return false;}
    }
    private static IOException invalid(){return new IOException("The selected setup file is invalid or does not match the configured service.");}
    private static final class Cursor {
        final String value;int at;
        Cursor(String value){this.value=value;}
        void ws(){while(at<value.length()&&(value.charAt(at)==' '||value.charAt(at)=='\n'||value.charAt(at)=='\r'||value.charAt(at)=='\t'))at++;}
        boolean take(char c){ws();if(at<value.length()&&value.charAt(at)==c){at++;return true;}return false;}
        Map<String,Object> object()throws IOException {
            Map<String,Object> result=new HashMap<>();if(!take('{'))throw invalid();
            if(!take('}'))while(true){String key=string();if(result.containsKey(key)||!take(':'))throw invalid();ws();Object item;
                if(at<value.length()&&value.charAt(at)=='"')item=string();
                else if(at<value.length()&&value.charAt(at)=='1'){at++;item=Integer.valueOf(1);}else throw invalid();
                result.put(key,item);if(result.size()>4)throw invalid();if(take('}'))break;if(!take(','))throw invalid();
            }
            ws();if(at!=value.length())throw invalid();return result;
        }
        String string()throws IOException {
            ws();if(at>=value.length()||value.charAt(at++)!='"')throw invalid();StringBuilder result=new StringBuilder();
            while(at<value.length()){
                char c=value.charAt(at++);if(c=='"')return result.toString();if(c<32)throw invalid();
                if(c=='\\'){
                    if(at>=value.length())throw invalid();char escape=value.charAt(at++);
                    switch(escape){case '"':case '\\':case '/':c=escape;break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
                    case 'u':if(at+4>value.length())throw invalid();int code=0;for(int n=0;n<4;n++){char hex=value.charAt(at++);int digit=hex>='0'&&hex<='9'?hex-'0':hex>='a'&&hex<='f'?hex-'a'+10:hex>='A'&&hex<='F'?hex-'A'+10:-1;if(digit<0)throw invalid();code=(code<<4)|digit;}c=(char)code;break;default:throw invalid();}
                }
                result.append(c);
            }
            throw invalid();
        }
    }
}
