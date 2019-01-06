package net.phonex.util.account;

import android.content.Intent;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import net.phonex.R;
import net.phonex.db.entity.SipProfile;
import net.phonex.db.entity.CallFirewall;
import net.phonex.pref.PreferencesConnector;
import net.phonex.ui.account.AccountPreferences;
import net.phonex.util.Log;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract implementation of the Account manager interface.
 */
public abstract class AbstractAccountManager implements IAccountManager {
    protected AccountPreferences parent;

    /**
     * Returns preferred system identifier of this manager.
     *
     * @return
     */
    static public String getId() {
        return null;
    }

    /**
     * Returns preferred human readable label for this manager.
     *
     * @return
     */
    static public String getLabel() {
        return null;
    }

    public void setParent(AccountPreferences aParent) {
        parent = aParent;
    }

    //Utilities functions
    protected boolean isEmpty(EditTextPreference edt) {
        if (edt.getText() == null) {
            return true;
        }
        if (edt.getText().equals("")) {
            return true;
        }
        return false;
    }

    protected boolean isMatching(EditTextPreference edt, String regex) {
        if (edt.getText() == null) {
            return false;
        }
        return Pattern.matches(regex, edt.getText());
    }

    /**
     * @param edt
     * @see EditTextPreference#getText()
     */
    protected String getText(EditTextPreference edt) {
        return edt.getText();
    }


    /**
     * @param fieldName
     * @see net.phonex.ui.preferences.BasePreferences#setStringEntrySummary(String)
     */
    protected void setStringFieldSummary(String fieldName) {
        parent.setStringEntrySummary(fieldName);
    }

    /**
     * @param fieldName
     * @see net.phonex.ui.preferences.BasePreferences#setPasswordFieldSummary(String)
     */
    protected void setPasswordFieldSummary(String fieldName) {
        parent.setPasswordFieldSummary(fieldName);
    }

    /**
     * @param fieldName
     * @see net.phonex.ui.preferences.BasePreferences#setListFieldSummary(String)
     */
    protected void setListFieldSummary(String fieldName) {
        parent.setListFieldSummary(fieldName);
    }

    /**
     * @see PreferenceScreen#findPreference(CharSequence)
     */
    @SuppressWarnings("deprecation")
    protected Preference findPreference(String fieldName) {
        return parent.findPreference(fieldName);
    }

    /**
     * @see PreferenceScreen#addPreference(Preference)
     */
    @SuppressWarnings("deprecation")
    protected void addPreference(Preference pref) {
        parent.getPreferenceScreen().addPreference(pref);
        markFieldValid(pref);
    }

    /**
     * Hide a preference from the preference screen.
     *
     * @param parentGroup key for parent group if any. If null no parent group are searched
     * @param fieldName   key for the field to remove
     */
    @SuppressWarnings("deprecation")
    protected void hidePreference(String parentGroup, String fieldName) {
        PreferenceScreen pfs = parent.getPreferenceScreen();
        PreferenceGroup parentPref = pfs;
        if (parentGroup != null) {
            parentPref = (PreferenceGroup) pfs.findPreference(parentGroup);
        }

        Preference toRemovePref = pfs.findPreference(fieldName);

        if (toRemovePref != null && parentPref != null) {
            boolean rem = parentPref.removePreference(toRemovePref);
            Log.df("Generic prefs", "Has removed it : %s", rem);
        } else {
            Log.df("Generic prefs", "Not able to find%s %s", parent, fieldName);
        }
    }


    private void markFieldInvalid(Preference field) {
        field.setLayoutResource(R.layout.invalid_preference_row);
    }

    private void markFieldValid(Preference field) {
        field.setLayoutResource(R.layout.preference_row);
    }

    /**
     * Check the validity of a field and if invalid mark it as invalid
     *
     * @param field      field to check
     * @param isNotValid if true this field is considered as invalid
     * @return if the field is valid (!isNotValid) This is convenient for &=
     * from a true variable over multiple fields
     */
    protected boolean checkField(Preference field, boolean isNotValid) {
        if (isNotValid) {
            markFieldInvalid(field);
        } else {
            markFieldValid(field);
        }
        return !isNotValid;
    }

    /**
     * Set global preferences for this accountManager
     * If some preference that need restart are modified here
     * Do not forget to return true in need restart
     */
    public void setDefaultParams(PreferencesConnector prefs) {
        // By default empty implementation
    }

    @Override
    public boolean needRestart() {
        return false;
    }

    public List<CallFirewall> getDefaultFilters(SipProfile acc) {
        return null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // By default empty implementation
    }

    public void onStart() {
    }

    public void onStop() {
    }

}
