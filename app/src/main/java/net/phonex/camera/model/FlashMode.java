package net.phonex.camera.model;

/**
 * Created by Matus on 21-Jul-15.
 */
public enum FlashMode {
    ON(0, "On"), AUTO(1, "Auto"), OFF(2, "Off");

    private int id;

    private String name;

    FlashMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static FlashMode getFlashModeById(int id) {
        for (FlashMode mode : values()) {
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
