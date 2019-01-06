package net.phonex.ui.intro;

import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.annotations.PinUnprotected;
import net.phonex.autologin.LoginCredentials;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.autologinQuick.QuickLoginPersister;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.gcm.RegistrationIntentService;
import net.phonex.login.LoginEventsListener;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.core.SipUri;
import net.phonex.introslider.SliderActivity;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.CredsData;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.service.SafeNetService;
import net.phonex.service.XService;
import net.phonex.soap.ChangePasswordTask;
import net.phonex.soap.PasswordChangeParams;
import net.phonex.soap.SSLSOAP;
import net.phonex.soap.entities.PasswordChangeV2Response;
import net.phonex.ui.PhonexActivity;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.ChangePasswordDialogFragment;
import net.phonex.ui.dialogs.LoginProgressDialogFragment;
import net.phonex.ui.dialogs.TrialCreatedDialogFragment;
import net.phonex.ui.help.HelpListActivity;
import net.phonex.ui.interfaces.OnAccountCreatedListener;
import net.phonex.ui.interfaces.OnPasswordChangeCompleted;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.DefaultServiceConnector;
import net.phonex.util.DefaultServiceConnector.ServiceConnectorListener;
import net.phonex.util.Log;
import net.phonex.util.LoginUtils;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;
import net.phonex.util.android.StatusbarNotifications;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@PinUnprotected
public class IntroActivity extends LockActionBarActivity implements ServiceConnectorListener, OnAccountCreatedListener, ChangePasswordDialogFragment.OnPasswordSelected, OnPasswordChangeCompleted, LoginEventsListener {

    private static final String THIS_FILE = "IntroActivity";

    public static final String EXTRA_CANCEL_NOTIFS = "cancel_notifs";
    public static final String EXTRA_JUST_KILLED = "just_killed";
    public static final String EXTRA_DECRYPT_SALT = "decrypt_salt";
    public static final String EXTRA_DECRYPT_IV = "decrypt_iv";

    public static final int HELP_MENU = Menu.FIRST + 1;
    public static final int HOMEPAGE_MENU = Menu.FIRST + 2;
    public static final int TRIAL_MENU = Menu.FIRST + 3;
    public static final int INTRO_MENU = Menu.FIRST + 4;

    private static final int SLIDER_ACTIVITY_REQUEST_CODE = 1001;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    static final String INTRO_SETUP_FRAGMENT_TAG = "intro_setup_fragment_tag";
    static final String FIRST_LOGIN_FRAGMENT_TAG = "first_login_fragment_tag";
    static final String CREATE_TRIAL_FRAGMENT_TAG = "create_trial_fragment_tag";
    static final String FORGOT_PASSWORD_FRAGMENT_TAG = "forgot_password_fragment_tag";
    static final String TASK_FRAGMENT_TAG = "task";

    protected Bundle lastSavedInstanceState;
    protected Dialog progressDialog;
    private int progressDialogType=1;

    private DefaultServiceConnector connector;
    private boolean cancelNotifications = false;
    private String decryptionSalt = null;
    private String decryptionIv = null;

    private ResurrectionReceiver resurrectionReceiver;
    private BroadcastReceiver registrationChangedReceiver;
    
    // For automatic credentials passing
    private CredsData creds;
    
    // User currently logged in.
    private SipProfileState state;
    
    private PreferencesManager prefWrapper;

    // Login fragment  + delayed login
    private LoginProgressDialogFragment loginProgressFragment;
    private final Object delayedLoginMutex = new Object();

    private Intent delayedLoginIntent = null;

    private AtomicBoolean activityResumed = new AtomicBoolean(false);
    private boolean firstApplicationLaunch;

