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

    private static String  pendingAreaFilter;
    private static String  pendingProductId;
    private static boolean pendingNuevoBien;
    private static boolean pendingNuevoMovimiento;

    public static void setPendingAreaFilter(String area) {
        pendingAreaFilter = area;
    }

    /** Reads and clears the pending area filter — null if none was set. */
    public static String consumePendingAreaFilter() {
        String value = pendingAreaFilter;
        pendingAreaFilter = null;
        return value;
    }

    /** Set before navigating to Productos — the controller will scroll to
     *  and select the bien with this ID on first load. */
    public static void setPendingProductId(String id) {
        pendingProductId = id;
    }

    /** Reads and clears the pending product ID — null if none was set. */
    public static String consumePendingProductId() {
        String value = pendingProductId;
        pendingProductId = null;
        return value;
    }

    public static void setPendingNuevoBien() { pendingNuevoBien = true; }

    /** Reads and clears the flag. Returns true if Ctrl+N triggered a new-bien request. */
    public static boolean consumePendingNuevoBien() {
        boolean v = pendingNuevoBien;
        pendingNuevoBien = false;
        return v;
    }

    public static void setPendingNuevoMovimiento() { pendingNuevoMovimiento = true; }

    public static boolean consumePendingNuevoMovimiento() {
        boolean v = pendingNuevoMovimiento;
        pendingNuevoMovimiento = false;
        return v;
    }
}
