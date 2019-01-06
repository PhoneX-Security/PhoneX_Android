package net.phonex.ui.sendFile;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.ft.storage.FileActionListener;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pub.a.Compatibility;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.fileManager.FileManager;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.StorageUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * FilePickerFragment on steroids (Loader)
 * Created by miroc on 3/6/15.
 *
 */
public class FilePickerFragment extends Fragment
        implements View.OnKeyListener, FilePickListener,
        LoaderManager.LoaderCallbacks<List<FileItemInfo>>, FileActionListener {
    private static final String TAG = "FilePickerFragment2";

    public static final String ACTION_FILE_PICKER_CONTEXT_MENU = "filePickerContextMenu";

    public static final int PLACEHOLDER_CAMERA = 1;
    public static final int PLACEHOLDER_DOWNLOAD = 2;
    public static final int PLACEHOLDER_PICTURES = 4;
    public static final int PLACEHOLDER_MUSIC = 5;
    public static final int PLACEHOLDER_STORAGE = 6;

    // the file given is a representative of this gallery and the real path is its parent
    public static final int PLACEHOLDER_GALLERY_THUMBNAIL = 7;

    public static final String EXTRA_PATH_ELEMENTS = "path_elements";
    public static final String EXTRA_PICKER_MANAGER = "picker_manager";
    public static final String EXTRA_FILE_PICKER_TYPE = "filePickerType";
    public static final String EXTRA_MEDIA_TYPE = "mediaType";
    public static final String EXTRA_TITLE_STRING = "defaultTitleString";
    public static final String EXTRA_DISPLAY_MODE = "displayMode";
    public static final String EXTRA_SHOWING_SELECTION = "showingSelectedFiles";

    private static final String ITEMS_VIEW_TAG = "itemsViewTag";

    public static final int FILEPICKER_RESULT_CODE = 84681104;

    public static final String THIS_FRAGMENT_TAG = "file_picker_fragment_tag";

    // context menu
    public static final int MENU_OPEN = ContextMenu.FIRST;
    public static final int MENU_OPEN_DIRECTORY = ContextMenu.FIRST + 1;
    public static final int MENU_DELETE = ContextMenu.FIRST + 2;
    public static final int MENU_DELETE_MULTI = ContextMenu.FIRST + 3;
    public static final int MENU_DECRYPT = ContextMenu.FIRST + 4;
    public static final int MENU_DECRYPT_MULTI = ContextMenu.FIRST + 5;

    private OnFilesChosenListener listener;
    private boolean listenerDetectedInAttach = false;

    @InjectView(R.id.progressContainer) View progressContainerView;
    @InjectView(R.id.selection_bar) View selectionBar;
    @InjectView(R.id.my_toolbar) Toolbar toolbar;

    @InjectView(R.id.send_menu) ViewGroup sendMenu;
    @InjectView(R.id.manager_menu) ViewGroup managerMenu;
    @InjectView(R.id.selected_size_text) TextView selectedSizeText;
    @InjectView(R.id.limit_text) TextView limitText;
    @InjectView(R.id.send) ImageButton sendButton;


    public enum FilePickerType {
        SEND_FILES,
        MANAGE_FILES
    }

    enum DisplayMode {
        LIST,
        GRID
    }

    public interface OnFilesChosenListener {
        void onFileItemInfosChosen(List<FileItemInfo> files, Action action);
    }

    // vvvvvv items that must be saved when changing state

    private FilePickManager pickManager;
    // List of absolute paths - where am I in directory tree
    // use synchronized methods below to access pathElements
    private ArrayList<String> pathElements = new ArrayList<>();
    private FilePickerType filePickerType;
    private MediaType mediaType;
    private String filePickerTitle;
    private DisplayMode displayMode;
    private boolean showingSelectedFiles;

    // ^^^^^ items that must be saved when changing state

    private ListView itemsListView;

    private GridView itemsGridView;

    private FileAdapter adapter;

    private SortingType sortingType;
    private volatile boolean sortingAscending;

    private boolean useGridMode() {
        return displayMode == DisplayMode.GRID;
    }

    @Override
    public Loader<List<FileItemInfo>> onCreateLoader(int id, Bundle args) {
        File path = null;
        if (getLevel() > 0){
            // get last path we remember
            path = new File(getLastPath());
        }

        return new FilePickerLoader(getActivity(), path, getLevel(), sortingType, sortingAscending, mediaType);
    }

    @Override
    public void onLoadFinished(Loader<List<FileItemInfo>> loader, List<FileItemInfo> data) {
        Log.df(TAG, "onLoadFinished; data size %d", data.size());
        setProgressVisibility(false);
        enableFileSorting(!isMenuLevel());
        enableFastScrolling(data.size() > 25);
        if (showingSelectedFiles) {
            showSelectedFiles();
        } else {
            adapter.setData(data);
        }
        pickManager.addListener(this);
    }

    private synchronized boolean isMenuLevel(){
        return pathElements.isEmpty();
    }

    private synchronized int getLevel(){
        return pathElements.size();
    }
    private synchronized String getLastPath(){
        return pathElements.get(pathElements.size() - 1);
    }
    private synchronized void removeLastPath(){
        pathElements.remove(pathElements.size() - 1);
    }

    @Override
    public void onLoaderReset(Loader<List<FileItemInfo>> loader) {
        adapter.setData(null);
    }

    private void setProgressVisibility(boolean visible){
        if (progressContainerView != null){
            try {
                progressContainerView.setVisibility(visible ? View.VISIBLE : View.GONE);
            } catch(Exception ex){
                Log.e(TAG, "Exception, cannot set progress visibility");
            }
        }
    }

    public static FilePickerFragment newInstance(Context ctx, OnFilesChosenListener listener) {
        FilePickerFragment fragment = new FilePickerFragment();
        fragment.listener = listener;
        fragment.filePickerType = FilePickerType.SEND_FILES;

        fragment.sortingType = SortingType.ALPHABET;
        fragment.sortingAscending = true;

        return fragment;
    }

    public static FilePickerFragment newInstance(Context ctx, OnFilesChosenListener listener,
                                                 FilePickerType filePickerType, String startingPath,
                                                 MediaType mediaType) {
        FilePickerFragment fragment = new FilePickerFragment();
        fragment.listener = listener;
        fragment.filePickerType = filePickerType;
        if (MediaType.SECURE_STORAGE_ONLY.equals(mediaType)) {
            // if displaying secure storage only, do not go further up
            if (startingPath != null) {
                // we were given path to secure directory
                fragment.pathElements.add(startingPath);
            } else {
                // programmer forgot to add path to secure directory
                fragment.pathElements.add(PreferencesManager.getUserSecureStorageFolder(ctx).getAbsolutePath());
            }
        } else
        if (startingPath != null) {
            File folder = new File(startingPath);
            List<String> paths = null;
            if (folder.exists() && folder.isDirectory()) {
                List<String> stoppers = FilePickerLoader.getMainPaths(ctx);
                List<String> pathElements = new ArrayList<>();

                while (folder != null) {
                    pathElements.add(folder.getAbsolutePath());
                    if (stoppers.contains(folder.getAbsolutePath())) break;
                    folder = folder.getParentFile();
                }

                // the same paths but backwards
                paths = new ArrayList<>();
                for (int i = pathElements.size() - 1; i >= 0; i--) {
                    paths.add(pathElements.get(i));
                }
            }
            if (paths != null) {
                fragment.pathElements.addAll(paths);
            }
        }

        fragment.mediaType = mediaType;
        switch (mediaType) {
            case IMAGES:
                fragment.displayMode = DisplayMode.GRID;
                break;
            case SECURE_STORAGE_ONLY:
            case ALL_FILES:
            default:
                fragment.displayMode = DisplayMode.LIST;
        }

        // sort descending by date for media files
        if (mediaType != MediaType.ALL_FILES) {
            fragment.sortingType = SortingType.DATE;
            fragment.sortingAscending = false;
        } else {
            fragment.sortingType = SortingType.ALPHABET;
            fragment.sortingAscending = true;
        }

        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (useGridMode()) {
            adapter = new FileAdapter(getActivity(), R.layout.filechooser_image_item, displayMode);
            itemsGridView.setAdapter(adapter);
        } else {
            adapter = new FileAdapter(getActivity(), R.layout.filechooser_item, displayMode);
            itemsListView.setAdapter(adapter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().initLoader(0, null, this);
        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.x

        // quick fix - since Broadcast activity edit text steals focus which opens a keyboard when orientation is changed
        // TODO create fragment for content shown in BroadcastActivity
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LayoutUtils.hideSwKeyboard(getActivity());

        if (pickManager != null && pickManager.getSelectedFilesCount() != 0) {
            setSelectedFiles();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickManager = new FilePickManager(getActivity());


        // reload state
        if (MediaType.SECURE_STORAGE_ONLY.equals(mediaType)) {
            filePickerTitle = getString(R.string.filepicker_secure_storage);
        } else {
            filePickerTitle = getString(R.string.filepicker_title);
        }
        if (savedInstanceState != null){
            synchronized (this){
                pathElements = savedInstanceState.getStringArrayList(EXTRA_PATH_ELEMENTS);
            }
            pickManager = savedInstanceState.getParcelable(EXTRA_PICKER_MANAGER);
            filePickerType = (FilePickerType) savedInstanceState.getSerializable(EXTRA_FILE_PICKER_TYPE);
            mediaType = (MediaType) savedInstanceState.getSerializable(EXTRA_MEDIA_TYPE);
            filePickerTitle = savedInstanceState.getString(EXTRA_TITLE_STRING);
            displayMode = (DisplayMode) savedInstanceState.getSerializable(EXTRA_DISPLAY_MODE);
            showingSelectedFiles = savedInstanceState.getBoolean(EXTRA_SHOWING_SELECTION);
        }

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (listener == null && activity instanceof OnFilesChosenListener){
            listener = (OnFilesChosenListener) activity;
            listenerDetectedInAttach = true;
            Log.v(TAG, "Listener auto-detected in onAttach()");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        pickManager.removeListener(this);
        if (listenerDetectedInAttach){
            listener = null;
            Log.v(TAG, "Auto-detected listener reset in onDetach()");
        }
    }

    private int getNumColumns() {
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float widthInches = metrics.widthPixels / (metrics.xdpi);

        // display at least 2 tiles, otherwise put so many, that they are at least inch wide each
        return Math.max(2, (int) widthInches);
    }

    private View initItemsView(boolean setAdapter) {
        View itemsView;

        if (useGridMode()) {
            itemsGridView = new GridView(getActivity());
            itemsGridView.setNumColumns(getNumColumns());
            itemsGridView.setOnItemClickListener(new GridView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> listView, View itemView, int position, long itemId) {
                    final FileItemInfo selectedFileItemInfo = adapter.getItem(position);

                    if (selectedFileItemInfo.specialPlaceholder) {
                        specialFileItemClicked(selectedFileItemInfo);
                    } else {
                        final File selectedFile = new File(selectedFileItemInfo.absolutePath);
                        if (selectedFile.isDirectory()) {
                            directoryFileItemClicked(selectedFile);
                        } else {
                            leafFileItemClicked(selectedFileItemInfo);
                        }
                    }
                }
            });
            itemsView = itemsGridView;
            if (setAdapter && adapter != null) {
                itemsGridView.setAdapter(adapter);
            }
        } else {
            itemsListView = new ListView(getActivity());
            itemsListView.setOnItemClickListener(new ListView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> listView, View itemView, int position, long itemId) {
                    final FileItemInfo selectedFileItemInfo = adapter.getItem(position);

                    if (selectedFileItemInfo.specialPlaceholder) {
                        specialFileItemClicked(selectedFileItemInfo);
                    } else {
                        final File selectedFile = new File(selectedFileItemInfo.absolutePath);
                        if (selectedFile.isDirectory()) {
                            directoryFileItemClicked(selectedFile);
                        } else {
                            leafFileItemClicked(selectedFileItemInfo);
                        }
                    }
                }
            });
            itemsView = itemsListView;
            if (setAdapter && adapter != null) {
                itemsListView.setAdapter(adapter);
            }
        }

        registerForContextMenu(itemsView);
        itemsView.setOnCreateContextMenuListener(this);

        itemsView.setTag(ITEMS_VIEW_TAG);

        return itemsView;
    }

    private synchronized void changeDisplayMode(DisplayMode newDisplayMode) {
        if (newDisplayMode == displayMode) {
            return;
        }
        View mainBodyView = getView();
        if (mainBodyView == null) {
            return;
        }
        LinearLayout body = (LinearLayout) mainBodyView.findViewById(R.id.body);
        if (body == null) {
            return;
        }

        setProgressVisibility(true);

        View oldItemsView = body.findViewWithTag(ITEMS_VIEW_TAG);
        if (oldItemsView != null) {
            body.removeView(oldItemsView);
            if (oldItemsView.equals(itemsGridView)) itemsGridView = null;
            if (oldItemsView.equals(itemsListView)) itemsListView = null;
        }

        displayMode = newDisplayMode;

        if (displayMode == DisplayMode.LIST) {
            adapter.setResourceId(R.layout.filechooser_item);
        } else if (displayMode == DisplayMode.GRID) {
            adapter.setResourceId(R.layout.filechooser_image_item);
        } else {
            adapter.setResourceId(R.layout.filechooser_item);
        }

        refreshDisplayModeIcon();

        adapter.setDisplayMode(newDisplayMode);

        body.addView(initItemsView(true), 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mainBodyView = inflater.inflate(R.layout.filechooser_main, container, false);
        ButterKnife.inject(this, mainBodyView);

        final LinearLayout body = (LinearLayout) mainBodyView.findViewById(R.id.body);

        initToolbar();
        initSelectionBar();

        body.addView(initItemsView(false), 0);
        return mainBodyView;
    }

    private void initSelectionBar() {
        selectionBar.setClickable(true);
        selectionBar.setOnClickListener(v -> {
            if (showingSelectedFiles) {
                goUp();
            } else {
                showSelectedFiles();
            }
        });

        // always show the clear button
        selectionBar.findViewById(R.id.clear).setOnClickListener(v -> selectionMenuItemClicked(R.id.clear));

        if (filePickerType == FilePickerType.MANAGE_FILES) {
            selectionBar.findViewById(R.id.filepicker_open).setOnClickListener(v -> selectionMenuItemClicked(R.id.filepicker_open));
            selectionBar.findViewById(R.id.filepicker_delete).setOnClickListener(v -> selectionMenuItemClicked(R.id.filepicker_delete));
            selectionBar.findViewById(R.id.filepicker_decrypt).setOnClickListener(v -> selectionMenuItemClicked(R.id.filepicker_decrypt));
            // hide the send button, from file manager there is no option to send
            selectionBar.findViewById(R.id.send_menu).setVisibility(View.GONE);
        } else if (filePickerType == FilePickerType.SEND_FILES) {
            selectionBar.findViewById(R.id.filepicker_open).setVisibility(View.GONE);
            selectionBar.findViewById(R.id.filepicker_delete).setVisibility(View.GONE);
            selectionBar.findViewById(R.id.filepicker_decrypt).setVisibility(View.GONE);
            // only show the send button, other buttons will be hidden
            selectionBar.findViewById(R.id.send).setOnClickListener(v -> selectionMenuItemClicked(R.id.send));
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        FileItemInfo fileItemInfo = adapter.getItem(info.position);
        if (fileItemInfo == null) {
            return;
        }
        if (!fileItemInfo.canBeSelected) {
            return;
        }
        // add to selection
        if (!fileItemInfo.isSelected()) {
            leafFileItemClicked(fileItemInfo);
        }

        Intent intent = new Intent(ACTION_FILE_PICKER_CONTEXT_MENU);

        MenuItem item;

        item = menu.add(0, MENU_OPEN, 0, R.string.open_file);
        item.setIntent(intent);
        item = menu.add(0, MENU_DELETE, 0, R.string.file_menu_delete);
        item.setIntent(intent);
        if (fileItemInfo.isSecure) {
            item = menu.add(0, MENU_DECRYPT, 0, R.string.file_menu_decrypt);
            item.setIntent(intent);
        }
        if (pickManager != null && pickManager.getSelectedFilesCount() > 1) {
            item = menu.add(0, MENU_DELETE_MULTI, 0, R.string.file_menu_delete_multi);
            item.setIntent(intent);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info==null){
            Log.w(TAG, "Null info, do nothing");
            return super.onContextItemSelected(item);
        }
        FileItemInfo fileItemInfo = adapter.getItem(info.position);
        if (fileItemInfo == null) {
            return super.onContextItemSelected(item);
        }

        ArrayList<FileItemInfo> list = new ArrayList<>();
        switch (item.getItemId()) {
            case MENU_OPEN:
                openFile(fileItemInfo);
                break;
            case MENU_OPEN_DIRECTORY:
                FileManager.openDirectory(FileStorageUri.fromFileItemInfo(fileItemInfo), getActivity());
                break;
            case MENU_DELETE:
                list.add(fileItemInfo);
                signalAction(list, Action.DELETE, false);
                break;
            case MENU_DELETE_MULTI:
                signalAction(pickManager.getSelectedFileItemInfos(), Action.DELETE, false);
                break;
            case MENU_DECRYPT:
                list.add(fileItemInfo);
                signalAction(list, Action.DECRYPT, false);
                break;
            case MENU_DECRYPT_MULTI:
                signalAction(pickManager.getSelectedFileItemInfos(), Action.DECRYPT, false);
                break;
        }
        // consume the event
        return true;
    }

    private void restartFileLoader(){
        if (isAdded()){
            pickManager.removeListener(this);
            // reset scroll position manually
            if (useGridMode()){
                itemsGridView.setSelection(0);
            } else {
                itemsListView.setSelectionAfterHeaderView();
            }
            getLoaderManager().restartLoader(0, null, this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.inf(TAG, "onSaveInstanceState; list size %d", getLevel());
        synchronized (this){
            outState.putStringArrayList(EXTRA_PATH_ELEMENTS, pathElements);
            outState.putParcelable(EXTRA_PICKER_MANAGER, pickManager);
            outState.putSerializable(EXTRA_FILE_PICKER_TYPE, filePickerType);
            outState.putSerializable(EXTRA_MEDIA_TYPE, mediaType);
            outState.putString(EXTRA_TITLE_STRING, filePickerTitle);
            outState.putSerializable(EXTRA_DISPLAY_MODE, displayMode);
            outState.putBoolean(EXTRA_SHOWING_SELECTION, showingSelectedFiles);
        }
    }

    private void changeTitle(String title) {
        filePickerTitle = title;
        toolbar.setTitle(filePickerTitle);
    }

    private void changeTitleLocalize(File file) {
        if (isAdded()) {
            changeTitle(StorageUtils.getLocalizedFileName(getActivity(), file));
        } else {
            // fragment is not added to activity, does it even make sense to show something?
            changeTitle(file.getName());
        }
    }

    // "UP" and other placeholders.
    synchronized private void specialFileItemClicked(final FileItemInfo selectedItem) {
        if (selectedItem.isUp()) {
            goUp();
        } else {
            // Different placeholder - shortcuts to directories.
            pathElements.clear();
            pathElements.add(selectedItem.absolutePath);
            changeTitleLocalize(new File(selectedItem.absolutePath));
            restartFileLoader();
        }
    }

    /**
     * Go one level up in the filesystem hierarchy.
     * @return true if gone up, false if top level directory
     */
    synchronized public boolean goUp() {
        showingSelectedFiles = false;
        if (MediaType.SECURE_STORAGE_ONLY.equals(mediaType)) {
            // from SECURE_STORAGE_ONLY we do not want to go up to directory list
            // however if we are deeper, then do go up
            if (pathElements.size() <= 1) {
                return false;
            }
        }
        if (!pathElements.isEmpty()) {
            pathElements.remove(pathElements.size() - 1);
            if (pathElements.isEmpty()) {
                changeTitle(getString(R.string.filepicker_title));
            } else {
                changeTitleLocalize(new File(pathElements.get(pathElements.size() - 1)));
            }
            restartFileLoader();
            return true;
        }
        return false;
    }

    synchronized private void directoryFileItemClicked(final File selectedFile) {
        pathElements.add(selectedFile.getAbsolutePath());
        changeTitleLocalize(selectedFile);
        restartFileLoader();
    }

    synchronized private void leafFileItemClicked(final FileItemInfo fileItemInfo){
        if (fileItemInfo.isSelected()) {
            pickManager.deselectFile(fileItemInfo);
        } else {
            pickManager.selectFile(fileItemInfo);
        }
        setSelectedFiles();
    }

    private void showSelectedFiles() {
        if (pickManager == null) {
            return;
        }
        if (!showingSelectedFiles && pickManager.getSelectedFilesCount() == 0) {
            // selected files are not shown, but there is nothing to show
            return;
        }
        if (showingSelectedFiles && pickManager.getSelectedFilesCount() == 0) {
            // selected files were being shown, but they were all deleted
            // hide the list of selected files
            goUp();
            return;
        }
        changeTitle(getString(R.string.file_picker_selected_files));
        if (!showingSelectedFiles) pathElements.add(filePickerTitle);

        adapter.setData(pickManager.getSelectedFileItemInfos());
        showingSelectedFiles = true;
    }

    // FilePickListener STUFF
    public void fileSelected (final FileItemInfo file, final int position) {
        for (int i = 0; i < adapter.getCount(); ++i) {
            final FileItemInfo fileItemInfo = adapter.getItem(i);
            if (fileItemInfo.equals(file)) {
                setSelectedIfShown(i, position);
            }
        }
    }

    public void fileDeselected (final FileItemInfo file, final int position)
    {
        for (int i = 0; i < adapter.getCount(); ++i) {

            final FileItemInfo fileItemInfo = adapter.getItem(i);

            if (fileItemInfo.equals(file)) {

                setSelectedIfShown(i, FileItemInfo.UNSELECTED);

            } else {

                if (fileItemInfo.isSelected()) {

                    setSelectedIfShown(i, fileItemInfo.getSelectionOrder());
                }
            }
        }
    }

    private void setSelectedIfShown(final int itemIndex, final int selectionIndex) {

        final View itemView = getItemViewAt(itemIndex);
        if (itemView != null && isAdded()) {
            FileSelectionUtils.selectView(itemView, selectionIndex, getActivity());
        }
    }

    public void fillIn(final List<FileItemInfo> selectedFiles) {

        final int count = selectedFiles.size();
        for (int i = 0; i < count; ++i) {

            final FileItemInfo fileItemInfo = selectedFiles.get(i);
            final int itemIndex = adapter.getPosition(fileItemInfo);
            if (itemIndex != -1) {

                final FileItemInfo newFileItemInfo = adapter.getItem(itemIndex);
                newFileItemInfo.selectionOrder = i;
                selectedFiles.set(i, newFileItemInfo);
            }
        }
    }

    private View getItemViewAt(final int index) {

        if (useGridMode()) {
            // GridView does not have special header views
            final int firstPosition = itemsGridView.getFirstVisiblePosition();
            final int wantedChild = index - firstPosition;

            return (wantedChild < 0 || wantedChild >= itemsGridView.getChildCount()) ?
                    null :
                    itemsGridView.getChildAt(wantedChild);
        } else {
            final int firstPosition = itemsListView.getFirstVisiblePosition() - itemsListView.getHeaderViewsCount();
            final int wantedChild = index - firstPosition;

            return (wantedChild < 0 || wantedChild >= itemsListView.getChildCount()) ?
                    null :
                    itemsListView.getChildAt(wantedChild);
        }
    }

    public void notifySelectError() {

    }

    public void clearSelection(){
        Log.vf(TAG, "clearing selection");
        for (int i = 0; i < adapter.getCount(); ++i) {

            adapter.getItem(i).selectionOrder = FileItemInfo.UNSELECTED;
            setSelectedIfShown(i, FileItemInfo.UNSELECTED);
        }

        setSelectedFiles();
    }

    private void initToolbar(){;
        toolbar.setTitle(filePickerTitle);

        // Manually setting icon + action
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(v -> {
            // goUp or finish if top level
            if (!goUp()){
                finish();
            }
        });

        // Inflate menu
        toolbar.inflateMenu(R.menu.filepicker_display);
        refreshDisplayModeIcon();

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.sort_order) {
                AnalyticsReporter.from(FilePickerFragment.this).buttonClick(AppButtons.FILE_PICKER_SORT);
                showSortDialog();
                return true;
            } else if (item.getItemId() == R.id.display_mode) {
                AnalyticsReporter.from(FilePickerFragment.this).buttonClick(AppButtons.FILE_PICKER_CHANGE_DISPLAY_MODE);
                if (displayMode == DisplayMode.GRID) {
                    changeDisplayMode(DisplayMode.LIST);
                } else {
                    changeDisplayMode(DisplayMode.GRID);
                }
                restartFileLoader();
                return true;
            }
            return false;
        });
    }

    private void showSortDialog(){
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.title(R.string.file_picker_sorting_prompt)
                .items(R.array.file_picker_sorting_array)
                .itemsCallbackSingleChoice(getSortListIndex(), (dialog, itemView, which, text) -> {
                    switch (which) {
                        case 0:
                            sortingAscending = true;
                            sortingType = SortingType.ALPHABET;
                            break;
                        case 1:
                            sortingAscending = false;
                            sortingType = SortingType.ALPHABET;
                            break;
                        case 2:
                            sortingAscending = true;
                            sortingType = SortingType.DATE;
                            break;
                        case 3:
                            sortingAscending = false;
                            sortingType = SortingType.DATE;
                            break;
                        default:
                            sortingAscending = true;
                            sortingType = SortingType.ALPHABET;
                    }
                    restartFileLoader();
                    return true;
                });
        builder.build().show();
    }

    private void refreshDisplayModeIcon(){
        try {
            toolbar.getMenu().getItem(1).setIcon(getDisplayModeIconRes());
        } catch (Exception e){
            Log.ef(TAG, e, "refreshDisplayModeIcon; error");
        }
    }

    private int getDisplayModeIconRes(){
        if (!useGridMode()){
            return R.drawable.ic_view_comfy_black_24px;
        } else {
            return R.drawable.ic_view_stream_black_24px;
        }
    }

    private int getSortListIndex(){
        if (sortingType == SortingType.ALPHABET){
            return sortingAscending ? 0 : 1;
        } else {
            return sortingAscending ? 2 : 3;
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    // Options
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        // Attach file
        menu.add(Menu.NONE, 0, Menu.NONE, R.string.select_file)
                .setIcon(R.drawable.ic_attach_file_black_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Call
        if (Compatibility.isCallSupported()) {
            menu.add(Menu.NONE, 0, Menu.NONE, R.string.conversation_call_button)
                    .setIcon(R.drawable.call_contact)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    private void enableFileSorting(final boolean enable) {
        Log.vf(TAG, "enableFileSorting; [%s]", enable);
        try {
            toolbar.getMenu().getItem(0).setVisible(enable);
        } catch (Exception e){
            Log.ef(TAG, "enableFileSorting; cannot set visibility");
        }
    }

    private void enableFastScrolling(final boolean enable){
        Log.vf(TAG, "EnableFastScrollVisibility; [%s]", enable);
        if (useGridMode()) {
            itemsGridView.setFastScrollEnabled(enable);
            itemsGridView.setFastScrollAlwaysVisible(enable);
        } else {
            itemsListView.setFastScrollEnabled(enable);
            itemsListView.setFastScrollAlwaysVisible(enable);
        }
    }

    private void selectionMenuItemClicked(int id) {
        switch (id) {
            case R.id.clear:
                pickManager.clearSelection();
            case R.id.send:
                signalAction(null, Action.SEND, true);
                break;
            case R.id.filepicker_open:
                // OPEN action will be only available if exactly one file is selected
                if (pickManager.getSelectedFileItemInfos().size() == 1){
                    openFile(pickManager.getSelectedFileItemInfos().get(0));
                }
                break;
            case R.id.filepicker_delete:
                signalAction(pickManager.getSelectedFileItemInfos(), Action.DELETE, false);
                Log.df(TAG, "Delete " + pickManager.getSelectedFileItemInfos());
                break;
            case R.id.filepicker_decrypt:
                signalAction(pickManager.getSelectedFileItemInfos(), Action.DECRYPT, false);
                Log.df(TAG, "Decrypt " + pickManager.getSelectedFileItemInfos());
                break;
        }
    }

    @Override
    public boolean actionFinished() {
        pickManager.dropMissingFiles(getActivity().getContentResolver());
        setSelectedFiles();
        restartFileLoader();
        return true;
    }

    private void signalAction(List<FileItemInfo> files, Action action, boolean shouldFinish) {
        if (files == null) {
            if (pickManager == null) {
                return;
            }
            files = pickManager.getSelectedFileItemInfos();
        }
        if (files.isEmpty()) {
            return;
        }
        try {
            // Try set listener.
            boolean signaled = false;

            if (listener != null){
                Log.v(TAG, "Signaling via listener");
                listener.onFileItemInfosChosen(files, action);
                signaled = true;
            }

            Intent intent = null;
            if (!signaled) {
                intent = new Intent();
                intent.putParcelableArrayListExtra(Intents.EXTRA_FILES_PICKED_PATHS, new ArrayList<>(files));
                intent.putExtra(Intents.EXTRA_FILES_PICKED_ACTION, action);
            }

            // If no listener was set, try target fragment.
            if (!signaled){
                final Fragment fragment = getTargetFragment();
                if (fragment != null) {
                    Log.v(TAG, "Signaling via target fragment");
                    fragment.onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
                    signaled = true;
                }
            }

            // If target fragment was not set, try parent activity, it may be the listener.
            // If not, just pass result if is non-null.
            if (!signaled){
                final Activity act = getActivity();
                if (act instanceof FilePickerFragment.OnFilesChosenListener){
                    Log.v(TAG, "Signaling via detected listener (activity)");
                    final FilePickerFragment.OnFilesChosenListener tmpListener = (FilePickerFragment.OnFilesChosenListener) act;
                    tmpListener.onFileItemInfosChosen(files, action);
                    signaled = true;

                } else if (act != null){
                    Log.v(TAG, "Signaling via activity result");
                    act.setResult(FilePickerFragment.FILEPICKER_RESULT_CODE, intent);
                    signaled = true;
                }
            }

            if (!signaled){
                Log.e(TAG, "Could not signal file for action " + action);
            }

            if (shouldFinish) {
                finish();
            }

        } catch (Exception ex) {
            Log.e(TAG, "Cannot signal user action", ex);
        }
    }

    private void setSelectedFiles() {
        if (!isAdded()) {
            return;
        }

        if (pickManager == null || selectionBar == null) {
            return;
        }
        selectionBar.findViewById(R.id.disabler)
                .setVisibility(pickManager.getSelectedFilesCount() > 0 ? View.INVISIBLE : View.VISIBLE);

        if (filePickerType == FilePickerType.MANAGE_FILES) {
            sendMenu.setVisibility(View.GONE);

            selectionBar.findViewById(R.id.filepicker_decrypt)
                    .setVisibility(pickManager.hasAnyEncrypted() ? View.VISIBLE : View.GONE);

            selectionBar.findViewById(R.id.filepicker_open)
                    .setVisibility(pickManager.getSelectedFilesCount() <= 1 ? View.VISIBLE : View.GONE);

            selectedSizeText.setText(String.format("%d", pickManager.getSelectedFilesCount()));
            limitText.setVisibility(View.GONE);

            return;
        }

        if (filePickerType == FilePickerType.SEND_FILES) {
            managerMenu.setVisibility(View.GONE);
            if (pickManager.hasFilesLimit()){
                limitText.setVisibility(View.VISIBLE);
                limitText.setText(String.format("%d / %d", pickManager.getSelectedFilesCount(), pickManager.getFilesLimit()));
            } else {
                limitText.setVisibility(View.GONE);
            }

            boolean sendingEnabled = true;
            if (pickManager.getSelectedFilesCount() == 0){
                sendingEnabled = false;
            }
            if (pickManager.selectionExceededMaxSize()) {
                selectedSizeText.setTextColor(getResources().getColor(R.color.material_red_500));
                sendingEnabled = false;
            } else {
                selectedSizeText.setTextColor(LayoutUtils.getSecondaryTextColor(getActivity()));
            }
            if (pickManager.selectionExceededFilesLimit()){
                sendingEnabled = false;
                limitText.setTextColor(getResources().getColor(R.color.material_red_500));
            } else {
                limitText.setTextColor(LayoutUtils.getSecondaryTextColor(getActivity()));
            }
            if (!sendingEnabled){
                sendButton.setEnabled(false);
                sendButton.setImageResource(R.drawable.ic_send_black_24px_disabled);
            } else {
                sendButton.setEnabled(true);
                sendButton.setImageResource(R.drawable.ic_send_black_24px);
            }

            selectedSizeText.setText(String.format("%s / %s",
                    FileSizeReprepresentation.bytesToRepresentation(pickManager.getSelectedFilesSize()).toString(),
                    FileSizeReprepresentation.bytesToRepresentation(FilePickManager.MAXIMUM_BYTES_TO_PICK).toString()));
        }
    }

    private void openFile(FileItemInfo info) {
        FileStorageUri uri = FileStorageUri.fromFileItemInfo(info);
        if (FileUtils.isImage(uri.getFilename()) && MiscUtils.fileExistsAndIsAfile(uri.getAbsolutePath())) {
            // this will start slideshow on all files in the directory
//            List<FileStorageUri> uris = new ArrayList<>();
//            for (int i = 0; i < adapter.getCount(); i++) {
//                uris.add(FileStorageUri.fromFileItemInfo(adapter.getItem(i)));
//            }
//            int position = uris.indexOf(uri);
//            FileManager.openSlideshow(uris, position, getActivity());

            List<FileStorageUri> uris = FileStorageUri.fromFileItemInfos(pickManager.getSelectedFileItemInfos());
            List<FileStorageUri> images = new ArrayList<>();
            for (FileStorageUri fromSelection : uris) {
                if (FileUtils.isImage(fromSelection.getFilename()) && MiscUtils.fileExistsAndIsAfile(fromSelection.getAbsolutePath())) {
                    images.add(fromSelection);
                }
            }
            int position = uris.indexOf(uri);
            FileManager.openSlideshow(uris, position, getActivity());
        } else {
            ArrayList<FileItemInfo> infos = new ArrayList<>();
            infos.add(info);
            signalAction(infos, Action.DECRYPT_AND_OPEN, false);
        }
    }

    private void finish() {
        if (filePickerType == FilePickerType.SEND_FILES) {
            getFragmentManager().popBackStack();
        } else if (filePickerType == FilePickerType.MANAGE_FILES) {
            getActivity().finish();
        }
    }

    public enum Action {
        NONE,
        CANCEL,
        SEND,
        DELETE,
        DELETE_CLONE,
        DECRYPT,
        DECRYPT_AND_OPEN
    }
}