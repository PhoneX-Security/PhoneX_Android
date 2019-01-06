package net.phonex.ui.fileManager;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.sendFile.FilePickerFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Matus on 7/8/2015.
 */
public class FileActionDialogFragment extends AlertDialogFragment {

    public interface FileActionListener {
        public void onDialogPositiveClick(List<FileStorageUri> files,
                                          FilePickerFragment.Action action,
                                          boolean showProgressDialog,
                                          Long msgId);
        public void onDialogNegativeClick(List<FileStorageUri> files, FilePickerFragment.Action action);
    }

    public static FileActionDialogFragment newInstance(ArrayList<FileStorageUri> fileUris,
                                                       FilePickerFragment.Action action,
                                                       FileActionListener listener,
                                                       boolean showProgressDialog) {
        return newInstance(fileUris, action, listener, showProgressDialog, null);
    }

    public static FileActionDialogFragment newInstance(ArrayList<FileStorageUri> fileUris,
                                                       FilePickerFragment.Action action,
                                                       FileActionListener listener,
                                                       boolean showProgressDialog,
                                                       Long msgId) {
        FileActionDialogFragment fr = new FileActionDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(EXTRA_FILE_INFOS, fileUris);
        fr.setArguments(args);
        fr.listener = listener;
        fr.action = action;
        fr.showProgressDialog = showProgressDialog;
        fr.msgId = msgId;
        return fr;
    }

    public static final String EXTRA_FILE_INFOS = "files";

    private ArrayList<FileStorageUri> files;

    private FileActionListener listener;

    private FilePickerFragment.Action action;

    private boolean showProgressDialog;

    private Long msgId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        files = getArguments().getParcelableArrayList(EXTRA_FILE_INFOS);
        setRetainInstance(true);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title;
        String content;
        String positive;

        if (FilePickerFragment.Action.DELETE == action) {
            title = getResources().getQuantityString(R.plurals.file_dialog_delete_title, files.size());
            content = getResources().getQuantityString(R.plurals.file_dialog_delete_confirm, files.size());
            positive = getString(R.string.notif_delete);
        } else if (FilePickerFragment.Action.DECRYPT == action) {
            title = getResources().getQuantityString(R.plurals.file_dialog_decrypt_title, files.size());
            content = getResources().getQuantityString(R.plurals.file_dialog_decrypt_confirm, files.size());
            positive = getString(R.string.filepicker_decrypt);
        } else if (FilePickerFragment.Action.DECRYPT_AND_OPEN == action) {
            title = getString(R.string.file_dialog_open_title);
            content = getString(R.string.file_dialog_open_confirm);
            positive = getString(R.string.filepicker_decrypt_open);
        } else {
            throw new RuntimeException("Unknown Action type");
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View bodyView = inflater.inflate(R.layout.dialog_file_list, null, false);
        ListView listView = (ListView) bodyView.findViewById(R.id.dialog_files_list);

        ((TextView) bodyView.findViewById(R.id.dialog_files_text)).setText(content);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.dialog_file_list_item);
        for (FileStorageUri info : files) {
            adapter.add(info.getFilename());
        }
        listView.setAdapter(adapter);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(title)
                .customView(bodyView, false)
                .positiveText(positive)
                .negativeText(R.string.cancel)
                .autoDismiss(true);

        builder.callback(new MaterialDialog.ButtonCallback() {
            @Override
            public void onNegative(MaterialDialog dialog) {
                listener.onDialogNegativeClick(files, action);
            }

            @Override
            public void onPositive(final MaterialDialog dialog) {
                listener.onDialogPositiveClick(files, action, showProgressDialog, msgId);
            }
        });

        return builder.build();
    }

    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }
}
