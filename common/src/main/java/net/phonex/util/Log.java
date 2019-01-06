package net.phonex.util;

import net.phonex.common.Settings;

/**
 * Logger subsystem.
 * 
 * @author ph4r05
 */
public class Log {
	/**
	 * Static log level value.
	 * Applicable during debugging. In release mode
	 * logs are removed by ProGuard.
	 */
	private static int logLevel = 5;
	
	public static final int LOG_E=1;
	public static final int LOG_W=2;
	public static final int LOG_I=3;
	public static final int LOG_D=4;
	public static final int LOG_V=5;
	
	/**
	 * Change current logging level
	 * @param level new log level 1 <= level <= 6 
	 */
	public static void setLogLevel(int level) {
		logLevel = level;
	}
	
	/**
	 * Get the current log level
	 * @return the log level
	 */
	public static int getLogLevel() {
	    return logLevel;
	}
	
	/**
	 * Log verbose
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 */
	public static void v(String tag, String msg) {
		if(logLevel >= 5 && Settings.debuggingRelease()) {
            logLong2Android(LOG_V, tag, msg);
		}
	}

	/**
	 * Log verbose
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 * @param tr Error to serialize in log
	 */
	public static void v(String tag, String msg, Throwable tr) {
		if(logLevel >= 5 && Settings.debuggingRelease()) {
            logLong2Android(LOG_V, tag, msg, tr);
		}
	}
	
	public static void vf(String tag, final String msg, final Object... args) {
		if(logLevel >= 5 && Settings.debuggingRelease()) {
            logLong2Android(LOG_V, tag, String.format(msg, args));
		}
	}
	
	public static void vf(String tag, final Throwable tr, final String msg, final Object... args) {
		if(logLevel >= 5 && Settings.debuggingRelease()) {
            logLong2Android(LOG_V, tag, String.format(msg, args), tr);
		}
	}
	
	/**
	 * Log debug
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 */
	public static void d(String tag, String msg) {
		if(logLevel >= 4 && Settings.debuggingRelease()) {
            logLong2Android(LOG_D, tag, msg);
		}
	}

	/**
	 * Log debug
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 * @param tr Error to serialize in log
	 */
	public static void d(String tag, String msg, Throwable tr) {
		if(logLevel >= 4 && Settings.debuggingRelease()) {
            logLong2Android(LOG_D, tag, msg, tr);
		}
	}
	
	public static void df(String tag, final String msg, final Object... args) {
		if(logLevel >= 4 && Settings.debuggingRelease()) {
            logLong2Android(LOG_D, tag, String.format(msg, args));
		}
	}
	
	public static void df(String tag, final Throwable tr, final String msg, final Object... args) {
		if(logLevel >= 4 && Settings.debuggingRelease()) {
            logLong2Android(LOG_D, tag, String.format(msg, args), tr);
		}
	}
	
	/**
	 * Log info
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 */
	public static void i(String tag, String msg) {
		if(logLevel >= 3 && Settings.debuggingRelease()) {
            logLong2Android(LOG_I, tag, msg);
		}
	}

	/**
	 * Log info
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 * @param tr Error to serialize in log
	 */
	public static void i(String tag, String msg, Throwable tr) {
		if(logLevel >= 3 && Settings.debuggingRelease()) {
            logLong2Android(LOG_I, tag, msg, tr);
		}
	}

	public static void inf(String tag, final String msg, final Object... args) {
		if(logLevel >= 3 && Settings.debuggingRelease()) {
            logLong2Android(LOG_I, tag, String.format(msg, args));
		}
	}
	
	public static void inf(String tag, final Throwable tr, final String msg, final Object... args) {
		if(logLevel >= 3 && Settings.debuggingRelease()) {
            logLong2Android(LOG_I, tag, String.format(msg, args), tr);
		}
	}
	
	/**
	 * Log warning
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 */
	public static void w(String tag, String msg) {
		if(logLevel >= 2 && Settings.debuggingRelease()) {
            logLong2Android(LOG_W, tag, msg);
		}
	}

	/**
	 * Log warning
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 * @param tr Error to serialize in log
	 */
	public static void w(String tag, String msg, Throwable tr) {
		if(logLevel >= 2 && Settings.debuggingRelease()) {
            logLong2Android(LOG_W, tag, msg, tr);
		}
	}

	public static void wf(String tag, final String msg, final Object... args) {
		if(logLevel >= 2 && Settings.debuggingRelease()) {
            logLong2Android(LOG_W, tag, String.format(msg, args));
		}
	}
	
	public static void wf(String tag, final Throwable tr, final String msg, final Object... args) {
		if(logLevel >= 2 && Settings.debuggingRelease()) {
            logLong2Android(LOG_W, tag, String.format(msg, args), tr);
		}
	}
	
	/**
	 * Log error
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 */
	public static void e(String tag, String msg) {
		if(logLevel >= 1 && Settings.debuggingRelease()) {
            logLong2Android(LOG_E, tag, msg);
		}
	}

