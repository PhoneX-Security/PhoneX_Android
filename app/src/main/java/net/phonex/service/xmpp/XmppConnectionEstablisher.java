package net.phonex.service.xmpp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import net.phonex.db.entity.SipProfile;
import net.phonex.service.runEngine.MyWakeLock;
import net.phonex.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException.AlreadyLoggedInException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.packet.StreamError;

import java.util.Random;

/**
 * Class responsible for establishing an connection to the server 
 * and notifying of successful connection establishment & login.
 * 
 * Class also handles reconnection in case of a problem.
 * 
 * @author ph4r05
 *
 */
public class XmppConnectionEstablisher implements ConnectionListener {
	private static final String THIS_FILE = "XmppConnectionEstablisher";
	
	/**
	 * Manager responsible for this connection.
	 */
	final private XmppManager manager;
	
	/**
	 * User record storing current connection.
	 */
	final private XmppUserRecord rec;
	
	/**
	 * Holds the connection to the server
	 */
    final private AbstractXMPPConnection connection;
    
    /**
     * Random base for reconnection back-off.
     * Between 5 and 15 seconds
     */
    final private int randomBase = new Random().nextInt(7) + 5; 
    
    /**
     * Holds the state of the connection process.
     * If done, thread will be stopped.
     */
    private volatile boolean done = false;
    
    /**
     * Connection working thread.
     */
    private ConnectionThread reconnectionThread;
    
    /**
     * Wakelock for connection.
     */
    private final MyWakeLock wakeLock;

    /**
     * Connectivity manager for watching current network status.
     */
    private ConnectivityManager connectivityManager;

    /**
     * Login resource for XMPP connection identifying this device.
     */
    private final String loginResource;
    
