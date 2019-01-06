package net.phonex.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.xml.datatype.DatatypeConfigurationException;

public class DateUtils {
	private static final String TAG = "DateUtils";

	public static String formatToDate(Date date, Locale locale){
		return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date);
	}

	public static String timestampToDate(long timestamp, Locale locale){
		DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
		return df.format(new Date(timestamp));
	}

	public static String timestampToTime(long timestamp){
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
		return simpleDateFormat.format(new Date(timestamp));
	}

	public static CharSequence relativeTimeFromNow(long timestamp){
		int flags = android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE;
		return android.text.format.DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS, flags);
	}
	
	/**
	 * Converts string to Calendar
	 * 
	 * @param s
	 * @return
	 * @throws DatatypeConfigurationException
	 * @throws ParseException
	 */
	public static Calendar stringToCalendar(String s)
			throws DatatypeConfigurationException, ParseException {
		
		return stringToCalendar(s, "yyyy-MM-dd'T'HH:mm:ss.SSSz");
	}
	
	/**
	 * Converts string to Calendar
	 * 
	 * @param s
	 * @param format
	 * @return
	 * @throws DatatypeConfigurationException
	 * @throws ParseException
	 */
	public static Calendar stringToCalendar(String s, String format)
			throws DatatypeConfigurationException, ParseException {
		
		Date date;
		SimpleDateFormat simpleDateFormat;

		simpleDateFormat = new SimpleDateFormat(format);
		date = simpleDateFormat.parse(s);
		
		Calendar cal = Calendar.getInstance();
	    cal.setTime(date);
		
		return cal;
	}

	/**
	 * Return millis in 01:23:45 format (hours:minutes:seconds)
	 * @param millis
	 * @return
	 */
	public static String formatTime(long millis) {
		return formatTime(millis, false);
	}

	/**
	 * Return millis in 01:23:45 format (hours:minutes:seconds)
	 * @param millis
	 * @param alwaysShowHours
	 * @return
	 */
	public static String formatTime(long millis, boolean alwaysShowHours) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 60;

        String secondsD = String.valueOf(seconds);
        String minutesD = String.valueOf(minutes);

        String hoursD = null;
        if (hours != 0 || alwaysShowHours){
            hoursD = String.valueOf(hours);
        }

        if (seconds < 10){
            secondsD = "0" + seconds;
        }

        if (minutes < 10){
            minutesD = "0" + minutes;
        }

        if (hoursD != null && hours < 10){
            hoursD = "0" + hours;
        }

        String toReturn;
        if (hoursD != null){
            toReturn = hoursD + ":" + minutesD + ":" + secondsD;
        } else {
            toReturn = minutesD + ":" + secondsD;
        }
        return toReturn;
    }
}
