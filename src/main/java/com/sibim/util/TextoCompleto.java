package com.sibim.util;

import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * A text cut with «…» says itself whole when the mouse is on it (18-nov-2026, every screen at
 * 1366 px with the real inventory: Bienes cut names, áreas, categorías and resguardantes,
 * Movimientos the bien and the motivo, Depreciación and Auditoría their names and details,
 * Completar datos and Nuevo resguardo the áreas). Only while it is cut, and never over a
 * tooltip the screen already gave it. One mouse filter per window does it for every label,
 * button and table cell in it, also those made later.
 */
public final class TextoCompleto {

    /** Where JavaFX keeps a node's installed tooltip, also one put with Tooltip.install. */
    private static final String TOOLTIP_INSTALADO = "javafx.scene.control.Tooltip";

    private TextoCompleto() {}

    /** On {@code raiz} (a window's root or a dialog's pane), once. */
    public static void instalar(Node raiz) {
        if (raiz == null || raiz.getProperties().putIfAbsent(TextoCompleto.class, Boolean.TRUE) != null) return;
        raiz.addEventFilter(MouseEvent.MOUSE_MOVED, TextoCompleto::alMover);
    }

    static void alMover(MouseEvent e) {
        Node n = e.getPickResult() == null ? null : e.getPickResult().getIntersectedNode();
        for (int i = 0; n != null && i < 6; i++, n = n.getParent()) {
            if (n instanceof Labeled l) {
                revisar(l);
                return;
            }
        }
    }

    /** Gives {@code l} its whole text as tooltip while it is cut, and takes it away when not. */
    static void revisar(Labeled l) {
        Tooltip t = l.getTooltip();
        boolean nuestro = t != null && t.getProperties().containsKey(TextoCompleto.class);
        if (!nuestro && (t != null || l.getProperties().get(TOOLTIP_INSTALADO) != null)) return;
        String completo = l.getText();
        if (completo != null && !completo.isBlank() && cortado(l)) {
            if (nuestro) {
                if (!completo.equals(t.getText())) t.setText(completo);
            } else {
                Tooltip nuevo = new Tooltip(completo);
                nuevo.getProperties().put(TextoCompleto.class, Boolean.TRUE);
                nuevo.setWrapText(true);
                nuevo.setMaxWidth(480);
                nuevo.setShowDelay(Duration.millis(350));
                l.setTooltip(nuevo);
            }
        } else if (nuestro) {
            l.setTooltip(null);
        }
    }

    /** Whether what is shown is shorter than its text (the skin puts «…» in its Text). */
    static boolean cortado(Labeled l) {
        for (Node c : l.getChildrenUnmodifiable())
            if (c instanceof Text tx) {
                String visto = tx.getText();
                return visto != null && !visto.equals(l.getText()) && (visto.endsWith("…") || visto.endsWith("..."));
            }
        return false;
    }
}
