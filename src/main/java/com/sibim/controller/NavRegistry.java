package com.sibim.controller;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Every sidebar destination in one place, with the lookups and installers that
 * used to be hand-written (and kept in sync by hand) in MainController.
 *
 * Construction validates what previously nobody checked: a view, a button or a
 * keyboard shortcut used twice is a bug, and it now fails at startup instead of
 * silently shadowing another item.
 */
final class NavRegistry {

    private final List<NavItem> items;
    private final Map<String, NavItem> byView = new LinkedHashMap<>();
    private final Map<Button, NavItem> byButton = new IdentityHashMap<>();

    NavRegistry(List<NavItem> items) {
        Set<KeyCombination> accelerators = new HashSet<>();
        for (NavItem it : items) {
            if (byView.putIfAbsent(it.view(), it) != null)
                throw new IllegalArgumentException("Vista repetida en la navegación: " + it.view());
            if (byButton.put(it.button(), it) != null)
                throw new IllegalArgumentException("El mismo botón está registrado dos veces: " + it.view());
            if (it.accelerator() != null && !accelerators.add(it.accelerator()))
                throw new IllegalArgumentException("Atajo de teclado repetido (" + it.shortcutText() + "): " + it.view());
        }
        this.items = List.copyOf(items);
    }

    List<NavItem> items() { return items; }

    Optional<NavItem> byView(String view) { return Optional.ofNullable(byView.get(view)); }

    Optional<NavItem> byButton(Button button) { return Optional.ofNullable(byButton.get(button)); }

    /** Buttons in display order — for hover effects and collapse-to-icons. */
    List<Button> buttons() {
        List<Button> out = new ArrayList<>(items.size());
        for (NavItem it : items) out.add(it.button());
        return out;
    }

    List<NavItem> inSection(NavSection section) {
        return items.stream().filter(it -> it.section() == section).toList();
    }

    /** Registers every item's shortcut on the scene; admin-only items are ignored for non-admins. */
    void installAccelerators(Scene scene, BooleanSupplier isAdmin) {
        var accelerators = scene.getAccelerators();
        for (NavItem it : items) {
            if (it.accelerator() == null) continue;
            accelerators.put(it.accelerator(), () -> {
                if (it.adminOnly() && !isAdmin.getAsBoolean()) return;
                it.run();
            });
        }
    }

    /** Command-palette entries, in sidebar order. */
    List<SearchPaletteDialog.NavEntry> paletteEntries(boolean admin) {
        List<SearchPaletteDialog.NavEntry> out = new ArrayList<>();
        for (NavItem it : items) {
            if (!it.inPalette() || (it.adminOnly() && !admin)) continue;
            out.add(new SearchPaletteDialog.NavEntry(it.iconLiteral(), it.label(), it.shortcutText(), it::run));
        }
        return out;
    }

    /** Tooltip text comes from the FXML; this only applies the shared show/hide timing. */
    void tuneTooltips(Duration showDelay, Duration hideDelay) {
        for (NavItem it : items) {
            Tooltip tip = it.button().getTooltip();
            if (tip == null) continue;
            tip.setShowDelay(showDelay);
            tip.setHideDelay(hideDelay);
        }
    }
}
