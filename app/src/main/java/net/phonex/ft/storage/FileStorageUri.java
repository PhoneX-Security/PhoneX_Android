package net.phonex.ft.storage;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.db.entity.ReceivedFile;
import net.phonex.ui.sendFile.FileItemInfo;
import net.phonex.util.system.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to access files in secure storage and system storage.
 *
 * Used format:
 * scheme - secstor (STORAGE_SCHEME_SECURE) for secure storage
 *        - norstor (STORAGE_SCHEME_NORMAL) for system storage
 * path - path to the parent directory of this file
 * query parameter filename (STORAGE_FILENAME) - user friendly name
 * query parameter filesystemname (STORAGE_FILESYSTEM_NAME) - real name in filesystem
 * all files should have both query parameters (not secure files should have equal fs name and filename)
 *
 * Also needed to retain default handling of system URIs by Glide.
 *
 * Created by Matus on 10.6.2015.
 */
public class FileStorageUri implements Parcelable {

    /**
     * Uri scheme used for files in secure storage. Test using isSecureStorage() == true
     */
    public static final String STORAGE_SCHEME_SECURE = "secstor";
    /**
     * Uri scheme used for unprotected files on file system. Test using isSecureStorage() == false
     */
    public static final String STORAGE_SCHEME_NORMAL = "norstor";
    /**
     * Uri scheme used for thumbnail in DB. Test using isThumbnail() == true
     */
    public static final String STORAGE_SCHEME_THUMBNAIL = "thumbnail";
    public static final String STORAGE_FILENAME = "filename";
    public static final String STORAGE_FILESYSTEM_NAME = "filesystemname";

    Uri uri;

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public FileStorageUri(Uri uri) {
        this.uri = uri;
    }

    public FileStorageUri(String uri) {
        this.uri = Uri.parse(uri);
    }

    public FileStorageUri(String parentPath, String filename, String fileSystemName, boolean secureStorage) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(secureStorage ? STORAGE_SCHEME_SECURE : STORAGE_SCHEME_NORMAL);
        builder.path(parentPath);
        if (filename != null) {
            builder.appendQueryParameter(STORAGE_FILENAME, filename);
        }
        if (fileSystemName != null || filename != null) {
            builder.appendQueryParameter(STORAGE_FILESYSTEM_NAME, fileSystemName == null ? filename : fileSystemName);
        }
        uri = builder.build();
    }

    /**
     * Create new FileStorageUri used for thumbnails that are still only in DB.
     *
     * @param thumbnailPath does not matter
     * @param filename must be correct filename, because test on file type is performed
     * @return
     */
    public static String newThumbnailUriString(String thumbnailPath, String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("filename must not be null and must have correct file type");
        }
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(STORAGE_SCHEME_THUMBNAIL);
        builder.path(thumbnailPath);
        builder.appendQueryParameter(STORAGE_FILENAME, filename);
        return builder.build().toString();
    }

    public static FileStorageUri fromReceivedFile(ReceivedFile receivedFile) {
        if (receivedFile.getStorageUri() != null) {
            return new FileStorageUri(receivedFile.getStorageUri());
        }
        String parentPath = FilenameUtils.getPath(receivedFile.getPath());
        String fileName = FilenameUtils.getName(receivedFile.getPath());
        return new FileStorageUri(parentPath, fileName, fileName, false);
    }

    public static List<FileStorageUri> fromReceivedFiles(List<ReceivedFile> receivedFiles) {
        ArrayList<FileStorageUri> uris = new ArrayList<>();
        for (ReceivedFile receivedFile : receivedFiles) {
            uris.add(fromReceivedFile(receivedFile));
        }
        return uris;
    }

    public static FileStorageUri fromFileItemInfo(FileItemInfo info) {
        return new FileStorageUri(FilenameUtils.getPath(info.absolutePath), info.filename, info.fileSystemName, info.isSecure);
    }

    public static List<FileStorageUri> fromFileItemInfos(List<FileItemInfo> infos) {
        ArrayList<FileStorageUri> uris = new ArrayList<>();
        for (FileItemInfo info : infos) {
            uris.add(fromFileItemInfo(info));
        }
        return uris;
    }

    /**
     * Used in MessageFragment
     * @param uri
     * @return
     */
    public static List<FileItemInfo> toFileItemInfoList(FileStorageUri uri) {
        ArrayList<FileItemInfo> infos = new ArrayList<>();
        infos.add(new FileItemInfo(uri.getFilename(), 0, uri.getAbsolutePath(),
                true, 0, uri.isSecureStorage(), uri.getFilesystemName(), 0L));
        return infos;
    }

    public static String getParentPath(FileStorageUri uri) {
        return uri.getUri().getPath();
    }

    public String getParentPath() {
        return getParentPath(this);
    }

    public static String getFilename(FileStorageUri uri) {
        return uri.getUri().isHierarchical() ? Uri.decode(uri.getUri().getQueryParameter(STORAGE_FILENAME)) : null;
    }

    public String getFilename() {
        return getFilename(this);
    }

    public static String getFilesystemName(FileStorageUri uri) {
        String fsName = uri.getUri().isHierarchical() ? Uri.decode(uri.getUri().getQueryParameter(STORAGE_FILESYSTEM_NAME)) : null;
        return fsName == null ? getFilename(uri) : fsName;
    }

    public String getFilesystemName() {
        return getFilesystemName(this);
    }

    public static boolean isSecureStorage(FileStorageUri uri) {
        return STORAGE_SCHEME_SECURE.equals(uri.getUri().getScheme());
    }

    public boolean isSecureStorage() {
        return isSecureStorage(this);
    }

    public static boolean isThumbnail(FileStorageUri uri) {
        return STORAGE_SCHEME_THUMBNAIL.equals(uri.getUri().getScheme());
    }

    public boolean isThumbnail() {
        return isThumbnail(this);
    }

    public static String getAbsolutePath(FileStorageUri uri) {
        return uri.getUri().getPath() + File.separator + uri.getFilesystemName();
    }

    public String getAbsolutePath() {
        return getAbsolutePath(this);
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileStorageUri that = (FileStorageUri) o;

        return uri.equals(that.uri);

    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.uri, 0);
    }

    private FileStorageUri(Parcel in) {
        this.uri = in.readParcelable(Uri.class.getClassLoader());
    }

    public static final Parcelable.Creator<FileStorageUri> CREATOR = new Parcelable.Creator<FileStorageUri>() {
        public FileStorageUri createFromParcel(Parcel source) {
            return new FileStorageUri(source);
        }

        public FileStorageUri[] newArray(int size) {
            return new FileStorageUri[size];
        }
    };
}
