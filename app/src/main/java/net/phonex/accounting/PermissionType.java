package net.phonex.accounting;

/**
 * These types and names need to stay synchronized with license server permission names
 * Created by miroc on 21.10.15.
 */
public enum PermissionType {
    CALLS_OUTGOING_SECONDS("calls.outgoing.seconds"),
    FILES_OUTGOING_FILES("files.outgoing.files"),
    MESSAGES_OUTGOING_LIMIT("messages.outgoing.limit"),
    MESSAGES_OUTGOING_DAY_LIMIT("messages.outgoing.day_limit"),
    UNKNOWN("unknown");

    public static PermissionType fromName(String text) {
        if (text != null) {
            for (PermissionType type : PermissionType.values()) {
                if (text.equalsIgnoreCase(type.name)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }

    private final String name;

    PermissionType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
