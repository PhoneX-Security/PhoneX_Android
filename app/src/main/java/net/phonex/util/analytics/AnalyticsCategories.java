package net.phonex.util.analytics;

/**
 * Created by miroc on 2.9.15.
 */
public enum AnalyticsCategories {
    // types differentiation: event is passive (message received), action is active (message sent)
    EVENT, BUTTON_CLICK, PASSIVE_EVENT;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
