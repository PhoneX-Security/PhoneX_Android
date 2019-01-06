package net.phonex.util.android;

import android.content.Context;
import android.view.accessibility.AccessibilityManager;

/**
 * Accessibility service manager. Simple wrapper
 */
public class AccessibilitySvcManager {
    private AccessibilityManager accessibilityManager = null;
	public AccessibilitySvcManager() {

    }

    public void init(Context context) {
        if(accessibilityManager == null) {
            accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        }
    }

    public boolean isEnabled() {
        return accessibilityManager.isEnabled();
    }
}
