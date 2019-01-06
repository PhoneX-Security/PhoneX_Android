package net.phonex;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import net.phonex.core.Constants;
import net.phonex.pref.PhonexConfig;
import net.phonex.pub.a.Compatibility;
import net.phonex.pref.PreferencesConnector;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;


public final class PhonexSettings {
	private static final String THIS_FILE = "PhonexSettings";

	private PhonexSettings() {}
	
	/**
	 * Email address for support and feedback
	 * If none return the feedback feature is disabled
	 * @return the email address of support
	 */
	public static String getSupportEmail() {
		return "support@phone-x.net";
	}
	
	/**
	 * SIP User agent to send by default in SIP messages (by default device infos are added to User Agent string)
	 * @return the default user agent
	 */
	public static String getUserAgent() {
		return "PhoneX";
	}

    /**
     * Returns string app identification.
     * @param ctx
     * @return
     */
    public static String getApplicationDesc(Context ctx) {
        String result = "";
        PackageManager pm = ctx.getPackageManager();
        result += ctx.getApplicationInfo().loadLabel(pm);
        PackageInfo pinfo = PreferencesConnector.getCurrentPackageInfos(ctx);
        if(pinfo != null) {
            result += " " + pinfo.versionName + ", rev.: " + pinfo.versionCode;
        }
        return result;
    }

    /**
     * Returns string app identification, version, code.
     * Returns JSON.
     * @param ctx
     * @return
     */
    public static String getUniversalApplicationDesc(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo pinfo = PreferencesConnector.getCurrentPackageInfos(ctx);
        Integer rc = 0;
        String ac = "0";
        String info = "";
        try {
            info = "" + ctx.getApplicationInfo().loadLabel(pm);
        } catch(Exception ex){
            Log.wf(THIS_FILE, ex, "Cannot determine application label");
        }

        if(pinfo != null) {
            rc = pinfo.versionCode;
            ac = pinfo.versionName;
        }

		String dev = "";
		try {
			dev = Build.MANUFACTURER + ";" + Build.DEVICE + ";" + Build.MODEL;
		} catch(Exception ex){

		}

        JSONObject object = new JSONObject();
        try {
            object.put("v", 1);
            object.put("p", Compatibility.isBlackBerry() ? "blackberry" : "android");
            object.put("pid", Build.VERSION.SDK_INT);
            object.put("dev", dev);
            object.put("rc", rc);
            object.put("ac", ac);
            object.put("info", info);
			object.put("locales", new JSONArray(Lists.newArrayList(Locale.getDefault())));

			TimeZone tz = TimeZone.getDefault();
			Calendar cal = GregorianCalendar.getInstance(tz);
			int offsetInMillis = tz.getOffset(cal.getTimeInMillis());
			String offset = String.format("GMT%s%02d%02d",
					(offsetInMillis >= 0 ? "+" : "-"), Math.abs(offsetInMillis / 3600000), Math.abs((offsetInMillis / 60000) % 60));
			object.put("tz", offset);
        } catch (JSONException e) {
            Log.ef(THIS_FILE, e, "Exception in writing JSON object");
        }

        return object.toString();
    }
	
	private static Locale locale(String isoCode) {
    	String[] codes = isoCode.split("_");
    	if(codes.length == 2) {
    		return new Locale(codes[0].toLowerCase(), codes[1].toUpperCase());
    	}else if(codes.length == 1){
    		return new Locale(codes[0].toLowerCase());
    	}
    	Log.ef(THIS_FILE, "Invalid locale %s", isoCode);
    	return null;
    }
	
	/**
	 * Set application language (locale) according to provided ISO code
	 * @param isoCode
	 * @param ctxt
	 * @return
	 */
	public static Locale setLocale(String isoCode, Context ctxt){
		Locale l = locale(isoCode);
		if (l==null){
			return null;
		}
		
		setLocale(l, ctxt);
		return l;
	}
	
	public static Locale setLocale(Locale l, Context ctxt){	 
		Resources res = ctxt.getResources(); 
		DisplayMetrics dm = res.getDisplayMetrics(); 
		Configuration conf = res.getConfiguration(); 
		conf.locale = l; 
		res.updateConfiguration(conf, dm);
		
		Log.vf(THIS_FILE, "Locale [%s] was set", l);
		return l;
	}
	
