package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.JuntarBienes;
import com.sibim.service.JuntarBienes.Paso;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Bienes › Más › «Son el mismo bien…»: two records of one bien, side by side; one stays and
 *  takes what the other has (data it lacks, photos, history, its place in the resguardo),
 *  the other leaves. Undone from the notice. */
public final class JuntarBienesDialog {

    private final List<Producto> inventario;
    private final Runnable alCambiar;
    private final JuntarBienes servicio = new JuntarBienes();
    // Before the two sides: each one joins this group when it is built.
    private final ToggleGroup queda = new ToggleGroup();
    private final Lado a = new Lado("Primer bien"), b = new Lado("Segundo bien");
    private final VBox resumen = new VBox(6);
    private final TextField motivo = new TextField();
    private final Button juntar = new Button("Juntar");
    private Dialog<ButtonType> dialog;

    private JuntarBienesDialog(List<Producto> inventario, Runnable alCambiar) {
        this.inventario = inventario;
        this.alCambiar = alCambiar;
    }

    /** @param primero the bien selected in Bienes, or null */
    public static void show(List<Producto> inventario, Producto primero, Runnable alCambiar) {
        show(inventario, primero, null, alCambiar);
    }

    /** Both already chosen (from Fotos repetidas). */
    public static void show(List<Producto> inventario, Producto primero, Producto segundo, Runnable alCambiar) {
        JuntarBienesDialog d = new JuntarBienesDialog(inventario, alCambiar);
        d.abrir(primero, segundo);
    }

    /** One of the two bienes: looked up by its número (or the one on its old label). */
    private final class Lado {
        final TextField buscar = new TextField();
        final RadioButton seQueda = new RadioButton("Se queda este");
        final VBox ficha = new VBox(4);
        final VBox caja;
        Producto bien;

        Lado(String titulo) {
            Label t = new Label(titulo);
            t.getStyleClass().add("trf-paso-titulo");
            buscar.setPromptText("Número: SOP/04, o el de la etiqueta vieja");
            buscar.getStyleClass().add("form-input");
            buscar.setOnAction(e -> elegir(encontrar(buscar.getText())));
            buscar.focusedProperty().addListener((o, antes, ahora) -> { if (!ahora && bien == null && !buscar.getText().isBlank()) elegir(encontrar(buscar.getText())); });
            seQueda.setToggleGroup(queda);
            seQueda.setDisable(true);
            caja = new VBox(8, t, buscar, ficha, seQueda);
            caja.getStyleClass().add("trf-panel");
            caja.setPadding(new Insets(12));
            HBox.setHgrow(caja, Priority.ALWAYS);
            caja.setPrefWidth(420);
            mostrar();
        }

        void elegir(Producto p) {
            if (p == null && !buscar.getText().isBlank()) {
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No hay un bien activo con el número «" + buscar.getText().trim() + "»");
                return;
            }
            bien = p;
            if (p != null) buscar.setText(p.getCodigo());
            seQueda.setDisable(p == null);
            Producto otro = (this == a ? b : a).bien;
            if (p != null && otro != null && queda.getSelectedToggle() == null)
                (JuntarBienes.sugerirQueda(a.bien, b.bien) == a.bien ? a : b).seQueda.setSelected(true);
            mostrar();
            actualizar();
        }

        void mostrar() {
            ficha.getChildren().clear();
            if (bien == null) {
                Label vacio = new Label("Escribe su número y Enter");
                vacio.getStyleClass().add("muted-sm");
                ficha.getChildren().add(vacio);
                return;
            }
            Label nombre = new Label(bien.getNombre());
            nombre.getStyleClass().add("org-child-label");
            nombre.setWrapText(true);
            ficha.getChildren().add(nombre);
            linea("Área", bien.getArea());
            linea("Resguardante", bien.getResguardante());
            linea("Número anterior", anterior(bien));
            linea("Serie", bien.getNumeroSerie());
            linea("Factura", bien.getNumeroFactura());
            linea("Costo", bien.getPrecioCompra() != null ? FormatUtils.formatCurrency(bien.getPrecioCompra()) : null);
            linea("Fecha", bien.getFechaAdquisicion() != null ? FormatUtils.formatDate(bien.getFechaAdquisicion()) : null);
            linea("Foto", bien.getFotoUrl() != null && !bien.getFotoUrl().isBlank() ? "sí" : "no tiene");
        }

        private void linea(String que, String valor) {
            Label l = new Label(que + ": " + (valor == null || valor.isBlank() ? "—" : valor));
            l.getStyleClass().add("muted-sm");
            l.setWrapText(true);
            ficha.getChildren().add(l);
        }
    }

