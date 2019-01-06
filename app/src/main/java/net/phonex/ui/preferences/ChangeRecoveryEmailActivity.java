package net.phonex.ui.preferences;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import net.phonex.R;
import net.phonex.ui.lock.activity.LockActionBarActivity;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class ChangeRecoveryEmailActivity extends LockActionBarActivity{
	private static final String TAG = "ChangeRecoveryEmailActivity";
	@InjectView(R.id.my_toolbar) Toolbar toolbar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.change_recovery_email_activity);

		ButterKnife.inject(this);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle(R.string.recovery_email);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
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