	/**
	 * Loads default language(locale) from shared preferences and applies this setting.
	 * 
	 * @param ctxt
	 * @return
	 */
	public static Locale loadDefaultLanguage(Context ctxt){
		PreferencesConnector preferencesConnector = new PreferencesConnector(ctxt);
		final String lang = preferencesConnector.getString(PhonexConfig.LANGUAGE, "auto");
		return setLocale(getLocale(lang), ctxt);
	}

	/**
	 * Loads default language(locale) from shared preferences and applies this setting.
	 * Throws no exception.
	 *
	 * @param ctxt
	 * @return
	 */
	public static Locale loadDefaultLanguageNoThrow(Context ctxt){
		try {
			return loadDefaultLanguage(ctxt);
		} catch(Throwable t){
			Log.e(THIS_FILE, "Exception when loading default language", t);
		}

		return null;
	}

	public static void storeDefaultLanguage(Context ctxt, final String lang){
		// Old way of storing language - supporting UI preferences.
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ctxt).edit();
		editor.putString(PhonexConfig.LANGUAGE, lang).apply();

		// Prefer thread-safe, process-safe preferences engine.
		PreferencesConnector preferencesConnector = new PreferencesConnector(ctxt);
		preferencesConnector.setString(PhonexConfig.LANGUAGE, lang);
	}

    /**
     * Translates language string to the locale.
     * If "auto" or null string is passed, default locale is returned.
     * @param lang
     * @return
     */
    public static Locale getLocale(String lang){
        Locale finalLocale = null;

        if ("auto".equals(lang) || TextUtils.isEmpty(lang) || String.valueOf(0).equals(lang)){
            finalLocale = Locale.getDefault();
        } else {
            finalLocale = locale(lang);
            Log.vf(THIS_FILE, "Detecting locale for lang [%s] is: %s", lang, finalLocale);
            if (finalLocale == null || String.valueOf(0).equals(finalLocale.toString())){
                finalLocale = Locale.getDefault();
            }
        }

        return finalLocale;
    }

	/**
	 * Get the link to the FAQ. If null or empty the link to FAQ is not displayed
	 * @return link to the FAQ
	 */
	public static String getFaqLink() {
		return "https://www.phone-x.net/?subweb=articlescat&acid=37"; //"http://phone-x.net";
	}

    /**
     * Returns trial link based on the default locale.
     * @return
     */
    public static String getTermsOfUseLink(){
        final String lang = Locale.getDefault().getLanguage();
        if(    "cs".equalsIgnoreCase(lang)  || "sk".equalsIgnoreCase(lang))
        {
            return "https://www.phone-x.net/cs/podpora/obchodni-a-licencni-podminky";
        } else {
            return "https://www.phone-x.net/en/support/terms-and-conditions";
        }
    }
	
	/**
	 * Do we want to display messaging feature
	 * @return true if the feature is enabled in this distribution
	 */
	public static boolean supportMessaging() {
		return true;
	}

	/**
	 * Shall we force the no mulitple call feature to be set to false
	 * @return true if we don't want to support multiple calls at all.
	 */
	public static boolean forceNoMultipleCalls() {
		return false;
	}

    /**
     * Get the SD card folder name.
     * This folder will be used to store call records, configs and logs
     * @return the name of the folder to use
     */
    public static String getSDCardFolder() {
        return "phonex";
    }
	
	public static boolean hasOpus(){
		return false;
	}
	
	public static String getStunServer(){
		return "stun.phone-x.net";
	}

	public static String getTurnServer() {
		return "turn.phone-x.net:3477";
	}
	
	public static boolean supportPhotos() {
	    return false;
	}
	
	public static boolean debuggingRelease(){
		return BuildConfig.DEBUG;
	}
	
	public static boolean enableLogs(){
		return debuggingRelease();
	}
	
	public static boolean useTLS(){
		return true;
	}
	
	public static boolean enableUpdateCheck(){
		return true;
	}
	
	public static boolean restrictSettings(){
		// in debug, allow debug settings
		return !debuggingRelease();
	}
	
	public static boolean enabledSipPresence(){
		return false;
	}

    public static boolean useZrtp(){
        return true;
    }

    public static boolean useSrtp(){
        return true;
    }

	public static boolean useVideo() {
		return debuggingRelease();
	}

	/**
	 * Returns capabilities supported by the current release.
	 * @return
	 */
	public static Set<String> getCapabilities(){
		Set<String> caps = new HashSet<>();
        caps.add(Constants.CAP_PROTOCOL_MESSAGES_2_2);
        caps.add(Constants.CAP_PUSH);
		return caps;
	}
}











