package net.phonex.ui.customViews;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.service.MyPresenceManager;

import java.util.List;

/**
 * Adapter class for displaying content of statusSpinner in Action Bar
 * 
 * @author miroc
 *
 */
public class StatusSpinnerAdapter extends BaseAdapter implements SpinnerAdapter {

    // static list of available presence statuses
    public final List<Integer> data;

    private Context context;
    private int layoutResource;
    private int dropdownResource;
    private LayoutInflater mInflater;


    public StatusSpinnerAdapter(Context context) {
        data = MyPresenceManager.ACTIVABLE_PRESENCE_STATUSES;
        this.context = context;
        layoutResource = R.layout.actionbar_status_item;
        dropdownResource = R.layout.actionbar_status_dropdown_item;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
	
	@Override
	public int getCount() {		
		return data.size();
	}

	@Override
	public Object getItem(int position) {
		return data.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v;
        if (convertView == null) {
            v = mInflater.inflate(layoutResource, parent, false);
            
        } else {
            v = convertView;
        }
        
        ImageView iconView  = (ImageView) v.findViewById(R.id.icon);
        Integer statusValue = data.get(position);
        iconView.setImageResource(MyPresenceManager.getStatusIcon(statusValue));
        return v;
	} 
	
	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		View v;
        if (convertView == null) {
            v = mInflater.inflate(dropdownResource, parent, false);
            
        } else {
            v = convertView;
        }
        
        TextView menuItemView = (TextView) v.findViewById(R.id.list_text);
        Integer statusValue = data.get(position);

        menuItemView.setText(MyPresenceManager.getStatusText(statusValue));
        menuItemView.setCompoundDrawablesWithIntrinsicBounds(context.getResources().getDrawable(
                MyPresenceManager.getStatusIcon(statusValue)), null, null, null);
        return v;
	}
}