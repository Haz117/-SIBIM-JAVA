package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.LoSeguro;
import com.sibim.service.LoSeguro.Propuesta;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Bienes › Más and Inicio › Por hacer › «Lo seguro, de una vez»: the fixes of {@link LoSeguro} in one
 * window, each with how many bienes, an example and a box; «Aplicar lo marcado» does them, and each
 * one done gets its own «Deshacer».
 */
public final class LoSeguroDialog {

    /** One fix in the window and what became of it. */
    static final class Tarjeta {
        final Propuesta propuesta;
        final CheckBox marca = new CheckBox();
        final Label estado = new Label();
        final Button deshacer = new Button("Deshacer");
        LoSeguro.Hecho hecho;
        Tarjeta(Propuesta p) { propuesta = p; }
    }

    private final CalidadDatosService calidad = new CalidadDatosService();
    private final List<Tarjeta> tarjetas = new ArrayList<>();
    private final Runnable alCambiar;
    private boolean cambio;
    private Dialog<ButtonType> dialog;
    private final Button aplicar = new Button("Aplicar lo marcado");
    private final Label total = new Label();

    private LoSeguroDialog(List<Propuesta> propuestas, Runnable alCambiar) {
        propuestas.forEach(p -> tarjetas.add(new Tarjeta(p)));
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar on closing, when something was applied or undone */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        new LoSeguroDialog(LoSeguro.proponer(bienes), alCambiar).abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Lo seguro, de una vez");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(820);
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-check-decagram", "Lo seguro, de una vez",
            "Lo que SIBIM pone sin dudas. Quita la marca a lo que no quieras y aplica; cada arreglo se deshace aparte",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        VBox lista = new VBox(10);
        if (tarjetas.isEmpty()) {
            Label nada = new Label("No queda nada que SIBIM pueda arreglar solo: los folios están en su lugar, "
                + "los bienes tienen su tipo, su valor residual y su color");
            nada.setWrapText(true);
            nada.getStyleClass().add("empty-state-hint");
            lista.getChildren().add(nada);
        }
        for (Tarjeta t : tarjetas) lista.getChildren().add(tarjeta(t));
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("area-elegir-lista");
        scroll.setPrefViewportHeight(430);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        aplicar.setGraphic(new FontIcon("mdi2c-check-all"));
        aplicar.getStyleClass().add("btn-primary");
        aplicar.setOnAction(e -> aplicarMarcados());
        total.getStyleClass().add("muted-sm");
        HBox abajo = new HBox(12, aplicar, total);
        abajo.setAlignment(Pos.CENTER_LEFT);
        ver(abajo, !tarjetas.isEmpty());

        VBox cuerpo = new VBox(12, scroll, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        actualizarTotal();
        dialog.showAndWait();
    }

    private VBox tarjeta(Tarjeta t) {
        Propuesta p = t.propuesta;
        t.marca.setSelected(true);
        t.marca.setText(p.arreglo().titulo);
        t.marca.getStyleClass().add("org-child-label");
        t.marca.selectedProperty().addListener((o, a, b) -> actualizarTotal());
        Label cuantos = new Label(FormatUtils.plural(p.bienes().size(), "bien", "bienes"));
        cuantos.getStyleClass().addAll("cell-badge", "cell-badge-blue");
        cuantos.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        t.estado.setWrapText(true);
        ver(t.estado, false);
        t.deshacer.setGraphic(new FontIcon("mdi2u-undo"));
        t.deshacer.getStyleClass().add("btn-secondary");
        t.deshacer.setMinWidth(Region.USE_PREF_SIZE);
        t.deshacer.setOnAction(e -> deshacer(t));
        ver(t.deshacer, false);
        HBox arriba = new HBox(10, t.marca, cuantos, hueco, t.estado, t.deshacer);
        arriba.setAlignment(Pos.CENTER_LEFT);

        Label explica = new Label(p.arreglo().explica);
        explica.getStyleClass().add("muted-sm");
        explica.setWrapText(true);
        explica.setMinHeight(Region.USE_PREF_SIZE);
        Label ejemplo = new Label("Por ejemplo, " + p.ejemplo());
        ejemplo.getStyleClass().add("org-table-sub");
        ejemplo.setWrapText(true);
        Hyperlink verCuales = new Hyperlink("Ver cuáles");
        verCuales.setOnAction(e -> verCuales(p));
        HBox lineaEjemplo = new HBox(8, ejemplo, verCuales);
        lineaEjemplo.setAlignment(Pos.CENTER_LEFT);
        VBox caja = new VBox(4, arriba, explica, lineaEjemplo);
        if (p.fuera() != null) {
            Label fuera = new Label(p.fuera());
            fuera.getStyleClass().add("org-table-sub");
            fuera.setWrapText(true);
            fuera.setMinHeight(Region.USE_PREF_SIZE);
            fuera.setGraphic(new FontIcon("mdi2i-information-outline"));
            caja.getChildren().add(fuera);
        }
        caja.getStyleClass().add("cnt-fila");
        caja.setStyle("-fx-cursor: default;");
        return caja;
    }

    private void actualizarTotal() {
        List<Tarjeta> marcadas = tarjetas.stream().filter(t -> t.hecho == null && t.marca.isSelected()).toList();
        int n = marcadas.stream().mapToInt(t -> t.propuesta.bienes().size()).sum();
        total.setText(marcadas.isEmpty() ? "Nada marcado" : FormatUtils.plural(marcadas.size(), "arreglo", "arreglos")
            + " · " + FormatUtils.plural(n, "cambio", "cambios"));
        aplicar.setDisable(marcadas.isEmpty());
    }

    /** The bienes of a fix and what each one gets. */
    private void verCuales(Propuesta p) {
        ListView<String> l = new ListView<>();
        for (Producto b : p.bienes()) {
            String que = switch (p.arreglo()) {
                case FOLIO -> "«" + Objects.requireNonNullElse(b.getNumeroFactura(), "").trim() + "» a «Folio fiscal»";
                case TIPO -> "MUEBLE";
                case RESIDUAL -> FormatUtils.formatCurrency(b.getPrecioCompra()) + " → "
                    + FormatUtils.formatCurrency(com.sibim.util.ProductoUtils.residualSugerido(b.getPrecioCompra()));
                case COLOR -> p.colores().get(b);
            };
            l.getItems().add(b.getCodigo() + " · " + b.getNombre() + " · " + Objects.requireNonNullElse(b.getArea(), "Sin área") + " — " + que);
        }
        l.setPrefSize(760, 420);
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(dialog.getDialogPane().getScene().getWindow());
        d.setTitle(p.arreglo().titulo);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogUtil.applyStylesheet(d.getDialogPane());
        Label titulo = new Label(p.arreglo().titulo + ": " + FormatUtils.plural(p.bienes().size(), "bien", "bienes"));
        titulo.getStyleClass().add("trf-paso-titulo");
        VBox c = new VBox(8, titulo, l);
        c.setPadding(new Insets(14));
        d.getDialogPane().setContent(c);
        d.showAndWait();
    }

    private Scene escena() {
        return dialog.getDialogPane().getScene();
    }

    /** One after another in the background; one that fails says so and the rest go on. */
    private void aplicarMarcados() {
        List<Tarjeta> marcadas = tarjetas.stream().filter(t -> t.hecho == null && t.marca.isSelected()).toList();
        if (marcadas.isEmpty()) return;
        aplicar.setDisable(true);
        Map<Tarjeta, Object> resultado = new LinkedHashMap<>();
        DialogUtil.runAsyncWithProgress(escena(), "Aplicando…",
            () -> {
                for (Tarjeta t : marcadas) {
                    try { resultado.put(t, LoSeguro.aplicar(calidad, t.propuesta)); }
                    catch (Exception e) { resultado.put(t, e); }
                }
                return resultado;
            },
            r -> {
                int hechos = 0;
                for (var e : r.entrySet()) {
                    Tarjeta t = e.getKey();
                    if (e.getValue() instanceof LoSeguro.Hecho h) {
                        t.hecho = h;
                        hechos += h.cuantos();
                        cambio = true;
                        t.estado.setText("Hecho en " + FormatUtils.plural(h.cuantos(), "bien", "bienes"));
                        t.estado.getStyleClass().setAll("cell-badge", "cell-badge-success");
                        ver(t.deshacer, h.cuantos() > 0);
                    } else {
                        t.estado.setText("No se pudo: " + com.sibim.util.MensajeError.de((Exception) e.getValue()));
                        t.estado.getStyleClass().setAll("cell-badge", "cell-badge-danger");
                    }
                    ver(t.estado, true);
                    t.marca.setDisable(true);
                }
                actualizarTotal();
                NotificacionUtil.exito(escena(), FormatUtils.plural(hechos, "cambio hecho", "cambios hechos"));
            },
            e -> {
                aplicar.setDisable(false);
                NotificacionUtil.error(escena(), "No se aplicó: " + com.sibim.util.MensajeError.de(e));
            });
    }

    private void deshacer(Tarjeta t) {
        if (t.hecho == null) return;
        t.deshacer.setDisable(true);
        DialogUtil.runAsync(() -> LoSeguro.deshacer(calidad, t.hecho),
            n -> {
                cambio = true;
                t.estado.setText("Deshecho: " + FormatUtils.plural(n, "bien quedó", "bienes quedaron") + " como estaba");
                t.estado.getStyleClass().setAll("cell-badge", "cell-badge-muted");
                ver(t.deshacer, false);
            },
            ex -> {
                t.deshacer.setDisable(false);
                NotificacionUtil.error(escena(), "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex));
            });
    }

    private static void ver(javafx.scene.Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
