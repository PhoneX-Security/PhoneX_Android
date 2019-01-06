package net.phonex.soap;

import net.phonex.db.entity.SipClist;

/**
 * Created by miroc on 6.1.15.
 */
public class ClistRenameTaskParams extends ClistAddTaskParams {
    protected String newDisplayName;

    public ClistRenameTaskParams(SipClist profile) {
        super(profile);
    }

    public String getNewDisplayName() {
        return newDisplayName;
    }

    public void setNewDisplayName(String newDisplayName) {
        this.newDisplayName = newDisplayName;
    }
}
