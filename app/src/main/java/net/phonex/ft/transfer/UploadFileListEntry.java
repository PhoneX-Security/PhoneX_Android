package net.phonex.ft.transfer;

/**
 * stored in fileUploadList
 * @author miroc
 *
 */
public class UploadFileListEntry {
	public UploadFileParams params;
    public volatile boolean cancelled=false;
}
