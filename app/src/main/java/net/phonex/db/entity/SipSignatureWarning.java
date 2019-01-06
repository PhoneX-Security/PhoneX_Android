package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import net.phonex.core.Constants;
import net.phonex.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class SipSignatureWarning {
	public static final String TABLE = "signature_warning";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_METHOD = "method";
    public static final String FIELD_IS_REQ = "isReq";
    public static final String FIELD_RESP_CODE = "respCode";
    public static final String FIELD_CSEQ = "cseq";
    public static final String FIELD_CSEQ_NUM = "cseqNum";
    public static final String FIELD_REQ_URI = "reqURI";
    public static final String FIELD_FROM_URI = "fromURI";
    public static final String FIELD_TO_URI = "toURI";
    public static final String FIELD_SRC_IP = "srcIP";
    public static final String FIELD_SRC_PORT = "srcPort";
    public static final String FIELD_ABSORBED = "absorbed";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_LAST = "dateLast";
    public static final String FIELD_ERROR_CODE = "errorCode";
    public static final String FIELD_USER_SAW = "userSaw";
    public static final String FIELD_DROPPED = "dropped";
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    public final static long INVALID_ID = -1;
    
    public static final String[] FULL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_METHOD, FIELD_IS_REQ, FIELD_RESP_CODE, FIELD_CSEQ, FIELD_CSEQ_NUM,
    	FIELD_REQ_URI, FIELD_FROM_URI, FIELD_TO_URI, FIELD_SRC_IP, FIELD_SRC_PORT, FIELD_DATE_CREATED, 
    	FIELD_ERROR_CODE, FIELD_USER_SAW, FIELD_ABSORBED, FIELD_DATE_LAST, FIELD_DROPPED
    };
    
    /**
     * Uri for content provider of certificate
     */
    public static final Uri SIGNATURE_WARNING_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base uri for sip message content provider.<br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri SIGNATURE_WARNING_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    // SQL Create command for SIP signature warnings table.
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + SipSignatureWarning.TABLE
            + " ("
            + SipSignatureWarning.FIELD_ID                  + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + SipSignatureWarning.FIELD_METHOD              + " TEXT, "
            + SipSignatureWarning.FIELD_IS_REQ              + " INTEGER DEFAULT 1, "
            + SipSignatureWarning.FIELD_RESP_CODE           + " INTEGER DEFAULT 0, "
            + SipSignatureWarning.FIELD_CSEQ                + " TEXT, "
            + SipSignatureWarning.FIELD_CSEQ_NUM            + " INTEGER, "
            + SipSignatureWarning.FIELD_REQ_URI             + " TEXT, "
            + SipSignatureWarning.FIELD_FROM_URI            + " TEXT, "
            + SipSignatureWarning.FIELD_TO_URI              + " TEXT, "
            + SipSignatureWarning.FIELD_SRC_IP              + " TEXT, "
            + SipSignatureWarning.FIELD_SRC_PORT            + " INTEGER DEFAULT 0, "
            + SipSignatureWarning.FIELD_ABSORBED            + " INTEGER DEFAULT 0, "
            + SipSignatureWarning.FIELD_DATE_CREATED        + " TEXT, "
            + SipSignatureWarning.FIELD_DATE_LAST           + " TEXT, "
            + SipSignatureWarning.FIELD_ERROR_CODE          + " INTEGER DEFAULT 0, "
            + SipSignatureWarning.FIELD_USER_SAW            + " INTEGER DEFAULT 0, "
            + SipSignatureWarning.FIELD_DROPPED             + " INTEGER DEFAULT 0 "
            + ");";

	private static final String THIS_FILE = "SipSignatureWarning";
	
	// Locale for date formating
	protected Locale    locale     = Locale.getDefault();
	
	protected Integer   id         = Integer.valueOf((int)INVALID_ID);
	protected String    method;
	protected boolean   isReq      = true;
	protected int       respCode   = -1;
	protected String    cseq;
	protected int       cseqNum    = -1;
	protected String    reqURI;
	protected String    fromURI;
	protected String    toURI;
	protected String    srcIP;
	protected int       srcPort     = 0;
	protected Date      dateCreated;
	protected int       errorCode   = -1;
	protected boolean   userSaw     = false;
	protected Date      dateLast;
	protected int       absorbed    = 0;
	protected boolean   dropped     = true;
	
	public SipSignatureWarning() {
		
    }
	
	 /**
     * Construct a sip profile wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #SIP_SIGNATURE_WARNING}.
     * 
     * @param c the cursor to unpack
     */
    public SipSignatureWarning(Cursor c) {
        super();
        createFromDb(c);
    }
    
    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public SipSignatureWarning(Parcel in) {
        id          = in.readInt();
        method      = in.readString();
        isReq       = (in.readInt() == 0) ? false : true;
        respCode    = in.readInt();
        cseq        = in.readString();
        cseqNum     = in.readInt();
        reqURI      = in.readString();
        fromURI     = in.readString();
        toURI       = in.readString();
        srcIP       = in.readString();
        srcPort     = in.readInt();
        dateCreated = new Date(in.readLong());
        errorCode   = in.readInt();
        userSaw     = (in.readInt() == 0) ? false : true;
        dateLast    = new Date(in.readLong());
        absorbed    = in.readInt();
        dropped     = (in.readInt() == 0) ? false : true;
    }

    /**
     * Create account wrapper with cursor datas.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {
        //ContentValues args = new ContentValues();     
        this.createFromCursor(c);
        
        /*
        try {
        	DatabaseUtils.cursorRowToContentValues(c, args);
        	createFromContentValue(args);
        } catch(Exception e){
        	Log.d(THIS_FILE, "Cannot load to content values, falling back to default", e);
        	this.createFromCursor(c);
        }
        */
    }
    
    /**
     * OldSchool method of initialization from cursor
     * @param c
     */
    private final void createFromCursor(Cursor c){
    	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
    	
    	int colCount = c.getColumnCount();
    	for(int i=0; i<colCount; i++){
    		String colname = c.getColumnName(i);
    		if (FIELD_ID.equals(colname)){
    			this.id = c.getInt(i);
    		} else if (FIELD_METHOD.equals(colname)){
    			this.method = c.getString(i);
    		} else if (FIELD_IS_REQ.equals(colname)){
    			this.isReq = (c.getInt(i) == 0) ? false : true;
    		} else if (FIELD_RESP_CODE.equals(colname)){
    			this.respCode = c.getInt(i);
    		} else if (FIELD_CSEQ.equals(colname)){
    			this.cseq = c.getString(i);
    		} else if (FIELD_CSEQ_NUM.equals(colname)){
    			this.cseqNum = c.getInt(i);
    		} else if (FIELD_REQ_URI.equals(colname)){
    			this.reqURI = c.getString(i);
    		} else if (FIELD_FROM_URI.equals(colname)){
    			this.fromURI = c.getString(i);
    		} else if (FIELD_TO_URI.equals(colname)){
    			this.toURI = c.getString(i);
    		} else if (FIELD_SRC_IP.equals(colname)){
    			this.srcIP = c.getString(i);
    		} else if (FIELD_SRC_PORT.equals(colname)){
    			this.srcPort = c.getInt(i);
    		} else if (FIELD_ERROR_CODE.equals(colname)){
    			this.errorCode = c.getInt(i);
    		} else if (FIELD_USER_SAW.equals(colname)){
    			this.userSaw = (c.getInt(i) == 0) ? false : true;
    		} else if (FIELD_DROPPED.equals(colname)){
    			this.dropped = (c.getInt(i) == 0) ? false : true;
    		} else if (FIELD_ABSORBED.equals(colname)){
    			this.absorbed = c.getInt(i);
    		} else if (FIELD_DATE_CREATED.equals(colname)){
    			try {
    				this.dateCreated = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
            		Log.wf(THIS_FILE, e, "Parse exception: %s", c.getString(i));
    			}
    		} else if (FIELD_DATE_LAST.equals(colname)){
    			try {
    				this.dateLast = iso8601Format.parse(c.getString(i));
    			} catch(ParseException e){
            		Log.wf(THIS_FILE, e, "Parse exception: %s", c.getString(i));
    			}
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
    public final void createFromContentValue(ContentValues args) {
        Integer tmp_i;
        String tmp_s;
        
        // Application specific settings
        tmp_i = args.getAsInteger(FIELD_ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_METHOD);
        if (tmp_s != null) {
            method = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_IS_REQ);
        if (tmp_i != null) {
            isReq = tmp_i != 0;
        }
        tmp_i = args.getAsInteger(FIELD_RESP_CODE);
        if (tmp_i != null) {
            respCode = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_CSEQ);
        if (tmp_s != null) {
            cseq = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_CSEQ_NUM);
        if (tmp_i != null) {
            cseqNum = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_REQ_URI);
        if (tmp_s != null) {
            reqURI = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_FROM_URI);
        if (tmp_s != null) {
            fromURI = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_TO_URI);
        if (tmp_s != null) {
            toURI = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_SRC_IP);
        if (tmp_s != null) {
            srcIP = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_SRC_PORT);
        if (tmp_i != null) {
            srcPort = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_DATE_CREATED);
        if(tmp_s != null){
        	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        	try {
        		dateCreated = iso8601Format.parse(tmp_s);
        	} catch(ParseException e){
        		Log.wf(THIS_FILE, e, "Parse exception: %s", tmp_s);
        	}
        }
        tmp_s = args.getAsString(FIELD_DATE_LAST);
        if(tmp_s != null){
        	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        	try {
        		dateLast = iso8601Format.parse(tmp_s);
        	} catch(ParseException e){
        		Log.wf(THIS_FILE, e, "Parse exception: %s", tmp_s);
        	}
        }
        tmp_i = args.getAsInteger(FIELD_ERROR_CODE);
        if (tmp_i != null) {
            errorCode = tmp_i;
        }
        tmp_i = args.getAsInteger(FIELD_USER_SAW);
        if (tmp_i != null) {
            userSaw = tmp_i != 0;
        }
        tmp_i = args.getAsInteger(FIELD_DROPPED);
        if (tmp_i != null) {
            dropped = tmp_i != 0;
        }
        tmp_i = args.getAsInteger(FIELD_ABSORBED);
        if (tmp_i != null) {
            absorbed = tmp_i;
        }
    }
    
    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id!=null && id!=INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        
        args.put(FIELD_METHOD, this.method);
        args.put(FIELD_IS_REQ, this.isReq);
        args.put(FIELD_RESP_CODE, this.respCode);
        args.put(FIELD_CSEQ, this.cseq);
        args.put(FIELD_CSEQ_NUM, this.cseqNum);
        args.put(FIELD_REQ_URI, this.reqURI);
        args.put(FIELD_FROM_URI, this.fromURI);
        args.put(FIELD_TO_URI, this.toURI);
        args.put(FIELD_SRC_IP, this.srcIP);
        args.put(FIELD_SRC_PORT, this.srcPort);
        SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        args.put(FIELD_DATE_CREATED, iso8601Format.format(dateCreated));
        args.put(FIELD_DATE_LAST, iso8601Format.format(dateLast));
        args.put(FIELD_ERROR_CODE, errorCode);
        args.put(FIELD_USER_SAW, userSaw);
        args.put(FIELD_DROPPED, dropped);
        args.put(FIELD_ABSORBED, absorbed);
        
        return args;
    }
    
    /**
     * Creates string representation of date that is expected in this class.
     * Can be used in UPDATE queries.
     * 
     * @param dt
     * @return
     */
    public static String formatDate(Date dt, Locale locale){
    	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        return iso8601Format.format(dt);
    }
    
    public static String formatDate(Date dt){
    	return formatDate(dt, Locale.getDefault());
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Date getDateCreated() {
		return dateCreated;
	}
	
	public String getDateCreatedFormatted(){
		SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        return iso8601Format.format(dateCreated);
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public boolean isReq() {
		return isReq;
	}

	public void setReq(boolean isReq) {
		this.isReq = isReq;
	}

	public int getRespCode() {
		return respCode;
	}

	public void setRespCode(int respCode) {
		this.respCode = respCode;
	}

	public String getCseq() {
		return cseq;
	}

	public void setCseq(String cseq) {
		this.cseq = cseq;
	}

	public int getCseqNum() {
		return cseqNum;
	}

	public void setCseqNum(int cseqNum) {
		this.cseqNum = cseqNum;
	}

	public String getReqURI() {
		return reqURI;
	}

	public void setReqURI(String reqURI) {
		this.reqURI = reqURI;
	}

	public String getFromURI() {
		return fromURI;
	}

	public void setFromURI(String fromURI) {
		this.fromURI = fromURI;
	}

	public String getToURI() {
		return toURI;
	}

	public void setToURI(String toURI) {
		this.toURI = toURI;
	}

	public String getSrcIP() {
		return srcIP;
	}

	public void setSrcIP(String srcIP) {
		this.srcIP = srcIP;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(int errorCode) {
		this.errorCode = errorCode;
	}

	public boolean isUserSaw() {
		return userSaw;
	}

	public void setUserSaw(boolean userSaw) {
		this.userSaw = userSaw;
	}

	public Date getDateLast() {
		return dateLast;
	}

	public void setDateLast(Date dateLast) {
		this.dateLast = dateLast;
	}

	public int getAbsorbed() {
		return absorbed;
	}

	public void setAbsorbed(int absorbed) {
		this.absorbed = absorbed;
	}

	public boolean isDropped() {
		return dropped;
	}

	public void setDropped(boolean dropped) {
		this.dropped = dropped;
	}

	public int getSrcPort() {
		return srcPort;
	}

	public void setSrcPort(int srcPort) {
		this.srcPort = srcPort;
	}
	
	/**
	 * Returns SIP address of the remote side of received message.
	 * Takes care about request/response ordering of from/to.
	 * @return
	 */
	public String getRemoteURI(){
		return isReq ? fromURI : toURI;
	}
	
	/**
	 * Returns SIP address of the local side of received message.
	 * Takes care about request/response ordering of from/to.
	 * @return
	 */
	public String getLocalURI(){
		return isReq ? toURI : fromURI;
	}

	@Override
	public String toString() {
		return "SipSignatureWarning [locale=" + locale + ", id=" + id
				+ ", method=" + method + ", isReq=" + isReq + ", respCode="
				+ respCode + ", cseq=" + cseq + ", cseqNum=" + cseqNum
				+ ", reqURI=" + reqURI + ", fromURI=" + fromURI + ", toURI="
				+ toURI + ", srcIP=" + srcIP + ", srcPort=" + srcPort
				+ ", dateCreated=" + dateCreated + ", errorCode=" + errorCode
				+ ", userSaw=" + userSaw + ", dateLast=" + dateLast
				+ ", absorbed=" + absorbed + ", dropped=" + dropped + "]";
	}
}
