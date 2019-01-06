package net.phonex.ui.chat;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;

/**
 * Simple scroll position holder for message fragment.
 *
 * Created by dusanklinec on 29.04.15.
 */
public class ScrollPosition implements Parcelable {
    private static final String TAG = "ChatScrollPosition";
    private Integer index;
    private Integer offset;
    private Integer count;

    public ScrollPosition() {
    }

    public ScrollPosition(Integer index, Integer offset) {
        this.index = index;
        this.offset = offset;
    }

    public ScrollPosition(Integer index, Integer offset, Integer count) {
        this.index = index;
        this.offset = offset;
        this.count = count;
    }

    public void copyFrom(ScrollPosition pos){
        if (pos == null){
            return;
        }

        index = pos.getIndex();
        offset = pos.getOffset();
        count = pos.getCount();
    }

    public void reset(Integer count){
        index = null;
        offset = null;
        this.count = count;
    }

    public static ScrollPosition getPositionFromTop(final AbsListView mList){
        final int index = mList.getFirstVisiblePosition();
        final View v = mList.getChildAt(0);
        if (v == null){
            return new ScrollPosition(index, null);
        }

        final int top = (v.getTop() - mList.getPaddingTop());
        return new ScrollPosition(index, top);
    }

    public static int getIndex(final AbsListView mList){
        if (mList == null){
            return -1;
        }

        return mList.getFirstVisiblePosition();
    }

    public static Integer getOffset(final AbsListView mList){
        if (mList == null){
            return null;
        }

        final View v = mList.getChildAt(0);
        if (v == null){
            return null;
        }

        return (v.getTop() - mList.getPaddingTop());
    }

    public void applyPosition(final ListView mList){
        if (index != null && offset != null){
            Log.vf(TAG, "Apply position idx: %s, offset: %s", index, offset);
            mList.setSelectionFromTop(index, offset);
        } else if (index != null){
            Log.vf(TAG, "Apply position idx: %s", index);
            mList.setSelection(index);
        } else {
            Log.vf(TAG, "Apply position, scroll to bottom");
            LayoutUtils.scrollToBottom(mList);
        }
    }

    /**
     * Returns true if this position matches to given one.
     * If looseMatch is true, only main aspect is checked, not the offset (if present).
     * @param pos
     * @param looseMatch
     * @return
     */
    public boolean matchesTo(ScrollPosition pos, boolean looseMatch){
        if (pos == null || pos.index != null && pos.getIndex() == null){
            return false;
        }

        if (index != null && offset != null && looseMatch){
            return index.equals(pos.getIndex()) && offset.equals(pos.getOffset());

        } else if (index != null){
            return index.equals(pos.getIndex());

        } else {
            return pos.getIndex() == null;

        }
    }

    /**
     * Returns true if it directly says to scroll to bottom.
     * Beware that this position may indicate to scroll to the bottom but in a different way
     * (e.g., by specifying exact bottom position).
     *
     * @return
     */
    public boolean isToBottom(){
        return index == null;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "ChatScrollPosition{" +
                "index=" + index +
                ", offset=" + offset +
                ", count=" + count +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.index);
        dest.writeValue(this.offset);
        dest.writeValue(this.count);
    }

    private ScrollPosition(Parcel in) {
        this.index = (Integer) in.readValue(Integer.class.getClassLoader());
        this.offset = (Integer) in.readValue(Integer.class.getClassLoader());
        this.count = (Integer) in.readValue(Integer.class.getClassLoader());
    }

    public static final Creator<ScrollPosition> CREATOR = new Creator<ScrollPosition>() {
        public ScrollPosition createFromParcel(Parcel source) {
            return new ScrollPosition(source);
        }

        public ScrollPosition[] newArray(int size) {
            return new ScrollPosition[size];
        }
    };
}
