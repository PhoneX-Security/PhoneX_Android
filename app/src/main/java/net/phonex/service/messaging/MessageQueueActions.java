package net.phonex.service.messaging;

import net.phonex.db.entity.QueuedMessage;

/**
 * Created by miroc on 19.10.14.
 */
public interface MessageQueueActions {
   public int deleteMessage(long messageId);

   public int deleteAndReportToAppLayer(QueuedMessage msg, SendingState state);

   public int setMessageProcessed(long messageId, boolean isProcessed);
   public int storeFinalMessageWithHash(long messageId, String finalMessage);
}
