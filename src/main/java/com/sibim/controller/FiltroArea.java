package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.session.SessionManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Set;

/**
 * The área filter the way Bienes has it, for every screen that filters by área:
 * "Todas las áreas" first (the null value), then the caller's áreas in
 * organigrama order, each with its icon; hidden for an account that sees only
 * one área. The filters added later used an editable picker without icons and
 * looked out of place next to the other combos.
 */
public final class FiltroArea {

    private FiltroArea() {}

    public static void configurar(ComboBox<String> combo) {
        combo.getItems().setAll((String) null);
        Areas.getAllAreaNames().stream().filter(SessionManager::isAreaAccessible).forEach(combo.getItems()::add);
        combo.setConverter(new javafx.util.StringConverter<>() {
            public String toString(String a) { return a == null ? "Todas las áreas" : a; }
            public String fromString(String s) { return null; }
        });
        combo.setCellFactory(lv -> ProductosListCells.areaListCell());
        combo.setButtonCell(ProductosListCells.areaListCell());
        combo.setPromptText("Todas las áreas");
        combo.setVisibleRowCount(12);
        Set<String> mias = SessionManager.getAccessibleAreas();
        if (mias != null && mias.size() <= 1) { combo.setVisible(false); combo.setManaged(false); }
    }

    /** The área's icon in the list of a type-to-search área picker in a form.
     *  Call before DialogUtil.makeFilterable, which keeps a cell factory already set. */
    public static void conIconos(ComboBox<String> combo) {
        combo.setCellFactory(lv -> {
            ListCell<String> celda = new ListCell<>() {
                @Override protected void updateItem(String area, boolean empty) {
                    super.updateItem(area, empty);
                    setText(empty || area == null ? null : area);
                    setGraphic(empty || area == null ? null : icono(area));
                }
            };
            celda.prefWidthProperty().bind(combo.widthProperty().subtract(24));
            celda.setTextOverrun(OverrunStyle.ELLIPSIS);
            celda.setGraphicTextGap(8);
            return celda;
        });
    }

    /** The área's icon, as in the Bienes filter. */
    public static FontIcon icono(String area) {
        FontIcon fi = new FontIcon(ProductosListCells.areaIcon(area));
        fi.setIconSize(13);
        fi.getStyleClass().add("area-filter-icon");
        return fi;
    }
}
