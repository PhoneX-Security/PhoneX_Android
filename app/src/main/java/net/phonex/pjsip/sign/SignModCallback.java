package net.phonex.pjsip.sign;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.pref.PhonexConfig;
import net.phonex.core.SipUri;
import net.phonex.db.entity.SipSignatureWarning;
import net.phonex.db.entity.UserCertificate;
import net.phonex.pub.a.PjManager;
import net.phonex.service.runEngine.AppWakeLock;
import net.phonex.service.runEngine.MyWakeLock;
import net.phonex.sip.PjCallback;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.android.StatusbarNotifications;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.KeyPairGenerator;

import net.phonex.xv.SignCallback;
import net.phonex.xv.Xvi;
import net.phonex.xv.esignInfo_t;
import net.phonex.xv.esign_sign_err_e;
import net.phonex.xv.hashReturn_t;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.XviConstants;

import org.spongycastle.util.encoders.Base64;

import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;


/**
 * Module for asymmetrical digital message signatures & verification.
 * This part of module is callback that belongs to pjsip_mod_sign.
 * 
 * At the moment, module sits just above transport layer, thus messages
 * being sent are passing through this module just before sending
 * (in order to avoid any further modifications making signature invalid),
 * incoming messages are validated as they are received by transport layer,
 * enabling to drop invalid messages.
 * 
 * pjsip_mod_sign already pre-process messages by parsing some key 
 * header fields in order to determine sender and recipient (to know
 * which private key to use to sing for sent messages or which 
 * certificate to use for verification for incoming messages).
 * 
 * @author ph4r05
 *
 */
public class SignModCallback extends SignCallback {
	private static final String THIS_FILE  = "SignModCallback";
	private static final String SIGN_DESC  = "SHA256withRSA";   // SHA256withRSA
	private static final String SIGN_CHSET = "UTF-8";
	private static final String EXECUTOR_NAME = "ModSignature.Handler";
	
    @SuppressWarnings("unused")
	private SharedPreferences prefs_db;
    private Context mCtxt = null;
    
    private PjManager pjService;
	private StatusbarNotifications notificationManager;
    @SuppressWarnings("unused")
	private PjCallback pjCb;
    
    private static WorkerHandler msgHandler;
    private static HandlerThread handlerThread;
    private        AppWakeLock   wakeLock;
    private        MyWakeLock    wlock;
    
    private KeyPairGenerator kpg    = null;
    private String ksPath           = null;
    private String ksPasswd         = null;
    private KeyStore ks             = null;
    private PrivateKey myPrivateKey = null;
    private Signature mySign        = null;
    
    private final Handler mHandler = new Handler();
    private CertificateContentObserver certObserver = null;
    
    private HashMap<String, CertRec> certCache = new HashMap<String, CertRec>();
    
    /**
     * Represents inserted warnings before last cleanup.
     */
    @SuppressWarnings("unused")
	private long insertedWarnings = 0;
    @SuppressWarnings("unused")
	private long lastCleanupTime  = 0;
	
    private class CertRec{
    	public int status = 0;
    	public X509Certificate cert = null;
    }
    
    public SignModCallback(Context ctxt) {
        mCtxt    = ctxt;
        prefs_db = ctxt.getSharedPreferences("sign_db", Context.MODE_PRIVATE);
        kpg      = new KeyPairGenerator();
        certCache.clear();
    }
    
    public void setContext(Context ctxt){
    	mCtxt = ctxt;
    }

    public void initService(PjManager srv) {
        pjService = srv;
        notificationManager = pjService.getService().getNotificationManager();
        pjCb = pjService.getPjCallback();
        wakeLock = new AppWakeLock((PowerManager) srv.getService().getSystemService(Context.POWER_SERVICE), "net.phonex.signmod");
        wlock    = new MyWakeLock(srv.getService(), "net.phonex.signmodlock", true);

        if (handlerThread == null) {
            handlerThread = new HandlerThread(EXECUTOR_NAME);
            handlerThread.start();
        }
        if (msgHandler == null) {
            msgHandler = new WorkerHandler(handlerThread.getLooper(), this);
        }
        
        // Register content observer for certificates -> update CertCache on change
        if(certObserver == null && srv.getService() != null) {
        	certObserver = new CertificateContentObserver(mHandler);
        	srv.getService().getContentResolver().registerContentObserver(UserCertificate.CERTIFICATE_URI, true, certObserver);
        }
    }
    
