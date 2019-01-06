package net.phonex.ui.sendFile;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.accounting.PermissionLimits;
import net.phonex.util.Log;
import net.phonex.util.StorageUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by eldred on 19/02/15.
 */

public class FilePickManager implements Parcelable{

    private static final String TAG = "FilePickManager";
    public static final long MAXIMUM_BYTES_TO_PICK =
            20L * 1024L * 1024L; // 20 MB

    private boolean sizeLimitEnforced = true;
    private int filesLimit = -1;

    private final List<FilePickListener> listeners = new ArrayList<>();
    private ArrayList<FileItemInfo> selectedFileItemInfos = new ArrayList<>();

    public FilePickManager(Context context) {
        this.selectedFileItemInfos = new ArrayList<>();
        filesLimit = PermissionLimits.getFilesLimit(context.getContentResolver());
    }

    synchronized public List<FileItemInfo> getSelectedFileItemInfos() {
        return selectedFileItemInfos;
    }

    public boolean selectionExceededMaxSize(){
        return getSelectedFilesSize() > FilePickManager.MAXIMUM_BYTES_TO_PICK;
    }

    public boolean selectionExceededFilesLimit(){
        return hasFilesLimit() && getSelectedFilesCount() > filesLimit;
    }

    public int getSelectedFilesCount() {
        return selectedFileItemInfos.size();
    }

    public int getFilesLimit() {
        return filesLimit;
    }
    public boolean hasFilesLimit(){
        return filesLimit >= 0;
    }

    public boolean hasAnyEncrypted() {
        for (FileItemInfo f : selectedFileItemInfos) {
            if (f.isSecure) return true;
        }
        return false;
    }

    public long getSelectedFilesSize() {

        long result = 0L;

        for (final FileItemInfo fileItem : selectedFileItemInfos) {
            result += fileItem.sizeInBytes;
        }

        return result;
    }

    synchronized public void selectFile(final FileItemInfo fileItem) {

        if (selectedFileItemInfos.contains(fileItem)) {
            for (FilePickListener listener : listeners) {
                listener.notifySelectError();
            }
        }
        else {
            final int position = selectedFileItemInfos.size();
            selectedFileItemInfos.add(fileItem);
            fileItem.selectionOrder = position;

            for (FilePickListener listener : listeners) {
                listener.fileSelected(fileItem, position);
                if (sizeLimitEnforced && (getSelectedFilesSize() + fileItem.sizeInBytes) >= MAXIMUM_BYTES_TO_PICK) {
                    listener.notifySelectError();
                }
            }

        }
    }

    synchronized public boolean deselectFile(final FileItemInfo fileItem) {

        final int position = selectedFileItemInfos.indexOf(fileItem);
        if (position != -1) {

            Log.vf(TAG, "deselecting: %d", position);
            selectedFileItemInfos.remove(position);
            fileItem.selectionOrder = FileItemInfo.UNSELECTED;

            for (int i = position; i < selectedFileItemInfos.size(); ++i) {
                --(selectedFileItemInfos.get(i).selectionOrder);
            }

            for (FilePickListener listener : listeners) {
                listener.fileDeselected(fileItem, position);
            }
        }
        return selectedFileItemInfos.isEmpty();
    }

    synchronized public void addListener(final FilePickListener listener) {
        listeners.add(listener);
        listener.fillIn(selectedFileItemInfos);
    }

    synchronized public void removeListener(final FilePickListener listener) {
        listeners.remove(listener);
    }

    synchronized public void clearSelection() {
        selectedFileItemInfos.clear();
        for (FilePickListener listener : listeners) {
            listener.clearSelection();
        }
    }

    synchronized public void dropMissingFiles(ContentResolver resolver) {
        Iterator iterator = selectedFileItemInfos.iterator();
        while (iterator.hasNext()) {
            FileItemInfo info = (FileItemInfo) iterator.next();
            if (!StorageUtils.existsUri(info.getUri().toString(), resolver)) {
                iterator.remove();
            }
        }
    }

    /* Parcelable interface implemented below */
    protected FilePickManager(Parcel in) {
        if (in.readByte() == 0x01) {
            selectedFileItemInfos = new ArrayList<>();
            in.readList(selectedFileItemInfos, FileItemInfo.class.getClassLoader());
        } else {
            selectedFileItemInfos = null;
        }
        sizeLimitEnforced = in.readByte() == 0xFF;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (selectedFileItemInfos == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(selectedFileItemInfos);
        }
        dest.writeByte(sizeLimitEnforced ? (byte)0xFF : (byte)0x00);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<FilePickManager> CREATOR = new Parcelable.Creator<FilePickManager>() {
        @Override
        public FilePickManager createFromParcel(Parcel in) {
            return new FilePickManager(in);
        }

        @Override
        public FilePickManager[] newArray(int size) {
            return new FilePickManager[size];
        }
    };

}
