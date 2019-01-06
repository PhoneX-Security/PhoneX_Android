package net.phonex.ui.intro;

import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.util.Log;

public class FirstLaunchFragment extends Fragment {
    private static final String TAG = "FirstLaunchFragment";

    public static FirstLaunchFragment newInstance() {
        FirstLaunchFragment f = new FirstLaunchFragment();

        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_first_launch, container, false);

        // Watch for button clicks.
        Button buttonContinue = (Button)v.findViewById(R.id.button_sign_in);
        buttonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntroSetupFragment introSetupFragment = new IntroSetupFragment();
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_content, introSetupFragment, IntroActivity.INTRO_SETUP_FRAGMENT_TAG)
                        .addToBackStack(null)
                        .commit();
            }
        });

        Button buttonObtainLicense = (Button)v.findViewById(R.id.button_create_trial_license);
        buttonObtainLicense.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment newFragment = CreateAccountFragment.newInstance();
                if (getFragmentManager()!=null){
                    getFragmentManager().beginTransaction()
                            .replace(R.id.fragment_content, newFragment, IntroActivity.CREATE_TRIAL_FRAGMENT_TAG)
                            .addToBackStack(null)
                            .commit();
                }
            }
        });

        if (android.os.Build.VERSION.SDK_INT <= 16) {
            Log.vf(TAG, "setting lightRobotoTypeface");
            // Replacing "sans-serif-thin" with "sans-serif-light" on Ice Cream Sandwich
            // android:fontFamily="sans-serif-thin"      // roboto thin (android 4.2)
            Typeface lightRobotoTypeface = Typeface.create("sans-serif-light", Typeface.NORMAL);

            TextView title = (TextView) v.findViewById(R.id.title);
            TextView subtitle = (TextView) v.findViewById(R.id.subtitle);
            title.setTypeface(lightRobotoTypeface);
            subtitle.setTypeface(lightRobotoTypeface);
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //You need to add the following line for this solution to work; thanks skayred
//        getView().setFocusableInTouchMode(true);
//
//        getView().setOnKeyListener( new View.OnKeyListener(){
//            @Override
//            public boolean onKey( View v, int keyCode, KeyEvent event ){
//                if( keyCode == KeyEvent.KEYCODE_BACK ){
//                    getFragmentManager().popBackStack();
//                    return true;
//                }
//                return false;
//            }
//        } );
    }
}