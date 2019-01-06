package net.phonex.ui.sendFile;

import java.util.List;

/**
 * Created by eldred on 20/02/15.
 */
public interface FilePickListener {

    public void fileSelected (final FileItemInfo file, final int position);
    public void fileDeselected (final FileItemInfo file, final int position);

    public void notifySelectError();
    public void clearSelection();

    public void fillIn(final List<FileItemInfo> selectedFiles);
}
