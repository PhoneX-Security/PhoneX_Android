package net.phonex.pub.a;

/**
 * Simple media state change listener, related to bluetooth headsets.
 * Created by dusanklinec on 17.12.15.
 */
public interface MediaDeviceChangeListener {
    void onMediaStateChanged(int status);
}
