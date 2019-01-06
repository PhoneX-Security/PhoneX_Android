package net.phonex.ui.help;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import net.phonex.R;
import net.phonex.annotations.PinUnprotected;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.lock.activity.LockActivity;
import net.phonex.util.Log;

/**
 * Created by Crazy on 23. 7. 2014.
 */
@PinUnprotected
public class HelpListActivity extends LockActionBarActivity {
    private static final String TAG = "HelpActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            boolean fragmentShown=false;
            Log.vf(TAG, "Creating help activity, saved state=%s", savedInstanceState);

            setContentView(R.layout.activity_with_fragment_and_toolbar);

            Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            setTitle(R.string.help);

            if (savedInstanceState!=null){
                try {
                    Fragment f = getFragmentManager().findFragmentById(R.id.fragment_content);
                    fragmentShown=true;
                } catch(Exception ex){
                    Log.w(TAG, "Cannot find help fragment.");
                }
            }

            // Show initial fragment.
            if (!fragmentShown) {
                HelpListFragment newFragment = new HelpListFragment();
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.add(R.id.fragment_content, newFragment);
                transaction.commit();
            }

        } catch(Exception ex){
            Log.e(TAG, "Cannot create activity", ex);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case android.R.id.home:
                FragmentManager fragmentManager = getFragmentManager();
                int backStackCount = fragmentManager.getBackStackEntryCount();
                if (backStackCount > 0){
                    Log.df(TAG, "Back pressed, backStackCount=%d", backStackCount);
                    fragmentManager.popBackStack();
                    return true;
                }

                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
