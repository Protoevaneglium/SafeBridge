package com.stange.safebridge;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONException;
import org.json.JSONObject;

/** Separate setup secret only. Forgetting it never stops, resets or revokes a session. */
public final class PreparedAccessStore {
    public static final class Descriptor {
        public final String endpoint,slotId,preparedToken;
        public Descriptor(String endpoint,String slotId,String preparedToken){this.endpoint=endpoint;this.slotId=slotId;this.preparedToken=preparedToken;}
        // Deliberately no toString, logging, parceling or automatic sharing.
    }
    private static final Object LOCK=new Object();
    private static final String ALIAS="safe_bridge_prepared_access_v1";
    private static final byte[] MAGIC={83,66,80,65,1};
    private static final Set<String> FIELDS=new HashSet<>(Arrays.asList("v","endpoint","slotId","preparedToken"));
    private final AtomicFile atomic;
    private final byte[] aad;
    public PreparedAccessStore(Context context){Context app=context.getApplicationContext();atomic=new AtomicFile(new File(app.getNoBackupFilesDir(),"prepared-access-v1.enc"));aad=(app.getPackageName()+"|PreparedAccessStore|1").getBytes(StandardCharsets.UTF_8);}
    public Descriptor load()throws IOException{
        synchronized(LOCK){
            byte[] encrypted;
            try(FileInputStream input=atomic.openRead()){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>8192)throw new IOException("Prepared-access record is too large.");bytes.write(buffer,0,count);}encrypted=bytes.toByteArray();
            }catch(FileNotFoundException absent){File base=atomic.getBaseFile();if(base.exists() || new File(base.getPath()+".bak").exists() || new File(base.getPath()+".new").exists())throw new IOException("Prepared access could not be read.");return null;}
            byte[] plaintext=null;
            try{
                if(encrypted.length<MAGIC.length+28)throw new IOException("Invalid encrypted prepared access.");
                for(int i=0;i<MAGIC.length;i++)if(encrypted[i]!=MAGIC[i])throw new IOException("Unsupported prepared-access record.");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Arrays.copyOfRange(encrypted,MAGIC.length,MAGIC.length+12)));cipher.updateAAD(aad);
                plaintext=cipher.doFinal(encrypted,MAGIC.length+12,encrypted.length-MAGIC.length-12);
                if(plaintext.length>4096)throw new IOException("Prepared access is too large.");
                JSONObject json=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));Set<String> names=new HashSet<>();Iterator<String> keys=json.keys();while(keys.hasNext())names.add(keys.next());
                if(!FIELDS.equals(names) || !(json.get("v") instanceof Number) || ((Number)json.get("v")).doubleValue()!=1d)throw new IOException("Unsupported prepared-access fields.");
                if(!(json.get("endpoint") instanceof String) || !(json.get("slotId") instanceof String) || !(json.get("preparedToken") instanceof String))throw new IOException("Invalid prepared-access fields.");
                String endpoint=PreparedAccessRules.validate(json.getString("endpoint"),json.getString("slotId"),json.getString("preparedToken"),LiveConfig.endpoint());
                return new Descriptor(endpoint,json.getString("slotId"),json.getString("preparedToken"));
            }catch(GeneralSecurityException|JSONException|RuntimeException invalid){throw new IOException("Prepared access could not be decrypted. No plaintext fallback is available.");}
            finally{if(plaintext!=null)Arrays.fill(plaintext,(byte)0);Arrays.fill(encrypted,(byte)0);}
        }
    }
    public void save(Descriptor descriptor)throws IOException{
        if(descriptor==null)throw new IOException("Prepared access is missing.");
        String endpoint=PreparedAccessRules.validate(descriptor.endpoint,descriptor.slotId,descriptor.preparedToken,LiveConfig.endpoint());
        synchronized(LOCK){
            byte[] plaintext;
            try{JSONObject json=new JSONObject();json.put("v",1);json.put("endpoint",endpoint);json.put("slotId",descriptor.slotId);json.put("preparedToken",descriptor.preparedToken);plaintext=json.toString().getBytes(StandardCharsets.UTF_8);}
            catch(JSONException invalid){throw new IOException("Invalid prepared access.");}
            FileOutputStream output=null;
            try{
                if(plaintext.length>4096)throw new IOException("Prepared access is too large.");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(aad);byte[] iv=cipher.getIV();if(iv==null || iv.length!=12)throw new IOException("Secure encryption nonce unavailable.");byte[] ciphertext=cipher.doFinal(plaintext);
                output=atomic.startWrite();output.write(MAGIC);output.write(iv);output.write(ciphertext);output.getFD().sync();atomic.finishWrite(output);output=null;Arrays.fill(ciphertext,(byte)0);
            }catch(GeneralSecurityException|RuntimeException invalid){throw new IOException("Secure prepared-access storage unavailable. No plaintext fallback is available.");}
            finally{if(output!=null)atomic.failWrite(output);Arrays.fill(plaintext,(byte)0);}
        }
    }
    /** Local forgetting only: no network or session-management dependency exists here. */
    public void clear()throws IOException{
        synchronized(LOCK){atomic.delete();File base=atomic.getBaseFile();if(base.exists() || new File(base.getPath()+".bak").exists() || new File(base.getPath()+".new").exists())throw new IOException("Prepared-access removal could not be confirmed.");
            try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}
            catch(GeneralSecurityException|RuntimeException invalid){throw new IOException("Prepared-access key cleanup could not be confirmed.");}}
    }
    private SecretKey key(boolean create)throws GeneralSecurityException,IOException{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS)){Key key=store.getKey(ALIAS,null);if(key instanceof SecretKey)return (SecretKey)key;throw new GeneralSecurityException("Unexpected keystore entry.");}
        if(!create)throw new GeneralSecurityException("Prepared-access key unavailable.");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());return generator.generateKey();
    }
}
