package net.phonex.db.entity;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import net.phonex.sip.SipStatusCode;
import net.phonex.util.Log;

import java.io.Serializable;
import java.util.Comparator;


/**
 * Holder for a sip profile state.<br/>
 * It allows to unpack content values from registration/activation status of a {@link SipProfile}.
 * it's internally represented as in-memory table, see DBProvider.profilesStatus
 */
public class SipProfileState implements Parcelable, Serializable{
	private static final String THIS_FILE="SipProfileState";

    /**
     * Primary key for serialization of the object.
     */
	private static final long serialVersionUID = -1169766490870514L;
	public int primaryKey = -1;
	private int databaseId;
	private int pjsuaId;
	private String accountManager;
	private boolean active;
	private int statusCode;
	private int statusType=0;
	private String statusText;
	private int addedStatus;
	private int expires;
	private String displayName;
	private int priority;
	private String regUri = "";
	private boolean inCall=false;
	private boolean inGsmCall=false;

	/**
	 * Account id.<br/>
	 * Identifier of the SIP account associated. It's the identifier of the account for the API.
	 * 
	 * @see SipProfile#FIELD_ID
	 * @see Integer
	 */
	public final static String ACCOUNT_ID = "account_id";
	/**
	 * Identifier for underlying sip stack. <br/>
	 * This is an internal identifier you normally don't need to use when using the api from an external application.
	 * 
	 * @see Integer
	 */
	public final static String PJSUA_ID = "pjsua_id";
	/**
	 * Wizard key. <br/>
	 * Wizard identifier associated to the account. This is a shortcut to not have to query {@link SipProfile} database
	 * 
	 * @see String
	 */
	public final static String WIZARD = "wizard";
	/**
	 * Activation state.<br/>
	 * Active state of the account. This is a shortcut to not have to query {@link SipProfile} database
	 * 
	 * @see Boolean
	 */
	public final static String ACTIVE = "active";
	/**
	 * Status code of the latest registration.<br/>
	 * SIP code of latest registration.
	 * 
	 * @see Integer
	 */
	public final static String STATUS_CODE = "status_code";
	/**
	 * Status type (e.g., busy).
	 * 
	 * @see Integer
	 */
	public final static String STATUS_TYPE = "status_type";
	/**
	 * Status comment of latest registration.<br/>
	 * Sip comment of latest registration.
	 * 
	 * @see String
	 */
	public final static String STATUS_TEXT = "status_text";
	/**
	 * Status of sip stack adding of the account.<br/>
	 * When the application adds the account to the stack it may fails if the sip profile is invalid.
	 * 
	 * @see Integer
	 */
	public final static String ADDED_STATUS = "added_status";
	/**
	 * Latest know expires time. <br/>
	 * Expires value of latest registration. It's actually useful to detect that it was unregister testing 0 value. 
	 * Else it's not necessarily relevant information.
	 * 
	 * @see Integer
	 */
	public final static String EXPIRES = "expires";
	/**
	 * Display name of the account.<br.>
	 * This is a shortcut to not have to query {@link SipProfile} database
	 */
	public final static String DISPLAY_NAME = "display_name";
	/**
     * Priority of the account.<br.>
     * This is a shortcut to not have to query {@link SipProfile} database
     */
	public final static String PRIORITY = "priority";
	/**
     * Registration uri of the account.<br.>
     * This is a shortcut to not have to query {@link SipProfile} database
     */
	public final static String REG_URI = "reg_uri";
	
	public final static String IN_CALL = "inCall";
	public final static String IN_GSM_CALL = "inGsmCall";


	public static final String [] FULL_PROJECTION = new String[] {
		ACCOUNT_ID, PJSUA_ID, WIZARD, ACTIVE, STATUS_CODE, STATUS_TYPE, STATUS_TEXT,
			EXPIRES, DISPLAY_NAME, PRIORITY, REG_URI, IN_CALL, IN_GSM_CALL
	};
	
	
	public SipProfileState(Parcel in) {
		readFromParcel(in);
	}
	
