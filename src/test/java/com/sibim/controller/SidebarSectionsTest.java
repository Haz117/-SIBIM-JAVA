package com.sibim.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The accordion behind the sidebar. Animations are disabled (animate=false) so every state
 * change is synchronous and deterministic; preferences go to a throw-away node.
 */
class SidebarSectionsTest {

    private Preferences prefs;
    private VBox navRoot, opsRoot, ctrlRoot;
    private Button btnDash, btnMov, btnRes;
    private Label alertBadge, loanBadge;
    private SidebarSections sections;

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // toolkit already running
        }
    }

    @BeforeEach
    void setUp() {
        prefs = Preferences.userRoot().node("sibim/test/sidebar/" + UUID.randomUUID());
        btnDash = new Button("Dashboard");
        btnMov = new Button("Movimientos");
        btnRes = new Button("Resguardos");
        alertBadge = badge();
        loanBadge = badge();
        navRoot = section(btnDash);
        opsRoot = section(btnMov, alertBadge);
        ctrlRoot = section(btnRes, loanBadge);
        sections = build();
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        prefs.removeNode();
        // removing only the leaf would leave empty sibim/test/sidebar parents behind in the registry on every run
        Preferences.userRoot().node("sibim/test").removeNode();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Label badge() {
        Label l = new Label("0");
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    /** VBox[ header HBox(title, badge, chevron), items VBox(children…) ] — the shape main.fxml has. */
    private static VBox section(javafx.scene.Node... items) {
        Label title = new Label("TITULO");
        title.getStyleClass().add("nav-section-label");
        Label headerBadge = new Label();
        headerBadge.getStyleClass().add("nav-section-badge");
        headerBadge.setVisible(false);
        headerBadge.setManaged(false);
        FontIcon chevron = new FontIcon("mdi2c-chevron-down");
        HBox header = new HBox(title, headerBadge, chevron);
        header.getStyleClass().add("nav-section-header");
        VBox itemsBox = new VBox(items);
        itemsBox.getStyleClass().add("nav-section-items");
        VBox root = new VBox(header, itemsBox);
        root.getStyleClass().add("nav-section");
        return root;
    }

    private SidebarSections build() {
        Map<NavSection, VBox> roots = new EnumMap<>(NavSection.class);
        roots.put(NavSection.NAVEGACION, navRoot);
        roots.put(NavSection.OPERACIONES, opsRoot);
        roots.put(NavSection.CONTROL, ctrlRoot);
        return new SidebarSections(roots, prefs, false);
    }

    private static HBox header(VBox root) { return (HBox) root.getChildren().get(0); }
    private static VBox items(VBox root)  { return (VBox) root.getChildren().get(1); }
    private static Label headerBadge(VBox root) { return (Label) header(root).getChildren().get(1); }
    private static FontIcon chevron(VBox root)  { return (FontIcon) header(root).getChildren().get(2); }

    // ── initial state & persistence ──────────────────────────────────────────

    @Test
    void freshInstall_opensNavegacionAndOperaciones_only() {
        assertTrue(sections.isExpanded(NavSection.NAVEGACION));
        assertTrue(sections.isExpanded(NavSection.OPERACIONES));
        assertFalse(sections.isExpanded(NavSection.CONTROL));
        assertTrue(items(navRoot).isManaged());
        assertFalse(items(ctrlRoot).isManaged(), "a folded section takes no space");
        assertFalse(items(ctrlRoot).isVisible());
    }

    @Test
    void toggle_foldsAndUnfolds_updatesChevron_andRemembersTheChoice() {
        sections.toggle(NavSection.CONTROL);
        assertTrue(sections.isExpanded(NavSection.CONTROL));
        assertTrue(items(ctrlRoot).isManaged());
        assertEquals("mdi2c-chevron-down", chevron(ctrlRoot).getIconLiteral());

        sections.toggle(NavSection.NAVEGACION);
        assertFalse(items(navRoot).isManaged());
        assertEquals("mdi2c-chevron-right", chevron(navRoot).getIconLiteral());

        SidebarSections restarted = build();            // same preferences = same user, next launch
        assertTrue(restarted.isExpanded(NavSection.CONTROL), "manual choice survives a restart");
        assertFalse(restarted.isExpanded(NavSection.NAVEGACION));
    }

    // ── reveal / active highlight ────────────────────────────────────────────

    @Test
    void reveal_opensTheFoldedSectionOfAButton_withoutRememberingIt() {
        assertFalse(sections.isExpanded(NavSection.CONTROL));

        assertTrue(sections.reveal(btnRes), "the section had to be opened");

        assertTrue(sections.isExpanded(NavSection.CONTROL));
        assertTrue(items(ctrlRoot).isManaged());
        assertFalse(build().isExpanded(NavSection.CONTROL), "navigating must not change the saved layout");
    }

    @Test
    void reveal_isANoOp_whenAlreadyOpenOrUnknownNode() {
        assertFalse(sections.reveal(btnDash));
        assertFalse(sections.reveal(new Button("suelto")));
    }

    @Test
    void markActive_highlightsOnlyTheSectionThatHoldsTheButton() {
        sections.markActive(btnMov);
        assertTrue(opsRoot.getStyleClass().contains("nav-section-active"));
        assertFalse(navRoot.getStyleClass().contains("nav-section-active"));

        sections.markActive(btnDash);
        assertTrue(navRoot.getStyleClass().contains("nav-section-active"));
        assertFalse(opsRoot.getStyleClass().contains("nav-section-active"), "the previous highlight is cleared");

        sections.markActive(null);
        assertFalse(navRoot.getStyleClass().contains("nav-section-active"));
    }

    // ── aggregated badge ─────────────────────────────────────────────────────

    @Test
    void badge_showsTheSumOfItemBadges_onlyWhileFolded() {
        sections.bindBadge(NavSection.OPERACIONES, alertBadge);
        alertBadge.setText("17");
        alertBadge.setVisible(true);

        assertFalse(headerBadge(opsRoot).isVisible(), "an open section shows its own item badge instead");

        sections.toggle(NavSection.OPERACIONES);
        assertTrue(headerBadge(opsRoot).isVisible());
        assertEquals("17", headerBadge(opsRoot).getText());

        alertBadge.setText("3");
        assertEquals("3", headerBadge(opsRoot).getText(), "it follows the item badge live");

        alertBadge.setVisible(false);
        assertFalse(headerBadge(opsRoot).isVisible(), "no visible item badge, nothing to sum");
    }

    @Test
    void badge_sumsSeveralItems_andKeepsThePlusOfOverflowCounts() {
        Label second = badge();
        sections.bindBadge(NavSection.OPERACIONES, alertBadge, second);
        alertBadge.setText("4");
        alertBadge.setVisible(true);
        second.setText("99+");
        second.setVisible(true);

        sections.toggle(NavSection.OPERACIONES);

        assertEquals("103+", headerBadge(opsRoot).getText());
    }

    // ── rail mode (sidebar collapsed to icons) ───────────────────────────────

    @Test
    void railMode_showsEveryItemAndHidesHeaders_thenRestoresTheFoldState() {
        sections.bindBadge(NavSection.CONTROL, loanBadge);
        loanBadge.setText("2");
        loanBadge.setVisible(true);
        assertFalse(items(ctrlRoot).isManaged());

        sections.setRailMode(true);
        assertTrue(items(ctrlRoot).isManaged(), "icons must be reachable in the rail");
        assertFalse(header(ctrlRoot).isManaged(), "headers are hidden in the rail");
        assertFalse(headerBadge(ctrlRoot).isVisible());

        sections.setRailMode(false);
        assertFalse(items(ctrlRoot).isManaged(), "the fold state comes back");
        assertTrue(header(ctrlRoot).isManaged());
        assertTrue(headerBadge(ctrlRoot).isVisible());
    }

    @Test
    void reveal_doesNothingInRailMode_becauseEverythingIsAlreadyShown() {
        sections.setRailMode(true);
        assertFalse(sections.reveal(btnRes));
        assertFalse(sections.isExpanded(NavSection.CONTROL));
    }

    // ── keyboard / mouse on the header ───────────────────────────────────────

    @Test
    void headerKeys_enterAndSpaceToggle_otherKeysDont() {
        assertFalse(sections.isExpanded(NavSection.CONTROL));

        Event.fireEvent(header(ctrlRoot), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
        assertTrue(sections.isExpanded(NavSection.CONTROL));

        Event.fireEvent(header(ctrlRoot), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
        assertFalse(sections.isExpanded(NavSection.CONTROL));

        Event.fireEvent(header(ctrlRoot), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false));
        assertFalse(sections.isExpanded(NavSection.CONTROL));
    }

    @Test
    void headerIsAccessible_focusableAndAnnouncesItsState() {
        assertTrue(header(ctrlRoot).isFocusTraversable());
        assertTrue(header(ctrlRoot).getAccessibleText().contains("contraída"));
        sections.toggle(NavSection.CONTROL);
        assertTrue(header(ctrlRoot).getAccessibleText().contains("expandida"));
    }

    @Test
    void layoutCallback_firesAfterEveryStructuralChange() {
        int[] calls = { 0 };
        sections.setOnLayoutChanged(() -> calls[0]++);

        sections.toggle(NavSection.CONTROL);
        sections.reveal(btnRes);          // already open -> no change
        sections.setRailMode(true);
        sections.setRailMode(false);

        assertEquals(3, calls[0], "toggle + rail on + rail off; reveal of an open section changes nothing");
    }
}
