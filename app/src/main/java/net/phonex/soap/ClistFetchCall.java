package net.phonex.soap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import net.phonex.R;
import net.phonex.core.MemoryPrefManager;
import net.phonex.db.entity.SipClist;
import net.phonex.db.DBBulkInserter;
import net.phonex.db.DBBulkQuery;
import net.phonex.db.DBBulkUpdater;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.entity.UserCertificate;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.ksoap2.SoapEnvelope;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.service.ContactsManager;
import net.phonex.soap.entities.CertificateRequestElement;
import net.phonex.soap.entities.CertificateStatus;
import net.phonex.soap.entities.CertificateWrapper;
import net.phonex.soap.entities.ContactListElement;
import net.phonex.soap.entities.ContactlistGetRequest;
import net.phonex.soap.entities.ContactlistGetResponse;
import net.phonex.soap.entities.GetCertificateRequest;
import net.phonex.soap.entities.GetCertificateResponse;
import net.phonex.soap.entities.UserWhitelistStatus;
import net.phonex.util.ContactListUtils;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.pki.TrustVerifier;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ClistFetchCall extends DefaultSOAPCall {
    public static final String TAG = "ClistFetchCall";
    private ClistFetchParams par;

    public ClistFetchCall(Context ctxt, ClistFetchParams params) {
        super(ctxt);
        this.par = params;
    }

    public ClistFetchCall(Context ctxt) {
        super(ctxt);
    }

    public void setParameters(ClistFetchParams par) {
        this.par = par;
    }

    @Override
    public void run() {
        PreferencesConnector prefs = new PreferencesConnector(getContext());

        Exception toThrow = null;
        final long clistStartTimestamp = System.currentTimeMillis();

        // HTTP transport - declare before TRY block to be able to
        // extract response in catch block for debugging
        try {
            /**
             * Preparing phase - initialize SSL connections
             *
             * Install HTTPS support with client credentials and trust verifier
             */
            initSoap(par.getStoragePass());
            TrustVerifier tv = SSLSOAP.getDefaultTrustManager(getContext());

            /**
             * Obtaining client certificate
             */
            Log.i(TAG, "Fetching contactlist from server");

            // Set clist in sync - avoid presence updates.
            prefs.setBoolean(PreferencesManager.CLIST_IN_SYNC, true);

            publishProgress(getContext().getString(R.string.p_getting_clist));

            // request
            ContactlistGetRequest req = new ContactlistGetRequest();
            req.setTargetUser("");
            req.setUsers(null);
            req.setIgnoreNullWrappers(true);

            // create envelope
            SoapSerializationEnvelope soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
            req.register(soapEnvelope);
            new ContactlistGetResponse().register(soapEnvelope);
            soapEnvelope.setOutputSoapObject(req);
            // call remote SOAP function
            Object obj = null;

            final SSLSOAP.SSLContextHolder sslContextHolder = soap.sslContextHolder;


            try {
                obj = SOAPHelper.simpleSOAPRequest(soapEnvelope,
                        par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(getContext()),
                        "contactlistGetRequest", true, sslContextHolder);
                publishProgress(getContext().getString(R.string.p_processing_clist));
            } catch(ArrayIndexOutOfBoundsException ex){
                // contact list is empty, probably
                obj=null;
                Log.i(TAG, "Contactlist is null (ArrayIndexOutOfBoundsException)");
            } catch(Exception ex){
                // unspecified error during contactlist download
                obj=null;
                Log.e(TAG, "Cannot parse contactlist response", ex);
            }

            // empty contactlist -> delete and return
            if (obj==null){
                this.clearContactlist(par);
                return;
            }

            Log.inf(TAG, "Pure object response retrieved, class: %s; %s", obj.getClass().getCanonicalName(), obj.toString());
            if (!(obj instanceof ContactlistGetResponse)){
                Log.w(TAG, "Bad format of response from server - probably problem during unmarshaling");
                throw new IllegalArgumentException("Bad format of response");
            }

            final ContactlistGetResponse clistResp = (ContactlistGetResponse) obj;
            if (clistResp.isEmpty()){
                //this.publishProgress(new DefaultAsyncProgress(1.0, "Done"));
                Log.d(TAG, "Returned empty contact list");
                this.clearContactlist(par);
                return;
            }

            publishProgress(getContext().getString(R.string.p_getting_local_clist));
            int i = 1;
            int ln = clistResp.size();

            final List<String> usrNames = new LinkedList<>();
            Map<String, ContactListElement> contacts = new HashMap<String, ContactListElement>();
            Map<String, X509Certificate> certificates = new HashMap<String, X509Certificate>();
            Map<String, String> certificateHashes = new HashMap<String, String>();

            for(ContactListElement elem : clistResp){
                if (elem==null) {
                    continue;
                }

                Log.vf(TAG, "Contactlist element %d/%d: %s, ", i, ln, elem);
                contacts.put(elem.getUsersip(), elem);

                // create where query now
                usrNames.add(elem.getUsersip());
                i+=1;
            }

            // lookup certificate database and find certificate hashes if exists
            DBBulkQuery crtQuery = new DBBulkQuery(
                    getContext().getContentResolver(),
                    UserCertificate.CERTIFICATE_URI,
                    UserCertificate.NORMAL_PROJECTION,
                    UserCertificate.FIELD_CERTIFICATE_STATUS + "=" + UserCertificate.CERTIFICATE_STATUS_OK,
                    UserCertificate.FIELD_OWNER,
                    new String[] { }
            );
            crtQuery.add(usrNames);

            // Bulk query on the accounts, process in a row.
            for(; crtQuery.moveToNext(); ){
                Cursor c = crtQuery.getCurrentCursor();
                try {
                    UserCertificate sipCert = new UserCertificate(c);
                    sipCert.getCertificateObj();
                    certificateHashes.put(sipCert.getOwner(), sipCert.getCertificateHash());
                    Log.vf(TAG, "Loaded certificate for: %s; Hash: %s", sipCert.getOwner(), sipCert.getCertificateHash());

                } catch(Exception e){
                    Log.ef(TAG, e, "Exception in updating presence");
                }
            }

            // Get all certificates for users.
            // If certificate is already stored in database, validate it with hash only
            // to save bandwidth.
            GetCertificateRequest certReq = new GetCertificateRequest();
            for(String sip : contacts.keySet()){
                CertificateRequestElement cre = new CertificateRequestElement();
                cre.setUser(sip);

                // we have certificate hash of this one
                if (certificateHashes.containsKey(sip)){
                    Log.vf(TAG, "SIP: %s; found among stored hashes", sip);
                    cre.setCertificateHash(certificateHashes.get(sip));
                }

                certReq.add(cre);
            }

            // create envelope
            soapEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
            certReq.register(soapEnvelope);
            new GetCertificateResponse().register(soapEnvelope);
            soapEnvelope.setOutputSoapObject(certReq);
            // soap call
            publishProgress(getContext().getString(R.string.p_getting_missing_csr));
            obj = SOAPHelper.simpleSOAPRequest(soapEnvelope,
                    par.getServiceURL() + ServiceConstants.getSOAPServiceEndpoint(getContext()),
                    "certificateGetRequest", true, sslContextHolder);
            publishProgress(getContext().getString(R.string.p_processing_csr));

            // Prepare DB bulk updater object for certificate query time update. This operation is invoked
            // quite often but SQLite is not performing good for updating large number of records one by one.
            final Date lastUpdateDate = new Date();
            final ContentValues dataUpdate = new ContentValues();
            dataUpdate.put(UserCertificate.FIELD_DATE_LAST_QUERY, UserCertificate.formatDate(lastUpdateDate));
            dataUpdate.put(UserCertificate.FIELD_CERTIFICATE_STATUS, UserCertificate.CERTIFICATE_STATUS_OK);
            DBBulkUpdater certQueryTimeUpdater = new DBBulkUpdater(ctxt.getContentResolver(), UserCertificate.CERTIFICATE_URI, UserCertificate.FIELD_OWNER, dataUpdate);

            // Prepare DB bulk inserter object for inserting a new certicate to the database.
            DBBulkInserter certInserter = new DBBulkInserter(ctxt.getContentResolver(), UserCertificate.CERTIFICATE_URI);
            certInserter.setOperationThreshold(50);

            // Iterate over records.
            if (obj instanceof GetCertificateResponse) {
                final GetCertificateResponse respc = (GetCertificateResponse) obj;
                Log.vf(TAG, "Certificates returned: %s, certHashesLen: %s", respc.size(), certificateHashes.size());

                for (CertificateWrapper wr : respc) {
                    if (wr == null) continue;
                    String user = wr.getUser();
                    Log.vf(TAG, "CRT, user: %s, status: %s", user, wr.getStatus());

                    // test if we provided some certificate. If yes, look on certificate status.
                    // If status = OK then update database (last query), otherwise delete record
                    // because the new one with provided answer will be inserted afterwards.
                    if (certificateHashes.containsKey(user)){
                        CertificateStatus providedStatus = wr.getProvidedCertStatus();

                        // invalid? Delete certificate then
                        if (providedStatus == CertificateStatus.OK){
                            certQueryTimeUpdater.add(user);
                            Log.vf(TAG, "Certificate for user: %s; updated in database (query time also), status=OK", user);

                            // We don't have to continue, certificate is valid -> move to next user.
                            continue;
                        } else {
                            // something is wrong with stored certificate,
                            // deleting from certificate database.
                            try {
                                String selection = UserCertificate.FIELD_OWNER + "=?";
                                String[] selectionArgs = new String[] { user };
                                int deleteResult = getContext().getContentResolver().delete(UserCertificate.CERTIFICATE_URI, selection, selectionArgs);
                                Log.df(TAG, "Certificate for user [%s] removed; int: %s, status=%s", user, deleteResult, providedStatus);
                            } catch(Exception e){
                                Log.ef(TAG, e, "Exception during removing invalid certificate for: %s, status=%s", user, providedStatus);
                            }
                        }
                    }

                    // If we are here then
                    //	a) user had no certificate stored
                    //	b) or user had certificate stored, but was invalid
                    // thus process this result - new certificate should be provided or error code if
                    // something is wrong with certificate on server side (missing, invalid, revoked).
                    byte[] cert = wr.getCertificate();
                    try {
                        // Store certificate to database in each case (invalid vs. ok), both is
                        // useful to know. We than have fresh data stored in database (no need to re-query
                        // in case of error).
                        UserCertificate crt2db = new UserCertificate();
                        crt2db.setDateCreated(lastUpdateDate);
                        crt2db.setDateLastQuery(lastUpdateDate);
                        crt2db.setCertificateStatus(wr.getStatus());
                        crt2db.setOwner(user);

                        // Returned certificate is valid, process & store it.
                        if (wr.getStatus() == CertificateStatus.OK && cert != null && cert.length > 0) {
                            X509Certificate realCert = CertificatesAndKeys.buildCertificate(cert);

                            // check CN match
                            String CNfromCert = CertificatesAndKeys.getCertificateCN(realCert);
                            if (!user.equals(CNfromCert)){
                                Log.e(TAG, "Security alert! Server returned certificate with different CN!");
                                continue;
                            }

                            // Verify new certificate with trust verifier
                            try {
                                tv.checkTrusted(realCert);
                            } catch(Exception e){
                                Log.i(TAG, "Certificate was not verified", e);
                                continue;
                            }

                            // Store certificate to database.
                            // We now need to compute certificate digest.
                            String certificateHash = MessageDigest.getCertificateDigest(realCert);
                            Log.vf(TAG, "Certificate digest computed, user=%s, digest=%s", user, certificateHash);

                            crt2db.setCertificate(realCert.getEncoded());
                            crt2db.setCertificateHash(certificateHash);

                            certificates.put(wr.getUser(), realCert);
                            certificateHashes.put(wr.getUser(), certificateHash);
                        }

                        // store result of this query to DB. Can also have error code - usable not to query for
                        // certificate too often.
                        certInserter.add(crt2db.getDbContentValues());
                    } catch (Exception e) {
                        Log.ef(TAG, "cannot decode certificate");
                    }
                }
            }

            // Perform certificate insert & update in a bulk.
            certInserter.finish();
            certQueryTimeUpdater.finish();

            // Update clist table if requested
            Log.vf(TAG, "Clist update strategy: %s", par.getClistTableUpdateStrategy());
            switch (par.getClistTableUpdateStrategy()){
                case DROP_AND_UPDATE:
                    dropAndUpdateClist(contacts, certificateHashes);
                    break;
                case UPDATE:
                    updateClist(contacts, certificateHashes);
                    break;
                case DO_NOT_UPDATE:
                    Log.d(TAG, "Not updating contact list from server");
                    break;
            }

            Log.i(TAG, "Finished properly");
            prefs.setLong(PreferencesManager.LAST_CLIST_SYNC, clistStartTimestamp);
            MemoryPrefManager.setPreferenceLongValue(getContext(), PreferencesManager.LAST_CLIST_SYNC, clistStartTimestamp);

        } catch (Exception e) {
            Log.e(TAG, "Exception", e);

            toThrow = e;
        } finally {
            prefs.setBoolean(PreferencesManager.CLIST_IN_SYNC, false);
        }
        // if any exception was thrown, save it for later to rethrow (weird design I know)
        thrownException = toThrow;
    }

    private void dropAndUpdateClist(Map<String, ContactListElement> contacts, Map<String, String> certificateHashes){
        Log.vf(TAG, "dropAndUpdateClist");
        publishProgress(getContext().getString(R.string.p_synchronizing_clist));

        // truncate contact list table at first to have fresh data
        this.clearContactlist(par);

        // delete all filters having canAnswer as action
        getContext().getContentResolver().delete(CallFirewallScheme.URI,
                CallFirewallScheme.FIELD_ACTION + "=" + CallFirewall.ACTION_CAN_ANSWER, null);


        addContacts(contacts, certificateHashes);
    }

    private void printSet(String name, Set<String> set){
        for (String s: set){
            Log.vf(TAG, name + " : " + s );
        }
    }


    private void updateClist(Map<String, ContactListElement> contacts, Map<String, String> certificateHashes){
        Log.vf(TAG, "updateClist");
        List<SipClist> localContacts = SipClist.getAllProfiles(getContext(), new String[]{SipClist.FIELD_SIP});

        Map<String, SipClist> localSips = new HashMap<>();

        for(SipClist localContact : localContacts){
            localSips.put(localContact.getCanonicalSip(), localContact);
        }

        Set<String> sipsToAdd = new HashSet<>(contacts.keySet());
        sipsToAdd.removeAll(localSips.keySet());

        Set<String> sipsToRemove = new HashSet<>(localSips.keySet());
        sipsToRemove.removeAll(contacts.keySet());

        printSet("localsips.keyset", localSips.keySet());
        printSet("contacts.keyset", contacts.keySet());
        printSet("sipsToAdd", sipsToAdd);
        printSet("sipsToRemove", sipsToRemove);

//        remove old entries
        SipClist.removeProfiles(getContext(), sipsToRemove);

        // delete all filters having canAnswer as action
        for (String sipToRemove : sipsToRemove){
            getContext().getContentResolver().delete(CallFirewallScheme.URI,
                    CallFirewallScheme.FIELD_ACTION + "="
                            + CallFirewall.ACTION_CAN_ANSWER
                            + " AND " + CallFirewallScheme.FIELD_ACCOUNT_ID + "=?",
                    new String[]{localSips.get(sipToRemove).getId().toString()});
        }

        // add new entries
        Map<String, ContactListElement> newContacts = new HashMap<>();
        for(String newSip : sipsToAdd){
            newContacts.put(newSip, contacts.get(newSip));
        }



        addContacts(newContacts, certificateHashes);
    }

    private void addContacts(Map<String, ContactListElement> contacts, Map<String, String> certificateHashes){

        if (contacts.size() == 0){
            Log.df(TAG, "no contacts to add");
            return;
        }

        // List of content values to be inserted in a batch.
        List<ContentValues> users2insert = new ArrayList<ContentValues>(contacts.size());

        // List of sip addresses that will be added to the whitelist.
        List<String> sip2whitelist = new ArrayList<String>(contacts.size());

        // now iterate over contacts and store it to database
        Set<Map.Entry<String, ContactListElement>> entries = contacts.entrySet();
        for(Map.Entry<String, ContactListElement> e : entries){
            try {
                Log.vf(TAG, "addContacts; adding user %s", e.getKey());
                String sip = e.getKey();

                SipClist u = new SipClist();
                u.setAccount(par.getDbId());
                u.setSip(sip);
                u.setDateCreated(new Date());
                u.setDateLastModified(new Date());

                // extract display name, use display name provided by server, if any
                final String serverDisplayName = e.getValue().getDisplayName();
                String finalDisplayName = serverDisplayName;
                if (serverDisplayName==null || serverDisplayName.length()==0){
                    String[] splits = sip.split("@", 2);
                    finalDisplayName = splits[0];
                }
                u.setDisplayName(finalDisplayName);

                // whitelist status
                UserWhitelistStatus wstatus =  e.getValue().getWhitelistStatus();
                u.setInWhitelist(wstatus == UserWhitelistStatus.IN ? true : false);

                // certificate hash
                if (certificateHashes.containsKey(sip)){
                    u.setCertificateHash(certificateHashes.get(sip));
                } else {
                    u.setCertificateHash("");
                }

                users2insert.add(u.getDbContentValues());
                Log.df(TAG, "Contact [%s] add 2 store; display name: %s", sip, finalDisplayName);

                // now add to filter to accept incoming calls from whitelisted entries
                sip2whitelist.add(sip);
            } catch(Exception ex){
                Log.ef(TAG, ex, "Exception during removing old contacts for: %s", par.getSip());
                throw ex;
            }
        }

        // Add users to the database.
        DBBulkInserter contactInserter = new DBBulkInserter(getContext().getContentResolver(), SipClist.CLIST_URI);
        contactInserter.insert(users2insert);

        // Initialize whitelist.
        ContactListUtils.addToFilterWhitelist(getContext(), par.getDbId(), sip2whitelist);

        // Broadcast message clist got updates.
        ContactsManager.broadcastUserAddedChange(getContext());
    }

    /**
     * Truncate contact list table at first to have fresh data
     * @param par
     * @return
     */
    public boolean clearContactlist(ClistFetchParams par){
        try {
            String selection = SipClist.FIELD_ACCOUNT + "=?";
            String[] selectionArgs = new String[] { par.getDbId().toString() };
            int deleteResult = getContext().getContentResolver().delete(SipClist.CLIST_URI, selection, selectionArgs);
            Log.df(TAG, "Old contacts for [%s] removed; int: %s", par.getSip(), deleteResult);
            return true;
        } catch(Exception e){
            Log.ef(TAG, e, "Exception during removing old contacts for: %s", par.getSip());
            return false;
        }
    }

    private void publishProgress(String string) {
        if (progressEventListener != null){
            progressEventListener.publishProgress(string);
        }
    }
}
