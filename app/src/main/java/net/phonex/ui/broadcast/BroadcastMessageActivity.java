package net.phonex.ui.broadcast;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.tokenautocomplete.TokenCompleteTextView;

import net.phonex.R;
import net.phonex.accounting.PermissionLimits;
import net.phonex.camera.CameraActivity;
import net.phonex.core.IService;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.service.messaging.AmpDispatcher;
import net.phonex.ui.chat.AttachmentAdapter;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.inapp.ExpiredLicenseDialogs;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.sendFile.FileItemInfo;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.ui.sendFile.MediaType;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.StorageUtils;
import net.phonex.util.analytics.AppButtons;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Activity taking care of Message broadcast and forwarding
 * Created by miroc on 22.2.15.
 */
public class BroadcastMessageActivity extends LockActionBarActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, SipContactCompletionView.TokenListener, FilePickerFragment.OnFilesChosenListener, DefaultServiceConnector.ServiceConnectorListener {
    public static final String TAG = "BroadcastMessageActivity";

    public static final String EXTRA_CLISTS = "extra_clists";
    public static final String EXTRA_FORWARDED_SIP_MESSAGE = "extra_forwarded_sip_message";
    public static final String EXTRA_FORWARDED_FILE_URIS = "extra_forwarded_file_uris";
    public static final String EXTRA_FORWARDED_FILES = "extra_forwarded_files";
    public static final String EXTRA_FILTER_STRING = "extra_filter_string";

    private static final String FILE_PICKER_FRAGMENT_TAG = "file_picker_fragment_tag";

    @InjectView(R.id.my_toolbar) Toolbar toolbar;
    @InjectView(R.id.file_list) ListView fileList;
    @InjectView(R.id.embedded_text_editor) EditText messageInput;
    @InjectView(R.id.message_label) TextView messageLabel;
    // Message limit
    @InjectView(R.id.limit) TextView limitText;
    @InjectView(R.id.limit_explanation) TextView limitExplanationText;
    @InjectView(R.id.limit_container) ViewGroup limitContainer;

    private DefaultServiceConnector serviceConnector;
    private SipClistCursorAdapter completionAdapter;
    private ForwardedFilesAdapter fileListAdapter;
    private SecureRandom secureRandom = new SecureRandom();
    private SipMessage forwardedMessage = null;
    private ArrayList<String> forwardedFiles = null;
    private ArrayList<SipClist> contacts = new ArrayList<>();
    private SipContactCompletionView completionView;
    private int cachedMessagesDayLimit = -1;

    public static Bundle getArgumentsForBroadcastMessage(ArrayList<SipClist> contacts){
        Bundle b = new Bundle();
        b.putParcelableArrayList(EXTRA_CLISTS, contacts);
        return b;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.broadcast_message_activity);
        ButterKnife.inject(this);

