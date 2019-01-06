package net.phonex.ui.chat;

import android.app.FragmentManager;
import android.os.Bundle;
import android.view.MenuItem;

import net.phonex.PhonexSettings;
import net.phonex.pub.a.Compatibility;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.ui.sendFile.FilePickerFragment;
import net.phonex.util.Log;

public class MessageActivity extends LockActionBarActivity {
    private static final String TAG = "MessageActivity";

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // load language set in preferences
        PhonexSettings.loadDefaultLanguage(this);
        
        if (savedInstanceState == null) {
            // During initial setup, plug in the details fragment.
            MessageFragment detailFragment = new MessageFragment();
            detailFragment.setArguments(getIntent().getExtras());

            getFragmentManager().beginTransaction().add(android.R.id.content, detailFragment, MessageFragment.THIS_FRAGMENT_TAG).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * If FilePickerFragment is displayed and device is rotated,
         * FilePickerFragment loses listener and signals action with target fragment (MessageFragment).
         * The MessageFragment's getActivity() returns null in such situation.
         * The activity is needed for showing dialogs and for content resolver when taking action
         * other than file sending. Therefore it will be set manually.
         * FIXME Check if this is OK.
         * Proper solution would be for this activity to implement OnFilesChosenListener,
         * however for that we need to connect to service, which is done in MessageFragment now.
         */
        MessageFragment messageFragment =
                (MessageFragment) getFragmentManager().findFragmentByTag(MessageFragment.THIS_FRAGMENT_TAG);
        if (messageFragment != null) {
            messageFragment.setActivity(this);
            Log.d(TAG, "Set activity to a MessageFragment");
        } else {
            Log.d(TAG, "MessageFragment is not associated with this FragmentManager");
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            int backStackEntryCount = fm.getBackStackEntryCount();
            if (backStackEntryCount > 0) {
                // if there is any fragment in backstack, remove it (for FilePicker fragment)
                FragmentManager.BackStackEntry entry = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
                if (entry != null && FilePickerFragment.THIS_FRAGMENT_TAG.equals(entry.getName())) {
                    FilePickerFragment fragment = (FilePickerFragment) fm.findFragmentByTag(FilePickerFragment.THIS_FRAGMENT_TAG);
                    if (fragment != null) {
                        if (fragment.goUp()) {
                            return;
                        }
                    }
                }
                fm.popBackStack();
                return;
            }
        }
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == Compatibility.getHomeMenuId()) {
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