    public void deinit(){
    	if(certObserver != null && pjService != null && pjService.getService() != null) {
    		pjService.getService().getContentResolver().unregisterContentObserver(certObserver);
        }
    	
    	// Reset wake lock.
    	if (wakeLock!=null){
    		try {
    			wakeLock.reset();
    		} catch(Exception e){
    			Log.e(THIS_FILE, "Exception in wlock deinit", e);
    		}
    	}
    	
    	// De-init wlock.
    	if (wlock!=null){
    		try {
    			wlock.deinit();
    		} catch(Exception e){
    			Log.e(THIS_FILE, "Exception in wlock deinit", e);
    		}
    	}
    	
    	try {
    		stopService();
    	} catch(Exception e){
    		Log.e(THIS_FILE, "Exception in stopService()", e);
    	}
    }

    /**
     * Stops running background threads.
     */
    public void stopService() {
    	if (handlerThread!=null){
    		MiscUtils.stopHandlerThread(handlerThread, true);
    		handlerThread = null;
    	}
        msgHandler = null;
    }
    
    /**
     * Initializes signing of outgoing messages. 
     * Key store containing private key and password to 
     * unlock are is passed as parameter.
     * 
     * @param keyStore
     * @param password
     * @throws Exception 
     */
    public synchronized int initSignatures(String keyStore, String password) throws Exception{
		Exception ex = null;
		try {
			certCache.clear();
			this.ksPath       = keyStore;
			this.ksPasswd     = password;
			this.ks           = kpg.readKeyStore(keyStore, mCtxt, password.toCharArray());
			this.myPrivateKey = (PrivateKey) ks.getKey(CertificatesAndKeys.DEFAULT_KEY_ALIAS, password.toCharArray());
			this.mySign       = Signature.getInstance(SIGN_DESC);
			
			Log.inf(THIS_FILE, "Signatures initialized from keystore: %s; ISNULL(pk) = %s; class: %s", 
			        keyStore, 
			        ((myPrivateKey) == (null)), 
			        this.toString());
			return 0;
		} catch(Exception e){
			ks           = null;
			myPrivateKey = null;
			
			Log.e(THIS_FILE, "Exception during signature initialization", e);
			ex=e;
		}
		
		throw ex;
    }
    
    /**
     * Flush certificate cache
     */
    public synchronized void flushCache(){
    	this.certCache.clear();
    }
    
	@Override
	public synchronized int sign(esignInfo_t sdata, hashReturn_t hash) {
		wlock.lock();
		
		int cseqInt    = sdata.getCseqInt();
		String method  = getString(sdata.getMethod());
		String reqUri  = getString(sdata.getReqUriStr());
		String fromUri = getString(sdata.getFromUriStr());
		String toUri   = getString(sdata.getToUriStr());
		String toSign  = getString(sdata.getAccumStr());
		String digest  = getString(sdata.getAccumSha256Str());
		
		// Logic part - do something...
		Log.df(THIS_FILE, "Sign callback called; cseq[%s] method[%s] reqUri[%s] fromUri[%s] toUri[%s] digest[%s]", 
		        cseqInt, 
		        method, 
		        reqUri, 
		        fromUri, 
		        toUri, 
		        digest);
		Log.df(THIS_FILE, "Sign callback called; toSign: <<<EOF\n%s\nEOF;", toSign);
		
		// return in hash structure - pass multiple values back
		String tmpHash = "0000xxxx0000";
		String tmpDesc = "v1;"+SIGN_DESC;
		
		// load private key of logged user somehow to this class
		// https://www.owasp.org/index.php/Digital_Signature_Implementation_in_Java
		try {
			Log.df(THIS_FILE, "ISNULL(pk)=%s; class: %s", ((this.myPrivateKey) == (null)), this.toString());
			
			// try to initialize private key if null, load private key from key store
			if (this.myPrivateKey==null && this.ksPath!=null && this.ksPasswd!=null){
				this.ks           = kpg.readKeyStore(this.ksPath, mCtxt, this.ksPasswd.toCharArray());
				this.myPrivateKey = (PrivateKey) ks.getKey(CertificatesAndKeys.DEFAULT_KEY_ALIAS, this.ksPasswd.toCharArray());
				this.mySign       = Signature.getInstance(SIGN_DESC);
				Log.d(THIS_FILE, "Private key loaded from key store");
			}
			
			// signature itself + time measurement
			long sigStart = System.currentTimeMillis();
			if (myPrivateKey!=null){ 
				mySign.initSign(this.myPrivateKey);
				mySign.update(digest.getBytes(SIGN_CHSET));
				byte[] byteSignedData = mySign.sign();
				tmpHash = new String(Base64.encode(byteSignedData), SIGN_CHSET);
				Log.df(THIS_FILE, "Signature generated: %s; it took: %s ms;", tmpHash, ((System.currentTimeMillis()) - (sigStart)));
			} else {
				Log.d(THIS_FILE, "Cannot sign packet, privateKey is null");
			}
		} catch (Exception e) {
			Log.e(THIS_FILE, "Exception during signature generation", e);
		}
		
		pj_str_t hashRet = Xvi.pj_str_copy(tmpHash);
		pj_str_t descRet = Xvi.pj_str_copy(tmpDesc);
		
		hash.setErrCode(0);
		hash.setRetStatus(0);
		hash.setHash(hashRet);
		hash.setDesc(descRet);
		
		wlock.unlock();
		return 0;
	}
	
