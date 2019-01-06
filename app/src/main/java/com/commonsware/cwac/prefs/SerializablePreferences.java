package com.commonsware.cwac.prefs;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by ph4r05 on 7/16/14.
 */
public interface SerializablePreferences {
    public void exportPrefs(OutputStream os);
    public int importPrefs(InputStream is) throws ImportException;
}

