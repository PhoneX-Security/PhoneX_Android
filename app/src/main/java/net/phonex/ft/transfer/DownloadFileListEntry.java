package net.phonex.ft.transfer;

/**
 * List entry for certificate-check for one user.
* @author ph4r05
*
*/
public class DownloadFileListEntry {
    public DownloadFileParams params;
    public boolean storeResult=false;
    public boolean deleteOnly=false;
    public volatile boolean cancelled=false;
}
