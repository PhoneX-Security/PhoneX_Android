package net.phonex.soap;

import android.content.ContentValues;
import android.content.Context;

import net.phonex.db.entity.SipClist;
import net.phonex.db.DBBulkDeleter;
import net.phonex.db.DBBulkInserter;
import net.phonex.db.DBBulkUpdater;
import net.phonex.db.entity.PairingRequest;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.entities.PairingRequestElement;
import net.phonex.soap.entities.PairingRequestFetchRequest;
import net.phonex.soap.entities.PairingRequestFetchResponse;
import net.phonex.soap.entities.PairingRequestList;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppPassiveEvents;
import net.phonex.util.android.StatusbarNotifications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Matus on 24-Aug-15.
 */
public class PairingRequestFetchCall implements RunnableWithException {
    private static final String TAG = "PairingRequestFetchCall";
    private final Context context;
    private SOAPHelper soap;
    private Exception thrownException;
    private StatusbarNotifications statusbarNotifications;

    public PairingRequestFetchCall(Context context, StatusbarNotifications statusbarNotifications) {
        soap = new SOAPHelper(context);
        this.statusbarNotifications = statusbarNotifications;
        this.context = context;
    }

    @Override
    public void run(){

        Exception toThrow = null;

        try {
            soap.init();
            Log.i(TAG, "Fetching pairing request list from server");
            PairingRequestFetchRequest request = new PairingRequestFetchRequest();
            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, PairingRequestFetchResponse.class);
            final PairingRequestFetchResponse response = (PairingRequestFetchResponse) soap.makeSoapRequest(envelope, "PairingRequestFetch");

            if (response==null){
                Log.ef(TAG, "PairingRequestFetchRequest response is null");
                return;
            }

            if (response.getErrCode() != 0) {
                Log.wf(TAG, "PairingRequestFetchRequest finished with error code %d", response.getErrCode());
                return;
            }

            if (response.getRequestList() == null) {
                Log.d(TAG, "pairing request list is null");
                return;
            }

            processPairingRequestList(response.getRequestList());
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);

            toThrow = e;
        }
        // if any exception was thrown
        thrownException = toThrow;
    }

    private void processPairingRequestList(PairingRequestList pairingRequestList){
        List<PairingRequest> all = PairingRequest.getAll(context.getContentResolver(), PairingRequest.FULL_PROJECTION);
        Map<Long, PairingRequest> pairingRequestMap = new HashMap<>(all.size());

        PairingRequest lastNewPairingRequest = null;
        int newPairingRequestCount = 0;

        Set<Long> serverIdsToDelete = new HashSet<>();

        // insert pairing requests in bulk
        DBBulkInserter inserter = new DBBulkInserter(context.getContentResolver(), PairingRequest.URI);
        // update some requests to unseen
        ContentValues cv = new ContentValues();
        cv.put(PairingRequest.FIELD_SEEN, false);
        DBBulkUpdater updaterToUnseen = new DBBulkUpdater(context.getContentResolver(), PairingRequest.URI, PairingRequest.FIELD_SERVER_ID, cv);

        DBBulkDeleter deleter = new DBBulkDeleter(context.getContentResolver(), PairingRequest.URI, PairingRequest.FIELD_SERVER_ID);

        for (PairingRequest pr : all){
            pairingRequestMap.put(pr.getServerId(), pr);
            serverIdsToDelete.add(pr.getServerId());
        }

        List<SipClist> contacts = SipClist.getAllProfiles(context, new String[]{SipClist.FIELD_SIP});
        Set<String> contactNames = new HashSet<>();
        for(SipClist contact : contacts){
            contactNames.add(contact.getCanonicalSip());
        }

        for (PairingRequestElement element : pairingRequestList) {
            if (element.getResolution() != PairingRequestResolutionEnum.NONE){
                // if resolution other than none, ignore
                continue;
            }

            if(contactNames.contains(element.getFromUser())){
                // If already in our contact list, ignore
                continue;
            }

            // present in server request, keep in local db
            serverIdsToDelete.remove(element.getId());

            // insert or update to unseen?
            if (!pairingRequestMap.containsKey(element.getId())){
                PairingRequest pairingRequest = elementToPairingRequest(element);
                inserter.add(pairingRequest.getDbContentValues());
                // mark this as new request
                lastNewPairingRequest = pairingRequest;
                newPairingRequestCount++;
            } else {
                PairingRequest request = pairingRequestMap.get(element.getId());
                if (request.getTstamp() != element.getTstamp()){
                    lastNewPairingRequest = request;
                    newPairingRequestCount++;
                    updaterToUnseen.add(String.valueOf(request.getServerId()));
                }
            }
        }

        List<String> listToDelete = new ArrayList<>();
        for (long serverId : serverIdsToDelete){
            listToDelete.add(String.valueOf(serverId));
        }
        deleter.add(listToDelete);

        // update db
        inserter.finish();
        updaterToUnseen.finish();
        deleter.finish();

        // send notification
        if (lastNewPairingRequest != null && newPairingRequestCount > 0){
            AnalyticsReporter.fromApplicationContext(context.getApplicationContext()).passiveEvent(AppPassiveEvents.CONTACT_REQUEST_RECEIVED);
            sendNotification(lastNewPairingRequest, newPairingRequestCount);
        }
    }

    private PairingRequest elementToPairingRequest(PairingRequestElement element){
        PairingRequest request = new PairingRequest();
        request.setServerId(element.getId());
        request.setFromUser(element.getFromUser());
        request.setTstamp(element.getTstamp());
        request.setResolution(element.getResolution());
        request.setResolutionTstamp(element.getResolutionTstamp());
        return request;
    }

    private void sendNotification(PairingRequest lastPairingRequest, int newRequestsCount){
        try {
            statusbarNotifications.notifyPairingRequest(lastPairingRequest, newRequestsCount);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show notification about new pairing request", t);
        }
    }

    @Override
    public Exception getThrownException() {
        return thrownException;
    }
}