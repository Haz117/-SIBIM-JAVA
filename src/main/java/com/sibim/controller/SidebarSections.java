package com.sibim.controller;

import com.sibim.util.AnimationUtils;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Accordion behaviour for the sidebar's four sections.
 *
 * Why: 15 nav buttons in 4 groups don't fit in the sidebar at the default 800 px window
 * height, so the last items were cut off and "Control patrimonial" / "Sistema" were only
 * reachable by scrolling. Each section is now a header + an items box that can be folded.
 *
 * <ul>
 *   <li>Manual folding is remembered (Preferences); folding done by navigating is not.</li>
 *   <li>Opening a page (shortcut, palette, tutorial…) reveals its section.</li>
 *   <li>A folded section that holds the active page keeps its header highlighted.</li>
 *   <li>A folded section shows the sum of its items' badges (e.g. 17 alerts) on the header.</li>
 *   <li>"Rail mode" (sidebar collapsed to icons) hides the headers and shows every item.</li>
 * </ul>
 *
 * Expected FXML shape of each section root: {@code VBox[header HBox(title Label, badge Label,
 * chevron FontIcon), items VBox]}.
 */
final class SidebarSections {

    /** System property that redirects the saved fold state (tests use it so they never touch the user's real layout). */
    static final String PREFS_NODE_PROPERTY = "sibim.sidebar.prefs";

    static Preferences defaultPrefs() {
        return Preferences.userRoot().node(System.getProperty(PREFS_NODE_PROPERTY, "sibim/sidebar"));
    }

    private static final Duration ANIM = Duration.millis(170);
    private static final String CHEVRON_OPEN   = "mdi2c-chevron-down";
    private static final String CHEVRON_CLOSED = "mdi2c-chevron-right";

    private static final class Section {
        final NavSection id;
        final VBox root;
        final HBox header;
        final VBox items;
        final FontIcon chevron;
        final Label badge;
        final List<Label> itemBadges = new ArrayList<>();
        boolean expanded;
        Timeline running;

        Section(NavSection id, VBox root) {
            this.id = id;
            this.root = root;
            this.header = (HBox) root.getChildren().get(0);
            this.items = (VBox) root.getChildren().get(1);
            FontIcon icon = null;
            Label badgeLabel = null;
            for (Node n : header.getChildren()) {
                if (n instanceof FontIcon fi) icon = fi;
                else if (n instanceof Label l && l.getStyleClass().contains("nav-section-badge")) badgeLabel = l;
            }
            this.chevron = icon;
            this.badge = badgeLabel;
        }
    }

    private final Map<NavSection, Section> sections = new EnumMap<>(NavSection.class);
    private final Preferences prefs;
    private final boolean animate;
    private Runnable onLayoutChanged = () -> {};
    private boolean rail;

