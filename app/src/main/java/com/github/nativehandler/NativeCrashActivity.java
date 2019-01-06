package com.github.nativehandler;

import android.app.Activity;
import android.os.Bundle;

import net.phonex.util.Log;
import net.phonex.util.system.ProcKiller;

public class NativeCrashActivity extends Activity {
    private final static String THIS_FILE="NativeCrash";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		NativeError e = (NativeError)getIntent().getSerializableExtra("error");

        // If native part crashed, phonex has to be killed...
        Log.w(THIS_FILE, "API 19, has to kill app.");
        ProcKiller.killPhoneX(this.getApplicationContext(), true, true, true, true, true);

		throw e;
	}
	
}
