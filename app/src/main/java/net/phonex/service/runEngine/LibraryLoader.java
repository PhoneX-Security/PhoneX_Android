package net.phonex.service.runEngine;

import android.content.Context;
import android.util.Log;

import net.phonex.pub.a.Compatibility;
import net.phonex.util.android.JNILibsManager;

/**
 * Loads all necessary external libraries.
 * Should be called by a dedicated class loader.
 * 
 * @author ph4r05
 */
public class LibraryLoader {
	private static final String THIS_FILE="LibraryLoader";
	
	/**
	 * Loads all needed external libraries.
	 */
	public static void loadLibraries(Context ctxt){
		try {
            // Try to load the natives
        	Log.v(THIS_FILE, "Initializing native crash handler.");
        	System.loadLibrary(JNILibsManager.STD_LIB_NAME);
        	
        	// do we have libcorkscrew? If we have Android without native one
        	try {
	        	if (!Compatibility.isApiGreaterOrEquals(16)){
	        		System.loadLibrary(JNILibsManager.CORK_NAME);
	        		Log.v(THIS_FILE, "Local libcorkscrew loaded");
	        	}
        	} catch(Exception e){
        		Log.v(THIS_FILE, "Problem with loading local libcorcscrew");
        	}
        	
            System.loadLibrary(JNILibsManager.CRASH_NAME);
            Log.v(THIS_FILE, "Native crash libs just loaded");
            
//            new NativeCrashHandler().registerForNativeCrash(ctxt);
            Log.v(THIS_FILE, "Native crash handler initialized");
        } catch (UnsatisfiedLinkError e) {
            // If it fails we probably are running on a special hardware
            Log.e(THIS_FILE, "We have a problem with the current stack.... Not yet Implemented", e);
        } catch (Exception e) {
            Log.e(THIS_FILE, "We have a problem with the current stack....", e);
        }
		
		// Load SIP STACK.
		System.loadLibrary(JNILibsManager.STD_LIB_NAME);
        System.loadLibrary(JNILibsManager.STACK_NAME);
        Log.v(THIS_FILE, "Libraries loaded");
	}
}