    SidebarSections(Map<NavSection, VBox> roots, Preferences prefs, boolean animate) {
        this.prefs = prefs;
        this.animate = animate;
        roots.forEach((id, root) -> {
            Section s = new Section(id, root);
            s.expanded = prefs.getBoolean(key(id), id.defaultExpanded);
            sections.put(id, s);
            wire(s);
            applyItemsInstant(s);
            updateHeaderState(s);
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called after any change that moves nav buttons (used to re-position the active-tab marker). */
    void setOnLayoutChanged(Runnable r) { this.onLayoutChanged = r != null ? r : () -> {}; }

    boolean isExpanded(NavSection id) { return sections.get(id).expanded; }

    /** True when the section's items are actually on screen (rail mode shows everything). */
    boolean isItemsShown(NavSection id) { return sections.get(id).items.isManaged(); }

    /** User folded/unfolded a section: animate and remember the choice. */
    void toggle(NavSection id) {
        setExpanded(id, !sections.get(id).expanded, true, animate);
    }

    /** Programmatic change (e.g. from a test): no animation, remembered. */
    void setExpanded(NavSection id, boolean expanded) {
        setExpanded(id, expanded, true, false);
    }

    /**
     * Opens the section that contains {@code node} if it is folded. Instant (no animation) and
     * not remembered, so the active-tab marker can be positioned right after.
     * @return true if a section had to be opened
     */
    boolean reveal(Node node) {
        for (Section s : sections.values()) {
            if (contains(s, node)) {
                if (s.expanded || rail) return false;
                setExpanded(s.id, true, false, false);
                return true;
            }
        }
        return false;
    }

    /** Highlights the header of the section that holds the active page. */
    void markActive(Node activeButton) {
        for (Section s : sections.values()) {
            s.root.getStyleClass().remove("nav-section-active");
            if (activeButton != null && contains(s, activeButton)) s.root.getStyleClass().add("nav-section-active");
        }
    }

    /** Sums the given item badges onto the section header while the section is folded. */
    void bindBadge(NavSection id, Label... itemBadges) {
        Section s = sections.get(id);
        for (Label l : itemBadges) {
            if (l == null) continue;
            s.itemBadges.add(l);
            l.textProperty().addListener((o, a, b) -> refreshBadge(s));
            l.visibleProperty().addListener((o, a, b) -> refreshBadge(s));
        }
        refreshBadge(s);
    }

    /** Sidebar collapsed to icons: headers hidden, every item visible. Leaving it restores the fold state. */
    void setRailMode(boolean on) {
        if (rail == on) return;
        rail = on;
        for (Section s : sections.values()) {
            stopRunning(s);
            s.header.setVisible(!on);
            s.header.setManaged(!on);
            applyItemsInstant(s);
            refreshBadge(s);
        }
        onLayoutChanged.run();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void wire(Section s) {
        s.header.setFocusTraversable(true);
        s.header.setAccessibleRole(AccessibleRole.BUTTON);
        Tooltip.install(s.header, new Tooltip("Plegar / desplegar " + s.id.title));
        s.header.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) toggle(s.id);
        });
        s.header.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                toggle(s.id);
                e.consume();
            }
        });
    }

    private void setExpanded(NavSection id, boolean expanded, boolean persist, boolean animated) {
        Section s = sections.get(id);
        if (s.expanded == expanded) return;
        s.expanded = expanded;
        if (persist) prefs.putBoolean(key(id), expanded);
        stopRunning(s);
        updateHeaderState(s);
        if (rail) return;                                   // rail keeps every item on screen

        boolean canAnimate = animated && AnimationUtils.isEnabled() && s.items.getScene() != null;
        if (!canAnimate) {
            applyItemsInstant(s);
            onLayoutChanged.run();
            return;
        }
        if (expanded) animateOpen(s); else animateClose(s);
    }

    private void animateOpen(Section s) {
        s.items.setManaged(true);
        s.items.setVisible(true);
        double target = s.items.prefHeight(s.items.getWidth() > 0 ? s.items.getWidth() : -1);
        clipToBounds(s.items);
        s.items.setMinHeight(0);
        s.items.setMaxHeight(0);
        s.items.setOpacity(0);
        s.running = new Timeline(new KeyFrame(ANIM,
            new KeyValue(s.items.maxHeightProperty(), target, Interpolator.EASE_OUT),
            new KeyValue(s.items.opacityProperty(), 1, Interpolator.EASE_OUT)));
        s.running.setOnFinished(e -> {
            s.running = null;
            applyItemsInstant(s);
            onLayoutChanged.run();
        });
        s.running.play();
    }

    private void animateClose(Section s) {
        double from = s.items.getHeight();
        if (from <= 0) {
            applyItemsInstant(s);
            onLayoutChanged.run();
            return;
        }
        clipToBounds(s.items);
        s.items.setMinHeight(0);
        s.items.setMaxHeight(from);
        s.running = new Timeline(new KeyFrame(ANIM,
            new KeyValue(s.items.maxHeightProperty(), 0, Interpolator.EASE_IN),
            new KeyValue(s.items.opacityProperty(), 0, Interpolator.EASE_IN)));
        s.running.setOnFinished(e -> {
            s.running = null;
            applyItemsInstant(s);
            onLayoutChanged.run();
        });
        s.running.play();
    }

    private static void clipToBounds(VBox box) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(box.widthProperty());
        clip.heightProperty().bind(box.heightProperty());
        box.setClip(clip);
    }

    private void stopRunning(Section s) {
        if (s.running != null) {
            s.running.stop();
            s.running = null;
        }
    }

    /** Puts the items box in its final state for the current fold/rail state, clearing any animation leftovers. */
    private void applyItemsInstant(Section s) {
        boolean show = rail || s.expanded;
        s.items.setVisible(show);
        s.items.setManaged(show);
        s.items.setOpacity(1);
        s.items.setMinHeight(Region.USE_COMPUTED_SIZE);
        s.items.setMaxHeight(Region.USE_COMPUTED_SIZE);
        s.items.setClip(null);
    }

    private void updateHeaderState(Section s) {
        if (s.chevron != null) s.chevron.setIconLiteral(s.expanded ? CHEVRON_OPEN : CHEVRON_CLOSED);
        s.header.setAccessibleText(s.id.title + (s.expanded ? ", expandida" : ", contraída"));
        refreshBadge(s);
    }

    private void refreshBadge(Section s) {
        if (s.badge == null) return;
        int sum = 0;
        boolean plus = false;
        for (Label l : s.itemBadges) {
            if (!l.isVisible()) continue;
            String text = l.getText() == null ? "" : l.getText();
            plus |= text.contains("+");
            String digits = text.replaceAll("\\D", "");
            if (!digits.isEmpty()) sum += Integer.parseInt(digits);
        }
        boolean show = !s.expanded && !rail && sum > 0;
        s.badge.setText(sum + (plus ? "+" : ""));
        s.badge.setVisible(show);
        s.badge.setManaged(show);
    }

    private static boolean contains(Section s, Node node) {
        for (Node p = node; p != null; p = p.getParent()) {
            if (p == s.items) return true;
        }
        return false;
    }

    private static String key(NavSection id) {
        return "open." + id.name();
    }
}
