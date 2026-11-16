package com.stange.safebridge;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.stange.safebridge.live.LiveCoordinator;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Minimal remote-management journal, encrypted with an AndroidKeystore AES-GCM key.
 * No coordinates, location samples, recipient data or history. Never falls back to
 * plaintext. Uses the app's no-backup directory and AtomicFile replacement + sync.
 * Keystore protection is device-dependent; this does not protect a compromised OS,
 * prevent all forensic recovery or make a monitored device safe.
 */
public final class LiveSessionStore implements LiveCoordinator.Store {
    private static final Object DISK_LOCK=new Object();
    private static final String KEY_ALIAS="safe_bridge_live_management_v2";
    private static final int MAX_BYTES=16384;
    private static final byte[] MAGIC={83,66,76,83,2};
    private static final Set<String> FIELDS=new HashSet<>(Arrays.asList("schema","endpoint","generation","id","readToken","writeToken","revokeToken","creationKey","createPermit","admissionTicket","managementReceipt","durationSeconds","expiresAtMs","leaseUntilMs","locallyStopped","creationUncertain"));
    private final AtomicFile atomic;
    private final byte[] aad;
    public LiveSessionStore(Context context){
        Context app=context.getApplicationContext();
        atomic=new AtomicFile(new File(app.getNoBackupFilesDir(),"live-management-v2.enc"));
        aad=(app.getPackageName()+"|LiveSessionStore|2").getBytes(StandardCharsets.UTF_8);
    }
    @Override public LiveCoordinator.Management load()throws IOException{
        synchronized(DISK_LOCK){
            byte[] encrypted;
            try(FileInputStream input=atomic.openRead()){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)throw new IOException("Secure management record is too large.");bytes.write(buffer,0,count);}
                encrypted=bytes.toByteArray();
            }catch(FileNotFoundException absent){
                File base=atomic.getBaseFile();
                if(base.exists() || new File(base.getPath()+".bak").exists() || new File(base.getPath()+".new").exists())throw new IOException("Secure management record could not be read.");
                return null;
            }
            byte[] plaintext=null;
            try{
                if(encrypted.length<MAGIC.length+12+16)throw new IOException("Invalid secure management record.");
                for(int i=0;i<MAGIC.length;i++)if(encrypted[i]!=MAGIC[i])throw new IOException("Unsupported secure management record.");
                byte[] iv=Arrays.copyOfRange(encrypted,MAGIC.length,MAGIC.length+12);
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));cipher.updateAAD(aad);
                plaintext=cipher.doFinal(encrypted,MAGIC.length+12,encrypted.length-MAGIC.length-12);
                JSONObject json=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));
                Map<String,Object> values=new HashMap<>();Iterator<String> keys=json.keys();
                while(keys.hasNext()){String name=keys.next();if(!FIELDS.contains(name))throw new IOException("Unexpected secure management field.");Object value=json.get(name);values.put(name,value==JSONObject.NULL?null:value);}
                return LiveCoordinator.Management.fromMap(values);
            }catch(GeneralSecurityException|JSONException|RuntimeException invalid){
                throw new IOException("Secure session management could not be decrypted. No plaintext fallback is available.");
            }finally{if(plaintext!=null)Arrays.fill(plaintext,(byte)0);Arrays.fill(encrypted,(byte)0);}
        }
    }
    @Override public void save(LiveCoordinator.Management value)throws IOException{
        if(value==null)throw new IOException("Missing session management.");
        synchronized(DISK_LOCK){
            Map<String,Object> fields=value.toMap();
            if(!FIELDS.equals(fields.keySet()))throw new IOException("Unexpected session management fields.");
            // Validate before encryption; this schema contains capabilities only, never fixes.
            LiveCoordinator.Management.fromMap(fields);
            JSONObject json=new JSONObject();
            try{for(Map.Entry<String,Object> field:fields.entrySet())json.put(field.getKey(),field.getValue()==null?JSONObject.NULL:field.getValue());}
            catch(JSONException invalid){throw new IOException("Invalid secure management fields.");}
            byte[] plaintext=json.toString().getBytes(StandardCharsets.UTF_8);
            FileOutputStream output=null;
            try{
                if(plaintext.length>MAX_BYTES-64)throw new IOException("Session management is too large.");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(aad);
                byte[] iv=cipher.getIV();if(iv==null || iv.length!=12)throw new IOException("Secure encryption nonce unavailable.");
                byte[] ciphertext=cipher.doFinal(plaintext);
                output=atomic.startWrite();output.write(MAGIC);output.write(iv);output.write(ciphertext);output.getFD().sync();
                atomic.finishWrite(output);output=null;Arrays.fill(ciphertext,(byte)0);
            }catch(GeneralSecurityException|RuntimeException invalid){throw new IOException("Secure session storage is unavailable. No plaintext fallback is available.");}
            finally{if(output!=null)atomic.failWrite(output);Arrays.fill(plaintext,(byte)0);}
        }
    }
    @Override public void clear()throws IOException{
        synchronized(DISK_LOCK){
            atomic.delete();File base=atomic.getBaseFile();
            if(base.exists() || new File(base.getPath()+".bak").exists() || new File(base.getPath()+".new").exists())throw new IOException("Secure management removal could not be confirmed.");
            try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(KEY_ALIAS))store.deleteEntry(KEY_ALIAS);}
            catch(GeneralSecurityException|RuntimeException invalid){throw new IOException("Secure management key cleanup could not be confirmed.");}
        }
    }
    private SecretKey key(boolean create)throws GeneralSecurityException,IOException{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY_ALIAS)){
            Key value=store.getKey(KEY_ALIAS,null);
            if(value instanceof SecretKey)return (SecretKey)value;
            throw new GeneralSecurityException("Unexpected keystore entry.");
        }
        if(!create)throw new GeneralSecurityException("Secure session key is unavailable.");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());
        return generator.generateKey();
    }
}
