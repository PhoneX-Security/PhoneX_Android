package net.phonex.autologin;

import android.content.Context;

import net.phonex.autologin.exceptions.PasswordPersisterException;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

/**
 *
 * Created by miroc on 2.2.16.
 */
public interface KeyPairStorage {
    // API 19+, otherwise default values in KeyPairGeneratorSpec.Builder
    // if key size is different than the defaults, max password size will be computed incorrectly!
    String RSA = "RSA";
    int RSA_KEY_SIZE = 2048;
    BigInteger RSA_PUBLIC_EXPONENT = BigInteger.valueOf(3);

    PrivateKey retrieveKey() throws PasswordPersisterException;

    KeyPair generateKeyPair(Context context, Date end) throws GeneralSecurityException;

    PublicKey retrievePublicKey() throws PasswordPersisterException;

    boolean isKeyPairAvailable();
}
