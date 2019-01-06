package net.phonex.ui.sendFile;

import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;

import net.phonex.R;
import net.phonex.db.entity.FileStorage;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pref.PreferencesManager;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.StorageUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by miroc on 5.3.15.
 */
public class FilePickerLoader extends AsyncTaskLoader<List<FileItemInfo>> {
    private static final String TAG = "FilePickerLoader";

    private final File root = new File(Environment.getExternalStorageDirectory() + "");

    private List<FileItemInfo> files;
    private int level;
    private File path;
    private SortingType sortingType = SortingType.ALPHABET;
    private boolean sortingAscending = true;

    private String goUpText;

    private MediaType mediaType;

    public FilePickerLoader(Context context, File path, int level, SortingType sortingType, boolean sortingAscending, MediaType mediaType) {
        super(context);
        this.path = path;
        this.level = level;
        this.sortingAscending = sortingAscending;
        this.sortingType = sortingType;
        this.mediaType = mediaType == null ? MediaType.ALL_FILES : mediaType;

        goUpText = context.getString(R.string.filepicker_up);

        Log.df(TAG, "FilePickerLoader FilePickerFragment2; level [%d], path [%s]", level, path);
    }

    @Override
    public List<FileItemInfo> loadInBackground() {
        if (level == 0){
            switch (mediaType) {
                case IMAGES:
                    return loadImageDirectories();
                case ALL_FILES:
                default:
                    return loadInitialMenu();
            }
        } else {
            return loadFileList();
        }
    }

    public static List<String> getMainPaths(Context context) {
        List<String> paths = new ArrayList<>();
        final MiscUtils.CameraDirectory cd = MiscUtils.getPhotoDirectory();
        if (cd != null && cd.path != null) {
            paths.add(cd.path.getAbsolutePath());
        }
        final File pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        paths.add(pictureDirectory.getAbsolutePath());
        File musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        paths.add(musicDirectory.getAbsolutePath());
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        paths.add(downloadDirectory.getAbsolutePath());
        // Currently we store photos simply in the Secure storage directory
//        File securePhotoStorageDirectory = PreferencesManager.getSecureCameraFolder(context);
//        if (securePhotoStorageDirectory != null) paths.add(securePhotoStorageDirectory.getAbsolutePath());
        File secureStorageDirectory = PreferencesManager.getRootSecureStorageFolder(context);
        if (secureStorageDirectory != null) paths.add(secureStorageDirectory.getAbsolutePath());

        return paths;
    }

    private List<FileItemInfo> loadImageDirectories() {
        Context ctx = getContext();
        ArrayList<FileItemInfo> itemList = new ArrayList<>();

        // Secure storage placeholder.
        File receivedSecureStorageDirectory = PreferencesManager.getUserSecureStorageFolder(getContext());
        if (receivedSecureStorageDirectory != null && receivedSecureStorageDirectory.exists() && receivedSecureStorageDirectory.isDirectory()) {

            addFileItemToList(itemList, receivedSecureStorageDirectory, StorageUtils.getLocalizedFileName(getContext(), receivedSecureStorageDirectory),
                    StorageUtils.getLocalizedFileImageResource(receivedSecureStorageDirectory), FilePickerFragment.PLACEHOLDER_DOWNLOAD, true, false);
        }

        // Secure photo storage placeholder.
        // Currently we store photos simply in the Secure storage directory
        File securePhotoStorageDirectory = PreferencesManager.getSecureCameraFolder(getContext());
        if (securePhotoStorageDirectory != null && securePhotoStorageDirectory.exists() && securePhotoStorageDirectory.isDirectory()) {

            addFileItemToList(itemList, securePhotoStorageDirectory, StorageUtils.getLocalizedFileName(getContext(), securePhotoStorageDirectory),
                    StorageUtils.getLocalizedFileImageResource(securePhotoStorageDirectory), FilePickerFragment.PLACEHOLDER_DOWNLOAD, true, false);

            // TODO search for newest secure photo and set as representative
        }

        ContentResolver imagesResolver = getContext().getContentResolver();
        Cursor imagesCursor = imagesResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DATA},//, MediaStore.Images.Media.SIZE}, // column for file path
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC");

