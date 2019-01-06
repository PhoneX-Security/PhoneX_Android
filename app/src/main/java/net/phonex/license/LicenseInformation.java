package net.phonex.license;

import android.content.Context;

import net.phonex.R;
import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.soap.entities.AccountInfoV1Response;
import net.phonex.soap.entities.AuthCheckV3Response;

import java.text.DateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Wrapper class for license information from SipProfile or particular SOAP response
 * Created by miroc on 20.4.15.
 */
public class LicenseInformation {
    private Date licenseExpiresOn;
    private Date licenseIssuedOn;
    private boolean licenseExpired;
    private String licenseType;

    public static final String TYPE_TRIAL = "trial";
    public static final String TYPE_FULL = "full";

    public static int getTextLimitPerDay(Context context) {
        return PhonexConfig.getIntegerPref(context, PhonexConfig.TRIAL_MESSAGE_LIMIT_PER_DAY);
    }

    public static boolean isTextLimitExceeded(Context context){
        int textLimitPerDay = getTextLimitPerDay(context);
        int outgoingMessageCount = TrialEventLog.getOutgoingMessageCount(context, 1);
        return outgoingMessageCount >= textLimitPerDay;
    }

    public LicenseInformation(SipProfile profile){
        licenseExpiresOn = profile.getLicenseExpiresOn();
        licenseIssuedOn = profile.getLicenseIssuedOn();
        licenseExpired = profile.isLicenseExpired();
        licenseType = profile.getLicenseType();

        if (licenseExpiresOn == null){
            licenseExpiresOn = new GregorianCalendar(2016, 1, 1).getTime();
        }
        if (licenseIssuedOn == null){
            licenseIssuedOn = new GregorianCalendar(2013, 9 , 3).getTime();
        }
    }

    public LicenseInformation(AccountInfoV1Response response) {
        // TODO remove this later
        if (response.getAccountExpires() == null){
            // temporary fix - for those who do not have any expiration date set
            response.setAccountExpires(new GregorianCalendar(2016, 1, 1));
        }

        if (response.getAccountIssued() == null){
            response.setAccountIssued(new GregorianCalendar(2013, 9 , 3));
        }

        licenseExpiresOn = response.getAccountExpires().getTime();
        licenseIssuedOn = response.getAccountIssued().getTime();
        licenseType = response.getLicenseType();

        licenseExpired = response.getServerTime().after(response.getAccountExpires());
    }

    public LicenseInformation(AuthCheckV3Response response) {
        // TODO remove this later
        if (response.getAccountExpires() == null){
            // temporary fix - for those who do not have any expiration date set
            response.setAccountExpires(new GregorianCalendar(2016, 1, 1));
        }

        if (response.getAccountIssued() == null){
            response.setAccountIssued(new GregorianCalendar(2013, 9 , 3));
        }

        licenseExpiresOn = response.getAccountExpires().getTime();
        licenseIssuedOn = response.getAccountIssued().getTime();
        licenseType = response.getLicenseType();

        licenseExpired = response.getServerTime().after(response.getAccountExpires());
    }

    public Date getLicenseExpiresOn() {
        return licenseExpiresOn;
    }

    public Date getLicenseIssuedOn() {
        return licenseIssuedOn;
    }

    public boolean isLicenseExpired() {
        return licenseExpired;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public boolean isTrial(){
        return TYPE_TRIAL.equals(licenseType);
    }

    public String getFormattedExpiration(Locale locale){
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
        return df.format(licenseExpiresOn);
    }

    public long timeToExpiration(){
         return licenseExpiresOn.getTime() - System.currentTimeMillis();
    }

    public String getFormattedLicenseType(Context context){
        switch (licenseType){
            case TYPE_TRIAL:
                return context.getString(R.string.license_type_trial);
            case TYPE_FULL:
            default:
                return context.getString(R.string.license_type_full);
        }
    }

    @Override
    public String toString() {
        return "LicenseInformation{" +
                "licenseExpiresOn=" + licenseExpiresOn +
                ", licenseIssuedOn=" + licenseIssuedOn +
                ", licenseExpired=" + licenseExpired +
                ", licenseType='" + licenseType + '\'' +
                '}';
    }
}
