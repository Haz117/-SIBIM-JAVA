package com.sibim.util;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;

/** Scrolls a long page to one of its sections (index chips, summary cards). */
public final class Desplazamiento {

    private Desplazamiento() {}

    /** Brings {@code destino} to the top of the ScrollPane that contains it.
     *  Resolved when called: before a page is shown its ScrollPane has no skin
     *  and its content can't be reached through getParent(). */
    public static void irA(Node destino) {
        if (destino == null) return;
        ScrollPane scroll = null;
        for (Parent p = destino.getParent(); p != null; p = p.getParent())
            if (p instanceof ScrollPane sp) { scroll = sp; break; }
        if (scroll == null || scroll.getContent() == null) return;
        Node contenido = scroll.getContent();
        double desplazable = contenido.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        if (desplazable <= 0) return;
        Point2D enEscena = destino.localToScene(0, 0);
        Point2D enContenido = contenido.sceneToLocal(enEscena);
        scroll.setVvalue(Math.max(0, Math.min(1, (enContenido.getY() - 12) / desplazable)));
        destino.requestFocus();
    }
}
