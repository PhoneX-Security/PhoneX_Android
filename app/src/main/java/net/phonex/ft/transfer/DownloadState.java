package net.phonex.ft.transfer;

import android.os.CancellationSignal;

import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.UserCertificate;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.FTHolder;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.soap.entities.FtStoredFile;
import static net.phonex.ft.FTHolder.UnpackingResult;
import static net.phonex.ft.FTHolder.DownloadFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dusanklinec on 10.04.15.
 */
public class DownloadState {
    public Long                     msgId;
    public Long                     queueMsgId;
    public String                   nonce2;
    public String                   sender;
    public String                   domain;
    public SipMessage               msg;

    public DownloadFileParams       params;
    public FtStoredFile             storedFile;
    public DHOffline                dhOffline;
    public UserCertificate          senderCrt;
    public DHKeyHelper              dhelper;
    public FTHolder                 ftHolder;
    public FTHolder.DownloadResult  downResult;
    public UnpackingResult unpackResult;
    public boolean                      deleteOnly;
    public boolean                      deletedFromServer;
    public boolean                      downloadPackRightNow;
    public net.phonex.pub.proto.FileTransfer.MetaFile metaFile;
    public List<DownloadFile>           transferFiles = new ArrayList<>();
    public Long                 transferRecordId;
    public FileTransfer         transferRecord;

    public List<String>                            filePaths = new ArrayList<>();
    public boolean                                 didErrorOccurr;
    public boolean                                 didTimeout;
    public boolean                                 didCancel;
    public boolean                                 operationSuccessful;
    public boolean                                 recoverableFault;
    public boolean                                 didMacFail;
    public FileTransferError                       errCode;

    public Canceller                               canceller;
    public CancellationSignal                      cancelSignal;

    public void throwIfCancel() throws OperationCancelledException {
        if (wasCancelled()){
            throw new OperationCancelledException();
        }
    }

    public boolean wasCancelled(){
        return (canceller != null && canceller.isCancelled());
    }
}
