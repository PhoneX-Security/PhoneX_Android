package net.phonex.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.camera.CameraActivity;
import net.phonex.core.Intents;
import net.phonex.core.MemoryPrefManager;
import net.phonex.login.LogoutIntentService;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.PairingRequest;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.events.NetworkStateReceiver;
import net.phonex.inapp.ReloadPurchasesService;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.SafeNetService;
import net.phonex.service.XService;
import net.phonex.ui.account.AccountPreferences;
import net.phonex.ui.actionbar.StatusView;
import net.phonex.ui.addContact.AddContactActivity;
import net.phonex.ui.broadcast.BroadcastMessageActivity;
import net.phonex.ui.chat.MessageActivity;
import net.phonex.ui.cl.ContactListFragment;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.fileManager.FileManagerActivity;
import net.phonex.ui.help.HelpListActivity;
import net.phonex.ui.inapp.LicenseExpiredDialogFragment;
import net.phonex.ui.inapp.ManageLicenseActivity;
import net.phonex.ui.interfaces.OnConnectivityChanged;
import net.phonex.ui.intro.IntroActivity;
import net.phonex.ui.invite.InviteActivity;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.preferences.ChangeRecoveryEmailFragment;
import net.phonex.ui.slidingtab.MyPagerAdapter;
import net.phonex.ui.slidingtab.SlidingTabLayout;
import net.phonex.ui.versionUpdate.VersionChecker;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.ProfileUtils;
import net.phonex.util.SimpleContentObserver;
import net.phonex.util.account.AccountManagerFactory;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;
import net.phonex.util.analytics.AppEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shining new PhonexActivity
 * @author miroc
 */
public class PhonexActivity extends LockActionBarActivity implements OnConnectivityChanged {
    public static final String TAG = "PhonexActivity";

    public static final int REQUEST_EDIT_DISTRIBUTION_ACCOUNT = 0;
    public final static int REQUEST_CODE_CHANGE_PREFS = 1;

    public static final int SETTINGS_MENU = Menu.FIRST + 2;
    public static final int SWITCH_OFF_MENU = Menu.FIRST + 3;
    public static final int HELP_MENU = Menu.FIRST + 4;
    public static final int ACCOUNT_MANAGER_MENU = Menu.FIRST + 5;
//    public static final int BROADCAST_MESSAGE_MENU = Menu.FIRST + 6;
    public static final int FILE_MANAGER_MENU = Menu.FIRST + 7;
    public static final int CAMERA_MENU = Menu.FIRST + 8;
    public static final int OPTIONS_ADD_BUDDY = Menu.FIRST + 10;
    public static final int MANAGE_LICENSE_MENU = Menu.FIRST + 11;
    public static final int INVITE_FRIEND_MENU = Menu.FIRST + 12;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private Toolbar toolbar;
    private ActionMenuView bottomMenuView;
    private ViewPager viewPager;
    private MyPagerAdapter viewPagerAdapter;
    private SlidingTabLayout slidingTabLayout;
    private ViewGroup appClosingWarning;

    private SipProfile sipProfile;

    // Fragments we use in SliderTabs
    public static final int CONTACT_LIST_TAB_POS = 0;
    public static final int CONVERSATIONS_TAB_POS = 1;
    public static final int NOTIFICATIONS_TAB_POS = 2;

    // Observers
    private final Handler mHandler = new Handler();
    private SimpleContentObserver callLogObserver = new SimpleContentObserver(mHandler, () -> loadUnseenNotificationsCount(true));
    private SimpleContentObserver sipMessagesObserver = new SimpleContentObserver(mHandler, () -> loadUnreadConversationsCount(true));
    private SimpleContentObserver pairingRequestsObserver = new SimpleContentObserver(mHandler, () -> loadUnseenPairingRequestsCount(true));

    private Locale lastKnownLocale;
    private PreferencesConnector prefProviderWrapper;
    private VersionChecker versionChecker;

    private List<BroadcastReceiver> broadcastReceivers = new ArrayList<>();
    private StatusView statusView;
    private ImageButton broadcastButton;

    private PreferencesConnector preferencesConnector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lastKnownLocale = PhonexSettings.loadDefaultLanguage(this);
        prefProviderWrapper = new PreferencesConnector(this);

        setContentView(R.layout.phonex_activity);
        preferencesConnector = new PreferencesConnector(this);

        statusView = new StatusView(this);

        initToolbar(statusView);
        initTabs(savedInstanceState);

        appClosingWarning = (ViewGroup) findViewById(R.id.warning_container);


        registerReceivers();

        sipProfile = SipProfile.getCurrentProfile(this);

