package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import net.phonex.accounting.PermissionType;
import net.phonex.accounting.entities.AccountingPermissionId;
import net.phonex.core.Constants;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.guava.Joiner;
import net.phonex.util.guava.Lists;
import net.phonex.util.guava.Tuple;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by miroc on 7.10.15.
 */
public class AccountingPermission implements Parcelable {
    public static final String TAG = " AccountingPermission";
    public final static long DEFAULT_LIC_ID = 0l;

    // <ATTRIBUTES>
    protected long id;
    protected long permId; // reference to permission id
    protected long licId; // reference to which bought product does this permission comes from
    protected String name; // permission name

    protected int localView; // If 1 this is a local view of the counter. Otherwise it is a server view.

    protected long spent;      // Currently spent value from this permission.
    protected long value;     // Non-changing value of particular permission, from server.
    protected String aref;       // Not used for now.

    protected Date dateCreated;  // Time it was seen for the first time.
    protected Date dateModified; // Time of the last modification.
    
    protected long actionIdFirst;   // Accounting Log Action ID associated with this permission, first one.
    protected long actionCtrFirst;  // Accounting Log Counter ID associated with this permission, first one.
    protected long actionIdLast;    // Accounting Log Action ID associated with this permission, last one.
    protected long actionCtrLast;   // Accounting Log Counter ID associated with this permission, last one.
    protected long aggregationCount; // Number of records accounted to this permission spent record.

    // Fields defined in the policy.
    protected int subscription;  // If 1 the record is subscription, has from-to validity.
    protected Date validFrom;       // Permission validity from.
    protected Date validTo;         // Permission validity to.
    // </ATTRIBUTES>

    public static final String TABLE = "AccountingPermission";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_PERM_ID = "permId";
    public static final String FIELD_LIC_ID = "licId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_LOCAL_VIEW = "localView";
    public static final String FIELD_SPENT = "spent";
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_AREF = "aref";
    public static final String FIELD_DATE_CREATED = "dateCreated";
    public static final String FIELD_DATE_MODIFIED = "dateModified";
    public static final String FIELD_ACTION_ID_FIRST = "actionIdFirst";
    public static final String FIELD_ACTION_CTR_FIRST = "actionCtrFirst";
    public static final String FIELD_ACTION_ID_LAST = "actionIdLast";
    public static final String FIELD_ACTION_CTR_LAST = "actionCtrLast";
    public static final String FIELD_AGGREGATION_COUNT = "aggregationCount";
    public static final String FIELD_SUBSCRIPTION = "subscription";
    public static final String FIELD_VALID_FROM = "validFrom";
    public static final String FIELD_VALID_TO = "validTo";

    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);

    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");


    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_PERM_ID, FIELD_LIC_ID, FIELD_NAME, FIELD_LOCAL_VIEW, FIELD_SPENT, FIELD_VALUE, FIELD_AREF, FIELD_DATE_CREATED, FIELD_DATE_MODIFIED, FIELD_ACTION_ID_FIRST, FIELD_ACTION_CTR_FIRST, FIELD_ACTION_ID_LAST, FIELD_ACTION_CTR_LAST, FIELD_AGGREGATION_COUNT, FIELD_SUBSCRIPTION, FIELD_VALID_FROM, FIELD_VALID_TO
    };


    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_PERM_ID + " INTEGER DEFAULT 0, "
            + FIELD_LIC_ID + " INTEGER DEFAULT 0, "
            + FIELD_NAME + " TEXT, "
            + FIELD_LOCAL_VIEW + " INTEGER DEFAULT 0, "
            + FIELD_SPENT + " INTEGER DEFAULT 0, "
            + FIELD_VALUE + " INTEGER DEFAULT 0, "
            + FIELD_AREF + " TEXT, "
            + FIELD_DATE_CREATED + " INTEGER DEFAULT 0, "
            + FIELD_DATE_MODIFIED + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_ID_FIRST + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_CTR_FIRST + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_ID_LAST + " INTEGER DEFAULT 0, "
            + FIELD_ACTION_CTR_LAST + " INTEGER DEFAULT 0, "
            + FIELD_AGGREGATION_COUNT + " INTEGER DEFAULT 0, "
            + FIELD_SUBSCRIPTION + " INTEGER DEFAULT 0, "
            + FIELD_VALID_FROM + " INTEGER DEFAULT 0, "
            + FIELD_VALID_TO + " INTEGER DEFAULT 0"
            + ");";

    public AccountingPermission(Cursor c) {
        createFromCursor(c);
    }


    private final void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_PERM_ID.equals(colname)) {
                this.permId = c.getLong(i);
            } else if (FIELD_LIC_ID.equals(colname)) {
                this.licId = c.getLong(i);
            } else if (FIELD_NAME.equals(colname)) {
                this.name = c.getString(i);
            } else if (FIELD_LOCAL_VIEW.equals(colname)) {
                this.localView = c.getInt(i);
            } else if (FIELD_SPENT.equals(colname)) {
                this.spent = c.getLong(i);
            } else if (FIELD_VALUE.equals(colname)) {
                this.value = c.getLong(i);
            } else if (FIELD_AREF.equals(colname)) {
                this.aref = c.getString(i);
            } else if (FIELD_DATE_CREATED.equals(colname)) {
                this.dateCreated = new Date(c.getLong(i));
            } else if (FIELD_DATE_MODIFIED.equals(colname)) {
                this.dateModified = new Date(c.getLong(i));
            } else if (FIELD_ACTION_ID_FIRST.equals(colname)) {
                this.actionIdFirst = c.getLong(i);
            } else if (FIELD_ACTION_CTR_FIRST.equals(colname)) {
                this.actionCtrFirst = c.getLong(i);
            } else if (FIELD_ACTION_ID_LAST.equals(colname)) {
                this.actionIdLast = c.getLong(i);
            } else if (FIELD_ACTION_CTR_LAST.equals(colname)) {
                this.actionCtrLast = c.getLong(i);
            } else if (FIELD_AGGREGATION_COUNT.equals(colname)) {
                this.aggregationCount = c.getLong(i);
            } else if (FIELD_SUBSCRIPTION.equals(colname)) {
                this.subscription = c.getInt(i);
            } else if (FIELD_VALID_FROM.equals(colname)) {
                this.validFrom = new Date(c.getLong(i));
            } else if (FIELD_VALID_TO.equals(colname)) {
                this.validTo = new Date(c.getLong(i));
            } else {
                Log.w(TAG, "Unknown column name: " + colname);
            }
        }
    }


    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        // do not put ID there where e.g. inserting (id = 0)
