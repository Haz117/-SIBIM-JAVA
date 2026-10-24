package com.sibim.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Dialogs at "Texto: Grande / Extra", and form labels that keep their room. */
class DialogUtilAnchoTest {

    @BeforeAll
    static void arrancarFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException yaArrancado) { /* ok */ }
    }

    private static <T> T enFx(Callable<T> c) throws Exception {
        var listo = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { listo.complete(c.call()); } catch (Throwable t) { listo.completeExceptionally(t); }
        });
        return listo.get(10, TimeUnit.SECONDS);
    }

    /** At "Extra" the dialog is as much wider as its text (×1.3), whether its
     *  width was set before or after; at "Normal" it keeps its width. */
    @Test
    void conTextoExtra_laVentanaCreceLoMismoQueSuTexto() throws Exception {
        double[] r = enFx(() -> {
            DialogPane antes = new DialogPane();
            antes.setPrefWidth(400);
            DialogUtil.anchoSegunTexto(antes, 2);
            DialogPane despues = new DialogPane();
            DialogUtil.anchoSegunTexto(despues, 2);
            despues.setPrefWidth(500);
            DialogPane normal = new DialogPane();
            normal.setPrefWidth(400);
            DialogUtil.anchoSegunTexto(normal, 0);
            DialogUtil.anchoSegunTexto(antes, 2);   // a second call doesn't grow it again
            return new double[]{ antes.getPrefWidth(), despues.getPrefWidth(), normal.getPrefWidth() };
        });
        assertEquals(400 * EscalaTexto.FACTORES[1], r[0], 0.01);
        assertEquals(500 * EscalaTexto.FACTORES[1], r[1], 0.01);
        assertEquals(400, r[2]);
    }

    /** A wide field column used to squeeze the labels back to the column's
     *  least width, and the "?" of "Código CONAC" went under its field. */
    @Test
    void unaColumnaDeCamposAncha_noAprietaLasEtiquetas() throws Exception {
        double[] r = enFx(() -> {
            GridPane g = DialogUtil.formGrid(60);
            HBox etiqueta = DialogUtil.fieldLabelWithHelp("Código CONAC", "Clave contable");
            TextField campo = new TextField();
            campo.setPrefColumnCount(60);
            g.add(etiqueta, 0, 0);
            g.add(campo, 1, 0);
            new Scene(g, 420, 120);
            g.applyCss();
            g.layout();
            return new double[]{ etiqueta.getWidth(), etiqueta.prefWidth(-1),
                etiqueta.getBoundsInParent().getMaxX(), campo.getBoundsInParent().getMinX() };
        });
        assertEquals(r[1], r[0], 0.5, "la etiqueta y su \"?\" caben enteros");
        assertTrue(r[2] <= r[3], "el campo empieza después de la etiqueta");
    }

    /** The least width still holds when every label is short. */
    @Test
    void conEtiquetasCortas_laColumnaGuardaSuAnchoMinimo() throws Exception {
        double ancho = enFx(() -> {
            GridPane g = DialogUtil.formGrid(110);
            g.add(DialogUtil.fieldLabel("Año"), 0, 0);
            g.add(new TextField(), 1, 0);
            new Scene(g, 500, 120);
            g.applyCss();
            g.layout();
            return g.getCellBounds(0, 0).getWidth();
        });
        assertEquals(110, ancho, 0.5);
    }
}