	@Override
	public synchronized int verifySign(esignInfo_t sdata, String sign, String desc) {
		wlock.lock();
		
		int cseqInt      = sdata.getCseqInt();
		String method    = getString(sdata.getMethod()).toUpperCase().trim();
		String reqUri    = getString(sdata.getReqUriStr());
		String fromUri   = getString(sdata.getFromUriStr());
		String toUri     = getString(sdata.getToUriStr());
		String toSign    = getString(sdata.getAccumStr());
		String digest    = getString(sdata.getAccumSha256Str());
		String ip        = getString(sdata.getIp());
		boolean drop     = false;
		boolean doDrop   = false;
		boolean doWarn   = pjService.getService().getPrefs().getBoolean(PhonexConfig.SHOW_SIPSIG_WARNINGS, false);
		int dropFlag     = 0;
		int returnVal    = esign_sign_err_e.ESIGN_SIGN_ERR_GENERIC.swigValue();
		String remoteSip = SipUri.getCanonicalSipContact(sdata.getIsRequest() > 0 ? fromUri : toUri, false);
		
		if ("INVITE".equalsIgnoreCase(method)){
			drop = pjService.getService().getPrefs().getBoolean(PhonexConfig.DROP_BAD_INVITE, false);
		} else if ("BYE".equalsIgnoreCase(method)){
			drop = pjService.getService().getPrefs().getBoolean(PhonexConfig.DROP_BAD_BYE, false);
		}
		if (drop) dropFlag = XviConstants.EESIGN_FLAG_DROP_PACKET;
		
		Log.df(THIS_FILE, "SignVerify callback called; cseq[%s] method[%s] reqUri[%s] fromUri[%s] toUri[%s] digest[%s] ip[%s]", 
		        cseqInt, 
		        method, 
		        reqUri, 
		        fromUri, 
		        toUri, 
		        digest, 
		        ip);
		Log.df(THIS_FILE, "SignVerify callback called; EESIGN[%s] EEDESC[%s]", sign, desc);
		Log.df(THIS_FILE, "SignVerify callback called; toSign: <<<EOF\n%s\nEOF;", toSign);
		
		// if no signature -> return 1, nothing to verify, show warning!
		if (sign==null || sign.length()==0){
			wlock.unlock();
			return esign_sign_err_e.ESIGN_SIGN_ERR_GENERIC.swigValue();
		}
		
		String tmpHash = "0000xxxx0000";
		try {
			tmpHash = digest;
			Log.df(THIS_FILE, "Computed hash on my side[%s]; remote sip[%s]", tmpHash, remoteSip);
			
			// certificate is locally cached to avoid SQLite queries
			CertRec rec = null;
			
			// cache miss
			if (!certCache.containsKey(remoteSip)){
				rec = new CertRec();
				
				// load public key of the remote peer
	        	UserCertificate sipRemoteCert = CertificatesAndKeys.getRemoteCertificate(mCtxt, remoteSip);
	        	if (sipRemoteCert==null) 
	        		rec.status=5;
	        	else if (sipRemoteCert.getCertificateStatus() != UserCertificate.CERTIFICATE_STATUS_OK)
	        		rec.status=6;
	        	else {
	        		rec.status = 0;
	        		rec.cert   = sipRemoteCert.getCertificateObj();
	        	}
	        	
	        	certCache.put(remoteSip, rec);
	        	Log.df(THIS_FILE, "Cache miss detected for [%s] in verify", remoteSip);
			} else {
				rec = certCache.get(remoteSip);
			}
	        
			// Certificate & Signature verification block 
			do {
				// Certificate validity check
		        if (rec.status==5){
		        	Log.wf(THIS_FILE, "Certificate for sip [%s] is missing, cannot verify signature", remoteSip);
		        	returnVal = esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_CERT_MISSING.swigValue();
		        	doDrop    = true;
		        	break;
		        	
		        } else if (rec.status==6){
		        	Log.wf(THIS_FILE, "Certificate for sip [%s] is not OK, cannot verify signature", remoteSip);
		        	returnVal = esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_CERT_ERR.swigValue();
		        	doDrop    = true;
		        	break;
		        	
		        }
		        if (rec.cert==null){
		        	Log.wf(THIS_FILE, "Certificate is null for [%s]", remoteSip);
		        	returnVal = esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_CERT_ERR.swigValue();
		        	doDrop    = true;
		        	break;
		        	
		        }
	        	
	        	// 6.2 Verifying the Signature
		        long sigStart = System.currentTimeMillis();
	        	X509Certificate remoteCert = rec.cert;
	        	PublicKey pubKeySender = remoteCert.getPublicKey();
	    	    Signature myVerifySign = Signature.getInstance(SIGN_DESC);
	    	    myVerifySign.initVerify(pubKeySender);
	    	    myVerifySign.update(tmpHash.getBytes(SIGN_CHSET));
	    	    
	    	    byte[] byteSignedData = Base64.decode(sign.getBytes(SIGN_CHSET));
	    	    boolean verifySign    = myVerifySign.verify(byteSignedData);
	    	    
	    	    // Use notification / intents to inform user about result of signature check
	    	    // DropPacket ? pjsuaConstants.EESIGN_FLAG_DROP_PACKET
	        	if (verifySign){
	        		Log.inf(THIS_FILE, "Signature is OK!; it took %s ms", ((System.currentTimeMillis()) - (sigStart)));
	        		returnVal = esign_sign_err_e.ESIGN_SIGN_ERR_SUCCESS.swigValue();
	        		break;
	        		
	        	} else {
	        		Log.wf(THIS_FILE, "Signature is NOT VALID!; it took %s ms", ((System.currentTimeMillis()) - (sigStart)));
	        		Log.df(THIS_FILE, "PublicKey used for verification: [%s]; certificate[%s]", pubKeySender, remoteCert);
	        		returnVal = esign_sign_err_e.ESIGN_SIGN_ERR_SIGNATURE_INVALID.swigValue();
	        		doDrop    = true;
	        		break;
	        		
	        	}
			} while(false);
			
		} catch(Exception e){
			Log.e(THIS_FILE, "Problem with hashing & signature", e);
		}
		
		// Should we warn user?
		Log.vf(THIS_FILE, "Going to show signature error, warn=%s; drop=%s", doWarn, doDrop);
		
		if (doWarn && doDrop){
			String error = this.getToastError(returnVal, remoteSip, ip);
			getExecutor().execute(new ToastDatabaseWarningJob(sdata, drop, error, returnVal, this), 0);
			//getHandler().execute(new ToastWarningJob(error, this), 0);
		}
		
		wlock.unlock();
		return doDrop ? (returnVal | dropFlag) : returnVal;
	}
	