    /**
     * Connection in rec has to be already initialized with configuration.
     * @param rec
     * @param mgr
     */
    public XmppConnectionEstablisher(XmppUserRecord rec, XmppManager mgr) {
    	this.rec = rec;
    	this.manager = mgr;
        this.connection = rec.getConnection();
        this.wakeLock = new MyWakeLock(mgr.getSvc().getApplicationContext(), "XMPPCE_" + rec.getUserDbId());
        this.loginResource = mgr.getXmppResourceString();
        
        // Register itself as a connection listener.
        this.connection.addConnectionListener(this);
        this.connectivityManager = (ConnectivityManager) mgr.getSvc().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Returns true if current network connection is valid.
     * @return
     */
    private boolean isConnected(){
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
        return ni!=null && ni.isConnected();
    }

    /**
     * Returns true if the reconnection mechanism is enabled.
     * Return false if done or already connected.
     *
     * @return true if automatic reconnections are allowed.
     */
    private boolean isReconnectionAllowed() {
        return !done 
        		&& connection!=null 
        		&& !connection.isConnected() 
        		&& rec.isReconnectionAllowed();
    }
    
    /**
     * True if process is not finished.
     * @return
     */
    private boolean isProcessUnfinished() {
        return !done 
        		&& connection!=null 
        		&& rec.isReconnectionAllowed() 
        		&& (!connection.isConnected() || !connection.isAuthenticated() || !connection.isSecureConnection());
    }
    
    /**
     * Returns true if reconnection thread is running.
     * @return
     */
    synchronized public boolean isReconnectionRunning(){
        if (reconnectionThread==null){
            return false;
        }

        if (reconnectionThread.isAlive()){
            return true;
        } else {
            reconnectionThread=null; // Cleanup.
            return false;
        }
    }
    
    /**
     * Fires listeners when a reconnection attempt has failed.
     *
     * @param exception the exception that occurred.
     */
    protected void notifyReconnectionFailed(Exception exception) {
        if (isReconnectionAllowed() && rec!=null && rec.getConnectionListener()!=null) {
        	rec.getConnectionListener().reconnectionFailed(exception);
        }
    }

    /**
     * Fires listeners when The XMPPConnection will retry a reconnection. Expressed in seconds.
     *
     * @param seconds the number of seconds that a reconnection will be attempted in.
     */
    protected void notifyAttemptToReconnectIn(int seconds) {
        if (isReconnectionAllowed() && rec!=null && rec.getConnectionListener()!=null) {
        	rec.getConnectionListener().reconnectingIn(seconds);
        }
    }

    /**
     * Starts a reconnection mechanism if it was configured to do that.
     * The algorithm is been executed when the first connection error is detected.
     * <p/>
     * The reconnection mechanism will try to reconnect periodically in this way:
     * <ol>
     * <li>First it will try 6 times every 10 seconds.
     * <li>Then it will try 10 times every 1 minute.
     * <li>Finally it will try indefinitely every 5 minutes.
     * </ol>
     */
    synchronized public void connectAndLogin() {
        if (this.isProcessUnfinished()) {
            // Since there is no thread running, creates a new one to attempt
            // the reconnection.
            // avoid to run duplicated reconnectionThread -- fd: 16/09/2010
            if (isReconnectionRunning()) {
            	return;
            }
            
            reconnectionThread = new ConnectionThread();
            reconnectionThread.setName("PhoneX Connection Establisher");
            reconnectionThread.setDaemon(true);
            reconnectionThread.start();
            Log.vf(THIS_FILE, "Connect&Login=%s", rec.getKey());
        }
    }
    
    /**
     * Updates user profile in the record.
     * If password has changed, this will be needed in login() step.
     * @param profile
     */
    public void updateProfile(SipProfile profile){
    	if (rec!=null){
    		rec.setProfile(profile);
    	}
    }
    
    /**
     * Terminate this object.
     */
    synchronized public void terminate(){
    	done=true;
    	Log.v(THIS_FILE, "Terminating");
    }
    
    /**
     * Implements ConnectionListener.
     * Stop establishing if connection was closed from outside.
     */
    @Override
    public void connectionClosed() {
    	done = true;
    	Log.vf(THIS_FILE, "Closed, connection: %s", connection);
    }
    
    @Override
    public void connectionClosedOnError(Exception e) {    	
        done = false;
        Log.v(THIS_FILE, "Connection closed on error");
        
        if (e instanceof StreamErrorException) {
            StreamErrorException xmppEx = (StreamErrorException) e;
            StreamError error = xmppEx.getStreamError();
            String reason = error.getCode();

            if ("conflict".equals(reason)) {
                return;
            }
        }

        if (this.isReconnectionAllowed()) {
            this.connectAndLogin();
        }
    }
    
    /**
     * Starts reconnection thread if not already running.
     * If running & resetRunning==true, then reconnection counters
     * (i.e., attempt counter) are reseted for faster reconnection 
     * (used when connectivity was recovered).
     * 
     * Check for connection validity is performed, if problem is found
     * done=false (can start reconnection again).
     * 
     * @param resetRunning
     */
    private void maybeStartReconnection(boolean resetRunning){
    	// If reconnection thread is running, do nothing.
    	if (isReconnectionRunning()){
    		Log.vf(THIS_FILE, "Reconnection already running, reset: %s, attempts: %s, state: %s",
                    resetRunning, reconnectionThread.attempts, reconnectionThread.state);

    		if (resetRunning){
    			reconnectionThread.reset();
    		}
    		return;
    	}
    	
    	// Reconnection thread is not running.
    	// If we are done, check if the connection is valid
    	// and if not, switch done to false.
    	if (connection==null || connection.isConnected()==false || connection.isAuthenticated()==false || connection.isSecureConnection()==false){
    		Log.v(THIS_FILE, "Process done, but connection is not valid, starting again");
    		done=false;
    	}
    	
    	// Standard check for reconnection. If allowed, continue.
    	if (isReconnectionAllowed()==false){
    		Log.vf(THIS_FILE, "Reconnection is not allowed. Done=%s, connNull=%s, isConnected=%s",
                    done, connection==null, connection==null ? "NULL" : connection.isConnected());
    		return;
    	}
    	
    	// Start new reconnection thread.
		Log.v(THIS_FILE, "Starting a new reconnection thread");
		connectAndLogin();
    }
    
    /**
     * Connectivity recovered.
     * Starts reconnection if not started.
     */
    synchronized public void connectivityOn(){
    	maybeStartReconnection(true);
    }
    
    /**
     * Checks connection, starts reconnection if it is 
     * needed.
     */
    synchronized public void startIfNeeded(){
    	maybeStartReconnection(false);
    }
    
    /**
     * Signal from upper layer that connectivity has changed.
     * @param connected
     */
    synchronized public void connectivityChanged(boolean connected){
    	if (connected){
    		connectivityOn();
    	}
    }
    
	@Override
	public void authenticated(XMPPConnection arg0) {
	}

	@Override
	public void connected(XMPPConnection arg0) {
	}

	@Override
	public void reconnectingIn(int arg0) {
	}

	/**
	 * Reconnection is in progress anyway. Stop this class.
	 */
	@Override
	public void reconnectionFailed(Exception arg0) {
		Log.vf(THIS_FILE, "Reconnection failed for connection: %s", connection);
	}

	/**
	 * Reconnection was successful, this class is not needed anymore.
	 */
	@Override
	public void reconnectionSuccessful() {
		Log.vf(THIS_FILE, "Reconnection successful for connection: %s", connection);
	}

	public boolean isDone() {
		return done;
	}
    
    /**
     * Main working thread for establishing a connection.
     * @author ph4r05
     */
    private class ConnectionThread extends Thread {
    	private static final int STATE_CONNECT=0;
    	private static final int STATE_LOGIN=1;
    	private static final int STATE_FINAL=2;
    	private static final int FAST_RECONNECT_COUNT = 3;
    	
    	/**
    	 * Current working state: 0=try to connect, 1=try to login, 2=update roster, done. 
    	 */
    	private int state=0;
    	
        /**
         * Holds the current number of reconnection attempts
         */
        private int attempts = 0;
        
        /**
         * Reflects change of connectivity from turned off to turned on.
         */
        private volatile boolean connectivityJustTurnedOn=false;

        /**
         * Returns the number of seconds until the next reconnection attempt.
         *
         * @return the number of seconds until the next reconnection attempt.
         */
        private long timeDelay() {
            attempts++;
            if (attempts > 15*FAST_RECONNECT_COUNT) {
            	if ((attempts % FAST_RECONNECT_COUNT)==0){
            		return randomBase*6*5;      // between 2.5 and 7.5 minutes (~5 minutes)
            	} else {
            		return randomBase;		    // fast reconnect.
            	}
            }
            if (attempts > 10) {
            	if ((attempts % FAST_RECONNECT_COUNT)==0){
            		return randomBase*6;       // between 30 and 90 seconds (~1 minute)
            	} else {
            		return randomBase;		   // fast reconnect.
            	}
            }
            if (attempts>5){ 
            	return randomBase;       // 10 seconds
            }
            
            // 0 seconds in the beginning. Thread just started.
            return 0;
        }
        
        /**
         * Resets internal state.
         * Calling on connectivity recovered for fast 
         * reconnecting.
         */
        public void reset(){
        	connectivityOn();
        	state=STATE_CONNECT;
        	attempts=0;
        }
        
        /**
         * Moves state, uses by outside connectionListener to reflect current changes to the connection. 
         * New state can be only bigger than previous one (no regression backward is allowed).
         */
        @SuppressWarnings("unused")
		public void moveState(int newState){
        	if (state>=STATE_FINAL) return;
        	if (state>=newState) return;
        	state=newState;
        }
        
        /**
         * Called when connectivity has been turned on.
         */
        public void connectivityOn(){
        	this.connectivityJustTurnedOn=true;
        }

        /**
         * The process will try the reconnection until the connection succeed or the user
         * cancel it
         */
        public void run() {
            // The process will try to reconnect until the connection is established or
            // the user cancel the reconnection process {@link XMPPConnection#disconnect()}
        	final long timeSleepTime = 500;
        	Log.v(THIS_FILE, "Establisher thread started.");
        	
            while (XmppConnectionEstablisher.this.isProcessUnfinished() && state != STATE_FINAL) {
                // Find how much time we should wait until the next reconnection
            	long remainingTicks = (long) Math.ceil(timeDelay() * (1000.0/timeSleepTime));
                int curState = state;

                // If not connected, finish. Wait for recovering a connectivity.
                if (!isConnected()){
                    Log.v(THIS_FILE, "Network not connected, stopping reconnection");
                    return;
                }

                // Switch to off to be able to detect on change.
                connectivityJustTurnedOn = false;
                
                // Sleep until we're ready for the next reconnection attempt. Notify
                // listeners once per second about how much time remains before the next
                // reconnection attempt.
                // If there is state transition, do not wait any longer.
                boolean wakelockSleep = attempts < 15*FAST_RECONNECT_COUNT;
                if (wakelockSleep){
                	wakeLock.lock("sleep");
                }

                // Sleeping while block.
                while (XmppConnectionEstablisher.this.isProcessUnfinished() 
                		&& remainingTicks > 0 
                		&& curState == state 
                		&& state != STATE_FINAL 
                		&& connectivityJustTurnedOn==false) 
                {
                    try {
                        Thread.sleep(timeSleepTime);
                        remainingTicks--;
                        XmppConnectionEstablisher.this.notifyAttemptToReconnectIn((int)(remainingTicks / (1000.0/timeSleepTime)));
                    }
                    catch (InterruptedException e1) {
                        Log.w(THIS_FILE, "Sleeping thread interrupted");
                    }
                }
                if (wakelockSleep){
                	wakeLock.unlock();
                }
                
                // If connectivity has been turned on, reduce number of attempts and reset 
                // the state according to the connection state.
                if (connectivityJustTurnedOn){
                	attempts = 0;
                	if (connection.isAuthenticated() && connection.isSecureConnection()){
                		state = STATE_FINAL;
                	} else if(connection.isConnected()){
                		state = STATE_LOGIN;
                	} else {
                		state = STATE_CONNECT;
                	}
                }

                // Make attempt depending on the situation.
                // Try to connect to the server.
                long prevTimeout = connection.getPacketReplyTimeout();
                if (state==STATE_CONNECT && XmppConnectionEstablisher.this.isReconnectionAllowed()){
                	wakeLock.lock("connect");
	                try {
	                	Log.vf(THIS_FILE, "Calling connection.connect(), attempt=%d", attempts);
	                	connection.setPacketReplyTimeout(10000);
                        connection.connect();
                        
                        // Move internal state to login.
                        if (connection.isConnected()){
                        	Log.vf(THIS_FILE, "Suceeded to connect @ %s; secure=%s", connection, connection.isSecureConnection());
                        	state=STATE_LOGIN;
                        }
	                }
	                catch (Exception e) {
	                    // Fires the failed reconnection notification
	                	Log.d(THIS_FILE, "Connect(): Connection throws exception, but I am not stopping...", e);
	                	XmppConnectionEstablisher.this.notifyReconnectionFailed(e);
	                }
	                
	                connection.setPacketReplyTimeout(prevTimeout);
	                wakeLock.unlock();
                }
                
                // If in login state, try to log in. 
                // This state might be switched right after connection, or after back-off.
                // Connection has to be created and secure.
                if (state==STATE_LOGIN && XmppConnectionEstablisher.this.isProcessUnfinished())
                {
                	wakeLock.lock("login");
                	try {
                		final SipProfile profile = XmppConnectionEstablisher.this.rec.getProfile();
                		connection.setPacketReplyTimeout(7000);
                        connection.login(profile.getXmpp_user(), profile.getXmpp_password(), loginResource);
                        
                        // Move internal state to finished
                        if (connection.isAuthenticated() && connection.isSecureConnection()){
                        	Log.vf(THIS_FILE, "Succeeded to login @ %s", connection);
                        	state=STATE_FINAL;
                        }
	                } catch(AlreadyLoggedInException ale){
	                	// This kind of exception is pleasant, since we are done here.
	                	if (connection.isSecureConnection()){
	                		state=STATE_FINAL;
	                	} else {
	                		state=STATE_CONNECT;
	                	}
	                } catch(NotConnectedException nce){
	                	// Not connected? Weird, so roll back to connect state.
	                	Log.d(THIS_FILE, "Exception, not connected during login().");
	                	state=STATE_CONNECT;
	                } catch (Exception e) {
	                    // Some general login exception, try again then...
	                	Log.d(THIS_FILE, "login(): Connection throws exception, but I am not stopping...", e);
	                }
                	
                	connection.setPacketReplyTimeout(prevTimeout);
                	wakeLock.unlock();
                }
                
                // In final state just break this loop.
                if (state==STATE_FINAL || !XmppConnectionEstablisher.this.isProcessUnfinished()){
                	Log.v(THIS_FILE, "Already logged in/finished");
                	break;
                }
            }
            
            Log.vf(THIS_FILE, "ConnectEstablishment finished. done=%s; con=%s", done, connection);
            
            // If connection is connected & authenticated, signalize this to the manager.
            if (!done && connection != null
            		&& connection.isConnected() 
            		&& connection.isAuthenticated()
            		&& connection.isSecureConnection())
            {
            	wakeLock.lock("onAuthenticated");
            	try {
            		Log.v(THIS_FILE, "In finished state, going to notify manager");
            		manager.onAuthenticated(rec);
            	} catch(Exception e){
            		Log.e(THIS_FILE, "Exception in connection thread", e);
            	}
            	wakeLock.unlock();
            }
        }
    }	
}
