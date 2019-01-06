package net.phonex.ui.customViews;

/**
 * Interface for UI elements with two options, Left or right.
 * Typically used to answer/hangup incomming call.
 */
public interface ILRChooser {

    /**
     * User triggers event by grabbing left hand side handle.
     */
    int LEFT_SIDE = 0;

    /**
     * User triggers event by grabbing right hand side handle.
     */
    int RIGHT_SIDE = 1;

    /**
     * Event on user moving either left or right handle.
     *
     * @param side Which "dial handle" the user grabbed, either
     *            {@link #LEFT_SIDE}, {@link #RIGHT_SIDE}.
     */
    void onLRChoice(int side);

    public interface ILRChooserUser {
        void setLRChooser(ILRChooser l);
    }
}
