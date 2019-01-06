package net.phonex.ui.addContact;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import net.phonex.R;
import net.phonex.ui.lock.activity.LockActionBarActivity;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class AddContactActivity extends LockActionBarActivity {
    private static final String TAG = "AddContactActivity";
    @InjectView(R.id.my_toolbar)
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_with_fragment_and_toolbar);

        if (savedInstanceState == null) {
            // During initial setup, plug in the details fragment.
            AddContactFragment f = AddContactFragment.newInstance(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(
                    R.id.fragment_content, f).commit();
        }

        ButterKnife.inject(this);

        toolbar.setTitle(R.string.add_account);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
