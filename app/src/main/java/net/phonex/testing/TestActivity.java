package net.phonex.testing;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

//import com.kpbird.chipsedittextlibrary.ChipsAdapter;
//import com.kpbird.chipsedittextlibrary.ChipsItem;
//import com.kpbird.chipsedittextlibrary.ChipsMultiAutoCompleteTextview;

import net.phonex.R;
import net.phonex.ui.customViews.ILRChooser;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.util.Log;

/**
 * Testing activity to test separate UI components without need to start & login into PhoneX.
 * If you want to use it, update AndroidManifest.xml and make this activity listening to launcher intent.
 *
 * Created by ph4r05 on 8/24/14.ui
 */
public class TestActivity extends AppCompatActivity{
    private static final String THIS_FILE="TestActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        setContentView(R.layout.activity_get_premium);

    }

//    protected void initMaterialChips(){
//
//
//        ChipsMultiAutoCompleteTextview ch = (ChipsMultiAutoCompleteTextview) findViewById(R.id.chipsMultiAutoCompleteTextview1);
//
//        String[] countries = new String[]{"Gusto", "Husto", "Tusto", "Cesto"}; //getResources().getStringArray(R.array.country);
////        TypedArray imgs = getResources().obtainTypedArray(R.array.flags);
//
//        ArrayList<ChipsItem> arrCountry = new ArrayList<ChipsItem>();
//
//        for (int i = 0; i < countries.length; i++) {
//
//            ChipsItem item = new ChipsItem(
//                    countries[i],
//                    R.drawable.logo_small
//            );
//
//
//            arrCountry.add(item);
//            Log.i("Main Activity", arrCountry.get(i).getTitle() + " = "
//                    + arrCountry.get(i).getImageid());
//        }
//
//        Log.i("MainActivity", "Array :" + arrCountry.size());
//
//        ChipsAdapter chipsAdapter = new ChipsAdapter(this, arrCountry);
//        ch.setAdapter(chipsAdapter);
//    }
/*
        if(savedInstanceState != null && savedInstanceState.containsKey(fragTag)){
            fragmentToAdd = getFragmentManager().getFragment(savedInstanceState, fragTag);
            Log.vf(THIS_FILE, "restore saved fragment instance; savedInstance=%s", savedInstanceState);
        } else {
            Log.v(THIS_FILE, "Going to initialize filepicker");
            fragmentToAdd = FilePickerFragment.newInstance(getApplicationContext(), new FilePickerFragment.OnFilesChosenListener() {
                @Override
                public void onFilesChosen(File selection) {
                    Toast.makeText(TestActivity.this, "File: " + selection.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        }*/

    //public abstract FragmentTransaction add (int containerViewId, Fragment fragment, String tag)
    //Add a fragment to the activity state. This fragment may optionally also have its view (if Fragment.onCreateView returns non-null) into a container view of the activity.

//    }

//    private static class ChangePasswordDialogFragment extends AlertDialogFragment{
////        private static final String EXTRA_SIP_CLIST = "extra_sip_clist";
////        private static final String EXTRA_STORAGE_PASS = "extra_storage_pass";
////        private SipClist profile;
////        private String storagePass;
//
//
////        private Button submitButton;
//        private EditText passEditText;
//        private EditText passwConfirmEditText;
//        private PasswordValidator validator;
//
//
//        public static ChangePasswordDialogFragment newInstance() {
//            ChangePasswordDialogFragment fr = new ChangePasswordDialogFragment();
////            Bundle args = new Bundle();
////            args.putParcelable(EXTRA_SIP_CLIST, clist);
////            args.putString(EXTRA_STORAGE_PASS, storagePass);
////            fr.setArguments(args);
//            return fr;
//        }
//
//        @Override
//        public void onCreate(Bundle savedInstanceState) {
//            super.onCreate(savedInstanceState);
////            profile = getArguments().getParcelable(EXTRA_SIP_CLIST);
////            storagePass = getArguments().getString(EXTRA_STORAGE_PASS);
//            validator = PasswordValidator.build(true, 8);
//        }
//
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            LayoutInflater inflater = getActivity().getLayoutInflater();
//            View titleView = inflater.inflate(R.layout.dialog_title, null);
//            View bodyView = inflater.inflate(R.layout.change_pass_dialog_fragment, null);
//
//            passEditText = (EditText) bodyView.findViewById(R.id.password);
//            passwConfirmEditText = (EditText) bodyView.findViewById(R.id.passwordConfirmation);
//
//            FrameLayout file = (FrameLayout) titleView.findViewById(R.id.body);
//            file.addView(bodyView);
//            ((TextView) titleView.findViewById(R.id.title)).setText(R.string.intro_pass_title);
//
//            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//            builder.setView(titleView);
//            builder.setOnKeyListener(this);
//
//            //set buttons
//            builder.setNeutralButton(R.string.save, null);
//            final AlertDialog ad = builder.create();
//
//            ad.setOnShowListener(new DialogInterface.OnShowListener() {
//
//                @Override
//                public void onShow(DialogInterface dialog) {
//
//                    Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
//                    b.setOnClickListener(new View.OnClickListener() {
//
//                        @Override
//                        public void onClick(View view) {
//                            String password = passEditText.getText().toString();
//                            String passwordConfirmation = passwConfirmEditText.getText().toString();
//
//                            if (TextUtils.isEmpty(password) || TextUtils.isEmpty(passwordConfirmation)){
//                                return;
//                            }
//
//                            List<PasswordValidator.Rule> validatedRules = validator.validate(password);
//                            if (validatedRules.size() > 0){
//                                passEditText.requestFocus();
//                                // Show first error
//                                passEditText.setError(getString(validatedRules.get(0).getErrorStringId()));
//                                return;
//                            }
//
//                            if(!password.equals(passwordConfirmation)) {
//                                passwConfirmEditText.requestFocus();
//                                passwConfirmEditText.setError("Confirmed password is not the same.");
//                                return;
//                            }
//
//                            //Dismiss once everything is OK.
//                            ad.dismiss();
//                        }
//                    });
//                }
//            });
//
////            builder.setNegativeButton(R.string.cancel, null);
//
//            return ad;
//        }
//    }
}
