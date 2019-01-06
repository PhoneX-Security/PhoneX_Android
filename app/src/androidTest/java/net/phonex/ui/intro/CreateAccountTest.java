package net.phonex.ui.intro;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.UiThreadTest;

import net.phonex.soap.SSLSOAP;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

/**
 * A bit cumbersome, but working test for trial creation fail cases
 * Created by miroc on 17.3.15.
 */
public class CreateAccountTest extends AndroidTestCase implements RequestAccountTask.RequestTrialEventListener {
    // On particular IPs (assigned to ioffice.phone-x.net), this captcha always work
    private static final String QA_CAPTCHA = "captcha";

    private CountDownLatch signal;
    private RequestAccountTask task;

    private int responseCode = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();

        KeyStore keyStore = SSLSOAP.loadWebTrustStore(context);
        SSLContext sslContext = SSLSOAP.getSSLContext(keyStore, null, context, new SecureRandom());
        SSLSOAP.setHostnameVerifier();

        signal = new CountDownLatch(1);
        task = new RequestAccountTask(context);
        task.setListener(this);
        task.setSslContext(sslContext);
    }

    // TODO 17.6.2015 - temp turned off until we have custom testing qa server
//    @UiThreadTest
//    public void testBadCaptcha() throws InterruptedException {
//        task.setTrialAccountParameters("Non-probable-captcha", "xx");
//        task.execute();
//        signal.await(10, TimeUnit.SECONDS);
//        assertEquals(CreateAccountFragment.RESPONSE_ERR_BAD_CAPTCHA, responseCode);
//    }
//
//    @UiThreadTest
//    public void testExistingUsername() throws InterruptedException {
//        task.setTrialAccountParameters(QA_CAPTCHA, "test318");
//        task.execute();
//        signal.await(10, TimeUnit.SECONDS);
//        assertEquals(CreateAccountFragment.RESPONSE_ERR_EXISTING_USERNAME, responseCode);
//    }
//
//    @UiThreadTest
//    public void testUsernameBadFormat() throws InterruptedException {
//        task.setTrialAccountParameters(QA_CAPTCHA, "a");
//        task.execute();
//        signal.await(10, TimeUnit.SECONDS);
//        assertEquals(CreateAccountFragment.RESPONSE_ERR_USERNAME_BAD_FORMAT, responseCode);
//    }
//
//    @UiThreadTest
//    public void testMissingFields() throws InterruptedException {
//        task.setTrialAccountParameters(null, "a");
//        task.execute();
//        signal.await(10, TimeUnit.SECONDS);
//        assertEquals(CreateAccountFragment.RESPONSE_ERR_MISSING_FIELDS, responseCode);
//    }

    @Override
    public void onReceivedResponse(JSONObject response) {
        try {
            responseCode = response.getInt("responseCode");
        } catch (JSONException e) {
            responseCode = -1;
        }
        signal.countDown();
    }

    @Override
    public void reloadCaptcha() {
        // nothing
    }

    @Override
    public void onError(String localizedErrorMessage) {
        // nothing
    }
}
