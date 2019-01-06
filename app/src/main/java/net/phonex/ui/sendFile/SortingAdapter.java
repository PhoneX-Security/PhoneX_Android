package net.phonex.ui.sendFile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.util.Log;

import java.util.List;

/**
 * Adapter class for displaying content of statusSpinner in Action Bar
 * 
 * @author miroc
 *
 */
public class SortingAdapter extends BaseAdapter implements SpinnerAdapter {

    private static final String TAG = "SortingAdapter";
    // static list of available presence statuses
    public final List<SortingType> data;

    private Context context;
    private int layoutResource;
    private int dropdownResource;
    private LayoutInflater mInflater;


    public SortingAdapter(Context context) {
        data = SortingType.getList();
        this.context = context;
        layoutResource = R.layout.filepicker_sorting_item;
        dropdownResource = R.layout.filepicker_sorting_dropdown_item;
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

        TextView text = (TextView) v.findViewById(R.id.text1);
        SortingType sortingType = data.get(position);
        Log.vf(TAG, "getView; position [%d], sortingType [%s], textView [%s]", position, sortingType, text);
        text.setText(context.getString(sortingType.getText()));
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

        TextView text =(TextView) v.findViewById(R.id.text1);
        SortingType sortingType = data.get(position);
        text.setText(context.getString(sortingType.getText()));
        return v;
	}
}