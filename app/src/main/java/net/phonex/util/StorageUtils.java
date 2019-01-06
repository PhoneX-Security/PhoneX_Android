package net.phonex.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import net.phonex.R;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pref.PreferencesManager;

import java.io.File;

/**
 * Created by Matus on 7/7/2015.
 */
public class StorageUtils {
    private static String TAG = "StorageUtils";

    public static boolean existsUri(String uriString, ContentResolver cr) {
        if (uriString == null) {
            throw new IllegalArgumentException("uri == null");
        }
        FileStorageUri uri = new FileStorageUri(uriString);

        // speed up the test by first checking the physical file
        if (!(new File(uri.getAbsolutePath()).exists())) {
            return false;
        }

        if (uri.isSecureStorage()) {
            try {
                FileStorage fileStorage = FileStorage.getFileStorageByUri(uri.getUri(), cr);
                return fileStorage != null;
            } catch (FileStorage.FileStorageException e) {
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean isSecureStorage(String uriString) {
        Uri uri = Uri.parse(uriString);
        return FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme());
    }

    public static boolean isSecureStorage(Uri uri) {
        return FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme());
    }

    public static boolean deleteFile(ContentResolver contentResolver, Uri uri, boolean deleteCloneIfExists) {
        boolean isSecure = FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme());
        if (isSecure) {
            FileStorage fileStorage;
            try {
                fileStorage = FileStorage.getFileStorageByUri(uri, contentResolver);
            } catch (FileStorage.FileStorageException e) {
                Log.wf(TAG, "Exception in deleting file", e);
                return false;
            }
            return fileStorage != null && fileStorage.delete(contentResolver, deleteCloneIfExists);
        } else {
            String fileSystemName = uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME);
            String path = uri.getPath();
            File file = new File(path, fileSystemName);

            String where = String.format("%s=?", ReceivedFile.FIELD_STORAGE_URI);
            String[] args = new String[]{String.format("%s", uri)};
            int deleted = contentResolver.delete(ReceivedFile.URI, where, args);
            Log.df(TAG, "Deleted [%d] records from ReceivedFile where uri [%s] (file from normal storage)", deleted, uri);

            return file.exists() && file.delete();
        }
    }

    public static String getClonePathIfExists(FileStorageUri info, ContentResolver contentResolver) {
        if (!info.isSecureStorage()) return null;
        try {
            FileStorage fileStorage = FileStorage.getFileStorageByUri(info.getUri(), contentResolver);
            if (fileStorage == null) return null;
            return fileStorage.getCleartextPathFixInconsistency(contentResolver);
        } catch (FileStorage.FileStorageException e) {
            Log.wf(TAG, "Exception in deleting file", e);
            return null;
        }
    }

    /**
     * Recursively delete contents of a given directory.
     *
     * @param directory
     * @return
     */
    public static boolean deleteDirectoryContents(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            // illegal argument
            return false;
        }

        boolean allDeleted = true;

        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    // recursion, file system should not be so deep to cause SO
                    allDeleted &= deleteDirectoryContents(child);
                }
                allDeleted &= child.delete();
            }
        } else {
            // could not list children
            return false;
        }
        return allDeleted;
    }

    public static String getLocalizedFileName(Context context, File file) {
        if (file != null) {
            if (file.isDirectory()) {
                if (PreferencesManager.DOWNLOAD_FOLDER.equals(file.getName())) {
                    return context.getString(R.string.filepicker_secure_storage);
                }
                if (PreferencesManager.RECEIVED_FILES_FOLDER.equals(file.getName())) {
                    return context.getString(R.string.received_files);
                }
                if (PreferencesManager.SECURE_CAMERA_FOLDER.equals(file.getName())) {
                    return context.getString(R.string.camera_secure);
                }
            }

            return file.getName();
        }
        return null;
    }

    public static int getLocalizedFileImageResource(File file) {
        if (file != null) {
            if (PreferencesManager.DOWNLOAD_FOLDER.equals(file.getName())) {
                return R.drawable.svg_encrypted;
            }
            if (PreferencesManager.RECEIVED_FILES_FOLDER.equals(file.getName())) {
                return R.drawable.ic_folder_black_48px;
            }
            if (PreferencesManager.SECURE_CAMERA_FOLDER.equals(file.getName())) {
                return R.drawable.ic_photo_camera_black_48px;
            }
            if (file.isDirectory()) {
                return R.drawable.ic_folder_black_48px;
            } else {
                return R.drawable.ic_insert_drive_file_black_48px;
            }
        }

        return R.drawable.ic_insert_drive_file_black_48px;
    }
}
