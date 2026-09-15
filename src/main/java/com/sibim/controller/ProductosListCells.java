package com.sibim.controller;

import com.sibim.model.Categoria;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import org.kordamp.ikonli.javafx.FontIcon;

class ProductosListCells {

    static ListCell<Categoria> categoriaListCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Categoria c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText("Todas las categorías"); setGraphic(null);
                    return;
                }
                String icono = c.getIcono();
                if (icono != null && !icono.isBlank()) {
                    Label badge = new Label(icono + "  " + c.getNombre());
                    badge.getStyleClass().add("combo-cell-label");
                    setGraphic(badge); setText(null);
                } else {
                    setText(c.getNombre()); setGraphic(null);
                }
            }
        };
    }

    static ListCell<String> areaListCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String area, boolean empty) {
                super.updateItem(area, empty);
                if (empty || area == null) {
                    setText("Todas las áreas"); setGraphic(null);
                    return;
                }
                String icon = areaIcon(area);
                FontIcon fi = new FontIcon(icon);
                fi.setIconSize(13);
                fi.getStyleClass().add("area-filter-icon");
                Label lbl = new Label("  " + area, fi);
                lbl.getStyleClass().add("combo-cell-label");
                setGraphic(lbl); setText(null);
            }
        };
    }

    static String areaIcon(String area) {
        if (area == null) return "mdi2o-office-building-outline";
        String lo = area.toLowerCase();
        if (lo.startsWith("secretar")) return "mdi2b-briefcase-outline";
        if (lo.startsWith("despacho") || lo.startsWith("presidencia")) return "mdi2s-star-outline";
        if (lo.contains("recursos humanos")) return "mdi2a-account-group-outline";
        if (lo.contains("tecnolog")) return "mdi2m-monitor-multiple";
        if (lo.contains("seguridad")) return "mdi2s-shield-outline";
        if (lo.contains("obras") || lo.contains("servicio")) return "mdi2w-wrench-outline";
        if (lo.contains("finanz") || lo.contains("tesorer") || lo.contains("contab") || lo.contains("presupuest")) return "mdi2c-currency-usd";
        if (lo.contains("bienes")) return "mdi2p-package-variant";
        if (lo.contains("juridic") || lo.contains("contralo")) return "mdi2s-scale-balance";
        if (lo.contains("bienestar") || lo.contains("social") || lo.contains("salud")) return "mdi2h-heart-outline";
        if (lo.contains("educac") || lo.contains("cultura")) return "mdi2b-book-outline";
        if (lo.contains("deporte")) return "mdi2s-soccer";
        if (lo.contains("turismo") || lo.contains("economic")) return "mdi2c-chart-line";
        if (lo.contains("transparencia") || lo.contains("acceso")) return "mdi2e-eye-outline";
        if (lo.contains("comunicac") || lo.contains("marketing")) return "mdi2m-microphone-outline";
        if (lo.contains("planeac") || lo.contains("evaluac")) return "mdi2c-clipboard-text-outline";
        if (lo.contains("indigena") || lo.contains("pueblos")) return "mdi2l-leaf";
        if (lo.startsWith("direcci")) return "mdi2f-folder-outline";
        if (lo.contains("unidad") || lo.contains("coordinac") || lo.contains("subdi")) return "mdi2t-text-box-outline";
        return "mdi2o-office-building-outline";
    }
}
