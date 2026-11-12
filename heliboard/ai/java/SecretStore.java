// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
/** The API key is never stored in preferences, backups or source code. */
public final class SecretStore {
    private static final String GROQ="groq",GEMINI="gemini";
    private static AtomicFile file(Context c,String provider) {
        return new AtomicFile(new File(c.getNoBackupFilesDir(),GEMINI.equals(provider)?"gemini-key.enc":"groq-key.enc"));
    }
    private static javax.crypto.SecretKey key(String provider)throws Exception {
        String alias=GEMINI.equals(provider)?"tecladobr.gemini.v1":"tecladobr.groq.v1";
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(alias)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            gen.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey(alias,null);
    }
    // Groq methods remain compatible with existing encrypted credentials and tests.
    public static boolean hasGeminiKey(Context c) {return file(c,GEMINI).getBaseFile().isFile();}
    public static synchronized void saveGemini(Context c,String value)throws Exception {saveFor(c,GEMINI,value);}
    static synchronized String readGemini(Context c)throws Exception {return readFor(c,GEMINI);}
    public static synchronized void removeGemini(Context c) {file(c,GEMINI).delete();}
    public static boolean hasKey(Context c) {
        return file(c,GROQ).getBaseFile().isFile();
    }
    public static synchronized void save(Context c,String value)throws Exception {saveFor(c,GROQ,value);}
    private static void saveFor(Context c,String provider,String value)throws Exception {
        value=value.trim();
        if(value.length()<16||value.length()>512||value.contains("\n")||value.contains("\r"))
            throw new IllegalArgumentException("Confira a chave do provedor.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key(provider));
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        AtomicFile f=file(c,provider);
        FileOutputStream out=null;
        try {
            out=f.startWrite();
            out.write(cipher.getIV().length);
            out.write(cipher.getIV());
            out.write(encrypted);
            f.finishWrite(out);
        }
        catch(Exception e) {
            if(out!=null)f.failWrite(out);
            throw e;
        }
    }
    static synchronized String read(Context c)throws Exception {return readFor(c,GROQ);}
    private static String readFor(Context c,String provider)throws Exception {
        byte[] bytes=file(c,provider).readFully();
        if(bytes.length<30)throw new IOException("Chave indisponível. Salve a chave novamente.");
        int count=bytes[0]&255;
        if(count<12||count>16||bytes.length<=count+17)throw new IOException("Chave inválida.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(provider),new GCMParameterSpec(128,bytes,1,count));
        return new String(cipher.doFinal(bytes,1+count,bytes.length-count-1),StandardCharsets.UTF_8);
    }
    public static synchronized void remove(Context c) {
        file(c,GROQ).delete();
    }
}

