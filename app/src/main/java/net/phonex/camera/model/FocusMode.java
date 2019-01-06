package net.phonex.camera.model;

/**
 * Created by Matus on 21-Jul-15.
 */
public enum FocusMode {
    // TODO translate
    AUTO(0, "Auto"), TOUCH(1, "Touch");

    private int id;

    private String name;

    FocusMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static FocusMode getFocusModeById(int id) {
        for (FocusMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return null;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }

}
