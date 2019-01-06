package net.phonex.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;

import net.phonex.core.Constants;
import net.phonex.db.entity.Thumbnail;
import net.phonex.pub.a.Compatibility;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.ft.storage.FileStorageUri;
import net.sqlcipher.Cursor;

//import org.acra.ACRA;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Misc util methods.
 * 
 * @author ph4r05
 *
 */
public class MiscUtils {
	private static final String TAG="MiscUtils";
    private static final String[] cameraFolders = new String[] { "dcim", "camera", "100andro", "100media" };
    private static final String DIRHASH_INVALID = "INVALID";
    private static final String DIRHASH_EMPTY = "EMPTY";
	
	private static Class<?> settingsGlobal;
	private static Integer WIFI_SLEEP_POLICY_DEFAULT;
	private static Integer WIFI_SLEEP_POLICY_NEVER;

    /**
     * When reporting custom exception to ACRA, use this method because it checks if build is in debug mode or not
     * @param throwable
     */
    public static void reportExceptionToAcra(Throwable throwable){
		// Allow acra also in debug - some customers may want to have debug version
//        if (!BuildConfig.DEBUG){
//            ACRA.getErrorReporter().handleException(throwable);
//        }
    }

	/**
	 * Returns string with length up to given length.
	 * 
	 * @param in
	 * @param maxlength
	 * @return
	 */
	public static String getStringMaxLen(String in, int maxlength){
		if (in==null) {
			return null;
		}
		
		final int slen = in.length();
		return in.substring(0, slen > maxlength ? maxlength : slen);
	}
	
	/**
	 * Returns true if given date is in this current day.
	 * @param when
	 * @return
	 */
	public static boolean isToday(Date when){
		Calendar cal = Calendar.getInstance();
		Calendar calLast = Calendar.getInstance();
		calLast.setTime(when);
		
		return (cal.get(Calendar.DAY_OF_YEAR) == calLast.get(Calendar.DAY_OF_YEAR))
				&& (cal.get(Calendar.YEAR) == calLast.get(Calendar.YEAR));
	}
	
	/**
	 * Returns scaled down pair of <width, height> with respect to the original
	 * size, keeping same aspect ratio, enforcing max long edge size as given.
	 * 
	 * @param width
	 * @param height
	 * @param maxLongEdge
	 * @return
	 */
	public static Pair<Integer, Integer> scaleDownImage(int width, int height, int maxLongEdge){
		int rWidth = 0;
		int rHeight = 0;
		
		// if width is longer or equal side
		if (width >= height){
			double ratio = (double)height / (double)width;
			rWidth = maxLongEdge;
			rHeight = (int) Math.ceil((double)maxLongEdge * ratio);
		} else {
			double ratio = (double)width / (double)height;
			rHeight = maxLongEdge;
			rWidth = (int) Math.ceil((double)maxLongEdge * ratio);
		}
		
		return new Pair<Integer, Integer>(rWidth, rHeight);
	}

	/**
	 * Decides which compression format to use. If bitmap has alpha channel, PNG is used to
	 * preserve its alpha, otherwise JPEG is used.
	 * @param bitmap
	 * @return
	 */
	private Bitmap.CompressFormat getFormat(Bitmap bitmap) {
		if (bitmap.hasAlpha()) {
			return Bitmap.CompressFormat.PNG;
		} else {
			return Bitmap.CompressFormat.JPEG;
		}
	}

