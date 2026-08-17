package com.sibim.session;

/**
 * Small hand-off for cross-view navigation that needs to carry a bit of
 * state (e.g. Organigrama → Bienes filtered by area). MainController
 * reloads each view's FXML fresh on every navigation, so there is no
 * standing reference between controllers — the source sets a pending value
 * here right before triggering navigation, and the destination consumes
 * (reads + clears) it once, in its own initialize().
 */
public final class NavigationContext {

    private NavigationContext() {}

    private static String pendingAreaFilter;

    public static void setPendingAreaFilter(String area) {
        pendingAreaFilter = area;
    }

    /** Reads and clears the pending area filter — null if none was set. */
    public static String consumePendingAreaFilter() {
        String value = pendingAreaFilter;
        pendingAreaFilter = null;
        return value;
    }
}
