package com.sibim.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * The rows and scroll bars of a rounded table follow its bottom corners.
 * A TableView's -fx-background-radius only rounds its own background: its rows
 * are rectangles drawn on top, so a tinted last row or the scroll bar ended in
 * a square corner inside the rounded border. The body (the VirtualFlow below
 * the header) is clipped instead of the table itself, which keeps the table's
 * drop shadow.
 */
public final class TablaRedondeada {

    private static final String YA = "sibim.tablaRedondeada";

    private TablaRedondeada() {}

    /** Every {@code .data-table} under {@code raiz}; calling it again is harmless. */
    public static void instalarTodas(Node raiz) {
        if (raiz == null) return;
        if (raiz instanceof TableView<?> t) instalar(t);
        if (raiz instanceof Parent p)
            for (Node n : p.lookupAll(".data-table"))
                if (n instanceof TableView<?> t) instalar(t);
    }

    public static void instalar(TableView<?> tabla) {
        if (tabla.getProperties().putIfAbsent(YA, Boolean.TRUE) != null) return;
        tabla.skinProperty().addListener((o, antes, ahora) -> recortar(tabla));
        tabla.backgroundProperty().addListener((o, antes, ahora) -> recortar(tabla));
        recortar(tabla);
    }

    private static void recortar(TableView<?> tabla) {
        if (!(tabla.lookup(".virtual-flow") instanceof Region cuerpo)) return;
        if (cuerpo.getProperties().putIfAbsent(YA, Boolean.TRUE) == null) {
            cuerpo.widthProperty().addListener((o, a, b) -> aplicar(tabla, cuerpo));
            cuerpo.heightProperty().addListener((o, a, b) -> aplicar(tabla, cuerpo));
        }
        aplicar(tabla, cuerpo);
    }

    private static void aplicar(TableView<?> tabla, Region cuerpo) {
        // Inside the 1 px border, so the curve runs just within it.
        double radio = Math.max(0, radioInferior(tabla.getBackground()) - 1);
        double w = cuerpo.getWidth(), h = cuerpo.getHeight();
        cuerpo.setClip(radio <= 0 || w <= 0 || h <= 0 ? null : forma(w, h, radio));
    }

    /** Square at the top (it meets the header), rounded at the bottom. */
    static Shape forma(double w, double h, double radio) {
        double r = Math.min(radio, Math.min(w, h) / 2);
        Rectangle arriba = new Rectangle(0, 0, w, Math.max(0, h - r));
        Rectangle redondo = new Rectangle(0, 0, w, h);
        redondo.setArcWidth(2 * r);
        redondo.setArcHeight(2 * r);
        return Shape.union(arriba, redondo);
    }

    static double radioInferior(Background fondo) {
        if (fondo == null || fondo.getFills().isEmpty()) return 0;
        var radios = fondo.getFills().get(0).getRadii();
        return Math.max(radios.getBottomLeftHorizontalRadius(), radios.getBottomRightHorizontalRadius());
    }
}
