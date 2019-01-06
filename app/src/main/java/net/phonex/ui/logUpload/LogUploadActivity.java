package net.phonex.ui.logUpload;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.core.Intents;
import net.phonex.ui.lock.activity.LockActionBarActivity;
import net.phonex.util.MiscUtils;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class LogUploadActivity extends LockActionBarActivity {
    @InjectView(R.id.my_toolbar) Toolbar toolbar;
    @InjectView(R.id.user_message) EditText userMessageEditText;
    @InjectView(R.id.button) Button submit;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_upload);
        ButterKnife.inject(this);
        toolbar.setTitle(R.string.send_logs);

        submit.setOnClickListener(v -> sendLogs());

        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
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

    private void sendLogs(){
        String userMessage = userMessageEditText.getText().toString();
        if (TextUtils.isEmpty(userMessage)){
            userMessage = "empty message";
        }

        Intent i = new Intent(Intents.ACTION_TRIGGER_LOG_UPLOAD);
        i.putExtra(Intents.EXTRA_LOG_UPLOAD_USER_MESSAGE, userMessage);

        MiscUtils.sendBroadcast(this, i);

        Toast.makeText(this, R.string.send_logs_progress, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected String activityAnalyticsName() {
        return this.getClass().getSimpleName();
    }
}