//        args.put(FIELD_ID, id);
        args.put(FIELD_PERM_ID, permId);
        args.put(FIELD_LIC_ID, licId);
        if (this.name != null)
            args.put(FIELD_NAME, name);
        args.put(FIELD_LOCAL_VIEW, localView);
        args.put(FIELD_SPENT, spent);
        args.put(FIELD_VALUE, value);
        if (this.aref != null)
            args.put(FIELD_AREF, aref);
        if (this.dateCreated != null)
            args.put(FIELD_DATE_CREATED, dateCreated.getTime());
        if (this.dateModified != null)
            args.put(FIELD_DATE_MODIFIED, dateModified.getTime());
        args.put(FIELD_ACTION_ID_FIRST, actionIdFirst);
        args.put(FIELD_ACTION_CTR_FIRST, actionCtrFirst);
        args.put(FIELD_ACTION_ID_LAST, actionIdLast);
        args.put(FIELD_ACTION_CTR_LAST, actionCtrLast);
        args.put(FIELD_AGGREGATION_COUNT, aggregationCount);
        args.put(FIELD_SUBSCRIPTION, subscription);
        if (this.validFrom != null)
            args.put(FIELD_VALID_FROM, validFrom.getTime());
        if (this.validTo != null)
            args.put(FIELD_VALID_TO, validTo.getTime());
        return args;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeLong(this.permId);
        dest.writeLong(this.licId);
        dest.writeString(this.name);
        dest.writeInt(this.localView);
        dest.writeLong(this.spent);
        dest.writeLong(this.value);
        dest.writeString(this.aref);
        dest.writeLong(dateCreated != null ? dateCreated.getTime() : -1);
        dest.writeLong(dateModified != null ? dateModified.getTime() : -1);
        dest.writeLong(this.actionIdFirst);
        dest.writeLong(this.actionCtrFirst);
        dest.writeLong(this.actionIdLast);
        dest.writeLong(this.actionCtrLast);
        dest.writeLong(this.aggregationCount);
        dest.writeInt(this.subscription);
        dest.writeLong(validFrom != null ? validFrom.getTime() : -1);
        dest.writeLong(validTo != null ? validTo.getTime() : -1);
    }

    public AccountingPermission() {
    }

    private AccountingPermission(Parcel in) {
        this.id = in.readLong();
        this.permId = in.readLong();
        this.licId = in.readLong();
        this.name = in.readString();
        this.localView = in.readInt();
        this.spent = in.readLong();
        this.value = in.readLong();
        this.aref = in.readString();
        long tmpDateCreated = in.readLong();
        this.dateCreated = tmpDateCreated == -1 ? null : new Date(tmpDateCreated);
        long tmpDateModified = in.readLong();
        this.dateModified = tmpDateModified == -1 ? null : new Date(tmpDateModified);
        this.actionIdFirst = in.readLong();
        this.actionCtrFirst = in.readLong();
        this.actionIdLast = in.readLong();
        this.actionCtrLast = in.readLong();
        this.aggregationCount = in.readLong();
        this.subscription = in.readInt();
        long tmpValidFrom = in.readLong();
        this.validFrom = tmpValidFrom == -1 ? null : new Date(tmpValidFrom);
        long tmpValidTo = in.readLong();
        this.validTo = tmpValidTo == -1 ? null : new Date(tmpValidTo);
    }

    public static final Parcelable.Creator<AccountingPermission> CREATOR = new Parcelable.Creator<AccountingPermission>() {
        public AccountingPermission createFromParcel(Parcel source) {
            return new AccountingPermission(source);
        }

        public AccountingPermission[] newArray(int size) {
            return new AccountingPermission[size];
        }
    };

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPermId() {
        return permId;
    }

    public void setPermId(long permId) {
        this.permId = permId;
    }

    public long getLicId() {
        return licId;
    }

    public void setLicId(long licId) {
        this.licId = licId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLocalView() {
        return localView;
    }

    public void setLocalView(int localView) {
        this.localView = localView;
    }

    public long getSpent() {
        return spent;
    }

    public void setSpent(long spent) {
        this.spent = spent;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public String getAref() {
        return aref;
    }

    public void setAref(String aref) {
        this.aref = aref;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }

    public long getActionIdFirst() {
        return actionIdFirst;
    }

    public void setActionIdFirst(long actionIdFirst) {
        this.actionIdFirst = actionIdFirst;
    }

    public long getActionCtrFirst() {
        return actionCtrFirst;
    }

    public void setActionCtrFirst(long actionCtrFirst) {
        this.actionCtrFirst = actionCtrFirst;
    }

    public long getActionIdLast() {
        return actionIdLast;
    }

    public void setActionIdLast(long actionIdLast) {
        this.actionIdLast = actionIdLast;
    }

    public long getActionCtrLast() {
        return actionCtrLast;
    }

    public void setActionCtrLast(long actionCtrLast) {
        this.actionCtrLast = actionCtrLast;
    }

    public long getAggregationCount() {
        return aggregationCount;
    }

    public void setAggregationCount(long aggregationCount) {
        this.aggregationCount = aggregationCount;
    }

    public int getSubscription() {
        return subscription;
    }

    public void setSubscription(int subscription) {
        this.subscription = subscription;
    }

    public Date validTo() {
        return validTo;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public Date getValidTo() {
        return validTo;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidTo(Date validTo) {
        this.validTo = validTo;
    }

    public static List<AccountingPermission> getAllByCompositeId(ContentResolver cr, List<AccountingPermissionId> ids) {
        List<AccountingPermission> result = new ArrayList<>();

        Cursor c;
        if (ids != null){
            List<String> selParts = new ArrayList<>(ids.size());
            List<String> selArgs = new ArrayList<>(ids.size() * 2); // key consists of 2 attributes

            for (AccountingPermissionId id : ids){
                selParts.add(String.format("( %s=? AND %s=?)", FIELD_LIC_ID, FIELD_PERM_ID));
                selArgs.add(String.valueOf(id.getLicId()));
                selArgs.add(String.valueOf(id.getPermId()));
            }

            c = cr.query(URI, FULL_PROJECTION,
                    Joiner.on(" OR ").join(selParts),
                    selArgs.toArray(new String[selArgs.size()]),
                    null);
        } else {
            c = cr.query(URI, FULL_PROJECTION, null, null, null);
        }

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        result.add(new AccountingPermission(c));
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error on looping over sip profiles", e);
            } finally {
                MiscUtils.closeCursorSilently(c);
            }
        }

        return result;
    }

    /**
     * Server and local time may be de-synchronized. This can be a problem when purchasing a license and it starts in e.g. ~5 mins in future.
     * Therefore take licenses starting up to 15 minutes in the future as already valid.
     * @param cr
     * @param name if null, all names are taken
     * @param validForDate
     * @return
     */
    public static List<AccountingPermission> getLocalPermissions(ContentResolver cr, String name, Date validForDate){
        return getLocalPermissions(cr, name, validForDate, FULL_PROJECTION);
    }

    public static List<AccountingPermission> getLocalPermissions(ContentResolver cr, String name, Date validForDate, String[] projection){
        long time = validForDate.getTime();
        long validFrom = time + 15*60*1000;

        return getLocalPermissions(cr, name, new Date(validFrom), new Date(time), projection);
    }

    private static List<AccountingPermission> getLocalPermissions(ContentResolver cr, String name, Date validFrom, Date validTo, String[] projection){
        String sel;
        String[] selArgs;
        if (name != null){
            sel = FIELD_LOCAL_VIEW + "=? AND " + FIELD_NAME + "=? AND " + FIELD_VALID_FROM + " <= ? AND " + FIELD_VALID_TO + ">= ?";
            selArgs = new String[]{String.valueOf(1), name, String.valueOf(validFrom.getTime()), String.valueOf(validTo.getTime())};
        } else {
            sel = FIELD_LOCAL_VIEW + "=? AND " + FIELD_VALID_FROM + " <= ? AND " + FIELD_VALID_TO + ">= ?";
            selArgs = new String[]{String.valueOf(1), String.valueOf(validFrom.getTime()), String.valueOf(validTo.getTime())};
        }

        String sortOrder = String.format("%s DESC, %s ASC", FIELD_SUBSCRIPTION, FIELD_VALID_FROM);
        List<AccountingPermission> result = new ArrayList<>();

        Cursor c = cr.query(URI, projection, sel, selArgs, sortOrder);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        result.add(new AccountingPermission(c));
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error on looping over sip profiles", e);
            } finally {
                MiscUtils.closeCursorSilently(c);
            }
        }
        return result;
    }


    public static int update(ContentResolver cr, long id, ContentValues cv){
        Log.vf(TAG, "update; cv=%s", cv);
        return cr.update(URI, cv,
                FIELD_ID + "=?",
                new String[] { String.valueOf(id) });
    }

    public PermissionType getPermissionType(){
        return PermissionType.fromName(getName());
    }

    public boolean isSubscription(){
        return subscription == 1;
    }

    @Override
    public String toString() {
        return "AP{" +
                "id=" + id +
                ", permId=" + permId +
                ", licId=" + licId +
                ", name='" + name + '\'' +
                ", localView=" + localView +
                ", spent=" + spent +
                ", value=" + value +
                ", dateCreated=" + dateCreated +
                ", aggregationCount=" + aggregationCount +
                ", subscription=" + subscription +
                ", validFrom=" + validFrom +
                ", validTo=" + validTo +
                '}';
    }

    public static Tuple<Integer, Integer> getSubscriptionsAndPackagesCount(ContentResolver cr) {
        List<AccountingPermission> permissions = getLocalPermissions(cr, null, new Date(), new String[]{FIELD_LIC_ID, FIELD_SUBSCRIPTION});

        List<Long> subscriptionsList = Lists.newArrayList();
        List<Long> packagesList = Lists.newArrayList();

        for(AccountingPermission permission : permissions){
            long licId = permission.getLicId();
            if (licId == DEFAULT_LIC_ID){
                // skip default
                continue;
            }

            if (permission.isSubscription()){
                if (!subscriptionsList.contains(licId)){
                    subscriptionsList.add(licId);
                }
            } else {
                if (!packagesList.contains(licId)){
                    packagesList.add(licId);
                }
            }
        }

        return Tuple.of(subscriptionsList.size(), packagesList.size());
    }
}
