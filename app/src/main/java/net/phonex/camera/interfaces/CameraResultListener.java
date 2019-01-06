package net.phonex.camera.interfaces;

import net.phonex.ft.storage.FileStorageUri;

/**
 * Created by Matus on 23-Jul-15.
 */
public interface CameraResultListener {
    /**
     * Photo has been accepted, successfully saved and should be sent
     * @param uri
     * @return true if activity will be ending, false otherwise (enable controls on false)
     */
    public boolean photoAccepted(FileStorageUri uri);

    /**
     * User cancelled photo
     */
    public void photoCancelled();

    /**
     * Error when initializing camera, taking photo or saving photo
     */
    public void photoError();
}
