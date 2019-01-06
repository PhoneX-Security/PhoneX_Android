package net.phonex.camera.interfaces;

import android.hardware.Camera;

/**
 * Created by Matus on 21-Jul-15.
 */
public interface FocusCallback {
    public void onFocused(Camera camera);
    /**
     * Called when surface has been created and controls can be displayed and splash screen hidden
     */
    public void onCameraReady();
    /**
     * Called when surface has been destroyed and controls splash screen should be displayed
     */
    public void onCameraBusy();
    public void onSurfaceDestroyed();
}
