/**
 * This file contains relicensed code from Apache copyright of 
 * Copyright (C) 2008 The Android Open Source Project
 */

package net.phonex.pub.a;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import net.frakbot.glowpadbackport.GlowPadView;
import net.phonex.R;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.SipCallSessionInfo;
import net.phonex.ui.customViews.ILRChooser;
import net.phonex.util.Log;

public class CallAnswerView extends RelativeLayout implements ILRChooser {
    private static final String TAG = "CallAnswerView";
    private static final int MODE_LOCKER = 0;
    private static final int MODE_NO_ACTION = 1;
    private GlowPadView glowPadView;
    private int controlMode;
    private SipCallSessionInfo currentCall;
    private ICallActionListener onTriggerListener;


    public CallAnswerView(Context context) {
        this(context, null, 0);
    }

    public CallAnswerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CallAnswerView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Finalize object style
        controlMode = MODE_NO_ACTION;
    }


    private void setCallLockerVisibility(int visibility) {
        controlMode = visibility == View.VISIBLE ? MODE_LOCKER : MODE_NO_ACTION;
        setVisibility(visibility);

        if (visibility == View.VISIBLE) {
            if (glowPadView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View view = inflater.inflate(R.layout.incall_ring_widget, null);
                glowPadView = (GlowPadView) view.findViewById(R.id.incomingCallWidget);
                glowPadView.setOnTriggerListener(new GlowPadViewTriggerListener());
//                glowPadView.setLRChooser(this);
                this.addView(glowPadView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                LayoutParams lp = (LayoutParams) glowPadView.getLayoutParams();
                lp.addRule(RelativeLayout.ALIGN_BOTTOM, RelativeLayout.TRUE);
            }
        }

        if (glowPadView != null) {
            glowPadView.setVisibility(visibility);
        }
    }

    public void setCallState(SipCallSessionInfo callInfo) {
        currentCall = callInfo;

        if (currentCall == null) {
            setCallLockerVisibility(GONE);
            return;
        }

        int state = currentCall.getCallState();
        switch (state) {
            case SipCallSession.InvState.INCOMING:
                setCallLockerVisibility(VISIBLE);
                break;
            case SipCallSession.InvState.PREPARING:
            case SipCallSession.InvState.CALLING:
            case SipCallSession.InvState.CONNECTING:
            case SipCallSession.InvState.CONFIRMED:
            case SipCallSession.InvState.NULL:
            case SipCallSession.InvState.DISCONNECTED:
                setCallLockerVisibility(GONE);
                break;
            case SipCallSession.InvState.EARLY:
            default:
                if (currentCall.isIncoming()) {
                    setCallLockerVisibility(VISIBLE);
                } else {
                    setCallLockerVisibility(GONE);
                }
                break;
        }

    }

    /**
     * Registers a callback to be invoked when the user triggers an event.
     *
     * @param listener the OnTriggerListener to attach to this view
     */
    public void setOnTriggerListener(ICallActionListener listener) {
        onTriggerListener = listener;
    }

    private void dispatchTriggerEvent(int whichHandle) {
        if (onTriggerListener != null) {
            onTriggerListener.onCallAction(whichHandle, currentCall);
        }
    }


    @Override
    public void onLRChoice(int side) {
        Log.df(TAG, "Call controls receive info from slider %s", side);
        if (controlMode != MODE_LOCKER) {
            // Oups we are not in locker mode and we get a trigger from
            // locker...
            // Should not happen... but... to be sure
            return;
        }
        switch (side) {
            case LEFT_SIDE:
                Log.d(TAG, "Call taken");
                dispatchTriggerEvent(ICallActionListener.TAKE_CALL);
                break;
            case RIGHT_SIDE:
                Log.d(TAG, "Call dropped");
                dispatchTriggerEvent(ICallActionListener.DONT_TAKE_CALL);
            default:
                break;
        }

        // TODO
//		if(slidingTabWidget != null) {
//		    slidingTabWidget.resetView();
//		}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.df(TAG, "onKeyDown() %s", keyCode);
        if (controlMode == MODE_LOCKER) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CALL:
                    dispatchTriggerEvent(ICallActionListener.TAKE_CALL);
                    return true;
                case KeyEvent.KEYCODE_ENDCALL:
                    //case KeyEvent.KEYCODE_POWER:
                    dispatchTriggerEvent(ICallActionListener.REJECT_CALL);
                    return true;
                default:
                    break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private class GlowPadViewTriggerListener implements GlowPadView.OnTriggerListener {

        @Override
        public void onGrabbed(View view, int i) {

        }

        @Override
        public void onReleased(View view, int i) {

        }

        @Override
        public void onTrigger(View view, int target) {
            final int resId = glowPadView.getResourceIdForTarget(target);
            switch (resId) {
                case R.drawable.ic_lockscreen_answer:
                    onLRChoice(ILRChooser.LEFT_SIDE);
                    break;

                case R.drawable.ic_lockscreen_decline:
                    onLRChoice(ILRChooser.RIGHT_SIDE);
                    break;
                default:
            }
            glowPadView.reset(true);
        }

        @Override
        public void onGrabbedStateChange(View view, int i) {

        }

        @Override
        public void onFinishFinalAnimation() {

        }
    }
}