	/**
     * Should not be used for external use of the API.
	 * Default constructor.
	 */
	public SipProfileState() {
		//Set default values
		addedStatus = -1;
		pjsuaId = -1;
		statusCode = -1;
		statusType = 0;
		statusText = "";
		expires = 0;
		inCall=false;
		inGsmCall=false;
	}
	/**
     * Should not be used for external use of the API.
	 * Constructor on the top of a sip account.
	 * 
	 * @param account The sip profile to associate this wrapper info to.
	 */
	public SipProfileState(SipProfile account) {
		this();
		
		databaseId = (int) account.getId();
		accountManager = account.getAccountManager();
		active = account.isActive();
		displayName = account.getDisplay_name();
		priority = account.getPriority();
		regUri = account.getReg_uri();
		
	}

    /**
     * Construct a sip state wrapper from a cursor retrieved with a
     * {@link ContentProvider} query on {@link SipProfile#ACCOUNT_STATUS_URI}.
     * 
     * @param c the cursor to unpack
     */
	public SipProfileState(Cursor c) {
		super();
		createFromDb(c);
	}

    /**
     * @see Parcelable#describeContents()
     */
	@Override
	public int describeContents() {
		return 0;
	}

	private void readFromParcel(Parcel in) {
		primaryKey = in.readInt();
		databaseId = in.readInt();
		pjsuaId = in.readInt();
		accountManager = in.readString();
		active = (in.readInt() == 1);
		statusCode = in.readInt();
		statusType = in.readInt();
		statusText = in.readString();
		addedStatus = in.readInt();
		expires = in.readInt();
		displayName = in.readString();
		priority = in.readInt();
		regUri = in.readString();
		inCall = (in.readInt() == 1);
		inGsmCall = (in.readInt() == 1);
	}

    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
	@Override
	public void writeToParcel(Parcel out, int arg1) {
		out.writeInt(primaryKey);
		out.writeInt(databaseId);
		out.writeInt(pjsuaId);
		out.writeString(accountManager);
		out.writeInt( (active?1:0) );
		out.writeInt(statusCode);
		out.writeInt(statusType);
		out.writeString(statusText);
		out.writeInt(addedStatus);
		out.writeInt(expires);
		out.writeString(displayName);
		out.writeInt(priority);
		out.writeString(regUri);
		out.writeInt( (inCall?1:0) );
		out.writeInt( (inGsmCall?1:0) );
	}
	

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
	public static final Parcelable.Creator<SipProfileState> CREATOR = new Parcelable.Creator<SipProfileState>() {
		public SipProfileState createFromParcel(Parcel in) {
			return new SipProfileState(in);
		}

		public SipProfileState[] newArray(int size) {
			return new SipProfileState[size];
		}
	};
	
	

	/** 
	 * Fill account state object from cursor.
	 * @param c cursor on the database queried from {@link SipProfile#ACCOUNT_STATUS_URI}
	 */
	public final void createFromDb(Cursor c) {
		ContentValues args = new ContentValues();
		DatabaseUtils.cursorRowToContentValues(c, args);
		createFromContentValue(args);
	}

    /** 
     * Fill account state object from content values.
     * @param args content values to wrap.
     */
	public final void createFromContentValue(ContentValues args) {
		Integer tmp_i;
		String tmp_s;
		Boolean tmp_b;
		
		tmp_i = args.getAsInteger(ACCOUNT_ID);
		if(tmp_i != null) {
			databaseId = tmp_i;
		}
		tmp_i = args.getAsInteger(PJSUA_ID);
		if(tmp_i != null) {
			pjsuaId = tmp_i;
		}
		tmp_s = args.getAsString(WIZARD);
		if(tmp_s != null) {
			accountManager = tmp_s;
		}
		tmp_b = args.getAsBoolean(ACTIVE);
		if(tmp_b != null) {
			active = tmp_b;
		}
		tmp_i = args.getAsInteger(STATUS_CODE);
		if(tmp_i != null) {
			statusCode = tmp_i;
		}
		tmp_i = args.getAsInteger(STATUS_TYPE);
		if(tmp_i != null) {
			statusType = tmp_i;
		}
		tmp_s = args.getAsString(STATUS_TEXT);
		if(tmp_s != null) {
			statusText = tmp_s;
		}
		tmp_i = args.getAsInteger(ADDED_STATUS);
		if(tmp_i != null) {
			addedStatus = tmp_i;
		}
		tmp_i = args.getAsInteger(EXPIRES);
		if(tmp_i != null) {
			expires = tmp_i;
		}
		tmp_s = args.getAsString(DISPLAY_NAME);
		if(tmp_s != null) {
			displayName = tmp_s;
		}
		tmp_s = args.getAsString(REG_URI);
		if(tmp_s != null) {
			regUri = tmp_s;
		}
		tmp_i = args.getAsInteger(PRIORITY);
		if(tmp_i != null) {
			priority = tmp_i;
		}
		tmp_b = args.getAsBoolean(IN_CALL);
		if(tmp_b != null) {
			inCall = tmp_b;
		}
	}

