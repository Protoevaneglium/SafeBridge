package com.stange.safebridge;
import java.io.IOException;
public final class EndpointOriginRulesTest {
 public static void main(String[] args)throws Exception{int checks=0;
  for(String host:new String[]{"example.org","10.1.2.3","192.168.1.2","172.16.2.3","[fd00::1]","[fe80::2]","[2001:db8::1]"}){LiveConfig.checkedEndpoint("https://"+host);checks++;}
  for(String host:new String[]{"localhost","localhost.","a.localhost","a.localhost.","127.0.0.1","127.0.0.2","127.255.255.255","127.1","2130706433","0.0.0.0","192.168.001.001","[::]","[0:0:0:0:0:0:0:0]","[::1]","[0:0:0:0:0:0:0:1]","[0000:0000:0000:0000:0000:0000:0000:0001]","[::ffff:127.0.0.2]","[::ffff:7f00:2]","[0:0:0:0:0:ffff:7f00:2]","[::ffff:192.168.1.2]","[::127.0.0.2]","[::7f00:2]"}){try{LiveConfig.checkedEndpoint("https://"+host);throw new AssertionError("expected blocked local origin");}catch(IOException expected){checks++;}}
  String[][] canonical={{"https://SERVICE.example:443/","https://service.example"},{"HTTPS://Service.Example/","https://service.example"},{"https://SERVICE.example:8443/","https://service.example:8443"},{"https://[2001:0DB8:0000:0000:0000:0000:0000:0002]:443/","https://[2001:db8::2]"},{"https://[2001:0db8:0:1:0:0:0:2]/","https://[2001:db8:0:1::2]"},{"https://[FD00:0:0:0:0:0:0:2]/","https://[fd00::2]"}};
  for(String[] pair:canonical){if(!LiveConfig.checkedEndpoint(pair[0]).equals(pair[1]))throw new AssertionError("origin normalization");checks++;}
  System.out.println("PASS "+checks+" origin cases: loopback/mapped/unspecified aliases denied, private self-hosted origins retained, no DNS/network");
 }
}
