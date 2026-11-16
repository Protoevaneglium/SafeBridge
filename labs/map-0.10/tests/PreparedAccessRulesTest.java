package com.stange.safebridge;
import java.io.IOException;
import java.util.Base64;
public final class PreparedAccessRulesTest {
 static int checks;
 static String slot=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
 static String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
 static void rejected(String endpoint,String id,String secret,String config)throws Exception{try{PreparedAccessRules.validate(endpoint,id,secret,config);throw new AssertionError("expected prepared rejection");}catch(IOException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  String origin="https://example.org";
  if(!PreparedAccessRules.validate(origin,slot,token,origin).equals(origin))throw new AssertionError();checks++;
  if(!PreparedAccessRules.validate(origin+"/",slot,token,origin).equals(origin))throw new AssertionError();checks++;
  if(!PreparedAccessRules.validate("https://SERVICE.example",slot,token,"https://service.example:443/").equals("https://service.example"))throw new AssertionError();checks++;
  rejected(origin,slot,token,"");rejected(origin,slot,token,null);rejected("https://other.example",slot,token,origin);rejected("http://example.org",slot,token,origin);
  rejected(origin+"/override",slot,token,origin);rejected(origin+"?secret=x",slot,token,origin);rejected(origin+"#fragment",slot,token,origin);rejected("https://user:secret@example.org",slot,token,origin);
  rejected(origin,null,token,origin);rejected(origin,slot,null,origin);rejected(origin,slot.substring(1),token,origin);rejected(origin,slot,token.substring(1),origin);
  rejected(origin,slot+"=",token,origin);rejected(origin,slot,token+"=",origin);rejected(origin,slot.substring(0,21)+"B",token,origin);rejected(origin,slot,token.substring(0,42)+"B",origin);
  rejected(origin,slot.substring(0,21)+"+",token,origin);rejected(origin,slot,"/"+token.substring(1),origin);rejected("https://localhost",slot,token,"https://localhost");
  System.out.println("PASS "+checks+" prepared-access rule cases: exact configured HTTPS origin and canonical 16/32-byte credentials");
 }
}
