package net.phonex.ui.actionbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipProfile;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.service.MyPresenceManager;
import net.phonex.ui.customViews.StatusSpinnerAdapter;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

/**
 * View displays user presence status in ActionBar
 * Created by miroc on 27.12.14.
 */
public class StatusView extends LinearLayout implements AdapterView.OnItemSelectedListener{
    private static final String TAG = "StatusView";

    private RelativeLayout iconNotRegistered;
    private Spinner spinner;

    private boolean isRegistered = false;
    private boolean isConnected = true;

    private boolean spinnerSelectionInitialized = false;

    public StatusView(Context context){
        this(context, null);
    }

    public StatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.vf(TAG, "StatusView constructor");

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.actionbar_status, this, true);

        setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        iconNotRegistered = (RelativeLayout) findViewById(R.id.loadingPanel);
        spinner = (Spinner) findViewById(R.id.status_spinner);
        spinner.setOnItemSelectedListener(this);

        StatusSpinnerAdapter spinnerAdapter = new StatusSpinnerAdapter(context);
        spinner.setAdapter(spinnerAdapter);
        setRegistered(MyPresenceManager.checkRegistrationState(getContext(), SipProfile.USER_ID));

        updateStateAndView();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View arg1, int position, long arg3) {
        try {
            PushNotifications.PresencePush.Status status = MyPresenceManager.getPresenceState(getContext(), SipProfile.USER_ID);
            int oldStatusValue = status.getNumber();
            Integer statusValue = (Integer) parent.getItemAtPosition(position);
            Log.df(TAG, "onItemSelected; oldStatusValue=%d, statusValue=%d", oldStatusValue, statusValue);
            if (oldStatusValue != statusValue){
                AnalyticsReporter.fromApplicationContext(getContext().getApplicationContext()).buttonClick(AppButtons.CHANGE_STATUS);
                MyPresenceManager.updatePresenceState(getContext(), SipProfile.USER_ID, statusValue);
            }
        } catch (Exception e){
            Log.ef(TAG, e, "Error while switching status");
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    public void setRegistered(boolean isRegistered) {
        Log.vf(TAG, "setRegistered [%s]", isRegistered);
        this.isRegistered = isRegistered;
        updateStateAndView();
    }

    public void setConnected(boolean isConnected) {
        Log.vf(TAG, "setConnected [%s]", isConnected);
        this.isConnected = isConnected;
        updateStateAndView();
    }

    /**
     * update view state of Spinner View in Action Bar according to registration and network connection state
     */
    private void updateStateAndView() {
        if (isRegistered && isConnected && !spinnerSelectionInitialized){
            // initial registration state only after user is connected and registered
            initSpinnerSelection();
        }
        updateView();
    }

    private void initSpinnerSelection(){
        Log.inf(TAG, "init spinner state");
        spinnerSelectionInitialized = true;
        setRegistered(MyPresenceManager.checkRegistrationState(getContext(), SipProfile.USER_ID));
        setSpinnerSelection(MyPresenceManager.getPresenceState(getContext(), SipProfile.USER_ID));
    }

    private void updateView(){
        Log.vf(TAG, "updating statusView state; registered [%s], connected [%s]", String.valueOf(isRegistered), String.valueOf(isConnected));
        if (isRegistered && isConnected) {
            spinner.setEnabled(true);
            iconNotRegistered.setVisibility(View.GONE);
            spinner.setVisibility(View.VISIBLE);
        } else {
            spinner.setEnabled(false);
            spinner.setVisibility(View.GONE);
            iconNotRegistered.setVisibility(View.VISIBLE);
        }
    }

    private void setSpinnerSelection(PushNotifications.PresencePush.Status presence){
        SpinnerAdapter adapter = spinner.getAdapter();
        for (int position=0; position < adapter.getCount(); position++){
            Integer statusValue = (Integer) adapter.getItem(position);
            if (presence.getNumber() == statusValue){
                spinner.setSelection(position);
            }
        }
    }

    public void onNetworkConnected() {
        setConnected(true);
    }

    public void onNetworkDisconnected() {
        setConnected(false);
    }

    public class RegistrationChangeListener extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.vf(TAG, "RegistrationChangeListener: receiving intent [%s]", intent == null ? "null" : intent.toString());
            if (intent.getAction().equals(Intents.ACTION_SIP_REGISTRATION_CHANGED_REGISTERED)) {
                setRegistered(true);
            } else if (intent.getAction().equals(Intents.ACTION_SIP_REGISTRATION_CHANGED_UNREGISTERED)) {
                setRegistered(false);
            }
        }

        public void register(Context context) {
            IntentFilter statusIntentsFilter = new IntentFilter();
            statusIntentsFilter.addAction(Intents.ACTION_SIP_REGISTRATION_CHANGED_REGISTERED);
            statusIntentsFilter.addAction(Intents.ACTION_SIP_REGISTRATION_CHANGED_UNREGISTERED);
            MiscUtils.registerReceiver(context, this, statusIntentsFilter);
        }
    }
}
