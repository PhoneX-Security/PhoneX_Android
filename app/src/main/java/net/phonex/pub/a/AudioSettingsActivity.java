package net.phonex.pub.a;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipCallSession;
import net.phonex.service.XService;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.Timer;
import java.util.TimerTask;

public class AudioSettingsActivity extends LockActionBarActivity implements OnSeekBarChangeListener, OnCheckedChangeListener, OnClickListener {
    protected static final String TAG = "AudioSettingsActivity";
    private final static int AUTO_QUIT_DELAY = 3000;
    private SeekBar speakerAmplification;
    private SeekBar microAmplification;
    private Button saveButton;
    private CheckBox echoCancellation;
    private boolean isAutoClose = false;
    private Timer quitTimer;
//    private ProgressBar txIndicator;
//    private ProgressBar rxIndicator;
    private LinearLayout okBar;

    private Toolbar toolbar;

    private IService xService;
    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intents.ACTION_SIP_CALL_CHANGED)) {
                if (xService != null) {
                    try {
                        SipCallSession[] callsInfo = xService.getCalls();
                        SipCallSession currentCallInfo = null;
                        if (callsInfo != null) {
                            for (SipCallSession callInfo : callsInfo) {
                                int state = callInfo.getCallState();
                                switch (state) {
                                    case SipCallSession.InvState.NULL:
                                    case SipCallSession.InvState.DISCONNECTED:
                                        break;
                                    default:
                                        currentCallInfo = callInfo;
                                        break;
                                }
                                if (currentCallInfo != null) {
                                    break;
                                }
                            }
                        }
                        if (currentCallInfo == null) {
                            finish();
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Not able to retrieve calls");
                    }
                }
            }
        }
    };
    private ServiceConnection sipConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            Log.d(TAG, "XService is connected");
            xService = IService.Stub.asInterface(arg1);
            updateUIFromMedia();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };
    private double div = 5;
    private double max = 15;
    private MonitorThread monitorThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_settings);

        toolbar = (Toolbar) findViewById(R.id.my_toolbar);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        speakerAmplification = (SeekBar) findViewById(R.id.speaker_level);
        microAmplification = (SeekBar) findViewById(R.id.micro_level);
        saveButton = (Button) findViewById(R.id.save_bt);
        echoCancellation = (CheckBox) findViewById(R.id.echo_cancellation);
        okBar = (LinearLayout) findViewById(R.id.ok_bar);

        speakerAmplification.setMax((int) (max * div * 2));
        microAmplification.setMax((int) (max * div * 2));

        speakerAmplification.setOnSeekBarChangeListener(this);
        microAmplification.setOnSeekBarChangeListener(this);

        saveButton.setOnClickListener(this);

        echoCancellation.setOnCheckedChangeListener(this);

