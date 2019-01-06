package net.phonex.soap;

import android.content.Context;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.PairingRequest;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.service.ContactsManager;
import net.phonex.service.XService;
import net.phonex.soap.GenKeyParams.GenKeyForUser;
import net.phonex.soap.entities.CertificateRequestElement;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.soap.entities.CertificateWrapper;
import net.phonex.soap.entities.ContactlistAction;
import net.phonex.soap.entities.ContactlistChangeRequest;
import net.phonex.soap.entities.ContactlistChangeRequestElement;
import net.phonex.soap.entities.ContactlistChangeResponse;
import net.phonex.soap.entities.ContactlistReturn;
import net.phonex.soap.entities.GetCertificateRequest;
import net.phonex.soap.entities.GetCertificateResponse;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.soap.entities.UserIdentifier;
import net.phonex.soap.entities.WhitelistAction;
import net.phonex.util.ContactListUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simplified contact add task
 *
 * @author miroc
 */
public class ClistAddTask implements Runnable{
    public final static String TAG = "ClistAddTask";
    private OnClistAddCallback listener;
    private CertificateWrapper wr;
    private byte[] certificate;
    private String domain;
    private ClistAddTaskParams params;
    private X509Certificate realCert;

    //callback interface
    public interface OnClistAddCallback {
        void onCompleted();
        void onError(Exception e);
    }

    // new attributes
    private Context context;
    private SOAPHelper soap;
    private Exception thrownException;

    public ClistAddTask(Context context, ClistAddTaskParams params) {
        soap = new SOAPHelper(context);
        this.context = context;
        this.params = params;
    }

    public void setListener(OnClistAddCallback listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        runInternal();

        if (listener == null){
            return;
        }

        if (thrownException == null) {
            listener.onCompleted();
        } else {
            listener.onError(thrownException);
        }
    }

    private void runInternal(){
        try {
            soap.init();

            // Get current logged user - for suffix completion

            SipProfile profile = SipProfile.getCurrentProfile(context);
            domain = profile.getSipDomain();
            Log.df(TAG, "Default domain determined: [%s]", domain);

            // Append default domain if not present.
            String userName = params.getUserName();
            if (!userName.contains("@")) {
                Log.df(TAG, "Added default domain to username: [%s]", userName);
                userName = userName + "@" + domain;
                params.setUserName(userName);
            }

            // Prepare request - Obtaining client certificate
            GetCertificateRequest request = new GetCertificateRequest();
            UserIdentifier ui = new UserIdentifier();
            ui.setUserSIP(userName);
            CertificateRequestElement cre = new CertificateRequestElement();
            cre.setUser(userName);
            request.add(cre);

            // Make Request
            SoapSerializationEnvelope envelope = soap.reqisterRequest(request, GetCertificateResponse.class);
            GetCertificateResponse response = (GetCertificateResponse) soap.makeSoapRequest(envelope, "getCertificateRequest");

            wr = response.get(0);
            certificate = wr.getCertificate();
            Log.inf(TAG, "Certificate obtained: %s", wr);


            // is contact already in database?
            if (isSipInLocalDb(params.getUserName())){
                Log.i(TAG, "Database already contains given SIP");
                return;
            }

            saveContactToLocalDb();
            // set request to modify server side contact-list
            try {
                storeOnServer(params);
            } catch (Exception t) {
                // Rollback adding user to the contact list.
                Set<String> sips = new HashSet<>();
                sips.add(params.getUserName());
                SipClist.removeProfiles(context, sips);
                throw t;
            }

            // Now add to filter to accept incoming calls from white-listed entries.
            ContactListUtils.addToFilterWhitelist(this.context, profile.getId(), params.getUserName());

            // Store certificate to database in each case (invalid vs. ok), both is
            // useful to know. We than have fresh data stored in database (no need to re-query
            // in case of error).
            UserCertificate crt2db = new UserCertificate();
            crt2db.setDateCreated(new Date());
            crt2db.setDateLastQuery(new Date());
            crt2db.setCertificateStatus(wr.getStatus());
            crt2db.setOwner(params.getUserName());
            boolean certOK = false;

            // Decode certificate.
            if (certificate != null && certificate.length > 0 && wr.getStatus() == CertificateStatus.OK) {
                realCert = CertificatesAndKeys.buildCertificate(certificate);
                Log.inf(TAG, "X509Certificate init done; to string: %s", realCert.toString());

                // Create record in certificate database.
                TrustVerifier tv = SSLSOAP.getDefaultTrustManager(context);
                certOK = CertificatesAndKeys.verifyClientCertificate(realCert, false, tv, null, params.getUserName());
                if (!certOK) {
                    Log.w(TAG, "Certificate verification failed, cannot add!");

                } else {
                    String certificateHash = MessageDigest.getCertificateDigest(realCert);
                    Log.vf(TAG, "Certificate digest computed: %s", certificateHash);
                    crt2db.setCertificate(realCert.getEncoded());
                    crt2db.setCertificateHash(certificateHash);
                }
            }

            // Store certificate to certificate database.
            context.getContentResolver().insert(UserCertificate.CERTIFICATE_URI, crt2db.getDbContentValues());

            // Rebroadcast old presence updates.
            ContactsManager.broadcastUserAddedChange(context);

            // Generate DH key for file transfer if desired.
            XService.triggerDHKeyUpdate(context, true);
            GenKeyParams gkp = params.getGenKeyParams();
            if (certOK){
                Log.v(TAG, "Starting DH key gen.");
                this.dhgen(gkp);
            }

            // Update resolution of possible contact requests to accepted - if there are some
            PairingRequest.updateResolution(context.getContentResolver(), params.getUserName(), PairingRequestResolutionEnum.ACCEPTED);
            Log.i(TAG, "Finished properly");
        } catch (Exception e) {
            Log.ef(TAG, e, "Exception", e);
            thrownException = e;
        }
    }

