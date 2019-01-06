package net.phonex.util.crypto;

import java.security.GeneralSecurityException;

/**
 * Created by miroc on 20.1.15.
 */
public class NativeCryptoHelper {
    static {
        System.loadLibrary("openssl-wrapper");
    }
    private static final int HMAC_SHA1 = 1;
    private static final int HMAC_SHA256 = 2;

    private static NativeCryptoHelper instance = null;

    /**
     * OpenSSLWrapper is a singleton class, getInstance has to be called
     * @return
     */
    public static NativeCryptoHelper getInstance() {
        if (instance==null){
            instance = new NativeCryptoHelper();
        }
        return instance;
    }

    /**
     * Native OpenSSL PBKDF2 implementation
     * @param pass Password string
     * @param salt
     * @param saltLen (in Bytes)
     * @param iter Number of iteration
     * @param out Output array, has to be allocated
     * @param outLen (in Bytes)
     * @param mac Either HMAC_SHA1 or HMAC_SHA256
     * @return 1 on success or 0 on error.
     */
    private native int pbkdf2(String pass, byte[] salt, int saltLen, int iter, byte[] out, int outLen, int mac);

    public int pbkdf2_hmac_sha256(String pass, byte[] salt, int iter, byte[] out) throws GeneralSecurityException {
        int status = pbkdf2(pass, salt, salt.length, iter, out, out.length, HMAC_SHA256);
        if (status == 0){
            throw new GeneralSecurityException("pbkdf2_hmac_sha256; error in computation; status [" + status + "] ");
        }
        return status;
    }

    public int pbkdf2_hmac_sha1(String pass, byte[] salt, int iter, byte[] out) throws GeneralSecurityException {
        int status = pbkdf2(pass, salt, salt.length, iter, out, out.length, HMAC_SHA1);
        if (status == 0){
            throw new GeneralSecurityException("pbkdf2_hmac_sha1; error in computation; status [" + status + "] ");
        }
        return status;
    }

    public native int scrypt(byte[] passwd, int passwdLen, byte[] salt, int saltLen, int N, int r, int p, byte[] out, int outLen);
}