//        rxIndicator = (ProgressBar) findViewById(R.id.rx_bar);
//        txIndicator = (ProgressBar) findViewById(R.id.tx_bar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent xServiceIntent = XService.getStartServiceIntent(this.getApplicationContext());
        bindService(xServiceIntent, sipConnection, BIND_AUTO_CREATE);


        int direction = getIntent().getIntExtra(Intent.EXTRA_KEY_EVENT, 0);
        if (direction == AudioManager.ADJUST_LOWER || direction == AudioManager.ADJUST_RAISE) {
            isAutoClose = true;
            okBar.setVisibility(View.GONE);
            delayedQuit(AUTO_QUIT_DELAY);
        } else {
            okBar.setVisibility(View.VISIBLE);
            isAutoClose = false;
        }

        MiscUtils.registerReceiver(this, callStateReceiver, new IntentFilter(Intents.ACTION_SIP_CALL_CHANGED));
        if (monitorThread == null) {
            monitorThread = new MonitorThread();
            monitorThread.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unbindService(sipConnection);
        } catch (Exception e) {
            //Just ignore that
        }

        if (quitTimer != null) {
            quitTimer.cancel();
            quitTimer.purge();
            quitTimer = null;
        }
        try {
            unregisterReceiver(callStateReceiver);
        } catch (IllegalArgumentException e) {
            //That's the case if not registered (early quit)
        }

        if (monitorThread != null) {
            monitorThread.markFinished();
            monitorThread = null;
        }
        xService = null;
    }

    public void delayedQuit(int time) {
        if (quitTimer != null) {
            quitTimer.cancel();
            quitTimer.purge();
            quitTimer = null;
        }

        Log.inf(TAG, "InMedia DelayQuit: %s", time);
        quitTimer = new Timer("timer.quit.audio");
        quitTimer.schedule(new LockTimerTask(), time);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:

                if (speakerAmplification != null) {
                    int step = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ? -1 : +1;
                    int newValue = speakerAmplification.getProgress() + step;
                    if (newValue >= 0 && newValue < speakerAmplification.getMax()) {
                        speakerAmplification.setProgress(newValue);
                    }
                }

                return true;
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
            case KeyEvent.KEYCODE_SEARCH:
                //Prevent search
                return true;
            default:
                //Nothing to do
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
            case KeyEvent.KEYCODE_SEARCH:
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void updateUIFromMedia() {
        boolean useBT = false;
        if (xService != null) {
            try {
                useBT = xService.getCurrentMediaState().isBluetoothScoOn;
            } catch (RemoteException e) {
                Log.e(TAG, "Problem with service", e);
            }
        }

        Float speakerLevel = PhonexConfig.getFloatPref(this, useBT ?
                PhonexConfig.SOUND_BT_SPEAKER_VOLUME : PhonexConfig.SOUND_SPEAKER_VOLUME);
        speakerAmplification.setProgress(valueToProgressUnit(speakerLevel));

        Float microLevel = PhonexConfig.getFloatPref(this, useBT ?
                PhonexConfig.SOUND_BT_MIC_VOLUME : PhonexConfig.SOUND_MIC_VOLUME);
        microAmplification.setProgress(valueToProgressUnit(microLevel));

        echoCancellation.setChecked(PhonexConfig.getBooleanPref(this,
                PhonexConfig.ECHO_CANCELLATION));

    }

    private int valueToProgressUnit(float val) {
        Log.df(TAG, "Value is %s", val);
        double dB = (10.0f * Math.log10(val));
        return (int) ((dB + max) * div);
    }

    private float progressUnitToValue(int pVal) {
        Log.df(TAG, "Progress is %s", pVal);
        double dB = pVal / div - max;
        return (float) Math.pow(10, dB / 10.0f);
    }

    @Override
    public void onProgressChanged(SeekBar arg0, int value, boolean arg2) {
        Log.d(TAG, "Progress has changed");
        if (xService != null) {
            try {
                float newValue = progressUnitToValue(value);
                String key;
                boolean useBT = xService.getCurrentMediaState().isBluetoothScoOn;
                int sId = arg0.getId();
                if (sId == R.id.speaker_level) {
                    xService.confAdjustTxLevel(0, newValue);
                    key = useBT ? PhonexConfig.SOUND_BT_SPEAKER_VOLUME : PhonexConfig.SOUND_SPEAKER_VOLUME;
                    PhonexConfig.setFloatPref(this, key, newValue);
                } else if (sId == R.id.micro_level) {
                    xService.confAdjustRxLevel(0, newValue);
                    key = useBT ? PhonexConfig.SOUND_BT_MIC_VOLUME : PhonexConfig.SOUND_MIC_VOLUME;
                    PhonexConfig.setFloatPref(this, key, newValue);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot set device level", e);
            }

        } else {
            //TODO : revert changes here !
        }

        //Update quit timer
        if (isAutoClose) {
            delayedQuit(AUTO_QUIT_DELAY);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // Nothing to do
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // Nothing to do
    }

    @Override
    public void onCheckedChanged(CompoundButton arg0, boolean value) {
        if (xService != null) {
            try {
                int bId = arg0.getId();
                if (bId == R.id.echo_cancellation) {
                    xService.setEchoCancellation(value);
                    PhonexConfig.setBooleanPref(this, PhonexConfig.ECHO_CANCELLATION, value);
                }
                //Update quit timer
                if (isAutoClose) {
                    delayedQuit(AUTO_QUIT_DELAY);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Impossible to set mic/speaker level", e);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.save_bt) {
            finish();
        }
    }

    private class LockTimerTask extends TimerTask {
        @Override
        public void run() {
            finish();
        }
    }

    private class MonitorThread extends Thread {
        private boolean finished = false;

        public synchronized void markFinished() {
            finished = true;
        }

        @Override
        public void run() {
            super.run();
            while (true) {
                if (xService != null) {
                    try {
                        long value = xService.confGetRxTxLevel(0);
                        runOnUiThread(new UpdateConfLevelRunnable((int) ((value >> 8) & 0xff), (int) (value & 0xff)));
                    } catch (RemoteException e) {
                        Log.e(TAG, "Problem with remote service", e);
                        break;
                    }
                }

                // End of loop, sleep for a while and exit if necessary
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interupted monitor thread", e);
                }
                synchronized (this) {
                    if (finished) {
                        break;
                    }
                }
            }
        }
    }

    private class UpdateConfLevelRunnable implements Runnable {
        private final int mRx, mTx;

        UpdateConfLevelRunnable(int rx, int tx) {
            mRx = rx;
            mTx = tx;
        }

        @Override
        public void run() {
//            txIndicator.setProgress(mTx);
//            rxIndicator.setProgress(mRx);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

}
