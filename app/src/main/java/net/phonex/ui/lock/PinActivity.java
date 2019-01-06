package net.phonex.ui.lock;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.annotations.PinUnprotected;
import net.phonex.ui.lock.activity.LockActivity;
import net.phonex.ui.lock.util.PinHelper;
import net.phonex.util.Log;
import net.phonex.util.ProfileUtils;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppEvents;

/**
 * @author ph4r05, miroc
 */
@PinUnprotected
public class PinActivity extends LockActivity implements PinFragment.Listener{
	
	public static final String TAG="PinActivity";

    private static final String VAR_CHANGE_PIN = "changePin";
    private static final String VAR_REMOVE_PIN = "removePin";

    // flag signaling that after PIN verification, user also wants to set a new pin
    private boolean changePin = false;
    // flag signaling that after PIN verification, user also wants delete pin
    private boolean removePin = false;

    /**
     * Starts PinPad activity
     * @param ctx
     */
    public static void startPinActivity(Context ctx){
        ctx.startActivity(getStartIntent(Intent.ACTION_MAIN, ctx));
    }
    public static void startPinActivityToRemovePin(Activity activity, int requestCode){
        activity.startActivityForResult(getStartIntent(Intent.ACTION_DELETE, activity), requestCode);
    }

    private static Intent getStartIntent(String action, Context ctx){
        Intent i = new Intent(ctx, PinActivity.class);
        i.setAction(action);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return i;
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (intent!= null && intent.getAction()!=null){
            switch (intent.getAction()){
                case Intent.ACTION_EDIT:
                    changePin = true;
                    break;
                case Intent.ACTION_DELETE:
                    removePin = true;
                    break;
            }
        }

        Log.inf(TAG, "onCreate: intent [%s] ", (intent != null ? intent.toString() : "null"));
        setContentView(R.layout.activity_with_fragment);

        Fragment pinFragment;
        if (PinHelper.hasPinSaved(this)){
            Log.d(TAG, "onCreate: saved PIN found, running verification instance of PinFragment");
            pinFragment = PinFragment.newInstanceForVerification();
        } else {
            Log.inf(TAG, "onCreate: no saved PIN found, running creation instance of PinFragment");
            pinFragment = PinFragment.newInstanceForCreation();
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_content, pinFragment)
                .commit();
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(VAR_CHANGE_PIN, changePin);
        outState.putBoolean(VAR_REMOVE_PIN, removePin);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        changePin = savedInstanceState.getBoolean(VAR_CHANGE_PIN);
        removePin = savedInstanceState.getBoolean(VAR_REMOVE_PIN);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }
	
	@Override
	public void onBackPressed() {
		this.moveTaskToBack(true);
	}

    @Override
    public void onValidated() {
        Log.inf(TAG, "Correct PIN entered");
        if (changePin){
            Fragment f = PinFragment.newInstanceForCreation();
            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_content, f)
                    .commit();
            AnalyticsReporter.from(this).event(AppEvents.PIN_LOCK_CHANGED);

        } else if (removePin){
            PinHelper.resetSavedPin(this);
            AnalyticsReporter.from(this).event(AppEvents.PIN_LOCK_DISABLED);
            setResult(RESULT_OK);
            this.finish();
        } else {
            PinHelper.lock(this, false);
            this.finish();
        }
    }

    @Override
    public void onPinCreated() {
        AnalyticsReporter.from(this).event(AppEvents.PIN_LOCK_CREATED);
        PinHelper.lock(this, false);
        this.finish();
    }

    @Override
    public void onTriesDepleted(int numberOfTries) {
        Log.inf(TAG, "Maximum number of invalid entered PINs reached, logging out");
        Toast.makeText(this, String.format(getString(R.string.pin_tries_depleted), numberOfTries), Toast.LENGTH_LONG).show();
        ProfileUtils.logout(this);
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
