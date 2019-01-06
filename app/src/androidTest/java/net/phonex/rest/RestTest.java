package net.phonex.rest;

import android.content.pm.PackageInfo;
import android.test.AndroidTestCase;

import junit.framework.Assert;

import net.phonex.rest.entities.Version;
import net.phonex.util.Log;

import org.junit.Test;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by miroc on 17.6.15.
 */
public class RestTest extends AndroidTestCase {
    private static final String TAG = "RestTest";
    private WebServerApi webServerApi;

    public int getVersionCode() {
        return versionCode;
    }

    // TODO I am not able to retrieve versionCode of the app in the test, therefore hardcoding for now
    private int versionCode = 2290;
//    private PackageInfo pInfo;

//    @Override
//    protected void setUp() throws Exception {
//        super.setUp();
//        webServerApi = TLSUtils.prepareWebServerApi();
////        pInfo = getContext().getPackageManager().getPackageArchiveInfo(getContext().getPackageName(), 0);
//    }

    @Test
    public void testNewestVersionCheck() throws Exception {
        webServerApi = TLSUtils.prepareWebServerApi();
        Assert.assertNotNull(webServerApi);
//        pInfo = getContext().getPackageManager().getPackageArchiveInfo(getContext().getPackageName(), 0);
        webServerApi.getNewestVersion(new Callback<Version>() {
            @Override
            public void success(Version version, Response response) {
                Assert.assertEquals(200, (int) version.getResponseCode());
                Assert.assertTrue(version.getVersionCode() >= getVersionCode());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.ef(TAG, error, "testNewestVersionCheck problem");
                Assert.assertTrue(false);
            }
        });
    }

    @Test
    public void testVersionCheck() throws Exception {
        webServerApi = TLSUtils.prepareWebServerApi();
//        pInfo = getContext().getPackageManager().getPackageArchiveInfo(getContext().getPackageName(), 0);

        Assert.assertNotNull(webServerApi);
//        Assert.assertNotNull(pInfo);

        webServerApi.getVersion(getVersionCode(), new Callback <Version>() {
            @Override
            public void success (Version version, Response response){
                Assert.assertEquals(200, (int) version.getResponseCode());
                Assert.assertEquals(getVersionCode(), (int) version.getVersionCode());
//                Assert.assertEquals(pInfo.versionName, version.getVersionName());
                Assert.assertNotNull(version.getVersionName());
                Assert.assertNotNull(version.getReleaseNotes());
                Assert.assertNotNull(version.getAvailableAtMarket());
            }

            @Override
            public void failure (RetrofitError error){
                Log.ef(TAG, error, "testVersionCheck problem");
                Assert.assertTrue(false);
            }
        });
    }
}