	public static String getSignatureError(Context mCtxt, int code, String text){
		String toReturn = ""; 
		try {
			if (       code == 5){//esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_CERT_MISSING.ordinal()){
				toReturn = mCtxt.getString(R.string.modsign_err_user_cert_missing, text);
			} else if (code == 6){//esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_CERT_ERR.ordinal()){
				toReturn = mCtxt.getString(R.string.modsign_err_user_cert_invalid, text);
			} else if (code == 7){//esign_sign_err_e.ESIGN_SIGN_ERR_SIGNATURE_INVALID.ordinal()){
				toReturn = mCtxt.getString(R.string.modsign_err_signature_invalid, text);
			} else if (code == 4){// esign_sign_err_e.ESIGN_SIGN_ERR_REMOTE_USER_UNKNOWN.ordinal()){
				toReturn = mCtxt.getString(R.string.modsign_err_remote_user_unknown, text);
			} else if (code == 0){//esign_sign_err_e.ESIGN_SIGN_ERR_SUCCESS.ordinal()){
				toReturn = mCtxt.getString(R.string.modsign_err_success);
			} else {
				toReturn = mCtxt.getString(R.string.modsign_err_generic, text);
			}
		} catch(Exception e){
			Log.e(THIS_FILE, "Exception in JNI ENUM int conversion", e);
		}
		
		return toReturn;
	}
	
