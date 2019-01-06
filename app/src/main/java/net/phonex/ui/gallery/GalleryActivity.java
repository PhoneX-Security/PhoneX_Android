package net.phonex.ui.gallery;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import net.phonex.PhonexSettings;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ui.lock.activity.LockActionBarActivity;

import java.util.ArrayList;

/**
 * Created by Matus on 7/15/2015.
 */
public class GalleryActivity extends LockActionBarActivity {

    public static final String EXTRA_START_POSITION = "extra_start_position";
    public static final String EXTRA_URIS = "extra_uris";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load language set in preferences
        PhonexSettings.loadDefaultLanguage(this);

        Intent intent = getIntent();
        int startingPosition = intent.getIntExtra(EXTRA_START_POSITION, 0);
        ArrayList<FileStorageUri> uris = intent.getParcelableArrayListExtra(EXTRA_URIS);

        if (uris == null || uris.isEmpty()) {
            finish();
        }

        if (savedInstanceState == null) {
            PagerFragment fragment = PagerFragment.newInstance(uris, startingPosition);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(android.R.id.content, fragment, PagerFragment.class.getSimpleName());
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