	/**
	 * Log error
	 * @param tag Tag for this log
	 * @param msg Msg for this log
	 * @param tr Error to serialize in log
	 */
	public static void e(String tag, String msg, Throwable tr) {
		if(logLevel >= 1 && Settings.debuggingRelease()) {
            logLong2Android(LOG_E, tag, msg, tr);
		}
	}
	
	public static void ef(String tag, final String msg, final Object... args) {
		if(logLevel >= 1 && Settings.debuggingRelease()) {
            logLong2Android(LOG_E, tag, String.format(msg, args));
		}
	}
	
	public static void ef(String tag, final Throwable tr, final String msg, final Object... args) {
		if(logLevel >= 1 && Settings.debuggingRelease()) {
            logLong2Android(LOG_E, tag, String.format(msg, args), tr);
		}
	}

    /**
     * Interface to log messages to Android, while separating each \n character to the new log message
     * so the log message is not truncated due to its big size.
     * @param logLevel
     * @param tag
     * @param msg
     */
    private static void logLong2Android(int logLevel, String tag, String msg){
        if (msg == null){
            log2Android(logLevel, tag, msg);
            return;
        }

        String[] parts = msg.split("\n");
        for (String part1 : parts) {
            String part = part1.trim();
            log2Android(logLevel, tag, part);
        }
    }

    /**
     * Interface to log messages to Android, while separating each \n character to the new log message
     * so the log message is not truncated due to its big size.
     * @param logLevel
     * @param tag
     * @param msg
     */
    private static void logLong2Android(int logLevel, String tag, String msg, Throwable thr){
        if (msg == null){
            log2Android(logLevel, tag, msg);
            return;
        }

        String[] parts = msg.split("\n");
        for (String part1 : parts) {
            String part = part1.trim();
            log2Android(logLevel, tag, part, thr);
        }
    }

    /**
     * Common interface to log to android via defining severity level.
     * @param logLevel
     * @param tag
     * @param msg
     */
    private static void log2Android(int logLevel, String tag, String msg){
        switch(logLevel){
            case LOG_E:
				android.util.Log.e(tag, msg);
				break;
            case LOG_W:
				android.util.Log.w(tag, msg);
				break;
            case LOG_I:
				android.util.Log.i(tag, msg);
				break;
            case LOG_D:
				android.util.Log.d(tag, msg);
				break;
            case LOG_V:
				android.util.Log.v(tag, msg);
				break;
            default:
				android.util.Log.v(tag, msg);
				break;
        }
    }

    /**
     * Common interface to log to android via defining severity level and throwable object.
     * @param logLevel
     * @param tag
     * @param msg
     * @param thr
     */
    private static void log2Android(int logLevel, String tag, String msg, Throwable thr){
        switch(logLevel){
            case LOG_E:
				android.util.Log.e(tag, msg, thr);
				break;
            case LOG_W:
				android.util.Log.w(tag, msg, thr);
				break;
            case LOG_I:
				android.util.Log.i(tag, msg, thr);
				break;
            case LOG_D:
				android.util.Log.d(tag, msg, thr);
				break;
            case LOG_V:
				android.util.Log.v(tag, msg, thr);
				break;
            default:
				android.util.Log.v(tag, msg, thr);
				break;
        }
    }
	
	public static void log(int logLevel, String tag, String msg){
		switch(logLevel){
			case LOG_E: e(tag, msg); break;
			case LOG_W: w(tag, msg); break;
			case LOG_I: i(tag, msg); break;
			case LOG_D: d(tag, msg); break;
			case LOG_V: v(tag, msg); break;
			default:    v(tag, msg); break;
		}
	}
	
	public static void logf(int logLevel, final String tag, final String fmtstr, final Object... args){
		switch(logLevel){
			case LOG_E: ef(tag,  fmtstr, args); break;
			case LOG_W: wf(tag,  fmtstr, args); break;
			case LOG_I: inf(tag, fmtstr, args); break;
			case LOG_D: df(tag,  fmtstr, args); break;
			case LOG_V: vf(tag,  fmtstr, args); break;
			default:    vf(tag,  fmtstr, args); break;
		}
	}
	
	public static void log(int logLevel, String tag, String msg, Throwable tr){
		switch(logLevel){
			case LOG_E: e(tag, msg, tr); break;
			case LOG_W: w(tag, msg, tr); break;
			case LOG_I: i(tag, msg, tr); break;
			case LOG_D: d(tag, msg, tr); break;
			case LOG_V: v(tag, msg, tr); break;
			default:    v(tag, msg, tr); break;
		}
	}
	
	public static void logf(int logLevel, final Throwable tr, final String tag, final String fmtstr, final Object... args){
		switch(logLevel){
			case LOG_E: ef (tag, tr, fmtstr, args); break;
			case LOG_W: wf (tag, tr, fmtstr, args); break;
			case LOG_I: inf(tag, tr, fmtstr, args); break;
			case LOG_D: df (tag, tr, fmtstr, args); break;
			case LOG_V: vf (tag, tr, fmtstr, args); break;
			default:    vf (tag, tr, fmtstr, args); break;
		}
	}
}