	public String getToastError(int code, String user, String ip){
		return getSignatureError(mCtxt, code, user + ";" + ip);
	}
	
	public void notifyAbout(SipSignatureWarning warn){
		if (notificationManager!=null){
			notificationManager.notifyWarning(warn);
		}
	}
	
	 private static Looper createLooper() {
        if (handlerThread == null) {
            Log.df(THIS_FILE, "Creating new HandlerThread [%s]", EXECUTOR_NAME);
            handlerThread = new HandlerThread(EXECUTOR_NAME);
            handlerThread.start();
        }
        return handlerThread.getLooper();
    }
    
    private WorkerHandler getExecutor() {
        if (msgHandler == null) {
        	msgHandler = new WorkerHandler(createLooper(), this);
            Log.i(THIS_FILE, "Handler was null, creating new WorkerHandler");
        }
        return msgHandler;
    }

	private static class WorkerHandler extends Handler {
        WeakReference<SignModCallback> sr;

        public WorkerHandler(Looper looper, SignModCallback stateReceiver) {
            super(looper);
            Log.d(THIS_FILE, "Create async worker");
            sr = new WeakReference<SignModCallback>(stateReceiver);
        }
        
        public void execute(Runnable task, int what) {
        	SignModCallback m = sr.get();
        	if (m!=null){
        		m.wakeLock.lock(task);
        	}
        	
            Message.obtain(this, what, task).sendToTarget();
        }

        public void handleMessage(Message msg) {
        	SignModCallback stateReceiver = sr.get();
            if (stateReceiver == null) {
            	// weak reference probably does not exist anymore (was garbage collected)
                return;
            }
            
            // simple message handler
            if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.wf(THIS_FILE, "can't handle msg: %s", msg);
            }
        }
        