        Log.setLogLevel(PhonexSettings.debuggingRelease() ? prefProviderWrapper.getLogLevel() : 0);

        // Dialog for displaying there is a new version
        versionChecker = new VersionChecker(this);

        selectTabWithAction(getIntent());

        showExpirationWarning();
        showMissingRecoveryEmailWarning(sipProfile);
        ReloadPurchasesService.initCheck(preferencesConnector, this);
    }

    private void showMissingRecoveryEmailWarning(SipProfile sipProfile) {
        // show status bar notification if email is missing and user has not seen this
        ChangeRecoveryEmailFragment.showNotificationIfMissing(this, sipProfile);

    }

    private void showExpirationWarning() {
        LicenseExpiredDialogFragment fragment = LicenseExpiredDialogFragment.newInstance(sipProfile);
        fragment.showIfExpired(getFragmentManager(), this);
    }


    private void initToolbar(StatusView statusView) {
        toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        drawerLayout = (DrawerLayout) findViewById(R.id.my_drawer_layout2);

        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        // init drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.application_name, R.string.application_name);
        drawerLayout.setDrawerListener(drawerToggle);

        //  add custom views
        // broadcast button should only be visible for messages -- hack, not added through menu because we want status switcher to be on the right
        broadcastButton = (ImageButton) LayoutInflater.from(this).inflate(R.layout.button_broadcast, null);
        broadcastButton.setVisibility(View.GONE);
        broadcastButton.setOnClickListener(v -> {
            getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_BROADCAST_MESSAGE);
                Intent it = new Intent(this, BroadcastMessageActivity.class);
                startActivity(it);
        });

        toolbar.addView(statusView, new Toolbar.LayoutParams(Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        toolbar.addView(broadcastButton, new Toolbar.LayoutParams(Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        // Add Bottom Menu
        bottomMenuView = (ActionMenuView) findViewById(R.id.bottom_menu_view);
        getMenuInflater().inflate(R.menu.phonex_activity_menu, bottomMenuView.getMenu());

        // Initialize search button
        MenuItem item = bottomMenuView.getMenu().findItem(R.id.action_search);
        SearchView search = (SearchView) MenuItemCompat.getActionView(item);
        if (search != null){
            search.setOnSearchClickListener(view -> {
                getAnalyticsReporter().buttonClick(AppButtons.MAIN_ACTIVITY_SEARCH);
                switchToFragment(CONTACT_LIST_TAB_POS);
            });
            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    ContactListFragment fragment = (ContactListFragment) getFragment(CONTACT_LIST_TAB_POS);
                    if (fragment!=null && fragment.isAdded()){
                        fragment.filterByDisplayName(s);
                    }
                    return true;

                }
            });
        } else {
            Log.df(TAG, "search is null");
        }

        bottomMenuView.setOnMenuItemClickListener(menuItem -> onOptionsItemSelected(menuItem));
    }

    private void registerReceivers(){
        StatusView.RegistrationChangeListener statusChangeReceiver = statusView.new RegistrationChangeListener();
        statusChangeReceiver.register(this);
        broadcastReceivers.add(statusChangeReceiver);

        NetworkStateReceiver networkStateReceiver = new NetworkStateReceiver(this);
        registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        broadcastReceivers.add(networkStateReceiver);

        LogoutEventReceiver logoutEventReceiver = new LogoutEventReceiver();
        IntentFilter logoutIntentFilter = new IntentFilter(Intents.ACTION_LOGOUT);
        logoutIntentFilter.addAction(Intents.ACTION_QUIT_SAFENET);
        MiscUtils.registerReceiver(this, logoutEventReceiver, logoutIntentFilter);
        broadcastReceivers.add(logoutEventReceiver);
    }

    private void unregisterReceivers(){
        for (BroadcastReceiver br : broadcastReceivers){
            unregisterReceiver(br);
        }
        broadcastReceivers.clear();
    }

    private void registerNotificationsObservers(){
        Log.vf(TAG, "registerNotificationsObservers");
        getContentResolver().registerContentObserver(CallLogScheme.URI, true, callLogObserver);
        getContentResolver().registerContentObserver(SipMessage.MESSAGE_URI, true, sipMessagesObserver);
        getContentResolver().registerContentObserver(PairingRequest.URI, true, pairingRequestsObserver);
    }

    private void unregisterNotificationsObservers(){
        Log.vf(TAG, "unregisterNotificationsObservers");
        getContentResolver().unregisterContentObserver(callLogObserver);
        getContentResolver().unregisterContentObserver(sipMessagesObserver);
        getContentResolver().unregisterContentObserver(pairingRequestsObserver);
    }

    Fragment getFragment(int position){
        return viewPagerAdapter.getRegisteredFragment(position);
    }

    private void initTabs(Bundle savedInstanceState){
        viewPagerAdapter =  new MyPagerAdapter(getFragmentManager(), this, toolbar);

        // Assigning ViewPager View and setting the viewPagerAdapter
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(viewPagerAdapter);

        // Assigning the Sliding Tab Layout View
        slidingTabLayout = (SlidingTabLayout) findViewById(R.id.tabs);
        slidingTabLayout.setDistributeEvenly(true); // To make the Tabs Fixed set this true, This makes the slidingTabLayout Space Evenly in Available width
        slidingTabLayout.setOnPageChangeListener(viewPagerAdapter);

        // Setting Custom Color for the Scroll bar indicator of the Tab View
        slidingTabLayout.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {
            @Override
            public int getIndicatorColor(int position) {
                return getResources().getColor(R.color.phonex_color_accent);
            }
        });

        // Setting the ViewPager For the SlidingTabsLayout
        slidingTabLayout.setViewPager(viewPager);
    }