    private class LoginEventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null){
                Log.ef(THIS_FILE, "Intent is null");
                return;
            }

            // if activity is hidden (e.g. screen off), cannot commit fragment transaction,
            // unless state loss allowed -> activityResumed.get()

            switch (intent.getAction()) {
                case Intents.ACTION_LOGIN_CANCELLED:
                    if (intent.hasExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE)) {
                        if (intent.hasExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_TITLE)) {
                            onLoginCancelled(intent.getStringExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_TITLE),
                                    intent.getStringExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE));
                        } else {
                            onLoginCancelled(intent.getStringExtra(Intents.EXTRA_LOGIN_CANCELLED_ERROR_MESSAGE));
                        }
                    } else {
                        Log.e(THIS_FILE, "ACTION_LOGIN_CANCELLED intent has missing extras");
                    }
                    break;
                case Intents.ACTION_LOGIN_FINISHED:
                    if (activityResumed.get()) {
                        onLoginFinished();
                    } else {
                        finish();
                    }
                    break;
                case Intents.ACTION_LOGIN_PASSWORD_CHANGE:
                    if (intent.hasExtra(Intents.EXTRA_LOGIN_PASSWORD_CHANGE_PARAMS)) {
                        passwordChangeRequired((PasswordChangeParams) intent.getSerializableExtra(Intents.EXTRA_LOGIN_PASSWORD_CHANGE_PARAMS));
                    } else {
                        Log.e(THIS_FILE, "ACTION_LOGIN_PASSWORD_CHANGE intent has missing extras");
                    }
                    break;
                case Intents.ACTION_LOGIN_PROGRESS:
                    GenericTaskProgress progress = intent.getParcelableExtra(Intents.EXTRA_GENERIC_PROGRESS);
                    if (progress == null){
                        Log.ef(THIS_FILE, "Progress is null");
                        return;
                    }
                    Log.df(THIS_FILE, "ACTION_LOGIN_PROGRESS done: [%b] message: [%s] error: [%s]", progress.isDone(), progress.getMessage(), progress.getError());

                    if (progress.getError() == null || progress.getError().equals(GenericError.NONE)) {
                        if (progress.isDone()) {
                            // nothing, handled by ACTION_LOGIN_FINISHED intent
                        } else {
                            if (loginProgressFragment != null || initProgressFragment()) {
                                loginProgressFragment.updateProgress(progress.getMessage());
                            }
                        }
                    } else {
                        if (loginProgressFragment != null) {
                            // here we could get the exception from the task
                            loginProgressFragment.dismissAllowingStateLoss();
                            loginProgressFragment.taskFinished(null);
                        }
                        AlertDialogFragment
                                .newInstance(getString(R.string.p_problem), progress.getMessage())
                                .showAllowingStateLoss(getFragmentManager(), "alert");
                    }

                    break;
            }
        }
    }

    private LoginEventReceiver loginEventReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        prefWrapper = new PreferencesManager(this);
        // Restart APP_CLOSED_BY_USER property after re-runing the application - without this, SIP stack doesn't start (it's precondition in {@link XService.isConnectivityValid})
        prefWrapper.setBoolean(PhonexConfig.APP_CLOSED_BY_USER, false);
        prefWrapper.setBoolean(PhonexConfig.QUICKLOGIN_USED, false);

        if (SipProfile.getCurrentProfile(this) != null && MemoryPrefManager.hasStoredCredentials(this)) {
            Log.d(THIS_FILE, "Active SIP profile detected, proceed to application");
            redirectToApplication();
            finish();
            return;
        }

        PhonexSettings.loadDefaultLanguage(this);
    	lastSavedInstanceState = savedInstanceState;

    	Log.vf(THIS_FILE, "onCreate(): IntoActivity, going to start service and wait for connection; bundle=%s; this=%s",
                savedInstanceState,
                this);

        // Detect first launch
        Boolean hasLaunched = prefWrapper.getBoolean(PreferencesManager.APP_HAS_BEEN_LAUNCHED);
        if (hasLaunched == null || !hasLaunched){
            firstApplicationLaunch = true;
            prefWrapper.setBoolean(PreferencesManager.APP_HAS_BEEN_LAUNCHED, true);
        } else {
            firstApplicationLaunch = false;
        }

        requestGcmToken();

        // Updated app notification can be closed
        new StatusbarNotifications(this).cancelAppUpdateNotif();

    	// Create default service connector
    	connector = new DefaultServiceConnector(this, this);

        SafeNetService.start(this, true);
        XService.start(this, true);

        // Init content view from XML layout
        setContentView(R.layout.activity_intro);

        // Get intent and handle it
        Intent intent = getIntent();
        handleIntents(intent);

        registerReceivers();

        QuickLoginPersister quickLoginPersister = new QuickLoginPersister(this);
        try {
            LoginCredentials credentials = quickLoginPersister.loadCredentials();
            Log.inf(THIS_FILE, "Quick login credentials loaded, we can log in");
            prefWrapper.setBoolean(PhonexConfig.QUICKLOGIN_USED, true);

            quicklogin(credentials);
            return;
        } catch (PasswordPersisterException | ServiceUnavailableException e) {
            Log.wf(THIS_FILE, e, "Unable to load quick login creds, continuing in intro activity");
        }

        // bind service only after we checked we cannot login quicklyconnector.connectService(this, true);
