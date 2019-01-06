package net.phonex.pub.a;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.SurfaceView;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.pjsip.reg.RegListenerModule;
import net.phonex.pjsip.sign.SignModule;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.parcels.SipMsgAux;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.service.XService.ToCall;
import net.phonex.service.runEngine.AndroidTimers;
import net.phonex.service.runEngine.LibraryLoader;
import net.phonex.sip.IPjPlugin;
import net.phonex.sip.PjCallback;
import net.phonex.sip.PjConfig;
import net.phonex.sip.PjUtils;
import net.phonex.sip.SipCallSessionUpdateTask;
import net.phonex.sip.SipStatusCode;
import net.phonex.sip.ZrtpCallbackImpl;
import net.phonex.pub.parcels.MakeCallResult;
import net.phonex.util.Log;
import net.phonex.util.android.AudioUtils;
import net.phonex.util.android.JNILibsManager;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.CertificatesAndKeys.PemPasswordSchemeV2;
import net.phonex.util.crypto.PRNGFixes;
import net.phonex.util.system.ProcKiller;

import net.phonex.xv.Xvi;
import net.phonex.xv.pj_pool_t;
import net.phonex.xv.pj_qos_params;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.pjsip_ssl_method;
import net.phonex.xv.pjsip_tls_setting;
import net.phonex.xv.pjsip_transport_type_e;
import net.phonex.xv.XviConstants;
import net.phonex.xv.pjsua_acc_info;
import net.phonex.xv.pjsua_call_flag;
import net.phonex.xv.pjsua_call_setting;
import net.phonex.xv.pjsua_call_vid_strm_op;
import net.phonex.xv.pjsua_conf_port_info;
import net.phonex.xv.pjsua_msg_data;
import net.phonex.xv.pjsua_transport_config;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;


public class PjManager {
    private static final String TAG = "PjManager";
    public static final int BYE_CAUSE_GSM_BUSY = 499;

    private static ArrayList<String> codecs = new ArrayList<String>();
    private static boolean codecsLoaded = false;
    private final Map<String, IPjPlugin> pjModules = new HashMap<String, IPjPlugin>();
    private XService service;
    private boolean stackLoaded = false;
    private boolean sipStackIsCorrupted = false;
    private boolean forceRestart = false;
    private Integer localUdpAccPjId;
    private Integer localTcpAccPjId;
    private Integer localTlsAccPjId;
    private Integer localUdp6AccPjId;
    private Integer localTls6AccPjId;
    private Integer localTcp6AccPjId;
    private PreferencesConnector prefs;

    private Integer hasBeenHoldByGSM = null;
    private Integer hasBeenChangedRingerMode = null;

    private PjCallback pjCallback;
    private ZrtpCallbackImpl zrtpCallback;
    private MediaManager mediaManager;

    private Timer taskTimer;
    private SignModule signModule = null;
    private String curNatType = "";

    private volatile boolean created = false;
//    private volatile boolean needRestart = false;

    /**
     * Empty constructor.
     */
    public PjManager() {

    }

