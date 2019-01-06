package net.phonex.util.account;

/**
 * Created by ph4r05 on 7/1/14.
 * immutable
 * @author ph4r05
 */
public class AccountManagerDesc {
    /**
     * Manager identifier that handles this account.
     */
    private final String id;

    /**
     * Textual representation of the account manager.
     */
    private final String label;

    /**
     * Manager icon resource identifier.
     */
    private final int icon;
    /**
     * When in inactive state, this icon is shown
     */
    private final int iconInactive;
    private final int priority;
    private final Class<?> classRef;

    public AccountManagerDesc(String label, String id, int icon, int iconInactive, int priority, Class<?> classRef) {
        this.label = label;
        this.id = id;
        this.icon = icon;
        this.iconInactive = iconInactive;
        this.priority = priority;
        this.classRef = classRef;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getIcon() {
        return icon;
    }

    public int getIconInactive() {
        return iconInactive;
    }

    public int getPriority() {
        return priority;
    }

    public Class<?> getClassRef() {
        return classRef;
    }
}
