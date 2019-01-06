package net.phonex.service.xmpp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;

import net.phonex.PhonexSettings;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.gcm.entities.GcmMessage;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.a.Compatibility;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.service.ContactsManager;
import net.phonex.service.ContactsManager.PresenceUpdate;
import net.phonex.service.SvcRunnable;
import net.phonex.service.XService;
import net.phonex.service.XService.SameThreadException;
import net.phonex.service.runEngine.MyWakeLock;
import net.phonex.service.xmpp.customIq.GcmMessagesAcksIQ;
import net.phonex.service.xmpp.customIq.PresenceQueryIQ;
import net.phonex.service.xmpp.customIq.PushIQ;
import net.phonex.service.xmpp.customIq.PushQueryIQ;
import net.phonex.service.xmpp.customIq.SendGcmTokenIQ;
import net.phonex.soap.SSLSOAP;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.guava.Lists;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;


/**
 * Main manager class for XMPP.
 * So far we support only one active user account.
 *
 * @author ph4r05
 */
public class XmppManager {
	private static final String TAG = "XmppManager";

	/**
	 * Force TLS channel here.
	 */
	public static final Integer DEFAULT_PORT = 5222;

	/**
	 * Resource for login, will be in JID.
	 */
	public static final String LOGIN_RESOURCE="phx";

	/**
	 * Sip service running this manager.
	 * Source of the context.
	 */
	private XService svc;

	/**
	 * Mapping of the user name to the internal ID.
	 */
	private final Map<String, XmppUserRecord> userMap = new ConcurrentHashMap<String, XmppUserRecord>();

	/**
	 * Last connectivity event signalized from the system.
	 */
	private volatile Boolean connected=null;

	/**
	 * Switch telling whether the application login was finished.
	 */
	private volatile boolean loginFinished=false;

	/**
	 * CPU wakelock.
	 */
	private MyWakeLock wakeLock;

    /**
     * Push notification listener
     */
    private IQPacketListener iqPacketListener;

	public XmppManager(XService xService) {
		svc = xService;
	}

	/**
	 * Initializes XMPP manager with given context.
	 */
	public void register(){
		this.wakeLock = new MyWakeLock(getSvc().getApplicationContext(), "XMPPManager");

		// Initialize Smack library as described in Smacks readme.
		SmackAndroid.init(svc.getApplicationContext());
		SmackConfiguration.addSaslMech("PLAIN");
		//SmackConfiguration.removeSaslMech("DIGEST-MD5");
		//SmackConfiguration.removeSaslMech("CRAM-MD5");
		//SmackConfiguration.addSaslMech("DIGEST-MD5");
		SmackConfiguration.setDefaultPacketReplyTimeout(15000);
		Log.v(TAG, "XMPP manager initialized");

        initIQProvider();
	}

    /**
     * Adds IQ provider for urn:xmpp:phx push IQ elements.
     */
    private void initIQProvider(){
        ProviderManager.addIQProvider(PushIQ.ELEMENT, PushIQ.NAMESPACE, new MyIQProvider());
    }

	/**
	 * Creates a connection configuration object for XMPP connection
	 * from user account record.
	 *
	 * @param account
	 * @return
	 * @throws IOException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws UnrecoverableKeyException
	 */
	private ConnectionConfiguration getConnectionConfig(SipProfile account)
			throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{

		ConnectionConfiguration connConfig = new ConnectionConfiguration(account.getXmpp_service());
		connConfig.setSecurityMode(SecurityMode.required); // Will force TLS/SSL connection.
		connConfig.setDebuggerEnabled(PhonexSettings.debuggingRelease());
		connConfig.setCompressionEnabled(false);

		StoredCredentials creds = MemoryPrefManager.loadCredentials(svc);
		if (creds==null || TextUtils.isEmpty(creds.getUserSip()) || TextUtils.isEmpty(creds.getUsrStoragePass())) {
			throw new IllegalArgumentException("No stored credentials");
		}

		// Generate SSL context
		KeyPairGenerator kpg = new KeyPairGenerator();
    	KeyStore ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(creds.getUserSip()), svc, creds.getUsrStoragePass().toCharArray());
		SSLContext sslContext = SSLSOAP.getSSLContext(ks, creds.getUsrStoragePass().toCharArray(), this.svc, new SecureRandom());

		// Pre-set SSL context manually.
		connConfig.setCustomSSLContext(sslContext);

		// Set TLS parameters.
		TLSUtils.setTLSOnly(connConfig);
		connConfig.setEnabledSSLCiphers(SSLSOAP.getAESCiphersuites());
		connConfig.setEnabledSSLProtocols(SSLSOAP.getSecureProtocols());

		// Custom socket factory cannot be set here since the protocol starts
		// with plaintext negotiating the TLS.
		//connConfig.setSocketFactory(SSLSOAP.getSSLSocketFactory(sslContext));