    private void abrir(Producto primero, Producto segundo) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Son el mismo bien");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(960, pantalla.getWidth() * 0.94), Math.min(720, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-call-merge", "Son el mismo bien",
            "Dos registros de un solo bien: uno se queda con lo de los dos y el otro se quita", AppColors.PRIMARY, AppColors.PRIMARY_D);

        HBox lados = new HBox(14, a.caja, b.caja);
        queda.selectedToggleProperty().addListener((o, x, y) -> actualizar());

        motivo.setPromptText("Por qué son el mismo (opcional): por ejemplo, «revisado en físico, la tiene Jurídica»");
        motivo.getStyleClass().add("form-input");

        juntar.setGraphic(new FontIcon("mdi2c-call-merge"));
        juntar.getStyleClass().add("btn-primary");
        juntar.setMinWidth(Region.USE_PREF_SIZE);
        juntar.setOnAction(e -> juntar());
        HBox abajo = new HBox(10, motivo, juntar);
        HBox.setHgrow(motivo, Priority.ALWAYS);
        abajo.setAlignment(Pos.CENTER_LEFT);

        resumen.getStyleClass().add("cnt-aviso");
        resumen.setPadding(new Insets(10, 12, 10, 12));
        VBox cuerpo = new VBox(12, lados, resumen, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        ScrollPane sp = new ScrollPane(cuerpo);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(sp, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, sp));

        if (primero != null) a.elegir(primero);
        if (segundo != null) b.elegir(segundo);
        actualizar();
        Platform.runLater(() -> (primero != null ? b : a).buscar.requestFocus());
        dialog.showAndWait();
    }

    private Producto encontrar(String texto) {
        String k = clave(texto);
        if (k.isEmpty()) return null;
        return inventario.stream().filter(p -> p.getFechaBaja() == null && k.equals(clave(p.getCodigo()))).findFirst()
            .or(() -> inventario.stream().filter(p -> p.getFechaBaja() == null && anterior(p) != null && k.equals(clave(anterior(p)))).findFirst())
            .orElse(null);
    }

    private static String clave(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String anterior(Producto p) {
        String d = p.getDescripcion();
        if (d == null || !d.contains("Inventario anterior:")) return null;
        String r = d.substring(d.indexOf("Inventario anterior:") + 20).trim();
        int i = r.indexOf('.');
        return (i >= 0 ? r.substring(0, i) : r).trim();
    }

    private Producto quedaElegido() {
        if (a.seQueda.isSelected()) return a.bien;
        if (b.seQueda.isSelected()) return b.bien;
        return null;
    }

    private void actualizar() {
        resumen.getChildren().clear();
        Producto q = quedaElegido();
        Producto s = q == null ? null : (q == a.bien ? b.bien : a.bien);
        boolean listo = a.bien != null && b.bien != null && q != null && s != null && !Objects.equals(a.bien.getId(), b.bien.getId());
        juntar.setDisable(!listo);
        if (a.bien != null && b.bien != null && Objects.equals(a.bien.getId(), b.bien.getId())) {
            resumen.getChildren().add(texto("Es el mismo registro: elige otro bien en el segundo lado."));
            return;
        }
        if (!listo) {
            resumen.getChildren().add(texto("Escribe los dos números y marca cuál se queda."));
            return;
        }
        List<Paso> pasos = JuntarBienes.loQuePasa(q, s, LocalDate.now());
        String datos = pasos.stream().filter(p -> p.etiqueta() != null && !p.etiqueta().startsWith("Nota"))
            .map(p -> p.etiqueta() + " (" + p.texto() + ")").collect(Collectors.joining(", "));
        resumen.getChildren().addAll(
            texto("Se queda " + q.getCodigo() + " y se quita " + s.getCodigo() + "."),
            texto(datos.isEmpty() ? s.getCodigo() + " no tiene datos que le falten a " + q.getCodigo() + "."
                : q.getCodigo() + " toma de " + s.getCodigo() + ": " + datos + "."),
            texto("Las fotos, movimientos, préstamos, conteos y mantenimientos de " + s.getCodigo() + " pasan a " + q.getCodigo()
                + "; si " + s.getCodigo() + " está en un resguardo y " + q.getCodigo() + " no, su lugar en el resguardo pasa a "
                + q.getCodigo() + ". Su número queda anotado en la descripción y en Auditoría."));
        // The área and the número are the ones of the bien that stays: say it when they differ.
        if (!clave(q.getArea()).equals(clave(s.getArea()))) {
            Label ojo = texto("Están en áreas distintas: el bien queda en " + Objects.requireNonNullElse(q.getArea(), "sin área")
                + " con el número " + q.getCodigo() + ". Si está en " + Objects.requireNonNullElse(s.getArea(), "la otra")
                + ", marca que se queda " + s.getCodigo() + ".");
            ojo.setGraphic(new FontIcon("mdi2a-alert-outline"));
            resumen.getChildren().add(ojo);
        }
    }

    private static Label texto(String s) {
        Label l = new Label(s);
        l.setWrapText(true);
        l.getStyleClass().add("muted-sm");
        return l;
    }

    private void juntar() {
        Producto q = quedaElegido();
        Producto s = q == a.bien ? b.bien : a.bien;
        if (q == null || s == null) return;
        if (!ConfirmacionUtil.confirmarPeligro("Juntar " + s.getCodigo() + " en " + q.getCodigo(),
                s.getCodigo() + " «" + s.getNombre() + "» se quita y " + q.getCodigo() + " se queda con lo de los dos.\n\n"
                    + "Lo puedes deshacer desde el aviso.", "Juntar")) return;
        Scene scene = dialog.getOwner() != null ? dialog.getOwner().getScene() : dialog.getDialogPane().getScene();
        juntar.setDisable(true);
        String porque = motivo.getText();
        DialogUtil.runAsyncWithProgress(dialog.getDialogPane().getScene(), "Juntando…",
            () -> servicio.juntar(q, s, porque),
            huella -> {
                dialog.close();
                if (alCambiar != null) alCambiar.run();
                NotificacionUtil.exitoConAccion(scene, s.getCodigo() + " se juntó en " + q.getCodigo()
                        + (huella.resguardosCancelados() > 0 ? "; su resguardo quedó vacío y se canceló" : ""),
                    "Deshacer", () -> DialogUtil.runAsync(() -> servicio.deshacer(huella),
                        () -> { if (alCambiar != null) alCambiar.run(); NotificacionUtil.info(scene, s.getCodigo() + " y " + q.getCodigo() + " regresaron como estaban"); },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> {
                juntar.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se juntaron: " + com.sibim.util.MensajeError.de(ex));
            });
    }
}