	    private void executeInternal(Runnable task) {
	        try {
	            task.run();
	        } catch (Throwable t) {
	            Log.ef(THIS_FILE, t, "run task: %s", task);
	        } finally {
	        	SignModCallback m = sr.get();
	        	if (m!=null){
	        		m.wakeLock.unlock(task);
	        	}
	        }
	    }
    };
    
    /**
     * Simple runnable class that produces Toast Warnings. 
     *  
     * @author ph4r05
     */
    @SuppressWarnings("unused")
	private class ToastWarningJob implements Runnable {
		private final String error;
		private final WeakReference<SignModCallback> sr;
		
		public ToastWarningJob(String error, SignModCallback sr){
			this.sr = new WeakReference<SignModCallback>(sr);
			this.error = error;
			wakeLock.lock(this);
		}
		
		@Override
		public void run() {
			try {
				SignModCallback srr = this.sr.get();
				if (srr==null){
					return;
				}
				
				Toast toast = Toast.makeText(srr.mCtxt, error, Toast.LENGTH_SHORT);
				toast.show();
				
			}catch(Exception e) {
				Log.e(THIS_FILE, "Native error ", e);
			}finally {
				wakeLock.unlock(this);
			}
		}
	}
    
    /**
     * Simple runnable class that produces Toast Warnings + adds new entry to the database 
     *  
     * @author ph4r05
     */
    private class ToastDatabaseWarningJob implements Runnable {
		private final String error;
		private final int errorCode;
		private final WeakReference<SignModCallback> sr;
		private final boolean dropped;
		private final int cseqInt;
		private final String cseq;
		private final String method;
		private final String reqUri;
		private final String fromUri;
		private final String toUri;
		private final String ip;
		private final boolean isReq;
		private final int respCode;
		
		public ToastDatabaseWarningJob(esignInfo_t sdata, boolean dropped, String error, int errorCode, SignModCallback sr){
			this.sr       = new WeakReference<>(sr);
			this.error    = error;
			this.errorCode = errorCode;
			this.dropped  = dropped;
			this.cseqInt  = sdata.getCseqInt();
			this.cseq     = getString(sdata.getCseqStr());
			this.method   = getString(sdata.getMethod()).toUpperCase().trim();
			this.reqUri   = getString(sdata.getReqUriStr());
			this.fromUri  = getString(sdata.getFromUriStr());
			this.toUri    = getString(sdata.getToUriStr());
			this.ip       = getString(sdata.getIp()); 
			this.isReq    = sdata.getIsRequest() != 0;
			this.respCode = sdata.getResp_status();
			
			wakeLock.lock(this);
		}
		
		@Override
		public void run() {
			SignModCallback srr = this.sr.get();
			if (srr==null){
				wakeLock.unlock(this);
				return;
			}
			
			try {
				// Create new warning object
				SipSignatureWarning warn = new SipSignatureWarning();
				warn.setCseq(cseq);
				warn.setCseqNum(cseqInt);
				warn.setDateCreated(new Date());
				warn.setDateLast(new Date());
				warn.setDropped(dropped);
				warn.setErrorCode(errorCode);
				warn.setFromURI(fromUri);
				warn.setMethod(method);
				warn.setReq(isReq);
				warn.setReqURI(reqUri);
				warn.setRespCode(respCode);
				warn.setToURI(toUri);
				warn.setSrcIP(ip);
				
				// IP address vs. port split
				try {
					if (ip.contains(":")){
						String ipa[] = ip.split(":", 2);
						warn.setSrcIP(ipa[0]);
						warn.setSrcPort(Integer.valueOf(ipa[1]));
					}
				} catch(Exception e){
					Log.w(THIS_FILE, "Problem with IP:port separation");
				}
				
				// Insert warning to the database
				ContentResolver cr = srr.pjService.getService().getContentResolver();
				cr.insert(SipSignatureWarning.SIGNATURE_WARNING_URI, warn.getDbContentValues());
				Log.vf(THIS_FILE, "Stored warn object to database; detail=[%s]", warn.toString());
				
				// Finally show some toast
				Toast toast = Toast.makeText(srr.mCtxt, error, Toast.LENGTH_LONG);
				toast.show();
				
				// And some notification for the user that may miss toast
				srr.notifyAbout(warn);
				
				Log.v(THIS_FILE, "Toast posted, job finished");
			}catch(Exception e) {
				Log.e(THIS_FILE, "Native error ", e);
			}finally {
				wakeLock.unlock(this);
			}
		}
	}
    
    /**
     * Observer for changes of certificate database
     */
    class CertificateContentObserver extends ContentObserver {

        public CertificateContentObserver(Handler h) {
            super(h);
        }

        public void onChange(boolean selfChange) {
            Log.d(THIS_FILE, "Certificate change detected, going to flush cache");
            flushCache();
        }
    }
    
    public static String getString(pj_str_t str, boolean nullAllowed){
    	if (str==null){
    		return nullAllowed ? null : "";
    	}
    	
    	final String ptr = str.getPtr();
    	if (ptr==null){
    		return nullAllowed ? null : "";
    	}
    	if (ptr.length()==0) return "";
    	
    	return new String(ptr);
    }
    
    public static String getString(pj_str_t str){
    	return getString(str, false);
    }
}
