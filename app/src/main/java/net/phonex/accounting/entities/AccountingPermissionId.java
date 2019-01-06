package net.phonex.accounting.entities;

import net.phonex.db.entity.AccountingPermission;

/**
 * Created by miroc on 8.10.15.
 */
public class AccountingPermissionId {
    private long permId;
    private long licId;

    public static AccountingPermissionId from(AccountingPermission accPerm){
        AccountingPermissionId obj  = new AccountingPermissionId();
        obj.permId = accPerm.getPermId();
        obj.licId = accPerm.getLicId();
        return obj;
    }

    public long getPermId() {
        return permId;
    }

    public long getLicId() {
        return licId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountingPermissionId that = (AccountingPermissionId) o;

        if (permId != that.permId) return false;
        return licId == that.licId;

    }

    @Override
    public int hashCode() {
        int result = (int) (permId ^ (permId >>> 32));
        result = 31 * result + (int) (licId ^ (licId >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "AccountingPermissionId{" +
                "permId=" + permId +
                ", licId=" + licId +
                '}';
    }
}
