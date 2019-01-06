package com.github.nativehandler;

import android.content.Context;
import android.content.Intent;

import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

public class NativeCrashHandler {

    private static final String TAG = "NativeCrashHandler";
    Context ctx;

	private void makeCrashReport(String reason, StackTraceElement[] stack, int threadID) {
		makeCrashReport(reason, "", stack, threadID);
	}

	private void makeCrashReport(String reason, String debugPCTrace, StackTraceElement[] stack, int threadID) {
		if (stack != null)
			NativeError.natSt = stack;
		NativeError e = debugPCTrace==null || debugPCTrace.length() == 0 ? new NativeError(reason, threadID) : new NativeError(reason, debugPCTrace, threadID);
		Intent intent = new Intent(ctx, NativeCrashActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("error", e);
		ctx.startActivity(intent);
	}

    // called from native code
    public void reportToAcra(String message){
        Log.vf(TAG, "reportToAcra); [%s]", message);
        Exception exception = new Exception(message);
        MiscUtils.reportExceptionToAcra(exception);
    }

	public void registerForNativeCrash(Context ctx) {
		this.ctx = ctx;
		if (!nRegisterForNativeCrash())
			throw new RuntimeException("Could not register for native crash as nativeCrashHandler_onLoad was not called in JNI context");
	}

	public void unregisterForNativeCrash() {
		this.ctx = null;
		nUnregisterForNativeCrash();
	}

	private native boolean nRegisterForNativeCrash();
	private native void nUnregisterForNativeCrash();

}
