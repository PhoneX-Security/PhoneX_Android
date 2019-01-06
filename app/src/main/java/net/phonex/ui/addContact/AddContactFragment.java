package net.phonex.ui.addContact;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.pub.parcels.GenericTaskProgress;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.GenericProgressDialogFragment;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Main fragment for adding contacts
 * @author miroc
 */
public class AddContactFragment extends Fragment implements TextWatcher, CompoundButton.OnCheckedChangeListener {
    private static final String THIS_FILE = "AddContactFragment";
    public static final String EXTRA_SIP = "sip";

    public static final int RESULT_CODE_OK = 1;

    @InjectView(R.id.sip) EditText sipText;
    @InjectView(R.id.alias) EditText aliasText;
    @InjectView(R.id.chooseAlias) CheckBox chooseAlias;
    @InjectView(R.id.button) Button button;

    /**
     * Instantiate fragment (sip can be added as a parameter)
     * @return
     */
    public static AddContactFragment newInstance(Bundle args) {
        AddContactFragment fragment = new AddContactFragment();
        if (args != null)
            fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_contact, container, false);
        ButterKnife.inject(this, view);

        button.setOnClickListener(this::onAddClick);
        sipText.addTextChangedListener(this);
        chooseAlias.setOnCheckedChangeListener(this);

        if (getArguments() != null){
            String defaultSip = getArguments().getString(EXTRA_SIP);
            if (defaultSip != null){
                sipText.setText(defaultSip);
                sipText.setEnabled(false);
            }
        }

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void onAddClick(View view){
        AnalyticsReporter.from(this).buttonClick(AppButtons.ADD_CONTACT_ADD);
        String sip = this.sipText.getText().toString();
        String alias = this.aliasText.getText().toString();

        if (TextUtils.isEmpty(sip)){
            return;
        }

        Log.wf(THIS_FILE, "together aliasText [%s] chooseAliasChecked [%s]", alias, String.valueOf(chooseAlias));
        if(chooseAlias.isChecked() && TextUtils.isEmpty(aliasText.getText())) {
            aliasText.requestFocus();
            aliasText.setError(getString(R.string.add_contact_empty_alias));
            return;
        }

        Log.i(THIS_FILE, "ADDING: " + sip);

        if (!chooseAlias.isChecked() || alias.length()==0){
            alias = extractDisplayedName(sip);
        }

        addContact(sip, alias);
    }

    private void addContact(String sip, String alias) {
        if (getActivity() == null) {
            Log.e(THIS_FILE, "Context is null");
            return;
        }

        // Show dialog + detect progress
        GenericProgressDialogFragment dialogFragment = GenericProgressDialogFragment.newInstance(new GenericProgressDialogFragment.EventListener() {
            @Override
            public void onComplete() {
                String text = String.format(getString(R.string.p_add_success), alias);
                Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                if (getActivity() != null){
                    getActivity().setResult(RESULT_CODE_OK);
                    getActivity().finish();
                }

            }

            @Override
            public void onError(GenericTaskProgress progress) {
                AlertDialogFragment.newInstance(getString(R.string.p_problem), progress.getMessage()).show(getFragmentManager(), "alert");
            }

        }, Intents.ACTION_ADD_CONTACT_PROGRESS);
        dialogFragment.show(getFragmentManager(), "tag");

        // Run task via intent
        Intent intent = new Intent(Intents.ACTION_TRIGGER_ADD_CONTACT);
        intent.putExtra(Intents.EXTRA_ADD_CONTACT_SIP, sip);
        intent.putExtra(Intents.EXTRA_ADD_CONTACT_ALIAS, alias);
        MiscUtils.sendBroadcast(getActivity(), intent);
    }

    private String extractDisplayedName(String userName) {
        if (userName == null) return null;
        if (userName.contains("@")) {
            String[] splits = userName.split("@");
            if (splits.length > 0) return splits[0];
        }
        return userName;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        sipText.setError(null);
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.chooseAlias) {
            aliasText.setError(null);
            if (isChecked) {
                AnalyticsReporter.from(this).buttonClick(AppButtons.ADD_CONTACT_CHOOSE_ALIAS);
                aliasText.setVisibility(View.VISIBLE);
            } else {
                aliasText.setVisibility(View.GONE);
            }
        }
    }
}
