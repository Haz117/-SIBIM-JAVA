package com.sibim.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class EmptyStateUtil {

    public static VBox build(String iconCode, String title, String subtitle) {
        FontIcon icon = new FontIcon(iconCode);
        icon.getStyleClass().add("empty-state-icon");
        Label lTitle = new Label(title);
        lTitle.getStyleClass().add("empty-state-title");
        Label lSub = new Label(subtitle);
        lSub.getStyleClass().add("empty-state-subtitle");
        lSub.setWrapText(true);
        lSub.setMaxWidth(320);
        VBox box = new VBox(10, icon, lTitle, lSub);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    public static VBox buildSearch(String query) {
        return build("mdi2m-magnify-close",
            "Sin resultados para «" + query + "»",
            "Prueba con otro término de búsqueda");
    }

    public static VBox buildNoResults(String query, Runnable onClear) {
        String title = (query == null || query.isBlank())
            ? "Sin resultados" : "Sin resultados para «" + query + "»";
        VBox box = build("mdi2m-magnify-close", title, "Ningún registro coincide con el filtro activo");
        javafx.scene.control.Button btn = new javafx.scene.control.Button("Limpiar filtros");
        btn.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
        btn.getStyleClass().add("btn-ghost");
        btn.setOnAction(e -> onClear.run());
        box.getChildren().add(btn);
        return box;
    }
}
