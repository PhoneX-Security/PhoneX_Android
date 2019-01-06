package net.phonex.pref;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Helper class to provide convenient methods to manipulate with SharedPreferences
 */
public class PreferencesHelper {
	private static final String TAG = "PreferencesHelper";
	protected SharedPreferences prefs;
	protected ContentResolver resolver;
	protected Context context;
	protected Editor sharedEditor;
    
	public PreferencesHelper() {
		
	}
	
	public PreferencesHelper(Context aContext) {
		context = aContext;
		resolver = aContext.getContentResolver();
	}
	
	public PreferencesHelper(Context aContext, SharedPreferences aPrefs) {
		context = aContext;
		resolver = aContext.getContentResolver();
		prefs = aPrefs;
	}
	
	/**
	 * Enter in edit mode
	 * To use for bulk modifications
	 */
	public void editStart() {
	    sharedEditor = prefs.edit();
	}
	
	/**
	 * Leave edit mode
	 */
	public void editCommit() {
	    if(sharedEditor != null) {
	        sharedEditor.commit();
	        sharedEditor = null;
	    }
	}
	
	public String getString(String key) {
		return prefs.getString(key, (String) null);
	}
	
	public Boolean getBoolean(String key) {
		if(prefs.contains(key)) {
		    return prefs.getBoolean(key, false);
		}
		return null;
	}
	
	public Float getFloat(String key) {
		if(prefs.contains(key)) {
		    return prefs.getFloat(key, 0.0f); 
		}
		return null;
	}

    public Long getLong(String key) {
        if(prefs.contains(key)) {
            return prefs.getLong(key, 0l);
        }
        return null;
    }

    public Integer getInteger(String key) {
		if(prefs.contains(key)) {
		    return prefs.getInt(key, 0); 
		}
		return null;
	}
	
	public void setString(String key, String value) {
	    if(sharedEditor == null) {
    		Editor editor = prefs.edit();
    		editor.putString(key, value);
    		editor.commit();
	    }else {
	        sharedEditor.putString(key, value);
	    }
	}

    public void deleteString(String key) {
        if(sharedEditor == null) {
            Editor editor = prefs.edit();
            editor.remove(key);
            editor.commit();
        }else {
            sharedEditor.remove(key);
        }
    }
	
	public void setBoolean(String key, boolean value) {
	    if(sharedEditor == null) {
    		Editor editor = prefs.edit();
    		editor.putBoolean(key, value);
    		editor.commit();
	    }else {
	        sharedEditor.putBoolean(key, value);
	    }
	}
	
	public void setFloat(String key, float value) {
	    if(sharedEditor == null) {
    		Editor editor = prefs.edit();
    		editor.putFloat(key, value);
    		editor.commit();
	    }else {
	        sharedEditor.putFloat(key, value);
	    }
	}

    public void setLong(String key, long value) {
        if(sharedEditor == null) {
            Editor editor = prefs.edit();
            editor.putLong(key, value);
            editor.commit();
        }else {
            sharedEditor.putLong(key, value);
        }
    }
	
	public void setInteger(String key, int value) {
	    if(sharedEditor == null) {
    		Editor editor = prefs.edit();
    		editor.putInt(key, value);
    		editor.commit();
	    }else {
	        sharedEditor.putFloat(key, value);
	    }
	}
	
	/**
	 * Returns true if there is an record with given key in shared preferences.
	 * Non-locally cached.
	 * @param key
	 * @return
	 */
	public boolean hasPreferenceKey(String key){
		return prefs.contains(key);
	}
	

    /**
     * Retrieve the context used for this preference wrapper
     * 
     * @return an android context
     */
	public Context getContext() {
		return context;
	}

	public SharedPreferences getPrefs() {
		return prefs;
	}

	public ContentResolver getResolver() {
		return resolver;
	}
}
