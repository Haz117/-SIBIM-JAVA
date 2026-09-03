package com.sibim.util;

import javafx.animation.PauseTransition;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class SearchUtils {

    private static final int HISTORY_MAX = 5;

    private SearchUtils() {}

    /**
     * Wires a debounced text listener on {@code field}: {@code onSearch} runs {@code millis}
     * after the user stops typing, instead of on every keystroke.
     */
    public static void debounce(TextField field, int millis, Consumer<String> onSearch) {
        PauseTransition pause = new PauseTransition(Duration.millis(millis));
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            pause.setOnFinished(e -> onSearch.accept(newVal));
            pause.playFromStart();
        });
    }

    /**
     * Attaches search-history behaviour to {@code field}.
     * On focus, shows a context menu with the last {@value #HISTORY_MAX} searches.
     * When Enter is pressed or focus leaves with a non-blank query, the term is
     * prepended to the history and persisted via Preferences.
     *
     * @param prefsKey  Preferences node path (e.g. "sibim/search-history/productos")
     * @param field     The TextField to instrument
     * @param onPick    Callback fired when the user selects a history entry (may be null)
     */
    public static void setupSearchHistory(String prefsKey, TextField field, Runnable onPick) {
        Preferences prefs = Preferences.userRoot().node(prefsKey);
        ContextMenu histMenu = new ContextMenu();
        histMenu.getStyleClass().add("search-history-menu");

        Runnable save = () -> {
            String t = field.getText().trim();
            if (!t.isBlank()) saveHistory(prefs, t);
        };

        field.setOnAction(e -> { save.run(); histMenu.hide(); });
        field.focusedProperty().addListener((obs, was, now) -> {
            if (!was && now) {
                List<String> history = loadHistory(prefs);
                if (history.isEmpty()) return;
                histMenu.getItems().clear();
                MenuItem header = new MenuItem("Búsquedas recientes");
                header.setDisable(true);
                header.getStyleClass().add("history-menu-header");
                histMenu.getItems().add(header);
                histMenu.getItems().add(new SeparatorMenuItem());
                for (String h : history) {
                    MenuItem item = new MenuItem(h);
                    FontIcon ico = new FontIcon("mdi2h-history");
                    ico.getStyleClass().add("history-icon");
                    item.setGraphic(ico);
                    item.setOnAction(ev -> {
                        field.setText(h);
                        field.positionCaret(h.length());
                        histMenu.hide();
                        if (onPick != null) onPick.run();
                    });
                    histMenu.getItems().add(item);
                }
                histMenu.show(field, javafx.geometry.Side.BOTTOM, 0, 0);
            } else if (was && !now) {
                save.run();
                histMenu.hide();
            }
        });
    }

    private static List<String> loadHistory(Preferences prefs) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < HISTORY_MAX; i++) {
            String v = prefs.get("h" + i, "");
            if (!v.isBlank()) result.add(v);
        }
        return result;
    }

    private static void saveHistory(Preferences prefs, String term) {
        List<String> list = loadHistory(prefs);
        list.remove(term);
        list.add(0, term);
        if (list.size() > HISTORY_MAX) list = list.subList(0, HISTORY_MAX);
        for (int i = 0; i < HISTORY_MAX; i++) {
            if (i < list.size()) prefs.put("h" + i, list.get(i));
            else prefs.remove("h" + i);
        }
    }
}
