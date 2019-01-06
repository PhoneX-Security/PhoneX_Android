package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import net.phonex.core.Constants;
import net.phonex.core.SipUri;
import net.phonex.db.DBProvider;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * Holder for Sip Contact list
 * @author ph4r05
 *
 */
public class SipClist implements Parcelable {

    /**
     * Primary key id.
     * 
     * @see Long
     */
    public static final String FIELD_ID = "_id";
    public static final String FIELD_ACCOUNT = "account";
    public static final String FIELD_SIP = "sip";
    public static final String FIELD_DISPLAY_NAME = "name";
    public static final String FIELD_CERTIFICATE = "certificate";
    public static final String FIELD_CERTIFICATE_HASH = "certificateHash";
    public static final String FIELD_IN_WHITELIST = "inWhitelist";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_LAST_CHANGE = "dateLastChange";
    public static final String FIELD_PRESENCE_ONLINE = "presenceOnline";
    
    /**
     * Textual description of the status.
     * @obsolete
     */
    public static final String FIELD_PRESENCE_STATUS = "presenceStatus";
    
    /**
     * Last update of the presence information. 
     */
    public static final String FIELD_PRESENCE_LAST_UPDATE = "presenceLastUpdate";
    
    /**
     * Presence status type (online/offline, away, DND, oncall, ...).
     * Uses protocol buffers enum State.
     */
    public static final String FIELD_PRESENCE_STATUS_TYPE = "presenceStatusType";
    
    /**
     * Presence custom text provided by user. 
     * Uses protocol buffers.
     */
    public static final String FIELD_PRESENCE_STATUS_TEXT = "presenceStatusText";
    
    /**
     * Prefix of the certificate hash provided by presence push notification.
     * Serves mainly to signalize certificate change. 
     */
    public static final String FIELD_PRESENCE_CERT_HASH_PREFIX = "presenceCertHashPrefix";
    
    /**
     * Certificate not before field (start of the validity of the certificate).
     */
    public static final String FIELD_PRESENCE_CERT_NOT_BEFORE = "presenceCertNotBefore";
    
    /**
     * Last certificate update for this contact caused by presence push message.
     * May be used to block too-often presence certificate updates (e.g., one in 5 minute interval). 
     */
    public static final String FIELD_PRESENCE_LAST_CERT_UPDATE = "presenceLastCertUpdate";
    
    /**
     * Number of certificate updates in the day(FIELD_PRESENCE_LAST_CERT_UPDATE).
     * May be used to block too-often presence certificate updates (e.g., 10 in one day).
     */
    public static final String FIELD_PRESENCE_NUM_CERT_UPDATE = "presenceNumCertUpdate";
    
    /**
     * Anti-DOS field for the presence caused certificate update.
     * May be used by CUSUM (cumulative sum http://www.ist-scampi.org/publications/papers/siris-globecom2004.pdf)
     * or by Adaptive threshold algorithm. 
     * For future extensions.
     */
    public static final String FIELD_PRESENCE_DOS_CERT_UPDATE = "presenceDosCertUpdate";
    
    
    public static final String FIELD_UNREAD_MESSAGES = "unreadMessages";
    
    /**
     * Semicolon separated list of capabilities the user supports.
     * Example: ";1.2.0;1.3.2;1.3.3;1.3.3.3;"
     */
    public static final String FIELD_CAPABILITIES = "capabilities";
    
    public static final String SIP_CONTACTLIST_TABLE = "clist";
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public final static Integer INVALID_ID = -1;
    
