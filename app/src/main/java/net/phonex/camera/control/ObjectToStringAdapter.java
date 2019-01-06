package net.phonex.camera.control;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Camera;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import net.phonex.PhonexSettings;
import net.phonex.R;

import java.util.List;
import java.util.Locale;

/**
 * Created by Matus on 21-Jul-15.
 */
public class ObjectToStringAdapter<T> extends BaseAdapter {


    private Context mContext;
    private List<T> mList;
    private int mLayoutId;

    @Override
    public int getCount() {
        if (mList != null) {
            return mList.size();
        }
        return 0;
    }

    public void addItems(List<T> items) {
        mList.addAll(items);
        notifyDataSetChanged();
    }

    public void addItem(T type) {
        mList.add(type);
        notifyDataSetChanged();
    }

    @Override
    public T getItem(int position) {
        return position >= 0 && position < mList.size() ? mList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public Context getContext() {
        return mContext;
    }

    public Resources getResources() {
        return getContext().getResources();
    }

    public List<T> getList() {
        return mList;
    }

    public void setList(List<T> list) {
        this.mList = list;
    }

    public void removeItem(int position) {
        mList.remove(position);
        notifyDataSetChanged();
    }

    public View getLayout() {
        return getInflater().inflate(getLayoutId(), null);
    }

    public int getLayoutId() {
        return mLayoutId;
    }

    public LayoutInflater getInflater() {
        return LayoutInflater.from(mContext);
    }

    public void clear() {
        mList.clear();
        notifyDataSetChanged();
    }

    public ObjectToStringAdapter(Context context, List<T> list) {
        mContext = context;
        setList(list);
        mLayoutId = R.layout.camera_object_to_string_list_item;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createView(position, convertView, true);
    }

    private String resolutionToMegapixels(Locale locale, int width, int height) {
        double megapixels = (width * height) / 1000000.0;
        return String.format(locale, "%.1f Mpx", megapixels);
    }

    private View createView(int position, View convertView, boolean dropDown) {
        ViewHolder holder;

        if (convertView == null) {
            if (dropDown) {
                convertView = getDropDownLayout();
            } else {
                convertView = getLayout();
            }
            holder = new ViewHolder();
            holder.text = (TextView) convertView.findViewById(R.id.title);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Locale locale = PhonexSettings.loadDefaultLanguage(getContext());

        T item = getItem(position);
        if (item != null) {
            if (item instanceof Camera.Size) {
                int width = ((Camera.Size) item).width;
                int height = ((Camera.Size) item).height;
                holder.text.setText(String.format("%s (%d x %d)", resolutionToMegapixels(locale, width, height), width, height));
            } else {
                holder.text.setText(item.toString());
            }
        }

        return convertView;
    }

    public View getDropDownLayout() {
        return getInflater().inflate(R.layout.camera_object_to_string_dropdown_list_item, null);
    }

    class ViewHolder {

        TextView text;

    }

}
