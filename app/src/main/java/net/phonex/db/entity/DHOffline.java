package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import net.phonex.core.Constants;
import net.phonex.db.DBBulkDeleter;
import net.phonex.db.DBProvider;
import net.phonex.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * API for accessing stored Diffie-Hellman public and private keys,
 * together with their prime group ID (4096 B prime groups are pre-generated and stored in assets folder)
 * @author miroc
 *
 */
public class DHOffline {
	public static final String TABLE = "dh_offline";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_SIP = "sip";
    public static final String FIELD_PUBLIC_KEY = "publicKey";
    public static final String FIELD_PRIVATE_KEY = "privateKey";
    public static final String FIELD_GROUP_NUMBER = "groupNumber";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_EXPIRE = "dateExpire";
    public static final String FIELD_NONCE1 = "nonce1";
    public static final String FIELD_NONCE2 = "nonce2";
    public static final String FIELD_ACERT_HASH = "aCertHash";
    public static final String DATE_FORMAT = "YYYY-MM-DD HH:MM:SS.SSS";
    
    public final static long INVALID_ID = -1;
    
    public static final String[] FULL_PROJECTION = new String[] {
    	FIELD_ID, FIELD_SIP, FIELD_PUBLIC_KEY, FIELD_PRIVATE_KEY, FIELD_GROUP_NUMBER, FIELD_DATE_CREATED,
    	FIELD_DATE_EXPIRE, FIELD_NONCE1, FIELD_NONCE2, FIELD_ACERT_HASH
    };
    
    public static final String[] LIGHT_PROJECTION = new String[] {
    	FIELD_ID, FIELD_SIP, FIELD_DATE_CREATED,
    	FIELD_DATE_EXPIRE, FIELD_NONCE1, FIELD_NONCE2, FIELD_ACERT_HASH
    };
    
