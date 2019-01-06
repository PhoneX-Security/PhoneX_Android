package net.phonex.accounting.entities;

/**
 * Created by miroc on 8.10.15.
 */
public class AccountingLogId {
    private long id;
    private long ctr;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountingLogId that = (AccountingLogId) o;

        if (id != that.id) return false;
        return ctr == that.ctr;

    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (ctr ^ (ctr >>> 32));
        return result;
    }
}

