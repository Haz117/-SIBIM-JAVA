package com.sibim.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class EmptyStateUtil {

    public static VBox build(String iconCode, String title, String subtitle) {
        FontIcon icon = new FontIcon(iconCode);
        icon.getStyleClass().add("empty-state-icon");
        StackPane iconWrap = new StackPane(icon);
        iconWrap.getStyleClass().add("empty-state-icon-circle");
        // Stay a circle — the parent VBox would otherwise stretch it into a full-width pill.
        iconWrap.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label lTitle = new Label(title);
        lTitle.getStyleClass().add("empty-state-title");
        Label lSub = new Label(subtitle);
        lSub.getStyleClass().add("empty-state-subtitle");
        lSub.setWrapText(true);
        lSub.setMaxWidth(320);
        // Without it a table's placeholder gave the text one line and cut it with "…".
        lSub.setMinHeight(Region.USE_PREF_SIZE);
        lSub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox box = new VBox(14, iconWrap, lTitle, lSub);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        sinEmpujar(box, iconWrap);
        return box;
    }

    /** A table's placeholder used to ask for its whole height (307 px with the table's header):
     *  in Préstamos at 1366×689, with its summary shown and no préstamos, the screen grew past the
     *  window and the status bar went out of sight (27-nov-2026). Now it can get smaller than it
     *  would like: the circle with the icon goes first, and what still doesn't fit is cut. */
    private static void sinEmpujar(VBox box, Region icono) {
        box.setMinHeight(0);
        javafx.scene.shape.Rectangle recorte = new javafx.scene.shape.Rectangle();
        recorte.widthProperty().bind(box.widthProperty());
        recorte.heightProperty().bind(box.heightProperty());
        box.setClip(recorte);
        box.heightProperty().addListener((o, antes, alto) -> {
            double ancho = box.getWidth() > 0 ? box.getWidth() : 320;
            double sinIcono = box.getInsets().getTop() + box.getInsets().getBottom();
            int resto = 0;
            for (javafx.scene.Node c : box.getChildren())
                if (c != icono && c.isVisible()) { sinIcono += c.prefHeight(ancho); resto++; }
            sinIcono += box.getSpacing() * Math.max(0, resto - 1);
            // Only from the sizes, never from whether the icon is showing: no back and forth.
            boolean cabe = alto.doubleValue() >= sinIcono + box.getSpacing() + icono.prefHeight(-1);
            if (icono.isManaged() != cabe) { icono.setVisible(cabe); icono.setManaged(cabe); }
        });
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