//        activityResumed = new AtomicBoolean(false);
        connector.connectService(this, true);

        addMainFragment(savedInstanceState);
    }

    private void requestGcmToken() {
        Log.df(THIS_FILE, "requestGcmToken");
        if (checkPlayServices()) {
            Log.df(THIS_FILE, "requesting Gcm token - play services available");
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, RegistrationIntentService.class);
            startService(intent);
        }
    }

    private void redirectToIntroductionIfUnseen(){
        // Show introduction to PhoneX if not seen yet
        Boolean introductionSeen = prefWrapper.getBoolean(PreferencesManager.APP_INTRODUCTION_SEEN);
        if (introductionSeen == null || !introductionSeen){
            boolean loginRemembered = LoginUtils.isLoginRemembered(prefWrapper);
            startIntroduction(loginRemembered);
        }
    }

    private void startIntroduction(boolean loginRemembered){
        Intent intent = new Intent(this, SliderActivity.class);
        // If user has saved login name in the form, he definitely already owns an account
        // Otherwise we assume he doesn't
        intent.putExtra(SliderActivity.EXTRA_ALREADY_HAS_ACCOUNT, loginRemembered);
        startActivityForResult(intent, SLIDER_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.vf(THIS_FILE, "onActivityResult; requestCode=%d, resultCode=%d", requestCode, resultCode);
        if (requestCode == SLIDER_ACTIVITY_REQUEST_CODE){
            switch (resultCode){
                case SliderActivity.ACTIVITY_RESPONSE_CODE_LOGIN:
                    // stay on this screen
                    saveIntroductionShown();
                    break;
                case SliderActivity.ACTIVITY_RESPONSE_CODE_NEW_ACCOUNT:
                    saveIntroductionShown();

                    Log.vf(THIS_FILE, "Going to new account fragment");
                    Fragment newFragment = CreateAccountFragment.newInstance();
                    if (getFragmentManager() != null) {
                        getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_content, newFragment, IntroActivity.CREATE_TRIAL_FRAGMENT_TAG)
                                .addToBackStack(null)
                                .commit();
                    }

                    break;
                case SliderActivity.ACTIVITY_RESPONSE_CODE_QUIT:
                    saveIntroductionShown();
                    break;
                default:
                    // for everything else, do nothing
                    break;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void saveIntroductionShown(){
        prefWrapper.setBoolean(PreferencesManager.APP_INTRODUCTION_SEEN, true);
    }


    private void registerReceivers(){
        // Register resurrection listener.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intents.ACTION_RESURRECTED);
        filter.addAction(Intents.ACTION_CHECK_LOGIN);
        resurrectionReceiver = new ResurrectionReceiver();
        MiscUtils.registerReceiver(getApplicationContext(), resurrectionReceiver, filter);

        // Sometimes after restart, this may help to redirect user back to app
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Intents.ACTION_SIP_REGISTRATION_CHANGED_REGISTERED);
        registrationChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals(Intents.ACTION_SIP_REGISTRATION_CHANGED_REGISTERED)){
                    Log.v(THIS_FILE, "Account registered, try to start app.");
                    // here check if we are currently trying to log in. If so, do not redirect

                    if (loginProgressFragment!=null && (loginProgressFragment.getTask() != null)){
                        onLoginFinished();
                    }
                }
            }

        };
        MiscUtils.registerReceiver(this, registrationChangedReceiver, intentfilter);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intents.ACTION_LOGIN_CANCELLED);
        intentFilter.addAction(Intents.ACTION_LOGIN_FINISHED);
        intentFilter.addAction(Intents.ACTION_LOGIN_PASSWORD_CHANGE);
        intentFilter.addAction(Intents.ACTION_LOGIN_PROGRESS);

        loginEventReceiver = new LoginEventReceiver();
        registerReceiver(loginEventReceiver, intentFilter);
    }
    
    /**
     * Handles incoming intents for this activity.
     * Is used e.g., for extracting user credentials from intent (user clicked on a link in a web browser).
     * 
     * @param intent
     */
    protected void handleIntents(Intent intent){
    	final String action = intent.getAction();
        final String type = intent.getType();
        
        Log.vf(THIS_FILE, "Intent: %s; action=%s; type=%s", intent, action, type);
        if (Intent.ACTION_VIEW.equals(action)) {
        	final Uri data = intent.getData();
            final List<String> segments = intent.getData().getPathSegments();
            if (segments == null || segments.isEmpty()) return;

            // if trial segment, go ahead, trial links with credentials handling
            if ("trial".equalsIgnoreCase(segments.get(0))){
                final String v = data.getQueryParameter("v");
                final String l = data.getQueryParameter("l");
                final String p = data.getQueryParameter("p");
                if (!"1".equalsIgnoreCase(v)
                        || l==null
                        || p==null
                        || TextUtils.isEmpty(l)
                        || TextUtils.isEmpty(v)
                        ) return;

                creds = new CredsData();
                creds.l = l;
                creds.p = p;
                creds.v = v;
                creds.when = System.currentTimeMillis();
            } else if("recoverycode".equalsIgnoreCase(segments.get(0))){
                Log.df(THIS_FILE, "recoverycode, segments=%s", segments);
                if (segments.size() == 3){
                    Fragment newFragment = ForgotPasswordFragment.newInstance(segments.get(1), segments.get(2));
                    if (getFragmentManager() != null) {
                        getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_content, newFragment, IntroActivity.FORGOT_PASSWORD_FRAGMENT_TAG)
                                .addToBackStack(null)
                                .commit();
                    }
                } else {
                    Log.wf(THIS_FILE, "recoverycode, segments size is not 3");
                }
           }
        }

        // Cancel notifications
        if (intent.hasExtra(EXTRA_CANCEL_NOTIFS)){
            cancelNotifications = intent.getBooleanExtra(EXTRA_CANCEL_NOTIFS, false);
            if (cancelNotifications && connector!=null && connector.isConnected()){
                try {
                    Log.v(THIS_FILE, "Cancel notifications");

                    connector.getService().cancelAllNotifications();
                    cancelNotifications=false;
                } catch(Exception ex){
                    Log.e(THIS_FILE, "Exception in cancelling notifications", ex);
                }
            }
        }

        // If the service was just killed and there is some decryption salt, try it.
        if (intent.hasExtra(EXTRA_JUST_KILLED)
                && intent.getBooleanExtra(EXTRA_JUST_KILLED, false)
                && intent.hasExtra(EXTRA_DECRYPT_SALT)
                && intent.hasExtra(EXTRA_DECRYPT_IV))
        {
            decryptionSalt = intent.getStringExtra(EXTRA_DECRYPT_SALT);
            decryptionIv = intent.getStringExtra(EXTRA_DECRYPT_IV);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cannot be invoked earlier because onActivityResult() has to be called first (it checks that intro has been seen)
        redirectToIntroductionIfUnseen();

        Intent intent = getIntent();
        if (intent != null){
            boolean secondDeviceDetected = intent.getBooleanExtra(Intents.EXTRA_LOGOUT_SECOND_DEVICE_DETECTED, false);
            if (secondDeviceDetected){
                AlertDialogFragment.info(this, R.string.second_device_detected_title, R.string.second_device_detected_desc);
            }
        }

        activityResumed.set(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed.set(false);
    }

    @Override
	protected void onStart() {
		super.onStart();

		if (!connector.isConnected()){
            Log.d(THIS_FILE, "Service=null, starting waiting progressbar dialog");
            if (progressDialog!=null) {
                this.progressDialog.show();
            }
		}
	}

	@Override
	protected void onDestroy() {
        super.onDestroy();

        dismissResurrectProgress();
		if (connector != null && connector.isConnected()){
			connector.disconnectService(this);
		}

        unregisterResurrectionListener();
        if (registrationChangedReceiver != null) {
            unregisterReceiver(registrationChangedReceiver);
            registrationChangedReceiver = null;
        }

        if (loginEventReceiver != null) {
            unregisterReceiver(loginEventReceiver);
            loginEventReceiver = null;
        }
	}

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();

        if (fm == null){
            super.onBackPressed();
            return;
        }
        if (fm.getBackStackEntryCount() == 0){
            super.onBackPressed();
            return;
        } else {
            fm.popBackStack();
        }
    }

	/**
	 * Initializes ProgressDialog waiting for service to bind to the activity.
	 * Closing this dialog before finishing (i.e., canceling) will close the whole activity.
	 * @param message
	 * @param title
	 */
	private void initResurrectProgress(String message, String title){
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(title)
                .content(message)
                .progress(true, 0)
                .cancelable(true)
                .negativeText(R.string.cancel);
        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onNegative(MaterialDialog dialog) {
                IntroActivity.this.finish();
            }
        });
        progressDialog = builder.build();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialogType = 1;
	}

	/**
	 * Callback from service connector - on XService connected.
	 */
	@Override
	public void onXServiceConnected(ComponentName arg0, IBinder arg1) {
		Log.v(THIS_FILE, "service: onServiceConnected");

        // If notifications should be cancelled - info from home activity with no user.
        if (cancelNotifications){
            try {
                if (connector!=null && connector.isConnected()){
                    Log.v(THIS_FILE, "Cancel notifications, onConnected()");

                    connector.getService().cancelAllNotifications();
                    cancelNotifications = false;
                }
            } catch(Exception e){
                Log.e(THIS_FILE, "Exception in notification cancellation", e);
            }
        }

        getService();

        // run OnServiceConnected on UIthread now
        runOnUiThread(() -> onServiceConnected(lastSavedInstanceState));
	}

	/**
	 * Callback from service connector - on XService disconnected.
	 */
	@Override
	public void onXServiceDisconnected(ComponentName arg0) {
        unregisterResurrectionListener();
	}

    /**
     * Tries to unregister receiver if not null;
     */
    public void unregisterResurrectionListener(){
        if (resurrectionReceiver==null){
            return;
        }

        try {
            this.unregisterReceiver(resurrectionReceiver);
            resurrectionReceiver = null;
        } catch(Exception ex){

        }
    }

	/**
	 * Custom event handler for service connected.
	 * @param savedInstanceState
	 */
	protected void onServiceConnected(Bundle savedInstanceState){
        Log.vf(THIS_FILE, "onServiceConnected");

        boolean runInitTask = true;

        synchronized (delayedLoginMutex){
            if (delayedLoginIntent != null) {
                Log.df(THIS_FILE, "onServiceConnected; delayed login found, broadcasting");
                MiscUtils.sendBroadcast(this, delayedLoginIntent);
                delayedLoginIntent = null;
                runInitTask = false;
            }
        }

        if (runInitTask){
            InitTask initTask = new InitTask();
            initTask.execute();
        }
    }
	
	protected void addMainFragment(Bundle savedInstanceState){
        if (savedInstanceState == null){
                IntroSetupFragment introSetupFragment = new IntroSetupFragment();
                introSetupFragment.setCreds(creds);
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_content, introSetupFragment, INTRO_SETUP_FRAGMENT_TAG)
                        .commit();
        }
	}

    @Override
    public void onAccountCreated(SerializableLoginParams parameters){
        AnalyticsReporter.from(this).event(AppEvents.ACCOUNT_CREATED);

        getFragmentManager().popBackStack();
        TrialCreatedDialogFragment.newInstance(parameters)
                .show(getFragmentManager(), "info");
    }

    @Override
    public void onLoginInitiated(SerializableLoginParams parameters){
        Log.vf(THIS_FILE, "onLoginInitiated");
        LoginUtils.rememberLogin(prefWrapper, true, parameters.username);
        login(parameters.sip, parameters.password, parameters.domain);
    }
	
	/**
	 * Returns IService - binded instance of XService, if there is any.
	 * @return service
	 */
	public IService getService() {
        Log.df(THIS_FILE, "connector == null [%b], connector.getService() == null [%b]", connector == null, connector == null || connector.getService() == null);
		return connector==null ? null : connector.getService();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.vf(THIS_FILE, "Saving to bundle; bundle=%s", outState);
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	Log.v(THIS_FILE, "onCreateOptionsMenu");

        // Phone-X site
        menu.add(Menu.NONE, HOMEPAGE_MENU, Menu.NONE, R.string.project_page)
                .setIcon(android.R.drawable.ic_menu_info_details)
                .setOnMenuItemClickListener(item -> {
                    Uri uri = Uri.parse("https://www.phone-x.net/");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return true;
                })
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // Currently turned off because it's distracting
//        // Trial license
//        menu.add(Menu.NONE, TRIAL_MENU, Menu.NONE, R.string.obtain_trial)
//                .setIcon(android.R.drawable.ic_menu_week)
//                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

//        // Help
//        menu.add(Menu.NONE, HELP_MENU, Menu.NONE, R.string.help)
//        .setIcon(android.R.drawable.ic_menu_help)
//        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        // Intro
        menu.add(Menu.NONE, INTRO_MENU, Menu.NONE, R.string.intro_info)
        .setIcon(android.R.drawable.ic_menu_compass)
        .setOnMenuItemClickListener(item -> {
            startIntroduction(true);
            return true;
        })
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case HELP_MENU:
                Intent intent = new Intent(this, HelpListActivity.class);
                startActivity(intent);
                return true;
            case INTRO_MENU: {
                Fragment fragment = getFragmentManager().findFragmentByTag(FIRST_LOGIN_FRAGMENT_TAG);
                if (fragment == null || !fragment.isVisible()) {
                    Fragment firstLaunchFragment = FirstLaunchFragment.newInstance();
                    getFragmentManager().beginTransaction()
                            .replace(R.id.fragment_content, firstLaunchFragment, FIRST_LOGIN_FRAGMENT_TAG)
                            .addToBackStack(null)
                            .commit();
                }
                return true;
            }
            case TRIAL_MENU: {
                Fragment fragment = getFragmentManager().findFragmentByTag(CREATE_TRIAL_FRAGMENT_TAG);
                if (fragment == null || !fragment.isVisible()) {
                    Fragment newFragment = CreateAccountFragment.newInstance();
                    getFragmentManager().beginTransaction()
                            .replace(R.id.fragment_content, newFragment, CREATE_TRIAL_FRAGMENT_TAG)
                            .addToBackStack(null)
                            .commitAllowingStateLoss();
                }
                return true;
            }
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Event indicated on resurrected event received.
     */
    private void onResurrected(){
        // Show waiting dialog to the user so he knows that something is going on.
        Log.v(THIS_FILE, "It was resurrected");
        dismissResurrectProgress();

        try {
            this.initResurrectProgress(getString(R.string.intro_resurrect_msg), getString(R.string.intro_init_title));
            this.progressDialog.show();
            progressDialogType = 2;
        } catch(Exception ex){
            Log.w(THIS_FILE, "Exception: cannot show dialog", ex);
        }

        // Expire dialog after 15 seconds.
        new Thread(() -> {
            try {
                Thread.sleep(20000);

                // If dialog was meanwhile changed, ignore this...
                if (progressDialogType!=2){
                    return;
                }

                dismissResurrectProgress();
            } catch(Exception ex){
                Log.e(THIS_FILE, "Waiting exception" ,ex);
            }
        }).start();
    }

    /**
     * Dismiss active progress bar.
     */
    private void dismissResurrectProgress(){
        if (progressDialog!=null && progressDialog.isShowing()){
            try {
                progressDialog.dismiss();
                progressDialog = null;
            } catch(Exception e){
                Log.e(THIS_FILE, "Exception: cannot dismiss diallog", e);
            }
        }
    }

    @Override
    public void onPasswordChangeCompleted(PasswordChangeV2Response response, PasswordChangeParams params) {
        if (response.getResult() != 1){
            AlertDialogFragment.newInstance(getString(R.string.p_error),getString(R.string.p_password_change_err) ).show(getFragmentManager(), "TAG");
            return;
        } else {
            SipUri.ParsedSipContactInfos in = SipUri.parseSipContact(params.getUserSIP());
            // here progress fragment should already be initialized, therefore do not create a new one and login
            login(params.getUserSIP(), params.getUserNewPass(), in.domain, false);
        }
    }

    @Override
    public void onPasswordSelected(PasswordChangeParams params) {
        try {
            // again display login fragment
            initProgressFragment();

            ChangePasswordTask task = new ChangePasswordTask(this);
            task.setContext(this);
            task.setFragmentManager(getFragmentManager());
            loginProgressFragment.setTask(task);
            task.execute(params);

        } catch(Exception e){
            Log.w(THIS_FILE, "Exception", e);
        }
    }

    public void quicklogin(LoginCredentials credentials){
        quicklogin(credentials.getSip(), credentials.password, credentials.domain);
    }

    public void quicklogin(final String sip, final CharSequence password, final String userDomain) {
        Intent intent = new Intent(Intents.ACTION_TRIGGER_QUICK_LOGIN);
        intent.putExtra(Intents.EXTRA_LOGIN_SIP, sip);
        intent.putExtra(Intents.EXTRA_LOGIN_PASSWORD, password.toString());
        intent.putExtra(Intents.EXTRA_LOGIN_DOMAIN, userDomain);
        MiscUtils.sendBroadcast(this, intent);
        Log.vf(THIS_FILE, "Intent to init Quick login sent");
    }

        /**
         * Entry point for login
         * @param sip
         * @param password
         * @param userDomain
         */

    public void login(final String sip, final CharSequence password, final String userDomain){
        login(sip, password, userDomain, true);
    }

    private void login(final String sip, final CharSequence password, final String userDomain, boolean showNewProgressFragment){
        Log.vf(THIS_FILE, "login; sip [%s], userDomain [%s]", sip, userDomain);

        // Keep this window on until login finishes.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = new Intent(Intents.ACTION_TRIGGER_LOGIN);
        intent.putExtra(Intents.EXTRA_LOGIN_SIP, sip);
        intent.putExtra(Intents.EXTRA_LOGIN_PASSWORD, password.toString());
        intent.putExtra(Intents.EXTRA_LOGIN_DOMAIN, userDomain);

        synchronized (delayedLoginMutex){
            if (getService() == null){
                // delay login
                Log.wf(THIS_FILE, "onConnectClick; cannot init login procedure, service is not bind");
                if (showNewProgressFragment){
                    if (initProgressFragment()) {
                        loginProgressFragment.updateProgress(getString(R.string.p_waiting_for_service));
                    }
                }
                delayedLoginIntent = intent;
            } else {
                if (showNewProgressFragment){
                    initProgressFragment();
                }
                // run login now
                Log.inf(THIS_FILE, "onConnectClick; initiating login procedure");
                delayedLoginIntent = null;
                Log.df(THIS_FILE, "onConnectClick; normal login, broadcasting");
                MiscUtils.sendBroadcast(this, intent);
            }
        }
    }

    private synchronized boolean initProgressFragment(){
        if (loginProgressFragment != null && loginProgressFragment.isVisible()){
            return true;
        }

        if (!activityResumed.get()) {
            return false;
        }

        loginProgressFragment = new LoginProgressDialogFragment();
        loginProgressFragment.setMessage(getString(R.string.p_authentication));
//        loginProgressFragment.setTitle(getString(R.string.p_authentication));
        loginProgressFragment.setCanClose(false); //set true before the last task
        loginProgressFragment.setCheckpointsNumber(14);
        loginProgressFragment.show(getFragmentManager(), TASK_FRAGMENT_TAG);
        return true;
    }

    @Override
    public void onLoginCancelled(String localizedErrorMessage) {
        onLoginCancelled(getString(R.string.p_authentication_failed), localizedErrorMessage);
    }

    @Override
    public void onLoginCancelled(String localizedErrorTitle, String localizedErrorMessage) {
        // On quick login - add login screen back
        try {
            if (prefWrapper.getBoolean(PhonexConfig.QUICKLOGIN_USED)) {
                connector.connectService(this, true);
                addMainFragment(null);
                Log.d(THIS_FILE, "Cancel on quicklogin - main fragment inserted");
            }
        } catch(Exception e){
            Log.ef(THIS_FILE, e, "Recovery after quicklogin failed");
        }

        try {
            loginProgressFragment.setTask(null);
            loginProgressFragment.dismiss();
            AlertDialogFragment.newInstance(localizedErrorTitle, localizedErrorMessage).showAllowingStateLoss(getFragmentManager(), "TAG");
        }catch (Exception e){
            Log.ef(THIS_FILE, e, "onLoginCancelled; error occurred");
        }
    }

    private void redirectToApplication() {
        Intent phonexIntent = new Intent(this, PhonexActivity.class);
        phonexIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        phonexIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(phonexIntent);
    }

    @Override
    public void onLoginFinished() {
        if(loginProgressFragment != null) {
	        loginProgressFragment.setTask(null);
	    }
        Log.vf(THIS_FILE, "onLoginFinished");

        // Hide active progress dialog, if there is any
        dismissResurrectProgress();

        redirectToApplication();

        finish();
    }

    @Override
    public void passwordChangeRequired(PasswordChangeParams parameters) {
        if (loginProgressFragment != null){
            loginProgressFragment.dismiss();
            loginProgressFragment.setTask(null);
        }
        ChangePasswordDialogFragment df = ChangePasswordDialogFragment.newInstance(parameters, this);
        df.showAllowingStateLoss(getFragmentManager(),"");
    }

    /**
	 * Task to initialize application. 
	 */
	private class InitTask extends AsyncTask<String, Void, Void>{
		@Override
		protected Void doInBackground(String... params) {
			Log.v(THIS_FILE, "Going to load user & init https.");
			
			if (getService()!=null){
	        	try {
	        		 // ID of the default user profile, for now it is static, 1.
	        		 // TODO: Refactor this.
					 state = getService().getSipProfileState(1); 
					 
					 // Set default HTTPS parameters - custom trustVerifier.
					 // Do in background thread.
					 new Thread(() -> {
                         try {
                             SSLSOAP.installTrustManager4HTTPS(null, null, getApplicationContext());
                             Log.v(THIS_FILE, "Initialized default ssl socket factory");
                          } catch(Exception e) {
                             Log.e(THIS_FILE, "Cannot configure default HTTPS.");
                          }
                     }).start();

                    // Load prefs if there is some indication of a snapshot saved.
                    if (!TextUtils.isEmpty(decryptionSalt) && !TextUtils.isEmpty(decryptionIv)){
                        Log.v(THIS_FILE, "Snapshot may be saved");

                        // Try to load stored snapshot.
                        MemoryPrefManager.loadSnapshot(getApplicationContext(), decryptionSalt, decryptionIv);
                        decryptionIv=null;
                        decryptionSalt=null;
                    }

				} catch (RemoteException e) {
					Log.w(THIS_FILE, "Profile 1 is null");
				}	
	        }
			
			return null;
		}

		@Override
		protected void onCancelled() {
			onFinished();
		}

		@Override
		protected void onPostExecute(Void result) {
			onFinished();
		}
		
		protected void onFinished(){
			Log.d(THIS_FILE, "Task finished");
			
	    	// Progress dialog is not needed now if waiting for service to connect.
            // If waiting for resurrection, keep it.
            if (progressDialogType==1) {
                dismissResurrectProgress();
            }
	    	
	        // If is active, redirect to PhoneX activity.
	        if (state != null && state.isActive()){
	        	onLoginFinished();
	        }
		}
	}

    /**
     * Listener for resurrection events.
     */
    private class ResurrectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (Intents.ACTION_CHECK_LOGIN.equals(action)){
                onLoginFinished();
            } else if (Intents.ACTION_RESURRECTED.equals(action)) {
                onResurrected();
            } else {
                Log.wf(THIS_FILE, "Unknown intent action [%s]", action);
                return;
            }
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        final GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Log.ef(THIS_FILE, "Google play services are not available");
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.ef(THIS_FILE, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
}