        if (imagesCursor != null && imagesCursor.moveToFirst()){
            //get columns
            int pathColumn = imagesCursor.getColumnIndex(MediaStore.Images.Media.DATA);
            //int sizeColumn = imagesCursor.getColumnIndex(MediaStore.Images.Media.SIZE);

            Map<String, String> representatives = new HashMap<>();
            Map<String, Integer> fileCounts = new HashMap<>();

            do {
                String path = imagesCursor.getString(pathColumn);
                //int size = imagesCursor.getInt(sizeColumn);
                File image = new File(path);
                String parent = image.getParent();
                Integer countInThisDir = fileCounts.get(parent);
                if (countInThisDir != null) {
                    fileCounts.put(parent, countInThisDir + 1);
                } else {
                    representatives.put(parent, image.getAbsolutePath());
                    fileCounts.put(parent, 1);
                }
                //Log.df(TAG, "[%s] [%d]", path, size);
            } while (imagesCursor.moveToNext());

            for (Map.Entry<String, String> entry : representatives.entrySet()) {
                File gallery = new File(entry.getKey());
                File representative = new File(entry.getValue());

                // file was found by media scanner, must be unencrypted file
                FileStorageUri representativeUri = new FileStorageUri(representative.getParent(),
                            representative.getName(), representative.getName(), false);

                addFileItemToList(itemList, new File(entry.getKey()), gallery.getName(),
                        R.drawable.ic_folder_black_48px,
//                        R.drawable.ic_action_collections_collection,
                        FilePickerFragment.PLACEHOLDER_GALLERY_THUMBNAIL,
                        false, false,
                        fileCounts.get(entry.getKey()),
                        representativeUri);
                //Log.df(TAG, "Added [%s] to galleries with representative [%s], size [%d]", gallery.getName(), entry.getValue(), fileCounts.get(entry.getKey()));
            }
        }

        if (imagesCursor != null) {
            imagesCursor.close();
        }

        // order by size
        Collections.sort(itemList, (lhs, rhs) ->
                rhs.sizeInBytes < lhs.sizeInBytes ? -1 :
                rhs.sizeInBytes > lhs.sizeInBytes ? 1 : 0);

        return itemList;
    }

    private List<FileItemInfo> loadInitialMenu(){
        Context ctx = getContext();
        ArrayList<FileItemInfo> itemList = new ArrayList<>();

        // Detect externally mounted file systems.
        final Set<String> externalMounts = MiscUtils.getExternalMounts();

        // Camera placeholder.
        final MiscUtils.CameraDirectory cd = MiscUtils.getPhotoDirectory();
        if (cd != null && cd.path != null && cd.mediaFilesCount > 0) {

            addFileItemToList(itemList, cd.path, ctx.getString(R.string.filepicker_camera),
//                    R.drawable.ic_action_device_access_camera,
                    R.drawable.ic_photo_camera_black_48px,
                    FilePickerFragment.PLACEHOLDER_CAMERA, true, false);
        }

        // External camera placeholder.
        final MiscUtils.CameraDirectory ecd = MiscUtils.detectExternalCameraDirectory(externalMounts);
        if (ecd != null && ecd.path != null && ecd.mediaFilesCount > 0) {

            addFileItemToList(itemList, ecd.path, ctx.getString(R.string.filepicker_camera_2),
//                    R.drawable.ic_action_device_access_camera,
                    R.drawable.ic_photo_camera_black_48px,
                    FilePickerFragment.PLACEHOLDER_CAMERA, true, false);
        }

        // Pictures placeholder.
        final File pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (pictureDirectory != null && pictureDirectory.exists() && pictureDirectory.isDirectory()) {

            addFileItemToList(itemList, pictureDirectory, ctx.getString(R.string.filepicker_pictures),
                    R.drawable.ic_photo_black_48px,
                    FilePickerFragment.PLACEHOLDER_PICTURES, true, false);
        }

        // Music placeholder.
        File musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (musicDirectory != null && musicDirectory.exists() && musicDirectory.isDirectory()) {

            addFileItemToList(itemList, musicDirectory, ctx.getString(R.string.filepicker_music),
                    R.drawable.ic_action_hardware_headphones, FilePickerFragment.PLACEHOLDER_MUSIC, true, false);
        }

        // Download placeholder.
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDirectory != null && downloadDirectory.exists() && downloadDirectory.isDirectory()) {

            addFileItemToList(itemList, downloadDirectory, ctx.getString(R.string.filepicker_download),
//                    R.drawable.ic_action_av_download,
                    R.drawable.ic_get_app_black_48px,
                    FilePickerFragment.PLACEHOLDER_DOWNLOAD, true, false);
        }

        // Secure storage placeholder.
        File secureStorageDirectory = PreferencesManager.getRootSecureStorageFolder(getContext());
        if (secureStorageDirectory != null && secureStorageDirectory.exists() && secureStorageDirectory.isDirectory()) {

            addFileItemToList(itemList, secureStorageDirectory, ctx.getString(R.string.filepicker_secure_storage),
                    R.drawable.svg_encrypted, FilePickerFragment.PLACEHOLDER_DOWNLOAD, true, false);
        }

        // Secure photo storage placeholder.
        // Currently we store photos simply in the Secure storage directory
