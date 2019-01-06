package net.phonex.ui.cl;


import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.SipProfileState;
import net.phonex.db.entity.PairingRequest;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.proto.PushNotifications;
import net.phonex.ui.PhonexActivity;
import net.phonex.ui.broadcast.BroadcastMessageActivity;
import net.phonex.ui.customViews.EmptyRecyclerView;
import net.phonex.ui.pairingRequest.PairingRequestsActivity;
import net.phonex.ui.slidingtab.TabInterface;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * ContactList Fragment with recycler view
 * @author miroc
 *
 */
public class ContactListFragment extends Fragment
        implements
        LoaderManager.LoaderCallbacks<Cursor>,
        TabInterface{
    private static final String TAG = "ContactListFragment";
    private static final String PARAM_CHECKED_CONTACTS = "param_checked_contacts";

    private ActionMode actionMode;
    private PreferencesConnector prefs;
    private int lastKnownSortOrder = -1;

    private final Handler handler = new Handler();
    private final RegistrationChangeListener registrationChangeListener = new RegistrationChangeListener();
    private final PairingRequestContentObserver prContentObserver = new PairingRequestContentObserver(handler);
    private String filter;

    // recycler view
    @InjectView(R.id.contact_request_container) RelativeLayout contactRequestContainer;
    @InjectView(R.id.contact_request_text) TextView contactRequestText;
    @InjectView(R.id.recycler_view) EmptyRecyclerView recyclerView;
    @InjectView(R.id.empty_view) TextView emptyView;
    private ContactListCursorAdapter recyclerAdapter;
    private RecyclerView.LayoutManager recyclerLayoutManager;
    /**
     * We manage checked contacts (selected in action mode) manually to deal with filtering
     */
    private Set<SipClist> checkedContacts = new HashSet<>();

    public static Fragment newInstance() {
        ContactListFragment f = new ContactListFragment();
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View v = inflater.inflate(R.layout.clist_list2, container, false);
        ButterKnife.inject(this, v);
        initRecyclerView();

        updatePairingRequestNotification();

        contactRequestContainer.setOnClickListener(view -> {
            Intent intent = new Intent(getActivity(), PairingRequestsActivity.class);
            startActivity(intent);
        });

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getLoaderManager().initLoader(0, null, this);
    }

    private void initRecyclerView() {
        recyclerView.setHasFixedSize(true);
        recyclerLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        recyclerView.setEmptyView(emptyView);

        // add divider manually
//        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), null));

        recyclerAdapter = new ContactListCursorAdapter(getActivity(), null);

        recyclerAdapter.setShowUserNames(prefs.getBoolean(PhonexConfig.CONTACT_LIST_SHOW_USERNAMES));
        recyclerAdapter.setIsValidForCall(isValidForCall(getActivity()));
        recyclerAdapter.setContactListFragment(this);
        recyclerAdapter.setActionListener(new ContactListActionListener());

        recyclerView.setAdapter(recyclerAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    private void updateTitle() {
        Activity activity = getActivity();
        if (activity != null && actionMode != null){
            int itemsCount = checkedContacts != null ? checkedContacts.size() : 0;
            String title = activity.getResources().getQuantityString(R.plurals.clist_actionmode_selected, itemsCount);
            actionMode.setTitle(String.format(title, itemsCount));
        }
    }

    private void updatePairingRequestNotification(){
        if (getActivity() == null){
            Log.wf(TAG, "updatePairingRequestNotification; activity null");
            return;
        }

        int requestCount = PairingRequest.getNonResolvedCount(getActivity().getContentResolver());

        if (requestCount > 0){
            contactRequestText.setText(String.format(getString(R.string.pairing_request_count), requestCount));
            if (PairingRequest.getUnseenCount(getActivity().getContentResolver()) > 0){
                contactRequestText.setTextColor(getResources().getColor(R.color.phonex_color_accent));
            } else {
                contactRequestText.setTextColor(LayoutUtils.getPrimaryTextColor(getActivity()));
            }
            contactRequestContainer.setVisibility(View.VISIBLE);
        } else {
            contactRequestContainer.setVisibility(View.GONE);
        }
    }

    private void toggleSelection(int position, SipClist clist){
        boolean wasSelected = recyclerAdapter.toggleSelection(position);
        if (wasSelected){
            checkedContacts.add(clist);
        } else {
            checkedContacts.remove(clist);
        }
        if (checkedContacts.size() > 0){
            updateTitle();
        } else {
            actionMode.finish();
        }
    }

    private ArrayList<SipClist> getCheckedContacts(){
        ArrayList<SipClist> contacts = new ArrayList<>(checkedContacts);
        return contacts;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        prefs = new PreferencesConnector(activity);
        lastKnownSortOrder = getCurrentSortOrder();

//        activity.getContentResolver().registerContentObserver(SipClist.CLIST_STATE_URI, true, clContentObserver);
//        activity.getContentResolver().registerContentObserver(SipClist.CLIST_STATE_ID_URI_BASE, true, clContentObserver);

        activity.getContentResolver().registerContentObserver(PairingRequest.URI, true, prContentObserver);
        activity.getContentResolver().registerContentObserver(PairingRequest.ID_URI_BASE, true, prContentObserver);

        MiscUtils.registerReceiver(activity, registrationChangeListener, new IntentFilter(Intents.ACTION_SIP_REGISTRATION_CHANGED));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Activity activity = getActivity();
        if (activity != null){
            activity.unregisterReceiver(registrationChangeListener);
//            activity.getContentResolver().unregisterContentObserver(clContentObserver);
            activity.getContentResolver().unregisterContentObserver(prContentObserver);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If sort order has changed, re-create the loader.
        final int curSortOrder = getCurrentSortOrder();
//        if (curSortOrder==-1){
//            return;
//        }

        if (curSortOrder!=lastKnownSortOrder){
            Log.v(TAG, "sortOrder has changed");
            lastKnownSortOrder = curSortOrder;
//            restartLoader();
        }

        Log.vf(TAG, "onResume; restarting loader");
        getLoaderManager().restartLoader(0, getArguments(), ContactListFragment.this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.actionMode != null) {
            this.actionMode.finish();
        }
    }


    // Here set the Loader and the Cursor for projection
    // Loader - create cursor loader for contact list elements, normal projection (without binary certificate)
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //Log.v(TAG, "onCreateLoader()");

        // Default sort order: case insensitive on display name.
        String sortOrder = "LOWER("+SipClist.FIELD_DISPLAY_NAME+")";

        if (sortOnlineFirst()){
            // We want to show offline statuses (int value 1) below all other statuses (int values 0,2,3,4...)
            int statusOfflineValue = PushNotifications.PresencePush.Status.OFFLINE_VALUE;
            String statusValueSortOrder = "CASE " + SipClist.FIELD_PRESENCE_STATUS_TYPE  + " WHEN " + statusOfflineValue + " THEN 0 ELSE 1 END";
            sortOrder = statusValueSortOrder + " DESC, " + sortOrder;
        }

        String selection = null;
        String[] selectionArgs = null;

        if (!TextUtils.isEmpty(filter)){
            selection = SipClist.FIELD_DISPLAY_NAME + " LIKE ? OR " + SipClist.FIELD_SIP + " LIKE ?";
            // all sips have postfix 'phone-x.net', therefore filter
            selectionArgs = new String[]{"%" + filter + "%", "%" + filter + "%@phone-x.net"};
        }

        return new CursorLoader(getActivity(), SipClist.CLIST_STATE_URI, SipClist.CONTACT_LIST_PROJECTION, selection, selectionArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.vf(TAG, "onLoadFinished");
        changeCursor(loader, data);
        restoreContactsSelection();
    }

    private void changeCursor(Loader<Cursor> loader, Cursor c) {
        Log.vf(TAG, "changeCursor");
        if (c == null){
            Log.wf(TAG, "changeCursor; cursor is null");
            return;
        } else if (c.isClosed()){
            Log.wf(TAG, "changeCursor; cursor is closed");
            return;
        }


        try {
            Log.vf(TAG, "changeCursor, count=%d", c.getCount());
            recyclerAdapter.swapCursor(c);
        } catch(Exception e){
            Log.w(TAG, "ChangeCursor exception", e);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.vf(TAG, "onLoaderReset");
        changeCursor(loader, null);
    }

    private void restoreContactsSelection(){
        // restore possible selection when data are reloaded
        if (actionMode != null && checkedContacts != null && checkedContacts.size() > 0){
            recyclerAdapter.clearSelections();
            int count = recyclerAdapter.getItemCount();
            for (int i = 0; i < count; i++){
                SipClist item = new SipClist(recyclerAdapter.getItem(i));
                if (checkedContacts.contains(item)){
                    recyclerAdapter.setSelection(i, true);
                }
            }
        }
    }

	public void showContactMenu(SipClist clist) {
        Log.df(TAG, "showContactMenu");
		if(actionMode != null) {
            Log.df(TAG, "showContactMenu; action mode is ON, do not show menu");
            actionMode.invalidate();
            // Don't see details in this case
            return;
        }
		
		ContactListMenuDialogFragment contactFragment = ContactListMenuDialogFragment.newInstance(clist);
		contactFragment.show(getFragmentManager(), "menu");
	}

    public synchronized void filterByDisplayName(String filter){
        this.filter = filter;

        if (recyclerAdapter != null){
            recyclerAdapter.clearSelections();
        }

        restartLoader();
    }

    /**
     * Loads current settings for the sort order of the contact list from preferences.
     * @return
     */
    private int getCurrentSortOrder(){
        if (prefs==null || recyclerAdapter == null){
            return -1;
        }
        return sortOnlineFirst() ? 1 : 0;
    }

    public boolean isValidForCall(Context ctx){
        if (ctx == null){
            Log.wf(TAG, "isValidForCall; context is null, returning false");
            return false;
        }
        SipProfileState state = SipProfileState.getProfileState(ctx, SipProfile.USER_ID);
        if (state == null || !state.isValidForCall()){
            return false;
        }
        return true;
    }

    private boolean sortOnlineFirst(){
        if (recyclerAdapter == null || prefs ==null){
            return false;
        }
        // in case of state not being valid for call or not initialized yet, also display all contacts as offline with appropriate sorting
        return recyclerAdapter.isValidForCall() && prefs.getBoolean(PhonexConfig.CONTACT_LIST_SORT_ONLINE);
    }

//    private synchronized void restartLoader(){
    private void restartLoader(){
        if (recyclerAdapter != null && isAdded()){
            recyclerAdapter.setIsValidForCall(isValidForCall(getActivity()));
            getLoaderManager().restartLoader(0, getArguments(), ContactListFragment.this);
        }
    }

    @Override
    public void onTabUnselected() {
        // TODO in case of rotation + not ending action mode in onPause method, this may cause crash.
        // Possible connection with FragmentPagerAdapter and it's fragment lifecycle, need to investigate more.
        if (actionMode != null){
            actionMode.finish();
        }
    }

    /**
     * Listener for SipRegistration change
     */
    private class RegistrationChangeListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action){
                case (Intents.ACTION_SIP_REGISTRATION_CHANGED):
                    Log.vf(TAG, "RegistrationChangeListener; Intent [%s] retrieved, restarting ContactList loader", action);
                    if (isAdded()){
                        // PHON-683 sometimes (e.g. after app restart) REGISTRATION_CHANGED is received during fragment startup,
                        // which prevents loader from loading all contacts for some reason.
                        // try delay this event to avoid collision
                        handler.postDelayed(() -> restartLoader(), 250);
                    }
                    break;
            }
        }
    }

    private class PairingRequestContentObserver extends ContentObserver {
        public PairingRequestContentObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updatePairingRequestNotification();
        }
    }

//    /**
//     * Observer for changes of CList DB
//     */
//    private class ContactListContentObserver extends ContentObserver{
//        public ContactListContentObserver(Handler h) {
//            super(h);
//        }
//
//        @Override
//        public void onChange(boolean selfChange) {
//            onChange(selfChange, null);
//        }
//
//        @Override
//        public void onChange(boolean selfChange, Uri uri) {
//            // in case of change, reload
//            if (isAdded()){
//                getLoaderManager().restartLoader(0, getArguments(), ContactListFragment.this);
//            }
//        }
//    }

    private class ContactListActionListener implements ContactListCursorAdapter.ActionListener{

        @Override
        public void onItemClicked(int position) {
            SipClist item = new SipClist(recyclerAdapter.getItem(position));
            if (actionMode != null){
                toggleSelection(position, item);
            } else {
                showContactMenu(item);
            }
        }

        @Override
        public void onItemLongClicked(int position) {
            SipClist item = new SipClist(recyclerAdapter.getItem(position));
            if (actionMode == null){
                PhonexActivity activity = (PhonexActivity) getActivity();
                actionMode = activity.getToolbar().startActionMode(new ActionModeCallbacks());
                toggleSelection(position, item);
            }
        }
    }

    private class ActionModeCallbacks implements ActionMode.Callback{

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater= getActivity().getMenuInflater();
            inflater.inflate(R.menu.clist_action_mode, menu);

            actionMode = mode;
            updateTitle();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.option_broadcast_message){
                AnalyticsReporter.from(ContactListFragment.this).buttonClick(AppButtons.MAIN_ACTIVITY_BROADCAST_MESSAGE);
                Log.vf(TAG, "onActionItemClicked; option_broadcast_message clicked");
                // Run Broadcast message activity
                ArrayList<SipClist> contacts = getCheckedContacts();

                Intent it = new Intent(getActivity(), BroadcastMessageActivity.class);
                it.putExtras(BroadcastMessageActivity.getArgumentsForBroadcastMessage(contacts));
                getActivity().startActivity(it);
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (actionMode != null) {
                actionMode = null;
                // visible selection
                recyclerAdapter.clearSelections();
                // selected items (including filtered-out)
                checkedContacts.clear();
            }
        }
    }
}
