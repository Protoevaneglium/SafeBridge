package com.stange.safebridge;
import java.io.IOException;
public final class LiveConfigTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Configuration contract failed");}
    public static void main(String[] args)throws Exception{
        check(!LiveConfig.configured());check(LiveConfig.endpoint().isEmpty());
        check(LiveConfig.checkedEndpoint("https://example.invalid").equals("https://example.invalid"));
        check(LiveConfig.checkedEndpoint("https://example.invalid/").equals("https://example.invalid"));
        check(LiveConfig.checkedEndpoint("https://example.invalid:8443").equals("https://example.invalid:8443"));
        String[] invalid={null,"","http://example.invalid","http://127.0.0.1:8787","ftp://example.invalid","https://name:secret@example.invalid","https://name@example.invalid","https://example.invalid?token=x","https://example.invalid#read=x","https://example.invalid/path","https://example.invalid/a/../","https://example.invalid/%2e%2e","https://example.invalid\\evil"," https://example.invalid","https://example.invalid ","https://example.invalid\n","https:///missing-host","https://example.invalid:0","https://example.invalid:65536","https://example.invalid:-1","//example.invalid"};
        for(String value:invalid){boolean rejected=false;try{LiveConfig.checkedEndpoint(value);}catch(IOException expected){rejected=true;}check(rejected);}
        System.out.println("PASS "+checks+" offline live-server configuration checks (no network)");
    }
}
