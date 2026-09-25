package com.sibim.util;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

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
        makeKeyboardActivatable(root);
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
        forEachChild(root, AccessibilityUtils::applyAccessibleTextFromTooltips);
    }

    /** Visits {@code root} and every node below it, including content that is
     *  not yet a scene-graph child because its container has no skin until it
     *  is shown — ScrollPane content, tab contents, TitledPane content and
     *  SplitPane items. Five screens use a ScrollPane as their FXML root, so a
     *  plain getChildrenUnmodifiable()/lookupAll() walk right after load sees
     *  nothing inside them. */
    public static void forEachNode(Node root, java.util.function.Consumer<Node> action) {
        if (root == null) return;
        action.accept(root);
        forEachChild(root, child -> forEachNode(child, action));
    }

    private static void forEachChild(Node node, java.util.function.Consumer<Node> action) {
        if (node instanceof javafx.scene.control.ScrollPane sp) {
            if (sp.getContent() != null) action.accept(sp.getContent());
            return;   // its only real child once skinned is that same content
        }
        if (node instanceof javafx.scene.control.TabPane tp) {
            for (var tab : tp.getTabs()) if (tab.getContent() != null) action.accept(tab.getContent());
            return;
        }
        if (node instanceof javafx.scene.control.TitledPane tp && tp.getContent() != null) {
            action.accept(tp.getContent());
        }
        if (node instanceof javafx.scene.control.SplitPane sp) {
            sp.getItems().forEach(action);
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) action.accept(child);
        }
    }

    // ── Keyboard activation for click-only nodes ────────────────────────────
    // Dashboard quick-action cards, clickable stat cards, the alert banner and
    // similar surfaces are plain VBox/HBox/Label with an onMouseClicked handler:
    // Tab skips them and Enter/Space does nothing, so keyboard-only users can't
    // reach those actions at all. Any such node that declares
    // accessibleRole="BUTTON" in FXML is opted in here — made focus-traversable
    // and wired so Enter/Space invoke the same onMouseClicked handler — which
    // keeps the FXML the single place that marks a node as a button.
    private static final String KEY_ACTIVATION_MARK = "sibim.a11y.keyActivation";

    /** Same opt-in as accessibleRole="BUTTON" in FXML, for click-only nodes
     *  built in Java — call after setOnMouseClicked. Needed because nodes
     *  created after the screen's FXML load (async data, rebuilt cards) are
     *  never seen by the one-time walk above. */
    public static <T extends Node> T asButton(T node, String accessibleText) {
        node.setAccessibleRole(AccessibleRole.BUTTON);
        if (accessibleText != null && !accessibleText.isBlank()) node.setAccessibleText(accessibleText);
        if (!node.getStyleClass().contains("a11y-focusable")) node.getStyleClass().add("a11y-focusable");
        makeKeyboardActivatable(node);
        return node;
    }

    private static void makeKeyboardActivatable(Node node) {
        if (node instanceof ButtonBase) return;
        if (node.getAccessibleRole() != AccessibleRole.BUTTON) return;
        if (node.getOnMouseClicked() == null) return;
        if (node.getProperties().putIfAbsent(KEY_ACTIVATION_MARK, Boolean.TRUE) != null) return;

        node.setFocusTraversable(true);
        node.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() != node) return;
            if (e.getCode() != KeyCode.ENTER && e.getCode() != KeyCode.SPACE) return;
            var handler = node.getOnMouseClicked();
            if (handler == null) return;
            e.consume();
            // Real coordinates (node centre) so handlers that anchor a popup
            // at the click point — e.g. the sidebar account menu — still land
            // next to the node instead of the screen's top-left corner.
            Bounds b = node.getLayoutBounds();
            double cx = b.getMinX() + b.getWidth() / 2, cy = b.getMinY() + b.getHeight() / 2;
            Point2D inScene = node.localToScene(cx, cy);
            Point2D onScreen = node.localToScreen(cx, cy);
            double sx = onScreen != null ? onScreen.getX() : 0, sy = onScreen != null ? onScreen.getY() : 0;
            handler.handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, inScene.getX(), inScene.getY(), sx, sy,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, false, false, null));
        });
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
