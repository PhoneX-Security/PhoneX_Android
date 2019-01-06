package net.phonex.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import net.phonex.R;
import net.phonex.pub.a.PjManager;
import net.phonex.sip.SipStatusCode;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.lock.activity.LockActivity;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

/**
 * fake activity, nothing is displayed except for AlertDialog
 * @author Miroc
 *
 */
public class CallErrorActivity extends LockActivity{
    public static final String GENERAL_ERROR = "GENERAL_ERROR";
    public static final String CUSTOM_ERROR = "CUSTOM_ERROR";
    public static final String CALL_ENDED_ERROR = "CALL_ENDED_ERROR";

    // static context - in order to obtain resources
    private static final String THIS_FILE = "CallErrorActivity"; 
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);         	

        Intent intent = getIntent();
    	// If no type, just return.
    	if (intent.hasExtra("TYPE")==false){
    		Log.wf(THIS_FILE, "Starting intent has no error; %s", getIntent());
    		return;
    	}

        final String type = intent.getStringExtra("TYPE");
        if (CUSTOM_ERROR.equals(type)){
            String m = intent.getStringExtra("MESSAGE");
            setContentView(R.layout.in_call_error);

            Log.ef(THIS_FILE, " problem with call, custom message: [%s] ", m);
            showDialog(getString(R.string.p_problem), m);

        } else if (GENERAL_ERROR.equals(type)) {
            String m = intent.getStringExtra("MESSAGE");
            setContentView(R.layout.in_call_error);

            Log.ef(THIS_FILE, " problem with call, code error message: [%s] ", m);
            showDialog(getString(R.string.p_problem), m != null ? getErrorMessage(m) : getString(R.string.p_problem_nonspecific));

        } else if (CALL_ENDED_ERROR.equals(type)) {
            CallErrorMessage errorObject = intent.getParcelableExtra("ERROR_OBJECT");
            if(!errorObject.isRelevantToLocalUser()){
                finish();
            } else {
                showCallEndedErrorMessage(errorObject);
            }
        }
    }

    private void showDialog(CharSequence title, CharSequence message){
        new AlertDialogWrapper.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                })
                .show();
    }

    private void showCallEndedErrorMessage(CallErrorMessage errorObject){
        Log.df(THIS_FILE, "showCallEndedErrorMessage; errorObject [%s]", MiscUtils.safeToString(errorObject));
        setContentView(R.layout.in_call_error);

        String notifTitle = getString(R.string.p_problem);
        String notifMessage = getErrorMessage(errorObject.statusCode);
        boolean useInfoIcon = false;

        switch (errorObject.statusCode) {
            // For some common errors, show only Toast
            case SipStatusCode.NOT_FOUND:
            case SipStatusCode.BUSY_HERE:
            case SipStatusCode.DECLINE:
            case SipStatusCode.REQUEST_TERMINATED:
            case SipStatusCode.PJSIP_SC_CALL_TSX_DOES_NOT_EXIST: // happens when we forcefully terminate the call
                Toast.makeText(this, notifMessage, Toast.LENGTH_LONG).show();
                finish();
                return;
        }

        if (errorObject.localByeCode != null && errorObject.localByeCode == PjManager.BYE_CAUSE_GSM_BUSY) {
            showDialog(notifTitle, getString(R.string.call_error_gsm_local));
            return;
        }

        if (errorObject.byeCauseCode != null && errorObject.byeCauseCode == PjManager.BYE_CAUSE_GSM_BUSY) {
            showDialog(notifTitle, getString(R.string.call_error_gsm_remote));
            return;
        }

        // For everything else (more important or critical errors), show alert dialog + error number
        notifMessage = String.format(getString(R.string.call_error_number), errorObject.statusCode) + ": " + notifMessage;
        showDialog(notifTitle, notifMessage);
    }

    private String getErrorMessage(int errorCode){
        String msg = null;
        switch (errorCode) {
            case SipStatusCode.FORBIDDEN:
                msg = getString(R.string.call_error_403);
                break;
            case SipStatusCode.NOT_FOUND:
                msg = getString(R.string.call_error_404);
                break;
            case SipStatusCode.REQUEST_TIMEOUT: //almost the same as decline
                msg = getString(R.string.call_error_408);
                break;
            case SipStatusCode.BUSY_HERE: //almost the same as decline
                msg = getString(R.string.call_error_486);
                break;
            case SipStatusCode.DECLINE:
                msg = getString(R.string.call_error_603);
                break;
            case SipStatusCode.GONE: // Gone
                msg = getString(R.string.call_error_410);
                break;
            case 477: // cannot send to next hop
                msg = getString(R.string.call_error_477);
                break;
            case SipStatusCode.TEMPORARILY_UNAVAILABLE: // temporarily unavailable
                msg = getString(R.string.call_error_480);
                break;
            case SipStatusCode.REQUEST_TERMINATED: // request terminated
                msg = getString(R.string.call_error_487);
                break;
            case SipStatusCode.SERVICE_UNAVAILABLE: // service unavailable
                msg = getString(R.string.call_error_503);
                break;
            case SipStatusCode.PJSIP_SC_CALL_TSX_DOES_NOT_EXIST: // can happen e.g. when call is ended forcefully (see pjsua_call_terminate)
                msg = getString(R.string.call_error_481);
                break;
            case SipStatusCode.NOT_IMPLEMENTED: // Not implemented / BlackBerry
                msg = getString(R.string.call_error_501);
                break;
            default:
                msg = getString(R.string.call_error_unspecific_msg);
                break;
        }
        return msg;
    }


    private String getErrorMessage(String error){
    	String msg = error;
    	try{    		
    		int errorCode = Integer.parseInt(error);
            String errorMessage = getErrorMessage(errorCode);
            if (errorMessage != null){
                msg = errorMessage;
            }

        } catch (Exception e) {
    		msg = "N/A";
			Log.wf(THIS_FILE, "Error code '%s' is unknown", error);
		}
    	
    	return msg;
    }

    /**
     * class for transferring error message together with other details (etc. if the given call is incoming/outgoing)
     */
    public static class CallErrorMessage implements Parcelable {
        public int statusCode;
        public boolean isIncoming;
        public boolean isMissedCall=false;
        public String remoteSip;
        public String remoteDisplayName;
        public int remoteId=-1;
        public Integer localByeCode;
        public Integer byeCauseCode;

        public CallErrorMessage(int statusCode, boolean isIncoming) {
            this.statusCode = statusCode;
            this.isIncoming = isIncoming;
        }

        /**
         * filter those messages that are not relevant to local user
         * @return boolean
         */
        public boolean isRelevantToLocalUser(){
            if (isIncoming){
                if (statusCode == SipStatusCode.DECLINE || statusCode == SipStatusCode.NOT_IMPLEMENTED
                        || statusCode == SipStatusCode.BUSY_HERE){
                    return false;
                }
            } else {
                if (statusCode == SipStatusCode.REQUEST_TERMINATED){
                    return false;
                }
            }

            return true;
        }

        protected CallErrorMessage(Parcel in) {
            statusCode = in.readInt();
            isIncoming = in.readByte() != 0x00;
            isMissedCall = in.readByte() != 0x00;
            remoteSip = in.readString();
            remoteDisplayName = in.readString();
            remoteId = in.readInt();
            byeCauseCode = (Integer)in.readValue(Integer.class.getClassLoader());
            localByeCode = (Integer)in.readValue(Integer.class.getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(statusCode);
            dest.writeByte((byte) (isIncoming ? 0x01 : 0x00));
            dest.writeByte((byte) (isMissedCall ? 0x01 : 0x00));
            dest.writeString(remoteSip);
            dest.writeString(remoteDisplayName);
            dest.writeInt(remoteId);
            dest.writeValue(byeCauseCode);
            dest.writeValue(localByeCode);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<CallErrorMessage> CREATOR = new Parcelable.Creator<CallErrorMessage>() {
            @Override
            public CallErrorMessage createFromParcel(Parcel in) {
                return new CallErrorMessage(in);
            }

            @Override
            public CallErrorMessage[] newArray(int size) {
                return new CallErrorMessage[size];
            }
        };

        @Override
        public String toString() {
            return "CallErrorMessage{" +
                    "statusCode=" + statusCode +
                    ", isIncoming=" + isIncoming +
                    ", isMissedCall=" + isMissedCall +
                    ", remoteSip='" + remoteSip + '\'' +
                    ", remoteDisplayName='" + remoteDisplayName + '\'' +
                    ", remoteId=" + remoteId +
                    ", localByeCode=" + localByeCode +
                    ", byeCauseCode=" + byeCauseCode +
                    '}';
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
