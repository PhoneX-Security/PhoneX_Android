package net.phonex.autologin;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import net.phonex.BuildConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.soap.SOAPException;
import net.phonex.util.Base64;

import net.phonex.PhonexSettings;
import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pref.PreferencesManager;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.entities.AuthStateFetchV1Request;
import net.phonex.soap.entities.AuthStateFetchV1Response;
import net.phonex.soap.entities.AuthStateSaveV1Request;
import net.phonex.soap.entities.AuthStateSaveV1Response;
import net.phonex.util.Log;

import java.io.IOException;

public class RemoteServerSecretKeyStorage implements SecretKeyStorage {

    private static final String TAG = "RemoteServerSecretKeyStorage";

    private Context context;
    private SOAPHelper soap;

    public RemoteServerSecretKeyStorage(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context==null");
        }
        this.context = context;
        this.soap = new SOAPHelper(context);
    }

    @Override
    public void uploadEncodedKeyAndNonce(final byte[] key, final byte[] nonce)
            throws ServiceUnavailableException, SecretKeyStorageException {

        String encodedSecret = null;
        String encodedNonce = null;
        try {
            encodedSecret = Base64.encodeBytes(key, 0, key.length, Base64.NO_OPTIONS);
            encodedNonce = Base64.encodeBytes(nonce, 0, nonce.length, Base64.NO_OPTIONS);
        } catch (IOException e) {
            Log.d(TAG, "Failed to encode bytes to base64", e);
            throw new SecretKeyStorageException(e);
        }

        String userIdentifier = PreferencesManager.getUUID(context);
        PackageInfo packageInfo = null;
        long appVersionCode = 0;
        String appVersion = PhonexSettings.getUniversalApplicationDesc(context);
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Could not get app version name and code", e);
        }

        Exception toThrow = null;

        try {
            soap.init();
            Log.i(TAG, "Saving authentication state request list to server");
            AuthStateSaveV1Request request = new AuthStateSaveV1Request();

            request.setSecret(encodedSecret);
            request.setNonce(encodedNonce);
            request.setIdentifier(userIdentifier);
            request.setAppVersion(appVersion);

//            if (BuildConfig.DEBUG) {
//                // for debug, set 2 versions back (assume current branch is 1 version ahead)
//                request.setAppVersionCode(appVersionCode - 2);
//            } else {
//                request.setAppVersionCode(appVersionCode);
//            }
            // In old version, auto-login was available on for application update
            // In new version, auto-login is available all the time, therefore always send lower version number to the server
            request.setAppVersionCode(appVersionCode - 2);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, AuthStateSaveV1Response.class);
            final AuthStateSaveV1Response response = (AuthStateSaveV1Response) soap.makeSoapRequest(envelope, "AuthStateSaveV1Request");

            if (response == null){
                Log.df(TAG, "AuthStateSaveV1Request response is null");
                return;
            }

            if (response.getErrCode() != 0) {
                Log.df(TAG, "AuthStateSaveV1Request finished with error code %d", response.getErrCode());
                return;
            }

            //timestamp = response.getTimestamp();

        } catch (SOAPException e) {
            Log.d(TAG, "SOAPException", e);
            toThrow = e;
            throw new ServiceUnavailableException(toThrow);
        } catch (Exception e) {
            Log.d(TAG, "Exception", e);
            toThrow = e;
            throw new SecretKeyStorageException(toThrow);
        }
    }

    @Override
    public byte[] downloadEncodedKey(final byte[] nonce, final byte[] userName)
            throws ServiceUnavailableException, SecretKeyStorageException {
        if (nonce == null) {// || nonce.length != NONCE_LENGTH_BYTE) {
            throw new IllegalArgumentException("nonce");
        }

        String encodedNonce;
        try {
            encodedNonce = Base64.encodeBytes(nonce, 0, nonce.length, Base64.NO_OPTIONS);
        } catch (IOException e) {
            Log.d(TAG, "Failed to encode bytes to base64", e);
            throw new SecretKeyStorageException(e);
        }

        String userNameString = new String(userName);

        try {
            Log.d(TAG, "Initialize SOAP helper");
            if (SipProfile.getCurrentProfile(context) != null) {
                Log.d(TAG, "downloadEncodedKey with client cert");
                soap.init();
            } else {
                Log.d(TAG, "downloadEncodedKey without client cert");
                soap.initWithoutClientAuth(userNameString);
            }
            Log.i(TAG, "Fetching authentication state from server");
            AuthStateFetchV1Request request = new AuthStateFetchV1Request();

            request.setUserName(userNameString);
            request.setNonce(encodedNonce);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, AuthStateFetchV1Response.class);
            final AuthStateFetchV1Response response = (AuthStateFetchV1Response) soap.makeSoapRequest(envelope, "AuthStateFetchV1Response");

            if (response == null){
                Log.df(TAG, "AuthStateFetchV1Request response is null");
                return null;
            }

            if (response.getErrCode() != 0) {
                Log.df(TAG, "AuthStateFetchV1Request finished with error code %d", response.getErrCode());
                return null;
            }

            if (response.getSecret() == null) {
                Log.d(TAG, "Secret key is null");
                return null;
            }

            return Base64.decode(response.getSecret());
        } catch (SOAPException e) {
            Log.d(TAG, "SOAPException", e);
            throw new ServiceUnavailableException(e);
        } catch (Exception e) {
            Log.d(TAG, "Exception in downloadEncodedKey", e);
            throw new SecretKeyStorageException(e);
        }
    }
}