package net.phonex.ui.fileManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import net.phonex.PhonexSettings;
import net.phonex.core.IService;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pref.PreferencesManager;
import net.phonex.service.XService;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.sendFile.FileItemInfo;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.ui.sendFile.MediaType;
import net.phonex.util.Log;

import java.io.File;
import java.util.List;

/**
 * Created by Matus on 7/2/2015.
 */
public class FileManagerActivity extends LockActionBarActivity implements FilePickerFragment.OnFilesChosenListener {

    private static final String TAG = "FileManagerActivity";

    public static final String EXTRA_START_PATH = "extra_start_path";
    public static final String EXTRA_SECURE_STORAGE_ONLY = "extra_secure_storage_only";

    private IService service;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            try {
                service = IService.Stub.asInterface(arg1);
                Log.inf(TAG, "service: onServiceConnected; FileManagerActivity=%s", FileManagerActivity.this);
            } catch(Exception e){
                Log.e(TAG, "service: Exception onServiceConnected", e);
            }

            service = IService.Stub.asInterface(arg1);
            Log.i(TAG, "SIP Service is connected.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load language set in preferences
        PhonexSettings.loadDefaultLanguage(this);

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            String startingPath = null;
            boolean secureStorageOnly = false;
            if (intent != null) {
                if (intent.hasExtra(EXTRA_SECURE_STORAGE_ONLY) && intent.getBooleanExtra(EXTRA_SECURE_STORAGE_ONLY, false)) {
                    File secureStorageRoot = PreferencesManager.getRootSecureStorageFolder(this);
                    if (secureStorageRoot == null) {
                        // we could not create secure storage directory, something is wrong
                        // show file picker instead, better than just closing the activity
                        secureStorageOnly = false;
                    } else {
                        startingPath = secureStorageRoot.getAbsolutePath();
                        secureStorageOnly = true;
                    }
                } else if (intent.hasExtra(EXTRA_START_PATH)) {
                    startingPath = intent.getStringExtra(EXTRA_START_PATH);
                }
            }

            FilePickerFragment filePickerFragment = FilePickerFragment
                    .newInstance(this, this, FilePickerFragment.FilePickerType.MANAGE_FILES, startingPath,
                            secureStorageOnly ? MediaType.SECURE_STORAGE_ONLY : MediaType.ALL_FILES);
            getFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, filePickerFragment, FilePickerFragment.THIS_FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(getApplicationContext(), XService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (service != null) {
            unbindService(connection);
        }
    }

    @Override
    public void onBackPressed() {
        FilePickerFragment filePickerFragment =
                (FilePickerFragment) getFragmentManager().findFragmentByTag(FilePickerFragment.THIS_FRAGMENT_TAG);
        if (filePickerFragment != null) {
            // true if went up a directory, false if cannot go up and event needs to be handled
            boolean upped = filePickerFragment.goUp();
            if (!upped) {
                finish();
            }
        }
    }

    @Override
    public void onFileItemInfosChosen(List<FileItemInfo> files, FilePickerFragment.Action action) {
        Log.d(TAG, "onFileItemInfosChosen() " + action);
        FileManager fileManager =
                new FileManager(service,
                        this,
                        (FilePickerFragment) getFragmentManager().findFragmentByTag(FilePickerFragment.THIS_FRAGMENT_TAG));
        if (FilePickerFragment.Action.DECRYPT == action) {
            fileManager.promptDecryptFiles(FileStorageUri.fromFileItemInfos(files), true);
        } else if (FilePickerFragment.Action.DELETE == action) {
            fileManager.promptDeleteFiles(FileStorageUri.fromFileItemInfos(files));
        } else if (FilePickerFragment.Action.DECRYPT_AND_OPEN == action) {
            fileManager.openFile(FileStorageUri.fromFileItemInfo(files.get(0)), this);
        } else {
            Log.df(TAG, "MessageFragment cannot handle action " + action);
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
