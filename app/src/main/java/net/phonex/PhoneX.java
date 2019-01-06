package net.phonex;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.multidex.BuildConfig;
import android.support.multidex.MultiDex;

//import com.github.nativehandler.NativeCrashHandler;
import com.google.android.gms.analytics.ExceptionReporter;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import net.phonex.pub.a.Compatibility;
import net.phonex.soap.SSLSOAP;
import net.phonex.util.Log;
import net.phonex.util.android.JNILibsManager;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.KeyStore;

public class PhoneX extends Application {
	private static final String THIS_FILE="PhoneX";
	
	@Override
    public void onCreate() {
        super.onCreate();

        // Default security preferences have to be loaded before acra initialization (it's disabled by default)
        setDefaultSecurityPreference();
//        initACRA();

        // required for trial requests
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        Log.v(THIS_FILE, "Registering application life cycle listener");
        registerActivityLifecycleCallbacks(new AppLifeCycleListener());

//        registerNativeCrashHandler(this);
        //LeakCanary.install(this);

        // everything is OK
        Log.v(THIS_FILE, "Application started");
    }

    private Tracker tracker;

    /**
     * Gets the default {@link Tracker} for this {@link Application}.
     * @return tracker
     */
    synchronized public Tracker getDefaultTracker() {
        if (tracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            if (BuildConfig.DEBUG){
                analytics.setDryRun(true);
            }

            boolean googleAnalyticsEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(net.phonex.ui.preferences.PreferenceManager.SEC_GOOGLE_ANALYTICS_ENABLE, false);
            Log.df(THIS_FILE, "getDefaultTracker; optingOut, googleAnalyticsEnabled = %s", googleAnalyticsEnabled);
            analytics.setAppOptOut(!googleAnalyticsEnabled);
            // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG

            tracker = analytics.newTracker(R.xml.global_tracker);
            ExceptionReporter reporter = new ExceptionReporter(
                    tracker,                                        // Currently used Tracker.
                    Thread.getDefaultUncaughtExceptionHandler(),      // Current default uncaught exception handler.
                    getApplicationContext());
            reporter.setExceptionParser(new AnalyticsExceptionParser());
            Thread.setDefaultUncaughtExceptionHandler(reporter);

            // When custom ExceptionReporter is set, enableExceptionReporting is not required to be set to true
//          tracker.enableExceptionReporting(true);
        }
        return tracker;
    }

    private void setDefaultSecurityPreference(){
        int securityXml = net.phonex.ui.preferences.PreferenceManager.getXmlResourceForType(net.phonex.ui.preferences.PreferenceManager.GROUP_SECURITY);
        PreferenceManager.setDefaultValues(this, securityXml, false);
    }

    /**
     * Enable multi-dex support for particular build flavor
     * This enables quick incremental build when building for API 21+ (Android 5), which compiles each module into separate dex
     * When app is built with multi-dex support, proguard can be turned off
     * @param base
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        if (BuildConfig.FLAVOR.equals("multidex")){
//            MultiDex.install(this);
//        }
//
    }

    /**
	 * Registers native crash handler (loads libraries).
	 * 
	 * @param ctxt
	 */
//	public static void registerNativeCrashHandler(Context ctxt){
//		try {
//            // Try to load the natives
//        	Log.v(THIS_FILE, "Initializing native crash handler");
//        	System.loadLibrary(JNILibsManager.STD_LIB_NAME);
//
//        	// do we have libcorkscrew? If we have Android without native one
//        	try {
//	        	if (!Compatibility.isApiGreaterOrEquals(16)){
//	        		System.loadLibrary(JNILibsManager.CORK_NAME);
//	        		Log.v(THIS_FILE, "Local libcorkscrew loaded");
//	        	}
//        	} catch(Exception e){
//        		Log.v(THIS_FILE, "Problem with loading local libcorcscrew");
//        	}
//
//            System.loadLibrary(JNILibsManager.CRASH_NAME);
//            Log.v(THIS_FILE, "Native crash libs just loaded");
//
//            new NativeCrashHandler().registerForNativeCrash(ctxt);
//            Log.v(THIS_FILE, "Native crash handler initialized");
//        } catch (UnsatisfiedLinkError e) {
//            // If it fails we probably are running on a special hardware
//            Log.e(THIS_FILE, "We have a problem with the current stack.... Not yet Implemented", e);
//        } catch (Exception e) {
//            Log.e(THIS_FILE, "We have a problem with the current stack....", e);
//        }
//	}
}
