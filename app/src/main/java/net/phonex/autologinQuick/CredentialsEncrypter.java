package net.phonex.autologinQuick;

import android.support.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.autologin.LoginCredentials;
import net.phonex.core.SipUri;
import net.phonex.pub.proto.AutoLogin;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CryptoHelper;

import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;


/**
 * Hybrid crypto for LoginCredential
 * @author miroc
 */
final class CredentialsEncrypter {
    private static final String TAG = "CredentialsEncrypter";

    /**
     * Encrypt (AES-GCM) and serialize login credentials
     * Internally it generates a SecretKey, which is wrapped using asymmetric crypto
     * @param credentials
     * @param secretKeyWrapper
     * @return
     * @throws GeneralSecurityException
     */
    public static byte[] encryptAndSerialize(@NonNull LoginCredentials credentials, SecretKeyWrapper secretKeyWrapper) throws GeneralSecurityException {
        byte[] serializedCreds = serialize(credentials);
        CryptoHelper.AesEncryptionResult encryptionResult;
        try {
            encryptionResult = CryptoHelper.encryptAesGcm(serializedCreds);
        } catch (CryptoHelper.CipherException e) {
            throw new GeneralSecurityException(e);
        }

        byte[] wrappedSecretKey = secretKeyWrapper.wrap(encryptionResult.secretKey);

        byte[] empty = new byte[1];

        AutoLogin.AutoLoginData data = AutoLogin.AutoLoginData.newBuilder()
                .setCredsEncrypted(ByteString.copyFrom(encryptionResult.ciphertext))
                .setIv(ByteString.copyFrom(encryptionResult.iv))
                .setPasswordEncrypted(ByteString.copyFrom(wrappedSecretKey))
                // we do not need to set these items, but in protobuf they are required (legacy), so we have to (e.g. dummy byte)
                .setNonce(ByteString.copyFrom(empty))
                .setUserName(ByteString.copyFrom(empty))
                .build();
        return data.toByteArray();
    }

    /**
     * Opposite of encryptAndSerialize()
     * @param storedCredentials
     * @param secretKeyWrapper
     * @return
     * @throws GeneralSecurityException
     */
    public static LoginCredentials deserializeAndDecrypt(@NonNull byte[] storedCredentials, @NonNull SecretKeyWrapper secretKeyWrapper) throws GeneralSecurityException {
        AutoLogin.AutoLoginData data;
        try {
            data = AutoLogin.AutoLoginData.parseFrom(storedCredentials);
        } catch (InvalidProtocolBufferException e) {
            throw new GeneralSecurityException("Failed to deserialize stored password", e);
        }

        SecretKey secretKey = secretKeyWrapper.unwrap(data.getPasswordEncrypted().toByteArray());
        byte[] iv = data.getIv().toByteArray();
        byte[] encryptedCreds = data.getCredsEncrypted().toByteArray();
        try {
            byte[] serializedCreds = CryptoHelper.decryptAesGcm(encryptedCreds, iv, secretKey);
            return deserialize(serializedCreds);
        } catch (CryptoHelper.CipherException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private static LoginCredentials deserialize(byte[] serializedLogin) throws GeneralSecurityException {
        Log.df(TAG, "deserialize; bytes=%s", MiscUtils.bytesToHex(serializedLogin));

        AutoLogin.AutoLoginCreds data;
        try {
            data = AutoLogin.AutoLoginCreds.parseFrom(serializedLogin);
        } catch (InvalidProtocolBufferException e) {
            throw new GeneralSecurityException("Failed to deserialize stored password", e);
        }

        SipUri.ParsedSipContactInfos parsedSipContact = SipUri.parseSipContact(new String(data.getSip().toByteArray()));
        return new LoginCredentials(new String(data.getPassword().toByteArray()), parsedSipContact.userName, parsedSipContact.domain);
    }

    private static byte[] serialize(@NonNull LoginCredentials loginCredentials){
        final String sip = loginCredentials.userName + "@" + loginCredentials.domain;

        AutoLogin.AutoLoginCreds data = AutoLogin.AutoLoginCreds.newBuilder()
                .setPassword(ByteString.copyFrom(loginCredentials.password.getBytes()))
                .setSip(ByteString.copyFrom(sip.getBytes()))
                .build();
        byte[] bytes = data.toByteArray();
        Log.df(TAG, "serialize; bytes=%s", MiscUtils.bytesToHex(bytes));
        return bytes;
    }
}