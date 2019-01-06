package net.phonex.service;

import android.content.Intent;

import net.phonex.core.Intents;
import net.phonex.pub.parcels.GenericError;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.PairingRequestFetchCall;
import net.phonex.soap.PairingRequestUpdateCall;
import net.phonex.soap.TaskWithCallback;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.soap.entities.PairingRequestUpdateElement;
import net.phonex.soap.entities.PairingRequestUpdateList;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by miroc on 30.8.15.
 */
public class PairingRequestManager{
    private static final String TAG = "PairingRequestManager";
    private final XService xService;
    private ExecutorService executor;

    // paring request fetch
    protected Future<?> futurePairingRequestFetch;

    public PairingRequestManager(XService xService) {
        this.xService = xService;
        executor = Executors.newSingleThreadExecutor();
    }

    public void triggerPairingRequestFetch(){
        Log.vf(TAG, "triggerPairingRequestFetch");
        // Start task if it is not running.
        // Schedules new task only if there is none scheduled or previous has finished.
        if (futurePairingRequestFetch == null || futurePairingRequestFetch.isDone()){
            futurePairingRequestFetch = executor.submit(new PairingRequestFetchCall(xService, xService.getNotificationManager()));
        }
    }

    public void triggerPairingRequestUpdate(PairingRequestUpdateElement element){
        Log.df(TAG, "triggerPairingRequestUpdate");
        if (element == null) {
            throw new IllegalArgumentException("No update elements defined");
        }

        PairingRequestUpdateList updates = new PairingRequestUpdateList();
        updates.add(element);

        TaskWithCallback task =  new TaskWithCallback(new PairingRequestUpdateCall(xService, updates), new TaskWithCallback.Callback() {
            @Override
            public void onCompleted() {
                Log.df(TAG, "onCompleted");
                Intent intent = new Intent(Intents.ACTION_PAIRING_REQUEST_UPDATE_PROGRESS);
                intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.doneInstance());
                MiscUtils.sendBroadcast(xService, intent);

                if (element.getResolution() == PairingRequestResolutionEnum.DENIED){
                    AnalyticsReporter.from(xService).event(AppEvents.CONTACT_REQUEST_DENIED);
                }
            }

            @Override
            public void onFailed(Exception e) {
                Log.df(TAG, "onFailed");
                Intent intent = new Intent(Intents.ACTION_PAIRING_REQUEST_UPDATE_PROGRESS);
                // error is not specified
                intent.putExtra(Intents.EXTRA_GENERIC_PROGRESS, GenericTaskProgress.errorInstance(GenericError.GENERIC_ERROR, null));
                MiscUtils.sendBroadcast(xService, intent);

            }
        });
        executor.submit(task);
    }
}