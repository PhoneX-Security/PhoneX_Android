package net.phonex.ui.cl;

import java.util.List;

/**
 * Created by miroc on 14.4.15.
 */
public interface SelectableRecyclerAdapter{
    boolean toggleSelection(int pos);
    void setSelection(int pos, boolean selected);
    void clearSelections();
    int getSelectedItemCount();
    List<Integer> getSelectedItems();
}
