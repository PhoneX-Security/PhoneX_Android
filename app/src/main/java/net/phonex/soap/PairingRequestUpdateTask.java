package net.phonex.soap;

import android.content.Context;

import net.phonex.soap.entities.PairingRequestUpdateList;

/**
 * Created by miroc on 30.8.15.
 */
public class PairingRequestUpdateTask implements Runnable {
    private final Callback callback;
    private final Context context;
    private final PairingRequestUpdateList updates;

    public PairingRequestUpdateTask(PairingRequestUpdateList updates, Context context, Callback callback) {
        this.context = context;
        this.updates = updates;
        this.callback = callback;
    }

    @Override
    public void run() {
        PairingRequestUpdateCall call = new PairingRequestUpdateCall(context, updates);
        call.run();
        if (callback != null){
            if (call.getThrownException() != null){
                callback.onUpdateFailed(call.getThrownException());
            } else {
                callback.onUpdateCompleted();
            }
        }
    }

    public interface Callback {
        void onUpdateCompleted();
        void onUpdateFailed(Exception e);
    }
}
