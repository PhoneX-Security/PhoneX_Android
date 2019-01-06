package net.phonex.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import net.phonex.db.entity.SipMessage;
import net.phonex.pub.proto.FileTransfer.GeneralMsgNotification;
import net.phonex.ui.fileManager.FileManagerActivity;
import net.phonex.util.system.FilenameUtils;

import java.io.File;
import java.io.IOException;


/**
 * utils related to file transfer
 * @author miroc
 *
 */
public class FileTransferUtils {
	private static final String TAG = "FileTransferUtils";
	
	/**
	 * before the actual FileNotificationMessage is send, we store dummy outgoing message in SipMessage DB.
	 * dummy = it's not actually send yet
	 *  
	 * The reason is we want to show it in MessageFragment adapter list and to display progress bar there. 
	 * Once the file is successfully send, the dummy message is send for real and it's status is updated to send
	 * 
	 * @param fromSip format <name>@<server>
	 * @param destSip format <name>@<server>
	 * @param body informative
	 * @param ctx Context, not null
	 */
	public static Uri insertOutgoingFileMsg(String fromSip, String destSip, String body, Context ctx){
    	SipMessage msg = new SipMessage(fromSip, //from
    			destSip, //to
    			destSip, //contact
				body, // body
    			SipMessage.MIME_FILE,  
    			System.currentTimeMillis(),
    			SipMessage.MESSAGE_TYPE_FILE_UPLOADING, null); 
    	msg.setBodyDecrypted(body);
		msg.setRead(false);
		msg.setOutgoing(true);

		Uri u = ctx.getContentResolver().insert(SipMessage.MESSAGE_URI, msg.getContentValues()); 
		Log.df(TAG, "Dummy sendFile msg inserted [%s]", msg.toString());
		return u;
	}
	

	/**
	 * set message property FIELD_TYPE to MESSAGE_TYPE_FILE_REJECTED
	 * @param id message ID
	 * @param ctx
	 */
	public static void setMessageToRejected(long id, Context ctx){
		SipMessage.setMessageType(ctx.getContentResolver(), id, SipMessage.MESSAGE_TYPE_FILE_REJECTED);		
	}

    /**
     * generate Base64 encoded serialized content of protobuf file notification message
     * it notifies that there is file ready for him/her to receive
     * @param nonceId - nonce2 from GetKey protocol, uniquely identifies file
     * @param filename
     * @param sipMessageNonce - unique nonce of SipMessage (as file notification is also associated with some SipMessage)
     * @return
     */
	public static GeneralMsgNotification createFileNotification(String nonceId, String filename, int sipMessageNonce){
		GeneralMsgNotification.Builder builder = GeneralMsgNotification.newBuilder();		
		builder.setFileTransferNonce(nonceId);
		builder.setTitle(filename);
        builder.setNonce(sipMessageNonce);
		return builder.build();
	}

	/**
	 * parse data created by createFileNotification
	 * @param encoded
	 * @return
	 * @throws IOException
	 */
	public static GeneralMsgNotification parseNotification(String encoded) throws IOException{
		byte[] data=  Base64.decode(encoded);
		GeneralMsgNotification msg = GeneralMsgNotification.parseFrom(data);
		return msg;
	}
	
	/**
	 * Broadcast android intent to open a given file.
	 * 
	 * @param absPath
	 */
	public static void openFile(Context ctxt, String absPath, boolean determineMime){
		File fl = new File(absPath);
		Intent newIntent = new Intent(android.content.Intent.ACTION_VIEW);

		String mimeType = "*/*";
		if (determineMime) {
			MimeTypeMap myMime = MimeTypeMap.getSingleton();
			mimeType = myMime.getMimeTypeFromExtension(FilenameUtils.getExtension(absPath));
		}
		
		newIntent.setDataAndType(Uri.fromFile(fl), mimeType);
		newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
		    ctxt.startActivity(newIntent);
		} catch (android.content.ActivityNotFoundException e) {
			// No handler -> open folder
			openFolder(ctxt, fl.getParent());
		}
	}
	
	/**
	 * Opens specified folder - sends general android intent.
	 */
	public static void openFolder(Context ctxt, String folder){
		Intent it = new Intent(ctxt, FileManagerActivity.class);
		it.putExtra(FileManagerActivity.EXTRA_START_PATH, folder);
		ctxt.startActivity(it);
    }
}
