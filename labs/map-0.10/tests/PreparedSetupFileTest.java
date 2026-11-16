package com.stange.safebridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PreparedSetupFileTest {
 static int checks;
 static final String ORIGIN="https://configured.example",SLOT=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]),KEY=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
 static String valid(){return "{\"v\":1,\"endpoint\":\""+ORIGIN+"\",\"slotId\":\""+SLOT+"\",\"preparedToken\":\""+KEY+"\"}";}
 interface Attempt{void run()throws Exception;}
 static void check(boolean ok){if(!ok)throw new AssertionError("setup check "+(checks+1));checks++;}
 static void rejected(Attempt a)throws Exception{try{a.run();throw new AssertionError("invalid setup accepted");}catch(IOException expected){check(!expected.getMessage().contains(KEY));}}
 public static void main(String[] args)throws Exception{
  PreparedSetupFile.Details d=PreparedSetupFile.read(new ByteArrayInputStream(valid().getBytes(StandardCharsets.UTF_8)),ORIGIN);check(d.endpoint.equals(ORIGIN)&&d.slotId.equals(SLOT)&&d.preparedToken.equals(KEY));
  d=PreparedSetupFile.parse("\n\t"+valid()+" \r\n",ORIGIN);check(d.endpoint.equals(ORIGIN));
  for(String bad:new String[]{"", "{}", "[]", valid()+"{}",valid().replace("\"v\":1","\"v\":1.0"),valid().replace("\"v\":1","\"v\":true"),valid().replace("\"v\":1","\"v\":\"1\""),valid().replace("\"v\":1","\"v\":11"),valid().replace("\"v\":1","\"v\":0"),valid().replace("\"v\":1","\"v\":1,\"v\":1"),valid().replace("\"v\":1","\"v\":1,\"x\":1"),valid().replace("\"v\":1","\"v\":1,\"endpoint\":\"https://other.example\""),valid().replace("\"v\":1,",""),valid().replace(ORIGIN,"https://other.example"),valid().replace(ORIGIN,"http://configured.example"),valid().replace(ORIGIN,ORIGIN+"/"),valid().replace(ORIGIN,ORIGIN+"?secret=value"),valid().replace(ORIGIN,"https://user:secret@configured.example"),valid().replace(KEY,KEY+"="),valid().replace(SLOT,SLOT+"="),valid().replace(KEY,KEY.substring(1)),valid().replace(SLOT,"B"+SLOT.substring(1,21)+"B"),valid().replace(KEY,"A".repeat(42)+"B"),valid().replace(KEY," "+KEY),valid().replace("\"v\"","\"v\n\""),valid().replace("\"endpoint\"","\"\\q\""),valid().replace("\"endpoint\"","\"\\u0000\""),valid().replace("endpoint","\\u００６５ndpoint"),valid()+"null",valid().replace("\"v\":1","\"v\":{}"),valid().replace("\"v\":1","\"v\":[]")})rejected(()->PreparedSetupFile.parse(bad,ORIGIN));
  rejected(()->PreparedSetupFile.parse(valid(),""));rejected(()->PreparedSetupFile.parse(valid(),null));rejected(()->PreparedSetupFile.read(null,ORIGIN));
  String exactly=valid()+" ".repeat(PreparedSetupFile.MAX_BYTES-valid().getBytes(StandardCharsets.UTF_8).length);check(PreparedSetupFile.read(new ByteArrayInputStream(exactly.getBytes(StandardCharsets.UTF_8)),ORIGIN).slotId.equals(SLOT));
  rejected(()->PreparedSetupFile.read(new ByteArrayInputStream((exactly+" ").getBytes(StandardCharsets.UTF_8)),ORIGIN));
  final int[] calls={0};rejected(()->PreparedSetupFile.read(new InputStream(){public int read(){calls[0]++;return ' ';}},ORIGIN));check(calls[0]==4097);
  for(byte[] bad:new byte[][]{{(byte)0xc3},{(byte)0xc3,(byte)0x28},{(byte)0xff},{(byte)0xef,(byte)0xbb,(byte)0xbf,'{'}})rejected(()->PreparedSetupFile.read(new ByteArrayInputStream(bad),ORIGIN));
  rejected(()->PreparedSetupFile.read(new InputStream(){public int read()throws IOException{throw new IOException("synthetic provider failed");}},ORIGIN));
  check(PreparedSetupFile.parse(valid().replace("endpoint","end\\u0070oint"),ORIGIN).endpoint.equals(ORIGIN));
  System.out.println("PASS "+checks+" private setup-file bounded bytes/UTF-8/schema/duplicate/endpoint/canonical-secret checks (host only)");
 }
}
