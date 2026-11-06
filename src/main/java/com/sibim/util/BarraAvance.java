package com.sibim.util;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** A thin progress bar in the institutional green (avance of the áreas). The theme's
 *  ProgressBar only takes its own accent color and, in some containers, drew nothing. */
public final class BarraAvance extends StackPane {

    private final DoubleProperty avance = new SimpleDoubleProperty(0);

    public BarraAvance(double inicial) {
        Region relleno = new Region();
        relleno.getStyleClass().add("avance-relleno");
        getStyleClass().add("avance-pista");
        setAlignment(Pos.CENTER_LEFT);
        relleno.maxWidthProperty().bind(widthProperty().multiply(avance));
        relleno.prefWidthProperty().bind(relleno.maxWidthProperty());
        relleno.setMinWidth(0);
        getChildren().add(relleno);
        setMaxWidth(Double.MAX_VALUE);
        setAvance(inicial);
    }

    /** 0..1 (anything outside is clamped). */
    public void setAvance(double valor) {
        avance.set(Double.isNaN(valor) ? 0 : Math.max(0, Math.min(1, valor)));
    }

    public double getAvance() { return avance.get(); }
}
