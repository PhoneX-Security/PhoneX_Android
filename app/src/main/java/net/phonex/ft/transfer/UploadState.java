package net.phonex.ft.transfer;

import android.os.CancellationSignal;

import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.pub.proto.FileTransfer.GetDHKeyResponseBodySCip;
import net.phonex.soap.entities.FtGetDHKeyPart2Response;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.FTHolder;
import net.phonex.ft.FTHolder.UploadResult;
import net.phonex.ft.FTHolder.FtPreUploadFilesHolder;

/**
 * State class holding all state required for file transfer upload.
 */
public class UploadState {
    public Long                      msgId;
    public Long                      queueMsgId;
    public String                    nonce2;
    public byte[]                    nonce2b;
    public String                    destination;
    public String                    domain;
    public SipMessage                msg;

    public UploadFileParams          params;
    public GetDHKeyResponseBodySCip  resp1;
    public FtGetDHKeyPart2Response   getKeyResponse2;

    public UserCertificate           senderCrt;
    public DHKeyHelper               dhelper;
    public FTHolder                  ftHolder;
    public UploadResult              updResult;
    public FtPreUploadFilesHolder    preUploadHolder;

    public Long                      transferRecordId;
    public FileTransfer              transferRecord;

    public boolean                   operationSuccessful;
    public boolean                   recoverableFault;
    public FileTransferError         errCode;
    public Canceller                 canceller;
    public CancellationSignal        cancelSignal;

    public void throwIfCancel() throws OperationCancelledException {
        if (wasCancelled()){
            throw new OperationCancelledException();
        }
    }

    public boolean wasCancelled(){
        return (canceller != null && canceller.isCancelled()) || (cancelSignal != null && cancelSignal.isCanceled());
    }

}
