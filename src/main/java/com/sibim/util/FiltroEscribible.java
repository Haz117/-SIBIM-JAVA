package com.sibim.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A filter combo you can type in (5-nov-2026: "que se pueda escribir en el
 * input para buscar más rápido", above all for people): the list narrows to
 * what is typed — every word, in any order, without accents —, Enter takes
 * the first match, and an empty field means "all" (its prompt says so). The
 * value is null for "all", as the screens already read it.
 *
 * <p>The options live here, not in {@code getItems()} (which shows only the
 * matches while typing): screens reload them with {@link #opciones(ComboBox, List)}
 * and look them up with {@link #opciones(ComboBox)}.
 */
public final class FiltroEscribible {

    private static final String CLAVE = "sibim.filtro.opciones";

    private FiltroEscribible() {}

    /** @param texto how an option is written (and searched)
     *  @param todos the prompt of the empty field: "Todas las áreas" */
    public static <T> void instalar(ComboBox<T> combo, Function<T, String> texto, String todos) {
        List<T> actuales = new ArrayList<>(combo.getItems().stream().filter(Objects::nonNull).toList());
        if (opcionesDe(combo) != null) { opciones(combo, actuales); return; }
        List<T> opciones = new ArrayList<>(actuales);
        combo.getProperties().put(CLAVE, opciones);
        T valor = combo.getValue();
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(T t) { return t == null ? "" : texto.apply(t); }
            @Override public T fromString(String s) {
                if (s == null || s.isBlank()) return null;
                String buscado = s.trim();
                return opciones.stream().filter(o -> texto.apply(o).equalsIgnoreCase(buscado)).findFirst().orElse(null);
            }
        });
        combo.setPromptText(todos);
        DialogUtil.makeFilterable(combo, opciones, t -> t == null ? "" : texto.apply(t));
        if (valor != null && opciones.contains(valor)) combo.setValue(valor);
        // Once the combo is on screen its skin ties the field's prompt to the
        // combo's (set above); setting it then throws "A bound value cannot be set".
        if (!combo.getEditor().promptTextProperty().isBound()) combo.getEditor().setPromptText(todos);
    }

    /** The same for a filter of names. */
    public static void instalar(ComboBox<String> combo, String todos) {
        instalar(combo, s -> s, todos);
    }

    /** New options (the data was reloaded); the one chosen stays if it is still there. */
    public static <T> void opciones(ComboBox<T> combo, List<T> nuevas) {
        List<T> opciones = opcionesDe(combo);
        if (opciones == null) { combo.getItems().setAll(nuevas); return; }
        T valor = combo.getValue();
        opciones.clear();
        nuevas.stream().filter(Objects::nonNull).distinct().forEach(opciones::add);
        if (!combo.isShowing()) combo.setItems(FXCollections.observableArrayList(opciones));
        if (valor != null && opciones.contains(valor)) {
            combo.setValue(valor);
        } else if (valor != null) {
            // The field first: with its text still there the picker would take the old option back.
            combo.getEditor().setText("");
            combo.setValue(null);
        }
    }

    /** Every option, whatever the field is showing while someone types. */
    public static <T> List<T> opciones(ComboBox<T> combo) {
        List<T> opciones = opcionesDe(combo);
        return opciones != null ? List.copyOf(opciones) : combo.getItems().stream().filter(Objects::nonNull).toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> opcionesDe(ComboBox<T> combo) {
        return (List<T>) combo.getProperties().get(CLAVE);
    }

    /**
     * For an editable combo that also takes a name not in its list (a new
     * person): while typing, the list shows only the names that match, so the
     * one wanted is a click away instead of a scroll. What was typed stays.
     */
    public static void sugerirAlEscribir(ComboBox<String> combo) {
        List<String> todos = new ArrayList<>(combo.getItems());
        combo.setVisibleRowCount(9);
        boolean[] propio = { false };
        combo.getEditor().textProperty().addListener((o, antes, ahora) -> {
            if (propio[0]) return;
            Platform.runLater(() -> {
                TextField editor = combo.getEditor();
                String texto = editor.getText();
                int caret = editor.getCaretPosition();
                List<String> coinciden = DialogUtil.coincidencias(todos, s -> s, texto);
                if (coinciden.equals(combo.getItems())) return;
                propio[0] = true;
                combo.setItems(FXCollections.observableArrayList(coinciden));
                // Replacing the items can rewrite the field: what was typed goes back.
                if (!Objects.equals(editor.getText(), texto)) editor.setText(texto);
                editor.positionCaret(Math.min(caret, texto == null ? 0 : texto.length()));
                propio[0] = false;
                boolean escribiendo = combo.getScene() != null && combo.getScene().getFocusOwner() == editor;
                if (escribiendo && !coinciden.isEmpty() && texto != null && !texto.isBlank()) combo.show();
                else if (coinciden.isEmpty()) combo.hide();
            });
        });
    }
}
