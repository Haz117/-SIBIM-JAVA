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
 * the caller's áreas in organigrama order, each with its icon in the list;
 * hidden for an account that sees only one área. Since 5-nov-2026 one can type
 * in it to find an área (FiltroEscribible); empty is "Todas las áreas" (null).
 */
public final class FiltroArea {

    private FiltroArea() {}

    public static void configurar(ComboBox<String> combo) {
        combo.getItems().setAll(Areas.getAllAreaNames().stream().filter(SessionManager::isAreaAccessible).toList());
        conIconos(combo);
        com.sibim.util.FiltroEscribible.instalar(combo, "Todas las áreas");
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
