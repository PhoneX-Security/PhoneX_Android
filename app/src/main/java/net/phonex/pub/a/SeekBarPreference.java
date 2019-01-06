/**
 * This file contains relicensed code from Apache copyright of 
 * Copyright (C) 2010 Matthew Wiggins 
 */

package net.phonex.pub.a;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import net.phonex.util.Log;

public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG = "SeekBarPreference";
    private static final String DB_SUFFIX = "dB";
    private final Context context;
    private final float defaultValue, max;
    private final String dialogMessage, suffix;
    private SeekBar seekBar;
    private TextView valueText;
    private float value = 0.0f;
    private double subdivision = 5;

    public SeekBarPreference(Context aContext, AttributeSet attrs) {
        super(aContext, attrs);
        context = aContext;

        dialogMessage = attrs.getAttributeValue(ANDROID_NS, "dialogMessage");
        suffix = attrs.getAttributeValue(ANDROID_NS, "text");
        defaultValue = attrs.getAttributeFloatValue(ANDROID_NS, "defaultValue", 0.0f);
        max = attrs.getAttributeIntValue(ANDROID_NS, "max", 10);

    }

    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        TextView splashText = new TextView(context);
        if (dialogMessage != null) {
            splashText.setText(dialogMessage);
        }
        layout.addView(splashText);

        valueText = new TextView(context);
        valueText.setGravity(Gravity.CENTER_HORIZONTAL);
        valueText.setTextSize(32);
        params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(valueText, params);

        seekBar = new SeekBar(context);
        seekBar.setOnSeekBarChangeListener(this);
        layout.addView(seekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (shouldPersist()) {
            value = getPersistedFloat(defaultValue);
        }

        applySeekBarValues();

        return layout;
    }


    private void applySeekBarValues() {
        if (DB_SUFFIX.equals(suffix)) {
            seekBar.setMax((int) (2 * max * subdivision));
        } else {
            seekBar.setMax(valueToProgressUnit(max));
        }
        seekBar.setProgress(valueToProgressUnit(value));
    }


    private int valueToProgressUnit(float val) {
        if (DB_SUFFIX.equals(suffix)) {
            Log.df(TAG, "Value is %s", val);
            double dB = (10.0f * Math.log10(val));
            return (int) ((dB + max) * subdivision);
        }
        return (int) (val * subdivision);
    }

    private float progressUnitToValue(int pVal) {
        if (DB_SUFFIX.equals(suffix)) {
            Log.df(TAG, "Progress is %s", pVal);
            double dB = pVal / subdivision - max;
            return (float) Math.pow(10, dB / 10.0f);
        }

        return (float) (pVal / subdivision);
    }

    private String progressUnitToDisplay(int pVal) {
        if (DB_SUFFIX.equals(suffix)) {
            return Float.toString((float) (pVal / subdivision - max));
        }
        return Float.toString((float) (pVal / subdivision));
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        applySeekBarValues();
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object aDefaultValue) {
        super.onSetInitialValue(restore, aDefaultValue);
        if (restore) {
            value = shouldPersist() ? getPersistedFloat(defaultValue) : 0;
        } else {
            value = (Float) aDefaultValue;
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        Log.df(TAG, "Dialog is closing. Positive=%s; persist=%s", positiveResult, shouldPersist());
        if (positiveResult && shouldPersist()) {
            Log.df(TAG, "Save : %s", value);
            persistFloat(value);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seek, int aValue, boolean fromTouch) {
        String t = progressUnitToDisplay(aValue);
        valueText.setText(suffix == null ? t : t.concat(suffix));
        if (fromTouch) {
            value = progressUnitToValue(aValue);
            Log.df(TAG, "Set ratio value %s", value);
            callChangeListener(Float.valueOf(value));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seek) {
        // Interface unused implementation
    }

    @Override
    public void onStopTrackingTouch(SeekBar seek) {
        // Interface unused implementation
    }

}