//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        // activity.invalidateOptionsMenu();
//        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.menu.phonex_activity_message_menu, menu);
//
//        return super.onPrepareOptionsMenu(menu);
//    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if(drawerLayout.isDrawerOpen(Gravity.LEFT)){
            drawerLayout.closeDrawers();
            return;
        }
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }
        super.onBackPressed();
    }

    public void closeNavigationDrawers(){
        if (drawerLayout != null){
            drawerLayout.closeDrawers();
        }
    }

    public void showCloseDialog(){
        AlertDialogFragment.newInstance(getString(R.string.logout), getString(R.string.quit_info))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> logout(false))
                .setNegativeButton(getString(R.string.cancel), null)
                .show(getFragmentManager(), "");
    }

    public void navigateToMyAccountActivity(){
        Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI, new String[]{
                SipProfile.FIELD_ID
        }, SipProfile.FIELD_ACCOUNT_MANAGER + "=?", new String[]{
                AccountManagerFactory.getDefaultManagerId()
        }, null);

        Intent it = new Intent(this, AccountPreferences.class);
        it.putExtra(SipProfile.FIELD_ACCOUNT_MANAGER, AccountManagerFactory.getDefaultManagerId());
        Long accountId = null;
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    accountId = c.getLong(c.getColumnIndex(SipProfile.FIELD_ID));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in account manager get", e);
            } finally {
                c.close();
            }
        }
        if (accountId != null) {
            it.putExtra(SipProfile.FIELD_ID, accountId);
        }
        startActivityForResult(it, REQUEST_EDIT_DISTRIBUTION_ACCOUNT);
    }


    public void onMenuItemSelected(int menuId){
        switch (menuId) {
//            case BROADCAST_MESSAGE_MENU: {
//                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_BROADCAST_MESSAGE);
//                Intent it = new Intent(this, BroadcastMessageActivity.class);
//                startActivity(it);
//                break;
//            }
            case SETTINGS_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_SETTINGS);
                startActivityForResult(new Intent(Intents.ACTION_UI_PREFS_GLOBAL), REQUEST_CODE_CHANGE_PREFS);
                break;
            }
            case SWITCH_OFF_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_LOGOUT);
                Log.d(TAG, "CLOSE menu selected");
                showCloseDialog();
                break;
            }
            case HELP_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_HELP);
                // Create the fragment and show it as a dialog.
                Intent intent = new Intent(this, HelpListActivity.class);
                startActivity(intent);
                break;

            }
            case ACCOUNT_MANAGER_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_MY_ACCOUNT);
                navigateToMyAccountActivity();
                break;
            }
            case FILE_MANAGER_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_FILE_MANAGER);
                Intent it = new Intent(this, FileManagerActivity.class);
                it.putExtra(FileManagerActivity.EXTRA_SECURE_STORAGE_ONLY, true);
                startActivity(it);
                break;
            }
            case CAMERA_MENU: {
                getAnalyticsReporter().buttonClick(AppButtons.NAV_DRAWER_CAMERA);
                Intent it = new Intent(this, CameraActivity.class);
                startActivity(it);
                break;
            } case MANAGE_LICENSE_MENU: {
                ManageLicenseActivity.redirectFrom(this);
                break;
            } case INVITE_FRIEND_MENU: {
                startActivity(new Intent(this, InviteActivity.class));
                break;
            }
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {

            case R.id.action_add_buddy: {
                AnalyticsReporter.from(this).buttonClick(AppButtons.MAIN_ACTIVITY_ADD_CONTACT);
                Intent it = new Intent(this, AddContactActivity.class);
                startActivityForResult(it, 0);
            }
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean b = toolbar.hideOverflowMenu();
        Log.df(TAG, "initToolbar; hideOverflowMenu[%s]", b);

        // quite important, maybe cause for PHON-689 if missing
        // TODO - since quick start, experimentally turned off
//        startServices();

        // Check whether locale has not been changed.
        Locale currentLocale = PhonexSettings.loadDefaultLanguage(this);

        // Check current version and display appropriate info (show update available or show release notes if this is first run)
        versionChecker.checkVersion();

        toolbar.setTitle(viewPagerAdapter.getPageTitle(viewPager.getCurrentItem()));

        // If the locale changed, re-init this activity so it takes effect also here.
        // Null check is essential so as not to stuck in reset loop.
        if (lastKnownLocale != null && !lastKnownLocale.equals(currentLocale)){
            Log.vf(TAG, "LastLocale=%s, current locale=%s", lastKnownLocale, currentLocale);

            lastKnownLocale = currentLocale;
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }

        // show app closing warning if required
        boolean appClosing = MemoryPrefManager.getPreferenceBooleanValue(this, MemoryPrefManager.APP_CLOSING, false);
        Log.df(TAG, "onResume; appClosing=%s", appClosing);
        if (appClosing){
            appClosingWarning.setVisibility(View.VISIBLE);
//            Toast.makeText(this, R.string.application_is_being_closed, Toast.LENGTH_SHORT).show();
//            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNotificationsObservers();
        // when returning from MessageFragment, wait for this count to reload before showing any content to avoid dissappearing notif.
        loadUnreadConversationsCount(true);
        // load notification counts in background
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... params) {
                loadUnseenNotificationsCount(false);
//                loadUnreadConversationsCount(false);
                loadUnseenPairingRequestsCount(false);
                return null;
            }
            @Override
            protected void onPostExecute(Void aVoid) {
                slidingTabLayout.refreshNotificationCounts();
            }
        }.execute();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNotificationsObservers();
    }

    private void checkIntent(Intent intent) {
        if (intent == null){
            return;
        }
        if (Intents.ACTION_LOGOUT.equals(intent.getAction())){
            logout(false);
        } else {
            try {
                selectTabWithAction(intent);
            } catch (Exception e){
                Log.ef(TAG, e, "onNewIntent; unable to select particular tab with intent [%s]", intent);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.inf(TAG, "onNewIntent [%s]", intent);

        checkIntent(intent);
    }

    private void switchToFragment(int position){
        if (viewPager!=null)
            viewPager.setCurrentItem(position);
    }

    private void selectTabWithAction(Intent intent) {
        Log.df(TAG, "selectTabWithAction; intent [%s]", intent);
        String callAction = intent.getAction();
        Integer position = null;

        // TODO
        if (!TextUtils.isEmpty(callAction)) {
            if (callAction.equalsIgnoreCase(Intents.ACTION_CONTACT_LIST)) {
                position = CONTACT_LIST_TAB_POS;
            } else if (callAction.equalsIgnoreCase(Intents.ACTION_SIP_MESSAGES)) {
                position = CONVERSATIONS_TAB_POS;

                Bundle b = intent.getExtras();
                if (b != null) {
                    Log.d(TAG, "Starting received message directly");
                    Intent it = new Intent(this, MessageActivity.class);
                    it.putExtras(b);
                    this.startActivity(it);
                }

            } else if (callAction.equalsIgnoreCase(Intents.ACTION_NOTIFICATIONS)) {
                position = NOTIFICATIONS_TAB_POS;
            }
        }

        Log.df(TAG, "selectTabWithAction; position %s", position);

        if (position != null && viewPager != null){
            viewPager.setCurrentItem(position);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceivers();
        Log.d(TAG, "onDestroy");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CHANGE_PREFS) {
            Locale prevLocale = getResources().getConfiguration().locale;
            Locale newLocale = PhonexSettings.loadDefaultLanguage(this);
            MiscUtils.sendBroadcast(this, new Intent(Intents.ACTION_SETTINGS_MODIFIED));

            // restart activity if locale changed
            if (!newLocale.equals(prevLocale)) {
                restartActivity();
            }
        }
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    private void startServices() {
        Log.d(TAG, "Start services");
        // if application is redirected here (and skipping IntroActivity), start services directly
        SafeNetService.start(this, true);
        XService.start(this, true);

//        Thread t = new Thread("StartXService") {
//            public void run() {
//                Context appContext = PhonexActivity.this.getApplicationContext();
//                if (appContext == null){
//                    Log.ef(TAG, "startServices; Error: application context is null, cannot start XService");
//                    return;
//                }
//
//                Intent serviceIntent = XService.getStartServiceIntent(appContext);
//                serviceIntent.putExtra(Intents.EXTRA_OUTGOING_ACTIVITY, new ComponentName(PhonexActivity.this, PhonexActivity.class));
//
//                // Start service - should run indefinitely.
//                Log.d(TAG, "Going to start XService");
//                startService(serviceIntent);
//
//                postXServiceStart();
//            }
//        };
//        t.start();
    }

//    /**
//     * After SIP service was started.
//     */
//    private void postXServiceStart() {
//        if (sipProfile == null) {
//            Log.e(TAG, "No account in database in PhoneX activity in postServiceStart().");
//                // Start IntroActivity for user to login.
//                Intent phonexIntent = new Intent(this, IntroActivity.class);
//                phonexIntent.setAction("android.intent.category.LAUNCHER");
//                phonexIntent.putExtra(IntroActivity.EXTRA_CANCEL_NOTIFS, true);
//                phonexIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//                phonexIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                startActivity(phonexIntent);
//                finish();
//        }
//    }

    public void logout(boolean goToLogin){
        logout(goToLogin, false);
    }

    private void logout(boolean goToLogin, boolean secondDeviceDetected){
        Log.wf(TAG, "Logging out, redirecting to login [%s]", String.valueOf(goToLogin));
        prefProviderWrapper.setBoolean(PhonexConfig.APP_CLOSED_BY_USER, true);
        ProfileUtils.sendUnregisterIntent(this);
        getAnalyticsReporter().event(AppEvents.LOGOUT);

        finish();
        if (goToLogin){
            Intent intent = new Intent(this, IntroActivity.class);
            if (secondDeviceDetected){
                intent.putExtra(Intents.EXTRA_LOGOUT_SECOND_DEVICE_DETECTED, true);
            }
            startActivity(intent);
        }

        // delete passwords & contact list & messages & callog        ;
        startService(LogoutIntentService.createIntent(this, true));
    }

    private void restartActivity() {
        Log.v(TAG, "Going to restart activity");
        super.recreate();
    }

    // TODO add network listener directly in status view
    @Override
    public void onNetworkConnected() {
        if (statusView!=null){
            statusView.onNetworkConnected();
        }
    }

    @Override
    public void onNetworkDisconnected() {
        if (statusView!=null){
            statusView.onNetworkDisconnected();
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

    void loadUnseenNotificationsCount(boolean refreshTabs){
        int count = CallLog.getNumberOfUnseenLogs(PhonexActivity.this);
        Log.vf(TAG, "loadUnseenNotificationsCount; count=%d", count);
        viewPagerAdapter.setNotificationCount(2, count);
        if (refreshTabs){
            slidingTabLayout.refreshNotificationCounts();
        }
    }

    void loadUnreadConversationsCount(boolean refreshTabs){
        int count = SipMessage.getUnreadConversationsCount(getContentResolver());
        Log.vf(TAG, "loadUnreadConversationsCount; count=%d", count);
        viewPagerAdapter.setNotificationCount(1, count);
        if (refreshTabs){
            slidingTabLayout.refreshNotificationCounts();
        }
    }

    void loadUnseenPairingRequestsCount(boolean refreshTabs){
        int count = PairingRequest.getUnseenCount(getContentResolver());
        Log.vf(TAG, "loadUnseenPairingRequestsCount; count=%d", count);
        viewPagerAdapter.setNotificationCount(0, count);
        if (refreshTabs){
            slidingTabLayout.refreshNotificationCounts();
        }
    }

    public void onTabSwitched(int position) {
        if (position == 1){
            broadcastButton.setVisibility(View.VISIBLE);
        } else {
            broadcastButton.setVisibility(View.GONE);
        }
    }

    public class LogoutEventReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.vf(TAG, "LogoutEventReceiver: receiving intent [%s]", intent);
            if (intent == null || intent.getAction() == null){
                return;
            }

            if (intent.getAction().equals(Intents.ACTION_LOGOUT)) {
                boolean secondDeviceDetected = intent.getBooleanExtra(Intents.EXTRA_LOGOUT_SECOND_DEVICE_DETECTED, false);
                logout(true, secondDeviceDetected);
            } else if (intent.getAction().equals(Intents.ACTION_QUIT_SAFENET)) {
                // if this intent as activity is still alive, finish it
                finish();
            }
        }
    }
}