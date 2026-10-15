package com.sibim.util;

import javafx.css.CssParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscalaTextoTest {

    @Test
    void escalaCadaReglaConTamanoEnPx() {
        String css = """
            /* comentario { -fx-font-size: 99px; } */
            .page-title, .page-subtitle { -fx-font-size: 20px; -fx-text-fill: red; }
            .dialog-pane > .content .label { -fx-font-size: 13px; }
            .sin-tamano { -fx-text-fill: blue; }
            """;
        String r = EscalaTexto.escalar(css);

        assertTrue(r.contains(".text-scale-lg .page-title, .text-scale-lg .page-subtitle { -fx-font-size: 23px; }"), r);
        assertTrue(r.contains(".text-scale-xl .page-title, .text-scale-xl .page-subtitle { -fx-font-size: 26px; }"), r);
        // A dialog carries the class on the DialogPane itself.
        assertTrue(r.contains(".dialog-pane.text-scale-lg > .content .label"), r);
        assertTrue(r.contains("{ -fx-font-size: 15px; }"), r);   // 13 × 1.15 = 14.95 → 15
        assertFalse(r.contains("sin-tamano"));
        assertFalse(r.contains("99px"), "los comentarios no cuentan");
    }

    @Test
    void losBotonesYLasReglasAjustadasAManoNoSeTocan() {
        String r = EscalaTexto.escalar("""
            .btn-primary { -fx-font-size: 13px; }
            .nav-btn .label { -fx-font-size: 13px; }
            .text-scale-lg .page-title { -fx-font-size: 26px; }
            .root { -fx-font-size: 13px; }
            """);
        assertEquals("", r);
    }

    @Test
    void redondeaAMedioPixel() {
        assertEquals("14", EscalaTexto.redondear(13.9));
        assertEquals("12.5", EscalaTexto.redondear(12.65));
    }

    /** In a real scene (no window): the data: stylesheet loads, a label grows
     *  under "Grande", and a button keeps its size. */
    @Test
    void enUnaEscena_elTextoCreceYLosBotonesNo() throws Exception {
        try { javafx.application.Platform.startup(() -> {}); } catch (IllegalStateException yaArrancado) { /* ok */ }
        var listo = new java.util.concurrent.CompletableFuture<double[]>();
        javafx.application.Platform.runLater(() -> {
            try {
                var titulo = new javafx.scene.control.Label("Bienes");
                titulo.getStyleClass().add("page-title");
                var boton = new javafx.scene.control.Button("Nuevo");
                boton.getStyleClass().add("btn-primary");
                var raiz = new javafx.scene.layout.VBox(titulo, boton);
                var escena = new javafx.scene.Scene(raiz);
                escena.getStylesheets().addAll(EscalaTexto.hojas());
                raiz.applyCss();
                double tituloNormal = titulo.getFont().getSize(), botonNormal = boton.getFont().getSize();
                raiz.getStyleClass().add("text-scale-lg");
                raiz.applyCss();
                listo.complete(new double[]{tituloNormal, titulo.getFont().getSize(), botonNormal, boton.getFont().getSize()});
            } catch (Throwable t) { listo.completeExceptionally(t); }
        });
        double[] r = listo.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(r[1] > r[0], "el título debe crecer: " + r[0] + " → " + r[1]);
        assertEquals(r[2], r[3], 0.01, "el botón conserva su tamaño");
    }

    /** The real stylesheet: every size is covered and the result parses with no error. */
    @Test
    void laHojaDeLaAppSeLeeSinErrores() throws Exception {
        String url = EscalaTexto.hoja();
        assertTrue(url.startsWith("data:text/css;base64,"));
        String css = new String(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)), StandardCharsets.UTF_8);
        assertTrue(css.lines().count() > 200, "debería escalar cientos de reglas, hubo " + css.lines().count());

        var errors = CssParser.errorsProperty();
        errors.clear();
        var sheet = new CssParser().parse(css);
        assertFalse(sheet.getRules().isEmpty());
        List<String> msgs = errors.stream().map(e -> e.getMessage()).limit(20).toList();
        assertEquals(0, errors.size(), "Errores de parseo:\n  " + String.join("\n  ", msgs));
    }
}
