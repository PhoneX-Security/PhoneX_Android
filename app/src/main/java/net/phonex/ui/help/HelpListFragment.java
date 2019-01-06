package net.phonex.ui.help;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.ui.WebViewFragment;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Crazy on 23. 7. 2014.
 */
public class HelpListFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final String TAG = "HelpFragment";
    private static final int ABOUT = 0;
    private static final int FAQ   = 1;
    private static final int TERMS_OF_USE = 2;
    private static final int CHECK_UPDATE = 3;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        setRetainInstance(true);

        View v = inflater.inflate(R.layout.help, container, false);
        ListView lv = (ListView) v.findViewById(android.R.id.list);
        lv.setOnItemClickListener(this);

        ArrayList<HelpEntry> items = new ArrayList<>();

        // About
        items.add(new HelpEntry(ABOUT, R.string.about, R.drawable.ic_info));

        // FAQ
        if(!TextUtils.isEmpty(PhonexSettings.getFaqLink())) {
            items.add(new HelpEntry(FAQ, R.string.faq, R.drawable.ic_help_faq));
        }

        // Do not show any legal information at the moment, not implemented yet.
        if(!TextUtils.isEmpty(PhonexSettings.getTermsOfUseLink())) {
            items.add(new HelpEntry(TERMS_OF_USE, R.string.terms_of_use, R.drawable.ic_help_paragraph));
        }
        // Do not show update check, not implemented yet.
        //items.add(new HelpEntry(CHECK_UPDATE, R.string.check_update, R.drawable.ic_sync));

        lv.setAdapter(new HelpArrayAdapter(getActivity(), items));
        return v;
    }

    @Override
    public void onItemClick(AdapterView<?> av, View v, int position, long id) {
        HelpArrayAdapter haa = (HelpArrayAdapter) av.getAdapter();
        HelpEntry he = haa.getItem(position);

        Fragment newFragment=null;
        String txName=null;

        FragmentManager fragmentManager = getFragmentManager();
        switch (he.id) {
            case ABOUT:
                newFragment = AboutFragment.newInstance();
                txName = "about";
                break;

            case FAQ:
                newFragment = WebViewFragment.newInstance(PhonexSettings.getFaqLink());
                txName = "faq";
                break;

            case TERMS_OF_USE:
                newFragment = WebViewFragment.newInstance(PhonexSettings.getTermsOfUseLink());
                txName = "terms_of_use";
                break;

            case CHECK_UPDATE:
                break;

            default:
                return;
        }

        if (newFragment!=null) {
            Log.vf(TAG, "Replacing a new fragment %s", txName);
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_content, newFragment)
                    .addToBackStack(txName)
                    .commit();
        }
    }

    /**
     * Help entry class.
     */
    private static class HelpEntry {
        public int id;
        public int icon;
        public int label;
        public HelpEntry(int id, int label, int icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    /**
     * Array adapter for help entries.
     */
    private class HelpArrayAdapter extends ArrayAdapter<HelpEntry> {
        public HelpArrayAdapter(Context ctxt, List<HelpEntry> items) {
            super(ctxt, R.layout.help_list_row, items);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Non-optimized with ViewHolder at the moment
            if (convertView == null){
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.help_list_row, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text1);
            ImageView icon = (ImageView) convertView.findViewById(R.id.icon1);
            HelpEntry helpEntry = getItem(position);
            text.setText(helpEntry.label);
            icon.setImageResource(helpEntry.icon);
            return convertView;
        }
    }

    /**
     * About fragment - shows about.
     */
    public static class AboutFragment extends Fragment {
        private static final String TAG="AboutFragment";

        public static AboutFragment newInstance() {
            return new AboutFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.help_about, container, false);

            // Add version number
            TextView ver = (TextView) v.findViewById(R.id.phonex_text);
            TextView rev = (TextView) v.findViewById(R.id.phonex_rev);
            String appDescription = getString(R.string.intro_phonex);
            String appRevision = getString(R.string.phonex_revision);

            try {
                PackageInfo pinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                if(pinfo != null) {
                    appDescription = String.format(appDescription, pinfo.versionName);
                    appRevision = String.format(appRevision, String.valueOf(pinfo.versionCode));
                } else {
                    appDescription = String.format(appDescription, "");
                    appRevision = String.format(appDescription, "");
                }
            } catch(Exception ex){
                Log.w(TAG, "Cannot set version", ex);
                appDescription = String.format(appDescription, "");
                appRevision = String.format(appDescription, "");
            }

            ver.setText(appDescription);
            rev.setText(appRevision);
            return v;
        }
    }

}
