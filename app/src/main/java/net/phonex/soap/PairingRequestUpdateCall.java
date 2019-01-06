package net.phonex.soap;

import android.content.Context;

import net.phonex.db.entity.PairingRequest;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.entities.PairingRequestUpdateElement;
import net.phonex.soap.entities.PairingRequestUpdateList;
import net.phonex.soap.entities.PairingRequestUpdateRequest;
import net.phonex.soap.entities.PairingRequestUpdateResponse;
import net.phonex.util.Log;

/**
 * Created by Matus on 24-Aug-15.
 */
public class PairingRequestUpdateCall implements RunnableWithException {
    private static final String TAG = "PairingRequestUpdateCall";

    private PairingRequestUpdateList updates;
    private SOAPHelper soap;
    private Exception thrownException = null;
    private Context context;

    public PairingRequestUpdateCall(Context context, PairingRequestUpdateList updates) {
        soap = new SOAPHelper(context);
        this.context = context;
        this.updates = updates;
    }

    @Override
    public void run() {
        Exception toThrow = null;

        try {
            soap.init();

            Log.i(TAG, "Updating pairing request(s)");

            PairingRequestUpdateRequest request = new PairingRequestUpdateRequest();
            request.setUpdateList(updates);

            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, PairingRequestUpdateResponse.class);
            PairingRequestUpdateResponse response = (PairingRequestUpdateResponse) soap.makeSoapRequest(envelope, "pairingRequestUpdate");

            if (response == null){
                Log.w(TAG, "Empty response from server");
                return;
            }

            if (response.getErrCode() != 0) {
                Log.wf(TAG, "PairingRequestUpdateResponse finished with error code %d", response.getErrCode());
                return;
            }

            if (response.getResultList() == null || response.getResultList().isEmpty()) {
                Log.w(TAG, "Returned empty pairing request response list, requests not resolved!");
                return;
            }

            if (response.getResultList().size() != request.getUpdateList().size()) {
                Log.w(TAG, "Size of response is different than size of request, requests not resolved properly!");
                return;
            }

            int index = 0;
            for (Integer errorCode : response.getResultList()) {
                if (errorCode != 0) {
                    Log.df(TAG, "Request finished with error. errorCode=[%d]", errorCode);
                } else {
                    Log.df(TAG, "Request finished successfully.");

                    // update resolution successfully acknowledged pairing requests
                    // order of response elements is the same as order of request elements
//                    PairingRequest.updateResolution(context.getContentResolver(), element.getId(), element.getResolution());

                    // just delete
                    PairingRequestUpdateElement element = updates.get(index);
                    PairingRequest.delete(context.getContentResolver(), element.getId());

                }
                index++;
            }

        } catch (Exception e) {
            Log.ef(TAG, e, "Exception");
            toThrow = e;
        }
        // if any exception was thrown
        thrownException = toThrow;
    }


    public Exception getThrownException() {
        return thrownException;
    }
}