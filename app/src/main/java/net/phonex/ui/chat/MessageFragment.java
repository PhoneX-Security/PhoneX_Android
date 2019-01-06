package net.phonex.ui.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.accounting.PermissionLimits;
import net.phonex.camera.CameraActivity;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.QueuedMessage;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.Thumbnail;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.ft.storage.DeletedByMessageListener;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ft.transfer.FileTransferManager;
import net.phonex.pub.a.Compatibility;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.service.MyPresenceManager;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.messaging.AmpDispatcher;
import net.phonex.service.messaging.MessageManager;
import net.phonex.ui.broadcast.BroadcastMessageActivity;
import net.phonex.ui.call.CallActivity;
import net.phonex.ui.chat.MessageAdapter.ViewHolder;
import net.phonex.ui.chat.compounds.FileView;
import net.phonex.ui.customViews.CursorListFragment;
import net.phonex.ui.fileManager.FileManager;
import net.phonex.ui.inapp.ExpiredLicenseDialogs;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.sendFile.FileItemInfo;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.ui.sendFile.MediaType;
import net.phonex.util.FileTransferUtils;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.SecureClipboard;
import net.phonex.util.SmileyParser;
import net.phonex.util.StorageUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;
import net.phonex.util.android.ClipboardManager;
import net.phonex.util.android.StatusbarNotifications;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class MessageFragment extends CursorListFragment implements LoaderManager.LoaderCallbacks<Cursor>,
        OnClickListener, OnMessageActionListener, FilePickerFragment.OnFilesChosenListener,
        ChatScrollListener.PixelScrollListener, DeletedByMessageListener {
	
    private static final String TAG = "MessageFragment";

    public static final int MENU_COPY   = ContextMenu.FIRST;
    public static final int MENU_DELETE = ContextMenu.FIRST + 1;
    public static final int MENU_CANCEL = ContextMenu.FIRST + 2;
    public static final int MENU_OPEN_DIRECTORY = ContextMenu.FIRST + 4;
    public static final int MENU_DELETE_FILES = ContextMenu.FIRST + 5;
    public static final int MENU_COPY_SECURE = ContextMenu.FIRST + 6;
    public static final int MENU_FORWARD = ContextMenu.FIRST + 7;

    public static final int MENU_ATTACH_FILE = ContextMenu.FIRST + 11;
    public static final int MENU_CALL = ContextMenu.FIRST + 12;

    public static final int MENU_OPEN = ContextMenu.FIRST + 14;
    public static final int MENU_DELETE_CLONE = ContextMenu.FIRST + 15;
    public static final int MENU_DECRYPT = ContextMenu.FIRST + 16;
    public static final int MENU_DELETE_FILE = ContextMenu.FIRST + 17;

    public static final String THIS_FRAGMENT_TAG = "message_fragment_tag";

    private static final String FILE_PICKER_FRAGMENT_TAG = "file_picker_fragment_tag";
    private static final String EXTRA_POSTPONED_TRANSFER = "extra_postponed_transfer";
    private static final String EXTRA_LAST_CONTACT = "extra_last_contact";
    private static final String EXTRA_SCROLL_POSITION = "extra_scroll_position";
    private static final int FILE_PICKER_CODE = FilePickerFragment.FILEPICKER_RESULT_CODE;
    private static final int MSG_SHOW_PROGRESS_FRAGMENT=0;
    private static final int MSG_FILE_NOTIFY_RECEIVED=1;

    public static final String ACTION_FILE_MESSAGE_CONTEXT_MENU = "fileMessageContextMenu";
    public static final String ACTION_TEXT_MESSAGE_CONTEXT_MENU = "textMessageContextMenu";

    private SipClist remoteContact;

    @InjectView(R.id.my_toolbar) Toolbar toolbar;

    @InjectView(R.id.embedded_text_editor) EditText bodyInput;
    @InjectView(R.id.send_button) ImageButton sendButton;
    @InjectView(R.id.limit) TextView limitText;
    @InjectView(R.id.limit_explanation) TextView limitExplanationText;
    @InjectView(R.id.limit_container) ViewGroup limitContainer;

    private SipProfile sipProfile;
    private MessageAdapter adapter;
    private IService service;
    private Context ctxt;

    private AnalyticsReporter analyticsReporter;

    private Activity activity;
    private int cachedMessagesDayLimit = -1;

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    private SecureRandom srandom;

    private final Handler handler = new LoadingHandler(this);
    private final ChatScrollListener scrollListener = new ChatScrollListener(this);

    private final ContentObserver statusContentObserver = new StatusContentObserver(new Handler());

    private ClipboardManager clipboardManager;
    private SecureClipboard secureClipboard;
    private MessagesReceiver msgReceiver;
    private ArrayList<String> fileUrisToSend;
    private final ScrollPosition lastScrollPosition = new ScrollPosition();
    private int lastCursorCount = -1;
    private Parcelable listViewState;
    private boolean viewRefreshed = false;


    // Last contact used in this fragment recovered via savedInstance.
    private String lastContactUsed;
    // if we contact is support account, do not apply any restrictions
    private boolean isSupportAccount = false;

    private int messageCountSentLastDay;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);

        ListView lv = getListView();
        lv.setOnCreateContextMenuListener(this);
        registerForContextMenu(lv);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // State is too complex, retain the instance when configuration changes
        setRetainInstance(true);

        srandom = new SecureRandom();

        SmileyParser.init(getActivity());
        clipboardManager = ClipboardManager.getInstance(getActivity());
        secureClipboard = new SecureClipboard(getActivity());

        // load remote contact
        final String contactSip = SipUri.getCanonicalSipContact(getArguments().getString(SipMessage.FIELD_FROM), false);
        remoteContact = getProfileFromDbSip(getActivity(), contactSip);
        onRemoteContactLoaded();

        Log.vf(TAG, "onCreate; remote  contact loaded [%s]", remoteContact);
        checkIfSupportAccount();

        // load profile info
        sipProfile = SipProfile.getCurrentProfile(getActivity());

        initProgress(getString(R.string.p_loading_view), getString(R.string.intro_init_title));

        // Recover state after rotation.
        if (savedInstanceState != null){
            synchronized (this){
                if (savedInstanceState.containsKey(EXTRA_LAST_CONTACT)){
                    lastContactUsed = savedInstanceState.getString(EXTRA_LAST_CONTACT);
                    Log.vf(TAG, "Last user: %s", lastContactUsed);
                }

                if (savedInstanceState.containsKey(EXTRA_SCROLL_POSITION)){
                    final ScrollPosition tmpPos = savedInstanceState.getParcelable(EXTRA_SCROLL_POSITION);
                    lastScrollPosition.copyFrom(tmpPos);
                    Log.vf(TAG, "Last scroll position: %s", lastScrollPosition);
                }

                if (savedInstanceState.containsKey(EXTRA_POSTPONED_TRANSFER)){
                    fileUrisToSend = savedInstanceState.getStringArrayList(EXTRA_POSTPONED_TRANSFER);
                    Log.v(TAG, "Postponed transfer recovered from bundle");
                }
            }
        }
    }
    
    /*******************************
     *  Waiting for the service to connect
     *******************************/
    protected ProgressDialog progressDialog;
    private void initProgress(String message, String title){
    	
		progressDialog=new ProgressDialog(getActivity());
		progressDialog.setMessage(message);
		progressDialog.setTitle(title);
		progressDialog.setCancelable(true);
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.setIndeterminate(true);
		progressDialog.setOnCancelListener(dialog -> Log.df(TAG, "Progressbar canceled"));
        progressDialog.setOnDismissListener(dialog -> Log.df(TAG, "Progressbar dismissed"));
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.message_fragment, container, false);
        ButterKnife.inject(this, v);

        initToolbar();
        updateMessageLimitCount();
        setupUiElements();
        return v;
    }

    private void setupUiElements(){
        // Listener for Send action when textEdit is in horizontal editing mode.
        bodyInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick();
                return true;
            }
            return false;
        });
    }

    private void checkIfSupportAccount(){
        PreferencesManager prefs = new PreferencesManager(getActivity());
        String supportSip = prefs.getString(PhonexConfig.SIP_SUPPORT_ACCOUNT);
        if (supportSip != null && remoteContact != null){
            Log.df(TAG, "checkIfSupportAccount; sipProfile [%s], supportAccount [%s]", remoteContact.getSip(), supportSip);
            isSupportAccount = remoteContact.getSip().equals(supportSip);
        }
    }

    private void updateMessageLimitCount(){
        Log.vf(TAG, "updateMessageLimitCount");
        if (isSupportAccount){
            return;
        }

        int messagesDayLimit = PermissionLimits.getMessagesDayLimit(getActivity().getContentResolver());
        cachedMessagesDayLimit = messagesDayLimit;
        Log.vf(TAG, "updateMessageLimitCount; messagesDayLimit=%d", messagesDayLimit);

        if (messagesDayLimit >= 0){
            messageCountSentLastDay = TrialEventLog.getOutgoingMessageCount(getActivity(), 1);

            String explanation = String.format(getString(R.string.trial_message_limit_explanation), messagesDayLimit);
            limitExplanationText.setText(explanation);
            limitText.setText(messageCountSentLastDay + "/" + messagesDayLimit);
            limitContainer.setBackgroundColor(
                    getResources().getColor(messageCountSentLastDay >= messagesDayLimit ? R.color.material_red_500 : R.color.material_green_500)
            );
            limitContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initToolbar(){
        LockActionBarActivity activity = (LockActionBarActivity) getActivity();
        if (activity != null){
            activity.setSupportActionBar(toolbar);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void registerStatusContentObserver(){
        if (remoteContact == null || remoteContact.getId() == null){
            Log.inf(TAG, "Remote contact undefined, probably deleted meanwhile %s", remoteContact);
            return;
        }

        Uri contactUri = ContentUris.withAppendedId(SipClist.CLIST_STATE_ID_URI_BASE, remoteContact.getId());
        getActivity().getContentResolver().registerContentObserver(contactUri, false, statusContentObserver);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
        	try {
	            service = IService.Stub.asInterface(arg1);
	            Log.inf(TAG, "service: onServiceConnected; msgFragment=%s", MessageFragment.this);
	            MessageFragment.this.onServiceConnected();

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
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final ListView mList = getListView();
        mList.setDivider(null);

        mList.setOnScrollListener(scrollListener);

        sendButton.setOnClickListener(this);

        if (listViewState != null) {
            mList.onRestoreInstanceState(listViewState);
            Log.vf(TAG, "View state restored: %s", listViewState);
        }

        viewRefreshed = true;
        scrollListener.onViewRecreated();
        adapter = new MessageAdapter(getActivity(), null, this);
        adapter.setActionListener(this); //providing onResend reference
        mList.setAdapter(adapter);
        mList.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        setMessagingEnabledForContact();
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ctxt = getActivity().getApplicationContext();
        getActivity().bindService(new Intent(ctxt, XService.class), connection, Context.BIND_AUTO_CREATE);

        // We have context - trigger DB check.
        MessageManager.triggerCheck(activity);
        analyticsReporter = AnalyticsReporter.from(this);

        this.activity = activity;
    }
    
    @Override
    public void onDetach() {
        unregister();
        try {
            getActivity().unbindService(connection);
        } catch (Exception e) {
            // Just ignore that
        }
        service = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.df(TAG, "Resume compose message act, code=%s, from=%s", hashCode(), remoteContact);
        PhonexSettings.loadDefaultLanguageNoThrow(getActivity());

        // Wait for service binding.
        onResumeShowProgressDialog();

        // allow sending read acks
        if (adapter != null){
            adapter.setFragmentForeground(true);
        }

        updateRemoteContactToolbarInfo();

        if (remoteContact != null){
            setNotificationFrom(remoteContact.getSip());
        }

        register();
        FileTransferManager.requestTransferProgress(ctxt, null);
        updateMessageLimitCount();
    }

    @Override
    public void onPause() {
        super.onPause();

        // prevent sending read acks
        if (adapter != null){
            adapter.setFragmentForeground(false);
        }

        // Set current active chat contact, none.
        setNotificationFrom(null);

        // Set read flag for all messages from the current chat contact.
        // Assume messages were already read so the unread notification disappears
        // from the contact list.
        if (remoteContact != null){
            final String remoteSip = SipUri.getCanonicalSipContact(remoteContact.getSip(), false);
            final Context ctxt = getActivity().getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    setMessagesRead(remoteSip, ctxt);
                }
            }).start();
        }

        listViewState = getListView().onSaveInstanceState();
        unregister();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.inf(TAG, "onSaveInstanceState;");
        synchronized (this){
            outState.putString(EXTRA_LAST_CONTACT, getCurrentSip());
            outState.putParcelable(EXTRA_SCROLL_POSITION, lastScrollPosition);

            if (fileUrisToSend != null && !fileUrisToSend.isEmpty()) {
                outState.putStringArrayList(EXTRA_POSTPONED_TRANSFER, fileUrisToSend);
                Log.v(TAG, "Postponed transfer stored in bundle");
            }
        }
    }

    private String getCurrentSip(){
        if (remoteContact == null){
            return null;
        }

        return SipUri.getCanonicalSipContact(remoteContact.getSip(), false);
    }

    private synchronized void onResumeShowProgressDialog(){
        try {
            if (service == null && progressDialog != null && !progressDialog.isShowing()) {
                // Reset contactSip for KitKat.
//                remoteContact = null;

                Log.d(TAG, "Service=null, starting waiting progressbar dialog");
                this.progressDialog.show();
            }
        } catch(Throwable t){
            Log.e(TAG, "Exception in showing dialog on start", t);
        }
    }

    private synchronized void onConnectedDismissDialog(){
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.setMessage("Done");
                progressDialog.dismiss();
            }
        } catch(Throwable e){
            Log.e(TAG, "Exception in hiding progress dialog.", e);
        }
    }

    /**
     * Service connected callback
     */
    protected void onServiceConnected(){
        onConnectedDismissDialog();
        scrollListener.onViewRecreated();
        viewRefreshed = true;

        // Setup from args
        final String contactSip = SipUri.getCanonicalSipContact(getArguments().getString(SipMessage.FIELD_FROM), false);
        setupRemoteContact();
        updateRemoteContactToolbarInfo();
        registerStatusContentObserver();
        if (adapter !=null){
            adapter.setXService(service);
        }

        // Check stored data and reset them if contact does not match.
        if (!contactSip.equals(lastContactUsed)) {
            Log.vf(TAG, "Last contact does not match, reset state; %s vs %s", contactSip, lastContactUsed);
            lastScrollPosition.reset(null);
            lastContactUsed = contactSip;
            fileUrisToSend = null;
            listViewState = null;
            scrollListener.reset();
        }

        // If there is a postponed send file request, process it (it might be waiting for service to be ready).
        handlePostponedSendFile();
    }

    /**
     * Registers progress monitoring receiver.
     */
    protected synchronized void register(){
        if (ctxt == null){
            Log.d(TAG, "Could not register, no context");
            return;
        }

        if (msgReceiver != null){
            Log.v(TAG, "Receiver already registered");
        }

        // Register broadcast receiver
        try {
            msgReceiver = getNewMessagesReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intents.ACTION_SIP_MESSAGE_RECEIVED);
            filter.addAction(Intents.ACTION_FILEUPLOAD_PROGRESS);
            filter.addAction(Intents.ACTION_FILEDOWNLOAD_PROGRESS);
            MiscUtils.registerReceiver(ctxt, msgReceiver, filter);
            Log.vf(TAG, "Receiver registration successful, %s", msgReceiver);

        } catch(Exception ex){
            Log.e(TAG, "Exception in receiver registration", ex);
            msgReceiver = null;
        }
    }

    /**
     * Unregisters progress monitoring receiver.
     */
    protected synchronized void unregister(){
        if (ctxt == null){
            Log.d(TAG, "Could not unregister, no context");
            return;
        }

        if (msgReceiver != null){
            try {
                ctxt.unregisterReceiver(msgReceiver);
                Log.vf(TAG, "Receiver unregistration successful, %s", msgReceiver);
                msgReceiver = null;

            } catch(Exception ex){
                Log.e(TAG, "Exception in receiver unregistration", ex);
                msgReceiver = null;
            }
        }
    }

    /**
     * construct bundle for passing arguments when starting MessageActivity.class via intent
     * @param sipUri - may be with or without scheme: (e.g sip:), this will be stripped out
     * @return
     */
    public static Bundle getArguments(String sipUri) {
        Bundle bundle = new Bundle();
        if (sipUri != null) {
            bundle.putString(SipMessage.FIELD_FROM, sipUri);
        }
        return bundle;
    }



    private SipProfile getCurrentUserProfile(){
    	if (sipProfile == null){
    		sipProfile = SipProfile.getProfileFromDbId(getActivity(), SipProfile.USER_ID, SipProfile.ACC_PROJECTION);
    	}
    	return sipProfile;
    }

    /**
     * Restarts data loader.
     */
    private void loadMessageContent() {
    	Log.d(TAG, "LoadingMessageContent()");

    	// Set all messages to read
    	String from = SipUri.getCanonicalSipContact(getArguments().getString(SipMessage.FIELD_FROM), false);
        setMessagesRead(from, null);

    	// Restart loader
        getLoaderManager().restartLoader(0, getArguments(), this);
    }

    /**
     * Sets all messages read from the given contact.
     * @param from
     * @param ctxt Context, can be null. Then activity is used.
     */
    private void setMessagesRead(String from, Context ctxt){
        if (TextUtils.isEmpty(from)){
            return;
        }

        try {
            Context context = ctxt;
            if (ctxt==null){
                final Activity activity = getActivity();
                if (activity!=null){
                    context = activity.getApplicationContext();
                }
            }

            if (context==null){
                Log.e(TAG, "Cannot set messages to read, context is null");
                return;
            }

            String selection = SipMessage.FIELD_FROM + "=? AND " + SipMessage.FIELD_READ + "=?";
            String[] selectionArgs = new String[]{from, "0"};

            // first extract all nonces that have been updated (to acknowledge remote contact that they are read)
            Cursor cursor = context.getContentResolver().query(SipMessage.MESSAGE_URI, new String[]{SipMessage.FIELD_RANDOM_NUM}, selection, selectionArgs, null);
            List<Integer> updatedNonces = new ArrayList<Integer>();
            try {
                if (cursor != null){
                    while (cursor.moveToNext()){
                        updatedNonces.add(cursor.getInt(0));
                    }
                }
            } finally {
                if (cursor!=null)
                    cursor.close();
            }

            // next update all unread sip-messages to read
            ContentValues args = new ContentValues();
            args.put(SipMessage.FIELD_READ, true);

            context.getContentResolver().update(SipMessage.MESSAGE_URI, args,
                    selection, selectionArgs);

            // send ack to remote contact, switch from <-> to
            String sendFrom = SipUri.getCanonicalSipContact(getCurrentUserProfile().getUriString(),false);
            String sendTo = from;
            if (updatedNonces.size() > 0){
                AmpDispatcher.dispatchReadAckNotification(context, sendFrom, sendTo, updatedNonces);
            }
        } catch(Exception ex){
            Log.e(TAG, "Exception, cannot set messages to read", ex);
        }
    }

    /**
     * Sets remote contact user is having conversation with to the notification manager.
     * @param contact
     */
    private void setNotificationFrom(String contact){
        try {
            final Activity activity = getActivity();
            final Context context = (activity==null) ? ctxt : activity.getApplicationContext();
            if (context==null){
                return;
            }

            StatusbarNotifications.setCurrentConversationInfo(context, contact);
        } catch(Exception ex){
            Log.e(TAG, "Exception: cannot set source address for notifications.");
        }
    }

    /**
     * Sets remote contact, only SIP is required, everything else is loaded from DB
     */
    private boolean setupRemoteContact() {
        Log.d(TAG, "setupRemoteContact()");
        String fromSip = null;

        if (remoteContact == null || SipClist.INVALID_ID.equals(remoteContact.getId())){
            Log.ef(TAG, "MessageFragment initialized with invalid SIP uri, no associated SipClist exists in DB: %s", remoteContact);

        }

        loadMessageContent();
        setNotificationFrom(remoteContact.getSip());
        return true;
    }

    private void updateRemoteContactToolbarInfo(){
        if (getActivity()== null || toolbar == null || remoteContact == null){
            return;
        }

        int statusIcon = MyPresenceManager.getStatusIcon(getActivity(), remoteContact.getPresenceStatusType());

        toolbar.setTitle(remoteContact.getDisplayName());
        toolbar.setLogo(statusIcon);
    }

    /**
     * Scrolls to the defined position. Handles exceptions and invalid data.
     * @param p Scroll position to scroll to.
     */
    private void applyScrollPosition(final ScrollPosition p){
        if (p == null){
            return;
        }

        try {
            final ListView mList = getListView();
            final ScrollPosition pos = new ScrollPosition();
            pos.copyFrom(p);

            scrollListener.applyScrollPosition(mList, pos);
        } catch(Throwable t){
            Log.e(TAG, "Could not apply scroll position", t);
        }
    }

    /**
     * onClick listener for re-send button.
     * Changes type of the message so it is sent again.
     */
    @Override
	public void onResend(SipMessage msg) {
    	if (service == null) {
            return;
        }
        try {
            // Change type and reset send count.
            final Date dt = new Date();

            ContentValues args = new ContentValues();
            args.put(SipMessage.FIELD_TYPE, SipMessage.MESSAGE_TYPE_QUEUED);
            args.put(SipMessage.FIELD_ERROR_CODE, 0);
            args.put(SipMessage.FIELD_ERROR_TEXT, "");
            args.put(SipMessage.FIELD_DATE, dt.getTime());    // Set new time - so it would correspond to the message ordering.
            args.put(SipMessage.FIELD_RANDOM_NUM, srandom.nextInt()); // reset nonce as well - it is identified as a completely new message

            SipMessage.updateMessage(getActivity().getContentResolver(), msg.getId(), args);
            Log.df(TAG, "Message with id [%s] prepared for re-send.", msg.getId());

            // Actual resend, before enqueue-ing, duplicated message is deleted from the queue

            // NewFile notification is stored with this MIME
            if (msg.getMimeType().equals(SipMessage.MIME_FILE)){
                AmpDispatcher.dispatchNewFileNotification(getActivity(), (int) msg.getId());
            } else { // otherwise it is a text message
                AmpDispatcher.dispatchTextMessage(getActivity(), (int) msg.getId());
            }

        } catch (Exception e) {
            Log.e(TAG, "Not able to resend message", e);
        }
	}

    /**
     * Calls current contact (sends ACTION_CALL intent) stored in contactSipp.
     * If there is a contact to call, this conversation will close.
     */
    private void callCurrent(){
        if (!Compatibility.isCallSupported()){
            Log.e(TAG, "Call request on unsupported device.");
            return;
        }

		if(TextUtils.isEmpty(remoteContact.getSip())) {
			Log.w(TAG, "Call button pressed but have no contact to call");
			return;
		}

        CallActivity.invokeActivityForCall(getActivity(), remoteContact.getSip(), SipProfile.USER_ID);
        getFragmentManager().popBackStackImmediate();
    }
    
    /**
     * Sends message currently typed in the bodyInput field.
     */
    private void sendMessage() {
        if (sipProfile == null || sipProfile.getId() == SipProfile.INVALID_ID){
            Log.ef(TAG, "Cannot send message, user is null [%s]", sipProfile);
            return;
        }

        if (PermissionLimits.isMessageLimitExceeded(getActivity())){
            ExpiredLicenseDialogs.showMessageLimitPrompt(getActivity());
            return;
        }

        try {
            final String textToSend = bodyInput.getText().toString();
            final String remoteSip = SipUri.getCanonicalSipContact(remoteContact.getSip(), false);
            final Date dt = new Date();
            String mySip = SipUri.getCanonicalSipContact(sipProfile.getSipUserName() + "@" + sipProfile.getSipDomain(), false);

            if(!TextUtils.isEmpty(textToSend)) {
                // Save new message to the database.
                // Message manager will take care of it.
                SipMessage msg = new SipMessage(
                        mySip,
                        remoteSip,
                        remoteSip,
                        textToSend,
                        SipMessage.MIME_TEXT,
                        dt.getTime(),
                        SipMessage.MESSAGE_TYPE_QUEUED,
                        remoteSip);
                msg.setOutgoing(true);
                msg.setRead(false);
                msg.setRandNum(srandom.nextInt());
                Log.df(TAG, "Inserting SipMessage in DB [%s]", msg);

                Uri lastInsertedUri = getActivity().getContentResolver().insert(SipMessage.MESSAGE_URI, msg.getContentValues());
                // now dispatch the message (= put in MessageQueue)
                AmpDispatcher.dispatchTextMessage(getActivity(), (int) ContentUris.parseId(lastInsertedUri));
                Log.d(TAG, "Message stored to the message queue.");

                // if trial, also log this event
                if (cachedMessagesDayLimit >= 0){
                    TrialEventLog.logOutgoingMessage(getActivity());
                }

                // Now clear message text field.
                bodyInput.setText("");
                bodyInput.setFocusable(true);

                updateMessageLimitCount();
            }
        } catch (Exception e){
            Log.e(TAG, "Not able to send message", e);
        }
    }

    /**
     * OnClick handler for buttons registered to this fragment.
     */
    @Override
    public void onClick(View v) {
        int clickedId = v.getId();
        if (clickedId == R.id.send_button) {
            analyticsReporter.buttonClick(AppButtons.MESSAGE_ACTIVITY_SEND);
            sendMessage();
        }
    }

    @Override
    public void onFileItemInfosChosen(List<FileItemInfo> files, FilePickerFragment.Action action) {
        if (FilePickerFragment.Action.SEND == action) {
            sendFileItemInfos(files);
        } else if (activity != null && FilePickerFragment.Action.DECRYPT == action) {
            FileManager fileManager = new FileManager(service, activity, this);
            fileManager.promptDecryptFiles(FileStorageUri.fromFileItemInfos(files), true);
        } else if (activity != null && FilePickerFragment.Action.DELETE == action) {
            FileManager fileManager = new FileManager(service, activity, this);
            fileManager.promptDeleteFiles(FileStorageUri.fromFileItemInfos(files));
        } else if (activity != null && FilePickerFragment.Action.DECRYPT_AND_OPEN == action) {
            FileManager fileManager = new FileManager(service, activity, this);
            fileManager.openFile(FileStorageUri.fromFileItemInfo(files.get(0)), activity);
        } else if (activity == null) {
            Log.df(TAG, "MessageFragment cannot handle action, because there is no attached activity");
        } else {
            Log.df(TAG, "MessageFragment cannot handle action " + action);
        }
    }

    /**
     * Checks for postponed send file request, if there is any, it is handled.
     * Called when service is connected so we can finish the request.
     */
    private synchronized void handlePostponedSendFile(){
        if (fileUrisToSend == null || fileUrisToSend.isEmpty()){
            fileUrisToSend = null;
            return;
        }

        boolean jobDone = handleSendFile(fileUrisToSend, null);
        if (jobDone){
            Log.v(TAG, "Postponed file transfer processed");
            fileUrisToSend = null;
        }
    }

    /**
     * Sends file to the current contact stored in remoteContact via service binding.
     * @param fileUris
     * @param msg
     * @return
     */
    private boolean handleSendFile(List<String> fileUris, String msg){
        if (fileUris == null || fileUris.isEmpty()){
            return false;
        }

        if (service == null){
            Log.ef(TAG, "XService is null, cannot run task and send file, contact: %s", remoteContact);
            return false;
        }

        try {
            service.sendFiles(remoteContact.getSip(), fileUris, msg);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "error sending file", e);
        }

        return false;
    }

    private void sendFileItemInfos(final List<FileItemInfo> files) {
        handler.post(new SvcRunnable("sendFiles") {
            @Override
            protected void doRun() throws XService.SameThreadException {
                final ArrayList<String> fileUris = new ArrayList<>();
                Log.df(TAG, "sendFileItemInfos() files.size() = %d", files.size());
                for (int i = 0; i < files.size(); ++i) {
                    final File file = new File(files.get(i).absolutePath);//, files.get(i).fileSystemName);
                    Log.d(TAG, "Looking for file " + files.get(i).absolutePath + " name " + files.get(i).fileSystemName);
                    if (!file.exists()) {
                        showErrorDialog(getString(R.string.msg_sending_failed), getString(R.string.some_files_were_deleted));
                        return;
                    }

                    String uri = files.get(i).getUri().toString();
                    fileUris.add(Uri.decode(uri));
                    Log.d(TAG, "Adding uri for file transfer: " + Uri.decode(uri));
                }

                if (service == null) {
                    Log.wf(TAG, "XService is null, cannot run task and send file, contact: %s", remoteContact);
                    fileUrisToSend = fileUris;
                    return;
                }

                handleSendFile(fileUris, null);
            }
        });
    }

	/**
	 * callback after clicking Accept or Reject in file notification message
	 * @param messageId - message storing corresponding file nonce
	 * @param isAccepted 
	 */
	@Override
	public void onFileReceived(long messageId, boolean isAccepted) {
		Context c = getActivity();
      	if (c == null){
      		Log.e(TAG, "getActivity returns null context, cannot run task and receive file");
      		return;
      	}

        if (!isAccepted){
            // Rejection has to be confirmed.
            final RejectFileDialogFragment dialog = RejectFileDialogFragment.newInstance(messageId);
            dialog.show(getFragmentManager(), "");

        } else {
            // Accept is straightforward.
            MessageManager.confirmTransfer(c.getApplicationContext(), messageId, true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().getContentResolver().unregisterContentObserver(statusContentObserver);
    }

    /**
     * Callback on cursor change from the parent.
     */
    @Override
    public void changeCursor(Loader<Cursor> loader, Cursor c) {
        if(adapter != null) {
            adapter.swapCursor(c);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    	SipProfile p = getCurrentUserProfile();
    	String userSip = SipUri.getCanonicalSipContact(p.getUriString(),false);
    	
    	String canonRemoteFrom = SipUri.getCanonicalSipContact(remoteContact.getSip(), false);
    	Log.df(TAG, "onCreateLoader() contactSipp=[%s] canon=[%s] bundle:%s",
                remoteContact,
                canonRemoteFrom,
                args.toString());
        Builder toLoadUriBuilder = SipMessage.THREAD_ID_URI_BASE.buildUpon().appendEncodedPath(canonRemoteFrom).appendEncodedPath(userSip);
        
        Uri u = toLoadUriBuilder.build();
        Log.df(TAG, "onCreateLoader() uri=[%s]", u.toString());
        
        return new CursorLoader(getActivity(), u,
        		SipMessage.FULL_PROJECTION,
        		null, null,
                SipMessage.TABLE_NAME + "." + SipMessage.FIELD_DATE + " ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        final int count = data.getCount();
        Log.vf(TAG, "onLoadFinished(), count: %s vs last one %s, lastPos: %s; justCreated: %s", count, lastCursorCount, lastScrollPosition, viewRefreshed);

        scrollListener.onLoadFinished(count);
        if (viewRefreshed || lastCursorCount == -1) {
            viewRefreshed = false;

            // View was just created, scroll to the given position it was last time.
            lastScrollPosition.setCount(count);
            // Scroll back to the last position.
            applyScrollPosition(lastScrollPosition);
            Log.vf(TAG, "apply scroll position, lastCursorCount: %s, position: %s", lastCursorCount, lastScrollPosition);

        } else if (lastCursorCount != count) {
            // Something has changed, if we are too close to the bottom, scroll to bottom automatically.
            if (shouldScrollDown()){
                lastScrollPosition.reset(count);
                applyScrollPosition(lastScrollPosition);
                Log.vf(TAG, "Content changed, scroll position close to the bottom, scrolled down");
            }
        }

        lastCursorCount = count;
        lastScrollPosition.setCount(count);
    }

    private boolean shouldScrollDown(){
        try {
            final long threshold = LayoutUtils.dp2Pix(getResources(), 150);
            return scrollListener.shouldScrollDown(threshold);

        } catch(Throwable t){
            Log.e(TAG, "Exception, could not set scroll position properly");
        }

        return false;
    }

    @Override
    public void onScroll(AbsListView view, float deltaY, long total) {

    }

    @Override
    public void onScrollChanged(ScrollPosition candPosition) {
        lastScrollPosition.copyFrom(candPosition);
        Log.vf(TAG, "New scroll, previous: %s, candidate: %s", lastScrollPosition, candPosition);
    }

    @Override
    public boolean filesDeletedByMessageId(Long messageId) {
        return 0 < ctxt.getContentResolver().delete(ReceivedFile.URI,
                ReceivedFile.FIELD_MSG_ID + "=?", new String[] {messageId.toString()});
    }

    /**
     * Handler is used in onLoadFinished to initialize progress fragment
     */
    static class LoadingHandler extends Handler{
    	private final WeakReference<MessageFragment> mfrag;
    	LoadingHandler(MessageFragment mf) {
            mfrag = new WeakReference<MessageFragment>(mf);
        }
    	
    	@Override
        public void handleMessage(Message msg) {
    		MessageFragment mf = mfrag.get();
        	if (mf==null) {
        		Log.i(TAG, "Message fragment is null now... Cannot process message");
        		return;
        	}
    		if(msg.what == MSG_SHOW_PROGRESS_FRAGMENT) {
            	// Nothing to do now.
            } else if (msg.what == MSG_FILE_NOTIFY_RECEIVED){
            	mf.startFileDownload(msg.getData());
            }
        }
    }
        
    /**
     * start file downloading task 
     * @param b - must contains BUNDLE_MSG_CONTENT (SipMessage ContentValues) and BUNDLE_MSG_BODY_HASH data
     */
    public void startFileDownload(Bundle b) {    	
    	Context c = getActivity();
      	if (c == null){
      		Log.e(TAG, "getActivity returns null context, cannot run task and send file");
      	}
	}
    
	// Options
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        // Attach file
        menu.add(Menu.NONE, MENU_ATTACH_FILE, Menu.NONE, R.string.select_file)
                .setIcon(R.drawable.ic_attach_file_black_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Call
        if (Compatibility.isCallSupported()) {
            menu.add(Menu.NONE, MENU_CALL, Menu.NONE, R.string.conversation_call_button)
//                    .setIcon(R.drawable.call_contact)
                    .setIcon(R.drawable.ic_call_black_24px)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case MENU_ATTACH_FILE:
                analyticsReporter.buttonClick(AppButtons.MESSAGE_ACTIVITY_ADD_ATTACHMENT);

                // check permissions
                int filesLimit = PermissionLimits.getFilesLimit(getActivity().getContentResolver());
                if (filesLimit == 0){
                    ExpiredLicenseDialogs.showFilesLimitPrompt(getActivity());
                    return false;
                }

                MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
                builder.adapter(new AttachmentAdapter(getActivity(), R.layout.attachment_item), (dialog, itemView, which, text) -> {
                            Log.df(TAG, "onSelection() itemView.getId() = [%d]", itemView.getId());
                            dialog.dismiss();
                            switch (which) {
                                case 0:
                                    analyticsReporter.buttonClick(AppButtons.ATTACHMENT_MENU_TAKE_PHOTO);
                                    takePhoto();
                                    break;
                                case 1:
                                    analyticsReporter.buttonClick(AppButtons.ATTACHMENT_MENU_SEND_PICTURES);
                                    attachFile(MediaType.IMAGES);
                                    break;
                                case 2:
                                    analyticsReporter.buttonClick(AppButtons.ATTACHMENT_MENU_SEND_FILES);
                                    attachFile(MediaType.ALL_FILES);
                                    break;
                                default:
                                    break;
                            }
                        });
                builder.build().show();

                LayoutUtils.hideSwKeyboard(getActivity());
                break;
            case MENU_CALL:
                analyticsReporter.buttonClick(AppButtons.MESSAGE_ACTIVITY_CALL);
                callCurrent();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void attachFile(MediaType mediaType) {
        try {
            FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();
            FilePickerFragment fragment = FilePickerFragment.newInstance(
                    getActivity(), this, FilePickerFragment.FilePickerType.SEND_FILES, null, mediaType);
            fragment.setTargetFragment(this, FILE_PICKER_CODE);
            ft.replace(android.R.id.content, fragment, FILE_PICKER_FRAGMENT_TAG);
            ft.addToBackStack(FILE_PICKER_FRAGMENT_TAG);
            ft.commit();
        } catch(Exception ex){
            Log.e(TAG, "Cannot add filepicker fragment", ex);
        }
    }

    private void takePhoto() {
        Intent it = new Intent(getActivity(), CameraActivity.class);
        it.putExtra(CameraActivity.EXTRA_SINGLE_PHOTO, true);
        startActivityForResult(it, CameraActivity.REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(requestCode == FILE_PICKER_CODE && resultCode == Activity.RESULT_OK) {
            if (intent == null){
                Log.e(TAG, "Result from file picker is empty");
                return;
            }

            FilePickerFragment.Action action = FilePickerFragment.Action.SEND;

            if (intent.hasExtra(Intents.EXTRA_FILES_PICKED_ACTION)) {
                action = (FilePickerFragment.Action) intent.getSerializableExtra(Intents.EXTRA_FILES_PICKED_ACTION);
            }

            ArrayList<FileItemInfo> files = intent.getParcelableArrayListExtra(Intents.EXTRA_FILES_PICKED_PATHS);

            onFileItemInfosChosen(files, action);
        } else if (requestCode == CameraActivity.REQUEST_CODE) {
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
                sendFileItemInfos(FileStorageUri.toFileItemInfoList(uri));
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // Context menu depends on the item selected.

        if (v instanceof FileView) {
            Log.vf(TAG, "Context menu for FileView=%s", v);

            FileView fv = (FileView) v;
            buildContextMenuFile(menu, fv.getMessageId(), fv.getFileStorageUri());
        } else {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            Log.vf(TAG, "Context menu for pos=%s", info.position);

            Cursor c = (Cursor) adapter.getItem(info.position);
            if (c == null){
                return;
            }

            buildContextMenuMessage(menu, new SipMessage(c));
        }

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    /**
     * Adds options to menu that depend on the message
     * @param menu
     * @param msg
     */
    private void buildContextMenuMessage(ContextMenu menu, SipMessage msg) {
        // if user clicked on file, add some specific menu items

        Intent intent = new Intent(ACTION_TEXT_MESSAGE_CONTEXT_MENU);
        MenuItem item;

        // Allow forwarding
        if (msg.canBeForwarded()){
            item = menu.add(0, MENU_FORWARD, 0, R.string.msg_forward);
            item.setIntent(intent);
        }

        if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_UPLOADING
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES){
            // Downloading & uploading
            // Add cancel option
            item = menu.add(0, MENU_CANCEL, 1, R.string.cancel);
            item.setIntent(intent);
        } else {
            // Option to delete a message only for finished messages, no
            // download/upload in progress.
            item = menu.add(0, MENU_DELETE, 1, R.string.msg_delete);
            item.setIntent(intent);
        }

        // Do not show Copy message text options if the message contains files
        if (!msg.isFileType()) {
            item = menu.add(0, MENU_COPY, 2, R.string.copy_message_text);
            item.setIntent(intent);
        }
    }

    /**
     * Adds options to the menu that are specific for the clicked file
     */
    private void buildContextMenuFile(ContextMenu menu, Long sipMessageId, FileStorageUri uri) {
        if (!StorageUtils.existsUri(uri.toString(), ctxt.getContentResolver())) {
            return;
        }

        MenuItem item;
        Intent intent = new Intent(ACTION_FILE_MESSAGE_CONTEXT_MENU);
        intent.putExtra("messageId", sipMessageId);
        intent.putExtra("uri", uri);

        // open on short click instead?
        // PHON-633 Remove Open file as it can be done by tapping on the thumbnail, thus here it is redundant.
        //item = menu.add(1, MENU_OPEN, 0, R.string.open_file);
        //item.setIntent(intent);

        String deleteFilesTitle = getActivity().getResources().getQuantityString(R.plurals.delete_file, 1);
        item = menu.add(1, MENU_DELETE_FILE, 5, deleteFilesTitle);
        item.setIntent(intent);

//        item = menu.add(1, MENU_OPEN_DIRECTORY, 7, R.string.open_directory);
//        item.setIntent(intent);

        if (uri.isSecureStorage()) {
            try {
                FileStorage fileStorage = FileStorage.getFileStorageByUri(uri.getUri(), ctxt.getContentResolver());
                if (fileStorage != null) {
                    item = menu.add(1, MENU_DECRYPT, 4, R.string.file_menu_decrypt);
                    item.setIntent(intent);
                }
            } catch (FileStorage.FileStorageException e) {
                // TODO
            }
        }

    }
    
    @Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
	}
    
	@Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        // the intent is used to check if this action is for file message, text message
        // or from other source (e.g. file picker)
        // each menu item must have intent with the corresponding action!
        Intent intent = item.getIntent();

        // file specific options
        if (intent != null && ACTION_FILE_MESSAGE_CONTEXT_MENU.equals(intent.getAction())) {
            FileManager fileManager = new FileManager(service, getActivity(), this);

            Long sipMessageId = intent.getLongExtra("messageId", -1);
            FileStorageUri uri = intent.getParcelableExtra("uri");

            Log.df(TAG, "File related action, uri [%s]", uri);

            switch (item.getItemId()) {
                case MENU_DELETE_FILE: {
                    fileManager.promptDeleteFile(uri);
                    break;
                }

                case MENU_OPEN: {
                    openFile(uri, sipMessageId);
                    break;
                }

                case MENU_DECRYPT: {
                    fileManager.promptDecryptFile(uri, false);
                    break;
                }

                case MENU_DELETE_CLONE: {
                    fileManager.deleteClone(uri);
                    break;
                }

                // currently not used
                case MENU_OPEN_DIRECTORY: {
                    FileTransferUtils.openFolder(getActivity(), uri.getParentPath());
                    break;
                }
            }

            item.setIntent(null);
            return true;
        }

        if (intent == null || !ACTION_TEXT_MESSAGE_CONTEXT_MENU.equals(intent.getAction())) {
            Log.d(TAG, "This onContextItemSelected is not for message");
            return super.onContextItemSelected(item);
        }

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info==null){
            Log.w(TAG, "Null info, do nothing");
            return super.onContextItemSelected(item);
        }

        // general message options
        Cursor c = (Cursor) adapter.getItem(info.position);
        if (c != null) {
            SipMessage msg = new SipMessage(c);
            switch (item.getItemId()) {
                case MENU_FORWARD: {

                    // check permissions
                    if (msg.isFileType()){
                        int filesLimit = PermissionLimits.getFilesLimit(getActivity().getContentResolver());
                        if (filesLimit == 0){
                            ExpiredLicenseDialogs.showFilesLimitPrompt(getActivity());
                            return false;
                        }
                    } else {
                        if (PermissionLimits.isMessageLimitExceeded(getActivity())){
                            ExpiredLicenseDialogs.showMessageLimitPrompt(getActivity());
                            return false;
                        }
                    }

                    analyticsReporter.buttonClick(AppButtons.MESSAGE_CONTEXT_MENU_FORWARD);
                    forwardMessage(msg);
                    break;
                }
                case MENU_COPY: {
                    analyticsReporter.buttonClick(AppButtons.MESSAGE_CONTEXT_MENU_COPY);
                    clipboardManager.setText(msg.getDisplayName(), msg.getPlainBody());
                    break;
                }

                // currently not used in the context menu
                case MENU_COPY_SECURE: {
                    secureClipboard.setText(msg.getDisplayName(), msg.getPlainBody());
                    clipboardManager.setText(msg.getDisplayName(), SecureClipboard.SECURE_PLACEHOLDER);
                    break;
                }

                case MENU_DELETE: {
                    analyticsReporter.buttonClick(AppButtons.MESSAGE_CONTEXT_MENU_DELETE);
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());

                    builder.title(R.string.msg_delete)
                            .positiveText(R.string.notif_delete)
                            .negativeText(R.string.cancel)
                            .customView(R.layout.delete_message_dialog, false)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    boolean deleteFiles = false;
                                    if (dialog.getCustomView() != null) {
                                        CheckBox box = (CheckBox) dialog.getCustomView().findViewById(R.id.delete_files_checkbox);
                                        if (box != null) {
                                            deleteFiles = box.isChecked();
                                        }
                                    }
                                    dialog.dismiss();
                                    deleteMessage(msg, deleteFiles);
                                }

                                @Override
                                public void onNegative(MaterialDialog dialog) {
                                    dialog.dismiss();
                                }
                            });
                    MaterialDialog dialog = builder.build();
                    if (dialog.getCustomView() != null) {
                        CheckBox box = (CheckBox) dialog.getCustomView().findViewById(R.id.delete_files_checkbox);
                        if (msg.isFileType() && atLeastOneFileExistsFromMessage(msg.getId())) {
                            box.setVisibility(View.VISIBLE);
                        } else {
                            box.setVisibility(View.GONE);
                        }
                    }
                    dialog.show();
                    break;
                }

                case MENU_CANCEL: {
                    analyticsReporter.buttonClick(AppButtons.MESSAGE_CONTEXT_MENU_CANCEL_FILE_TRANSFER);
                	try {
                		Log.df(TAG, "Cancell clicked on msg: %s", msg.getId());
                        final int msgType = msg.getType();

	                	if (msgType == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING || msgType == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META){
	                		service.cancelDownload(msg.getId());
	                	} else if (msgType == SipMessage.MESSAGE_TYPE_FILE_UPLOADING || msgType == SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES){
	                		service.cancelUpload(msg.getId());
	                	}
                	} catch (Exception e){
                		Log.e(TAG, "Cannot cancel, exception thrown.", e);
                	}         	
                    break;
                }

                // currently not used, files can only be deleted by checkbox when deleting message
                case MENU_DELETE_FILES: {
                    analyticsReporter.buttonClick(AppButtons.MESSAGE_CONTEXT_MENU_DELETE_FILES);
                	if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META){
                		
                		// Delete file physically from storage. 
                		final Long msgId = msg.getId();
                		if (msgId == null || msgId < 0){
                			break;
                		}

                        FileManager fileManager = new FileManager(service, getActivity(), this);
                        fileManager.promptDeleteFilesByMessageId(msgId);
                	}
                    break;
                }
                default:
                    break;
            }

        }
        // consume
        return true;
    }

    private boolean atLeastOneFileExistsFromMessage(Long messageId) {
        final List<ReceivedFile> rf = ReceivedFile.getFilesByMsgId(getActivity().getContentResolver(), messageId);
        for (ReceivedFile file : rf) {
            if (StorageUtils.existsUri(FileStorageUri.fromReceivedFile(file).toString(), getActivity().getContentResolver())) {
                return true;
            }
        }
        return false;
    }

    private void deleteMessage(SipMessage msg, boolean deleteFiles) {
        // delete thumbnails, if we already have them
        // they should be present if:
        //   file was rejected
        //   meta was downloaded
        //   files are being downloaded, therefore thumbnails must have been downloaded
        //   files are waiting for user to decide
        // if all files are downloaded, thumbnails are already deleted
        if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_REJECTED
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING
                || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_READY )
        {
            int deleted = Thumbnail.deleteByMessageId(getActivity().getContentResolver(), msg.getId());
            Log.vf(TAG, "Deleting message [%s], deleted %d thumbnails", msg.getId(), deleted);
        }

        // if user wishes to delete physical files
        if (deleteFiles){
            // this will also delete ReceivedFile(s)
            FileManager fileManager = new FileManager(service, getActivity(), this);
            fileManager.deleteFilesByMessageId(msg.getId());
        }

        // Is downloaded? If yes, delete received file also - leaves physical file.
        if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED || msg.getType() == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META){

            // Delete file physically from storage.
            final String nonce2 = msg.getFileNonce();
            if (!TextUtils.isEmpty(nonce2)){
                getActivity().getContentResolver().delete(ReceivedFile.URI, ReceivedFile.FIELD_FILE_NONCE + "=?", new String[] {nonce2});
            }
        }

        // delete message from the queue (msg won't be actually send if hasn't been yet)
        int count = QueuedMessage.deleteOutgoingSipMessage(getActivity().getContentResolver(), msg.getTo(), msg.getId());
        Log.vf(TAG, "Number of removed items in the messagequeue [%d]", count);

        // Delete particular message
        SipMessage.deleteById(getActivity().getContentResolver(), msg.getId());
    }

    private void forwardMessage(SipMessage msg) {
        final Context ctxt = getActivity().getApplicationContext();
        final Intent it = new Intent(getActivity(), BroadcastMessageActivity.class);
        Bundle b = new Bundle();

        b.putParcelable(BroadcastMessageActivity.EXTRA_FORWARDED_SIP_MESSAGE, msg);

        if (msg.isFileType()){
            List<ReceivedFile> files = ReceivedFile.getFilesByMsgId(ctxt.getContentResolver(), msg.getId());
            ArrayList<String> fileUris = new ArrayList<>();
            if (files == null || files.size() == 0){
                Log.ef(TAG, "forwardMessage; Cannot get arguments for file forwarding - no files connected with nonce 2 [%s]", msg.getFileNonce());
                Toast.makeText(getActivity(), R.string.upd_failed_internal_error, Toast.LENGTH_SHORT).show();
                return;
            }

            for (ReceivedFile file : files){
                if (file.getStorageUri() != null) {
                    fileUris.add(file.getStorageUri());
                } else {
                    File f = new File(file.getPath());
                    Uri uri = new Uri.Builder().scheme(FileStorageUri.STORAGE_SCHEME_NORMAL)
                            .appendPath(f.getParentFile().getAbsolutePath())
                            .appendQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME, f.getName())
                            .appendQueryParameter(FileStorageUri.STORAGE_FILENAME, f.getName())
                            .build();
                    fileUris.add(uri.toString());
                }
            }

            b.putStringArrayList(BroadcastMessageActivity.EXTRA_FORWARDED_FILE_URIS, fileUris);
        }

        it.putExtras(b);
        MiscUtils.configureIntent(ctxt, it);
        getActivity().startActivityForResult(it, 0);
    }

    /**
     * BROADCAST RECEIVER FOR SENT MESSAGES 
     * Listens for failed messages and tries to resend.
     */
    public class MessagesReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			
			Log.inf(TAG, "received intent in MessageReceiver, action:%s", arg1.getAction());
			
			final String action = arg1.getAction();
			
			if (Intents.ACTION_SIP_MESSAGE_RECEIVED.equals(action)){
				onMessageReceived(arg1);
			} else if (Intents.ACTION_FILEUPLOAD_PROGRESS.equals(action)){
				onFileUploadProgress(arg1);
			}  else if (Intents.ACTION_FILEDOWNLOAD_PROGRESS.equals(action)){
				onFileDownloadProgress(arg1);
			}
		}
		
		/**
		 * Receives progress update intents about file download.
		 * @param intent
		 */
		private void onFileDownloadProgress(Intent intent){			
			FileTransferProgress progress = (FileTransferProgress) intent.getParcelableExtra(Intents.FILEDOWNLOAD_INTENT_PROGRESS);
			Log.inf(TAG, "download progress:%s", progress.toString());
			
			updateProgress(progress);
			
			// If finished -> change message type.
			if (progress.isDone()){
                final FileTransferError ftError = progress.getError();
				if (ftError == FileTransferError.NONE){
					// Nothing to do here.
					
				} else if (ftError == FileTransferError.DOWN_DOWNLOAD_ERROR) {
					// Some download error, user may try to download it again...
					showErrorDialog(getString(R.string.dwn_failed), getString(R.string.dwn_failed_download));

                } else if (ftError == FileTransferError.CANCELLED) {
                    // Nothing to do, cancelled, user knows he cancelled it.
                    return;

                } else if (ftError == FileTransferError.TIMEOUT) {
                    // Nothing to do, timed out, do not show notifications.
                    return;

                } else if (FileTransferProgress.errorTryAgain(progress.getError())){
                    // If transfer is recoverable by try-again, let user try it.
                    showErrorDialog(getString(R.string.dwn_failed), getString(R.string.dwn_failed_recoverable));

				} else {
					// Non-recoverable, mark as defective.
					showErrorDialog(getString(R.string.dwn_failed), getString(R.string.dwn_failed_generic));
				}
			}
		}

		/**
		 * Receives progress update intents about file upload.
		 * @param intent
		 */
		private void onFileUploadProgress(Intent intent){			
			FileTransferProgress progress = (FileTransferProgress) intent.getParcelableExtra(Intents.FILEUPLOAD_INTENT_PROGRESS);
			Log.inf(TAG, "upload progress:%s", progress.toString());
			
			updateProgress(progress);
			
			// If finished -> change message type.
			if (progress.isDone()){
                final FileTransferError ftError = progress.getError();
                if (ftError == FileTransferError.NONE){
					// Nothing to do here.
					
				} else if (ftError == FileTransferError.UPD_QUOTA_EXCEEDED) {
					// Quota exceeded.
					showErrorDialog(getString(R.string.upd_failed), getString(R.string.upd_failed_full_mailbox));
					
				} else if (ftError == FileTransferError.UPD_FILE_TOO_BIG) {
					// Uploaded file is too big.
					showErrorDialog(getString(R.string.upd_failed), getString(R.string.upd_failed_too_big));
					
				} else if (ftError == FileTransferError.UPD_NO_AVAILABLE_DHKEYS) {
                    // No available DH keys for remote user.
                    showErrorDialog(getString(R.string.upd_failed), getString(R.string.upd_failed_no_keys));

                } else if (ftError == FileTransferError.CANCELLED) {
                    // Nothing to do, cancelled, user knows he cancelled it.
                    return;

                } else if (ftError == FileTransferError.TIMEOUT) {
                    // Nothing to do, timed out, do not show notifications.
                    return;

				} else {
					// Generic error
					showErrorDialog(getString(R.string.upd_failed), getString(R.string.upd_failed_generic)); 
				}
			}
		}
		
		/**
		 * Generic update progress method for both upload & download.
		 * @param progress
		 */
		private void updateProgress(FileTransferProgress progress){
			// progress bar update - fancy progress effect is not saved in DB, we have to update it manually
            try {
                View listItemView = getListItemViewByMessageId(progress.getMessageId());
                if (listItemView==null) {
                    return;
                }
                
                // we can either call getView and refresh the whole view:
                // or update just single item in View
                final ViewHolder tagView = (ViewHolder) listItemView.getTag();
                tagView.fileTransferProgress.updateProgress(progress);

            } catch (Exception e){
                Log.ef(TAG, e, "Unexpected exception while updateProgress()");
            }
		}

		/**
		 * Handles intent sent by UAReceiver.on_pager_status() - whether message was successfully sent or not.
		 * @param intent
		 */
		private void onMessageReceived(Intent intent){
			// Nothing to do...
		}
    }

    private View getListItemViewByMessageId(long messageId){
        final MessageFragment mf = MessageFragment.this;
        if (mf.isDetached() || mf.isHidden() || mf.isRemoving()){
            return null;
        }

        ListView list = mf.getListView();
        final int start = list.getFirstVisiblePosition();
        final int last  = list.getLastVisiblePosition();
        for(int i=start;i<=last;i++){
            Cursor item = (Cursor)list.getItemAtPosition(i);
            long loadedId = item.getLong(item.getColumnIndex(SipMessage.FIELD_ID_FROM_THREADS_ALIAS));

            if(messageId == loadedId){
                // we found that corresponding cursor is shown in ListView
                // now we want to increase progress in ProgressBar of its View
                View view = list.getChildAt(i-start);

                return view;
            }
        }
        return null;
    }

    public MessagesReceiver getNewMessagesReceiver(){
    	return new MessagesReceiver();
    }

    @Override
    public void onResendTimeoutRefresh(long messageId, long resendTime){
        try {
            View listItemView = getListItemViewByMessageId(messageId);
            if (listItemView==null) {
                return;
            }

            // we can either call getView and refresh the whole view:
            // or update just single item in View
            final ViewHolder tagView = (ViewHolder) listItemView.getTag();
            tagView.statusView.setText(MessageAdapter.getResendTimeoutText(getActivity(), resendTime));

        } catch (Exception e){
            Log.ef(TAG, e, "Unexpected exception while updateProgress()");
        }
    }

    public void openFile(FileStorageUri uri, Long messageId) {
        if (uri == null || MiscUtils.isEmpty(uri.getAbsolutePath())) {
            return;
        }

        if (messageId >= 0 && FileUtils.isImage(uri.getFilename())
                && MiscUtils.fileExistsAndIsAfile(uri.getAbsolutePath())) {
            List<FileStorageUri> messageFiles =
                    FileStorageUri.fromReceivedFiles(
                            ReceivedFile.getFilesByMsgId(ctxt.getContentResolver(), messageId));

            int startingPosition;
            for (startingPosition = 0; startingPosition < messageFiles.size(); startingPosition++) {
                if (messageFiles.get(startingPosition).getAbsolutePath().equals(uri.getAbsolutePath())) {
                    break;
                }
            }
            if (startingPosition == messageFiles.size()) startingPosition = 0;
            FileManager.openSlideshow(messageFiles, startingPosition, getActivity());
        } else {
            FileManager fileManager = new FileManager(service, getActivity(), this);
            fileManager.openFile(uri, getActivity());
        }
    }

    public void openFile(ReceivedFile receivedFile) {
        // Check for null / path emptiness. If there is no path it means file is not downloaded yet or error happened.
        if (receivedFile == null || MiscUtils.isEmpty(receivedFile.getPath())){
            return;
        }

        openFile(FileStorageUri.fromReceivedFile(receivedFile), receivedFile.getMsgId());
    }

    @Override
    public void decryptFile(FileStorageUri uri) {
        if (!uri.isSecureStorage()) {
            return;
        }
        try {
            if (null != FileStorage.getFileStorageByUri(uri.getUri(), ctxt.getContentResolver())) {
                service.decryptFile(uri.getUri().toString());
            } else {
                Log.w(TAG, "File does not exist in DB " + uri.getUri().toString());
            }
        } catch (FileStorage.FileStorageException  e) {
            Log.w(TAG, "Exception at promptDecryptFile", e);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception at promptDecryptFile", e);
        }
    }

    @Override
    public void deleteFileClone(FileStorageUri uri) {
        FileManager fileManager = new FileManager(null, getActivity(), this);
        fileManager.deleteClone(uri);
    }

    @Override
    public void onStop() {
        super.onStop();
        // when Fragment is no longer visible, stop periodical UI refreshes
        adapter.cancelUiRefresh();
    }

    /**
	 * Shows alert dialog with specified error string. 
	 * 
	 * @param title
	 * @param body
	 */
	public void showErrorDialog(String title, String body){
		final AlertDialog.Builder dialog = new AlertDialog.Builder(this.getActivity());
		dialog.setTitle(title);
		dialog.setMessage(body);
		
		dialog.setIcon(android.R.drawable.ic_dialog_alert);
		dialog.setCancelable(false);
		dialog.setPositiveButton(R.string.ok, (dialog1, which) -> {
        });
		
		AlertDialog alertDialog = dialog.create();
		alertDialog.setCanceledOnTouchOutside(false);
		alertDialog.show();
	}

    @Override
    public boolean actionFinished() {
        Fragment fragment = getFragmentManager().findFragmentByTag(FILE_PICKER_FRAGMENT_TAG);
        if (fragment != null && fragment instanceof FilePickerFragment) {
            ((FilePickerFragment) fragment).actionFinished();
        } else {
            getLoaderManager().restartLoader(0, getArguments(), MessageFragment.this);
        }
        return true;
    }

    /**
     * Listener for remoteContact load finished.
     * If contact is sentinel - hide the send button.
     */
    public void onRemoteContactLoaded(){
        try {
            getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMessagingEnabledForContact();
                    }
                }
            );
        } catch(Exception e){
            Log.ef(TAG, e, "Exception when processing onRemoteContactLoaded()");
        }
    }

    /**
     * Sets UI enabled status according to the remote contact.
     * Has to be called on UI thread.
     */
    public void setMessagingEnabledForContact(){
        final boolean isValid = remoteContact != null && remoteContact.isValidDb();
        try {
            if (sendButton != null) {
                sendButton.setEnabled(isValid);
            }
            if (bodyInput != null) {
                bodyInput.setEnabled(isValid);
            }
        }catch(Exception e){
            Log.ef(TAG, e, "Exception in setting UI for contact, valid: %s", isValid);
        }
    }

    /**
     * Loads SipClist from database.
     * If Sip was not loaded, returns sentinel.
     *
     * @param ctxt
     * @param sip
     * @return SipClist, not null
     */
    private static SipClist getProfileFromDbSip(Context ctxt, String sip){
        try {
            final SipClist sipClist = SipClist.getProfileFromDbSip(ctxt, sip);
            if (sipClist != null && sipClist.isValidDb()){
                return sipClist;
            }

        } catch(Exception e){
            Log.ef(TAG, e, "Exception when loading sip: %s", sip);
        }

        // Construct sentinel object so it is displayed even if the original record was deleted.
        // This is mainly for support contact - when contact got deleted.
        Log.inf(TAG, "SIP %s was not loaded from DB, building sentinel", sip);
        final SipClist sipClist = new SipClist();
        sipClist.setDisplayName(sip);
        sipClist.setSip(sip);
        sipClist.setPresenceOnline(false);
        sipClist.setPresenceStatusType(PushNotifications.PresencePush.Status.OFFLINE_VALUE);
        return sipClist;
    }

    private class StatusContentObserver extends ContentObserver {

        public StatusContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            try {
                if (remoteContact == null){
                    // Service was probably not loaded yet, ignore this update.
                    // OnServiceConnected event update is called.
                    return;
                }

                SipClist sipClist = getProfileFromDbSip(getActivity(), remoteContact.getSip());
                if (sipClist == null || SipClist.INVALID_ID.equals(sipClist.getId())) {
                    Log.ef(TAG, "MessageFragment initialized with invalid SIP uri [%s], no associated SipClist exists in DB", sipClist);
                    return;
                }
                remoteContact = sipClist;
                onRemoteContactLoaded();

                // reload remoteContact and associated UI fields (status mainly)
                updateRemoteContactToolbarInfo();
            } catch(Exception ex){
                Log.e(TAG, "Exception, cannot update contact in message fragment", ex);
            }
        }
    }
}