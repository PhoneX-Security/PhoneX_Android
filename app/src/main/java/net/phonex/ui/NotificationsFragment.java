package net.phonex.ui;

import android.app.Activity;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import net.phonex.R;
import net.phonex.db.entity.CallLog;
import net.phonex.db.entity.SipClist;
import net.phonex.db.scheme.CallLogScheme;
import net.phonex.ui.cl.ContactListMenuDialogFragment;
import net.phonex.ui.customViews.CursorListFragment;
import net.phonex.ui.notifications.NotificationListAdapter;
import net.phonex.ui.notifications.NotificationView;
import net.phonex.ui.notifications.NotificationWrapper;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

/**
 * Notifications (missed calls, etc.)
 * @author miroc
 *
 */
public class NotificationsFragment extends CursorListFragment implements NotificationListAdapter.OnNotificationListAction {
    private static final String TAG = "NotificationsFragment";

    public static final String FIELD_TYPE = "notifType";
    public static final int CURSOR_ALL_CALLS = 0;

    private static final int MENU_DELETE = 0;
    private static final int MENU_DELETE_TYPE = 2;

    private NotificationListAdapter adapter;
    public static NotificationsFragment newInstance() {
        return new NotificationsFragment();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);


        adapter = new NotificationListAdapter(getActivity(), null, 0);
        adapter.setActionListener(this);
        setListAdapter(adapter);

        ListView lv = getListView();
        registerForContextMenu(lv);

        getLoaderManager().initLoader(CURSOR_ALL_CALLS, null, this);
    }

    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    	View v = inflater.inflate(R.layout.notifications_fragment, container, false);
        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    // Here set the Loader and the Cursor for projection
    // Loader - create cursor loader for contact list elements, normal projection (without binary certificate)
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.v(TAG, "onCreateLoader()");

        if (CURSOR_ALL_CALLS == id) {
            return new CursorLoader(
                    getActivity(),
                    CallLogScheme.URI,
                    CallLogScheme.FULL_PROJECTION,
                    null,
                    null,
                    CallLogScheme.FIELD_DATE + " desc");
        } else {
            Log.e(TAG, "Unknown cursor to load");
            return null;
        }
    }

    @Override
    public void changeCursor(Loader<Cursor> loader, Cursor c) {
        if (c == null || c.isClosed()){
            Log.wf(TAG, "changeCursor; cursor is null or closed");
            return;
        }

    	try {
            final int cursorId = loader.getId();
            NotifyCursorWrapper w = new NotifyCursorWrapper(c, cursorId);
    		adapter.swapCursor(w);

            // Hide call log notification - user is currently able to read it in this dialog.
//            try {
//                final Activity activity = this.getActivity();
//                if (activity!=null){
//                    final Context ctxt = activity.getApplicationContext();
//                    final Intent intent = XService.buildIntent(ctxt, Constants.ACTION_HIDE_CALLLOG_NOTIF);
//                    MiscUtils.sendBroadcast(ctxt, intent);
//                }
//            } catch(Exception ex){
//                Log.e(TAG, "Cannot hide call log notification.");
//            }

        } catch(Exception e){
    		Log.w(TAG, "ChangeCursor exception", e);
    	}   
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.add(0, MENU_DELETE, 0, R.string.notif_delete);
        menu.add(0, MENU_DELETE_TYPE, 0, R.string.notif_delete_type);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        final Object tag = info.targetView.getTag();
        if (tag == null || !(tag instanceof NotificationView)){
            return super.onContextItemSelected(item);
        }

        NotificationView nv = (NotificationView) tag;
        NotificationWrapper w = (NotificationWrapper) nv.primaryActionView.getTag();
        switch (item.getItemId()) {
            case MENU_DELETE: {
                if (w.type== CURSOR_ALL_CALLS) {
                    final CallLog clog = (CallLog) w.obj;
                    getActivity().getContentResolver().delete(
                            CallLogScheme.URI,
                            CallLogScheme.FIELD_ID+"=?",
                            new String[] {String.valueOf(clog.getId())}
                    );
                }

                break;
            }

            case MENU_DELETE_TYPE: {
                if (w.type== CURSOR_ALL_CALLS) {
                    getActivity().getContentResolver().delete(
                            CallLogScheme.URI,
                            null,
                            null
                    );
                }

                break;
            }
            default:
                break;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void showNotificationMenu(NotificationWrapper w) {
        AnalyticsReporter.from(this).buttonClick(AppButtons.MAIN_ACTIVITY_OPEN_CONVERSATION);

        if (w.type == NotificationsFragment.CURSOR_ALL_CALLS){
            SipClist contact = SipClist.getProfileFromDbSip(getActivity(), w.toCallLog().getRemoteContactSip());
            ContactListMenuDialogFragment contactFragment = ContactListMenuDialogFragment.newInstance(contact, false);
            contactFragment.show(getFragmentManager(), "menu");
        }
    }

    /**
     * Adds Cursor ID to the cursor data.
     */
    private static class NotifyCursorWrapper extends CursorWrapper {
        private final int cursorId;
        private int columnCount;

        private NotifyCursorWrapper(Cursor cursor, int id) {
            super(cursor);
            cursorId = id;
            columnCount = cursor.getColumnCount();
        }

        @Override
        public int getColumnCount() {
            columnCount = super.getColumnCount();
            return columnCount+1;
        }

        @Override
        public int getColumnIndex(String columnName) {
            if (FIELD_TYPE.equals(columnName)){
                return super.getColumnCount();
            }

            return super.getColumnIndex(columnName);
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
            if (FIELD_TYPE.equals(columnName)){
                return super.getColumnCount();
            }

            return super.getColumnIndexOrThrow(columnName);
        }

        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex==columnCount){
                return FIELD_TYPE;
            }

            return super.getColumnName(columnIndex);
        }

        @Override
        public String[] getColumnNames() {
            String[] columns = new String[columnCount+1];
            System.arraycopy(super.getColumnNames(), 0, columns, 0, columnCount);
            columns[columnCount] = FIELD_TYPE;
            return columns;
        }

        @Override
        public int getInt(int columnIndex) {
            if (columnIndex==columnCount){
                return cursorId;
            }

            return super.getInt(columnIndex);
        }
    }
}