    public static final String[] FULL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_ACCOUNT, FIELD_SIP, FIELD_DISPLAY_NAME,
    	FIELD_CERTIFICATE, FIELD_CERTIFICATE_HASH,
    	FIELD_IN_WHITELIST, FIELD_DATE_CREATED, FIELD_DATE_LAST_CHANGE,
    	FIELD_PRESENCE_ONLINE, FIELD_PRESENCE_STATUS, FIELD_PRESENCE_LAST_UPDATE,
    	FIELD_PRESENCE_STATUS_TYPE, FIELD_PRESENCE_STATUS_TEXT, FIELD_PRESENCE_CERT_HASH_PREFIX,
    	FIELD_PRESENCE_CERT_NOT_BEFORE,
    	FIELD_PRESENCE_LAST_CERT_UPDATE, FIELD_PRESENCE_NUM_CERT_UPDATE, FIELD_PRESENCE_NUM_CERT_UPDATE,
    	FIELD_CAPABILITIES
    	
    };
    
    public static final String[] NORMAL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_ACCOUNT, FIELD_SIP, FIELD_DISPLAY_NAME,
    	FIELD_CERTIFICATE_HASH,
    	FIELD_IN_WHITELIST, FIELD_DATE_CREATED, FIELD_DATE_LAST_CHANGE,
    	FIELD_PRESENCE_ONLINE, FIELD_PRESENCE_STATUS, FIELD_PRESENCE_LAST_UPDATE,
    	FIELD_PRESENCE_STATUS_TYPE, FIELD_PRESENCE_STATUS_TEXT, FIELD_PRESENCE_CERT_HASH_PREFIX,
    	FIELD_PRESENCE_CERT_NOT_BEFORE,
    	FIELD_PRESENCE_LAST_CERT_UPDATE, FIELD_PRESENCE_NUM_CERT_UPDATE, FIELD_PRESENCE_NUM_CERT_UPDATE
    };
    
    public static final String[] LIGHT_PROJECTION = new String[] {
    	FIELD_ID, FIELD_ACCOUNT, FIELD_SIP, FIELD_DISPLAY_NAME, FIELD_PRESENCE_ONLINE, FIELD_PRESENCE_STATUS, FIELD_PRESENCE_STATUS_TEXT, FIELD_PRESENCE_STATUS_TYPE
    };

    public static final String[] CONTACT_LIST_PROJECTION = new String[] {
            FIELD_ID, FIELD_ACCOUNT, FIELD_SIP, FIELD_DISPLAY_NAME, FIELD_PRESENCE_ONLINE, FIELD_PRESENCE_STATUS, FIELD_PRESENCE_STATUS_TEXT, FIELD_PRESENCE_STATUS_TYPE
    };
    
    /**
     * Uri for content provider of contact
     */
    public static final Uri CLIST_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + SIP_CONTACTLIST_TABLE);
    /**
     * Base uri for contact content provider.<br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri CLIST_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + SIP_CONTACTLIST_TABLE + "/");

    /**
     * Uri for content provider of contact list element presence state.
     * This just new view to contactlist, data is contained in contact list table. 
     * It is very similar to SipProfileState. It has own contectObserver, but behaves quite same as ContactList.
     * 
     * Can be later implemented as MemoryMap, as SipProfileStatus is.
     * 
     * CLIST_STATUS * = CLIST_* + CLIST_STATUS,
     * meaning it is normal CLIST + STATUS information. To this can be registered loader interested
     * in both contact list elements and its presence data.
     */
    public final static String CLIST_STATUS_TABLE_NAME = "clist_status";
    public static final Uri CLIST_STATE_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + CLIST_STATUS_TABLE_NAME);
    public static final Uri CLIST_STATE_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + CLIST_STATUS_TABLE_NAME + "/");

    // SQL Create command for contact list table.
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + SipClist.SIP_CONTACTLIST_TABLE
            + " ("
            + SipClist.FIELD_ID 				+ " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + SipClist.FIELD_ACCOUNT 			+ " INTEGER, "
            + SipClist.FIELD_SIP 				+ " TEXT, "
            + SipClist.FIELD_DISPLAY_NAME 		+ " TEXT, "
            + SipClist.FIELD_IN_WHITELIST 		+ " INTEGER, "
            + SipClist.FIELD_CERTIFICATE 		+ " BLOB, "
            + SipClist.FIELD_CERTIFICATE_HASH	+ " TEXT, "
            + SipClist.FIELD_DATE_CREATED		+ " TEXT, "
            + SipClist.FIELD_DATE_LAST_CHANGE	+ " TEXT, "
            + SipClist.FIELD_PRESENCE_ONLINE	+ " INTEGER, "
            + SipClist.FIELD_PRESENCE_STATUS		+ " TEXT, "
            + SipClist.FIELD_PRESENCE_LAST_UPDATE	+ " TEXT, "
            + SipClist.FIELD_PRESENCE_STATUS_TYPE	    + " INTEGER DEFAULT "+ PushNotifications.PresencePush.Status.OFFLINE_VALUE+" , "
            + SipClist.FIELD_PRESENCE_STATUS_TEXT	    + " TEXT, "
            + SipClist.FIELD_PRESENCE_CERT_HASH_PREFIX	+ " TEXT, "
            + SipClist.FIELD_PRESENCE_CERT_NOT_BEFORE	+ " INTEGER DEFAULT 0, "
            + SipClist.FIELD_PRESENCE_LAST_CERT_UPDATE	+ " INTEGER DEFAULT 0, "
            + SipClist.FIELD_PRESENCE_NUM_CERT_UPDATE	+ " INTEGER DEFAULT 0, "
            + SipClist.FIELD_PRESENCE_DOS_CERT_UPDATE	+ " TEXT, "
            + SipClist.FIELD_CAPABILITIES               + " TEXT "
            + ");";
    
	private static final String THIS_FILE = "SipClist";
	
	protected Integer id = INVALID_ID;
	protected Integer account;
	protected String sip;
	protected String displayName;
	protected byte[] certificate;
	protected String certificateHash;
	protected boolean inWhitelist;
	protected Date dateCreated;
	protected Date dateLastModified;
	protected boolean presenceOnline=false;
	protected String presenceStatus=null;
	protected Date presenceLastUpdate;
	
	protected Integer presenceStatusType;
	protected String presenceStatusText;
	protected String presenceCertHashPrefix;
	protected Date presenceCertNotBefore;
	
	protected Date presenceLastCertUpdate;
    protected Integer presenceNumCertUpdate;
    protected String presenceDosCertUpdate;
	
	protected Integer unreadMessages;
	protected String capabilities;
	
	/**
	 * Locale for date conversion.
	 */
	protected Locale locale;
	
	public SipClist() {

    }
	
	 /**
     * Construct a sip profile wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #SIP_CONTACTLIST_TABLE}.
     * 
     * @param c the cursor to unpack
     */
    public SipClist(Cursor c) {
        super();
        createFromDb(c);
    }
    
    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public SipClist(Parcel in) {
        id = in.readInt();
        account = in.readInt();
        sip = in.readString();
        displayName = getReadParcelableString(in.readString());
        certificate = in.createByteArray(); 
        certificateHash = getReadParcelableString(in.readString());
        inWhitelist = (in.readInt() != 0) ? true:false;
        dateCreated = new Date(in.readLong());
        dateLastModified = new Date(in.readLong());
        presenceOnline = (in.readInt() != 0) ? true:false;
        presenceStatus = getReadParcelableString(in.readString());
        presenceLastUpdate = new Date(in.readLong());
        presenceStatusType = in.readInt();
        if (presenceStatusType==-1) presenceStatusType = null;
        presenceStatusText = getReadParcelableString(in.readString());
        presenceCertHashPrefix = getReadParcelableString(in.readString());
        presenceCertNotBefore = getReadParcelableDate(in.readLong());
        unreadMessages = in.readInt();
    	presenceLastCertUpdate = new Date(in.readLong());
        presenceNumCertUpdate = in.readInt();
        presenceDosCertUpdate = in.readString();
        capabilities = getReadParcelableString(in.readString());
    }
    
    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<SipClist> CREATOR = new Parcelable.Creator<SipClist>() {
        public SipClist createFromParcel(Parcel in) {
            return new SipClist(in);
        }

        public SipClist[] newArray(int size) {
            return new SipClist[size];
        }
    };

    /**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
    	dest.writeInt(id);
    	dest.writeInt(account);
    	dest.writeString(getWriteParcelableString(sip));
    	dest.writeString(getWriteParcelableString(displayName));
    	dest.writeByteArray(certificate != null ? certificate : new byte[0]);
    	dest.writeString(getWriteParcelableString(certificateHash));
    	dest.writeInt(inWhitelist ? 1 : 0);
    	dest.writeLong(dateCreated != null ? dateCreated.getTime() : 0);
    	dest.writeLong(dateLastModified != null ? dateLastModified.getTime() : 0);
    	dest.writeInt(presenceOnline ? 1 : 0);
    	dest.writeString(getWriteParcelableString(presenceStatus));
    	dest.writeLong(presenceLastUpdate != null ? presenceLastUpdate.getTime() : 0);
    	dest.writeInt(presenceStatusType != null ? presenceStatusType : -1);
    	dest.writeString(getWriteParcelableString(presenceStatusText));
    	dest.writeString(getWriteParcelableString(presenceCertHashPrefix));
    	dest.writeLong(presenceCertNotBefore != null ? presenceCertNotBefore.getTime() : 0);
    	dest.writeInt(unreadMessages != null ? unreadMessages : 0);
    	dest.writeLong(presenceLastCertUpdate != null ? presenceLastCertUpdate.getTime() : 0);
    	dest.writeInt(presenceNumCertUpdate != null ? presenceNumCertUpdate : 0);
    	dest.writeString(getWriteParcelableString(presenceDosCertUpdate));
    	dest.writeString(getWriteParcelableString(capabilities));
    }

    // Yes yes that's not clean but well as for now not problem with that.
    // and we send null.
    private String getWriteParcelableString(String str) {
        return (str == null) ? "null" : str;
    }

    private String getReadParcelableString(String str) {
        return str.equalsIgnoreCase("null") ? null : str;
    }
    
    private Date getReadParcelableDate(long lng) {
    	return lng==0 ? null : new Date(lng);
    }

    /**
     * Create account wrapper with cursor data.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        ContentValues args = new ContentValues();
        
        try {
        	DatabaseUtils.cursorRowToContentValues(c, args);
        	createFromContentValue(args);
        } catch(Exception e){
        	Log.w(THIS_FILE, "Cannot load to content values, falling back to default", e);
        	this.createFromCursor(c);
        }
    }
    
    private final void createFromCursor(Cursor c){
    	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
    	
    	int colCount = c.getColumnCount();
    	for(int i=0; i<colCount; i++){
    		String colname = c.getColumnName(i);
    		if (FIELD_ID.equals(colname)){
    			this.id = c.getInt(i);
    		} else if (FIELD_ACCOUNT.equals(colname)){
    			this.account = c.getInt(i);
    		} else if (FIELD_SIP.equals(colname)){
    			this.sip = c.getString(i);
    		} else if (FIELD_DISPLAY_NAME.equals(colname)){
    			this.displayName = c.getString(i);
    		} else if (FIELD_CERTIFICATE.equals(colname)){
    			this.certificate = c.getBlob(i);
    		} else if (FIELD_CERTIFICATE_HASH.equals(colname)){
    			this.certificateHash = c.getString(i);
    		} else if (FIELD_IN_WHITELIST.equals(colname)){
    			this.inWhitelist = c.getInt(i) != 0 ? true:false;
    		} else if (FIELD_PRESENCE_ONLINE.equals(colname)){
    			this.presenceOnline = c.getInt(i) != 0 ? true:false;
    		} else if (FIELD_PRESENCE_STATUS.equals(colname)){
    			this.presenceStatus = c.getString(i);
    		} else if (FIELD_DATE_CREATED.equals(colname)){
    			try {
    				this.dateCreated = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
            		Log.wf(THIS_FILE, e, "Parse exception: %s", c.getString(i));
    			}
    		} else if (FIELD_DATE_LAST_CHANGE.equals(colname)){
    			try {
    				this.dateLastModified = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
    				Log.wf(THIS_FILE, e, "Parse exception: %s", c.getString(i));
    			}
    		} else if (FIELD_PRESENCE_LAST_UPDATE.equals(colname)){
    			try {
    				this.presenceLastUpdate = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
    				Log.wf(THIS_FILE, e, "Parse exception: %s", c.getString(i));
    			}
    		} else if (FIELD_UNREAD_MESSAGES.equals(colname)){    			
        		this.unreadMessages = c.getInt(i);
    		} else if (FIELD_PRESENCE_STATUS_TYPE.equals(colname)){
    			this.presenceStatusType = c.getInt(i);
    		} else if (FIELD_PRESENCE_STATUS_TEXT.equals(colname)){
    			this.presenceStatusText = c.getString(i);
    		} else if (FIELD_PRESENCE_CERT_HASH_PREFIX.equals(colname)){
    			this.presenceCertHashPrefix = c.getString(i);
    		} else if (FIELD_PRESENCE_CERT_NOT_BEFORE.equals(colname)){
        		this.presenceCertNotBefore = new Date(c.getLong(i));
    		} else if (FIELD_PRESENCE_LAST_CERT_UPDATE.equals(colname)){
        		this.presenceLastCertUpdate = new Date(c.getLong(i));
    		} else if (FIELD_PRESENCE_NUM_CERT_UPDATE.equals(colname)){
        		this.presenceNumCertUpdate = c.getInt(i);
    		} else if (FIELD_PRESENCE_DOS_CERT_UPDATE.equals(colname)){
        		this.presenceDosCertUpdate = c.getString(i);
    		} else if (FIELD_CAPABILITIES.equals(colname)){
        		this.capabilities = c.getString(i);
    		} else {
    			Log.wf(THIS_FILE, "Unknown column name: %s", colname);
    		}
    	}
    }

    /**
     * Create account wrapper with content values pairs.
     * 
     * @param args the content value to unpack.
     */
    private final void createFromContentValue(ContentValues args) {
        Integer tmp_i;
        String tmp_s;
        
        // Application specific settings
        tmp_i = args.getAsInteger(FIELD_ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_ACCOUNT);
        if (tmp_i != null) {
            account = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_SIP);
        if (tmp_s != null) {
            sip = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_DISPLAY_NAME);
        if (tmp_s != null) {
            displayName = tmp_s;
        }
        byte[] tmp_ba = args.getAsByteArray(FIELD_CERTIFICATE);
        if (tmp_ba != null){
        	certificate = tmp_ba;
        }
        tmp_s = args.getAsString(FIELD_CERTIFICATE_HASH);
        if (tmp_s != null){
        	certificateHash = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_IN_WHITELIST);
        if (tmp_i != null){
        	inWhitelist = tmp_i==0 ? false:true;
        }
        tmp_s = args.getAsString(FIELD_DATE_CREATED);
        if(tmp_s != null){
        	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale!=null ? locale : Locale.getDefault());
        	try {
        		dateCreated = iso8601Format.parse(tmp_s);
        	} catch(ParseException e){
        		Log.wf(THIS_FILE, e, "Parse exception: %s", tmp_s);
        	}
        }
        tmp_s = args.getAsString(FIELD_DATE_LAST_CHANGE);
        if(tmp_s != null){
        	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale!=null ? locale : Locale.getDefault());
        	try {
        		dateLastModified = iso8601Format.parse(tmp_s);
        	} catch(ParseException e){
        		Log.wf(THIS_FILE, e, "Parse exception: %s", tmp_s);
        	}
        }
        tmp_i = args.getAsInteger(FIELD_PRESENCE_ONLINE);
        if (tmp_i != null){
        	presenceOnline = tmp_i == 0 ? false : true;
        }
        tmp_s = args.getAsString(FIELD_PRESENCE_STATUS);
        if (tmp_s != null){
        	presenceStatus = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_PRESENCE_LAST_UPDATE);
        if(tmp_s != null){
        	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale!=null ? locale : Locale.getDefault());
        	try {
        		presenceLastUpdate = iso8601Format.parse(tmp_s);
        	} catch(ParseException e){
        		Log.wf(THIS_FILE, e, "Parse exception: %s", tmp_s);
        	}
        }
        tmp_i = args.getAsInteger(FIELD_UNREAD_MESSAGES);
        if (tmp_i != null) {
            unreadMessages = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_PRESENCE_STATUS_TYPE);
        if (tmp_i != null) {
            presenceStatusType = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_PRESENCE_STATUS_TEXT);
        if (tmp_s != null){
        	presenceStatusText = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_PRESENCE_CERT_HASH_PREFIX);
        if (tmp_s != null){
        	presenceCertHashPrefix = tmp_s;
        }
        Long tmp_l = args.getAsLong(FIELD_PRESENCE_CERT_NOT_BEFORE);
        if (tmp_l != null){
        	presenceCertNotBefore = new Date(tmp_l);
        }
        tmp_l = args.getAsLong(FIELD_PRESENCE_LAST_CERT_UPDATE);
        if (tmp_l != null){
        	presenceLastCertUpdate = new Date(tmp_l);
        }
        tmp_i = args.getAsInteger(FIELD_PRESENCE_NUM_CERT_UPDATE);
        if (tmp_i != null) {
            presenceNumCertUpdate = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_PRESENCE_DOS_CERT_UPDATE);
        if (tmp_s != null){
        	presenceDosCertUpdate = tmp_s;
        }	
        tmp_s = args.getAsString(FIELD_CAPABILITIES);
        if (tmp_s != null){
        	capabilities = tmp_s;
        }	
    }
    
    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id!=null && id!=INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        args.put(FIELD_ACCOUNT, this.account);
        args.put(FIELD_SIP, this.sip);
        args.put(FIELD_DISPLAY_NAME, this.displayName);
        
        if (this.certificate!=null)
        	args.put(FIELD_CERTIFICATE, this.certificate);
        if (this.certificateHash!=null)
        	args.put(FIELD_CERTIFICATE_HASH, this.certificateHash);
        
        args.put(FIELD_IN_WHITELIST, this.inWhitelist);
        
        SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale!=null ? locale : Locale.getDefault());
        args.put(FIELD_DATE_CREATED, iso8601Format.format(dateCreated));
        args.put(FIELD_DATE_LAST_CHANGE, iso8601Format.format(dateLastModified));
        args.put(FIELD_PRESENCE_ONLINE, this.presenceOnline ? 1 : 0);
        if (this.presenceStatus!=null)
        	args.put(FIELD_PRESENCE_STATUS, this.presenceStatus);
        if (this.presenceLastUpdate!=null)
        	args.put(FIELD_PRESENCE_LAST_UPDATE, iso8601Format.format(this.presenceLastUpdate));
        if (this.presenceStatusType!=null)
        	args.put(FIELD_PRESENCE_STATUS_TYPE, presenceStatusType);
        if (this.presenceStatusText!=null)
        	args.put(FIELD_PRESENCE_STATUS_TEXT, presenceStatusText);
        if (this.presenceCertHashPrefix!=null)
        	args.put(FIELD_PRESENCE_CERT_HASH_PREFIX, presenceCertHashPrefix);
        if (this.presenceCertNotBefore!=null)
        	args.put(FIELD_PRESENCE_CERT_NOT_BEFORE, presenceCertNotBefore.getTime());
        if (this.presenceLastCertUpdate!=null)
        	args.put(FIELD_PRESENCE_LAST_CERT_UPDATE, presenceLastCertUpdate.getTime());
        if (this.presenceNumCertUpdate!=null)
        	args.put(FIELD_PRESENCE_NUM_CERT_UPDATE, presenceNumCertUpdate);
        if (this.presenceDosCertUpdate!=null)
        	args.put(FIELD_PRESENCE_DOS_CERT_UPDATE, presenceDosCertUpdate);
        if (this.capabilities!=null)
        	args.put(FIELD_CAPABILITIES, capabilities);
        return args;
    }

    public X509Certificate getCertificateObj() throws CertificateEncodingException, CertificateException{
    	if (this.certificate==null || this.certificate.length==0) return null;
    	
    	 // certificate factory according to X.509 std
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
            
        InputStream in = new ByteArrayInputStream(this.certificate);
        return (X509Certificate)cf.generateCertificate(in);
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SipClist [id=" + id + ", account=" + account + ", sip=" + sip
				+ ", displayName=" + displayName + ", certificate="
				+ Arrays.toString(certificate) + ", certificateHash="
				+ certificateHash + ", inWhitelist=" + inWhitelist
				+ ", dateCreated=" + dateCreated + ", dateLastModified="
				+ dateLastModified + ", presenceOnline=" + presenceOnline
				+ ", presenceStatus=" + presenceStatus
				+ ", presenceLastUpdate=" + presenceLastUpdate
				+ ", presenceStatusType=" + presenceStatusType
				+ ", presenceStatusText=" + presenceStatusText
				+ ", presenceCertHashPrefix=" + presenceCertHashPrefix
				+ ", presenceCertNotBefore=" + presenceCertNotBefore
				+ ", presenceLastCertUpdate=" + presenceLastCertUpdate
				+ ", presenceNumCertUpdate=" + presenceNumCertUpdate
				+ ", presenceDosCertUpdate=" + presenceDosCertUpdate
				+ ", unreadMessages=" + unreadMessages + ", locale=" + locale
				+ "]";
	}

	public static SipClist getProfileFromDbSip(Context ctxt, String sip) {
		return getProfileFromDbSip(ctxt, sip, SipClist.LIGHT_PROJECTION);
	}
	
	// Helpers static factory
    /**
     * Helper method to retrieve a SipClist object from its account database
     * 
     * @param ctxt Your application context. Mainly useful to get the content provider for the request.
     * @param sip Sip in text format: e.g: test610@phone-x.net 
     * @param projection The list of fields you want to retrieve. Must be in FIELD_* of this class.<br/>
     * Reducing your requested fields to minimum will improve speed of the request.
     * @return A wrapper SipClist object on the request you done. If not found an invalid account with an {@link #id} equals to {@link #INVALID_ID}
     */
    public static SipClist getProfileFromDbSip(Context ctxt, String sip, String[] projection) {
    	Cursor c = ctxt.getContentResolver().query(
				SipClist.CLIST_URI,
				projection,
				SipClist.FIELD_SIP +  "=?",
				new String[] {sip},
				null);
		
    	
//    	SipClist clist = new SipClist();

        SipClist clist = null;

        if (c != null) {
            try {
                if (c.moveToFirst()){
                	clist = new SipClist(c);
                }
            } catch (Exception e) {
                Log.e(THIS_FILE, "Error while getting SipClist from DB", e);
            } finally {
                c.close();
            }
        }
        
        return clist;
    }
    
    // Helpers static factory
    /**
     * Helper method to retrieve a SipClist object from its account database
     * 
     * @param ctxt Your application context. Mainly useful to get the content provider for the request.
     * @param sip Sip in text format: e.g: test610@phone-x.net 
     * @param projection The list of fields you want to retrieve. Must be in FIELD_* of this class.<br/>
     * Reducing your requested fields to minimum will improve speed of the request.
     * @return A wrapper SipClist object on the request you done. If not found an invalid account with an {@link #id} equals to {@link #INVALID_ID}
     */
    public static List<SipClist> getProfileFromDbSip(Context ctxt, List<String> sip, String[] projection) {
    	List<SipClist> ret = new LinkedList<SipClist>();
    	if (sip==null || sip.isEmpty()){
    		return ret;
    	}
    	
    	String[] sips = sip.toArray(new String[sip.size()]);
    	Cursor c = ctxt.getContentResolver().query(
				SipClist.CLIST_URI,
				projection,
				SipClist.FIELD_SIP +  " IN (" + DBProvider.getInPlaceholders(sip.size()) + " ) ",
				sips,
				null);
		
    	if (c==null){
    		return ret;
    	}
    	
    	try {
	    	while(c.moveToNext()){
	    		SipClist clist = new SipClist(c);
	    		ret.add(clist);
	    	}
    	} catch(Exception ex){
    		Log.e(THIS_FILE, "Error while getting SipClist from DB", ex);
    	} finally {
    		// Close cursor.
			try {
				c.close();
			} catch(Exception e) { }
    	}
		        
        return ret;
    }

    public static List<SipClist> getAllProfiles(Context ctxt, String[] projection) {
        List<SipClist> ret = new LinkedList<SipClist>();

        Cursor c = ctxt.getContentResolver().query(
                SipClist.CLIST_URI,
                projection,
                null,
                null,
                null);

        if (c==null){
            return ret;
        }

        try {
            while(c.moveToNext()){
                SipClist clist = new SipClist(c);
                ret.add(clist);
            }
        } catch(Exception ex){
            Log.e(THIS_FILE, "Error while getting SipClist from DB", ex);
        } finally {
            // Close cursor.
            try {
                c.close();
            } catch(Exception e) { }
        }

        return ret;
    }

    public static void removeProfiles(Context context, Set<String> sipsToRemove) {
        for (String sip : sipsToRemove){
            context.getContentResolver().delete(SipClist.CLIST_URI, SipClist.FIELD_SIP + "=?", new String[]{sip});
        }
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getAccount() {
		return account;
	}

	public void setAccount(Integer account) {
		this.account = account;
	}

	public String getSip() {
		return sip;
	}

    public String getCanonicalSip(boolean includeScheme){
        return SipUri.getCanonicalSipContact(getSip(), includeScheme);
    }

    public String getCanonicalSip(){
        return SipUri.getCanonicalSipContact(getSip(), false);
    }

	public void setSip(String sip) {
		this.sip = sip;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public byte[] getCertificate() {
		return certificate;
	}

	public void setCertificate(byte[] certificate) {
		this.certificate = certificate;
	}

	public String getCertificateHash() {
		return certificateHash;
	}

	public void setCertificateHash(String certificateHash) {
		this.certificateHash = certificateHash;
	}

	public boolean isInWhitelist() {
		return inWhitelist;
	}

	public void setInWhitelist(boolean inWhitelist) {
		this.inWhitelist = inWhitelist;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Date getDateLastModified() {
		return dateLastModified;
	}

	public void setDateLastModified(Date dateLastModified) {
		this.dateLastModified = dateLastModified;
	}

	public boolean isPresenceOnline() {
		return presenceOnline;
	}

	public void setPresenceOnline(boolean presenceOnline) {
		this.presenceOnline = presenceOnline;
	}

	public String getPresenceStatus() {
		return presenceStatus;
	}

	public void setPresenceStatus(String presenceStatus) {
		this.presenceStatus = presenceStatus;
	}

	public Date getPresenceLastUpdate() {
		return presenceLastUpdate;
	}

	public void setPresenceLastUpdate(Date presenceLastUpdate) {
		this.presenceLastUpdate = presenceLastUpdate;
	}

	public Integer getUnreadMessages() {
		return unreadMessages;
	}

	/**
	 * @return the presenceStatusType
	 */
	public Integer getPresenceStatusType() {
		return presenceStatusType;
	}

	/**
	 * @param presenceStatusType the presenceStatusType to set
	 */
	public void setPresenceStatusType(Integer presenceStatusType) {
		this.presenceStatusType = presenceStatusType;
	}

	/**
	 * @return the presenceStatusText
	 */
	public String getPresenceStatusText() {
		return presenceStatusText;
	}

	/**
	 * @param presenceStatusText the presenceStatusText to set
	 */
	public void setPresenceStatusText(String presenceStatusText) {
		this.presenceStatusText = presenceStatusText;
	}

	/**
	 * @return the presenceCertHashPrefix
	 */
	public String getPresenceCertHashPrefix() {
		return presenceCertHashPrefix;
	}

	/**
	 * @param presenceCertHashPrefix the presenceCertHashPrefix to set
	 */
	public void setPresenceCertHashPrefix(String presenceCertHashPrefix) {
		this.presenceCertHashPrefix = presenceCertHashPrefix;
	}

	/**
	 * @return the presenceCertNotBefore
	 */
	public Date getPresenceCertNotBefore() {
		return presenceCertNotBefore;
	}

	/**
	 * @param presenceCertNotBefore the presenceCertNotBefore to set
	 */
	public void setPresenceCertNotBefore(Date presenceCertNotBefore) {
		this.presenceCertNotBefore = presenceCertNotBefore;
	}

	/**
	 * @param unreadMessages the unreadMessages to set
	 */
	public void setUnreadMessages(Integer unreadMessages) {
		this.unreadMessages = unreadMessages;
	}

	/**
	 * @return the locale
	 */
	public Locale getLocale() {
		return locale;
	}

	/**
	 * @param locale the locale to set
	 */
	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	/**
	 * @return the presenceLastCertUpdate
	 */
	public Date getPresenceLastCertUpdate() {
		return presenceLastCertUpdate;
	}

	/**
	 * @param presenceLastCertUpdate the presenceLastCertUpdate to set
	 */
	public void setPresenceLastCertUpdate(Date presenceLastCertUpdate) {
		this.presenceLastCertUpdate = presenceLastCertUpdate;
	}

	/**
	 * @return the presenceNumCertUpdate
	 */
	public Integer getPresenceNumCertUpdate() {
		return presenceNumCertUpdate;
	}

	/**
	 * @param presenceNumCertUpdate the presenceNumCertUpdate to set
	 */
	public void setPresenceNumCertUpdate(Integer presenceNumCertUpdate) {
		this.presenceNumCertUpdate = presenceNumCertUpdate;
	}

	/**
	 * @return the presenceDosCertUpdate
	 */
	public String getPresenceDosCertUpdate() {
		return presenceDosCertUpdate;
	}

	/**
	 * @param presenceDosCertUpdate the presenceDosCertUpdate to set
	 */
	public void setPresenceDosCertUpdate(String presenceDosCertUpdate) {
		this.presenceDosCertUpdate = presenceDosCertUpdate;
	}

	public String getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(String capabilities) {
		this.capabilities = capabilities;
	}

    public boolean hasCapability(String capability){
        return hasCapability(capability, getCapabilities());
    }

	/**
	 * Adds capability to the string, checks for duplicates in O(mn).
	 * @param capability
	 */
	public static String addCapability(String capability, String capabilities){
		final String cap2search = ";" + capability + ";";
		if (TextUtils.isEmpty(capabilities)){
			return cap2search;
		}
		
		// Check for duplicates
		if (capabilities.contains(cap2search)){
			return capabilities;
		}
		
		// Add to non-empty capabilities set.
		return capabilities + capability + ";";
	}
	
	/**
	 * Determines whether given capability is among stored ones.
	 * 
	 * @param capability
	 * @param capabilities
	 * @return
	 */
	public static boolean hasCapability(String capability, String capabilities){
		if (TextUtils.isEmpty(capabilities)){
			return false;
		}
		
		return capabilities.contains(";" + capability + ";");
	}
	
	/**
	 * Parse given capability to the set. Deserialization routine.
	 * @param capabilities
	 * @return
	 */
	public static Set<String> getCapabilitiesAsSet(String capabilities){
		Set<String> ret = new HashSet<String>();
		if (TextUtils.isEmpty(capabilities)){
			return ret;
		}
		
		String[] caps = capabilities.split(";");
		for(String cap : caps){
			if (TextUtils.isEmpty(cap)) continue;
			ret.add(cap);
		}
		
		return ret;
	}
	
	/**
	 * Assemble capabilities hash set to the string that can be stored to the database.
	 * Serialization routine.
	 * 
	 * @param caps
	 * @return
	 */
	public static String assembleCapabilities(Set<String> caps){
		if (caps.isEmpty()) {
			return "";
		}
		
		StringBuilder sb = new StringBuilder(";");
		for(String cap : caps){
			sb.append(cap);
			sb.append(";");
		}
		
		return sb.toString();
	}

    // Hashcode is implemented over SIP value
    @Override
    public int hashCode() {
        return getSip().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SipClist clist = (SipClist) o;

        if (!sip.equals(clist.sip)) return false;

        return true;
    }

    /**
     * Returns true if the record was loaded from database successfully.
     * Returns false if ID is null or invalid_id (sentinel record).
     *
     * @return
     */
    public boolean isValidDb(){
        return this.id != null && !this.id.equals(INVALID_ID);
    }


}
