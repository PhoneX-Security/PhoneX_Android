package net.phonex.service.xmpp;

import net.phonex.db.entity.SipProfile;
import net.phonex.service.xmpp.XmppManager.CustomConnectionListener;
import net.phonex.service.xmpp.XmppManager.UserRosterListener;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.ping.PingManager;

/**
 * User record stored for each user using XMPP.
 * @author ph4r05
 */
public class XmppUserRecord {
	/**
	 * User identification number in this stack.
	 */
	private int userId;
	
	/**
	 * User database id.
	 */
	private long userDbId;
	
	/**
	 * Key this entry is registered with.
	 * User JID.
	 */
	private String key;
	
	/**
	 * Connection configuration.
	 * Used with aSMACK.
	 */
	private ConnectionConfiguration connectionConfiguration;
	
	/**
	 * Active XMPP connection.
	 */
	private AbstractXMPPConnection connection;
	
	/**
	 * XMPP roster listener.
	 */
	private UserRosterListener rosterListener;
	
	/**
	 * Profile at time of registration.
	 */
	private SipProfile profile;
	
	/**
	 * Connection establisher.
	 * Helps with initial connection & login. Not used after first
	 * successful connection was made.
	 */
	private XmppConnectionEstablisher connector;
	
	/**
	 * Connection ping manager.
	 */
	private PingManager pingManager;
	
	/**
	 * Informative connection listener.
	 */
	private CustomConnectionListener connectionListener;
	
	/**
	 * If reconnection is allowed in general.
	 */
	private boolean reconnectionAllowed=true;
	
	/**
	 * Roster is dumped after successful login into the application 
	 * to update presence of the buddies instantly.
	 * If true, roster dump was done.
	 */
	private boolean rosterDumpDone=false;
	
	/**
	 * Presence update waiting for connection being ready.
	 * Is send after authenticated. Then cleared.
	 */
	private Presence deferredPresence=null;
	
	/**
	 * Last presence updated sent over wire.
	 * Useful for retransmissions and connection tests if connection fails.
	 */
	private Presence lastPresence=null;

	public int getUserId() {
		return userId;
	}

	public XmppUserRecord setUserId(int userId) {
		this.userId = userId;
		return this;
	}

	public long getUserDbId() {
		return userDbId;
	}

	public XmppUserRecord setUserDbId(long userDbId) {
		this.userDbId = userDbId;
		return this;
	}

	public ConnectionConfiguration getConnectionConfiguration() {
		return connectionConfiguration;
	}

	public XmppUserRecord setConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
		this.connectionConfiguration = connectionConfiguration;
		return this;
	}

	public AbstractXMPPConnection getConnection() {
		return connection;
	}

	public XmppUserRecord setConnection(AbstractXMPPConnection connection) {
		this.connection = connection;
		return this;
	}

	public String getKey() {
		return key;
	}

	public XmppUserRecord setKey(String key) {
		this.key = key;
		return this;
	}

	public UserRosterListener getRosterListener() {
		return rosterListener;
	}

	public XmppUserRecord setRosterListener(UserRosterListener rosterListener) {
		this.rosterListener = rosterListener;
		return this;
	}

	public SipProfile getProfile() {
		return profile;
	}

	public XmppUserRecord setProfile(SipProfile profile) {
		this.profile = profile;
		return this;
	}

	public XmppConnectionEstablisher getConnector() {
		return connector;
	}

	public XmppUserRecord setConnector(XmppConnectionEstablisher connector) {
		this.connector = connector;
		return this;
	}

	public boolean isRosterDumpDone() {
		return rosterDumpDone;
	}

	public XmppUserRecord setRosterDumpDone(boolean rosterDumpDone) {
		this.rosterDumpDone = rosterDumpDone;
		return this;
	}

	public Presence getDeferredPresence() {
		return deferredPresence;
	}

	public XmppUserRecord setDeferredPresence(Presence deferredPresence) {
		this.deferredPresence = deferredPresence;
		return this;
	}

	public PingManager getPingManager() {
		return pingManager;
	}

	public XmppUserRecord setPingManager(PingManager pingManager) {
		this.pingManager = pingManager;
		return this;
	}

	public Presence getLastPresence() {
		return lastPresence;
	}
	
	public XmppUserRecord setLastPresence(Presence lastPresence) {
		this.lastPresence = lastPresence;
		return this;
	}
	
	public CustomConnectionListener getConnectionListener() {
		return connectionListener;
	}

	public XmppUserRecord setConnectionListener(CustomConnectionListener connectionListener) {
		this.connectionListener = connectionListener;
		return this;
	}

	public boolean isReconnectionAllowed() {
		return reconnectionAllowed;
	}

	public XmppUserRecord setReconnectionAllowed(boolean reconnectionAllowed) {
		this.reconnectionAllowed = reconnectionAllowed;
		return this;
	}	
	
}