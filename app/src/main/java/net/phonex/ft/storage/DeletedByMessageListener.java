package net.phonex.ft.storage;

/**
 * Created by Matus on 7/10/2015.
 */
public interface DeletedByMessageListener extends FileActionListener {
    boolean filesDeletedByMessageId(Long messageId);
}