    /**
     * Uri for content provider of certificate
     */
    public static final Uri DH_OFFLINE_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base uri for sip message content provider.<br/>
     * To append with {@link #FIELD_ID}
     * 
     * @see ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri DH_OFFLINE_ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    //SQL Create command for DH Offline table
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + DHOffline.TABLE
            + " ("
            + DHOffline.FIELD_ID 				+ " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + DHOffline.FIELD_SIP				+ " TEXT, "
            + DHOffline.FIELD_PUBLIC_KEY		+ " TEXT, "
            + DHOffline.FIELD_PRIVATE_KEY		+ " TEXT, "
            + DHOffline.FIELD_GROUP_NUMBER 		+ " INTEGER, "
            + DHOffline.FIELD_DATE_CREATED		+ " INTEGER, "
            + DHOffline.FIELD_DATE_EXPIRE		+ " INTEGER, "
            + DHOffline.FIELD_NONCE1			+ " TEXT, "
            + DHOffline.FIELD_NONCE2			+ " TEXT, "
            + DHOffline.FIELD_ACERT_HASH		+ " TEXT "
            + ");";

	private static final String THIS_FILE = "DHOffline";
	
	// Locale for date formating
	protected Locale    locale     = Locale.getDefault();
	
	protected Integer   id         = Integer.valueOf((int)INVALID_ID);
	protected String 	sip;
	protected String    publicKey;
	protected String    privateKey;
	protected int    	groupNumber;
	protected Date      dateCreated;
	protected Date      dateExpire;
	protected String    nonce1;
	protected String    nonce2;
	protected String 	aCertHash;
	
	public DHOffline() {
		
    }	
	 /**
     * Construct a sip profile wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link #SIP_SIGNATURE_WARNING}.
     * 
     * @param c the cursor to unpack
     */
    public DHOffline(Cursor c) {
        super();
        createFromDb(c);
    }
    
    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public DHOffline(Parcel in) {
        id          = in.readInt();
        sip 		= in.readString();
        publicKey 	= in.readString();
        privateKey 	= in.readString();
        groupNumber = in.readInt();    	
    	dateCreated = new Date(in.readLong());
    	dateExpire  = new Date(in.readLong());
    	nonce1      = in.readString();
    	nonce2      = in.readString();
    	aCertHash   = in.readString();
    }

    /**
     * Create account wrapper with cursor datas.
     * 
     * @param c cursor on the database
     */
    private final void createFromDb(Cursor c) {     
        this.createFromCursor(c);
    }
    
    /**
     * OldSchool method of initialization from cursor
     * @param c
     */
    private final void createFromCursor(Cursor c){    	
    	int colCount = c.getColumnCount();
    	for(int i=0; i<colCount; i++){
    		String colname = c.getColumnName(i);
    		if (FIELD_ID.equals(colname)){
    			this.id = c.getInt(i);
    		} else if (FIELD_SIP.equals(colname)){
    			this.sip = c.getString(i);
    		} else if (FIELD_PUBLIC_KEY.equals(colname)){
    			this.publicKey = c.getString(i);
    		} else if (FIELD_PRIVATE_KEY.equals(colname)){
    			this.privateKey = c.getString(i);    			
    		} else if (FIELD_GROUP_NUMBER.equals(colname)){
    			this.groupNumber = c.getInt(i);    		
    		} else if (FIELD_DATE_CREATED.equals(colname)){
    			this.dateCreated = new Date(c.getLong(i));
    		} else if (FIELD_DATE_EXPIRE.equals(colname)){
    			this.dateExpire = new Date(c.getLong(i));
    		} else if (FIELD_NONCE1.equals(colname)){
    			this.nonce1 = c.getString(i);
    		} else if (FIELD_NONCE2.equals(colname)){
    			this.nonce2 = c.getString(i);
    		} else if (FIELD_ACERT_HASH.equals(colname)){
    			this.aCertHash = c.getString(i);
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
        Long tmp_l;
        String tmp_s;
        
        // Application specific settings
        tmp_i = args.getAsInteger(FIELD_ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_s = args.getAsString(FIELD_SIP);
        if (tmp_s != null) {
            sip = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_PUBLIC_KEY);
        if (tmp_s != null) {
            publicKey = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_PRIVATE_KEY);
        if (tmp_s != null) {
            privateKey = tmp_s;
        }
        tmp_i = args.getAsInteger(FIELD_GROUP_NUMBER);
        if (tmp_i != null) {
            groupNumber = tmp_i;
        }
        tmp_l = args.getAsLong(FIELD_DATE_CREATED);
        if(tmp_l != null){
        	dateCreated = new Date(tmp_l);
        }
        tmp_l = args.getAsLong(FIELD_DATE_EXPIRE);
        if(tmp_l != null){
        	dateExpire = new Date(tmp_l);
        }
        tmp_s = args.getAsString(FIELD_NONCE1);
        if (tmp_s != null) {
            nonce1 = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_NONCE2);
        if (tmp_s != null) {
            nonce2 = tmp_s;
        }
        tmp_s = args.getAsString(FIELD_ACERT_HASH);
        if (tmp_s != null) {
            aCertHash = tmp_s;
        }
    }
    
    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id!=null && id!=INVALID_ID) {
            args.put(FIELD_ID, id);
        }
        args.put(FIELD_SIP, this.sip);
        args.put(FIELD_PUBLIC_KEY, this.publicKey);
        args.put(FIELD_PRIVATE_KEY, this.privateKey);
        args.put(FIELD_GROUP_NUMBER, this.groupNumber);        
        args.put(FIELD_DATE_CREATED, dateCreated.getTime());
        args.put(FIELD_DATE_EXPIRE, dateExpire.getTime());
        args.put(FIELD_NONCE1, this.nonce1);
        args.put(FIELD_NONCE2, this.nonce2);
        args.put(FIELD_ACERT_HASH, this.aCertHash);
        
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
    public static Date parseDate(String date){
    	SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
    	Date dateRet = null;
    	try {
    		dateRet = iso8601Format.parse(date);
    	} catch(ParseException e){
    		
    	}
    	
    	return dateRet;
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
	public Date getDateExpire() {
		return dateExpire;
	}
	public String getDateExpireFormatted(){
		SimpleDateFormat iso8601Format = new SimpleDateFormat(DATE_FORMAT, locale);
        return iso8601Format.format(dateExpire);
	}
	public void setDateExpire(Date dateExpire) {
		this.dateExpire = dateExpire;
	}
	public Locale getLocale() {
		return locale;
	}
	public void setLocale(Locale locale) {
		this.locale = locale;
	}
	public String getPublicKey() {
		return publicKey;
	}
	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}
	public String getPrivateKey() {
		return privateKey;
	}
	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}
	public String getSip() {
		return sip;
	}
	public void setSip(String sip) {
		this.sip = sip;
	}
	public int getGroupNumber() {
		return groupNumber;
	}
	public void setGroupNumber(int groupNumber) {
		this.groupNumber = groupNumber;
	}
	public String getNonce1() {
		return nonce1;
	}
	public void setNonce1(String nonce1) {
		this.nonce1 = nonce1;
	}
	public String getNonce2() {
		return nonce2;
	}
	public void setNonce2(String nonce2) {
		this.nonce2 = nonce2;
	}
	public String getaCertHash() {
		return aCertHash;
	}
	public void setaCertHash(String aCertHash) {
		this.aCertHash = aCertHash;
	}
	@Override
	public String toString() {
		return "DHOffline [locale=" + locale + ", id=" + id + ", sip=" + sip
				+ ", publicKey=" + publicKey + ", privateKey=" + privateKey
				+ ", groupNumber=" + groupNumber + ", dateCreated="
				+ dateCreated + ", dateExpire=" + dateExpire + ", nonce1="
				+ nonce1 + ", nonce2=" + nonce2 + ", aCertHash=" + aCertHash
				+ "]";
	}
	
	/**
	 * Looks up the DH key with given nonce2.
	 * 
	 * @param cr
	 * @param nonce2
	 * @return
	 */
	public static DHOffline getByNonce2(ContentResolver cr, String nonce2){		     				        
       Cursor c = cr.query(
    		DHOffline.DH_OFFLINE_URI,
    		DHOffline.FULL_PROJECTION,
    		DHOffline.FIELD_NONCE2 + "=?",
                       new String[] { nonce2 },
       		null);
       
       if (c==null){
    	   return null;
       }
       
       try {
    	   if (c.moveToFirst()){
    		   DHOffline item = new DHOffline(c);
    		   return item;
    	   }
       } catch (Exception e) {
           Log.e(THIS_FILE, "Error while getting DHOffline item", e);
           return null;
       } finally {
           c.close();
       }
       
       return null;
	}
	
	/**
	 * Deletes key corresponding to the given user with given nonce from database.
	 * 
	 * @param cr
	 * @param nonce2
	 * @param user
	 */
	public static int delete(ContentResolver cr, String nonce2, String user){
		try {
			return cr.delete(
					DH_OFFLINE_URI, 
					FIELD_NONCE2 + "=? AND " + FIELD_SIP + "=?", 
					new String[]{nonce2, user});
			
		} catch(Exception e){
			Log.e(THIS_FILE, "Cannot delete key", e);
		}
		
		return -1;
	}

    /**
     * Removes a DHKey with given nonce2
     *
     * @param nonce2
     * @return
     */
    public static boolean removeDHKey(ContentResolver cr, String nonce2){
        try {
            String selection = DHOffline.FIELD_NONCE2 + "=?";
            String[] selectionArgs = new String[] {nonce2};

            int d = cr.delete(
                    DHOffline.DH_OFFLINE_URI,
                    selection,
                    selectionArgs);

            return d>0;
        } catch(Exception e){
            Log.ef(THIS_FILE, e, "Exception during removing DHKey with nonce2: %s", nonce2);
            return false;
        }
    }

    /**
     * Removes a DHKey with given nonce2s
     *
     * @param nonces
     * @return
     */
    public static int removeDHKeys(ContentResolver cr, List<String> nonces){
        if (nonces==null || nonces.isEmpty()){
            return 0;
        }

        try {
            final DBBulkDeleter deleter = new DBBulkDeleter(cr, DHOffline.DH_OFFLINE_URI, DHOffline.FIELD_NONCE2);
            deleter.add(nonces);
            return deleter.finish();

        } catch(Exception e){
            Log.e(THIS_FILE, "Exception during removing DHKey", e);
            return 0;
        }
    }

    public static class RemoveDhKeyQuery {
        public String where;
        public String[] whereArgs;
    }

    /**
     * Removes DH keys that are either a) older than given date
     * OR b) does not have given certificate hash OR both OR just
     * equals the sip.
     *
     * Returns number of removed entries.
     */
    public static RemoveDhKeyQuery removeDHKeys(String sip, Date olderThan, String certHash, Date expirationLimit){
        RemoveDhKeyQuery res = new RemoveDhKeyQuery();
        try {
            ArrayList<String> args = new ArrayList<String>();

            String selection = DHOffline.FIELD_SIP + "=? ";
            args.add(sip);

            if (olderThan!=null && certHash!=null){
                selection += " AND (((" + DHOffline.FIELD_DATE_CREATED + "< ?) OR (" + DHOffline.FIELD_ACERT_HASH + "!= ?))";
                args.add(String.valueOf(olderThan.getTime()));
                args.add(certHash);

            } else if (olderThan!=null){
                selection += " AND ((" + DHOffline.FIELD_DATE_CREATED + "< ?)";
                args.add(String.valueOf(olderThan.getTime()));

            } else if (certHash!=null){
                selection += " AND ((" + DHOffline.FIELD_ACERT_HASH + "!= ?)";
                args.add(certHash);

            } else if (expirationLimit!=null){
                selection += " AND ( 1 ";
            }

            // Expiration
            if (expirationLimit!=null){
                selection += " OR " + DHOffline.FIELD_DATE_EXPIRE + "< ? )" ;
                args.add(String.valueOf(expirationLimit.getTime()));
            } else {
                // Closing the brace for AND condition
                selection += ")";
            }
            String[] selectionArgs = args.toArray(new String[args.size()]);

            res.where = selection;
            res.whereArgs = selectionArgs;
            return res;

        } catch(Exception e){
            Log.ef(THIS_FILE, e, "Exception during removing DHKey olderThan=%s; and certHash=%s", olderThan, certHash);
            return null;
        }
    }

    /**
     * Removes DH keys that are either a) older than given date
     * OR b) does not have given certificate hash OR both OR just
     * equals the sip.
     *
     * Returns number of removed entries.
     *
     * @param sip
     * @param olderThan
     * @param certHash
     * @param expirationLimit
     * @return
     */
    public static int removeDHKeys(ContentResolver cr, String sip, Date olderThan, String certHash, Date expirationLimit){
        int removed = 0;

        try {
            final RemoveDhKeyQuery dat = removeDHKeys(sip, olderThan, certHash, expirationLimit);

            removed = cr.delete(
                    DHOffline.DH_OFFLINE_URI,
                    dat.where,
                    dat.whereArgs);

            return removed;
        } catch(Exception e){
            Log.ef(THIS_FILE, e, "Exception during removing DHKey olderThan=%s; and certHash=%s", olderThan, certHash);
            return removed;
        }
    }
}