    /**
     * Should not be used for external use of the API.
     * Produce content value from the wrapper.
     * 
     * @return Complete content values from the current wrapper around sip
     *         profile state.
     */
	public ContentValues getAsContentValue() {
		ContentValues cv = new ContentValues();
		cv.put(ACCOUNT_ID, databaseId);
		cv.put(ACTIVE, active);
		cv.put(ADDED_STATUS, addedStatus);
		cv.put(DISPLAY_NAME, displayName);
		cv.put(EXPIRES, expires);
		cv.put(PJSUA_ID, pjsuaId);
		cv.put(PRIORITY, priority);
		cv.put(REG_URI, regUri);
		cv.put(STATUS_CODE, statusCode);
		cv.put(STATUS_TYPE, statusType);
		cv.put(STATUS_TEXT, statusText);
		cv.put(WIZARD, accountManager);
		cv.put(IN_CALL, inCall);
		cv.put(IN_GSM_CALL, inGsmCall);
		return cv;
	}

	/**
	 * @param databaseId the databaseId to set
	 */
	public void setDatabaseId(int databaseId) {
		this.databaseId = databaseId;
	}

	/**
     * Get the identifier identifier of the account that this state is linked to.
	 * @return the accountId identifier of the account : {@link #ACCOUNT_ID}
	 */
	public int getAccountId() {
		return databaseId;
	}

	/**
     * Should not be used for external use of the API.
	 * @param pjsuaId the pjsuaId to set
	 */
	public void setPjsuaId(int pjsuaId) {
		this.pjsuaId = pjsuaId;
	}

	/**
	 * Should not be used for external use of the API.
	 * @return the pjsuaId {@link #PJSUA_ID}
	 */
	public int getPjsuaId() {
		return pjsuaId;
	}

	/**
     * Should not be used for external use of the API.
	 * @param accountManager the accountManager to set
	 */
	public void setAccountManager(String accountManager) {
		this.accountManager = accountManager;
	}

	/**
	 * @return the accountManager {@link #WIZARD}
	 */
	public String getAccountManager() {
		return accountManager;
	}

	/**
     * Should not be used for external use of the API.
	 * @param active the active to set
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * @return the active {@link #ACTIVE}
	 */
	public boolean isActive() {
		return active;
	}

	/**
     * Should not be used for external use of the API.
	 * @param statusCode the statusCode to set
	 */
	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	/**
	 * @return the statusCode {@link #STATUS_TEXT}
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
     * Should not be used for external use of the API.
	 * @param statusText the statusText to set
	 */
	public void setStatusText(String statusText) {
		this.statusText = statusText;
	}

	/**
	 * @return the statusText {@link #STATUS_TEXT}
	 */
	public String getStatusText() {
		return statusText;
	}


	/**
     * Should not be used for external use of the API.
	 * @param addedStatus the addedStatus to set
	 */
	public void setAddedStatus(int addedStatus) {
		this.addedStatus = addedStatus;
	}


	/**
	 * @return the addedStatus {@link #ADDED_STATUS}
	 */
	public int getAddedStatus() {
		return addedStatus;
	}


	/**
     * Should not be used for external use of the API.
	 * @param expires the expires to set
	 */
	public void setExpires(int expires) {
		this.expires = expires;
	}


	/**
	 * @return the expires {@link #EXPIRES}
	 */
	public int getExpires() {
		return expires;
	}
    