    public static long getAccountIdForPjsipId(Context ctxt, int pjId) {
        long accId = SipProfile.INVALID_ID;

        Cursor c = ctxt.getContentResolver().query(SipProfile.ACCOUNT_STATUS_URI, null, null, null, null);
        if (c == null) {
            return accId;
        }

        try {
            while (c.moveToNext()) {
                int pjsuaId = c.getInt(c.getColumnIndex(SipProfileState.PJSUA_ID));
                Log.df(TAG, "Found pjsua [%s] searching %s", pjsuaId, pjId);
                if (pjsuaId == pjId) {
                    accId = c.getInt(c.getColumnIndex(SipProfileState.ACCOUNT_ID));
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error on looping over sip profiles", e);
        } finally {
            c.close();
        }

        return accId;
    }

    /**
     * Returns true if stack was successfully created.
     *
     * @return true if stack is created.
     */
    public boolean isCreated() {
        return created;
    }

    /**
     * Executes given job on service executor - all PJSIP calls from IU and managers should go
     * through this executor - serialization.
     *
     * @param runnable
     */
    public void executeOnServiceHandler(SvcRunnable runnable){
        service.getHandler().execute(runnable);
    }

    /**
     * Tries to load PjSip stack.
     *
     * @return true if stack was loaded successfully.
     */
    public boolean loadStack() {
        if (stackLoaded) {
            return true;
        }

        if (!sipStackIsCorrupted) {
            try {
                LibraryLoader.loadLibraries(service.getBaseContext());

                stackLoaded = true;
                Log.v(TAG, "Native libs just loaded");

                return true;
            } catch (UnsatisfiedLinkError e) {
                // If it fails we probably are running on a special hardware
                Log.e(TAG, "Link error with the stack libraries", e);
                stackLoaded = false;
                sipStackIsCorrupted = true;

                service.notifyUserOfMessage("Can't load library. Unknown CPU architecture.");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "General exception in stack loading", e);
            }
        } else {
            Log.e(TAG, "SIP stack is corrupted, skipping loading.");
        }

        return false;
    }

    /**
     * Start the sip stack Thread safing of this method must be ensured by upper
     * layer Every calls from pjsip that require start/stop/getInfos from the
     * underlying stack must be done on the same thread
     */
    public boolean startStack() throws SameThreadException {
        Log.setLogLevel(PhonexSettings.debuggingRelease() ? prefs.getLogLevel() : 0);

        if (!stackLoaded) {
            Log.e(TAG, "We have no sip stack, we can't start");
            return false;
        }

//        if (created || needRestart) {
        if (created ) {
            Log.d(TAG, "Stack already started");
            return false;
        }

        // Ensure the stack is not already created or is being created
        Log.d(TAG, "<pjcreate>");
        AndroidTimers.create(service);
        int status = Xvi.create();
        Log.df(TAG, "</pjcreate status=%s>", status);

        if (pjCallback == null) {
            Log.d(TAG, "Init PJSIP callback.");
            pjCallback = new PjCallback();
            pjCallback.initService(this);
        }

        pjCallback.reconfigure(service);
        if (zrtpCallback == null) {
            Log.d(TAG, "Init ZRTP callback.");
            zrtpCallback = new ZrtpCallbackImpl(this);
        }

        if (mediaManager == null) {
            mediaManager = new MediaManager(service);
        }
        mediaManager.startService();

        initPlugins();
        Xvi.setCallbackObject(pjCallback);
        Xvi.setZrtpCallbackObject(zrtpCallback);

        // Initialize configuration from preferences.
        PjConfig pjCfg = PjConfig.initConfiguration(service, prefs);

        // INITIALIZE
        status = Xvi.sipstack_init(pjCfg.getUa(), pjCfg.getLog(), pjCfg.getMedia(), pjCfg.getPj(), service);
        if (status != XviConstants.PJ_SUCCESS) {
            String msg = "Cannot initialize SIP stack: " + PjUtils.pjStrToString(Xvi.get_error_message(status));
            Log.e(TAG, msg);
            service.notifyUserOfMessage(msg);
            cleanDestroy();
            return false;
        }

        // Load dynamically linked ZRTP.
        loadZrtpLibrary();

        // Add transports
        boolean transportOk = initTransport();
        if (!transportOk) {
            return false;
        }

        // Add pjsip modules
        for (IPjPlugin mod : pjModules.values()) {
            mod.onBeforeStartPjsip();
        }

        // Initialization is done, now start pjsua
        status = Xvi.start();
        if (status != Xvi.PJ_SUCCESS) {
            String msg = "Cannot start SIP stack: " + PjUtils.pjStrToString(Xvi.get_error_message(status));
            Log.e(TAG, msg);
            service.notifyUserOfMessage(msg);
            cleanDestroy();
            return false;
        }

        // Init media codecs
        initCodecs();
        setCodecsPriorities();

        // More entropy
        addEntropy();

        created = true;
        Log.v(TAG, "Stack start finished");

        // Post executor to check thread validity.
        final boolean treg = pjsuaIsThreadRegistered();
        Log.vf(TAG, "PJ Thread registered=%s, PID=%s, TID=%s",
                treg, android.os.Process.myPid(), android.os.Process.myTid());

        // Check stack!
        checkStack();

        return true;
    }

    /**
     * Initializes local transports and local accounts.
     *
     * @return
     * @throws SameThreadException
     */
    private boolean initTransport() throws SameThreadException {
        // We need a local account for each transport  to not have the
        // application lost when direct call to the IP.

        // UDP
        if (prefs.isUDPEnabled()) {
            int udpPort = prefs.getUDPTransportPort();
            localUdpAccPjId = createLocalTransportAndAccount(
                    pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                    udpPort);

            if (localUdpAccPjId == null) {
                cleanDestroy();
                return false;
            }

            // UDP v6
            if (prefs.useIPv6()) {
                localUdp6AccPjId = createLocalTransportAndAccount(
                        pjsip_transport_type_e.PJSIP_TRANSPORT_UDP6,
                        udpPort == 0 ? udpPort : udpPort + 10);
            }
        }

        // TCP
        if (prefs.isTCPEnabled()) {
            int tcpPort = prefs.getTCPTransportPort();
            localTcpAccPjId = createLocalTransportAndAccount(
                    pjsip_transport_type_e.PJSIP_TRANSPORT_TCP,
                    tcpPort);

            if (localTcpAccPjId == null) {
                cleanDestroy();
                return false;
            }

            // TCP v6
            if (prefs.useIPv6()) {
                localTcp6AccPjId = createLocalTransportAndAccount(
                        pjsip_transport_type_e.PJSIP_TRANSPORT_TCP6,
                        tcpPort == 0 ? tcpPort : tcpPort + 10);
            }
        }

        // TLS
        if (prefs.isTLSEnabled()) {
            int tlsPort = prefs.getTLSTransportPort();
            localTlsAccPjId = createLocalTransportAndAccount(
                    pjsip_transport_type_e.PJSIP_TRANSPORT_TLS,
                    tlsPort);

            if (localTlsAccPjId == null) {
                cleanDestroy();
                return false;
            }

            // TLS v6
            if (prefs.useIPv6()) {
                localTls6AccPjId = createLocalTransportAndAccount(
                        pjsip_transport_type_e.PJSIP_TRANSPORT_TLS6,
                        tlsPort == 0 ? tlsPort : tlsPort + 10);
            }
        }

        return true;
    }

    /**
     * Loading ZRTP library dynamically.
     */
    private void loadZrtpLibrary() {
        Log.vf(TAG, "Going to load ZRTP library");
        File zrtpLib = JNILibsManager.getJNILibFile(service, JNILibsManager.ZRTP_GLUE_LIB);
        File zrtpcppLib = JNILibsManager.getJNILibFile(service, JNILibsManager.ZRTP_CORE_LIB);
        int zrtpLoadStatus = Xvi.jzrtp_zrtp_loadlib(
                Xvi.pj_str_copy(zrtpLib.getAbsolutePath()),
                Xvi.pj_str_copy(zrtpcppLib.getAbsolutePath()));
        Log.vf(TAG, "ZRTP load status=0x%x", zrtpLoadStatus);
    }

    /**
     * Add more randomness to ZRTP random pool
     * In time of writing this comment it uses OpenSSL
     * PRNG that should be seeded from /dev/urandom on
     * UNIX machines and also on Android.
     * But just to be sure, add more entropy to the
     * pool by using Java SecureRandom.
     */
    private void addEntropy() {
        PRNGFixes.apply();
        SecureRandom secRand = new SecureRandom();
        byte[] seed4pjsua = new byte[64];
        secRand.nextBytes(seed4pjsua);
        Xvi.jzrtp_addEntropy(seed4pjsua);
    }

    /**
     * Check stack for the validity.
     * If not valid, restarts whole application.
     */
    private void checkStack() {
        // Register executor thread if needed.
        service.getHandler().execute(new SvcRunnable("stackCheck") {
            @Override
            protected void doRun() throws SameThreadException {
                checkStackProcedure();
            }
        });
    }

    /**
     * Body of the stack check procedure.
     */
    private void checkStackProcedure() {
        // Post executor to check thread validity.
        final boolean treg = pjsuaIsThreadRegistered();
        Log.vf(TAG, "HandlerThread registered=%s, PID=%s, TID=%s",
                treg, android.os.Process.myPid(), android.os.Process.myTid());

        // If thread is not registered, something
        if (!treg) {
            Log.i(TAG, "Thread not registered");
            Log.e(TAG, "Has to kill app.");
            prepareForRestart();
            pjsuaThreadRegister();

            ProcKiller.killPhoneX(service, true, true, true, true, true);
        }
    }

    /**
     * Prepares PJSTACK for restart by ProcKiller.
     */
    public void prepareForRestart() {
        Log.vf(TAG, "prepareForRestart");
        created = false;
//        needRestart = true;
        
        // Block timer invocation & register current thread.
        AndroidTimers.setShuttingDown(true);
    }

    /**
     * Stop sip service
     *
     * @return true if stop has been performed
     */
    public boolean stopStack() throws SameThreadException {
        Log.d(TAG, "Stop stack");

        if (!forceRestart && getActiveCallInProgress() != null) {
            Log.e(TAG, "Active call in progress, not stopping.");
            // TODO : queue quit on end call;
            return false;
        }

        if (service.getNotificationManager() != null) {
            Log.d(TAG, "Cancel registration in stopStack");
            service.getNotificationManager().cancelRegisters();
        }

        // Notify modules about shutdown (to unregister their resources)
        for (IPjPlugin mod : pjModules.values()) {
            mod.onBeforeStopPjsip();
        }

        if (created) {
            cleanDestroy();
        }

        if (taskTimer != null) {
            taskTimer.cancel();
            taskTimer.purge();
            taskTimer = null;
        }

        forceRestart = false;
        return true;
    }

    /**
     * Clean destroy of the sip stack.
     *
     * @throws SameThreadException
     */
    private void cleanDestroy() throws SameThreadException {
        if (!created) {
            Log.w(TAG, "Nothing to destroy");
            return;
        }

        Log.d(TAG, "Shutting down SIP stack");

        // This will destroy all accounts so synchronize with accounts
        // management lock
        // long flags = 1; /*< Lazy disconnect : only RX */
        // Try with TX & RX if network is considered as available
        long flags = 0;
        if (!prefs.isValidConnectionForOutgoingRaw()) {
            // If we are current not valid for outgoing,
            // it means that we don't want the network for SIP now
            // so don't use RX | TX to not consume data at all
            Log.i(TAG, "Flag=3");
            flags = 3;
        }

        // Check stack
        // TODO: check stack, synchronous operation, has to wait for finish.
        //checkStack();
//        if (!created || needRestart) {
        if (!created ) {
//            Log.i(TAG, "Cannot unload, is not created or needs restart");
            Log.i(TAG, "Cannot unload, PjStack is not created");
            return;
        }

        // Unload ZRTP
        Log.vf(TAG, "Unloading ZRTP library");
        Xvi.jzrtp_zrtp_unloadlib();

        Log.v(TAG, "<stack_destroy>");
        Xvi.sipstack_destroy(flags);
        Log.v(TAG, "</stack_destroy>");

        service.getContentResolver().delete(SipProfile.ACCOUNT_STATUS_URI, null, null);
        if (pjCallback != null) {
            pjCallback.stopService();
            pjCallback = null;
        }

        if (mediaManager != null) {
            mediaManager.stopService();
            mediaManager = null;
        }

        AndroidTimers.destroy();
        created = false;
    }

    /**
     * Utility to create a transport
     *
     * @return transport id or -1 if failed
     */
    private Integer createTransport(pjsip_transport_type_e type, int port) throws SameThreadException {
        pjsua_transport_config cfg = new pjsua_transport_config();
        int[] tId = new int[1];
        int status;
        Xvi.transport_config_default(cfg);
        cfg.setPort(port);

        if (type.equals(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS)) {
            pjsip_tls_setting tlsSetting = cfg.getTls_setting();

            String caListFile = prefs.getString(PhonexConfig.CA_LIST_FILE);
            if (!TextUtils.isEmpty(caListFile)) {
                tlsSetting.setCa_list_file(Xvi.pj_str_copy(caListFile));
            }

            String certFile = prefs.getString(PhonexConfig.CERT_FILE);
            if (!TextUtils.isEmpty(certFile)) {
                tlsSetting.setCert_file(Xvi.pj_str_copy(certFile));
            }

            String privKey = prefs.getString(PhonexConfig.PRIVKEY_FILE);
            if (!TextUtils.isEmpty(privKey)) {
                tlsSetting.setPrivkey_file(Xvi.pj_str_copy(privKey));
            }

            String tlsPwd = MemoryPrefManager.getPreferenceStringValue(service, MemoryPrefManager.CRED_PEM_PASS, null);
            String tlsUsr = prefs.getString(PhonexConfig.TLS_USER);
            if (tlsPwd != null && !TextUtils.isEmpty(tlsPwd)) {
                Log.d(TAG, "Setting TLS password from settings");

                tlsSetting.setPassword(Xvi.pj_str_copy(tlsPwd));
                Log.d(TAG, "TLS password length from SafeNet.");
            } else if (service == null) {
                Log.i(TAG, "Service is null, cannot read TLS password");
            } else {
                // No password in shared variable (less secure), try to load directly from service
                // attribute - added on login.

                String storagePass = service.getStoragePass();
                if (storagePass != null && !TextUtils.isEmpty(storagePass)) {

                    Log.d(TAG, "Setting TLS password from storage password from service. ");
                    try {
                        final String pemStoragePass2 = PemPasswordSchemeV2.getStoragePass(this.service, tlsUsr, storagePass, true);
                        tlsSetting.setPassword(Xvi.pj_str_copy(pemStoragePass2));
                    } catch (Exception e) {
                        Log.e(TAG, "Error in generating digest for TLS password", e);
                    }
                } else {
                    Log.wf(TAG, "Cannot acquire TLS password, starting TLS connection may be problematic if privkey is encrypted; IsNull=%s",
                            ((storagePass) == (null)));
                }
            }

            boolean checkClient = prefs.getBoolean(PhonexConfig.TLS_VERIFY_CLIENT);
            tlsSetting.setVerify_client(checkClient ? 1 : 0);

//            tlsSetting.setMethod(prefs.getTLSMethod());
            // hardcoded TLSV1_1
            tlsSetting.setMethod(pjsip_ssl_method.PJSIP_TLSV1_METHOD);
            boolean checkServer = prefs.getBoolean(PhonexConfig.TLS_VERIFY_SERVER);
            tlsSetting.setVerify_server(checkServer ? 1 : 0);

            // Set only secure cipher suites
            PjConfig.setSSlCipherSuites(tlsSetting);
            cfg.setTls_setting(tlsSetting); //tlsSetting.setCiphers(value)
        } else {
            Log.df(TAG, "Non-TLS transport detected, type=%s", type);
        }

        if (prefs.getBoolean(PhonexConfig.ENABLE_QOS)) {
            Log.d(TAG, "QOS enabled");
            pj_qos_params qosParam = cfg.getQos_params();
            qosParam.setDscp_val((short) prefs.getInteger(PhonexConfig.DSCP_VAL));
            qosParam.setFlags((short) 1); // DSCP
            cfg.setQos_params(qosParam);
        }

        status = Xvi.transport_create(type, cfg, tId);
        if (status != XviConstants.PJ_SUCCESS) {
            String errorMsg = PjUtils.pjStrToString(Xvi.get_error_message(status));
            String msg = "Error in creating a transport " + errorMsg + " (" + status + ")";
            Log.e(TAG, msg);
            if (status == 120098) { /* Already binded */
                msg = service.getString(R.string.another_application_use_sip_port);
                Log.e(TAG, "Another application uses SIP port.");
            }

            Log.ef(TAG, "Cannot create transport, type=%s", type);
            service.notifyUserOfMessage(msg);
            return null;
        }
        return tId[0];
    }

    private Integer createLocalAccount(Integer transportId) throws SameThreadException {
        if (transportId == null) {
            return null;
        }

        int[] p_acc_id = new int[1];
        Xvi.acc_add_local(transportId, Xvi.PJ_FALSE, p_acc_id);
        return p_acc_id[0];
    }

    private Integer createLocalTransportAndAccount(pjsip_transport_type_e type, int port) throws SameThreadException {
        Integer transportId = createTransport(type, port);
        return createLocalAccount(transportId);
    }

    public boolean addAccount(SipProfile profile) throws SameThreadException {
        int status = XviConstants.PJ_FALSE;
        if (!created) {
            Log.e(TAG, "PJSIP is not started here, nothing can be done");
            return status == XviConstants.PJ_SUCCESS;

        }
        PjAccount account = new PjAccount(profile);
        account.applyExtraParams(service);

        // Force the use of a transport
        /*
         * switch (account.transport) { case SipProfile.TRANSPORT_UDP: if
         * (udpTranportId != null) {
         * //account.cfg.setTransport_id(udpTranportId); } break; case
         * SipProfile.TRANSPORT_TCP: if (tcpTranportId != null) { //
         * account.cfg.setTransport_id(tcpTranportId); } break; case
         * SipProfile.TRANSPORT_TLS: if (tlsTransportId != null) { //
         * account.cfg.setTransport_id(tlsTransportId); } break; default: break;
         * }
         */

        SipProfileState currentAccountStatus = getProfileState(profile);
        account.cfg.setRegister_on_acc_add(XviConstants.PJ_FALSE);

        if (currentAccountStatus.isAddedToStack()) {
            Xvi.sipstack_set_acc_user_data(account.cfg, account.css_cfg);
            status = Xvi.acc_modify(currentAccountStatus.getPjsuaId(), account.cfg);
            beforeAccountRegistration(currentAccountStatus.getPjsuaId(), profile);
            ContentValues cv = new ContentValues();
            cv.put(SipProfileState.ADDED_STATUS, status);
            service.getContentResolver().update(
                    ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, profile.getId()),
                    cv, null, null);

            // Re register
            if (status == XviConstants.PJ_SUCCESS) {
                status = Xvi.acc_set_registration(currentAccountStatus.getPjsuaId(), 1);
            }
        } else {
            int[] accId = new int[1];
            Xvi.sipstack_set_acc_user_data(account.cfg, account.css_cfg);
            status = Xvi.acc_add(account.cfg, XviConstants.PJ_FALSE, accId);
            beforeAccountRegistration(accId[0], profile);
            Xvi.acc_set_registration(accId[0], 1);

            if (status == XviConstants.PJ_SUCCESS) {
                SipProfileState ps = new SipProfileState(profile);
                ps.setAddedStatus(status);
                // default status is ONLINE
                ps.setStatusType(PushNotifications.PresencePush.Status.ONLINE_VALUE);
                ps.setPjsuaId(accId[0]);
                service.getContentResolver().insert(
                        ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE,
                                account.id), ps.getAsContentValue()
                );
            }
        }

