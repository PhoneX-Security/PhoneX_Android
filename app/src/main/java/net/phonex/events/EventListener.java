package net.phonex.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcelable;
import android.text.TextUtils;

import net.phonex.core.Intents;
import net.phonex.gcm.entities.GcmMessage;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.pub.parcels.CertUpdateParams;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.soap.entities.PairingRequestUpdateElement;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.system.ProcKiller;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class EventListener extends BroadcastReceiver {
    private static final String TAG = "EventListener";
    private static final String PROC_NET_ROUTE = "/proc/net/route";    

    // Comes from android.net.vpn.VpnManager.java
    // Action for broadcasting a connectivity state.
    public static final String ACTION_VPN_CONNECTIVITY = "vpn.connectivity";
    
    private XService service;
    
    // Store current state
    private final Object stateMutex = new Object(); 
    private String mNetworkType;
    private boolean mConnected = false;
    private String mRoutes = "";
    private Timer pollingTimer;

    /**
     * Check if the intent received is a sticky broadcast one 
     * A compact way
     * @param it intent received
     * @return true if it's an initial sticky broadcast
     */
    public boolean compatIsInitialStickyBroadcast(Intent it) {
        return isInitialStickyBroadcast();
    }
    
    /**
     * Constructor. 
     * @param aService
     */
    public EventListener(XService aService) {
        service = aService;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Run the handler in XServiceHandler to be protected by wake lock
    	final String action = intent==null ? "" : intent.getAction();
        service.getHandler().execute(new SvcRunnable("onReceive: " + action)  {
            public void doRun() throws SameThreadException {
                onReceiveInternal(context, intent, compatIsInitialStickyBroadcast(intent));
            }
        });
    }

    /**
     * Internal receiver that will run on sip executor thread
     * @param context Application context
     * @param intent Intent received
     * @throws SameThreadException
     */
    private void onReceiveInternal(Context context, Intent intent, boolean isSticky) throws SameThreadException {
        String action = intent.getAction();
        Log.df(TAG, "Internal receive %s", action);

        switch (action) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                onConnectivityChanged(activeNetwork, isSticky);
                break;
            case Intents.ACTION_SIP_ACCOUNT_CHANGED: {
                final long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);
                // Should that be threaded?
                if (accountId != SipProfile.INVALID_ID) {
                    final SipProfile account = service.getAccount(accountId);
                    if (account != null) {
                        Log.d(TAG, "Enqueue set account registration");
                        service.accountChanged(account);
                    }
                }

                break;
            }
            case Intents.ACTION_SIP_CAN_BE_STOPPED:
                service.cleanStop();

                break;
            case Intents.ACTION_SIP_REQUEST_RESTART:
                service.restartSipStack();

                break;
            case Intents.ACTION_SIP_REQUEST_BRUTAL_RESTART:
                Log.i(TAG, "Going to kill service");

                // Kill service.
                service.getHandler().execute(new SvcRunnable("forceRestart") {
                    @Override
                    protected void doRun() throws SameThreadException {
                        Log.v(TAG, "Going to destroy stack & kill service");
                        try {
                            Thread.sleep(6000);
                            Log.v(TAG, "Initializing APP restart");

                            service.getPjManager().prepareForRestart();
                            ProcKiller.killPhoneX(service, true, true, true, true, true);
                        } catch (Exception ex) {
                            Log.e(TAG, "Exception in app restart", ex);
                        }
                    }
                });

                Log.i(TAG, "Service should be killed already!");

                break;
            case ACTION_VPN_CONNECTIVITY:
                onConnectivityChanged(null, isSticky);

                break;
            case Intents.ACTION_TRIGGER_DHKEY_SYNC:
                service.triggerDHKeyUpdate(3000);

                break;
            case Intents.ACTION_TRIGGER_DHKEY_SYNC_NOW:
                service.triggerDHKeyUpdate(0);

                break;
            case Intents.ACTION_SETTINGS_MODIFIED:
                service.restartSipStack();
                // in case time expiration setting is changed to lower value, re-plan the alarm
                service.getMessageSecurityManager().setupExpirationCheckAlarm(true);

                break;
            case Intents.ACTION_CERT_UPDATE:
                if (!intent.hasExtra(Intents.CERT_INTENT_PARAMS)) {
                    Log.e(TAG, "Action cert update intent does not contain update parameters");
                    return;
                }

                final ArrayList<CertUpdateParams> ps = intent.getParcelableArrayListExtra(Intents.CERT_INTENT_PARAMS);
                final boolean allUsers = intent.getBooleanExtra(Intents.CERT_INTENT_ALLUSERS, false);

                service.triggerCertUpdate(ps, allUsers);

                break;
            case Intents.ACTION_SIP_REGISTRATION_CHANGED: {
                final long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);

                // Should that be threaded?
                if (accountId != SipProfile.INVALID_ID) {
                    final SipProfile account = service.getAccount(accountId);
                    if (account != null) {
                        service.registrationChanged(account);
                    }
                }
                break;
            }

            case Intents.ACTION_TRIGGER_PAIRING_REQUEST_UPDATE:
                Long requestId = (Long) intent.getSerializableExtra(Intents.EXTRA_PAIRING_REQUEST_ID);
                PairingRequestResolutionEnum resolutionEnum = (PairingRequestResolutionEnum) intent.getSerializableExtra(Intents.EXTRA_PAIRING_REQUEST_RESOLUTION);
                if (requestId == null || resolutionEnum == null){
                    Log.ef(TAG, "Invalid intent parameters %s", intent);
                    return;
                }

                PairingRequestUpdateElement element = new PairingRequestUpdateElement();
                element.setId(requestId);
                element.setResolution(resolutionEnum);
                service.getPairingRequestManager().triggerPairingRequestUpdate(element);
                break;

            case Intents.ACTION_TRIGGER_ADD_CONTACT:
                service.getContactsManager().triggerAddContact(
                        intent.getStringExtra(Intents.EXTRA_ADD_CONTACT_SIP),
                        intent.getStringExtra(Intents.EXTRA_ADD_CONTACT_ALIAS));
                break;
            case Intents.ACTION_TRIGGER_LOG_UPLOAD:
                service.getLogSendingManager().triggerLogUpload(intent.getStringExtra(Intents.EXTRA_LOG_UPLOAD_USER_MESSAGE));
                break;
            case Intents.ACTION_GCM_MESSAGES_RECEIVED:
                service.getGcmManager().onMessagesReceived(intent.getParcelableArrayListExtra(Intents.EXTRA_GCM_MESSAGES));
                break;
            default:
                Log.wf(TAG, "Unregistered action: [%s]", action);
                break;
        }
    }
    
    /**
     * Dumps system routes (packet routing).
     * @return
     */
    private String readSystemNetRoutes() {
        String routes = "";
        FileReader fr = null;
        try {
            fr = new FileReader(PROC_NET_ROUTE);

            StringBuffer contentBuf = new StringBuffer();
            BufferedReader buf = new BufferedReader(fr);
            String line;
            while ((line = buf.readLine()) != null) {
                contentBuf.append(line+"\n");
            }
            routes = contentBuf.toString();
            buf.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "No route file found routes", e);
        } catch (IOException e) {
            Log.e(TAG, "Unable to read route file", e);
        } catch (java.lang.ArrayIndexOutOfBoundsException e){
        	Log.e(TAG, "Array out of bound exception - BufferedReader.readLine problem?", e);
        }finally {
            MiscUtils.closeSilently(fr);
        }
        
        // Clean routes that point unique host 
        // this aims to workaround the fact android 4.x wakeup 3G layer when position is retrieve to resolve over 3g position
        String finalRoutes = routes;
        if(!TextUtils.isEmpty(routes)) {
            String[] items = routes.split("\n");
            if (items==null){
                return finalRoutes;
            }

            List<String> finalItems = new ArrayList<String>();
            for(int curLine = 0, numLines=items.length; curLine <numLines; curLine++){
                if (curLine == 0) {
                    continue;
                }

                boolean addItem = true;
                String[] ent = items[curLine].split("\t");
                if(ent.length <= 8){
                    continue;
                }

                String maskStr = ent[7];
                if(maskStr.matches("^[0-9A-F]{8}$")) {
                    int lastMaskPart = Integer.parseInt(maskStr.substring(0, 2), 16);
                    if(lastMaskPart > 192) {
                        // if more than 255.255.255.192 : ignore this line
                        addItem = false;
                    }
                }else {
                    Log.wf(TAG, "Problem with route mask [%s]", maskStr);
                }

                if(addItem) {
                    finalItems.add(items[curLine]);
                }
            }

            finalRoutes = TextUtils.join("\n", finalItems); 
        }
        
        return finalRoutes;
    }

    /**
     * Treat the fact that the connectivity has changed
     * @param info Network info
     * @param isSticky
     * @throws SameThreadException
     */
    private void onConnectivityChanged(NetworkInfo info, boolean isSticky) throws SameThreadException {
        // We only care about the default network, and getActiveNetworkInfo()
        // is the only way to distinguish them. However, as broadcasts are
        // delivered asynchronously, we might miss DISCONNECTED events from
        // getActiveNetworkInfo(), which is critical to our SIP stack. To
        // solve this, if it is a DISCONNECTED event to our current network,
        // respect it. Otherwise get a new one from getActiveNetworkInfo().
        if (info == null || info.isConnected() ||
                !info.getTypeName().equals(mNetworkType)) {
            ConnectivityManager cm = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
            info = cm.getActiveNetworkInfo();
        }

        boolean connected = (info != null && info.isConnected() && service.isConnectivityValid());
        String networkType = connected ? info.getTypeName() : "null";
        String currentRoutes = readSystemNetRoutes();
        String oldRoutes;
        synchronized (stateMutex) {
            oldRoutes = mRoutes;
        }
        
        // Ignore the event if the current active network is not changed.
        if (connected == mConnected && networkType.equals(mNetworkType) && currentRoutes.equals(oldRoutes)) {
            return;
        }
        
        if(Log.getLogLevel() >= 4) {
            if(!networkType.equals(mNetworkType)) {
                Log.d(TAG, String.format("onConnectivityChanged(): %s -> %s; ln_oldRoutes=%d, ln_curRoutes=%d @ " + this,
                		mNetworkType, networkType, currentRoutes.length(), mRoutes.length()));
            }else {
                Log.df(TAG, "Route changed : %s -> %s", mRoutes, currentRoutes);
            }
        }
        
        // Now process the event
        synchronized (stateMutex) {
            mRoutes = currentRoutes;
            mConnected = connected;
            mNetworkType = networkType;
        }

        Log.df(TAG, "onConnectivityChanged: connection status [%s]; networkType [%s], sticky: %s",
                String.valueOf(connected), mNetworkType, isSticky);
        if(!isSticky) {
            service.onConnectivityChanged(connected);
        }
    }
    
    /**
     * Starts routes monitoring.
     * Called during initialization. 
     */
    public void startMonitoring() {
        int pollingIntervalMin = service.getPrefs().getInteger(PhonexConfig.NETWORK_ROUTES_POLLING);
        
        Log.df(TAG, "Start monitoring of route file ? %s", pollingIntervalMin);
        if(pollingIntervalMin > 0) {
            pollingTimer = new Timer("NetworkMonitor", true);
            pollingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    String currentRoutes = readSystemNetRoutes();
                    String oldRoutes;
                    synchronized (stateMutex) {
                        oldRoutes = mRoutes;
                    }

                    if(!currentRoutes.equalsIgnoreCase(oldRoutes)) {
                        Log.d(TAG, String.format("Route changed; current=[%s] old=[%s] @ " + this, currentRoutes, oldRoutes));
                        // Run the handler in XServiceHandler to be protected by wake lock
                        service.getHandler().execute(new SvcRunnable("onConnectivityChanged")  {
                            public void doRun() throws SameThreadException {
                            	Log.v(TAG, "calling onConnectivityChanged(null, false)");
                                onConnectivityChanged(null, false);
                            }
                        });
                    }
                }
            }, 5000, pollingIntervalMin * 60 * 1000);
        }
    }
    
    /**
     * Stops monitoring of the routes.
     */
    public void stopMonitoring() {
        if(pollingTimer != null) {
            pollingTimer.cancel();
            pollingTimer.purge();
            pollingTimer = null;
        }
    }
    
    /**
     * Registers this intent receiver to the system.
     */
    public void registerReceiver(){
        Log.vf(TAG, "register receiver");
    	IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(Intents.ACTION_SIP_ACCOUNT_CHANGED);
		intentfilter.addAction(Intents.ACTION_SIP_CAN_BE_STOPPED);
        intentfilter.addAction(Intents.ACTION_SETTINGS_MODIFIED);
		intentfilter.addAction(Intents.ACTION_SIP_REQUEST_RESTART);
		intentfilter.addAction(Intents.ACTION_SIP_REQUEST_BRUTAL_RESTART);
		intentfilter.addAction(Intents.ACTION_TRIGGER_DHKEY_SYNC);
		intentfilter.addAction(Intents.ACTION_TRIGGER_DHKEY_SYNC_NOW);
		intentfilter.addAction(Intents.ACTION_CERT_UPDATE);
		intentfilter.addAction(Intents.ACTION_SIP_REGISTRATION_CHANGED);
		intentfilter.addAction(Intents.ACTION_TRIGGER_PAIRING_REQUEST_UPDATE);
		intentfilter.addAction(Intents.ACTION_TRIGGER_ADD_CONTACT);
		intentfilter.addAction(Intents.ACTION_TRIGGER_LOG_UPLOAD);
		intentfilter.addAction(Intents.ACTION_GCM_MESSAGES_RECEIVED);
        MiscUtils.registerReceiver(service, this, intentfilter);
        service.registerReceiver(this, new IntentFilter(EventListener.ACTION_VPN_CONNECTIVITY));
        service.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		// Called during creation of the object, load current routes & connection types.
		ConnectivityManager cm = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        
        boolean connected = (info != null && info.isConnected() && service.isConnectivityValid());
        String networkType = connected ? info.getTypeName() : "null";
        String currentRoutes = readSystemNetRoutes();
        synchronized (stateMutex) {
            mConnected = connected;
            mNetworkType = networkType;
            mRoutes = currentRoutes;
        }
        
        Log.inf(TAG, "EventListener registered; connected=%s; networkType=%s; len_routes=%d; @ %s",
        		mConnected, mNetworkType, mRoutes.length(), this);
    }
    
    /**
     * Unregisters this intent receiver from the system.
     */
    public void unregisterReceiver(){
    	Log.i(TAG, "EventListener unregistered");
    	service.unregisterReceiver(this);
    }
}
