package net.phonex;

import com.google.android.gms.analytics.ExceptionParser;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Created by miroc on 3.11.15.
 */
public class AnalyticsExceptionParser implements ExceptionParser {
    @Override
    public String getDescription(String p_thread, Throwable p_throwable) {
        return "Thread: " + p_thread + ", Exception: " + ExceptionUtils.getStackTrace(p_throwable);
    }
}
