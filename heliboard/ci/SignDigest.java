// SPDX-License-Identifier: GPL-3.0-only
// Operates locally. Outputs public certificate/signature bytes only; never the private key.
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
public class SignDigest {
    static byte[] le(int n) {return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();}
    static byte[] u64(long n) {return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array();}
    static byte[] join(byte[]... arr) throws IOException {var out=new ByteArrayOutputStream();for(var b:arr)out.write(b);return out.toByteArray();}
    static byte[] lp(byte[] b) throws IOException {return join(le(b.length),b);}
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("keystore digest expected-certificate-sha256 output-block");
        var ks=KeyStore.getInstance(new File(args[0]),"android".toCharArray());
        var cert=(X509Certificate)ks.getCertificate("androiddebugkey");
        var hex=HexFormat.of();
        var fingerprint=hex.formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        if(!fingerprint.equals(args[2]))throw new SecurityException("Certificate mismatch");
        byte[] digest=hex.parseHex(args[1]);if(digest.length!=32)throw new IllegalArgumentException("Digest length");
        byte[] alg=le(0x0103);
        byte[] signed=join(lp(lp(join(alg,lp(digest)))),lp(lp(cert.getEncoded())),lp(new byte[0]));
        var sig=Signature.getInstance("SHA256withRSA");
        sig.initSign((PrivateKey)ks.getKey("androiddebugkey","android".toCharArray()));sig.update(signed);
        byte[] signer=join(lp(signed),lp(lp(join(alg,lp(sig.sign())))),lp(cert.getPublicKey().getEncoded()));
        byte[] v2=lp(lp(signer));
        byte[] pair=join(u64(4+v2.length),le(0x7109871a),v2);
        byte[] block=join(u64(pair.length+24),pair,u64(pair.length+24),"APK Sig Block 42".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Files.write(Path.of(args[3]),block);
        System.out.println("Public signature produced; certificate SHA-256: "+fingerprint);
    }
}
