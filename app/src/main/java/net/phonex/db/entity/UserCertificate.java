package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;



/**
 * Helper for certificate stored in database
 * @author ph4r05
 *
 */
public class UserCertificate {
	public static final String TABLE = "certificates";
    public static final String FIELD_ID = "id";
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_CERTIFICATE_STATUS = "certificateStatus";
    public static final String FIELD_CERTIFICATE = "certificate";
    public static final String FIELD_CERTIFICATE_HASH = "certificateHash";
    public static final String FIELD_DATE_LAST_QUERY = "dateLastQuery";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public final static long INVALID_ID = -1;
    
    public static final String[] FULL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_OWNER, FIELD_CERTIFICATE, FIELD_CERTIFICATE_HASH, FIELD_DATE_CREATED,
    	FIELD_CERTIFICATE_STATUS, FIELD_DATE_LAST_QUERY
    };
    
    public static final String[] NORMAL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_OWNER, FIELD_CERTIFICATE_HASH, FIELD_DATE_CREATED,
    	FIELD_CERTIFICATE_STATUS, FIELD_DATE_LAST_QUERY
    };
    
    /**
     * Uri for content provider of certificate
     */
    public static final Uri CERTIFICATE_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base uri for sip message content provider.<br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri CERTIFICATE_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    // SQL Create command for certificate table.
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + UserCertificate.TABLE
            + " ("
            + UserCertificate.FIELD_ID 					+ " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + UserCertificate.FIELD_OWNER 				+ " TEXT, "
            + UserCertificate.FIELD_CERTIFICATE_STATUS	+ " INTEGER DEFAULT "+ UserCertificate.CERTIFICATE_STATUS_MISSING+", "
            + UserCertificate.FIELD_CERTIFICATE 			+ " BLOB, "
            + UserCertificate.FIELD_CERTIFICATE_HASH		+ " TEXT, "
            + UserCertificate.FIELD_DATE_CREATED			+ " TEXT, "
            + UserCertificate.FIELD_DATE_LAST_QUERY		+ " TEXT "
            + ");";


	private static final String TAG = "UserCertificate";
	
	public static final int CERTIFICATE_STATUS_OK = 1;
	public static final int CERTIFICATE_STATUS_INVALID = 2;
	public static final int CERTIFICATE_STATUS_REVOKED = 3;
	public static final int CERTIFICATE_STATUS_FORBIDDEN = 4;
	public static final int CERTIFICATE_STATUS_MISSING = 5;
	public static final int CERTIFICATE_STATUS_NOUSER = 6;
	
	protected Integer id;
	protected String owner;
	protected byte[] certificate;
	protected String certificateHash;
	protected Date dateCreated;
	protected int certificateStatus;
	protected Date dateLastQuery;
	
	public UserCertificate() {
		
    }
	
	 /**
     * Construct a sip profile wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #SIP_CONTACTLIST_TABLE}.
     * 
     * @param c the cursor to unpack
     */
    public UserCertificate(Cursor c) {
        super();
        createFromDb(c);
    }
    
    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public UserCertificate(Parcel in) {
        id = in.readInt();
        owner = in.readString();
        in.readByteArray(certificate);
        certificateHash = in.readString();
        dateCreated = new Date(in.readLong());
        certificateStatus = in.readInt();
        dateLastQuery = new Date(in.readLong());
    }

    /**
     * Create account wrapper with cursor datas.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        ContentValues args = new ContentValues();
        this.createFromCursor(c);
    }
    
    /**
     * OldSchool method of initialization from cursor
     * @param c
     */
    private final void createFromCursor(Cursor c){
    	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT);
    	
    	int colCount = c.getColumnCount();
    	for(int i=0; i<colCount; i++){
    		String colname = c.getColumnName(i);
    		if (FIELD_ID.equals(colname)){
    			this.id = c.getInt(i);
    		} else if (FIELD_OWNER.equals(colname)){
    			this.owner = c.getString(i);
    		} else if (FIELD_CERTIFICATE_STATUS.equals(colname)){
    			this.certificateStatus = c.getInt(i);
    		} else if (FIELD_CERTIFICATE.equals(colname)){
    			this.certificate = c.getBlob(i);
    		} else if (FIELD_CERTIFICATE_HASH.equals(colname)){
    			this.certificateHash = c.getString(i);
    		} else if (FIELD_DATE_CREATED.equals(colname)){
    			try {
    				this.dateCreated = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
            		Log.wf(TAG, e, "Parse exception: %s", c.getString(i));
    			}
    		}
			else if (FIELD_DATE_LAST_QUERY.equals(colname)){
    			try {
    				this.dateLastQuery = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
            		Log.wf(TAG, e, "Parse exception: %s", c.getString(i));
    			}
    		} else {
    			Log.wf(TAG, "Unknown column name: %s", colname);
    		}
    	}
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id!=null && id!=INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        args.put(FIELD_OWNER, this.owner);
        args.put(FIELD_CERTIFICATE_STATUS, this.certificateStatus);
        if (this.certificate!=null)
        	args.put(FIELD_CERTIFICATE, this.certificate);
        if (this.certificateHash!=null)
        	args.put(FIELD_CERTIFICATE_HASH, this.certificateHash);
        
        SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT);
        args.put(FIELD_DATE_CREATED, iso8601Format.format(dateCreated));
        args.put(FIELD_DATE_LAST_QUERY, iso8601Format.format(dateLastQuery));
        
        return args;
    }

    public X509Certificate getCertificateObj() throws CertificateEncodingException, CertificateException{
    	if (this.certificate==null || this.certificate.length==0) return null;
    	
    	try {
    		return CertificatesAndKeys.buildCertificate(this.certificate);
		} catch (Exception e) {
			throw new CertificateEncodingException(e);
		}
    }
    
    /**
     * Tries to parse stored byte representation of the certificate and returns whether
     * it is valid X509 certificate or not.
     * 
     * @return
     */
    public boolean isValidCertObj() {
    	if (this.certificate==null || this.certificate.length==0) return false;
    	
    	try {
    		CertificatesAndKeys.buildCertificate(this.certificate);
    		return true;
		} catch (Exception e) {
			return false;
		}
    }

	@Override
	public String toString() {
		return "UserCertificate [id=" + id + ", owner=" + owner
				+ ", certificate=" + Arrays.toString(certificate)
				+ ", certificateHash=" + certificateHash + ", dateCreated="
				+ dateCreated + ", certificateStatus=" + certificateStatus
				+ ", dateLastQuery=" + dateLastQuery + "]";
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
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

	public Date getDateCreated() {
		return dateCreated;
	}
	
	public String getDateCreatedFormatted(){
		return formatDate(dateCreated);
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	public int getCertificateStatus() {
		return certificateStatus;
	}

	public void setCertificateStatus(int certificateStatus) {
		this.certificateStatus = certificateStatus;
	}
	
	public void setCertificateStatus(CertificateStatus status) {
		switch(status){
		case FORBIDDEN:
			this.certificateStatus = CERTIFICATE_STATUS_FORBIDDEN;
			break;
		case MISSING:
			this.certificateStatus = CERTIFICATE_STATUS_MISSING;
			break;
		case NOUSER:
			this.certificateStatus = CERTIFICATE_STATUS_NOUSER;
			break;
		case OK:
			this.certificateStatus = CERTIFICATE_STATUS_OK;
			break;
		case REVOKED:
			this.certificateStatus = CERTIFICATE_STATUS_REVOKED;
			break;
		case INVALID:
		default:
			this.certificateStatus = CERTIFICATE_STATUS_INVALID;
			break;
		}
	}

	public Date getDateLastQuery() {
		return dateLastQuery;
	}
	
	/**
     * Creates string representation of date that is expected in this class.
     * Can be used in UPDATE queries.
     * 
     * @param dt
     * @return
     */
	public static String formatDate(Date toFormat){
		return formatDate(toFormat, Locale.getDefault());
	}
	
	public static String formatDate(Date toFormat, Locale locale){
		SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        return iso8601Format.format(toFormat);
	}
	
	public String getDateLastQueryFormatted(){
		return formatDate(dateLastQuery);
	}

	public void setDateLastQuery(Date dateLastQuery) {
		this.dateLastQuery = dateLastQuery;
	}
	
	public static String getCertificateErrorString(int status, Context ctxt){
		switch(status){
			case CERTIFICATE_STATUS_OK:
				return ctxt.getString(R.string.cert_status_ok);
			case CERTIFICATE_STATUS_INVALID:
				return ctxt.getString(R.string.cert_status_invalid);
			case CERTIFICATE_STATUS_REVOKED:
				return ctxt.getString(R.string.cert_status_revoked);
			case CERTIFICATE_STATUS_FORBIDDEN:
				return ctxt.getString(R.string.cert_status_forbidden);
			case CERTIFICATE_STATUS_MISSING:
				return ctxt.getString(R.string.cert_status_missing);
			case CERTIFICATE_STATUS_NOUSER:
				return ctxt.getString(R.string.cert_status_nouser);
		}
		
		return ctxt.getString(R.string.cert_status_missing);
	}
	
}
