package net.phonex.ui.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import net.phonex.PhonexSettings;
import net.phonex.R;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class PhonexPreferences extends LockActionBarActivity {
	private static final String TAG = "PhonexPreferences";
	private PreferenceEntryAdapter adapter;
	private PreferencesManager prefsWrapper;
    private Locale lastKnownLocale;

    @InjectView(R.id.list) ListView listView;
    @InjectView(R.id.my_toolbar) Toolbar toolbar;

	private Intent getIntentForType(int t) {
	    Intent it = new Intent(this, PreferencesContainer.class);
	    it.putExtra(PreferenceManager.EXTRA_PREFERENCE_GROUP, t);
	    return it;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lastKnownLocale = PhonexSettings.loadDefaultLanguage(this);

        setContentView(R.layout.activity_settings);

        ButterKnife.inject(this);

		prefsWrapper = new PreferencesManager(this);
		adapter = new PreferenceEntryAdapter(this, getPreferenceEntries());

        toolbar.setTitle(getTitle());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		        PreferenceEntry pref_gp = adapter.getItem(position);
		        startActivity(pref_gp.intent);
            }
        });
	}

    @Override
    protected void onResume() {
        super.onResume();
        Locale currentLocale = PhonexSettings.loadDefaultLanguage(this);

        // If the locale changed, re-init this activity so it takes effect also here.
        // Null check is essential so as not to stuck in reset loop.
        if (lastKnownLocale!=null && lastKnownLocale.equals(currentLocale)==false){
            Log.vf(TAG, "LastLocale=%s, current locale=%s", lastKnownLocale, currentLocale);

            lastKnownLocale = currentLocale;
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    private List<PreferenceEntry> getPreferenceEntries(){
        List<PreferenceEntry> prefs_list = new ArrayList<PreferenceEntry>();
        if (prefsWrapper.isDeveloperActive()){
            prefs_list.add(new PreferenceEntry(R.string.prefs_connectivity, -1,
                    R.drawable.ic_prefs_network, getIntentForType(PreferenceManager.GROUP_CONNECTIVITY)));
        }
        prefs_list.add(new PreferenceEntry(R.string.prefs_media, -1,
                R.drawable.ic_prefs_media, getIntentForType(PreferenceManager.GROUP_MEDIA)));
        prefs_list.add(new PreferenceEntry(R.string.prefs_ui, -1,
                R.drawable.ic_prefs_ui, getIntentForType(PreferenceManager.GROUP_UI)));
        prefs_list.add(new PreferenceEntry(R.string.security, -1,
                R.drawable.ic_prefs_security, getIntentForType(PreferenceManager.GROUP_SECURITY)));
        return prefs_list;
    }

    // re-add all preference entries (some might about to be hidden/shown)
    private void updateAdapterData(){
        adapter.clear();
        adapter.addAll(getPreferenceEntries());
        adapter.notifyDataSetChanged();
    }

    /**
     * Represents single preference entry in the preferences.
     */
	class PreferenceEntry {
		public String title;
		public int icon;
		public String summary;
		public Intent intent;

		public PreferenceEntry(int title_res, int summary_res, int icon, Intent intent) {
			this.title = getString(title_res);
			this.summary = summary_res == -1 ? null : getString(summary_res);
			this.icon = icon;
			this.intent = intent;
		}
	}

    /**
     * Array adapter for preferences entries in the preference menu.
     */
	class PreferenceEntryAdapter extends ArrayAdapter<PreferenceEntry>{
		public PreferenceEntryAdapter(Context context, List<PreferenceEntry> objects) {
			super(context, R.layout.preferences_list, objects);
		}
		
	    public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.preferences_list, parent, false);
            }
            
            PreferenceEntry pref_gp = adapter.getItem(position);
            ImageView icon_view = (ImageView)v.findViewById(R.id.icon);
            TextView title_view = (TextView)v.findViewById(android.R.id.title);
//            TextView summary_view = (TextView)v.findViewById(android.R.id.summary);

            icon_view.setImageResource(pref_gp.icon);
            title_view.setText(pref_gp.title);

            // If summary is null, hide it.
//            if (pref_gp.summary==null){
//                summary_view.setVisibility(View.GONE);
//            } else {
//                summary_view.setVisibility(View.VISIBLE);
//                summary_view.setText(pref_gp.summary);
//            }
            
            return v;
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preferences, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        PreferenceManager.onMainPrefsPrepareOptionMenu(menu, this, prefsWrapper);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.developer) {
            prefsWrapper.toggleDeveloperMode();
            updateAdapterData();

            return true;
        } else if (id == R.id.reset_settings) {
            prefsWrapper.resetToDefaults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