    /**
     * @return the display name {@link #DISPLAY_NAME}
     */
	public CharSequence getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * @return the priority {@link #PRIORITY}
	 */
	public int getPriority() {
		return priority;
	}
	/**
     * Should not be used for external use of the API.
	 * @param priority
	 */
	public void setPriority(int priority) {
		this.priority = priority;
	}

	/**
	 * @return the regUri {@link #REG_URI}
	 */
	public String getRegUri() {
		return regUri;
	}

	/**
	 * Is the account added to sip stack yet?
	 * @return true if the account has been added to sip stack and has a sip stack id.
	 */
	public boolean isAddedToStack() {
		return pjsuaId != -1;
	}
	
	/**
	 * @return the statusType
	 */
	public int getStatusType() {
		return statusType;
	}

	/**
	 * @param statusType the statusType to set
	 */
	public void setStatusType(int statusType) {
		this.statusType = statusType;
	}
	
	/**
	 * @return the inCall
	 */
	public boolean isInCall() {
		return inCall;
	}

	public boolean isInGsmCall() {
		return inGsmCall;
	}

	public void setInGsmCall(boolean inGsmCall) {
		this.inGsmCall = inGsmCall;
	}

	/**
	 * Is the account valid for sip calls?
	 * @return true if it should be possible to make a call using the associated account.
	 */
	public boolean isValidForCall() {
		if(active) {
			if(TextUtils.isEmpty(getRegUri())) {
				return true;
			}
			return (isAddedToStack() && getStatusCode() == SipStatusCode.OK && getExpires() > 0);
		}
		return false;
	}
	
	/**
	 * Loads profile from content provider by id.
	 * 
	 * @param ctxt
	 * @param state OPTIONAL, if provided, this state is updated from information in DB.
	 * @param id
	 * @return
	 */
	public static SipProfileState getById(Context ctxt, SipProfileState state, long id){
		SipProfileState ret = state;
		
		Cursor c = ctxt.getContentResolver().query(
				ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, id),
				null, null, null, null);
		
        if (c != null) {
            try {
                if (c.getCount() > 0) {
                    c.moveToFirst();
                    if (ret==null){
                    	ret = new SipProfileState(c);
                    } else {
                    	ret.createFromDb(c);
                    }
                }
            } catch (Exception e) {
                Log.e(THIS_FILE, "Error on looping over sip profiles states", e);
            } finally {
                c.close();
            }
        }
        
        return ret;
	}

    public static SipProfileState getProfileState(Context ctxt, long accId){
        if (ctxt == null){
            Log.ef(THIS_FILE, "Trying to retrieve ProfileState for account ID [%d], but Context is null.", accId);
            return null;
        }

        SipProfile account = SipProfile.getProfileFromDbId(ctxt, accId, SipProfile.ACC_PROJECTION);

        if (account == null) {
            return null;
        }
        if (account.getId() == SipProfile.INVALID_ID) {
            return null;
        }
        SipProfileState accountInfo = new SipProfileState(account);
        accountInfo = SipProfileState.getById(ctxt, accountInfo, account.getId());
        return accountInfo;
    }

    /**
	 * Updates profile state with content values provided, if is added to stack. Otherwise ignored.
	 * @param ctxt
	 * @param id
	 * @param cv
	 */
	public static int update(Context ctxt, long id, ContentValues cv){
		SipProfileState state = getById(ctxt, null, id);
		if (state==null || !state.isAddedToStack()){
			return -1;
		}
		
        return ctxt.getContentResolver().update(
                ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, id),
                cv, null, null);
	}
	
	/**
	 * Compare accounts profile states.
	 * @return a comparator instance to compare profile states by priorities.
	 */
	public final static Comparator<SipProfileState> getComparator(){
		return ACC_INFO_COMPARATOR;
	}


	private static final Comparator<SipProfileState> ACC_INFO_COMPARATOR = new Comparator<SipProfileState>() {
		@Override
		public int compare(SipProfileState infos1,SipProfileState infos2) {
			if (infos1 != null && infos2 != null) {

				int c1 = infos1.getPriority();
				int c2 = infos2.getPriority();

				if (c1 > c2) {
					return 1;
				}
				if (c1 < c2) {
					return -1;
				}
			}

			return 0;
		}
	};


	@Override
	public String toString() {
		return "SipProfileState{" +
				"primaryKey=" + primaryKey +
				", databaseId=" + databaseId +
				", pjsuaId=" + pjsuaId +
				", accountManager='" + accountManager + '\'' +
				", active=" + active +
				", statusCode=" + statusCode +
				", statusType=" + statusType +
				", statusText='" + statusText + '\'' +
				", addedStatus=" + addedStatus +
				", expires=" + expires +
				", displayName='" + displayName + '\'' +
				", priority=" + priority +
				", regUri='" + regUri + '\'' +
				", inCall=" + inCall +
				", inGsmCall=" + inGsmCall +
				'}';
	}
}
