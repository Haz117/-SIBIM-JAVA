package com.sibim.util;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * A text field that can also be filled from a list, with its button beside it (18-nov-2026:
 * the tipo de bien was written «MUEBLE», «mueble», «MUBLE», «MUELE» and «MUEBLES», and a
 * color in 7 bienes). Choosing writes it in the field, which can still be typed; Alt+↓ opens
 * the list from the field. The button is off while the field can't be changed or the list is
 * empty.
 */
public final class ListaParaElegir {

    private ListaParaElegir() {}

    /** The button for {@code campo}, empty until {@link #opciones} fills it.
     *  @param que what is chosen, for the screen reader («el tipo de bien») */
    public static MenuButton boton(TextField campo, String que) {
        MenuButton b = new MenuButton();
        b.setGraphic(new FontIcon("mdi2f-format-list-bulleted"));
        b.getStyleClass().add("lista-para-elegir");
        b.setTooltip(new Tooltip("Elegir de la lista (Alt+↓)"));
        b.setAccessibleText("Elegir " + que + " de la lista");
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setMaxHeight(Double.MAX_VALUE);
        b.disableProperty().bind(campo.disabledProperty().or(campo.editableProperty().not())
            .or(Bindings.isEmpty(b.getItems())));
        campo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isAltDown() && e.getCode() == KeyCode.DOWN && !b.isDisabled()) {
                b.show();
                e.consume();
            }
        });
        return b;
    }

    /** What the list offers, in this order. */
    public static void opciones(MenuButton b, TextField campo, List<String> opciones) {
        List<MenuItem> items = new ArrayList<>();
        for (String o : opciones) {
            MenuItem mi = new MenuItem(o);
            mi.setMnemonicParsing(false);
            mi.setOnAction(e -> {
                campo.setText(o);
                campo.requestFocus();
                campo.positionCaret(o.length());
            });
            items.add(mi);
        }
        b.getItems().setAll(items);
    }

    /** The field with its button at the right, as wide as the field was. */
    public static HBox junto(TextField campo, MenuButton b) {
        HBox h = new HBox(6, campo, b);
        h.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(campo, Priority.ALWAYS);
        campo.setMaxWidth(Double.MAX_VALUE);
        return h;
    }
}
