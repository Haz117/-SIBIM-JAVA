package com.sibim.util;

import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

import java.util.List;

/** Attaches a live-filter suggestion dropdown to a TextField.
 *  Matches on substring (case-insensitive). Empty suggestion list is a no-op. */
public final class AutocompleteUtil {

    private AutocompleteUtil() {}

    public static void attach(TextField tf, List<String> suggestions) {
        if (tf == null || suggestions == null || suggestions.isEmpty()) return;

        ContextMenu popup = new ContextMenu();
        popup.setAutoHide(true);

        tf.textProperty().addListener((obs, oldVal, newVal) -> {
            popup.hide();
            if (newVal == null || newVal.isBlank()) return;
            String q = newVal.toLowerCase();
            List<MenuItem> items = suggestions.stream()
                .filter(s -> s.toLowerCase().contains(q) && !s.equalsIgnoreCase(newVal))
                .limit(8)
                .map(s -> {
                    MenuItem mi = new MenuItem(s);
                    mi.setOnAction(e -> { tf.setText(s); tf.positionCaret(s.length()); });
                    return mi;
                })
                .toList();
            if (items.isEmpty()) return;
            popup.getItems().setAll(items);
            if (!popup.isShowing()) popup.show(tf, Side.BOTTOM, 0, 0);
        });

        tf.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) popup.hide();
        });
    }
}
