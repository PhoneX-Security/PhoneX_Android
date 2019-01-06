package net.phonex.util.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import net.phonex.ui.intro.IntroActivity;
import net.phonex.util.Log;

/**
 * Created by dusanklinec on 05.06.15.
 */
public class AudioUtils {
    private static final String TAG = "AudioUtils";
    public static final int[] SAMPLE_RATES = new int[] { 8000, 11025, 16000, 22050, 44100 };

    /**
     * Tests whether there is almost one usable audio source for recording.
     * @return
     */
    public static boolean findAudioRecord() {
        return findAudioRecord(MediaRecorder.AudioSource.DEFAULT, true);
    }

    /**
     * Tests whether there is almost one usable audio source for recording.
     * Returns true if at least one configuration is usable for voice recording.
     * @param source
     * @return
     */
    public static boolean findAudioRecord(int source, boolean atLeastOne) {
        boolean success = false;
        int ctr = 0;

        for (int rate : SAMPLE_RATES) {
            for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
                for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
                    if (atLeastOne && success){
                        return true;
                    }

                    final String rateDesc = "#" + ctr + " rate: " + rate + "Hz, bytes: " + audioFormat + ", channel: " + channelConfig + ", src: " + source;
                    Log.df(TAG, "Attempting %s", rateDesc);
                    ctr += 1;

                    try {
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            // check if we can instantiate and have a success
                            AudioRecord recorder = new AudioRecord(source, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                Log.vf(TAG, "Rate seems OK, %s; buffSize=%s", rateDesc, bufferSize);
                                success |= true;

                            } else {
                                Log.wf(TAG, "Rate not supported, %s; buffSize=%s; state=%s", rateDesc, bufferSize, recorder.getState());
                            }

                            // Need to release it so it can be used again.
                            try {
                                recorder.release();
                            } catch(Exception ex){
                                Log.ef(TAG, ex, "Exception, cannot release recorder.");
                            }

                        } else {
                            Log.wf(TAG, "Invalid buffer size, %s", rateDesc);
                        }

                    } catch (Exception e) {
                        Log.ef(TAG, e, "Exception in rate determination, %s.", rateDesc);
                    }
                }
            }
        }

        return success;
    }
}
