package net.phonex.ui.notifications;

import net.phonex.db.entity.CallLog;

/**
 * Wraps notification types.
 */
public class NotificationWrapper {
    public final Object obj;
    public final int type;
    public NotificationWrapper(Object obj, int type){
        this.obj = obj;
        this.type = type;
    }

    public CallLog toCallLog(){
        return (CallLog) obj;
    }
}