		// Some misc. settings.
		// Original reconnection manager is disabled, since we have better.
		connConfig.setReconnectionAllowed(false);
		connConfig.setRosterLoadedAtLogin(true);
		return connConfig;
	}

	/**
	 * Returns true if connection is not null, is connected,
	 * is authenticated and secure.
	 *
	 * @param con
	 * @return
	 */
	public static boolean isConnectionWorking(final XMPPConnection con){
		return con!=null && con.isConnected() && con.isAuthenticated() && con.isSecureConnection();
	}

    /**
     * Returns resource string for XMPP login, e.g., test@phone-x/resource
     * which identifies this particular device. User may be connected with several devices
     * thus each resource should be as unique as possible.
     *
     * @return
     */
	public String getXmppResourceString(){
		return getXmppResourceString(svc);
	}

    public static String getXmppResourceString(Context context){
        final String deviceDesc = PreferencesManager.getDeviceDesc(context);
        String sha256 = Base64.encodeBytes(deviceDesc.getBytes());

        try {
            sha256 = MessageDigest.encodeHex(MessageDigest.hashSha256(sha256));
        } catch (Exception e) {
            Log.e(TAG, "Could not compute hash", e);
        }

        return getXmppResourcePrefix() + sha256.substring(0, 10);
    }

	private static String getXmppResourcePrefix(){
		return LOGIN_RESOURCE + (Compatibility.isBlackBerry() ? "bb" : "and") + "_";
	}

	/**
	 * Remove user from the internal configuration.
	 * If is connected, send offline presence status and disconnect.
	 *
	 * @param userSip
	 * @throws NotConnectedException
	 */
	public void removeUser(String userSip) throws NotConnectedException{
		final XmppUserRecord rec = userMap.get(userSip);
		if (rec==null) {
			return;
		}
		Log.vf(TAG, "Going to remove user: %s", userSip);
		wakeLock.lock();

		// Remove connector if exists
		rec.setReconnectionAllowed(false);
		XmppConnectionEstablisher ce = rec.getConnector();
		ce.terminate();

		// If user is connected, update presence (set offline) and disconnect.
		try {
			// Update presence to offline.
			svc.getContactsManager().setOfflinePresence(svc, userSip);

			// Get connection from user record, for disconnecting.
			final AbstractXMPPConnection con = rec != null ? rec.getConnection() : null;
			synchronized(this){
				if (con!=null){
					// Unregister roster listener.
					if (rec.getRosterListener()!=null){
						try {
							Roster roster = con.getRoster();
							roster.removeRosterListener(rec.getRosterListener());
							rec.setRosterListener(null);

						} catch(Exception e){
							Log.e(TAG, "Cannot unregister roster listener");
						}
					}

					// Disconnect, send unavailable presence update.
					if (con.isConnected() && con.isAuthenticated()){

						// Set offline presence & disconnect.
						final Presence presence = new Presence(Presence.Type.unavailable);
						con.disconnect(presence);
					}
				}

				rec.setConnection(null);
			}
		} catch(Exception ex){
			Log.w(TAG, "Exception in removing user", ex);
		}

		// Remove from internal storage
		userMap.remove(userSip);
		wakeLock.unlock();
	}

	/**
	 * Called when account is modified in the database.
	 * Publishes presence after connection if desired.
	 *
	 * @param account
	 */
	public synchronized void accountChanged(SipProfile account){
		try {
			wakeLock.lock("accountChanged");
			Log.vf(TAG, "Account changed, id=[%s], active=%s", account.getAcc_id(), account.isActive());

			// If login was not finished, do nothing, since SIP service is restarted after
			// successful login, user is removed and added again.
			if (!loginFinished){
				Log.v(TAG, "Not adding user, login process not finished yet.");
				return;
			}

			// If active==1 then add an account to the internal structures, it it is not present here.
			// If active==0 then de-activate existing user.
			final String userSip = account.getUsername();
			if (account.isActive()){
				XmppUserRecord rec;
				AbstractXMPPConnection con = null;

				// Check if user profile XMPP name and password is non-empty.
				// If yes it may signalize that account is not yet prepared
				// for XMPP or XMPP is disabled for this account.
				// This also avoids adding non-configured user to the configuration
				// and pointless connection attempts. It would also block addition
				// of the already configured user to the internal state.
				if (TextUtils.isEmpty(account.getXmpp_user())
						|| TextUtils.isEmpty(account.getXmpp_password())
						|| TextUtils.isEmpty(account.getXmpp_service())){
					Log.df(TAG, "XMPP is not enabled for user=%s", account.getAcc_id());
					return;
				}

				// If user is not present in the internal configuration,
				// add it here.
				if (!userMap.containsKey(userSip)){
					// User not present, initialize.
					rec = new XmppUserRecord();
					rec.setUserId(userMap.size())
					   .setUserDbId(account.getId())
					   .setKey(userSip)
					   .setProfile(account)
					   .setRosterDumpDone(false)
					   .setReconnectionAllowed(true);
					userMap.put(userSip, rec);
				} else {
					// User present, load.
					rec = userMap.get(userSip);
					con = rec.getConnection();
				}

				// New connection if current is empty.
				if (con==null){
					Log.vf(TAG, "Creating a new connection, usr=%s; server=%s; service=%s",
					        userSip,
					        account.getXmpp_server(),
					        account.getXmpp_service());
					ConnectionConfiguration connConfig = getConnectionConfig(account);

					// Workaround for static initialization section for ReconnectionManager.
					// Reconnection manager adds itself in the static initialization section
					// to the XMPPConnection.connectionCreationListeners. Thus if a new
					// connection is created, reconnection manager adds itself among
					// connections' addConnectionListener.
					Class.forName(ServiceDiscoveryManager.class.getName());
					Class.forName(PingManager.class.getName());

					// Create a new connection, reconnection manager will be attached now.
					rec.setConnectionConfiguration(null);
					con = new XMPPTCPConnection(connConfig);
					con.setPacketReplyTimeout(15000);

                    // add packet listeners for custom packet types
                    addCustomPacketListeners(con);

					// Add ping manager service.
					// Ping manager is already present in the connection.
					final XMPPConnection connection = con;
					final String user = rec.getKey();

					PingManager mPingManager = PingManager.getInstanceFor(con);
					mPingManager.setPingInterval(90);
					mPingManager.registerPingFailedListener(new PingFailedListener() {
						@Override
						public void pingFailed() {
							Log.i(TAG, "Ping failed..., con=" + connection
									+ "; id=" + connection.getConnectionID()
									+ " usr=" + user);

                            // TODO: implement connection lost technique.
                            // If 1-3 consecutive pings are lost, disconnect XMPP and try to connect.
                            // Has to take care about current state of the connection, reconnection
                            // should not be in progress right now.

						}
					});
					rec.setPingManager(mPingManager);

					// Add connection listener
					CustomConnectionListener listener = new CustomConnectionListener(rec.getKey(), this);
					con.addConnectionListener(listener);
					rec.setConnectionListener(listener);

					// Store connection to the map
					rec.setConnection(con);

					// Connection establisher
					XmppConnectionEstablisher ce = new XmppConnectionEstablisher(rec, this);
					rec.setConnector(ce);
				}

				// Update user record.
				rec.setProfile(account);
				rec.getConnector().updateProfile(account);

				// If not connected or authenticated, allocate a new connector and
				// perform retried login.
				// On successful finish, onAuthenticated() will be called.
				rec.getConnector().startIfNeeded();

			} else {
				// User not active -> disconnect.
				if (!userMap.containsKey(userSip)){
					// Nothing to do, user is not present in the internal configuration.
					return;
				}

				// Disconnect
				removeUser(userSip);
			}
		} catch(Exception e){
			Log.e(TAG, "Exception in accountChanged()", e);
		} finally {
			wakeLock.unlock();
		}
	}

    private void addCustomPacketListeners(XMPPConnection connection){
        Log.vf(TAG, "addCustomPacketListeners");
        // Create a packet filter to listen for new messages from a particular
        // user. We use an AndFilter to combine two other filters._
        PacketFilter filter = new PacketTypeFilter(PushIQ.class);
        // Assume we've created a XMPPConnection name "connection".

        // First, register a packet collector using the filter we created.
        PacketCollector myCollector = connection.createPacketCollector(filter);
        // Normally, you'd do something with the collector, like wait for new packets.
        if (iqPacketListener == null){
            iqPacketListener = new IQPacketListener(connection);
            iqPacketListener.setListener(new PushMessagesListenerImpl(svc));
        }
        // Re-add
        connection.removePacketListener(iqPacketListener);
        connection.addPacketListener(iqPacketListener, filter);
    }



	/**
	 * Called when user was authenticated for the first time in the current connection.
	 * Loads roster and sets roster listener.
	 */
	public synchronized void onAuthenticated(final XmppUserRecord rec){
		final AbstractXMPPConnection con = rec.getConnection();
		final boolean connectionValid = isConnectionWorking(con);
		Log.vf(TAG, "onAuthenticated, usr=%s; conValid=%s", rec.getKey(), connectionValid);

		// If a roster listener is empty, create a new one.
        if (!connectionValid) {
            Log.wf(TAG, "onAuth vs. connection not valid!");
            return;
        }

		// TODO be aware of fix this
		// DO NOT PUT ANY CODE HERE that delays UserRosterListener registration
		// SMACK might receive presence update before listener is registered
		// if such thing happens, the update will be ignored

        // Publish deferred presence update if there is some.
        if (!sendDeferredPresence(rec)){
			// Deferred presence was not sent, send the last one so server has at least something.
			resendLastPresence(rec);
		}

        // Roster dump.
        try {
            // Fetch roster, add our roster listener to the roster and dump it
            // to the presence layer.
            Roster roster = con.getRoster();
            if (rec.getRosterListener()==null){
                rec.setRosterListener(new UserRosterListener(rec.getKey(), this));

                roster.addRosterListener(rec.getRosterListener());
            }

            // If login was not finished, no presence updates are allowed.
            // Perform only if roster was not dumped yet.
            if (loginFinished && !rec.isRosterDumpDone()){
                // Initially load the whole roster and presence manually.
                dumpRosterPresenceToListenerInHandler(rec.getKey(), roster);
            }
        } catch(Exception e){
            Log.e(TAG, "Exception in onAuthenticated", e);
        }

        // Query all recent push updates.
        svc.getHandler().execute(new SvcRunnable("pushQuery") {
			@Override
			protected void doRun() throws SameThreadException {
				sendPushQuery(rec);
			}
		});

		// Send Gcm token later
		svc.getHandler().execute(new SvcRunnable("sendGcmToken") {
			@Override
			protected void doRun() throws SameThreadException {
				sendGcmToken(rec);
			}
		});
		svc.getHandler().execute(new SvcRunnable("ackGcmMessages") {
			@Override
			protected void doRun() throws SameThreadException {
				List<GcmMessage> gcmMessages = getSvc().getGcmManager().retrieveMessagesToAcknowledge();
				acknowledgeGcmMessages(rec, gcmMessages);
			}
		});
    }

	public void acknowledgeGcmMessages(String username, List<GcmMessage> gcmMessages){
		final XmppUserRecord rec = userMap==null ? null : userMap.get(username);
		if (rec == null){
			Log.ef(TAG, "User not found among register users. %s", username);
			return;
		}
		acknowledgeGcmMessages(rec, gcmMessages);
	}

	private void acknowledgeGcmMessages(XmppUserRecord rec, List<GcmMessage> gcmMessages) {
		if (gcmMessages == null || gcmMessages.size() == 0){
			Log.df(TAG, "acknowledgeGcmMessages; no messages.");
			return;
		}
		try {
			rec.getConnection().sendPacket(new GcmMessagesAcksIQ(gcmMessages));
			Log.inf(TAG, "acknowledgeGcmMessages; acknowledgment successfully sent.");
		} catch(Exception ex){
			Log.e(TAG, "Exception, cannot resend last presence", ex);
		}
	}

	private void sendGcmToken(XmppUserRecord rec) {
		Log.vf(TAG, "sendGcmToken");

		PreferencesConnector prefs = getSvc().getPrefs();
		if (prefs == null){
			Log.wf(TAG, "sendGcmToken; null preferences");
			return;
		}

		boolean tokenIsSent = prefs.getBoolean(PhonexConfig.GCM_TOKEN_SENT_TO_SERVER, false);
		if (tokenIsSent){
			Log.inf(TAG, "Token is already sent, not resending");
			return;
		}
		String token = prefs.getString(PhonexConfig.GCM_TOKEN);
		if (token == null){
			Log.ef(TAG, "No token is stored in preferences, not sending.");
			return;
		}

		PackageInfo pinfo = PreferencesConnector.getCurrentPackageInfos(getSvc());
		String appVersion = pinfo.versionName;
		String osVersion = Build.VERSION.RELEASE;
		Locale locale = PhonexSettings.loadDefaultLanguage(getSvc());
		List<String> langs = Lists.newArrayList(locale.getLanguage());

		try {
			SendGcmTokenIQ gcmTokenIQ = new SendGcmTokenIQ(token, appVersion, osVersion, langs);
			rec.getConnection().sendPacket(gcmTokenIQ);
			Log.inf(TAG, "sendGcmToken; token successfully sent.");
			prefs.setBoolean(PhonexConfig.GCM_TOKEN_SENT_TO_SERVER, true);
		} catch(Exception ex){
			Log.e(TAG, "Exception, cannot resend last presence", ex);
		}
	}

	/**
	 * Re-sends last presence for given user.
	 * Used when connection is re-connected.
	 * @param rec
	 */
	private void resendLastPresence(XmppUserRecord rec){
		final Presence lastPresence = rec.getLastPresence();
		if (lastPresence == null) {
			return;
		}

		try {
			rec.getConnection().sendPacket(lastPresence);
		} catch(Exception ex){
			Log.e(TAG, "Exception, cannot resend last presence", ex);
		}
	}

    /**
     * If there is some deferred presence update (waiting to be sent),
     * send it to the connection associated with rec.
     * @param rec
	 * @return true if there was a deferred presence and it was sent
     */
    private boolean sendDeferredPresence(XmppUserRecord rec){
        final Presence deferredPresence = rec.getDeferredPresence();
        if (deferredPresence == null) {
            return false;
        }

        try {
            rec.getConnection().sendPacket(deferredPresence);

            // Reset deferered presence, do not re-send this one again.
            rec.setDeferredPresence(null);
			return true;
        } catch(Exception ex){
            Log.e(TAG, "Exception, cannot update presence", ex);
        }

		return false;
    }

    /**
     * Sends pushQuery packet asking for recent push updates.
     * @param rec
     */
    private boolean sendPushQuery(XmppUserRecord rec){
        try {
            final PushQueryIQ packet = new PushQueryIQ();
            packet.setType(IQ.Type.GET);
            //packet.setTo(rec.getProfile().getSipDomain());

            rec.getConnection().sendPacket(packet);
            Log.v(TAG, "Push query sent");

            return true;
        } catch(Exception ex){
            Log.e(TAG, "Exception, cannot update presence", ex);
        }

        return false;
    }

    /**
     * Send presenceQueryIQ packet, asking for recent presence updates of contacts in my roster.
     * @param rec
     * @return
     */
    private boolean sendPresenceQuery(XmppUserRecord rec){
        try {
            final PresenceQueryIQ packet = new PresenceQueryIQ();
            packet.setType(IQ.Type.GET);
            //packet.setTo(rec.getProfile().getSipDomain());

            rec.getConnection().sendPacket(packet);
            Log.vf(TAG, "Presence query sent for user %s", rec.getKey());

            return true;
        } catch(Exception ex){
            Log.e(TAG, "Exception, cannot update presence", ex);
        }

        return false;
    }

	/**
	 * Unregister event signalized on application shutdown.
	 * Removes user from the internal structures, publishing unavailable presence update
	 * and setting all contacts in the database to offline.
	 */
	public synchronized void unregister(){
		wakeLock.lock("unregister");
		for(Entry<String, XmppUserRecord> e : userMap.entrySet()){
			Log.vf(TAG, "Unregistering user: %s", e.getKey());
			try {
				removeUser(e.getKey());
			} catch(Exception ex){
				Log.df(TAG, ex, "Exception during unregistration of user: %s", e.getKey());
			}
		}
		wakeLock.unlock();
	}

	/**
	 * Event on login finished.
	 */
	public synchronized void onLoginFinished(){
		setLoginFinished(true);
		Log.v(TAG, "onLoginFinished");
	}

	/**
	 * Dump all rosters to the presence layer if not dumped yet.
     * @param initialDumpOnly if true, roster dump is performed only if it was not before.
     *                        rec.isRosterDumpDone.
     * @param queryPresence if true then XMPP server is queried to dump all presence information from the roster.
     */
	public synchronized void dumpRosters(boolean initialDumpOnly, boolean queryPresence){
		if (!loginFinished){
			Log.d(TAG, "Cannot dump rosters, login not finished.");
			return;
		}

		// Iterate over internal users and check whether their rosters were dumped.
		wakeLock.lock("dumpRosters");
		for(Entry<String, XmppUserRecord> e : userMap.entrySet()){
			try {

				final XmppUserRecord rec = e.getValue();
				if (initialDumpOnly && rec.isRosterDumpDone()){
					continue;
				}

				// Has a valid connection?
				final AbstractXMPPConnection con = rec.getConnection();
				final boolean connectionValid = isConnectionWorking(con);
				Log.vf(TAG, "dumpRosters, usr=%s; conValid=%s", rec.getKey(), connectionValid);
				if (!connectionValid){
					continue;
				}

				Roster roster = con.getRoster();
				if (roster==null){
					continue;
				}

				dumpRosterPresenceToListenerInHandler(rec.getKey(), roster);

                // Try to reconnect if applicable
                if (queryPresence){
                    Log.v(TAG, "Sending presence query");
					sendPresenceQueryAsync(rec);
                }

			} catch(Exception ex){
				Log.e(TAG, "Exception during onLoginFinished", ex);
			}
		}
		wakeLock.unlock();
	}

    public void onConnectivityChanged(boolean on){
        if (on){
            onConnectivityOn();
        } else {
            onConnectivityLost();
        }
    }

	/**
	 * Event signalized on loss of connectivity (e.g., mobile connectivity
	 * loss due to incoming GSM call, etc...)
	 */
	public synchronized void onConnectivityLost(){
		connected = false;
		Log.v(TAG, "Connectivity lost.");

		svc.getHandler().execute(new SvcRunnable("connectivityLost") {
            @Override
            protected void doRun() throws SameThreadException {
				final ContactsManager contactsManager = svc.getContactsManager();
				for (Entry<String, XmppUserRecord> e : userMap.entrySet()) {
                    try {
						contactsManager.setOfflinePresence(svc, e.getKey());
                    } catch (Exception ex) {
                        Log.w(TAG, "Exception during presence update, user: " + e.getKey(), ex);
                    }
                }
            }
        });
	}

	/**
	 * Event signalizing connectivity is recovered.
	 * Tries to re-connect all connections.
	 */
	public synchronized void onConnectivityOn(){
		connected = true;
		Log.v(TAG, "Connectivity recovered.");

		wakeLock.lock("connectivityOn");
		for(Entry<String, XmppUserRecord> e : userMap.entrySet()){
			try {
				final XmppUserRecord rec = e.getValue();
				Log.vf(TAG, "Reconnecting user: %s", e.getKey());

				// If there exists connection establisher, notify it about state.
				XmppConnectionEstablisher ce = rec.getConnector();
				try {
					// Force some network activity.
					// If there is problem with connection, this should make it appear.
					if (rec.getLastPresence()!=null){
						rec.getConnection().sendPacket(rec.getLastPresence());
					}

					// Test server presence anyway.
					// If here, connection is valid, so more testing is not harmful.
					boolean response = rec.getPingManager().pingMyServer();
					Log.vf(TAG, "Ping response=%s", response);

					if (!response && rec.getConnection().isConnected()){
						// Disconnect it, sorry
						Log.v(TAG, "Ping failed, connection is connected -> disconnect it");
						rec.getConnection().disconnect();
					}
				} catch(Exception ex){
					Log.i(TAG, "Exception, ping failed", ex);
				}

				ce.connectivityOn();
			} catch(Exception ex){
				Log.wf(TAG, ex, "Exception during reconnecting of user: %s", e.getKey());
			}
		}

		wakeLock.unlock();
	}

	/**
	 * Public interface to set a presence state to the registered user.
	 * If storeToDeferred is enabled and user is present, but not connected,
	 * presence is stored to the pending presence updates and will be
	 * pushed as user is logged to the XMPP server.
	 *
	 * @param userName
	 * @param available
	 * @param protobufStatus ProtocolBuffers message, should be constructed using PresenceManager#generatePresenceText(
	 * @param storeToDeferred
	 * @throws NotConnectedException
	 */
	public synchronized void setPresence(String userName, boolean available, String protobufStatus, boolean storeToDeferred) throws NotConnectedException{
		final XmppUserRecord rec = userMap==null ? null : userMap.get(userName);
		if (rec==null){
			Log.ef(TAG, "User not found among register users. %s", userName);
			return;
		}

		final Presence presence = new Presence(available ? Presence.Type.available : Presence.Type.unavailable);
		if (protobufStatus != null){
			presence.setStatus(protobufStatus);
		}

		wakeLock.lock("setPresence");
		rec.setLastPresence(presence);
		try {
			final AbstractXMPPConnection con = rec.getConnection();
			if (!isConnectionWorking(con)){
				Log.d(TAG, "Cannot set presence, connection not valid.");

				// If enabled, store presence updated to deferred presence update,
				// it will be broadcasted on login.
				if(storeToDeferred){
					Log.v(TAG, "Storing presence update to deferred storage.");
					rec.setDeferredPresence(presence);
				}

				// Start reconnection if not started.
				rec.getConnector().startIfNeeded();

			} else {
				// Connection is working, send presence packet
				con.sendPacket(presence);
			}
		} catch(Exception ex){
			Log.ef(TAG, "Exception in setting presence of=%s", userName);

			// Something failed, add to deferred presence updates.
			if(storeToDeferred){
				Log.v(TAG, "Storing presence update to deferred storage.");
				rec.setDeferredPresence(presence);
			}
		}
		wakeLock.unlock();
	}

	/**
	 * Removes local identifier from the jid.
	 * @param jid
	 * @return
	 */
	public static String getBareJID(String jid){
		if (TextUtils.isEmpty(jid)){
			return jid;
		}

		int idx = jid.lastIndexOf("/");
		if (idx < 0) {
			return jid;
		}

		return jid.substring(0, idx);
	}

	/**
	 * Converts JID to SIP.
	 * @param jid
	 * @return
	 */
	public static String jid2sip(String jid){
		String from = getBareJID(jid);
		return SipUri.getCanonicalSipContact(from, false);
	}

	/**
	 * Dumps presence for whole roster to the contact manager.
	 * Uses service.
	 *
	 * @param userName
	 * @param roster
	 */
	protected void dumpRosterPresenceToListener(String userName, Roster roster){
		try {
			// Get record for the user.
			final XmppUserRecord rec = userMap.get(userName);
			if (rec==null){
				Log.w(TAG, "Record is null, cannot dump roster");
				return;
			}
			// If roster listener is null, roster cannot be dumped to it. Should not happen.
			if (rec.getRosterListener()==null){
				Log.i(TAG, "Roster listener is null, cannot dump.");
				return;
			}

			// Mark this roster as being dumped after login in order to avoid multiple calls
			// to this function since it is expensive.
			rec.setRosterDumpDone(true);

			// Get all roster entries from the roster, get their presence and dump them
			// to the presence listener.
			// Presence listener should be using executor for updating contacts presence.
			Collection<RosterEntry> entries = roster.getEntries();
			Log.vf(TAG, "Going to dump roster to presence listener, size=%s", entries.size());

			List<Presence> presenceList = new LinkedList<>();
			for (RosterEntry entry : entries) {
				Presence entryPresence = roster.getPresence(entry.getUser());
				presenceList.add(entryPresence);
			}

			// Batch presence update.
			rec.getRosterListener().presenceChanged(presenceList);
			Log.vf(TAG, "Roster dump finished for %s.", userName);
		} catch(Exception ex){
			Log.e(TAG, "Exception in dumping roster to the listener.", ex);
		}
	}

	/**
	 * Sets contact presence in executor.
	 *
	 * @param remoteUser
	 * @param statusText
	 */
	protected void setPresenceInHandler(final String remoteUser, final boolean isAvailable, final String statusText){
		svc.getHandler().execute(new SvcRunnable("setContactPresence"){
			@Override
			protected void doRun() throws SameThreadException {
				try {
					svc.getContactsManager().updatePresence(svc, remoteUser, isAvailable, statusText);
				} catch(Exception e){
					Log.e(TAG, "Exception in setting contact presence in executor.", e);
				}
			}
		});
	}

    /**
     * Tries to dump all rosters from valid connection to the presence listeners.
     * Performed in a handler to serialize roster access.
     * TODO: In future employ mechanism of elimination of the same processes in the queue.
     *       Example: in queue are two same tasks, both not started. Only one should be executed.
     *       Design proposal: unique id, map in the handler, allow only one added & not executed tasks.
     */
    protected void dumpRostersInHandler(){
        svc.getHandler().execute(new SvcRunnable("dumpRosters"){
            @Override
            protected void doRun() throws SameThreadException {
                try {
                    dumpRosters(false, true);
                } catch(Exception e){
                    Log.e(TAG, "Exception in dumping rosters.", e);
                }
            }
        });
    }

    /**
     * Dumps particular roster for given user in handler to avoid concurrency problems.
     * @param userName
     * @param roster
     */
    protected void dumpRosterPresenceToListenerInHandler(final String userName, final Roster roster){
        svc.getHandler().execute(new SvcRunnable("dumpRosterPresence"){
            @Override
            protected void doRun() throws SameThreadException {
                try {
                    dumpRosterPresenceToListener(userName, roster);
                } catch(Exception e){
                    Log.e(TAG, "Exception in dumping rosters.", e);
                }
            }
        });
    }

	/**
	 * Asynchronous presenceQuery IQ sending, for obtaining user's current presence.
	 * @param rec
	 */
	protected void sendPresenceQueryAsync(final XmppUserRecord rec){
		svc.getHandler().execute(new SvcRunnable("sendPresenceQuery"){
			@Override
			protected void doRun() throws SameThreadException {
				sendPresenceQuery(rec);
			}
		});

	}

    /**
     * Hack in order to obtain new presence update for contacts.
     * If there is last presence update, this method will: send unavailable presence & send last presence
     * provoking XMPP server to send all presence information needed.
     *
	 * @deprecated for PhoneX, not needed with XMPP servers with PhoneX plugin supporting preenceQuery IQ.
     * @param rec
     */
    protected void presenceRepublishInHandler(final XmppUserRecord rec){
        svc.getHandler().execute(new SvcRunnable("dumpRosterPresence"){
            @Override
            protected void doRun() throws SameThreadException {
                try {
                    // TODO: switch to presence query hack when it proves working. This is prototype for testing...
                    sendPresenceQuery(rec);

                    // Try to reconnect if applicable
                    final Presence lastPubPres = rec.getLastPresence();
                    if (lastPubPres == null) {
                        return;
                    }

                    Log.v(TAG, "Using on/off presence hack - handler");
                    final Presence unavailablePresence = new Presence(Presence.Type.unavailable);
                    rec.getConnection().sendPacket(unavailablePresence);
                    rec.getConnection().sendPacket(lastPubPres);
                } catch(Exception e){
                    Log.e(TAG, "Exception in presence reconnect.", e);
                }
            }
        });
    }

	/**
	 * Sets contact presence in executor.
	 * More at once.
	 *
	 * @param presences
	 */
	protected void setPresenceInExecutor(final Collection<PresenceUpdate> presences){
		svc.getHandler().execute(new SvcRunnable("setContactPresenceBulk"){
			@Override
			protected void doRun() throws SameThreadException {
				try {
					svc.getContactsManager().updatePresence(svc, presences);
				} catch(Exception e){
					Log.e(TAG, "Exception in setting contact presence in executor.", e);
				}
			}
		});
	}


	/**
	 * Custom connection listener for our connection.
	 * For now only for debugging purposes, helps with dead connection
	 * investigation.
	 *
	 * @author ph4r05
	 */
	public static class CustomConnectionListener implements ConnectionListener {
		private final WeakReference<XmppManager> xmppManagerWr;
		private final String user;

		public CustomConnectionListener(String user, XmppManager manager){
			this.user = user;
			this.xmppManagerWr = new WeakReference<XmppManager>(manager);
		}

		@Override
		public void authenticated(XMPPConnection arg0) {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection authenticated: %s; con=%s", user, arg0);
		}

		@Override
		public void connected(XMPPConnection arg0) {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection connected: %s; con=%s", user, arg0);
		}

		@Override
		public void connectionClosed() {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection closed: %s", user);
		}

		@Override
		public void connectionClosedOnError(Exception arg0) {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection closedOnError: %s", user);
		}

		@Override
		public void reconnectingIn(int arg0) {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection reconnecting in %s user: %s", arg0, user);
		}

		@Override
		public void reconnectionFailed(Exception arg0) {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection reconnection failed: %s", user);
		}

		@Override
		public void reconnectionSuccessful() {
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			Log.vf(TAG, "connection reconnected: %s", user);
		}

	}

	/**
	 * Custom roster listener.
	 * Listens to roster updates and routes this information to the
	 * contact & presence layer.
	 *
	 * @author ph4r05
	 */
	public static class UserRosterListener implements RosterListener {
		private final WeakReference<XmppManager> xmppManagerWr;
		private final String user;

		public UserRosterListener(String user, XmppManager manager){
			this.user = user;
			this.xmppManagerWr = new WeakReference<XmppManager>(manager);
		}

		@Override
		public void entriesAdded(Collection<String> arg0) {
			// Roster entries were added to the roster.
            XmppManager mgr = xmppManagerWr.get();
            if (mgr==null || !mgr.isLoginFinished()){
                return;
            }

            for(String u : arg0){
                // Remove resource identifier
                String remoteUser = jid2sip(u);
                Log.vf(TAG, "Roster added entry; to=%s; from=%s;", user, remoteUser);
            }

            // Dump here the roster, fetch it and update it.
            mgr.dumpRostersInHandler();
		}

		@Override
		public void entriesDeleted(Collection<String> arg0) {
			// Roster entries were deleted from the roster.
			// Even though this roster is not a primary storage
			// for our buddy list, react on removing by setting
			// contacts presence to offline.
			// If login was not finished, no presence updates are allowed.
			XmppManager mgr = xmppManagerWr.get();
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			for(String u : arg0){
				// Remove resource identifier
				String remoteUser = jid2sip(u);
				Log.vf(TAG, "Roster delete entry; to=%s; from=%s;", user, remoteUser);

				// Distribute information.
				mgr.setPresenceInHandler(remoteUser, false, "");
			}
		}

		@Override
		public void entriesUpdated(Collection<String> arg0) {
			// Roster entries were updated (e.g., nick name).
			// This is ignored for now since our buddy list is
			// stored in another primary location.
            Log.vf(TAG, "entriesUpdated; arg: %s", arg0.size());
		}

		/**
		 * Presence changed listener, able to dump multiple presence
		 * updates in time, making it more effective.
		 * @param presence
		 */
		public void presenceChanged(final Collection<Presence> presence){
			XmppManager mgr = xmppManagerWr.get();
			// If login was not finished, no presence updates are allowed.
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			List<PresenceUpdate> presUpdate = new LinkedList<PresenceUpdate>();
			for(Presence p : presence){
				// Remove resource identifier
				String remoteUser = jid2sip(p.getFrom());
                Log.vf(TAG, "Bulk roster update to=%s; from=%s; isAvailable=%s", user, remoteUser, p.isAvailable());

                //p.isAvailable() is a convenience method equivalent to getType() == Presence.Type.available
				presUpdate.add(new PresenceUpdate(remoteUser, p.isAvailable(), p.getStatus()));
			}

			// Distribute in a bulk way.
			mgr.setPresenceInExecutor(presUpdate);
		}

		@Override
		public void presenceChanged(Presence presence) {
			XmppManager mgr = xmppManagerWr.get();
			// If login was not finished, no presence updates are allowed.
			if (mgr==null || !mgr.isLoginFinished()){
				return;
			}

			// Remove resource identifier
			String remoteUser = jid2sip(presence.getFrom());
			Log.vf(TAG, "Roster update to=%s; from=%s;", user, remoteUser);

			// Distribute information.
			mgr.setPresenceInHandler(remoteUser, presence.isAvailable(), presence.getStatus());
		}
	}

	public XService getSvc() {
		return svc;
	}

	public void setSvc(XService svc) {
		this.svc = svc;
	}

	public Boolean getConnected() {
		return connected;
	}

	public boolean isLoginFinished() {
		return loginFinished;
	}

	public void setLoginFinished(boolean loginFinished) {
		this.loginFinished = loginFinished;
	}


}

