package net.phonex.ui.call;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import net.phonex.R;
import net.phonex.core.MediaState;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.pub.a.ICallActionListener;

import net.phonex.util.Log;

import butterknife.ButterKnife;
import butterknife.InjectView;


/**
 * Part of the InCall activity - bottom menu
 * Contains three basic buttons: settings, mute and speaker
 * TODO: add context menu: call information.
 * @author miroc
 *
 */
public class CallMenu extends LinearLayout{
	
	private static final String TAG = "InCallMenu";
	
	private ICallActionListener onTriggerListener;
	private SipCallSessionInfo currentCall;
	private MediaState lastMediaState;
	
	@InjectView(R.id.button_settings) ImageButton buttonSettings;
	@InjectView(R.id.button_mute) ImageButton buttonMute;
	@InjectView(R.id.button_bluetooth) ImageButton buttonBluetooth;
	@InjectView(R.id.button_speaker) ImageButton buttonSpeaker;
	
	private boolean callOngoing = false;

	public CallMenu(Context context, AttributeSet attrs) {
		super(context, attrs);		
		
		setOrientation(LinearLayout.HORIZONTAL);
		setGravity(Gravity.CENTER_HORIZONTAL);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.in_call_menu, this, true);

		ButterKnife.inject(this);

        buttonMute.setOnClickListener(v -> onMuteButtonClicked());
        buttonSpeaker.setOnClickListener(v -> onMuteSpeakerClicked());
		buttonBluetooth.setOnClickListener(v -> onBluetoothClicked());
		buttonSettings.setOnClickListener(v -> dispatchTriggerEvent(ICallActionListener.MEDIA_SETTINGS));
	}
	
	public CallMenu(Context context) {
		this(context, null);		
	}

    private void onMuteButtonClicked(){
        buttonMute.setSelected(!buttonMute.isSelected());
        if (buttonMute.isSelected()) {
            dispatchTriggerEvent(ICallActionListener.MUTE_ON);
        } else {
            dispatchTriggerEvent(ICallActionListener.MUTE_OFF);
        }
    }

    private void onMuteSpeakerClicked(){
        buttonSpeaker.setSelected(!buttonSpeaker.isSelected());
        if (buttonSpeaker.isSelected()) {
            dispatchTriggerEvent(ICallActionListener.SPEAKER_ON);
        } else {
            dispatchTriggerEvent(ICallActionListener.SPEAKER_OFF);
        }
    }

    private void onBluetoothClicked(){
        buttonBluetooth.setSelected(!buttonBluetooth.isSelected());
        if (buttonBluetooth.isSelected()) {
            dispatchTriggerEvent(ICallActionListener.BLUETOOTH_ON);
        } else {
            dispatchTriggerEvent(ICallActionListener.BLUETOOTH_OFF);
        }
    }
	
	public void setCallState(SipCallSessionInfo callInfo) {
		currentCall = callInfo;
		
		if(currentCall == null) {
			setVisibility(GONE);
			return;
		}
		
		int state = currentCall.getCallState();
		Log.df(TAG, "Mode is : %s, for session: %s", state, callInfo);
		switch (state) {
		case SipCallSession.InvState.INCOMING:
		    setVisibility(GONE);
			break;
        case SipCallSession.InvState.PREPARING:
		case SipCallSession.InvState.CALLING:
		case SipCallSession.InvState.CONNECTING:
        case SipCallSession.InvState.CONFIRMED:
		    setVisibility(VISIBLE);
			setEnabledMediaButtons(true);
			break;
		case SipCallSession.InvState.NULL:
		case SipCallSession.InvState.DISCONNECTED:
		    setVisibility(GONE);
			break;
		case SipCallSession.InvState.EARLY:
		default:
			if (currentCall.isIncoming()) {
			    setVisibility(GONE);
			} else {
			    setVisibility(VISIBLE);
				setEnabledMediaButtons(true);
			}
			break;
		}		
	}

	
	/**
	 * Registers a callback to be invoked when the user triggers an event.
	 * @param listener the OnTriggerListener to attach to this view
	 */
	public void setOnTriggerListener(ICallActionListener listener) {
		onTriggerListener = listener;
	}
	
	public void muteMicButtonState(boolean mute){
		if (buttonMute!=null){
			buttonMute.setVisibility(View.VISIBLE);
			buttonMute.setSelected(mute);
		}
	}
	
	public void setMediaState(MediaState mediaState) {
		// when state was changed, unlock speaker button
		if (buttonSpeaker!=null && (lastMediaState==null || (lastMediaState.isSpeakerphoneOn != mediaState.isSpeakerphoneOn))){
            // TODO locking of the button
//			buttonSpeaker.setLocked(false);
		}
		
		lastMediaState = mediaState;

        // Update menu
		boolean enabled, checked;
        
        // Mic
        if(lastMediaState == null) {
            enabled = callOngoing;
            checked = false;
        }else {
            enabled = callOngoing && lastMediaState.canMicrophoneMute;
            checked = lastMediaState.isMicrophoneMute;
        }

        if (buttonMute!=null){
        	buttonMute.setSelected(checked);
        }

        // Speaker
        Log.df(TAG, ">> Speaker %s", lastMediaState);
        if(lastMediaState == null) {
            enabled = callOngoing;
            checked = false;
        }else {
            Log.df(TAG, ">> Speaker %s", lastMediaState.isSpeakerphoneOn);
            enabled = callOngoing && lastMediaState.canSpeakerphoneOn;
            checked = lastMediaState.isSpeakerphoneOn;
        }
        
        if (buttonSpeaker!=null){
        	buttonSpeaker.setSelected(checked);
        }

        // Bluetooth
        Log.df(TAG, ">> Bluetooth %s", lastMediaState);
        if(lastMediaState == null) {
            enabled = callOngoing;
            checked = false;
        }else {
            Log.df(TAG, ">> Bluetooth %s", lastMediaState.isBluetoothScoOn);
            enabled = callOngoing && lastMediaState.isBluetoothSupported;
            checked = lastMediaState.isBluetoothScoOn;
        }

        if (buttonBluetooth!=null){
			buttonBluetooth.setSelected(checked);
        }
	}
	
	public void setEnabledMediaButtons(boolean isInCall) {
        callOngoing = isInCall;
        setMediaState(lastMediaState);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		// Finalize object style
		setEnabledMediaButtons(false);
	}
	
	private void dispatchTriggerEvent(int whichHandle) {
		if (onTriggerListener != null) {
			onTriggerListener.onCallAction(whichHandle, currentCall);
		}
	}
}