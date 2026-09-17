package com.sibim.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;

import java.util.prefs.Preferences;

/** Fills in screen-reader labels that would otherwise be silently missing.
 *
 *  JavaFX's accessibility bridge (Windows UI Automation / NVDA / JAWS / Narrator)
 *  reads a {@link Labeled}'s accessible name from {@code getText()} — never from
 *  its {@link Tooltip}. Every icon-only button in this app (refresh, pagination,
 *  collapse/expand, export…) sets a Tooltip for sighted users but has no visible
 *  text, so a screen reader announces it as an unlabeled "button" with nothing
 *  to say. Annotating every one of those buttons by hand across ~15 FXML files
 *  (and every dialog built in Java) would be easy to forget for anything added
 *  later, so instead this walks a subtree once and copies each Tooltip's text
 *  into {@code accessibleText} wherever a Labeled has none of its own — applied
 *  from a single point per surface (screen navigation, dialog show) so new
 *  screens/dialogs get it for free as long as they set a Tooltip. */
public final class AccessibilityUtils {

    private AccessibilityUtils() {}

    public static void applyAccessibleTextFromTooltips(Node root) {
        if (root == null) return;
        if (root instanceof Labeled labeled) {
            boolean noVisibleText = labeled.getText() == null || labeled.getText().isBlank();
            boolean noAccessibleTextYet = labeled.getAccessibleText() == null || labeled.getAccessibleText().isBlank();
            if (noVisibleText && noAccessibleTextYet) {
                Tooltip tip = labeled.getTooltip();
                if (tip != null && tip.getText() != null && !tip.getText().isBlank()) {
                    labeled.setAccessibleText(tip.getText());
                }
            }
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyAccessibleTextFromTooltips(child);
            }
        }
    }

    // ── Text size (low-vision accessibility) ────────────────────────────────
    // Most of styles.css sets absolute px font sizes per component class
    // rather than cascading from a single base -fx-font-size, so a Scene-wide
    // font bump wouldn't reach most text, and a uniform Scale transform risks
    // clipping/overlap since JavaFX layout bounds don't grow with it. Instead
    // this cycles a marker style class that a curated set of the highest-
    // traffic text selectors (page titles, form labels, table cells, dialog
    // body text, stat values) override in styles.css — same mechanism as the
    // existing row-density toggle, just for font size instead of row height.
    private static final Preferences TEXT_SCALE_PREFS =
        Preferences.userRoot().node("sibim/ui/text-scale");
    public static final String[] TEXT_SCALE_CLASSES = { "", "text-scale-lg", "text-scale-xl" };
    public static final String[] TEXT_SCALE_LABELS  = { "Normal", "Grande", "Extra" };
    public static final String[] TEXT_SCALE_ICONS   =
        { "mdi2f-format-size", "mdi2f-format-size", "mdi2f-format-size" };

    public static int getTextScaleIndex() {
        int idx = TEXT_SCALE_PREFS.getInt("index", 0);
        return idx >= 0 && idx < TEXT_SCALE_CLASSES.length ? idx : 0;
    }

    public static int cycleTextScaleIndex() {
        int next = (getTextScaleIndex() + 1) % TEXT_SCALE_CLASSES.length;
        TEXT_SCALE_PREFS.putInt("index", next);
        return next;
    }

    /** Removes any previous scale class from {@code node} and applies the
     *  current one, if any (index 0 = "Normal" = no class). Safe to call on
     *  any Parent — the main content area, or an individual dialog pane. */
    public static void applyCurrentTextScaleClass(Parent node) {
        if (node == null) return;
        node.getStyleClass().removeAll("text-scale-lg", "text-scale-xl");
        String cls = TEXT_SCALE_CLASSES[getTextScaleIndex()];
        if (!cls.isEmpty()) node.getStyleClass().add(cls);
    }
}
