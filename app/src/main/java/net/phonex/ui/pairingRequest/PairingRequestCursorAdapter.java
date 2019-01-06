package net.phonex.ui.pairingRequest;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.PairingRequest;
import net.phonex.ui.customViews.CursorRecyclerViewAdapter;
import net.phonex.util.DateUtils;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by Matus on 25-Aug-15.
 */
public class PairingRequestCursorAdapter extends CursorRecyclerViewAdapter<PairingRequestCursorAdapter.ViewHolder> {

    public PairingRequestCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor);
    }

    public void setListener(PairingRequestResolutionListener listener) {
        this.listener = listener;
    }

    private PairingRequestResolutionListener listener;

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Cursor cursor, int position) {
        PairingRequest request = new PairingRequest(cursor);

        String dispName = request.getFromUser();
        if (dispName != null && dispName.contains("@")) {
            String[] splits = dispName.split("@");
            dispName = splits[0];
        }

        viewHolder.name.setText(dispName);
        viewHolder.time.setText(DateUtils.relativeTimeFromNow(request.getTstamp()));

        viewHolder.container.setOnClickListener(v -> {
            if (listener != null) listener.onOptions(request);
        });
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.pairing_request_row, parent, false);

        return new ViewHolder(v);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        @InjectView(R.id.pairing_request_container) View container;
        @InjectView(R.id.name) TextView name;
        @InjectView(R.id.time) TextView time;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.inject(this, itemView);
        }
    }
}
