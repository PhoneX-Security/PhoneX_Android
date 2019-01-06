package net.phonex.ui.cl;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.core.SipUri;
import net.phonex.service.MyPresenceManager;
import net.phonex.ui.chat.MessageActivity;
import net.phonex.ui.chat.MessageFragment;
import net.phonex.ui.customViews.CheckableRelativeLayout;
import net.phonex.ui.customViews.CursorRecyclerViewAdapter;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * New and better adapter built for RecyclerView list
 * Created by miroc on 14.4.15.
 */
public class ContactListCursorAdapter extends CursorRecyclerViewAdapter<ContactListCursorAdapter.ViewHolder>
        implements
        View.OnClickListener,
        SelectableRecyclerAdapter {
    private static final String TAG = "ContactListCursorAdapter";

    private static final int TAG_CLIST = R.id.tag_clist;
    private static final int TAG_POSITION = R.id.tag_clist;

    private boolean showUserNames;
    private boolean showNewMessageNotification = true;
    private boolean isValidForCall = true; // cached value

    private ContactListFragment contactListFragment;
    private ActionListener actionListener;

    private SparseBooleanArray selectedItems = new SparseBooleanArray();

    public interface ActionListener{
        void onItemClicked(int position);
        void onItemLongClicked(int position);
    }

    public ContactListCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor);
    }



    // Create new views (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor, int position) {
        SipClist clist = new SipClist(cursor);
//        holder.contactContainer.setTag(TAG_CLIST, clist);
        holder.contactContainer.setTag(position);
        holder.contactContainer.setChecked(selectedItems.get(position));

        if (showNewMessageNotification){
            holder.newMessageButton.setTag(TAG_CLIST, clist);
            holder.newMessageButton.setVisibility(clist.getUnreadMessages()>0 ? View.VISIBLE : View.GONE);
        }

        final Integer presenceStatus = clist.getPresenceStatusType();
        holder.contactStatus.setImageResource(isValidForCall ? MyPresenceManager.getStatusIcon(presenceStatus) : MyPresenceManager.getOfflineStatusIcon());

        holder.name.setText(clist.getDisplayName());
        CharSequence formattedNumber = SipUri.parseSipContact(clist.getSip()).userName;
        holder.number.setText(formattedNumber);
        holder.number.setVisibility(showUserNames ? View.VISIBLE : View.GONE);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.clist_row, parent, false);

        ViewHolder holder = new ViewHolder(v);
        holder.newMessageButton.setOnClickListener(this);
        holder.contactContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (contactListFragment != null && contactListFragment.isAdded()){
                    actionListener.onItemClicked((Integer) v.getTag());
                }
            }
        });
        holder.contactContainer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (actionListener != null){
                    actionListener.onItemLongClicked((Integer) v.getTag());
                    return true;
                }
                return false;
            }
        });
        return holder;
    }

    @Override
    public void onClick(View v) {
        Log.vf(TAG, "onClick");
        if (v.getId() == R.id.new_message_button){
            SipClist clist = (SipClist) v.getTag(TAG_CLIST);

            String sipCanonical = SipUri.getCanonicalSipContact(clist.getSip(), true);
            Bundle b = MessageFragment.getArguments(sipCanonical);

            if (contactListFragment != null && contactListFragment.isAdded()){
                Intent it = new Intent(contactListFragment.getActivity(), MessageActivity.class);
                it.putExtras(b);
                contactListFragment.startActivity(it);
            }
        }
    }

    @Override
    public boolean toggleSelection(int pos) {
        boolean isSelected = false;
        if (selectedItems.get(pos, false)) {
            selectedItems.delete(pos);
        }
        else {
            selectedItems.put(pos, true);
            isSelected = true;
        }
        // This is necessary because I do not have access to the View object itself and
        // thus cannot set the activated state directly.
        // Instead I have to tell Android to ask the Adapter for a new ViewHolder binding.
        notifyItemChanged(pos);
        return isSelected;
    }

    @Override
    public void setSelection(int pos, boolean selected) {
        if (selected){
            selectedItems.put(pos, true);
        } else {
            selectedItems.delete(pos);
        }
        notifyItemChanged(pos);
    }

    @Override
    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getSelectedItemCount() {
        return selectedItems.size();
    }

    @Override
    public List<Integer> getSelectedItems() {
        List<Integer> items =
                new ArrayList<Integer>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }
        return items;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        @InjectView(R.id.contact_container) CheckableRelativeLayout contactContainer;
        @InjectView(R.id.contact_status) ImageView contactStatus;
        @InjectView(R.id.name) TextView name;
        @InjectView(R.id.number) TextView number;
        @InjectView(R.id.new_message_button) ImageButton newMessageButton;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.inject(this, itemView);
        }
    }

    /* Getters + setters */
    public void setShowUserNames(boolean showUserNames) {
        Log.vf(TAG, "setShowUserNames; %s", showUserNames);
        this.showUserNames = showUserNames;
    }

    public void setShowNewMessageNotification(boolean showNewMessageNotification) {
        this.showNewMessageNotification = showNewMessageNotification;
    }

    public void setIsValidForCall(boolean isValidForCall) {
        Log.vf(TAG, "setIsValidForCall; %s", isValidForCall);
        this.isValidForCall = isValidForCall;
    }

    public void setContactListFragment(ContactListFragment contactListFragment) {
        this.contactListFragment = contactListFragment;
    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public boolean isValidForCall() {
        return isValidForCall;
    }
}