//        File securePhotoStorageDirectory = PreferencesManager.getSecureCameraFolder(getContext());
//        if (securePhotoStorageDirectory != null && securePhotoStorageDirectory.exists() && securePhotoStorageDirectory.isDirectory()) {
//
//            addFileItemToList(itemList, securePhotoStorageDirectory, ctx.getString(R.string.filepicker_secure_photo_storage),
//                    R.drawable.svg_encrypted, FilePickerFragment.PLACEHOLDER_DOWNLOAD, true, false);
//        }

        // External storage placeholders
        if (!externalMounts.isEmpty()){
            int i = 0;
            final String external = ctx.getString(R.string.filepicker_external_sd);
            for(String externalMount : externalMounts){
                try {
                    File externalFile = new File(externalMount);
                    if (!externalFile.exists()){
                        Log.ef(TAG, "External folder detected but does not exist [%s]", externalMount);
                        continue;
                    }

                    addFileItemToList(itemList, externalFile,
                            externalMounts.size() == 1 ? external : external + " " + (i + 1),
                            R.drawable.ic_sd_storage, FilePickerFragment.PLACEHOLDER_STORAGE, true, false);

                    i += 1;
                } catch(Exception ex){
                    Log.e(TAG, "Problem with processing external mount directory");
                }
            }
        }

        // If there is no placeholder at all, move directly to the external storage.
        if (itemList.isEmpty()) {
            return loadFileList();
        }

        // Storage placeholder that always exists
        addFileItemToList(itemList, root,
                ctx.getString(R.string.filepicker_storage),
//                R.drawable.ic_action_collections_collection,
//                R.drawable.ic_folder_black_24px,
                R.drawable.ic_folder_black_48px,
                FilePickerFragment.PLACEHOLDER_STORAGE, true, false);

        return itemList;
    }

    private List<FileItemInfo> loadFileList() {
        List<FileItemInfo> itemList = new ArrayList<>();

        if (path == null){
            switch (mediaType) {
                case IMAGES:
                    return loadImageDirectories();
                case ALL_FILES:
                default:
                    return loadInitialMenu();
            }
        }

        try {
            path.mkdirs();
        } catch (SecurityException e) {
            Log.e(TAG, "unable to write on the sd card ", e);
        }

        // Checks whether path exists
        if (!path.exists()) {
            Log.ef(TAG, "path does not exist [%s]", path.getAbsolutePath());
            return itemList;
        }

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                // media files can be filtered only after secure storage names are translated
                final File sel = new File(dir, filename);
                // Filters based on whether the file is hidden or not
                return (sel.isFile() || sel.isDirectory()) && !sel.isHidden();
            }
        };

        // Load list of files in this directory.
        String[] fList = path.list(filter);

        try {
            final String rootAbs = root.getCanonicalPath();
            final String pathAbs = path.getCanonicalPath();
            Log.v(TAG, String.format("root=[%s] path=[%s] level=%d", rootAbs, pathAbs, level));
        } catch(Exception ex){
            Log.e(TAG, "Exception: getCanonicalPath()", ex);
            return itemList;
        }

        // If not first level special entry "up" is added.
        // but only if all files are being listed (not for SECURE_STORAGE_ONLY, not for IMAGES)
        if (level >= 1 && mediaType == MediaType.ALL_FILES) {
            FileItemInfo itemInfo = new FileItemInfo(goUpText,
                    R.drawable.ic_folder_open_black_48px,
//                    R.drawable.ic_folder_open_black_24px,
//                    R.drawable.directory_up,
                    null, false).setSpecialPlaceholder(true);
            itemInfo.setUp(true);
            itemList.add(itemInfo);
        }

        // Processing listed files, preparing for sorting.
        final ArrayList<FsEntry> fsEntries = new ArrayList<>(fList.length);
        for(String fPath : fList){
            // Convert into file path
            final File sel = new File(path, fPath);

            // Add to the collection, for further sortingType.
            final FsEntry fse = new FsEntry(sel.isDirectory(), sel.getName(), sel);
            fse.lastModified = sel.lastModified();
            fsEntries.add(fse);
        }

        // Filter the entries, that are from Secure Storage and convert their names
        try {
            List<FileStorage> fileStorageList = FileStorage.getFileStoragesByFolderPath(path.getAbsolutePath(), getContext().getContentResolver());
            for (FileStorage fileStorage : fileStorageList) {
                for (FsEntry fsEntry : fsEntries) {
                    if (fileStorage.getFileSystemName().equals(fsEntry.filename)) {
                        fsEntry.fileSystemName = fsEntry.filename;
                        fsEntry.filename = fileStorage.getFilename();
                        fsEntry.isSecure = true;
                        break;
                    }
                }
            }
        } catch (FileStorage.FileStorageException e) {
            Log.e(TAG, "Incorrect path for file storage " + path.getAbsolutePath(), e);
        }

        // filter media files if required, we must use the name after secure storage translation
        switch (mediaType) {
            case IMAGES:
                // do not list directories either, only image files
                Iterator<FsEntry> iterator = fsEntries.iterator();
                while(iterator.hasNext()) {
                    if (!FileUtils.isImage(iterator.next().filename)) {
                        iterator.remove();
                    }
                }
                break;
            case ALL_FILES:
            default:
                break;
        }

        // Use our comparator to sort FS entries
        // We have to pass type and ascending here, so it remains constant during sorting (to avoid Comparison method violates its general contract! exception)
        Collections.sort(fsEntries, new FsEntryComparator(sortingType, sortingAscending));

        // Add Items for the path content
        for(FsEntry e : fsEntries){
            int placeholderResourceId;
            if (e.isDirectory) {
                placeholderResourceId = R.drawable.ic_folder_black_48px; // directory icon
            } else if (FileUtils.isImage(e.filename) || FileUtils.isVideo(e.filename)) {
                placeholderResourceId = R.drawable.ic_photo_black_48px; // photo icon
            } else {
                placeholderResourceId = R.drawable.ic_insert_drive_file_black_48px; // general icon
            }

            if (e.isDirectory && e.file != null) {
                e.filename = StorageUtils.getLocalizedFileName(getContext(), e.file);
                placeholderResourceId = StorageUtils.getLocalizedFileImageResource(e.file);
            }

            final FileItemInfo fileItemInfo =
                    new FileItemInfo(e.filename,
                            placeholderResourceId,
                            e.file.getAbsolutePath(),
                            !e.isDirectory,
                            0L,
                            e.isSecure,
                            e.isSecure ? e.fileSystemName : e.filename,
                            e.lastModified);

            if (e.isSecure) {
                // TODO size is a couple bytes off
                fileItemInfo.sizeInBytes = e.file.length();
            } else {
                fileItemInfo.sizeInBytes = e.file.length();
            }

            itemList.add(fileItemInfo);
        }

        return itemList;
    }

    private void addFileItemToList(final ArrayList<FileItemInfo> fileItemInfoList,
                                   final File file, final String name,
                                   final int drawableId, final int placeHolderType,
                                   final boolean isSpecialPlaceHolder,
                                   final boolean canBeSelected,
                                   long size,
                                   final FileStorageUri representativeUri) {

        if (size < 0) size = file.length();

        final FileItemInfo item = new FileItemInfo(name, drawableId, file.getAbsolutePath(), canBeSelected, size, representativeUri);

        item.placeholderType = placeHolderType;
        item.specialPlaceholder = isSpecialPlaceHolder;

        fileItemInfoList.add(item);
    }

    private void addFileItemToList(final ArrayList<FileItemInfo> fileItemInfoList,
                                   final File file, final String name,
                                   final int drawableId, final int placeHolderType,
                                   final boolean isSpecialPlaceHolder,
                                   final boolean canBeSelected) {

        addFileItemToList(fileItemInfoList, file, name,
                drawableId, placeHolderType, isSpecialPlaceHolder, canBeSelected, -1, null);
    }


    /* Inspired by https://developer.android.com/reference/android/content/AsyncTaskLoader.html */


    /**
     * Called when there is new data to deliver to the client.  The
     * super class will take care of delivering it; the implementation
     * here just adds a little more logic.
     */
    @Override
    public void deliverResult(List<FileItemInfo> apps) {
        if (isReset()) {
            // An async query came in while the loader is stopped.  We
            // don't need the result.
            if (apps != null) {
                onReleaseResources(apps);
            }
        }
        List<FileItemInfo> oldApps = files;
        files = apps;

        if (isStarted()) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(apps);
        }

        // At this point we can release the resources associated with
        // 'oldApps' if needed; now that the new result is delivered we
        // know that it is no longer in use.
        if (oldApps != null) {
            onReleaseResources(oldApps);
        }
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading() {
        if (files != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(files);
        }

        // Start watching for changes in the app data.
//        if (mPackageObserver == null) {
//            mPackageObserver = new PackageIntentReceiver(this);
//        }

        // Has something interesting in the configuration changed since we
        // last built the app list?
//        boolean configChange = mLastConfig.applyNewConfig(getContext().getResources());

        if (takeContentChanged() || files == null){ //|| configChange) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to cancel a load.
     */
    @Override
    public void onCanceled(List<FileItemInfo> apps) {
        super.onCanceled(apps);

        // At this point we can release the resources associated with 'apps'
        // if needed.
        onReleaseResources(apps);
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated with 'apps'
        // if needed.
        if (files != null) {
            onReleaseResources(files);
            files = null;
        }

        // Stop monitoring for changes.
//        if (mPackageObserver != null) {
//            getContext().unregisterReceiver(mPackageObserver);
//            mPackageObserver = null;
//        }
    }


    private void onReleaseResources(List<FileItemInfo> apps) {
        // nothing to do with List
    }

    /**
     * Class for file system entries sortingType for display.
     *
     * @author ph4r05
     */
    public static class FsEntry {
        @SuppressWarnings("unused")
        public File file;
        public boolean isDirectory;
        public String filename;
        public long lastModified;

        public String fileSystemName;
        public boolean isSecure;

        public FsEntry(boolean isDirectory, String filename, File file) {
            super();

            this.isDirectory = isDirectory;
            this.filename = filename;
            this.file = file;
        }
    }

    /**
     * Comparator for file system entries.
     * Directory came first, then lexicographic ordering is used.
     *
     * @author ph4r05
     */
    private class FsEntryComparator implements Comparator<FsEntry> {
        private final SortingType sortingType;
        private final boolean sortingAscending;

        private FsEntryComparator(final SortingType sortingType, final boolean sortingAscending) {
            super();

            this.sortingType = sortingType != null ? sortingType : SortingType.ALPHABET;
            this.sortingAscending = sortingAscending;
        }

        @Override
        public int compare(FsEntry a, FsEntry b) {
            // Directory is preferred.
            if ( a.isDirectory && !b.isDirectory)
                return -1;

            if (!a.isDirectory &&  b.isDirectory)
                return  1;

            final int alphabetComparison = a.filename.compareToIgnoreCase(b.filename);
            final long modifiedDiff =  (a.lastModified - b.lastModified);
            final int lastModifiedCmp;
            if (modifiedDiff == 0){
                lastModifiedCmp = 0;
            } else {
                lastModifiedCmp = modifiedDiff > 0 ? 1 : -1;
            }

            final int order = this.sortingAscending ? 1 : -1;

            switch (this.sortingType){
                case ALPHABET:
                    return alphabetComparison * order;
                case DATE:
                    return lastModifiedCmp * order;
            }

            return alphabetComparison;
        }
    }

}
