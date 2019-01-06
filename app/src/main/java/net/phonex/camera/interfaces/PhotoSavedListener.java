package net.phonex.camera.interfaces;

import net.phonex.ft.storage.FileStorageUri;

/**
 * Created by Matus on 21-Jul-15.
 */
public interface PhotoSavedListener {
    public void photoSaved(FileStorageUri uri);
    public void photoError();
    public void photoProgress(Integer percentageDone);
}