	/**
	 * Create thumb from the file, using glide library.
	 * @param ctxt
	 * @param inputFile
	 * @param os
	 * @param maxLongEdge
	 * @param quality
	 * @return
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static boolean createThumb(Context ctxt, File inputFile, OutputStream os, int maxLongEdge, int quality) throws ExecutionException, InterruptedException {
		final FutureTarget<Bitmap> future = Glide.with(ctxt)
				.fromFile()
				.asBitmap()
				.load(inputFile)
				.centerCrop()
				.override(maxLongEdge, maxLongEdge)
				.into(maxLongEdge, maxLongEdge);

		final Bitmap bitmap = future.get();
		if (bitmap == null){
			return false;
		}

		// Write to the output stream.
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);

		Glide.clear(future);
		return true;
	}

	public static boolean createThumb(Context ctxt, InputStream is, OutputStream os, int maxLongEdge, int quality) throws ExecutionException, InterruptedException {
		final FutureTarget<Bitmap> future = Glide.with(ctxt)
				.load(is)
				.asBitmap()
				.centerCrop()
				.override(maxLongEdge, maxLongEdge)
				.into(maxLongEdge, maxLongEdge);

		final Bitmap bitmap = future.get();
		if (bitmap == null){
			return false;
		}

		// Write to the output stream.
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);

		Glide.clear(future);
		return true;
	}

    /**
     * Creates thumbnail of a given image.
     * Preserving aspect ratio, with maximum size of a longer edge as specified.
     * Writes JPEG encoded image of a given quality to a given output stream.
     *
     * Uses BitmapFactory to decode input stream, android declares support for:
     * JPEG, GIF, PNG, BMP, WebP.
     *
     * @param is
     * @param os
     * @param maxLongEdge
     * @param quality
     * @return
     */
	public static boolean createThumb(InputStream is, OutputStream os, int maxLongEdge, int quality){
		Bitmap imageBitmap = BitmapFactory.decodeStream(is);
		if (imageBitmap==null) return false;

		int width = imageBitmap.getWidth();
		int height = imageBitmap.getHeight();
		
		Pair<Integer, Integer> newDims = MiscUtils.scaleDownImage(width, height, maxLongEdge);
		imageBitmap = Bitmap.createScaledBitmap(imageBitmap, newDims.first, newDims.second, false);
		imageBitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);
		return true;
	}

    /**
     * Creates thumbnail of a given image.
     * Preserving aspect ratio, with maximum size of a longer edge as specified.
     * Returns JPEG encoded image of a given quality.
     *
     * @param is
     * @param maxLongEdge
     * @param quality
     * @return
     */
	public static byte[] createThumb(InputStream is, int maxLongEdge, int quality){
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		boolean success = createThumb(is, bos, maxLongEdge, quality);
		if (!success) return null;
		
		return bos.toByteArray();
	}

	public static boolean supportsGenerateThumbnail(String filename) {
		return FileUtils.isImage(filename) || FileUtils.isVideo(filename);
	}

	/**
	 *
	 * @param context
	 * @param uri FileStorageUri format
	 * @param imageView
	 * @param placeholder
	 * @param fromDB true if the image should be loaded from the Thumbnail table
	 */
	public static void loadIntoImageViewByUri(Context context, String uri, ImageView imageView, int placeholder, int error, boolean fromDB) {
		if (!fromDB) {
			//Log.vf(TAG, "loadIntoImageViewByUri, thumbnail does not exist, generating from uri %s", uri);
			Glide.with(context)
					.load(new FileStorageUri(uri))
					.centerCrop()
					.placeholder(placeholder)
					.crossFade(android.R.anim.fade_in, 150)
					.error(error)
					.diskCacheStrategy(DiskCacheStrategy.NONE) // NONE for security
					.dontAnimate()
					.into(imageView);
		} else {
			Thumbnail thumbnail = Thumbnail.getByUri(context.getContentResolver(), uri);
			if (thumbnail == null || thumbnail.getThumbnail() == null) {
				Log.vf(TAG, "loadIntoImageViewByUri, no existing thumbnail for uri %s", uri);
				return;
			}

			Log.vf(TAG, "loadIntoImageViewByUri, found existing thumbnail for uri %s", uri);
			Glide.with(context)
					.load(thumbnail.getThumbnail())
					.centerCrop()
					.placeholder(placeholder)
					.error(error)
					.dontAnimate()
					.into(imageView);
		}
	}

	public static void loadPlaceholderIntoImageView(Context context, ImageView imageView, int placeholder) {
		Glide.with(context)
				.load("")
				.centerCrop()
				.placeholder(placeholder)
				.error(placeholder)
				.dontAnimate()
				.into(imageView);
	}

	/**
	 * Converts byte array to hexa string.
	 * @param bytes
	 * @return
	 */
	public static String bytesToHex(byte[] bytes) {
		if (bytes==null) return "";
		return bytesToHex(bytes, bytes.length);
	}
	
	/**
	 * Converts byte array to hexa string.
	 * @param bytes
	 * @param maxLen
	 * @return
	 */
	public static String bytesToHex(byte[] bytes, int maxLen) {
		return bytesToHex(bytes, 0, maxLen);
	}

    public static String bytesToHex(byte[] bytes, int offset, int maxLen) {
        if (bytes==null) return "";
        final int ln = bytes.length - offset;
        final int cn = Math.min(maxLen, ln);

        StringBuilder sb = new StringBuilder();
        for ( int j = offset; j < (offset + cn); j++ ){
            sb.append(String.format("0x%02x ", bytes[j] & 0xff));
        }
        return sb.toString();
    }
	
	/**
	 * Closes closeable, absorbs & logs exception.
	 * @param c
	 */
	public static void closeSilently(String tag, Closeable c){
		if (c==null) return;
		try {
			c.close();
		} catch(Exception e){
			Log.ef(TAG, e, "Cannot close closable object. %s", tag);
		}
	}

    /**
     * Closes closeable, absorbs exception.
     * @param c
     */
    public static void closeSilently(Closeable c){
        if (c==null) return;
        try {
            c.close();
        } catch(Exception e){
            Log.ef(TAG, e, "Cannot close closable object");
        }
    }

    /**
     * Closes cursor, absorbs exception.
     * @param c
     */
    public static void closeCursorSilently(Cursor c){
        if (c==null) return;
        try {
            c.close();
        } catch(Exception e){
            Log.ef(TAG, e, "Cannot close cursor");
        }
    }

	/**
	 * Closes cursor silently & absorbs exception.
	 * Backward compatible with API versions when Cursor is not yet Closeable.
	 * @param c
     */
	public static void closeSilently(android.database.Cursor c){
		closeCursorSilently(c);
	}

    /**
     * Closes stream, absorbs exception.
     * @param c
     */
    public static void closeCursorSilently(android.database.Cursor c){
        if (c==null) return;
        try {
            c.close();
        } catch(Exception e){
            Log.ef(TAG, e, "Cannot close cursor");
        }
    }
	
	/**
	 * Returns Settings.Global class for reflective calls.
	 * Global is a nested class of the Settings, has to be done in a special way.
	 * 
	 * @return
	 */
	public static Class<?> getSettingsGlobal(){
		if (settingsGlobal!=null){
			return settingsGlobal;
		}
		
		try {
			Class<?> master = Class.forName("android.provider.Settings");
			Class<?>[] classes = master.getClasses();
			for(Class<?> cls : classes){
				if (cls==null) {
					continue;
				}
				
				if ("android.provider.Settings$Global".equals(cls.getName())){
					settingsGlobal = cls;
					return settingsGlobal;
				}
			}
			
			return null;
		} catch(Exception ex){
			Log.e(TAG, "Reflective call not successfull", ex);
		}
		
		return null;
	}
	
	/**
	 * Determines whether installing Android APKs from unknown sources is allowed.
	 * If not, update won't work.
	 * 
	 * @param ctxt
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static boolean isUnknownSourceInstallAllowed(Context ctxt){
		try {
			boolean unknownSource = false;
	        if (Build.VERSION.SDK_INT < 17) {
	            unknownSource = Settings.Secure.getInt(ctxt.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1;
	        } else {
				// Has to use reflection since API 17 is not directly reachable.
				// Original call would be:
				//   unknownSource = Settings.Global.getInt(ctxt.getContentResolver(), Settings.Global.INSTALL_NON_MARKET_APPS, 0) == 1;
				//
	        	try {					
					Class<?> c = getSettingsGlobal();
					Method m   = c.getMethod("getInt", new Class[] { ContentResolver.class, String.class, int.class });
					
					// Obtain constant value
					Field f = c.getField("INSTALL_NON_MARKET_APPS");
					final String constVal = (String) f.get(null);
					
					unknownSource = Integer.valueOf(1).equals((Integer) m.invoke(null, ctxt.getContentResolver(), constVal, 0));
	        	} catch(Exception e){
	        		Log.e("MiscUtils", "Exception in reflection invocation", e);
	        	}
	        }

	        return unknownSource;
		} catch(Exception e){
			Log.i(TAG, "Cannot determine if installing from unknown sources is allowed", e);
		}
		
		return false;
	}

    /**
     * @return default wifi sleep policy
     */
    @SuppressWarnings("deprecation")
	public static int getWifiSleepPolicyDefault() {
    	if (WIFI_SLEEP_POLICY_DEFAULT!=null){
    		return WIFI_SLEEP_POLICY_DEFAULT;
    	}
    	
    	// Load using reflection.
        if(Compatibility.isApiGreaterOrEquals(17)) {
        	try {
				Field f = getSettingsGlobal().getField("WIFI_SLEEP_POLICY_DEFAULT");
				WIFI_SLEEP_POLICY_DEFAULT = (Integer) f.get(null);
				
				return WIFI_SLEEP_POLICY_DEFAULT;
        	} catch(Exception e){
        		Log.e(TAG, "Problem with reflection", e);
        		return 0x00000000;
        	}
        } else {
        	WIFI_SLEEP_POLICY_DEFAULT = Settings.System.WIFI_SLEEP_POLICY_DEFAULT; 
            return WIFI_SLEEP_POLICY_DEFAULT;
        }
    }

    /**
     * @return wifi policy to never sleep
     */
    @SuppressWarnings("deprecation")
	public static int getWifiSleepPolicyNever() {
    	if (WIFI_SLEEP_POLICY_NEVER!=null){
    		return WIFI_SLEEP_POLICY_NEVER;
    	}
    	
    	// Load using reflection.
        if(Compatibility.isApiGreaterOrEquals(17)) {
        	try {
				Field f = getSettingsGlobal().getField("WIFI_SLEEP_POLICY_NEVER");
				WIFI_SLEEP_POLICY_NEVER = (Integer) f.get(null);
				
				return WIFI_SLEEP_POLICY_NEVER;
        	} catch(Exception e){
        		Log.e(TAG, "Problem with reflection", e);
        		return 0x00000002;
        	}
        } else {
        	WIFI_SLEEP_POLICY_NEVER = Settings.System.WIFI_SLEEP_POLICY_NEVER; 
            return WIFI_SLEEP_POLICY_NEVER;
        }
    }
    
    /**
     * Get the current wifi sleep policy
     * @param ctntResolver android content resolver
     * @return the current wifi sleep policy
     */
    @SuppressWarnings("deprecation")
	public static int getWifiSleepPolicy(ContentResolver ctntResolver) {
        if(Compatibility.isApiGreaterOrEquals(17)) {
        	try {
        		Class<?> c = getSettingsGlobal();
				Method m   = c.getMethod("getInt", new Class[] { ContentResolver.class, String.class, int.class });
				
				// Obtain constant value
				Field f = c.getField("WIFI_SLEEP_POLICY");
				final String constVal = (String) f.get(null);
				
				return (Integer) m.invoke(null, ctntResolver, constVal, getWifiSleepPolicyDefault());
        	} catch(Exception e){
        		Log.e(TAG, "Problem with reflection", e);
        		return 0;
        	}
            
        } else {
            return Settings.System.getInt(ctntResolver, Settings.System.WIFI_SLEEP_POLICY, Settings.System.WIFI_SLEEP_POLICY_DEFAULT);
        }
    }
    
    /**
     * Set wifi policy to a value
     * @param ctntResolver context content resolver
     * @param policy the policy to set
     */
    @SuppressWarnings("deprecation")
    public static void setWifiSleepPolicy(ContentResolver ctntResolver, int policy) {
        if(!Compatibility.isApiGreaterOrEquals(17)) {
            Settings.System.putInt(ctntResolver, Settings.System.WIFI_SLEEP_POLICY, policy);
        }else {
            // We are not granted permission to change that in api 17+
            //Settings.Global.putInt(ctntResolver, Settings.Global.WIFI_SLEEP_POLICY, policy);
        }
    }

    /**
     * Wrapper to set alarm at exact time
     * @see android.app.AlarmManager#setExact(int, long, PendingIntent)
     */
    public static void setExactAlarm(final AlarmManager alarmManager, final int alarmType, final long firstTime, final PendingIntent pendingIntent) {
        if(Compatibility.isApiGreaterOrEquals(19)) {
        	try {
	        	// Has to use reflection since API 19 is not directly reachable.
	            // Original call would be:
	            //   alarmManager.setExact(alarmType, firstTime, pendingIntent);
	            //
	            final Class<?>[] sig = new Class[] { int.class, long.class, PendingIntent.class }; 
	            Class<?> c = Class.forName("android.app.AlarmManager");
	            Method m   = c.getMethod("setExact", sig);
	
	            m.invoke(alarmManager, alarmType, firstTime, pendingIntent);
	            Log.vf(TAG, "ExactAlarm set, firstime=%s", firstTime);
        	} catch(Exception e){
        		Log.e(TAG, "Problem with setExact & reflection", e);
        	}
        }else {
            alarmManager.set(alarmType, firstTime, pendingIntent);
            Log.vf(TAG, "NormalAlarm set, firstime=%s", firstTime);
        }
    }

    /**
     * Stops HandlerThread by calling quit.
     * @param handlerThread
     * @param wait
     */
    public static void stopHandlerThread(HandlerThread handlerThread, boolean wait) {
        if (handlerThread == null) {
            return;
        }

        boolean quitOK = false;
        try {
            handlerThread.quit();
            quitOK = true;
        } catch (Exception e) {
            Log.d(TAG, "Exception: cannot quit thread", e);
        }

        if (!quitOK && handlerThread.isAlive() && wait) {
            try {
                handlerThread.join(500);
            } catch (Exception e) {
                Log.e(TAG, "Can t finish handler thread....", e);
            }
        }
    }

    /**
     * Useful when constructing POST HTTP requests
     * Number of parameters has to be even (pattern: name, value, name, value, ...)
     * 'name' cannot be null or empty
     * @param parameters
     * @return
     * @throws Exception
     */
    public static List<NameValuePair> makeNameValuePairs(String... parameters) throws Exception {
        if (parameters.length % 2 == 1){
            throw new Exception("Cannot prepare parameters - odd number of parameters + values is required");
        }
        List<NameValuePair> params = new ArrayList<NameValuePair>();

        String lastName = null;
        String lastValue = null;

        boolean isEven = true;
        for (int i=0; i<parameters.length; i++){
            if (isEven){
                lastName = parameters[i];
                if (lastName==null || lastName.equals("")){
                    throw new Exception("Cannot prepare parameters - name of the parameter cannot be null or empty string.");
                }
            } else {
                lastValue = parameters[i];
                if (lastValue != null){
                    params.add(new BasicNameValuePair(lastName, lastValue));
                }
            }
            isEven = !isEven;
        }
        return params;
    }

    public static List<NameValuePair> makeNameValuePairs(Map<String, String> map) throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        for (String key : map.keySet()){
            if (map.get(key) == null){
                continue;
            }
            params.add(new BasicNameValuePair(key, map.get(key)));
        }
        return params;
    }

    /**
     * Returns textual description of a device.
     * Dumps interesting android.os.Build.* constants.
     * @return
     */
    public final static StringBuilder getDeviceInfo() {
        final StringBuilder b = new StringBuilder();
        b.append("Device description:")
         .append("\nandroid.os.Build.BOARD: ")
                .append(Build.BOARD)
         .append("\nandroid.os.Build.BRAND: ")
                .append(Build.BRAND)
         .append("\nandroid.os.Build.DEVICE: ")
                .append(Build.DEVICE)
         .append("\nandroid.os.Build.ID: ")
                .append(Build.ID)
         .append("\nandroid.os.Build.MODEL: ")
                .append(Build.MODEL)
         .append("\nandroid.os.Build.PRODUCT: ")
                .append(Build.PRODUCT)
         .append("\nandroid.os.Build.TAGS: ")
                .append(Build.TAGS)
         .append("\nandroid.os.Build.CPU_ABI: ")
                .append(Build.CPU_ABI)
         .append("\nandroid.os.Build.VERSION.INCREMENTAL: ")
                .append(Build.VERSION.INCREMENTAL)
         .append("\nandroid.os.Build.VERSION.RELEASE: ")
                .append(Build.VERSION.RELEASE)
         .append("\nandroid.os.Build.VERSION.SDK_INT: ")
                .append(Build.VERSION.SDK_INT)
         .append("\n");
        return b;
    }

    /**
     * Translates single character to the NATO alphabet.
     * @param c
     * @return
     */
    public static String getNatoAlphabet(char c){
        switch(c){
            case 'a': return "Alfa";
            case 'b': return "Bravo";
            case 'c': return "Charlie";
            case 'd': return "Delta";
            case 'e': return "Echo";
            case 'f': return "Foxtrot";
            case 'g': return "Golf";
            case 'h': return "Hotel";
            case 'i': return "India";
            case 'j': return "Juliett";
            case 'k': return "Kilo";
            case 'l': return "Lima";
            case 'm': return "Mike";
            case 'n': return "November";
            case 'o': return "Oscar";
            case 'p': return "Papa";
            case 'q': return "Quebec";
            case 'r': return "Romeo";
            case 's': return "Sierra";
            case 't': return "Tango";
            case 'u': return "Uniform";
            case 'v': return "Victor";
            case 'w': return "Whiskey";
            case 'x': return "Xray";
            case 'y': return "Yankee";
            case 'z': return "Zulu";
            case '0': return "0";
            case '1': return "1";
            case '2': return "2";
            case '3': return "3";
            case '4': return "4";
            case '5': return "5";
            case '6': return "6";
            case '7': return "7";
            case '8': return "8";
            case '9': return "9";
        }

        return null;
    }

    /**
     * Tries to determine paths where external storage is mounted.
     * @return
     */
    public static Set<String> getExternalMounts() {
        // Get external directory.
        String extDir = null;
        String extDirCannon = null;
        String extDirHash = null;
        java.security.MessageDigest hash = null;
        try {
            hash = java.security.MessageDigest.getInstance("MD5");
            File extDcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            if (extDcimDir.exists()){
                extDcimDir = extDcimDir.getParentFile();
                extDir = extDcimDir.getAbsolutePath();
                extDirCannon = getCannonicalPath(extDcimDir);
                extDirHash = hashDirectory(extDcimDir, hash);
                Log.vf(TAG, "External emulated storage: abs=[%s] cannon=[%s]", extDir, extDirCannon);
            }
        } catch(Exception ex){
            Log.e(TAG, "DCIM not found.", ex);
        }

        // Get mount points.
        final HashSet<String> out = new HashSet<String>();
        final HashSet<String> canonSet = new HashSet<String>();
        final HashSet<String> hashSet = new HashSet<String>();
        final ArrayList<String> candidatePaths = new ArrayList<String>(8);
        canonSet.add(extDirCannon);
        hashSet.add(extDirHash);

        final String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = readMounts();
        Log.vf(TAG, "Mount output: [%s]", s);

        // Parse output of mount.
        try {
            final String[] lines = s.split("\n");
            for (String line : lines) {
                if (line.toLowerCase(Locale.US).contains("asec")) {
                    continue;
                }

                if (!line.matches(reg)) {
                    continue;
                }

                String[] parts = line.split(" ");
                for (String part : parts) {
                    if (!part.startsWith("/")) {
                        continue;
                    }

                    if (!part.toLowerCase(Locale.US).contains("vold")) {
                        candidatePaths.add(part);
                        break;
                    }
                }
            }
        } catch(Exception ex){
            Log.e(TAG, "Exception: cannot find mounted storage", ex);
        }

        // Another workaround - system environment variables
        try {
            List<String> envPaths = getEnvMounts();
            candidatePaths.addAll(envPaths);

            for(String part : candidatePaths){
                File fPart = new File(part);
                try {
                    fPart = fPart.getCanonicalFile();
                } catch(Exception ex){
                    Log.e(TAG, "Cannot obtain cannonical file representation", ex);
                }

                final String absPart = fPart.getAbsolutePath();

                // If it is the same as DCIMs, skip it.
                if (fPart.exists()==false
                        || fPart.canRead()==false
                        || part.equalsIgnoreCase(extDir)
                        || part.equalsIgnoreCase(extDirCannon)
                        || absPart.equalsIgnoreCase(extDir)
                        || absPart.equalsIgnoreCase(extDirCannon)){
                    continue;
                }

                // Compute canonical path. If there is already the folder pointing to the same
                // destination, do not add it.
                final String cannonPath = getCannonicalPath(fPart);
                if (!TextUtils.isEmpty(cannonPath) && canonSet.contains(cannonPath)){
                    continue;
                }

                // Hash directory.
                final String dirHash = hashDirectory(fPart, hash);
                if (hashSet.contains(dirHash) || DIRHASH_EMPTY.equals(dirHash) || DIRHASH_INVALID.equals(dirHash)){
                    continue;
                }

                out.add(absPart);
                hashSet.add(dirHash);
                if (!TextUtils.isEmpty(cannonPath)) {
                    canonSet.add(cannonPath);
                }

                Log.vf(TAG, "External mount added: abs=[%s] cannon=[%s] exists=%s hash=[%s]", absPart, cannonPath, fPart.exists(), dirHash);
            }

        } catch(Exception ex){
            Log.e(TAG, "Problem reading external storage with sys vars", ex);
        }

        return out;
    }

    /**
     * Hashes file directory according to its contents.
     * Hash is computed by using first 10 and last 10 files from directory listing with
     * sort order defined by date of last modification.
     *
     * @param directory Directory to hash according its contents.
     * @param hash Hashing algorithm to use.
     * @return
     */
    public static String hashDirectory(File directory, java.security.MessageDigest hash){
        if (directory==null){
            return "";
        }

        if (directory.exists()==false){
            return DIRHASH_INVALID;
        }

        final File[] files = directory.listFiles();
        if (files==null || files.length==0){
            return DIRHASH_EMPTY;
        }

        try {
            // Needs to be sorted, to guarantee the same result, sort by modification date.
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File lhs, File rhs) {
                    final long lm = lhs.lastModified();
                    final long rm = rhs.lastModified();
                    if (lm < rm) return -1;
                    if (lm > rm) return 1;
                    return lhs.compareTo(rhs);
                }
            });

            // Take first 10 and last 10.
            hash.reset();
            final int len = files.length;
            final boolean over20 = len > 20;
            final int limit = over20 ? 20 : len;
            for(int i=0, c=0; i<limit; i++, c++){
                if (over20 && i==11){
                    c = len-10;
                }

                final File cFile = files[c];
                final StringBuilder sb = new StringBuilder(64);
                sb.append(cFile.getName()).append(";").append(cFile.lastModified());
                hash.update(sb.toString().getBytes("UTF-8"));
            }

            final byte[] resHash = hash.digest();
            hash.reset();

            return Base64.encodeBytes(resHash);
        } catch (Exception e) {
            Log.e(TAG, "Exception in directory hashing", e);
        }

        return DIRHASH_INVALID;
    }

    /**
     * Reads environment variables for external and secondary storage.
     * @return
     */
    public static ArrayList<String> getEnvMounts(){
        try {
            // Primary physical SD-CARD (not emulated)
            final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");
            // All Secondary SD-CARDs (all exclude primary) separated by ":"  File.pathSeparator
            final String rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE");
            Log.vf(TAG, "RawExt[%s] SndExt[%s]", rawExternalStorage, rawSecondaryStoragesStr);

            final ArrayList<String> envPaths = new ArrayList<String>(8);
            if (!TextUtils.isEmpty(rawExternalStorage)) {
                envPaths.add(rawExternalStorage);
            }

            if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
                final String[] sndFolders = rawSecondaryStoragesStr.split(File.pathSeparator);
                for (String sndFolder : sndFolders) {
                    if (TextUtils.isEmpty(sndFolder)) {
                        continue;
                    }

                    envPaths.add(sndFolder);
                }
            }

            return envPaths;
        } catch(Exception ex){
            Log.e(TAG, "Exception in extracting env mounts", ex);
        }

        return new ArrayList<String>();
    }

    /**
     * Read all mount points.
     * @return
     */
    public static String readMounts(){
        StringBuilder sbuild = new StringBuilder();
        InputStream is = null;
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount").redirectErrorStream(true).start();
            process.waitFor();
            is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                sbuild.append(new String(buffer));
            }
            s = sbuild.toString();
        } catch (final Exception e) {
            Log.e(TAG, "Exception: cannot run mount command", e);
        } finally {
            closeSilently("mount", is);
        }

        // Try to read /proc/mounts
        if (TextUtils.isEmpty(s)){
            try {
                Log.v(TAG, "mount executed with empty output, reading /proc/mounts");
                sbuild = new StringBuilder();
                Scanner scanner = new Scanner(new File("/proc/mounts"));
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    sbuild.append(line).append("\n");
                }

                s = sbuild.toString().trim();
            } catch(Exception ex){
                Log.e(TAG, "Exception: cannot read /etc/mounts", ex);
            }
        }

        return s;
    }

    /**
     * Returns canonical path of a file.
     * If error occurrs, null is returned.
     *
     * @param fl
     * @return
     */
    public static String getCannonicalPath(File fl){
       if (fl==null){
           return null;
       }

        try {
            return fl.getCanonicalPath();
        } catch(Exception ex){
            Log.ef(TAG, ex, "Cannot compute cannonical path. File[%s]", fl.toString());
        }

        return null;
    }

    public static byte[] decompress(byte[] bytes) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        GZIPInputStream gis = new GZIPInputStream(is);

        byte[] buffer = new byte[8192];
        int length;
        while ((length = gis.read(buffer, 0, 8192)) != -1){
            os.write(buffer, 0, length);
        }
        os.close();
        gis.close();

        return os.toByteArray();
    }

    public static byte[] compressToBytes(byte[] bytes) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length);
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(bytes);
        gos.close();
        os.close();
        return os.toByteArray();
    }

	/**
     * Class represents camera directory.
     */
    public static class CameraDirectory {
        public int mediaFilesCount=0;
        public File path;
    }

    /**
     * Tries to find directory with photos in the DCIM folder.
     * @param dcim
     * @return
     */
    public static CameraDirectory getCameraDirectory(File dcim) {
        CameraDirectory cd = new CameraDirectory();

        // Find some directory with images under DCIM.
        FilenameFilter imageFilter = new MediaFileFilter();

        // Load list of files in this directory.
        int dcimSize = dcim.list(imageFilter).length;

        // Try to find in typical subdirectories.
        int maxDcimImages = dcimSize;
        File resultDirectory = dcim;

        String[] possibleSubdirs = dcim.list(new CameraFolderFilter());
        for(String subDir : possibleSubdirs){
            File subFile = new File(dcim, subDir);
            if (subFile.exists()==false || subFile.isDirectory()==false){
                continue;
            }

            int curImgSize = subFile.list(imageFilter).length;
            if (curImgSize > maxDcimImages){
                maxDcimImages = curImgSize;
                resultDirectory = subFile;
            }
        }

        cd.mediaFilesCount = maxDcimImages;
        cd.path = resultDirectory;

        return cd;
    }

    /**
     * Tries to determine directory where images from camera are stored.
     * @return
     */
    public static CameraDirectory getPhotoDirectory(){
        File dcim = null;
        try {
            dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        } catch(Exception ex){
            Log.e(TAG, "DCIM not found.", ex);
        }

        if (dcim==null || dcim.exists()==false){
            try {
                dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            } catch(Exception ex){
                Log.e(TAG, "DCIM not found.", ex);
            }
        }

        if (dcim==null || dcim.exists()==false || dcim.isDirectory()==false){
            return null;
        }

        return getCameraDirectory(dcim);
    }

    /**
     * Tries to detect external camera directory.
     * @param externalMounts Path to the external directories.
     * @return
     */
    public static CameraDirectory detectExternalCameraDirectory(Set<String> externalMounts){
        if (externalMounts==null || externalMounts.isEmpty()){
            return null;
        }

        CameraDirectory cdToReturn = null;
        for(String external : externalMounts){
            try {
                File extFile = new File(external);
                if (extFile.exists()==false){
                    Log.wf(TAG, "Non-existing external folder [%s]", external);
                    continue;
                }

                String[] possibleDcims = extFile.list(new CameraFolderFilter());
                if (possibleDcims==null || possibleDcims.length==0){
                    continue;
                }

                for(String posDcim : possibleDcims){
                    Log.vf(TAG, "possibleDcim: %s", posDcim);
                    File dcimFile = new File(extFile, posDcim);
                    if (dcimFile.exists()==false){
                        continue;
                    }

                    CameraDirectory curCamDir = getCameraDirectory(dcimFile);
                    if (curCamDir==null){
                        continue;
                    }

                    if (cdToReturn==null
                            || curCamDir.mediaFilesCount > cdToReturn.mediaFilesCount){
                        cdToReturn = curCamDir;
                        continue;
                    }
                }

            } catch(Exception ex){
                Log.e(TAG, "Exception in detecting image files", ex);
            }
        }

        return cdToReturn;
    }

    /**
     * Filename filter that accepts only image and video files.
     */
    public static class MediaFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String filename) {
            File sel = new File(dir, filename);
            return (sel.isFile()
                    && !sel.isHidden()
                    && (FileUtils.isImage(sel.getName()) || FileUtils.isVideo(sel.getName())));
        }
    }

    /**
     * Filename filter for searching camera folders.
     */
    public static class CameraFolderFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String filename) {
            File sel = new File(dir, filename);
            if (!sel.isDirectory()) {
                return false;
            }

            for(String tmpdir : cameraFolders){
                if (tmpdir.equalsIgnoreCase(filename)){
                    return true;
                }
            }

            return false;
        }
    }

    public static String safeToString(Object obj) {
        if (obj == null){
            return "@null";
        } else {
            return obj.toString();
        }
    }

    public static String getUsernamePathKey(String username) {
        // Normalization / Trim
        if (username == null){
            username = "";
        }

        username = username.trim();
        String[] parts = username.split("@");
        String effectiveUname = username;
        if (parts == null || parts.length!=2){
            effectiveUname = username + "@phone-x.net";
        }

        String toReturn = "uk_none";

        byte[] inpBytes = null;
        try {
            String ukeyBase = MessageDigest.hashHexSha256(effectiveUname.getBytes("UTF-8"));
            toReturn = "uk_" + ukeyBase.substring(0, 24);
        } catch (Exception ex){

        }

        return toReturn;
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
    }

    public static boolean delete(File f){
        if (f == null){
            return false;
        }

        boolean succ = false;
        try {
            succ = f.delete();
        } catch(Exception e){

        }

        return succ;
    }

    public static boolean isEmpty(String str){
        return str == null || str.length() == 0 || str.trim().length() == 0;
    }

	public static Integer collectionSize(Collection<?> col){
		if (col == null) return null;
		return col.size();
	}

	public static boolean fileExistsAndIsAfile(final String path){
		if (MiscUtils.isEmpty(path)){
			return false;
		}

		try {
			final File fl = new File(path);
			return fl.exists() && fl.isFile();

		} catch(Exception e){
			Log.e(TAG, "Exception in checking file existence", e);
			return false;
		}
	}

	public static boolean fileExistsAndIsAfile(final File file){
		if (file == null){
			return false;
		}

		try {
			return file.exists() && file.isFile();

		} catch(Exception e){
			Log.e(TAG, "Exception in checking file existence", e);
			return false;
		}
	}

	public static long getFileLength(String path){
		try {
			return new File(path).length();
		} catch(Exception ex){
			Log.e(TAG, "Cannot determine file length");
			return -1;
		}
	}

	public static boolean deleteFile(String path){
		try {
			return new File(path).delete();
		} catch(Exception ex){
			Log.e(TAG, "Cannot determine file length");
			return false;
		}
	}

	public static byte[] marshall(Parcelable parceable) {
		Parcel parcel = Parcel.obtain();
		parceable.writeToParcel(parcel, 0);
		byte[] bytes = parcel.marshall();
		parcel.recycle(); // not sure if needed or a good idea
		return bytes;
	}

	public static Parcel unmarshall(byte[] bytes) {
		Parcel parcel = Parcel.obtain();
		parcel.unmarshall(bytes, 0, bytes.length);
		parcel.setDataPosition(0); // this is extremely important!
		return parcel;
	}

	public static <T> T unmarshall(byte[] bytes, Parcelable.Creator<T> creator) {
		Parcel parcel = unmarshall(bytes);
		return creator.createFromParcel(parcel);
	}

	/**
	 * Ment to configure intent so only our app can hear it.
	 * @param ctxt
	 * @param intent
	 */
	public static void configureIntent(final Context ctxt, final Intent intent){
		try {
			intent.setPackage(ctxt.getApplicationContext().getPackageName());
		} catch(Exception ex){
			Log.e(TAG, "Could not confiure intent, exception", ex);
		}
	}

	/**
	 * Configures intent and send it securely.
	 * Sets intent package and broadcasts it with phonex permission.
	 * @param ctxt
	 * @param intent
	 */
	public static void sendBroadcast(final Context ctxt, final Intent intent){
		try {
			final Context appCtxt = ctxt.getApplicationContext();
			intent.setPackage(appCtxt.getPackageName());
			appCtxt.sendBroadcast(intent, Constants.PERMISSION_PHONEX);

		} catch(Exception ex){
			Log.e(TAG, "Could not configure intent, exception", ex);
		}
	}

	/**
	 * Registers broadcast receiver with phonex permission.
	 *
	 * @param ctxt
	 * @param receiver
	 * @param filter
	 */
	public static void registerReceiver(final Context ctxt, final BroadcastReceiver receiver, final IntentFilter filter){
		registerReceiver(ctxt, receiver, filter, null);
	}

	/**
	 * Registers broadcast receiver with phonex permission on given handler.
	 * @param ctxt
	 * @param receiver
	 * @param filter
	 * @param handler
	 */
	public static void registerReceiver(final Context ctxt, final BroadcastReceiver receiver, final IntentFilter filter, final Handler handler){
		ctxt.registerReceiver(receiver, filter, Constants.PERMISSION_PHONEX, handler);
	}

	/**
	 * Generates an integer using secure random.
	 * @return
	 */
	public static int randomInt() {
		return new SecureRandom().nextInt();
	}

	public static boolean isValidEmailOrEmptyString(CharSequence target) {
		if ("".equals(target)){
			return true;
		}
		return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
	}
}
