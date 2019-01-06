package net.phonex.ui.pairingRequest;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListAdapter;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListItem;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.core.SipUri;
import net.phonex.db.entity.PairingRequest;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.soap.entities.PairingRequestResolutionEnum;
import net.phonex.ui.addContact.AddContactActivity;
import net.phonex.ui.addContact.AddContactFragment;
import net.phonex.ui.customViews.EmptyRecyclerView;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.android.StatusbarNotifications;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by Matus on 25-Aug-15.
 */
public class PairingRequestsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, PairingRequestResolutionListener {
    private static final String TAG = "AddContactFragment";
    public static final String FRAGMENT_TAG = "AddContactFragment";

    private PairingRequestCursorAdapter recyclerAdapter;

    public static PairingRequestsFragment newInstance() {
        PairingRequestsFragment fragment = new PairingRequestsFragment();
        return fragment;
    }

    @InjectView(R.id.recycler_view) EmptyRecyclerView recyclerView;
    @InjectView(R.id.empty_view) View emptyView;

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, "onCreateLoader");

        // Default sort order: case insensitive on display name.
        String sortOrder = PairingRequest.FIELD_TSTAMP + " DESC";

        String selection = String.format("%s=?", PairingRequest.FIELD_RESOLUTION);
        String[] selectionArgs = new String[]{PairingRequestResolutionEnum.NONE.value()};

        return new CursorLoader(getActivity(), PairingRequest.URI, PairingRequest.FULL_PROJECTION, selection, selectionArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d(TAG, "onLoadFinished");
        changeCursor(loader, data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG, "onLoaderReset");
        changeCursor(loader, null);
    }

    private void changeCursor(Loader<Cursor> loader, Cursor c) {
        if (c == null || c.isClosed()) {
            Log.wf(TAG, "changeCursor; cursor is null or closed");
            return;
        }

        try {
            recyclerAdapter.swapCursor(c);
        } catch (Exception e) {
            Log.w(TAG, "ChangeCursor exception", e);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pairing_request_list, container, false);
        ButterKnife.inject(this, view);
        recyclerView.setEmptyView(emptyView);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(recyclerLayoutManager);

        recyclerAdapter = new PairingRequestCursorAdapter(getActivity(), null);
        recyclerAdapter.setListener(this);

        recyclerView.setAdapter(recyclerAdapter);

        // mark as 'seen' and cancel possible notification in a separate thread to avoid holding of the fragment start
        new Thread(() -> {
            try {
                markAsSeen();
                new StatusbarNotifications(getActivity()).cancelPairingRequestNotif();
            } catch (Exception e){
                Log.ef(TAG, e, "onViewCreated error");
            }
        }).start();

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(0, getArguments(), PairingRequestsFragment.this);
    }

    private void markAsSeen(){
        ContentValues cv = new ContentValues();
        cv.put(PairingRequest.FIELD_SEEN, true);
        PairingRequest.update(getActivity().getContentResolver(), cv);
    }

    @Override
    public void onOptions(PairingRequest pairingRequest) {
        if (pairingRequest == null) {
            Log.e(TAG, "pairingRequest == null");
            return;
        }


        MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter(getActivity());
        adapter.add(new MaterialSimpleListItem.Builder(getActivity())
                        .content(R.string.pairing_request_accept)
                        .icon(R.drawable.ic_person_add_black_24px)
                        .build()
        );
        adapter.add(new MaterialSimpleListItem.Builder(getActivity())
                        .content(R.string.pairing_request_reject)
                        .icon(R.drawable.ic_clear_black_24px)
                        .build()
        );

        String usernameOnly = SipUri.parseSipContact(pairingRequest.getFromUser()).userName;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.title(getString(R.string.pairing_request_prompt, usernameOnly))
                .adapter(adapter, (dialog, itemView, which, text) -> {
                    dialog.dismiss();
                    switch (which) {
                        case 0:
                            addContact(pairingRequest.getFromUser());
                            break;
                        case 1:
                            updatePairingRequest(pairingRequest, PairingRequestResolutionEnum.DENIED);
                            break;
                        // Disabled at the moment
//                        case 2:
//                            updatePairingRequest(pairingRequest, PairingRequestResolutionEnum.BLOCKED);
//                            break;
                        default:
                            break;
                    }
                });
        builder.build().show();
    }

    private boolean addContact(String userSip) {
        Intent it = new Intent(getActivity(), AddContactActivity.class);
        Bundle b = new Bundle();
        b.putString(AddContactFragment.EXTRA_SIP, userSip);
        it.putExtras(b);
        startActivityForResult(it, 1);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode==AddContactFragment.RESULT_CODE_OK){
            closeActivityIfAllResolved();
        }
    }

    private void updatePairingRequest(PairingRequest pairingRequest, PairingRequestResolutionEnum resolution) {
        if (pairingRequest == null) {
            Log.e(TAG, "null pairing request");
            return;
        }
        if (getActivity() == null) {
            Log.e(TAG, "Context is null");
            return;
        }

        // Show dialog
        GenericProgressDialogFragment dialogFragment = GenericProgressDialogFragment.newInstance(new GenericProgressDialogFragment.EventListener() {
            @Override
            public void onComplete() {
                closeActivityIfAllResolved();
                // nothing
            }

            @Override
            public void onError(GenericTaskProgress progress) {
                Log.ef(TAG, "onError; error=%s, message=%s", progress.getError(), progress.getMessage());
                if (getActivity()!=null && getFragmentManager() != null){
                    AlertDialogFragment.newInstance(getString(R.string.p_problem), getString(R.string.p_problem_nonspecific)).show(getFragmentManager(), "alert");
                }
            }
        }, Intents.ACTION_PAIRING_REQUEST_UPDATE_PROGRESS);
        dialogFragment.show(getFragmentManager(), "tag");

        // Run task via intent
        Intent intent = new Intent(Intents.ACTION_TRIGGER_PAIRING_REQUEST_UPDATE);
        intent.putExtra(Intents.EXTRA_PAIRING_REQUEST_ID, pairingRequest.getServerId());
        intent.putExtra(Intents.EXTRA_PAIRING_REQUEST_RESOLUTION, resolution);
        MiscUtils.sendBroadcast(getActivity(), intent);
    }

    private boolean closeActivityIfAllResolved(){
        if (getActivity() != null){
            if (PairingRequest.getNonResolvedCount(getActivity().getContentResolver()) == 0){
                // close activity if all requests are resolved
                getActivity().finish();
                return true;
            }
        }
        return false;
    }
}
