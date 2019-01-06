package net.phonex.util.account;

import android.content.Intent;

import net.phonex.db.entity.SipProfile;
import net.phonex.pref.PreferencesManager;
import net.phonex.ui.account.AccountPreferences;

/**
 * Account manager interface.
 */
public interface IAccountManager {
    /**
     * Set the parent preference container. This method may be used to store
     * parent context and use it.
     * 
     * @param parent The base preference container that is basically a
     *            preference activity.
     */
    void setParent(AccountPreferences parent);

    /**
     * Get the preference resource to be used for the preference view.
     * 
     * @return The preference resource identifier.
     */
    int getBasePreferenceResource();

    /**
     * Fill the layout once inflated with sip profile content.
     * 
     * @param account The account to fill information of.
     */
    void fillLayout(SipProfile account);

    /**
     * Update descriptions of fields. This is called each time something change.
     * It could update the description with content of the value.
     */
    void updateDescriptions();

    /**
     * Retrieve the default summary for a given field.
     * 
     * @param fieldName the name of the field to retrieve summary of.
     * @return The summary of the field.
     */
    String getDefaultFieldSummary(String fieldName);

    // Save
    /**
     * Build the account based on preference view contents.
     * 
     * @param account The sip profile already saved in database
     * @return the sip profile to save into databse based on fields contents.
     */
    SipProfile buildAccount(SipProfile account);

    /**
     * Set default global application preferences. This is a hook method to set
     * preference when an account is saved with this profile. It's useful for
     * sip providers that needs global settings hack.
     * 
     * @param prefs The preference wrapper interface.
     */
    void setDefaultParams(PreferencesManager prefs);

    boolean canSave();

    /**
     * Does the accountManager changes something that requires to restart sip stack? If
     * so once saved, the accountManager will also ask for a stack restart to take into
     * account any preference changed with
     * {@link #setDefaultParams(PreferencesManager)}
     * 
     * @return true if the accountManager would like the sip stack to restart
     */
    boolean needRestart();

    // Extras
    void onActivityResult(int requestCode, int resultCode, Intent data);

    void onStart();
    void onStop();
}
