package net.phonex.introslider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.viewpagerindicator.CirclePageIndicator;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.util.LayoutUtils;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class SliderActivity extends FragmentActivity {
    private static final String TAG = "SliderActivity";

    public static final int ACTIVITY_RESPONSE_CODE_QUIT = 10;
    public static final int ACTIVITY_RESPONSE_CODE_NEW_ACCOUNT = 11;
    public static final int ACTIVITY_RESPONSE_CODE_LOGIN = 12;

    // Intents emitted by fragments, processed locally
    private static final String INTENT_GET_STARTED = "net.phonex.introslider.intent_getstarted";
    private static final String INTENT_REDIRECT_TO_LOGIN = "net.phonex.introslider.intent_redirect_to_login";
    private static final String INTENT_REDIRECT_TO_NEW_ACCOUNT = "net.phonex.introslider.intent_redirect_to_new_account";

    public static final String EXTRA_ALREADY_HAS_ACCOUNT = "extra_already_has_account";

    SectionsPagerAdapter sectionsPagerAdapter;

    @InjectView(R.id.pager) ViewPager viewPager;
    @InjectView(R.id.button_left) ImageButton buttonLeft;
    @InjectView(R.id.button_right) ImageButton buttonRight;
    @InjectView(R.id.button_skip) Button buttonSkip;
    @InjectView(R.id.indicator) CirclePageIndicator indicator;


    boolean alreadyHasAccount = false;

    ActionReceiver actionReceiver = new ActionReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null){
            alreadyHasAccount = intent.getBooleanExtra(EXTRA_ALREADY_HAS_ACCOUNT, false);
        }

        setContentView(R.layout.activity_slider);
        ButterKnife.inject(this);

        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(sectionsPagerAdapter);

        indicator.setViewPager(viewPager);
        indicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position > 0) {
                    activateButtons();
                } else {
                    deactivateButtons();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        deactivateButtons();

        buttonLeft.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true));
        buttonRight.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true));
        buttonSkip.setOnClickListener(v -> {
            // requires android:noHistory="true" in manifest
            setResult(ACTIVITY_RESPONSE_CODE_QUIT);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_GET_STARTED);
        filter.addAction(INTENT_REDIRECT_TO_LOGIN);
        filter.addAction(INTENT_REDIRECT_TO_NEW_ACCOUNT);
        LocalBroadcastManager.getInstance(this).registerReceiver(actionReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(actionReceiver);
    }

    private void activateButtons() {
        buttonLeft.setVisibility(View.VISIBLE);
        buttonRight.setVisibility(View.VISIBLE);
//        buttonSkip.setVisibility(View.VISIBLE);
        indicator.setVisibility(View.VISIBLE);

        if (sectionsPagerAdapter.getCount() == 1) {
            // if there is only 1 slide, cannot go left or right
            buttonLeft.setEnabled(false);
            buttonRight.setEnabled(false);
        } else if (viewPager.getCurrentItem() == 0) {
            // if this is first page, cannot go left
            buttonLeft.setEnabled(false);
            buttonRight.setEnabled(true);
        } else if (viewPager.getCurrentItem() == sectionsPagerAdapter.getCount() - 1) {
            // if this is last page, cannot go right
            buttonRight.setEnabled(false);
            buttonRight.setVisibility(View.GONE);
            buttonLeft.setEnabled(false);
            buttonLeft.setVisibility(View.GONE);
        } else {
            // else can go left and right
            buttonLeft.setEnabled(true);
            buttonRight.setEnabled(true);
        }
    }

    private void deactivateButtons(){
        buttonLeft.setVisibility(View.GONE);
        buttonRight.setVisibility(View.GONE);
        buttonSkip.setVisibility(View.GONE);
//        indicator.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        new MaterialDialog.Builder(this)
                .content(R.string.skip_question)
                .negativeText(R.string.skip)
                .positiveText(R.string.continue_button)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        SliderActivity.this.setResult(ACTIVITY_RESPONSE_CODE_QUIT);
                        SliderActivity.this.finish();
                    }
                })
                .show();
    }

    private void switchToRight(){
        viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
    }

    private void redirectToLogin(){
        setResult(ACTIVITY_RESPONSE_CODE_LOGIN);
        finish();
    }

    private void redirectToNewAccount(){
        setResult(ACTIVITY_RESPONSE_CODE_NEW_ACCOUNT);
        finish();
    }

    /**
     * Receiver for actions emitted by Fragments
     */
    private class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null){
                Log.e(TAG, "Intent is null");
                return;
            }

            if (intent.getAction().equals(INTENT_GET_STARTED)){
                SliderActivity.this.switchToRight();
            } else if (intent.getAction().equals(INTENT_REDIRECT_TO_LOGIN)){
                SliderActivity.this.redirectToLogin();
            } else if (intent.getAction().equals(INTENT_REDIRECT_TO_NEW_ACCOUNT)){
                SliderActivity.this.redirectToNewAccount();
            }
        }
    }

    private class SectionsPagerAdapter extends FragmentStatePagerAdapter{
        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            Log.i("News", String.format("position = %d", position));
            return PlaceholderFragment.newInstance(position + 1, alreadyHasAccount);
        }

        @Override
        public int getCount() {
            return 7;
        }
    }

    public static class PlaceholderFragment extends Fragment {
        private int sectionNumber;
        private boolean hasAccount;

        private static final String ARG_SECTION_NUMBER = "section_number";
        private static final String ARG_HAS_ACCOUNT = "has_account";

        public static PlaceholderFragment newInstance(int sectionNumber, boolean hasAccount) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            args.putBoolean(ARG_HAS_ACCOUNT, hasAccount);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sectionNumber = getArguments().getInt(ARG_SECTION_NUMBER);
            hasAccount = getArguments().getBoolean(ARG_HAS_ACCOUNT);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            int layoutResId;
            int titleResId;
            int descResId;
            int imgResId;

            switch (sectionNumber){
                case 1: {
                    View view = inflater.inflate(R.layout.intro_slide_invitation, container, false);
                    Button getStartedButton = (Button) view.findViewById(R.id.get_started_button);
                    getStartedButton.setOnClickListener(view1 -> {
                        LocalBroadcastManager instance = LocalBroadcastManager.getInstance(getActivity());
                        instance.sendBroadcast(new Intent(INTENT_GET_STARTED));
                    });
                    TextView termsOfUse = (TextView) view.findViewById(R.id.text_terms_of_use);
                    LayoutUtils.setColoredText(termsOfUse,
                            getString(R.string.link_terms_of_use),
                            getString(R.string.link_terms_of_use_highlight),
                            getResources().getColor(R.color.phonex_color_accent));
                    termsOfUse.setOnClickListener(v -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PhonexSettings.getTermsOfUseLink()));
                        startActivity(browserIntent);
                    });

                    return view;
                }
                case 2:
                    layoutResId = R.layout.intro_slide_img_down;
                    titleResId = R.string.slide_calls_title;
                    descResId = R.string.slide_calls_desc;
                    imgResId = R.drawable.slide_calls;
                    break;
                case 3:
                    layoutResId = R.layout.intro_slide_img_up;
                    titleResId = R.string.slide_msg_title;
                    descResId = R.string.slide_msg_desc;
                    imgResId = R.drawable.slide_messages;
                    break;
                case 4:
                    layoutResId = R.layout.intro_slide_img_down;
                    titleResId = R.string.slide_secstorage_title;
                    descResId = R.string.slide_secstorage_desc;
                    imgResId = R.drawable.slide_securestorage;
                    break;
                case 5:
                    layoutResId = R.layout.intro_slide_img_up;
                    titleResId = R.string.slide_cl_title;
                    descResId = R.string.slide_cl_desc;
                    imgResId = R.drawable.slide_cl2;
                    break;
                case 6:
                    layoutResId = R.layout.intro_slide_img_down;
                    titleResId = R.string.slide_pin_title;
                    descResId = R.string.slide_pin_desc;
                    imgResId = R.drawable.slide_pinlock;
                    break;
                default:{
                    View view = inflater.inflate(R.layout.intro_slide_last, container, false);
                    Button newAccountButton = (Button) view.findViewById(R.id.new_account_redirect_button);
                    Button loginButton = (Button) view.findViewById(R.id.login_redirect_button);
                    TextView orTextView = (TextView) view.findViewById(R.id.or_text);

                    newAccountButton.setOnClickListener(view1 -> {
                        LocalBroadcastManager instance = LocalBroadcastManager.getInstance(getActivity());
                        instance.sendBroadcast(new Intent(INTENT_REDIRECT_TO_NEW_ACCOUNT));
                    });

                    loginButton.setOnClickListener(view1 -> {
                        LocalBroadcastManager instance = LocalBroadcastManager.getInstance(getActivity());
                        instance.sendBroadcast(new Intent(INTENT_REDIRECT_TO_LOGIN));
                    });

                    if (hasAccount){
                        // if you already have an account, do not show this button
                        newAccountButton.setVisibility(View.GONE);
                        orTextView.setVisibility(View.GONE);
                    }

                    return view;
                }
            }

            View view = inflater.inflate(layoutResId, container, false);
            ImageView imageView = (ImageView) view.findViewById(R.id.image);
            TextView titleTextView = (TextView) view.findViewById(R.id.title);
            TextView descTextView = (TextView) view.findViewById(R.id.description);

            imageView.setImageResource(imgResId);
            titleTextView.setText(titleResId);
            descTextView.setText(descResId);

            return view;
        }
    }
}