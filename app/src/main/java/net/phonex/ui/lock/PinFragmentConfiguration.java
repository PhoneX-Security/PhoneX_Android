package net.phonex.ui.lock;

/**
 * Created by miroc on 10.12.14.
 */
public class PinFragmentConfiguration {

    public static final int MAX_TRIES = 5;

    private boolean mShouldVibrateOnKey = true;
    private int mKeyVibrationDuration = 10;
    private int mMaxTries = MAX_TRIES;

    public int maxTries() {
        return mMaxTries;
    }

    @SuppressWarnings("unused")
    public PinFragmentConfiguration vibrateOnKey(boolean vibrate) {
        mShouldVibrateOnKey = vibrate;
        return this;
    }

    public boolean shouldVibrateOnKey() {
        return mShouldVibrateOnKey;
    }

    /**
     * @param duration duration of the vibration on key press. Throws {@code IllegalStateException}
     * if vibration state has been set to false. see {@link #vibrateOnKey(boolean)}
     */
    public PinFragmentConfiguration vibrationDuration(int duration) {
        mShouldVibrateOnKey = true;
        mKeyVibrationDuration = duration;
        return this;
    }

    public int vibrateDuration() {
        return mKeyVibrationDuration;
    }

    @Override
    public String toString() {
        return "PinFragmentConfiguration{" +
                "mShouldVibrateOnKey=" + mShouldVibrateOnKey +
                ", mKeyVibrationDuration=" + mKeyVibrationDuration +
                ", mMaxTries=" + mMaxTries +
                '}';
    }
}
