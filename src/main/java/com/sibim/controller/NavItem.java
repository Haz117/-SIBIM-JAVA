package com.sibim.controller;

import javafx.scene.control.Button;
import javafx.scene.input.KeyCombination;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;

/**
 * One sidebar destination. This is the single description of "a place you can go":
 * before it existed the same 15 destinations were spelled out separately as fields,
 * one-line handlers, tooltip calls, hover lists, two {@code switch}es, keyboard
 * accelerators and command-palette entries.
 *
 * The button (and therefore its label, icon and tooltip) still comes from main.fxml,
 * so a designer keeps editing those in the FXML; everything behavioural lives here.
 *
 * @param view         view name — the {@code /fxml/<view>.fxml} it loads, or an id for an action item
 * @param section      accordion group the button lives in
 * @param button       the FXML button
 * @param accelerator  global keyboard shortcut, or null
 * @param paletteLabel label shown in the command palette (defaults to the button text)
 * @param adminOnly    hidden from the palette and ignored by the accelerator for non-admins
 * @param inPalette    whether it is offered in the command palette
 * @param action       what selecting it does (navigate, or open a dialog for action items)
 */
record NavItem(String view, NavSection section, Button button, KeyCombination accelerator,
               String paletteLabel, boolean adminOnly, boolean inPalette, Runnable action) {

    NavItem {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(action, "action");
    }

    static NavItem of(String view, NavSection section, Button button, Runnable action, KeyCombination accelerator) {
        return new NavItem(view, section, button, accelerator, null, false, true, action);
    }

    NavItem withPaletteLabel(String label) {
        return new NavItem(view, section, button, accelerator, label, adminOnly, inPalette, action);
    }

    NavItem restrictedToAdmin() {
        return new NavItem(view, section, button, accelerator, paletteLabel, true, inPalette, action);
    }

    NavItem hiddenFromPalette() {
        return new NavItem(view, section, button, accelerator, paletteLabel, adminOnly, false, action);
    }

    /** Palette label, falling back to the button text from the FXML. */
    String label() {
        return paletteLabel != null ? paletteLabel : button.getText();
    }

    /** Icon literal taken from the FXML button, so sidebar and palette can never disagree. */
    String iconLiteral() {
        return button.getGraphic() instanceof FontIcon fi ? fi.getIconLiteral() : null;
    }

    /** "Ctrl+3", "Ctrl+Alt+G" … or an empty string when there is no shortcut. */
    String shortcutText() {
        return accelerator != null ? accelerator.getDisplayText() : "";
    }

    void run() {
        action.run();
    }
}
