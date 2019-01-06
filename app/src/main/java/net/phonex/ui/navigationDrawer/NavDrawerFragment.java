package net.phonex.ui.navigationDrawer;

import android.app.Activity;
import android.app.Fragment;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.SipProfile;
import net.phonex.ui.PhonexActivity;
import net.phonex.util.Log;
import net.phonex.util.SimpleContentObserver;
import net.phonex.util.guava.Tuple;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * A simple {@link Fragment} subclass.
 */
public class NavDrawerFragment extends Fragment{
    private static final String TAG = "NavDrawerFragment";


    @InjectView(R.id.menu2) LinearLayout menu2;
    @InjectView(R.id.text_username) TextView textUsername;
    @InjectView(R.id.text_license) TextView textLicense;

    private ArrayList<NavDrawerItem> menuActions = new ArrayList<>();

    private SipProfile profile;
    private AccountObserver accountObserver = new AccountObserver(null);

    private Handler handler = new Handler();
    private SimpleContentObserver permissionObserver = new SimpleContentObserver(handler, () -> refreshLicenseDescription());

    private MenuClickListener menuClickListener = new MenuClickListener();

    public NavDrawerFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_navigation_drawer, container, false);
        ButterKnife.inject(this, view);

        initMenu();


        fillMenuLayout(menuActions, menu2, inflater);
        updateTextViews();

        return view;
    }

    private void initMenu(){
        menuActions.add(new NavDrawerItem(getString(R.string.manage_license), R.drawable.ic_shopping_cart_black_24px, PhonexActivity.MANAGE_LICENSE_MENU));
        menuActions.add(new NavDrawerItem(getString(R.string.my_account), R.drawable.ic_person_black_24px, PhonexActivity.ACCOUNT_MANAGER_MENU));
        menuActions.add(new NavDrawerItem(getString(R.string.filepicker_secure_storage), R.drawable.ic_lock_black_24px, PhonexActivity.FILE_MANAGER_MENU));
        menuActions.add(new NavDrawerItem(getString(R.string.invite_friend), R.drawable.ic_email_black_24px, PhonexActivity.INVITE_FRIEND_MENU));
//        menuActions.add(new NavDrawerItem(getString(R.string.camera_secure), R.drawable.ic_action_device_access_camera, PhonexActivity.CAMERA_MENU));
        menuActions.add(new NavDrawerItem(getString(R.string.settings), R.drawable.ic_settings_black_24px, PhonexActivity.SETTINGS_MENU));
        menuActions.add(new NavDrawerItem(getString(R.string.help), R.drawable.ic_help_outline_black_24px, PhonexActivity.HELP_MENU));

        // Currently, we do not want to provide user with switch off button
//        menuActions.add(new NavDrawerItem(getString(R.string.switch_off), R.drawable.ic_power_settings_new_black_24px, PhonexActivity.SWITCH_OFF_MENU));
    }

    private void fillMenuLayout(ArrayList<NavDrawerItem> menuActions, LinearLayout menuLayout, LayoutInflater inflater) {
        for (NavDrawerItem item : menuActions){
            View vi = inflater.inflate(R.layout.drawer_list_item, null);
            vi.setTag(item.getId());
            vi.setOnClickListener(menuClickListener);

            ImageView imageView = (ImageView) vi.findViewById(R.id.icon);
            TextView textView = (TextView) vi.findViewById(R.id.title);
            imageView.setImageResource(item.getIcon());
            textView.setText(item.getTitle());

            menuLayout.addView(vi);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof PhonexActivity)){
            throw new ClassCastException("NavDrawerFragment can be attached to PhonexActivity only");
        }
        activity.getContentResolver().registerContentObserver(SipProfile.ACCOUNT_ID_URI_BASE, true, accountObserver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Activity activity = getActivity();
        if (activity != null){
            activity.getContentResolver().unregisterContentObserver(accountObserver);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getContentResolver().registerContentObserver(AccountingPermission.URI, true, permissionObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getContentResolver().unregisterContentObserver(permissionObserver);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profile = SipProfile.getCurrentProfile(getActivity());
    }

    private void updateTextViews(){
        if (profile == null){
            Log.wf(TAG, "fillProfileInformation; unable to load a profile, cannot fill profile information");
        } else {
            textUsername.setText(profile.getSipUserName());
//            fillProfileInformation();
        }

        new LoadPackagesCount().execute();
    }

    private void refreshLicenseDescription() {
        new LoadPackagesCount().execute();
    }

    private class MenuClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            int menuId = (Integer) v.getTag();
            PhonexActivity activity = (PhonexActivity) getActivity();
            if (activity==null){
                return;
            }

            activity.closeNavigationDrawers();
            activity.onMenuItemSelected(menuId);
        }
    }

    private class AccountObserver extends ContentObserver {
        public AccountObserver(Handler h) {
            super(h);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.vf(TAG, "AccountObserver; onChange");
            try {
                profile = SipProfile.getCurrentProfile(getActivity());
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTextViews();
                    }
                });
            } catch (Exception e){
                Log.wf(TAG, "AccountObserver");
            }
        }
    }

    /**
     * Loads number of subscriptions and packages (as tuple)
     */
    private class LoadPackagesCount extends AsyncTask<Void, Void, Tuple<Integer, Integer>> {
        @Override
        protected Tuple<Integer, Integer> doInBackground(Void... params) {
            if (getActivity() != null){
                return AccountingPermission.getSubscriptionsAndPackagesCount(NavDrawerFragment.this.getActivity().getContentResolver());
            } else {
                Log.wf(TAG, "unable to load packages count");
                return null;
            }
        }

        @Override
        protected void onPostExecute(Tuple<Integer, Integer> count) {
            // display number of packages
            Log.vf(TAG, "count loaded = %s", count);
            if (getActivity() != null && count != null){
                Integer subscriptionCount = count.getFirst();
                Integer packagesCount = count.getSecond();
                if (subscriptionCount!=null && packagesCount != null){
                    if (subscriptionCount == 0){
//                        textLicense.setTextColor(getResources().getColor(R.color.material_red_500));
                        textLicense.setText(R.string.no_purchased_license);
                    } else {
//                        textLicense.setTextColor(LayoutUtils.getPrimaryTextColor(getActivity()));
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format(getActivity().getResources().getQuantityString(R.plurals.subscription_count_plural, subscriptionCount), subscriptionCount));
                        if (packagesCount > 0){
                            sb.append(" + ");
                            sb.append(String.format(getActivity().getResources().getQuantityString(R.plurals.packages_count_plural, packagesCount), packagesCount));
                        }
                        textLicense.setText(sb.toString());
                    }
                }
            }
        }
    }
}
