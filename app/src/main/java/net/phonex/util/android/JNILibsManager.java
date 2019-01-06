package net.phonex.util.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import net.phonex.pref.PreferencesConnector;
import net.phonex.util.Log;

import java.io.File;
import java.lang.reflect.Field;

public class JNILibsManager {
	private static final String THIS_FILE = "NativeLibMgr";
    public static final String STD_LIB_NAME = "stlport_shared";
	public static final String STACK_NAME = "sipstackjni";
	public static final String CRASH_NAME = "crash";
	public static final String CORK_NAME = "localcorkscrew";
	public static final String OPUS_NAME = "pj_opus_codec";

    public static final String OPENSL_LIB = "libpj_opensl_dev.so";
    public static final String ZRTP_GLUE_LIB = "libzrtpglue.so";
    public static final String ZRTP_CORE_LIB = "libzrtpcpp.so";
	
	public static File getJNILibFile(Context ctx, String libName) {
		PackageInfo packageInfo = PreferencesConnector.getCurrentPackageInfos(ctx);
		if(packageInfo != null) {
			ApplicationInfo appInfo = packageInfo.applicationInfo;
			File f = getPackageJNILibFile(appInfo, libName, true);
			return f;
		}
		
		// This is the very last fallback method
		return new File(ctx.getFilesDir().getParent(), "lib" + File.separator + libName);
	}
	
	public static File getPackageJNILibFile(ApplicationInfo appInfo, String libName, boolean allowFallback) {
		Log.vf(THIS_FILE, "Dir %s", appInfo.dataDir);

        try {
            Field f = ApplicationInfo.class.getField("nativeLibraryDir");
            File nativeFile = new File((String) f.get(appInfo), libName);
            if(nativeFile.exists()) {
                Log.v(THIS_FILE, "Found native lib using clean way");
                return nativeFile;
            }
        } catch (Exception e) {
            Log.e(THIS_FILE, "Cant get field for native lib dir", e);
        }

		if(allowFallback) {
			return new File(appInfo.dataDir, "lib" + File.separator + libName);
		}else {
			return null;
		}
	}
	
	public static boolean isDebuggableApp(Context ctx) {
		try {
			PackageInfo pinfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
			return ( (pinfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
		} catch (NameNotFoundException e) {
			// Should not happen....or something is wrong with android...
			Log.e(THIS_FILE, "Not possible to find self name", e);
		}
		return false;
	}

}
