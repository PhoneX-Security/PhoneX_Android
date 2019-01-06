package net.phonex.sip;

import android.content.Context;

import net.phonex.db.entity.SipProfile;

/**
* Created by ph4r05 on 7/27/14.
*/
public interface IPjPlugin {
    /**
     * Set the android context for the module. Could be usefull to get
     * preferences for examples.
     *
     * @param ctxt android context
     */
    void setContext(Context ctxt);

    /**
     * Here pjsip endpoint should have this module added.
     */
    void onBeforeStartPjsip();

    /**
     * Here pjsip endpoint should have this module added.
     */
    void onBeforeStopPjsip();

    /**
     * This is fired just after account was added to pjsip and before will
     * be registered.
     *
     * @param pjId the pjsip id of the added account.
     * @param acc the profile account.
     */
    void onBeforeAccountStartRegistration(int pjId, SipProfile acc);
}
