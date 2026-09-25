package com.sibim.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NavRegistry replaces the ~10 hand-synchronised lists (handlers, tooltips, hover, two
 * switches, accelerators, palette) that described the sidebar destinations. These tests pin
 * the behaviour those lists had to keep in sync.
 */
class NavRegistryTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // toolkit already running (e.g. started by a TestFX class earlier in the suite)
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Button button(String text, String icon) {
        Button b = new Button(text);
        b.setGraphic(new FontIcon(icon));
        b.setTooltip(new Tooltip(text + " tip"));
        return b;
    }

    private static NavItem item(String view, NavSection section, Button b, List<String> log, KeyCode key,
                                KeyCombination.Modifier... mods) {
        KeyCombination.Modifier[] m = mods.length == 0 ? new KeyCombination.Modifier[] { KeyCombination.CONTROL_DOWN } : mods;
        return NavItem.of(view, section, b, () -> log.add(view), new KeyCodeCombination(key, m));
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void duplicateView_isRejected() {
        List<String> log = new ArrayList<>();
        NavItem a = item("productos", NavSection.NAVEGACION, button("A", "mdi2p-package-variant"), log, KeyCode.DIGIT1);
        NavItem b = item("productos", NavSection.NAVEGACION, button("B", "mdi2p-package-variant"), log, KeyCode.DIGIT2);
        var ex = assertThrows(IllegalArgumentException.class, () -> new NavRegistry(List.of(a, b)));
        assertTrue(ex.getMessage().contains("productos"));
    }

    @Test
    void sameButtonTwice_isRejected() {
        List<String> log = new ArrayList<>();
        Button shared = button("A", "mdi2p-package-variant");
        NavItem a = item("uno", NavSection.NAVEGACION, shared, log, KeyCode.DIGIT1);
        NavItem b = item("dos", NavSection.NAVEGACION, shared, log, KeyCode.DIGIT2);
        assertThrows(IllegalArgumentException.class, () -> new NavRegistry(List.of(a, b)));
    }

    @Test
    void duplicateShortcut_isRejected_soOneItemNeverShadowsAnother() {
        List<String> log = new ArrayList<>();
        NavItem a = item("uno", NavSection.NAVEGACION, button("A", "mdi2p-package-variant"), log, KeyCode.DIGIT1);
        NavItem b = item("dos", NavSection.OPERACIONES, button("B", "mdi2p-package-variant"), log, KeyCode.DIGIT1);
        var ex = assertThrows(IllegalArgumentException.class, () -> new NavRegistry(List.of(a, b)));
        assertTrue(ex.getMessage().contains("dos"));
    }

    // ── lookups ──────────────────────────────────────────────────────────────

    @Test
    void lookups_byViewAndByButton_andUnknownIsEmpty() {
        List<String> log = new ArrayList<>();
        Button bd = button("Dashboard", "mdi2v-view-dashboard");
        Button bp = button("Bienes", "mdi2p-package-variant");
        NavRegistry r = new NavRegistry(List.of(
            item("dashboard", NavSection.NAVEGACION, bd, log, KeyCode.DIGIT1),
            item("productos", NavSection.NAVEGACION, bp, log, KeyCode.DIGIT3)));

        assertSame(bp, r.byView("productos").orElseThrow().button());
        assertEquals("dashboard", r.byButton(bd).orElseThrow().view());
        assertTrue(r.byView("vista_inexistente").isEmpty());
        assertTrue(r.byButton(new Button("otro")).isEmpty());
    }

    @Test
    void buttonsAndSections_keepDisplayOrder() {
        List<String> log = new ArrayList<>();
        Button a = button("A", "mdi2p-package-variant"), b = button("B", "mdi2p-package-variant"),
               c = button("C", "mdi2p-package-variant");
        NavRegistry r = new NavRegistry(List.of(
            item("a", NavSection.NAVEGACION, a, log, KeyCode.DIGIT1),
            item("b", NavSection.OPERACIONES, b, log, KeyCode.DIGIT2),
            item("c", NavSection.NAVEGACION, c, log, KeyCode.DIGIT3)));

        assertEquals(List.of(a, b, c), r.buttons());
        assertEquals(List.of("a", "c"), r.inSection(NavSection.NAVEGACION).stream().map(NavItem::view).toList());
        assertEquals(List.of("b"), r.inSection(NavSection.OPERACIONES).stream().map(NavItem::view).toList());
        assertTrue(r.inSection(NavSection.SISTEMA).isEmpty());
    }

    // ── command palette ──────────────────────────────────────────────────────

    @Test
    void paletteEntries_followSidebarOrder_useButtonIconAndPaletteLabel_skipHiddenAndGateAdmin() {
        List<String> log = new ArrayList<>();
        NavRegistry r = new NavRegistry(List.of(
            item("dashboard", NavSection.NAVEGACION, button("Dashboard", "mdi2v-view-dashboard"), log, KeyCode.DIGIT1),
            item("productos", NavSection.NAVEGACION, button("Bienes", "mdi2p-package-variant"), log, KeyCode.DIGIT3)
                .withPaletteLabel("Bienes / Inventario"),
            item("conteo", NavSection.OPERACIONES, button("Conteo Físico", "mdi2c-clipboard-search-outline"), log,
                KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN).hiddenFromPalette(),
            item("auditoria", NavSection.SISTEMA, button("Auditoría", "mdi2s-shield-lock-outline"), log, KeyCode.DIGIT0)
                .restrictedToAdmin()));

        var user = r.paletteEntries(false);
        assertEquals(List.of("Dashboard", "Bienes / Inventario"), user.stream().map(e -> e.label()).toList());
        assertEquals("mdi2p-package-variant", user.get(1).icon(), "the icon comes from the FXML button, not a copy");
        assertEquals("Ctrl+3", user.get(1).shortcut());

        var admin = r.paletteEntries(true);
        assertEquals(List.of("Dashboard", "Bienes / Inventario", "Auditoría"), admin.stream().map(e -> e.label()).toList());

        admin.get(2).action().run();
        assertEquals(List.of("auditoria"), log, "selecting a palette entry runs the item's action");
    }

    // ── accelerators ─────────────────────────────────────────────────────────

    @Test
    void accelerators_runTheItemAction_andAdminOnlyIsGated() {
        List<String> log = new ArrayList<>();
        NavRegistry r = new NavRegistry(List.of(
            item("dashboard", NavSection.NAVEGACION, button("Dashboard", "mdi2v-view-dashboard"), log, KeyCode.DIGIT1),
            item("auditoria", NavSection.SISTEMA, button("Auditoría", "mdi2s-shield-lock-outline"), log, KeyCode.DIGIT0)
                .restrictedToAdmin()));
        Scene scene = new Scene(new Pane());
        boolean[] admin = { false };
        r.installAccelerators(scene, () -> admin[0]);

        var ctrl1 = new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN);
        var ctrl0 = new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN);
        scene.getAccelerators().get(ctrl1).run();
        scene.getAccelerators().get(ctrl0).run();
        assertEquals(List.of("dashboard"), log, "a non-admin cannot trigger the admin-only shortcut");

        admin[0] = true;
        scene.getAccelerators().get(ctrl0).run();
        assertEquals(List.of("dashboard", "auditoria"), log);
    }

    @Test
    void itemsWithoutAccelerator_areSkipped_andShortcutTextIsEmpty() {
        List<String> log = new ArrayList<>();
        NavItem noKey = NavItem.of("x", NavSection.SISTEMA, button("X", "mdi2c-cog-outline"), () -> log.add("x"), null);
        NavRegistry r = new NavRegistry(List.of(noKey));
        Scene scene = new Scene(new Pane());
        r.installAccelerators(scene, () -> true);
        assertTrue(scene.getAccelerators().isEmpty());
        assertEquals("", noKey.shortcutText());
    }

    // ── tooltips ─────────────────────────────────────────────────────────────

    @Test
    void tuneTooltips_appliesSharedTimingToEveryFxmlTooltip_andToleratesMissingOnes() {
        List<String> log = new ArrayList<>();
        Button withTip = button("A", "mdi2p-package-variant");
        Button noTip = new Button("B");
        NavRegistry r = new NavRegistry(List.of(
            item("a", NavSection.NAVEGACION, withTip, log, KeyCode.DIGIT1),
            NavItem.of("b", NavSection.NAVEGACION, noTip, () -> {}, null)));

        r.tuneTooltips(Duration.millis(700), Duration.millis(200));

        assertEquals(Duration.millis(700), withTip.getTooltip().getShowDelay());
        assertEquals(Duration.millis(200), withTip.getTooltip().getHideDelay());
        assertNull(noTip.getTooltip());
    }
}
