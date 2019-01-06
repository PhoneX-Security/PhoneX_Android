package net.phonex.ui.conversations;

import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import net.phonex.R;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.Thumbnail;
import net.phonex.service.messaging.MessageManager;
import net.phonex.ui.chat.MessageActivity;
import net.phonex.ui.chat.MessageFragment;
import net.phonex.ui.conversations.ConversationsListAdapter.ViewHolder;
import net.phonex.ui.customViews.CursorListFragment;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationsListFragment extends CursorListFragment {
    public static final int MENU_DELETE = 1;
    public static final int MENU_VIEW = 0;
    private static final String TAG = "ChatListListFragment";
    private ConversationsListAdapter adapter;
    private SipProfile userProfile = null;

    public static ConversationsListFragment newInstance() {
        return new ConversationsListFragment();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);


        adapter = new ConversationsListAdapter(getActivity(), null);
        setListAdapter(adapter);


        ListView lv = getListView();
        registerForContextMenu(lv);

        getLoaderManager().initLoader(0, null, this);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View v = inflater.inflate(R.layout.fragment_conversations, container, false);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Modify list view
        ListView lv = getListView();
        lv.setVerticalFadingEdgeEnabled(true);
            lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
            lv.setItemsCanFocus(true);
//        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // DB re-check
        MessageManager.triggerCheck(getActivity());
    }

    public void viewDetails(int position, ViewHolder cri) {
        String number = null;
        String fromFull = null;
        if (cri != null) {
            number = cri.getRemoteNumber();
        }
        viewDetails(position, number);
    }

    public void viewDetails(int position, String number) {
        Bundle b = MessageFragment.getArguments(number);
        Intent it = new Intent(getActivity(), MessageActivity.class);
        it.putExtras(b);
        startActivity(it);
    }

    // Options
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//        SipProfile p = getCurrentUserProfile();
//        if (p == null){
            // fix for PHON-483
//            return null;
//        }
//        String userSip = SipUri.getCanonicalSipContact(p.getUriString(), false);
//        String selection = SipMessage.FIELD_FROM + "== '" + userSip + "' OR " + SipMessage.FIELD_TO + "== '" + userSip + "' ";
//        return new CursorLoader(getActivity(), SipMessage.THREAD_URI, null, selection, null, null);
        return new CursorLoader(getActivity(), SipMessage.THREAD_URI, null, null, null, null);
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//        AdapterView.AdapterContextMenuInfo info =
//                (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.add(0, MENU_VIEW, 0, R.string.menu_view);
        menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        final Object tag = info.targetView.getTag();
        if (tag == null || !(tag instanceof ViewHolder)){
            return super.onContextItemSelected(item);
        }

        ViewHolder cri = (ViewHolder) tag;
        switch (item.getItemId()) {
            case MENU_DELETE: {
                confirmDeleteThread(cri.getRemoteNumber());
                break;
            }
            case MENU_VIEW: {
                viewDetails(info.position, cri);
                break;
            }
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        AnalyticsReporter.from(this).buttonClick(AppButtons.MAIN_ACTIVITY_OPEN_CONVERSATION);
        ViewHolder cri = (ViewHolder) v.getTag();
        viewDetails(position, cri);
    }

    private void confirmDeleteThread(final String from) {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
        builder.setTitle(R.string.confirm_dialog_title)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (TextUtils.isEmpty(from)) {
                        getActivity().getContentResolver().delete(SipMessage.MESSAGE_URI, null, null);
                    } else {
                        int deleted = Thumbnail.deleteBySender(getActivity().getContentResolver(), from);
                        Log.df(TAG, "Deleted %s thumbnails while deleting a thread of messages", deleted);
                        Builder threadUriBuilder = SipMessage.THREAD_ID_URI_BASE.buildUpon();
                        threadUriBuilder.appendEncodedPath(from);
                        getActivity().getContentResolver().delete(threadUriBuilder.build(), null, null);
                    }
                })
                .setNegativeButton(R.string.no, null)
                .setMessage(TextUtils.isEmpty(from)
                        ? R.string.confirm_delete_all_conversations
                        : R.string.confirm_delete_conversation)
                .show();
    }

    @Override
    public void changeCursor(Loader<Cursor> loader, Cursor c) {
        if (c == null || c.isClosed()){
            Log.wf(TAG, "changeCursor; cursor is null or closed");
            return;
        }

        adapter.swapCursor(c);
    }


}