        return status == XviConstants.PJ_SUCCESS;
    }

    private void beforeAccountRegistration(int pjId, SipProfile profile) {
        for (IPjPlugin mod : pjModules.values()) {
            mod.onBeforeAccountStartRegistration(pjId, profile);
        }
    }

    /**
     * Synchronize content provider backend from pjsip stack
     *
     * @param pjsuaId the pjsua id of the account to synchronize
     * @throws SameThreadException
     */
    public void updateProfileStateFromService(int pjsuaId) throws SameThreadException {
        if (!created) {
            return;
        }

        long accId = getAccountIdForPjsipId(service, pjsuaId);
        Log.df(TAG, "Update profile from service for %s aka in db %s, TID=%s", pjsuaId, accId, android.os.Process.myTid());
        if (accId == SipProfile.INVALID_ID) {
            Log.ef(TAG, "Trying to update not added account %s", pjsuaId);
            return;
        }

        int success;
        pjsua_acc_info pjAccountInfo;
        pjAccountInfo = new pjsua_acc_info();
        success = Xvi.acc_get_info(pjsuaId, pjAccountInfo);
        if (success == XviConstants.PJ_SUCCESS && pjAccountInfo != null) {
            ContentValues cv = new ContentValues();
            int statusCode = 0;

            try {
                // Should be fine : status code are coherent with RFC
                // status codes
                statusCode = pjAccountInfo.getStatus().swigValue();
                cv.put(SipProfileState.STATUS_CODE, statusCode);
            } catch (IllegalArgumentException e) {
                cv.put(SipProfileState.STATUS_CODE,
                        SipStatusCode.INTERNAL_SERVER_ERROR);
            }

            cv.put(SipProfileState.STATUS_TEXT, PjUtils.pjStrToString(pjAccountInfo.getStatus_text()));
            cv.put(SipProfileState.EXPIRES, pjAccountInfo.getExpires());

            if (statusCode == 503) {
                Log.d(TAG, "Status code for network error, no update");
                return;
            }

            service.getContentResolver().update(
                    ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, accId),
                    cv, null, null);

            Log.df(TAG, "Profile state UP: %s", cv);
        }
    }

    /**
     * Get the dynamic state of the profile
     *
     * @param account the sip profile from database. Important field is id.
     * @return the dynamic sip profile state
     */
    public SipProfileState getProfileState(SipProfile account) {
        if (!created || account == null) {
            return null;
        }
        if (account.getId() == SipProfile.INVALID_ID) {
            return null;
        }
        SipProfileState accountInfo = new SipProfileState(account);
        accountInfo = SipProfileState.getById(service.getApplicationContext(), accountInfo, account.getId());
        return accountInfo;
    }

    /**
     * Retrieve codecs from pjsip stack and store it inside preference storage
     * so that it can be retrieved in the interface view
     *
     * @throws SameThreadException
     */
    private void initCodecs() throws SameThreadException {
        synchronized (codecs) {
            if (!codecsLoaded) {
                int nbrCodecs, i;

                // Audio codecs
                nbrCodecs = Xvi.codecs_get_nbr();
                for (i = 0; i < nbrCodecs; i++) {
                    String codecId = PjUtils.pjStrToString(Xvi.codecs_get_id(i));
                    codecs.add(codecId);
                    Log.df(TAG, "Found audio codec [%s]", codecId);
                }
                // Set it in prefs if not already set correctly
                prefs.setCodecList(codecs);

                codecsLoaded = true;
            }
        }
    }

    /**
     * Append log for the codec in String builder
     *
     * @param sb    the buffer to be appended with the codec info
     * @param codec the codec name
     * @param prio  the priority of the codec
     */
    private void buffCodecLog(StringBuilder sb, String codec, short prio) {
        if (prio > 0 && Log.getLogLevel() >= 4 && PhonexSettings.debuggingRelease()) {
            sb.append(codec);
            sb.append(" (");
            sb.append(prio);
            sb.append(") - ");
        }
    }

    /**
     * Set the codec priority in pjsip stack layer based on preference store
     *
     * @throws SameThreadException
     */
    private void setCodecsPriorities() throws SameThreadException {
        ConnectivityManager cm = ((ConnectivityManager) service
                .getSystemService(Context.CONNECTIVITY_SERVICE));

        synchronized (codecs) {
            if (codecsLoaded) {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null) {

                    StringBuilder audioSb = new StringBuilder();
                    StringBuilder videoSb = new StringBuilder();
                    audioSb.append("Audio codecs: ");
                    videoSb.append("Video codecs: ");

                    String currentBandType = prefs.getString(
                            PhonexConfig.getBandTypeKey(ni.getType(), ni.getSubtype()),
                            PhonexConfig.CODEC_WB);

                    synchronized (codecs) {

                        for (String codec : codecs) {
                            short aPrio = prefs.getCodecPriority(codec, currentBandType, "-1");
                            buffCodecLog(audioSb, codec, aPrio);
                            pj_str_t codecStr = Xvi.pj_str_copy(codec);
                            if (aPrio >= 0) {
                                Xvi.codec_set_priority(codecStr, aPrio);
                            }

                            String codecKey = PhonexConfig.getCodecKey(codec,
                                    PhonexConfig.FRAMES_PER_PACKET_SUFFIX);
                            Integer frmPerPacket = PhonexConfig.getIntegerPref(
                                    service, codecKey);
                            if (frmPerPacket != null && frmPerPacket > 0) {
                                Log.vf(TAG, "Set codec [%s] frames/sec: %s", codec, frmPerPacket);
                                Xvi.codec_set_frames_per_packet(codecStr, frmPerPacket);
                            }
                        }
                    }

                    Log.d(TAG, audioSb.toString());
                    Log.d(TAG, videoSb.toString());
                }

            }
        }
    }

    /**
     * Answer a call
     *
     * @param callId the id of the call to answer to
     * @param code   the status code to send in the response
     * @return
     */
    public int callAnswer(int callId, int code) throws SameThreadException {
        if (!created) {
            Log.wf(TAG, "Not initialized, cannot answer");
            return -1;
        }

        Log.df(TAG, "Going to answer call %s, code %s", callId, code);

        // Stop ringing if answering the call with OK status.
        if (code == SipStatusCode.OK && mediaManager != null){
            mediaManager.stopRing();
        }

        pjsua_call_setting cs = new pjsua_call_setting();
        Xvi.call_setting_default(cs);
        cs.setAud_cnt(1);
        cs.setVid_cnt(prefs.getBoolean(PhonexConfig.USE_VIDEO) ? 1 : 0);
        cs.setFlag(0);
        return Xvi.call_answer2(callId, cs, code, null, null);
    }

    /**
     * Hangup a call
     *
     * @param callId the id of the call to hangup
     * @param code   the status code to send in the response
     * @return
     */
    public int callHangup(int callId, int code) throws SameThreadException {
        SipCallSession sipCallSession = getPjCallback().getActiveCallInProgress();
        if (sipCallSession == null) {
            Log.wf(TAG, "Trying to hang up but there is no active call");
            return -1;
        }
        return callHangup(callId, code, sipCallSession.getConfPort());
    }

    public int callHangup(int callId, int code, int confPort) throws SameThreadException {
        // Only for testing service crashes...
        if (PhonexSettings.debuggingRelease()) {
            if (callId == -1 && code == -1) pjsuaAssert();
        }

        if (!created) {
            return -1;
        }

        Log.df(TAG, "Going to call hangup on call %s, code: %s, port: %s", callId, code, confPort);

        // Get current call state.
        SipCallSession ci = getCallInfo(callId);
        if (ci != null && ci.getConfPort() >= 0) {
            // End call disconnects audio, if hangup takes too long, user assumes no
            // sound is not connected after hangup was called.
            this.disconnectSound(callId, ci.getConfPort());
        }

        // SipState: bye status.
        if (ci != null){
            ci.setLocalByeCode(code);
        }

        return Xvi.call_hangup_ex(callId, code, null);
    }

    /**
     * Terminate a call
     *
     * @param callId the id of the call to hangup
     * @param code   the status code to send in the response
     * @return
     */
    public int callTerminate(int callId, int code) throws SameThreadException {
        if (!created) {
            return -1;
        }

        Log.w(TAG, "callTerminate has been called");
        return Xvi.call_terminate(callId, code);
    }

    public int callXfer(int callId, String callee) throws SameThreadException {
        if (!created) {
            return -1;
        }

        return Xvi.call_xfer(callId, Xvi.pj_str_copy(callee), null);
    }

    public int callXferReplace(int callId, int otherCallId, int options) throws SameThreadException {
        if (!created) {
            return -1;
        }

        return Xvi.call_xfer_replaces(callId, otherCallId, options, null);
    }

    /**
     * Make a call
     *
     * @param callee remote contact ot call If not well formated we try to add
     *               domain name of the default account
     */
    public MakeCallResult makeCall(String callee, int accountId, Bundle b) throws SameThreadException {
        if (!created) {
            return MakeCallResult.create(-1);
        }

        if (Compatibility.isBlackBerry() && !AudioUtils.findAudioRecord()){
            Log.ef(TAG, "Could not make a call to %s, no usable audio recording route", callee);
            return MakeCallResult.create(-1);
        }

        final ToCall toCall = sanitizeSipUri(callee, accountId);
        if (toCall == null) {
            service.notifyUserOfMessage(service.getString(R.string.invalid_sip_uri) + ": " + callee);
            return MakeCallResult.create(-1);
        }

        final SipCallSession activeCall = getActiveCallInProgress();
        if (activeCall == null && getMediaManager() != null){
            getMediaManager().resetSettings();
        }

        pj_str_t uri = Xvi.pj_str_copy(toCall.getCallee());

        // Nothing to do with this values
        byte[] userData = new byte[1];
        int[] callId = new int[1];
        callId[0] = -1; // invalid value
        pjsua_call_setting cs = new pjsua_call_setting();
        pjsua_msg_data msgData = new pjsua_msg_data();
        int pjsuaAccId = toCall.getPjsipAccountId();

        // Call settings to add video
        Xvi.call_setting_default(cs);
        cs.setAud_cnt(1);
        cs.setVid_cnt(0);
        if (b != null && b.getBoolean(SipCallSession.OPT_CALL_VIDEO, false)) {
            cs.setVid_cnt(1);
        }
        cs.setFlag(0);

        pj_pool_t pool = Xvi.pool_create("call_pool", 512, 512);

        // Msg data to add headers
        Xvi.msg_data_init(msgData);
        Xvi.sipstack_init_acc_msg_data(pool, pjsuaAccId, msgData);
        if (b != null) {
            Bundle extraHeaders = b.getBundle(SipCallSession.OPT_CALL_EXTRA_HEADERS);
            PjUtils.addExtraHeaders(msgData, pool, extraHeaders);
        }

        int status = Xvi.call_make_call(pjsuaAccId, uri, cs, userData, msgData, callId);
        Log.inf(TAG, "makeCall; callId after [%d]", callId[0]);

        if (status != XviConstants.PJ_SUCCESS) {
            Log.ef(TAG, "Cannot make a new call, result code=%s", status);
        } else {
            // Refresh call state information.
            pjCallback.updateCallInfoFromStack(callId[0], null, PjCalls.CALL_UPDATE_MAKE_CALL);
        }

        Xvi.pj_pool_release(pool);
        return MakeCallResult.create(status, callId[0]);
    }

    public int updateCallOptions(int callId, Bundle options) {
        // TODO : if more options we should redesign this part.
        if (options.containsKey(SipCallSession.OPT_CALL_VIDEO)) {
            boolean add = options.getBoolean(SipCallSession.OPT_CALL_VIDEO);
            SipCallSession ci = getCallInfo(callId);
            if (add && ci.mediaHasVideo()) {
                // We already have one video running -- refuse to send another
                return -1;
            } else if (!add && !ci.mediaHasVideo()) {
                // We have no current video, no way to remove.
                return -1;
            }
            pjsua_call_vid_strm_op op = add ? pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_ADD
                    : pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_REMOVE;
            if (!add) {
                // TODO : manage remove case
            }
            return Xvi.call_set_vid_strm(callId, op, null);
        }

        return -1;
    }

    /**
     * Send message using SIP server.
     */
    public ToCall sendMessage(String callee, String message, long accountId, String mime,
                              final SipMsgAux msgAux) throws SameThreadException {
        if (!created) {
            return null;
        }

        ToCall toCall = sanitizeSipUri(callee, accountId);
        if (toCall != null) {

            pj_str_t pjmime = mime == null ? null : Xvi.pj_str_copy(mime);
            pj_str_t uri = Xvi.pj_str_copy(toCall.getCallee());
            pj_str_t text = Xvi.pj_str_copy(message);
            pjsua_msg_data msgData = null;
            pj_pool_t pool = null;

            if (msgAux != null){
                pool = Xvi.pool_create("tmp_msg_pool", 64, 64);
                msgData = new pjsua_msg_data();

                Xvi.msg_data_init(msgData);

                Bundle b = new Bundle();
                b.putString("X-MsgType", String.format("%s;%s",
                        msgAux.getMsgType() == null ? -1 : msgAux.getMsgType(),
                        msgAux.getMsgSubType() == null ? -1 : msgAux.getMsgSubType()));
                PjUtils.addExtraHeaders(msgData, pool, b);
            }

            // Nothing to do with this values
            byte[] userData = new byte[1];

            int status = Xvi.im_send(toCall.getPjsipAccountId(), uri, pjmime, text, msgData, userData);

            // Release pool if was used.
            if (pool != null){
                Xvi.pj_pool_release(pool);
                pool = null;
            }

            return (status == XviConstants.PJ_SUCCESS) ? toCall : null;
        }
        return toCall;
    }

    public int callHold(int callId) throws SameThreadException {
        if (!created) {
            return -1;
        }

        return Xvi.call_set_hold(callId, null);
    }

    public int callReinvite(int callId, boolean unhold) throws SameThreadException {
        if (!created) {
            return -1;
        }

        return Xvi.call_reinvite(callId, unhold ? pjsua_call_flag.PJSUA_CALL_UNHOLD.swigValue() : 0, null);
    }

    public SipCallSessionInfo updateCallInfoFromStack(Integer callId, Integer updateCode) throws SameThreadException {
        if (created/* && !creating */ && pjCallback != null) {
            SipCallSessionInfo callInfo = pjCallback.updateCallInfoFromStack(callId, null, updateCode);
            return callInfo;
        }
        return null;
    }

    public SipCallSession getCallInfo(int callId) {
        if (created/* && !creating */ && pjCallback != null) {
            SipCallSession callInfo = pjCallback.getCallInfo(callId);
            return callInfo;
        }
        return null;
    }

    /**
     * Entry method called from the service interface from the GUI.
     * User want to switch bluetooth to particular state.
     *
     * @param on
     * @throws SameThreadException
     */
    public void setBluetoothOn(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setBluetoothOn(on);
        }
    }

    /**
     * Mute microphone
     *
     * @param on true if microphone has to be muted
     * @throws SameThreadException
     */
    public void setMicrophoneMute(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setMicrophoneMute(on);
        }
    }

    /**
     * Change speaker phone mode
     *
     * @param on true if the speaker mode has to be on.
     * @throws SameThreadException
     */
    public void setSpeakerphoneOn(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setSpeakerphoneOn(on);
        }
    }

    public SipCallSession[] getCalls() {
        if (created && pjCallback != null) {
            SipCallSession[] callsInfo = pjCallback.getCalls();
            return callsInfo;
        }
        return new SipCallSession[0];
    }

    public void confAdjustTxLevel(int port, float value) throws SameThreadException {
        if (created && pjCallback != null) {
            Xvi.conf_adjust_tx_level(port, value); //monitor...
        }
    }

    public void confAdjustRxLevel(int port, float value) throws SameThreadException {
        if (created && pjCallback != null) {
            Xvi.conf_adjust_rx_level(port, value);
        }
    }

    public void setEchoCancellation(boolean on) throws SameThreadException {
        if (created && pjCallback != null) {
            Log.df(TAG, "echo cancelation: %s", on);
            Xvi.set_ec(on ? prefs.getEchoCancellationTail() : 0, prefs.getInteger(PhonexConfig.ECHO_CANCELLATION_MODE));
        }
    }

    public void adjustStreamVolume(int stream, int direction, int flags) {
        if (mediaManager != null) {
            mediaManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI);
        }
    }

    public void silenceRinger() {
        if (mediaManager != null) {
            mediaManager.stopRingAndUnfocus();
        }
    }

    /**
     * Change account registration / adding state
     *
     * @param account    The account to modify registration
     * @param renew      if 0 we ask for deletion of this account; if 1 we ask for
     *                   registration of this account (and add if necessary)
     * @param forceReAdd if true, we will first remove the account and then
     *                   re-add it
     * @return true if the operation get completed without problem
     * @throws SameThreadException
     */
    public boolean setAccountRegistration(SipProfile account, int renew, boolean forceReAdd) throws SameThreadException {
        Log.df(TAG, "setAccountRegistration; renew [%d], forceReAdd [%s], account [%s]", renew, forceReAdd, account);
        int status = -1;

        if (!sipProfileValidityCheck(account)){
            return false;
        }

        SipProfileState profileState = getProfileState(account);

        // In case of already added, we have to act finely
        // If it's local we can just consider that we have to re-add account
        // since it will actually just touch the account with a modify
        if (profileState != null && profileState.isAddedToStack()) {
            // The account is already there in accounts list
            service.getContentResolver().delete(
                    ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_URI, account.getId()), null,
                    null);
            Log.d(TAG, "Account already added to stack, remove and re-load or delete");
            if (renew == 1) {
                if (forceReAdd) {
                    status = Xvi.acc_del(profileState.getPjsuaId());
                    addAccount(account);
                } else {
//                    if (PhonexSettings.enabledSipPresence()) {
//                        Xvi.acc_set_online_status(profileState.getPjsuaId(), getOnlineForStatus(service.getPresence()));
//                    }
                    status = Xvi.acc_set_registration(profileState.getPjsuaId(), renew);
                }
            } else {
                // if(status == pjsuaConstants.PJ_SUCCESS && renew == 0) {
                Log.d(TAG, "Delete account!");
                status = Xvi.acc_del(profileState.getPjsuaId());
            }
        } else {
            if (renew == 1) {
                addAccount(account);
            } else {
                Log.wf(TAG, "Ask to unregister an unexisting account. id=[%s] profileState==NULL :%s",
                        account.getId(),
                        ((profileState) == (null)));
            }

        }
        // PJ_SUCCESS = 0
        return status == 0;
    }

    // used for re-registration od unregistration
    // see http://trac.pjsip.org/repos/wiki/IPAddressChange
    public boolean setRawAccountRegistration(SipProfile account, boolean register){
        int renew = register ? 1 : 0 ;

        int status = -1;

        if (!sipProfileValidityCheck(account)){
            return false;
        }

        SipProfileState profileState = getProfileState(account);
        if (profileState != null && profileState.isAddedToStack()) {
            // if forcing account re-registration, reset attempts counter
            Xvi.acc_reset_reg_attempts(profileState.getPjsuaId());

            status = Xvi.acc_set_registration(profileState.getPjsuaId(), renew);
            Log.vf(TAG, "Re-registration called, status: %s", status);

        } else {

            Log.wf(TAG, "Ask to (un)register an unexisting account. id=[%s] profileState==NULL :%s", account.getId(), ((profileState) == (null)));
        }
        // PJ_SUCCESS = 0
        return status == 0;
    }

    private boolean sipProfileValidityCheck(SipProfile account){
        if (!created || account == null) {
            Log.e(TAG, "PJSIP is not started here, nothing can be done");
            return false;
        }
        if (account.getId() == SipProfile.INVALID_ID) {
            Log.w(TAG, "Cannot set registration, account ID is invalid");
            return false;
        }
        return true;
    }

    public SipProfile getAccountForPjsipId(int pjId) {
        long accId = getAccountIdForPjsipId(service, pjId);
        if (accId == SipProfile.INVALID_ID) {
            return null;
        } else {
            return service.getAccount(accId);
        }
    }

    public int validateAudioClockRate(int aClockRate) {
        if (mediaManager != null) {
            return mediaManager.validateAudioClockRate(aClockRate);
        }
        return -1;
    }

    public void setAudioInCall(int beforeInit) {
        if (mediaManager != null) {
            mediaManager.setAudioInCall(beforeInit == XviConstants.PJ_TRUE);
        }
    }

    public void unsetAudioInCall() {

        if (mediaManager != null) {
            mediaManager.unsetAudioInCall();
        }
    }

    public SipCallSession getActiveCallInProgress() {
        if (created && pjCallback != null) {
            return pjCallback.getActiveCallInProgress();
        }
        return null;
    }

    public void refreshCallMediaState(final int callId) {
        service.getHandler().execute(new SvcRunnable("updateCallMediaState") {
            @Override
            public void doRun() throws SameThreadException {
                if (created && pjCallback != null) {
                    pjCallback.updateCallMediaState(callId);
                }
            }
        });
    }

    /**
     * Transform a string callee into a valid sip uri in the context of an
     * account
     *
     * @param callee    the callee string to call
     * @param accountId the context account
     * @return ToCall object representing what to call and using which account
     */
    public ToCall sanitizeSipUri(String callee, long accountId) throws SameThreadException {
        // pjsipAccountId is the account id in term of pjsip adding
        int pjsipAccountId = (int) SipProfile.INVALID_ID;

        // Fake a sip profile empty to get it's profile state
        // Real get from db will be done later
        SipProfile account = new SipProfile();
        account.setId(accountId);
        SipProfileState profileState = getProfileState(account);
        long finalAccountId = accountId;

        // If this is an invalid account id
        if (accountId == SipProfile.INVALID_ID || !profileState.isAddedToStack()) {
            int defaultPjsipAccount = Xvi.acc_get_default();

            boolean valid = false;
            account = getAccountForPjsipId(defaultPjsipAccount);
            if (account != null) {
                profileState = getProfileState(account);
                valid = profileState.isAddedToStack();
            }
            // If default account is not active
            if (!valid) {
                Cursor c = service.getContentResolver().query(SipProfile.ACCOUNT_STATUS_URI, null, null, null, null);
                if (c != null) {
                    try {
                        for (; c.moveToNext(); ) {
                            SipProfileState ps = new SipProfileState(c);
                            if (ps.isValidForCall()) {
                                finalAccountId = ps.getAccountId();
                                pjsipAccountId = ps.getPjsuaId();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error on looping over sip profiles state", e);
                    } finally {
                        c.close();
                    }
                }
            } else {
                // Use the default account
                finalAccountId = profileState.getAccountId();
                pjsipAccountId = profileState.getPjsuaId();
            }
        } else {
            // If the account is valid
            pjsipAccountId = profileState.getPjsuaId();
        }

        if (pjsipAccountId == SipProfile.INVALID_ID) {
            Log.e(TAG, "Unable to find a valid account for this call");
            return null;
        }

        // Check integrity of callee field
        ParsedSipContactInfos finalCallee = SipUri.parseSipContact(callee);

        if (TextUtils.isEmpty(finalCallee.domain) ||
                TextUtils.isEmpty(finalCallee.scheme)) {
            Log.df(TAG, "default acc : %s", finalAccountId);
            account = service.getAccount((int) finalAccountId);
        }

        if (TextUtils.isEmpty(finalCallee.domain)) {
            String defaultDomain = account.getDefaultDomain();
            finalCallee.domain = defaultDomain;
        }
        if (TextUtils.isEmpty(finalCallee.scheme)) {
            if (!TextUtils.isEmpty(account.getDefault_uri_scheme())) {
                finalCallee.scheme = account.getDefault_uri_scheme();
            } else {
                finalCallee.scheme = Constants.PROTOCOL_SIPS;
            }
        }

        if (!TextUtils.isEmpty(finalCallee.userName) &&
                (finalCallee.userName.contains(",") || finalCallee.userName.contains(";"))) {
            int commaIndex = finalCallee.userName.indexOf(",");
            int semiColumnIndex = finalCallee.userName.indexOf(";");
            if (semiColumnIndex > 0 && semiColumnIndex < commaIndex) {
                commaIndex = semiColumnIndex;
            }
            finalCallee.userName = finalCallee.userName.substring(0, commaIndex);
        }

        Log.df(TAG, "callee [%s]", finalCallee);

        if (Xvi.verify_sip_url(finalCallee.toString(false)) == 0) {
            // In worse worse case, find back the account id for uri.. but
            // probably useless case
            if (pjsipAccountId == SipProfile.INVALID_ID) {
                pjsipAccountId = Xvi.acc_find_for_outgoing(Xvi.pj_str_copy(finalCallee
                        .toString(false)));
            }
            return new ToCall(pjsipAccountId, finalCallee.toString(false));
        }

        return null;
    }

    /**
     * Reacts on GSM call state change, dropping all phonex calls.
     * @param state
     * @param incomingNumber
     * @throws SameThreadException
     */
    public void onGSMStateChanged(int state, String incomingNumber) throws SameThreadException {
        // Avoid ringing if new GSM state is not idle
        if (state != TelephonyManager.CALL_STATE_IDLE && mediaManager != null) {
            mediaManager.stopRingAndUnfocus();
        }

        // If there is an active GSM call, terminate outgoing phonex call.
        if (state == TelephonyManager.CALL_STATE_OFFHOOK){
            final List<SipCallSession> calls = pjCallback.getActiveCallsOngoing();
            for(SipCallSession call : calls){
                Log.vf(TAG, "Terminating call %s due to GSM call", call.getCallId());
                callHangup(call.getCallId(), BYE_CAUSE_GSM_BUSY, call.getConfPort());
            }
        }
    }

    /**
     * Reacts on GSM call state change, with intent to put active phonex calls on-hold.
     * @deprecated as on-hold does not work properly yet.
     *
     * @param state
     * @param incomingNumber
     * @throws SameThreadException
     */
    public void onGSMStateChangedWithHold(int state, String incomingNumber) throws SameThreadException {
        // Avoid ringing if new GSM state is not idle
        if (state != TelephonyManager.CALL_STATE_IDLE && mediaManager != null) {
            mediaManager.stopRingAndUnfocus();
        }

        // If new call state is not idle
        if (state != TelephonyManager.CALL_STATE_IDLE && pjCallback != null) {
            SipCallSession currentActiveCall = pjCallback.getActiveCallOngoing();
            // If we have a sip call on our side
            if (currentActiveCall != null) {
                AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // GSM is now off hook => hold current sip call
                    hasBeenHoldByGSM = currentActiveCall.getCallId();
                    callHold(hasBeenHoldByGSM);
                    Xvi.set_no_snd_dev();

                    am.setMode(AudioManager.MODE_IN_CALL);
                } else {
                    // We have a ringing incoming call.
                    // Avoid ringing
                    hasBeenChangedRingerMode = am.getRingerMode();
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    // And try to notify with tone
                    if (mediaManager != null) {
                        mediaManager.playInCallTone(MediaManager.TONE_CALL_WAITING);
                    }
                }
            }
        } else {
            // GSM is now back to an IDLE state, resume previously stopped SIP
            // calls
            if (hasBeenHoldByGSM != null && isCreated()) {
                Xvi.set_snd_dev(0, 0);
                callReinvite(hasBeenHoldByGSM, true);
                hasBeenHoldByGSM = null;
            }

            // GSM is now back to an IDLE state, reset ringerMode if was
            // changed.
            if (hasBeenChangedRingerMode != null) {
                AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
                am.setRingerMode(hasBeenChangedRingerMode);
                hasBeenChangedRingerMode = null;
            }
        }
    }

    public void zrtpSASVerified(int callId) throws SameThreadException {
        if (!created) {
            return;
        }
        Xvi.jzrtp_SASVerified(callId);
    }

    public void zrtpSASRevoke(int callId) throws SameThreadException {
        if (!created) {
            return;
        }
        Xvi.jzrtp_SASRevoked(callId);
    }

    public void setNatType(String natName, int status) {
        curNatType = natName;
    }

    /**
     * @return nat type name detected by pjsip. Empty string if nothing detected
     */
    public String getNatType() {
        return curNatType;
    }

    public void setNoSnd() throws SameThreadException {
        if (!created) {
            return;
        }
        Xvi.set_no_snd_dev();
    }

    public void setSnd() throws SameThreadException {
        if (!created) {
            return;
        }
        Xvi.set_snd_dev(0, 0);
    }

    public void updateTransportIp(String oldIPAddress) throws SameThreadException {
        if (!created) {
            return;
        }
        Log.df(TAG, "Trying to update my address in the current call to %s", oldIPAddress);
        Xvi.update_transport(Xvi.pj_str_copy(oldIPAddress));
    }

    /**
     * Get the signal level
     *
     * @param port The pjsip port to get signal from
     * @return an encoded long with rx level on higher byte and tx level on lower byte
     */
    public long getRxTxLevel(int port) {
        long[] rx_level = new long[1];
        long[] tx_level = new long[1];
        Xvi.conf_get_signal_level(port, tx_level, rx_level);
        return (rx_level[0] << 8 | tx_level[0]);
    }

    /**
     * Connect mic source to speaker output.
     * Usefull for tests.
     */
    public void startLoopbackTest() {
        Xvi.conf_connect(0, 0);
    }

    /**
     * Stop connection between mic source to speaker output.
     */
    public void stopLoopbackTest() {
        Xvi.conf_disconnect(0, 0);
    }

    /**
     * Disconnects sound port from conference bridge.
     * @param callId
     * @param port
     */
    public boolean disconnectSound(int callId, int port){
        try{
            pjsua_conf_port_info pInfo = new pjsua_conf_port_info();
            if (port < 0){
                Log.wf(TAG, "Conference port is invalid: %d", port);
                return false;
            }

            int status = Xvi.conf_get_port_info(port, pInfo);
            if (status != Xvi.PJ_SUCCESS){
                Log.inf(TAG, "Cannot get port [%d] info, no disconnect", port);
                return false;
            }

            status = Xvi.conf_get_port_info(0, pInfo);
            if (status != Xvi.PJ_SUCCESS){
                Log.inf(TAG, "Cannot get port [0] info, no disconnect");
                return false;
            }

            Xvi.conf_disconnect(0, port);
            Xvi.conf_disconnect(port, 0);
            Log.vf(TAG, "Sound was disconnected");
            return true;

        } catch(Exception ex){
            Log.ef(TAG, ex, "Exception when disconnecting sound for callId=%d and port=%d", callId, port);
            return false;
        }
    }

    /**
     * Pj modules init
     */
    private void initPlugins() {
        IPjPlugin rModule = new RegListenerModule();
        pjModules.put(RegListenerModule.class.getCanonicalName(), rModule);

        signModule = new SignModule();
        signModule.setContext(service);
        signModule.getSigCB().setContext(service);
        signModule.getSigCB().initService(this);

        try {
            String storagePass = service.getStoragePass();
            SipProfile currentProfile = SipProfile.getCurrentProfile(service);
            if (!TextUtils.isEmpty(storagePass)) {
                signModule.initSignatures(CertificatesAndKeys.derivePkcs12Filename(currentProfile.getSip()), storagePass);
                Log.df(TAG, "Initialized signatures for ModSign; storagePass length: %s", storagePass.length());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during setting sign module", e);
        }

        rModule = signModule;
        pjModules.put(SignModule.class.getCanonicalName(), rModule);

        for (IPjPlugin mod : pjModules.values()) {
            mod.setContext(service);
        }
    }

    public SignModule getSignModule() {
        return signModule;
    }

    public boolean isForceRestart() {
        return forceRestart;
    }

    public void setForceRestart(boolean forceRestart) {
        this.forceRestart = forceRestart;
    }

    public XService getService() {
        return service;
    }

    /**
     * Sets current service, context.
     *
     * @param aService
     */
    public void setService(XService aService) {
        service = aService;
        prefs = service.getPrefs();
    }

    public MediaManager getMediaManager() {
        return mediaManager;
    }

    public ZrtpCallbackImpl getZrtpCallback() {
        return zrtpCallback;
    }

    public PjCallback getPjCallback() {
        return pjCallback;
    }

    public void pjsuaKill() {
        Xvi.pjsua_kill();
    }

    public void pjsuaAssert() {
        Xvi.pjsua_assert();
    }

    /**
     * Returns true if calling thread is registered in pjsip.
     *
     * @return
     */
    public boolean pjsuaIsThreadRegistered() {
        return Xvi.pjsua_is_thread_registered() > 0;
    }

    public void pjsuaThreadRegister(int procId, int threadId) {
        Xvi.register_thread(procId, threadId);
    }

    public void pjsuaThreadRegister() {
        Xvi.register_thread(android.os.Process.myPid(), android.os.Process.myTid());
    }

    /**
     * Provide video render surface to native code.
     *
     * @param callId The call id for this video surface
     * @param window The video surface object
     */
    public void setVideoAndroidRenderer(int callId, SurfaceView window) {
        Xvi.vid_set_android_renderer(callId, (Object) window);
    }

    /**
     * Provide video capturer surface view (the one binded to camera).
     *
     * @param window The surface view object
     */
    public void setVideoAndroidCapturer(SurfaceView window) {
        Xvi.vid_set_android_capturer((Object) window);
    }


    public void onConnectivityChange(SipProfile account, boolean on) {
        Log.vf(TAG, "onConnectivityChange; hasConnectivity=%s, account=%s", on, account);
        if (on){
            // let PjSip to re-register
            setRawAccountRegistration(account, true);
        } else {
            // do nothing (not even un-register), let PjSip handle this
        }
    }

    public boolean isConnected() {
        return service == null || service.getConnected().get();
    }

    /**
     * Called when underflow event was detected on delay buffer thresholded over 55% of underflow rate.
     *
     * @param call_id
     * @param med_idx
     * @param conf_port_idx
     * @param underflow_status
     * @param underflow_ratio
     */
    public void onUnderflowEvent(final int call_id, final long med_idx, final int conf_port_idx,
                                 final int underflow_status, final double underflow_ratio)
    {

        try {
            getPjCallback().updateCallInfoByTask(call_id, new SipCallSessionUpdateTask() {
                @Override
                public SipCallSession updateCallSession(SipCallSession session) {
                    if (session == null) {
                        Log.wf(TAG, "Session is null");
                        return null;
                    }

                    session.setLastUnderflowRate(underflow_ratio);
                    session.setLastUnderflowStatus(underflow_status);
                    if (underflow_ratio < 0.50) {
                        return session;
                    }

                    final Integer cntRestarts = session.getCntUnderflowMediaRestarts();
                    final long now = System.currentTimeMillis();
                    final Long lastRestart = session.getLastUnderflowMediaRestart();
                    if (lastRestart == null || (now - lastRestart) > 1000 * 30) {
                        Log.vf(TAG, "Going to reinit sound, restarts: %s, last one: %s", cntRestarts, lastRestart);
                        session.setLastUnderflowMediaRestart(now);
                        session.setCntUnderflowMediaRestarts(cntRestarts == null ? 1 : cntRestarts + 1);

                        // Reinit it on manager
                        executeOnServiceHandler(new SvcRunnable("reinitSound") {
                            @Override
                            protected void doRun() throws SameThreadException {
                                mediaManager.reinitSound();
                            }
                        });

                    } else {
                        Log.wf(TAG, "Underflow restart too recent");
                    }

                    return session;
                }
            });
        } catch (SameThreadException e) {
            Log.e(TAG, "Exception", e);
        }
    }
}