        // connect service
        serviceConnector = new DefaultServiceConnector(this, this);
        serviceConnector.connectService(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initCompletionAdapter();
        initCompletionView();

        // restore state
        if (savedInstanceState == null){
            forwardedMessage = getIntent().getParcelableExtra(EXTRA_FORWARDED_SIP_MESSAGE);
            if (forwardedMessage != null){
                messageInput.setText(forwardedMessage.getBody());
            }

            retrieveForwardedFiles(getIntent());

            ArrayList<SipClist> receivedCLists = getIntent().getParcelableArrayListExtra(EXTRA_CLISTS);
            if (receivedCLists!=null){
                contacts = receivedCLists;
            }
            // Populate contacts
            for(SipClist contact : contacts){
                Log.df(TAG, "onCreate; contact [%s]", contact.getDisplayName());
                completionView.addObject(contact);
            }
        } else {
            contacts = savedInstanceState.getParcelableArrayList(EXTRA_CLISTS);
            forwardedMessage = savedInstanceState.getParcelable(EXTRA_FORWARDED_SIP_MESSAGE);
            forwardedFiles = savedInstanceState.getStringArrayList(EXTRA_FORWARDED_FILES);
        }

        if (forwardedMessage != null){
            getSupportActionBar().setTitle(getString(R.string.msg_forward));
        } else {
            getSupportActionBar().setTitle(getString(R.string.broadcast_title));
        }

        toggleFileForwardingView(isFilesForwarding());

        // Listener for Send action when textEdit is in horizontal editing mode.
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                return sendBroadcast();
            }
            return false;
        });
        initLimitWarning(isFilesForwarding());

        getLoaderManager().initLoader(0, null, this);
    }

    private void initLimitWarning(boolean hasFilesToForward){
        if (hasFilesToForward){
            updateFilesLimitWarning();
        } else {
            updateMessageLimitWarning();
        }
    }

    private boolean isFilesForwarding(){
        return forwardedFiles != null && forwardedFiles.size() > 0;
    }

    private void updateMessageLimitWarning(){
        int messagesDayLimit = PermissionLimits.getMessagesDayLimit(getContentResolver());
        cachedMessagesDayLimit = messagesDayLimit;
        Log.vf(TAG, "initLimitWarningCount; messagesDayLimit=%d", messagesDayLimit);

        if (messagesDayLimit >= 0){
            int messageCountSentLastDay = TrialEventLog.getOutgoingMessageCount(this, 1);

            String explanation = String.format(getString(R.string.trial_message_limit_explanation), messagesDayLimit);
            limitExplanationText.setText(explanation);
            limitText.setText(messageCountSentLastDay + "/" + messagesDayLimit);
            limitContainer.setBackgroundColor(
                    getResources().getColor(
                            messageCountSentLastDay >= messagesDayLimit ? R.color.material_red_500 : R.color.material_green_500)
            );
            limitContainer.setVisibility(View.VISIBLE);
        }
    }

    private void updateFilesLimitWarning(){
        int filesLimit = PermissionLimits.getFilesLimit(getContentResolver());
        if (filesLimit >= 0){
            String explanation = String.format(getString(R.string.files_limit_explanation), filesLimit);
            limitExplanationText.setText(explanation);

            int currentCheckedFilesLimit = fileList.getCheckedItemCount();

            limitText.setText(currentCheckedFilesLimit + "/" + filesLimit);
            limitContainer.setBackgroundColor(
                    getResources().getColor(currentCheckedFilesLimit > filesLimit ? R.color.material_red_500 : R.color.material_green_500)
            );
            limitContainer.setVisibility(View.VISIBLE);
        }
    }


    private void toggleFileForwardingView(boolean on){
        if (on){
            fileList.setVisibility(View.VISIBLE);
            messageInput.setVisibility(View.GONE);
            messageLabel.setText(R.string.broadcast_files);

            initFileListAdapter();

        } else {
            fileList.setVisibility(View.GONE);
            messageInput.setVisibility(View.VISIBLE);
            messageLabel.setText(R.string.broadcast_message);
        }
    }

    private void initFileListAdapter() {
        fileListAdapter = new ForwardedFilesAdapter(this, R.layout.filechooser_item, forwardedFiles);
        fileList.setAdapter(fileListAdapter);
        fileList.setOnItemClickListener((parent, view, position, id) -> updateFilesLimitWarning());

        for ( int i=0; i< fileListAdapter.getCount(); i++ ) {
            fileList.setItemChecked(i, true);
        }
    }

    private void showAlertMissingFiles(List<String> missingFiles){
                StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.upd_failed_some_files_missing_desc));
        for (String badPath : missingFiles){
            FileStorageUri uri = new FileStorageUri(badPath);
            sb.append(uri.getFilename()).append("\n");
        }
        AlertDialogFragment.alert(this, getString(R.string.upd_failed_some_files_missing_title), sb.toString());
    }


    /**
     * @param intent
     * @return true if there are some files to forward in intent bunde
     */
    private boolean retrieveForwardedFiles(Intent intent) {
        ArrayList<String> originalFileUris = intent.getStringArrayListExtra(EXTRA_FORWARDED_FILE_URIS);
        if (originalFileUris == null || originalFileUris.size()==0){
            return false;
        }

        ArrayList<String> fileUris = new ArrayList<>();
        ArrayList<String> missingFiles = new ArrayList<>();
        for (final String fileUri : originalFileUris) {
            if (!StorageUtils.existsUri(fileUri, getContentResolver())) {
                missingFiles.add(fileUri);
                Log.wf(TAG, "retrieveForwardedFiles; file with uri [%s] doesn't exist", fileUri);
            } else {
                fileUris.add(fileUri);
            }
        }

        if (fileUris.size() == 0){
            // close
            AlertDialogFragment
                    .newInstance(getString(R.string.upd_failed_some_files_missing_title), getString(R.string.upd_failed_all_files_missing))
                    .setNeutralButton(getString(R.string.ok), (dialog, which) -> finish())
                    .show(getFragmentManager(), "alert");
            return false;
        } else if (missingFiles.size() > 0){
            showAlertMissingFiles(missingFiles);
        }

        forwardedFiles = fileUris;
        return true;
    }

    private void initCompletionView(){
        completionView = (SipContactCompletionView) findViewById(R.id.recipients_view);
        completionView.setAdapter(completionAdapter);
        completionView.setDeletionStyle(TokenCompleteTextView.TokenDeleteStyle.Clear);
        completionView.setTokenListener(this);
        completionView.allowDuplicates(false);
        completionView.setThreshold(1);

    }

    private void initCompletionAdapter(){
        completionAdapter = new SipClistCursorAdapter(this, android.R.layout.simple_list_item_1, null,
                new String[] {SipClist.FIELD_DISPLAY_NAME},
                new int[] {android.R.id.text1}, 0);
        completionAdapter.setCursorToStringConverter(cursor -> {
            if (cursor == null || cursor.isClosed()) {
                return "";
            }

            SipClist clist = new SipClist(cursor);
            return clist.getDisplayName();
        });

//      https://stackoverflow.com/questions/9742017/changing-device-orientation-crashes-activity-when-using-autocompletetextview-and
        completionAdapter.setFilterQueryProvider(new FilterQueryProvider() {
            public Cursor runQuery(CharSequence constraint) {
                if (constraint != null) {
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(EXTRA_FILTER_STRING, constraint);
                    getLoaderManager().restartLoader(0, bundle, BroadcastMessageActivity.this);
                } else {
                    getLoaderManager().restartLoader(0, null, BroadcastMessageActivity.this);
                }
                return getContentResolver().query(SipClist.CLIST_STATE_URI,
                        SipClist.CONTACT_LIST_PROJECTION, null, null, null);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(EXTRA_CLISTS, contacts);
        outState.putParcelable(EXTRA_FORWARDED_SIP_MESSAGE, forwardedMessage);
        outState.putStringArrayList(EXTRA_FORWARDED_FILES, forwardedFiles);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm != null){
            if (fm.getBackStackEntryCount() > 0){
                getFragmentManager().popBackStack();
                return;
            }
        }
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.v(TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        if (forwardedMessage != null){
            inflater.inflate(R.menu.forward_message_menu, menu);
        } else {
            inflater.inflate(R.menu.broadcast_message_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
            case R.id.option_attach_file: {
                getAnalyticsReporter().buttonClick(AppButtons.BROADCAST_ACTIVITY_ADD_ATTACHMENT);
                LayoutUtils.hideSwKeyboard(this);

                if (contacts.size() == 0) {
                    Toast.makeText(this, R.string.broadcast_empty_recipients_list, Toast.LENGTH_SHORT).show();
                    return false;
                }

                int filesLimit = PermissionLimits.getFilesLimit(getContentResolver());
                if (filesLimit <= 0) {
                    ExpiredLicenseDialogs.showFilesLimitPrompt(this);
                    return false;
                }

                MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
                builder.adapter(new AttachmentAdapter(this, R.layout.attachment_item), new MaterialDialog.ListCallback() {
                    public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                        Log.df(TAG, "onSelection() itemView.getId() = [%d]", itemView.getId());
                        dialog.dismiss();
                        switch (which) {
                            case 0:
                                takePhoto();
                                break;
                            case 1:
                                attachFile(MediaType.IMAGES);
                                break;
                            case 2:
                                attachFile(MediaType.ALL_FILES);
                                break;
                            default:
                                break;
                        }
                    }
                });
                builder.build().show();

                LayoutUtils.hideSwKeyboard(this);
                break;
            }
            case R.id.option_send: {
                if (isFilesForwarding()) {
                    int filesLimit = PermissionLimits.getFilesLimit(getContentResolver());
                    if (filesLimit >= 0 && fileList.getCheckedItemCount() > filesLimit) {
                        ExpiredLicenseDialogs.showFilesLimitPrompt(this);
                        return false;
                    }
                } else {
                    if (PermissionLimits.isMessageLimitExceeded(this)) {
                        ExpiredLicenseDialogs.showMessageLimitPrompt(this);
                        return false;
                    }
                }

                getAnalyticsReporter().buttonClick(AppButtons.BROADCAST_ACTIVITY_SEND);
                return sendBroadcast();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void takePhoto() {
        Intent it = new Intent(this, CameraActivity.class);
        it.putExtra(CameraActivity.EXTRA_SINGLE_PHOTO, true);
        startActivityForResult(it, CameraActivity.REQUEST_CODE);
    }

    private void attachFile(MediaType mediaType) {
        try {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            FilePickerFragment fragment = FilePickerFragment.newInstance(
                    this, this, FilePickerFragment.FilePickerType.SEND_FILES, null, mediaType);
            ft.replace(android.R.id.content, fragment, FILE_PICKER_FRAGMENT_TAG);
            ft.addToBackStack(FILE_PICKER_FRAGMENT_TAG);
            ft.commit();
        } catch(Exception ex){
            Log.e(TAG, "Cannot add filepicker fragment", ex);
        }
    }

    private boolean sendBroadcast(){
        if (forwardedFiles!=null && forwardedFiles.size() > 0){
            forwardFiles();

        } else { // Sending text
            String text = messageInput.getText().toString();
            if (TextUtils.isEmpty(text)){
                return false;
            }
            if (contacts.size() == 0){
                Toast.makeText(this, R.string.broadcast_empty_recipients_list, Toast.LENGTH_SHORT).show();
                return false;
            }

            sendMessage(text);
        }

        return true;
    }

    private void showSendingInfoAndFinish(){
        Toast.makeText(this, R.string.broadcast_message_is_being_sent, Toast.LENGTH_LONG).show();
        finish();
    }

    // TODO do in background
    private void sendMessage(String text) {

        SipProfile acc = SipProfile.getProfileFromDbId(this, SipProfile.USER_ID, SipProfile.ACC_PROJECTION);
        if (acc==null || acc.getId() == SipProfile.INVALID_ID){
            Log.ef(TAG, "Cannot send message, user is null [%s]", acc);
            return;
        }

        final Date date = new Date();
        String mySip = SipUri.getCanonicalSipContact(acc.getSipUserName() + "@" + acc.getSipDomain(), false);

        for (SipClist contact : contacts){
            final String remoteSip = SipUri.getCanonicalSipContact(contact.getSip(), false);
            sendSingleMessage(text, mySip, remoteSip, date);
        }

        if (cachedMessagesDayLimit >= 0){
            TrialEventLog.logOutgoingMessage(this);
        }

        showSendingInfoAndFinish();
    }

    private void sendSingleMessage(String textToSend, String mySip, String remoteSip, Date date) {
        try {
            // Save new message to the database.
            // Message manager will take care of it.
            SipMessage msg = new SipMessage(
                    mySip,
                    remoteSip,
                    remoteSip,
                    textToSend,
                    SipMessage.MIME_TEXT,
                    date.getTime(),
                    SipMessage.MESSAGE_TYPE_QUEUED,
                    remoteSip);
            msg.setOutgoing(true);
            msg.setRead(false);
            msg.setRandNum(secureRandom.nextInt());
            Log.df(TAG, "Inserting SipMessage in DB [%s]", msg);
            Uri lastInsertedUri = getContentResolver().insert(SipMessage.MESSAGE_URI, msg.getContentValues());
            // now dispatch the message (= put in MessageQueue)
            AmpDispatcher.dispatchTextMessage(this, (int) ContentUris.parseId(lastInsertedUri));
            Log.d(TAG, "Message stored to the message queue.");

        } catch (Exception e) {
            Log.e(TAG, "Not able to send message", e);
        }
    }

    // TODO @Override
    public void onFilesChosen(List<String> files) {
        IService service = serviceConnector.getService();
        if (service == null){
            Log.e(TAG, "XService is null, cannot run task and send file");
            return;
        }

        if (contacts.size() == 0){
            Toast.makeText(this, R.string.broadcast_empty_recipients_list, Toast.LENGTH_LONG).show();
            return;
        }

        for (String file : files) {
            if (!StorageUtils.existsUri(file, getContentResolver())) {
                AlertDialogFragment.alert(this, R.string.msg_sending_failed, R.string.some_files_were_deleted);
                return;
            }
        }

        for (SipClist contact : contacts){
            sendFiles(service, files, contact.getSip(), null);
        }

        showSendingInfoAndFinish();
    }

    @Override
    public void onFileItemInfosChosen(List<FileItemInfo> files, FilePickerFragment.Action action) {
        if (FilePickerFragment.Action.SEND != action) {
            Log.df(TAG, "BroadcastMessageActivity cannot handle action " + action);
            return;
        }
        IService service = serviceConnector.getService();
        if (service == null){
            Log.e(TAG, "XService is null, cannot run task and send file");
            return;
        }

        for (final FileItemInfo file : files) {
            if (!StorageUtils.existsUri(file.getUri().toString(), getContentResolver())) {
                AlertDialogFragment.alert(this, R.string.msg_sending_failed, R.string.some_files_were_deleted);
                return;
            }
        }
        final List<String> fileUris = new ArrayList<>();
        for (final FileItemInfo file : files) {
            fileUris.add(file.getUri().toString());
        }

        if (contacts.size() == 0){
            Toast.makeText(this, R.string.broadcast_empty_recipients_list, Toast.LENGTH_LONG).show();
            return;
        }

        for (SipClist contact : contacts){
            sendFiles(service, fileUris, contact.getSip(), null);
        }

        showSendingInfoAndFinish();
    }

    // assume forwardedFiles is not null and size>0
    private void forwardFiles() {
        List<String> filesToSend = new ArrayList<>();
        List<String> missingFileNames = new ArrayList<>();

        SparseBooleanArray checkedItemPositions = fileList.getCheckedItemPositions();

        int pos = 0;
        for (String fileUri : forwardedFiles){
            if (!checkedItemPositions.get(pos)){
                // skip unchecked items
            } else {
                if (StorageUtils.existsUri(fileUri, getContentResolver())) {
                    filesToSend.add(fileUri);
                } else {
                    missingFileNames.add(fileUri);
                }
            }
            pos++;
        }

        if (fileList.getCheckedItemCount() == 0){
            Toast.makeText(this, R.string.no_files_selected, Toast.LENGTH_SHORT).show();
            return;
        } else if (missingFileNames.size() > 0){
            showAlertMissingFiles(missingFileNames);
            return;
        }

        onFilesChosen(filesToSend);
        showSendingInfoAndFinish();
    }

    private void sendFiles(final IService service, final List<String> filePaths, final String remoteSip, final String text) {
        try {
            service.sendFiles(remoteSip, filePaths, text);
        } catch (RemoteException e) {
            Log.ef(TAG, e, "error sending file");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == CameraActivity.REQUEST_CODE) {
            if (resultCode == CameraActivity.PHOTO_ACCEPTED) {
                if (intent == null){
                    Log.e(TAG, "Result from file camera is empty");
                    return;
                }
                FileStorageUri uri = intent.getParcelableExtra(CameraActivity.EXTRA_URI);
                if (uri == null) {
                    Log.e(TAG, "Result from file camera is missing uri");
                    return;
                }
                // this is quick solution, it would be much better to change file sending to uris now
                List<String> uris = new ArrayList<>();
                uris.add(uri.toString());
                onFilesChosen(uris);
            } else if (resultCode == CameraActivity.PHOTO_CANCELED) {
                Log.d(TAG, "User cancelled photo");
            } else if (resultCode == CameraActivity.PHOTO_ERROR) {
                Log.d(TAG, "Error taking or saving photo");
            }
        } else {
            Log.df(TAG, "Unknown request code [%d]", requestCode);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        FilePickerFragment filePickerFragment =
                (FilePickerFragment) getFragmentManager().findFragmentByTag(FilePickerFragment.THIS_FRAGMENT_TAG);
        if (filePickerFragment != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                // true if went up a directory, false if cannot go up and event needs to be handled
                if (!filePickerFragment.goUp()) {
                    getFragmentManager().popBackStack();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.v(TAG, "onCreateLoader()");
        String selection = null;
        String[] selectionArgs = null;

        if (args != null){
            CharSequence constraint = args.getCharSequence(EXTRA_FILTER_STRING);
            selection = SipClist.FIELD_DISPLAY_NAME + " LIKE ?";
            selectionArgs = new String[]{"%" + constraint + "%"};
        }

        return new CursorLoader(this,
                SipClist.CLIST_STATE_URI,
                SipClist.CONTACT_LIST_PROJECTION, selection, selectionArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        completionAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        completionAdapter.swapCursor(null);
    }

    @Override
    public void onTokenAdded(Object token) {
        SipClist clist = (SipClist) token;
        if (!contacts.contains(clist)){
            contacts.add(clist);
        }
    }

    @Override
    public void onTokenRemoved(Object token) {
        SipClist clist = (SipClist) token;
        contacts.remove(clist);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serviceConnector.disconnectService(this);
    }

    @Override
    public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
        // nothing
    }

    @Override
    public void onXServiceDisconnected(ComponentName arg0) {
        // nothing
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

}
