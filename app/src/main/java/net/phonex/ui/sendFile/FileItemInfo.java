package net.phonex.ui.sendFile;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.db.entity.FileStorage;
import net.phonex.ft.storage.FileStorageUri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Storage holder for array adapter.
 * View for individual row is constructed using this information.
 *
 * @author ph4r05, eldred, miroc
 */
public class FileItemInfo implements Parcelable {

    public static final int UNSELECTED = -1;

    public String filename; // the user friendly name for secure, or real name for normal file
    public int icon;
    public String absolutePath;

    public FileStorageUri uriRepresentative; // uri of newest file (of certain type) in this directory

    public boolean canBeSelected;
    public int selectionOrder = UNSELECTED;
    public boolean isUp = false; // folder ..

    public boolean isSecure;
    public String fileSystemName; // the real name

    // type 'long' according to return value from http://developer.android.com/reference/java/io/File.html#length()
    public long sizeInBytes;

    public long lastModified;

    public boolean specialPlaceholder = false;
    public int placeholderType = -1;

    public Uri getUri() {
        return FileStorageUri.fromFileItemInfo(this).getUri();
    }

    public FileItemInfo(final String filename, final int icon, final String absolutePath,
                        final boolean canBeSelected) {
        this(filename, icon, absolutePath, canBeSelected, 0L, false, null, 0L);
    }

    public FileItemInfo(final String filename, final int icon, final String absolutePath,
                        final boolean canBeSelected, final long length, final FileStorageUri representativeUri) {
        this(filename, icon, absolutePath, canBeSelected, length, false, null, 0L);
        uriRepresentative = representativeUri;
    }

    public FileItemInfo(final String filename, final int icon, final String absolutePath,
                        final boolean canBeSelected, final long sizeInBytes, final boolean isSecure, final String fileSystemName, final long lastModified) {
        this.filename = filename;
        this.icon = icon;
        this.absolutePath = absolutePath;
        this.canBeSelected = canBeSelected;
        this.isSecure = isSecure;
        this.sizeInBytes = sizeInBytes;

        if (fileSystemName == null) {
            this.fileSystemName = filename;
        } else {
            this.fileSystemName = fileSystemName;
        }

        this.lastModified = lastModified;
    }

    public FileItemInfo setSpecialPlaceholder(boolean specialPlaceholder) {
        this.specialPlaceholder = specialPlaceholder;
        return this;
    }

    public boolean isUp() {
        return isUp;
    }

    public void setUp(boolean isUp) {
        this.isUp = isUp;
    }

    boolean isSelected() {
        return (selectionOrder != UNSELECTED);
    }

    int getSelectionOrder() {

        return selectionOrder;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public InputStream getInputStream(ContentResolver cr) throws FileNotFoundException {
        if (isSecure) {
            try {
                FileStorage read = FileStorage.getFileStorageByFileSystemName(fileSystemName, cr);
                if (read == null) {
                    throw new FileNotFoundException();
                }
                return read.getInputStream();
            } catch (FileStorage.FileStorageException e) {
                throw new FileNotFoundException();
            }
        } else {
            return new FileInputStream(new File(absolutePath));
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((absolutePath == null) ? 0 : absolutePath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileItemInfo other = (FileItemInfo) obj;
        if (absolutePath == null) {
            if (other.absolutePath != null)
                return false;
        } else if (!absolutePath.equals(other.absolutePath))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return filename;
    }

    protected FileItemInfo(Parcel in) {
        filename = in.readString();
        icon = in.readInt();
        absolutePath = in.readString();
        canBeSelected = in.readByte() != 0x00;
        selectionOrder = in.readInt();
        isUp = in.readByte() != 0x00;
        sizeInBytes = in.readLong();
        specialPlaceholder = in.readByte() != 0x00;
        placeholderType = in.readInt();
        isSecure = in.readByte() != 0x00;
        fileSystemName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(filename);
        dest.writeInt(icon);
        dest.writeString(absolutePath);
        dest.writeByte((byte) (canBeSelected ? 0x01 : 0x00));
        dest.writeInt(selectionOrder);
        dest.writeByte((byte) (isUp ? 0x01 : 0x00));
        dest.writeLong(sizeInBytes);
        dest.writeByte((byte) (specialPlaceholder ? 0x01 : 0x00));
        dest.writeInt(placeholderType);
        dest.writeByte((byte) (isSecure ? 0x01 : 0x00));
        dest.writeString(fileSystemName);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<FileItemInfo> CREATOR = new Parcelable.Creator<FileItemInfo>() {
        @Override
        public FileItemInfo createFromParcel(Parcel in) {
            return new FileItemInfo(in);
        }

        @Override
        public FileItemInfo[] newArray(int size) {
            return new FileItemInfo[size];
        }
    };
}