    private boolean isSipInLocalDb(String sip){
        SipClist sipProfile = SipClist.getProfileFromDbSip(context, sip);
        return sipProfile != null;
    }

    private void saveContactToLocalDb() throws IOException, NoSuchAlgorithmException {
        // Store to local database.
        // This is done at first because storing it on the server invokes
        // further actions, e.g., presence update. Presence update needs contact list
        // entry to be present.
        SipClist clist = new SipClist();
        clist.setAccount(1);
        clist.setSip(params.getUserName());
        clist.setDisplayName(params.getDiplayName());
        clist.setCertificateHash(certificate != null && wr.getStatus() == CertificateStatus.OK ? MessageDigest.getCertificateDigest(certificate) : "");
        clist.setDateCreated(new Date());
        clist.setDateLastModified(new Date());
        clist.setInWhitelist(true);
        this.context.getContentResolver().insert(SipClist.CLIST_URI, clist.getDbContentValues());
        Log.vf(TAG, "Inserting into DB SipClist object: %s", clist.toString());
    }

    private void dhgen(GenKeyParams gkp){
        if (gkp != null && gkp.getNumKeys() > 0) {
            Log.i(TAG, "Generating DH keys for user.");

            // Initialize task an invoke main work method.
            GenKeyCall genKeyTask = new GenKeyCall();
            genKeyTask.setContext(context);

            // If something is messed up, breaks from the loop.
            do {
                if (gkp.getUserList() == null || gkp.getUserList().isEmpty()) {
                    if (gkp.getNumKeys() == 0) {
                        Log.w(TAG, "Keys to generate: 0!");
                        break;
                    }

                    GenKeyForUser kUser = new GenKeyForUser();
                    kUser.setNumKeys(gkp.getNumKeys());
                    kUser.setUserSip(params.getUserName());
                    kUser.setUserCert(this.realCert);

                    List<GenKeyForUser> uList = new ArrayList<GenKeyForUser>(1);
                    uList.add(kUser);

                    gkp.setUserList(uList);
                }

                // If no identity is provided, load it from global data.
                if (params.isLoadIdentity()) {
                    int code = genKeyTask.loadIdentity(gkp, new SecureRandom());
                    if (code < 0) {
                        Log.wf(TAG, "Cannot generate DH keys, identity cannot be loaded, code=%s", code);
                        break;
                    }
                }

                // Main method for generating keys & storing to DB & upload.
                genKeyTask.genKeys(gkp);
            } while (false);
        }
    }

    /**
     * Calls SOAP to ster given user to the server side contact list.
     * Throws exceptions on error.
     *
     * @param par
     * @throws Exception
     */
    private void storeOnServer(ClistAddTaskParams par) throws Exception {
        ContactlistChangeRequest clChangeReq = new ContactlistChangeRequest();
        ContactlistChangeRequestElement cElem = new ContactlistChangeRequestElement();
        UserIdentifier ui2 = new UserIdentifier();
        ui2.setUserSIP(par.getUserName());
        cElem.setAction(ContactlistAction.ADD);
        cElem.setUser(ui2);
        cElem.setDisplayName(par.getDiplayName());
        cElem.setWhitelistAction(WhitelistAction.ENABLE);
        clChangeReq.add(cElem);

        // Make Request
        SoapSerializationEnvelope envelope = soap.reqisterRequest(clChangeReq, ContactlistChangeResponse.class);
        ContactlistChangeResponse clResp = (ContactlistChangeResponse) soap.makeSoapRequest(envelope, "ContactlistChangeRequest");

        if (clResp.getPropertyCount() < 1) {
            Log.w(TAG, "Empty response");
            throw new IllegalArgumentException("Empty response");
        }

        ContactlistReturn clReturn = clResp.get(0);
        Log.inf(TAG, "Integer response: %s", clReturn);
        if (clReturn.getResultCode() < 0) {
            Log.wf(TAG, "Something wrong during adding to contact list [%d]", clReturn.getResultCode());
            throw new ClistAddException("Cannot add to contactlist on server", clReturn.getResultCode());
        }
    }

    public static class ClistAddException extends Exception {
        public static final long serialVersionUID=1L;
        // error codes returned by Soap server
        public static final int ERROR_CODE_NON_EXISTING_CONTACT = -1;
        private int code=0;

        public ClistAddException(String detailMessage, int code) {
            super(detailMessage);
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        /**
         * Get localized error code message
         */
        public String getErrorCodeMessage(Context ctx){
            switch (code){
                case ERROR_CODE_NON_EXISTING_CONTACT:
                    return ctx.getString(R.string.clist_add_error_nonexisting_user);
                default:
                    return ctx.getString(R.string.p_problem_nonspecific);
            }
        }
    }
}

