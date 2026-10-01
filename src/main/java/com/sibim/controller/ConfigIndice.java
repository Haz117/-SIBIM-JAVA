package com.sibim.controller;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Set;

/**
 * Configuración is one long page of sections (some added by code once their
 * data arrives). This puts an index of chips under the page header — one per
 * section, named after the section's own title — that scrolls straight to it,
 * so reaching "Respaldo" or "Usuarios" is a click instead of a long scroll.
 */
final class ConfigIndice {

    private ConfigIndice() {}

    private static final String ID = "configIndice";
    private static final Set<String> CLASES_TITULO = Set.of("card-title", "card-section-title", "dash-section-label");

    /** Builds (or rebuilds) the index for the page {@code enLaPagina} belongs to. */
    static void actualizar(Node enLaPagina) {
        if (enLaPagina == null || !(enLaPagina.getParent() instanceof VBox pagina)) return;
        FlowPane indice = new FlowPane(8, 8);
        indice.setId(ID);
        for (Node seccion : pagina.getChildren()) {
            if (ID.equals(seccion.getId()) || !seccion.isManaged()) continue;
            String titulo = tituloDe(seccion);
            if (titulo == null) continue;
            Button chip = new Button(titulo);
            chip.getStyleClass().add("btn-preset");
            chip.setOnAction(e -> irA(pagina, seccion));
            indice.getChildren().add(chip);
        }
        pagina.getChildren().removeIf(n -> ID.equals(n.getId()));
        if (indice.getChildren().size() >= 3) pagina.getChildren().add(Math.min(1, pagina.getChildren().size()), indice);
    }

    private static ScrollPane scrollDe(Node n) {
        for (Parent p = n.getParent(); p != null; p = p.getParent())
            if (p instanceof ScrollPane sp) return sp;
        return n.getScene() != null && n.getScene().getRoot() instanceof ScrollPane sp ? sp : null;
    }

    /** The first title label inside a section, in sentence case. */
    private static String tituloDe(Node n) {
        if (n instanceof Label l && l.getStyleClass().stream().anyMatch(CLASES_TITULO::contains)) {
            String t = l.getText();
            if (t == null || t.isBlank()) return null;
            return t.equals(t.toUpperCase()) ? t.charAt(0) + t.substring(1).toLowerCase() : t;
        }
        if (n instanceof Parent p)
            for (Node hijo : p.getChildrenUnmodifiable()) {
                if (!hijo.isManaged()) continue;
                String t = tituloDe(hijo);
                if (t != null) return t;
            }
        return null;
    }

    private static void irA(VBox pagina, Node seccion) {
        // Looked up at click time: before the page is shown its ScrollPane has no skin
        // yet, and its content is not reachable through getParent().
        ScrollPane scroll = scrollDe(pagina);
        if (scroll == null) return;
        Bounds contenido = pagina.getBoundsInLocal();
        double visible = scroll.getViewportBounds().getHeight();
        double desplazable = contenido.getHeight() - visible;
        if (desplazable <= 0) return;
        double y = seccion.getBoundsInParent().getMinY() - 12;
        scroll.setVvalue(Math.max(0, Math.min(1, y / desplazable)));
        seccion.requestFocus();
    }
}
