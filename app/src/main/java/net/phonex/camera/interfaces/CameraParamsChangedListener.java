package net.phonex.camera.interfaces;

/**
 * Created by Matus on 21-Jul-15.
 */
public interface CameraParamsChangedListener {
    public void onResolutionChanged(int id, boolean frontCamera);
    public void onFlashModeChanged(int id, boolean frontCamera);
    public void onCameraInstanceChanged(boolean useFrontCamera);
    public void onFocusModeChanged(int id, boolean frontCamera);
}
