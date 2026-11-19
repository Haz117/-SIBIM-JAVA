package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.FacturasDeCarpeta;
import com.sibim.service.FacturasDeCarpeta.Propuesta;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

/** Bienes › Más › «Leer las facturas de una carpeta»: each factura read and matched to its bienes
 *  (see {@link FacturasDeCarpeta}), with what it fills in them. The proveedor can be corrected
 *  before saving — each proveedor lays its PDF out its own way and the name is not always read. */
public final class FacturasCarpetaDialog {

    private FacturasCarpetaDialog() {}

    /** The ticked facturas and their proveedor as left, or empty if declined. */
    public record Eleccion(List<Propuesta> elegidas, Map<Propuesta, String> proveedor) {}

    public static Optional<Eleccion> show(String carpeta, FacturasDeCarpeta.Lectura lectura, List<Propuesta> propuestas) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Facturas de una carpeta");
        ButtonType ok = new ButtonType("Llenar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(980, pantalla.getWidth() * 0.94), Math.min(680, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        int leidas = lectura.leidas().size();
        HBox header = DialogUtil.gradientHeader("mdi2f-file-document-multiple-outline", "Facturas de una carpeta",
            FormatUtils.plural(leidas, "factura leída", "facturas leídas") + " en " + carpeta, AppColors.PRIMARY, AppColors.PRIMARY_D);

        long sinBienes = leidas - propuestas.size();
        Label aviso = new Label(propuestas.isEmpty()
            ? "Ninguna factura de la carpeta es de un bien del inventario (se buscan por su folio fiscal)."
            : FormatUtils.plural(propuestas.size(), "factura es", "facturas son") + " de bienes del inventario"
                + (sinBienes > 0 ? "; " + FormatUtils.plural(sinBienes, "no es", "no son") + " de ninguno" : "")
                + ". Solo se llena lo que les falta. Revisa el proveedor: si no se leyó o salió mal, escríbelo.");
        aviso.getStyleClass().add("cnt-aviso");
        aviso.setWrapText(true);
        aviso.setMaxWidth(Double.MAX_VALUE);
        aviso.setMinHeight(Region.USE_PREF_SIZE);
        aviso.setPadding(new Insets(8, 12, 8, 12));

        Map<Propuesta, CheckBox> marcas = new LinkedHashMap<>();
        Map<Propuesta, TextField> proveedores = new LinkedHashMap<>();
        GridPane lista = new GridPane();
        lista.setHgap(14);
        lista.setVgap(4);
        ColumnConstraints c0 = new ColumnConstraints(), c1 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(280);
        c1.setPrefWidth(320);
        lista.getColumnConstraints().addAll(c0, c1);
        Label total = new Label();
        Button boton = (Button) dialog.getDialogPane().lookupButton(ok);
        Runnable contar = () -> {
            Map<Propuesta, String> prov = new HashMap<>();
            proveedores.forEach((p, tf) -> prov.put(p, tf.getText()));
            List<Propuesta> el = marcas.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
            long n = FacturasDeCarpeta.renglones(el, prov).size();
            total.setText(FormatUtils.plural(n, "bien se llena", "bienes se llenan") + " de " + FormatUtils.plural(el.size(), "factura", "facturas"));
            boton.setText("Llenar " + FormatUtils.plural(n, "bien", "bienes"));
            boton.setDisable(n == 0);
        };
        int fila = 0;
        for (Propuesta p : propuestas) {
            var f = p.leida().factura();
            CheckBox cb = new CheckBox(p.leida().archivo().getFileName().toString());
            cb.setSelected(p.llenaAlgo());
            cb.setWrapText(true);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.getStyleClass().add("dialog-field-label");
            List<String> datos = new ArrayList<>();
            datos.add("Folio …" + f.uuid().substring(f.uuid().length() - 8));
            if (f.fecha() != null) datos.add(FormatUtils.formatDate(f.fecha()));
            if (f.total() != null) datos.add("total " + FormatUtils.formatCurrency(f.total()));
            if (f.rfcEmisor() != null) datos.add("RFC " + f.rfcEmisor());
            Label dato = new Label(String.join(" · ", datos));
            dato.getStyleClass().add("muted-sm");
            // Only the ones that lack something (a factura of 30 bienes, 5 without proveedor).
            List<String> faltan = p.bienes().stream().filter(b -> b.getProveedor() == null || b.getProveedor().isBlank()
                    || (f.fecha() != null && b.getFechaAdquisicion() == null)
                    || (com.sibim.service.MismaFactura.uuid(b.getFolioFiscal()) == null && com.sibim.service.MismaFactura.uuid(b.getNumeroFactura()) == null))
                .map(b -> Objects.requireNonNullElse(b.getCodigo(), "?")).toList();
            Label bienes = new Label(faltan.isEmpty()
                ? FormatUtils.plural(p.bienes().size(), "bien", "bienes") + ", con todo lo que dice la factura"
                : (faltan.size() == p.bienes().size() ? FormatUtils.plural(faltan.size(), "bien: ", "bienes: ")
                    : faltan.size() + " de sus " + p.bienes().size() + " bienes: ") + String.join(", ", faltan));
            bienes.getStyleClass().add("muted-sm");
            bienes.setWrapText(true);
            List<String> llena = new ArrayList<>();
            if (p.sinProveedor() > 0) llena.add("proveedor en " + p.sinProveedor() + (p.proveedor() == null ? " (si lo escribes)" : ""));
            if (p.sinFecha() > 0) llena.add("fecha en " + p.sinFecha());
            if (p.folioIncompleto() > 0) llena.add("folio completo en " + p.folioIncompleto());
            Label queLlena = new Label(llena.isEmpty() ? "Ya tienen todo lo que dice la factura" : "Llena " + String.join(", ", llena));
            queLlena.getStyleClass().add(llena.isEmpty() ? "muted-sm" : "field-hint");
            VBox texto = new VBox(2, cb, dato, bienes, queLlena);
            texto.setPadding(new Insets(6, 0, 6, 0));

            TextField prov = new TextField(Objects.requireNonNullElse(p.proveedor(), ""));
            prov.setPromptText("No se leyó: escríbelo como en la factura");
            prov.setAccessibleText("Proveedor de " + p.leida().archivo().getFileName());
            prov.textProperty().addListener((o, a, b) -> {
                // Typing the proveedor that wasn't read ticks its factura.
                if (p.sinProveedor() > 0 && (a == null || a.isBlank()) && b != null && !b.isBlank()) cb.setSelected(true);
                contar.run();
            });
            Label etiqueta = new Label(p.proveedor() == null ? "Proveedor (no se leyó)" : "Proveedor");
            etiqueta.getStyleClass().add(p.proveedor() == null ? "field-hint-warn" : "muted-sm");
            VBox provBox = new VBox(2, etiqueta, prov);
            cb.selectedProperty().addListener((o, a, b) -> contar.run());
            lista.add(texto, 0, fila);
            lista.add(provBox, 1, fila);
            GridPane.setValignment(provBox, VPos.CENTER);
            fila++;
            lista.add(new Separator(), 0, fila++, 2, 1);
            marcas.put(p, cb);
            proveedores.put(p, prov);
        }
        contar.run();
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        total.getStyleClass().add("table-count-label");
        VBox cuerpo = new VBox(10, aviso, scroll, total);
        if (!lectura.noLeidas().isEmpty()) {
            Label noLeidas = new Label(lectura.noLeidas().stream().limit(40)
                .map(n -> "• " + n.archivo().getFileName() + ": " + n.motivo()).collect(Collectors.joining("\n"))
                + (lectura.noLeidas().size() > 40 ? "\n…" : ""));
            noLeidas.getStyleClass().add("muted-sm");
            noLeidas.setWrapText(true);
            TitledPane tp = new TitledPane(FormatUtils.plural(lectura.noLeidas().size(), "archivo no se pudo leer", "archivos no se pudieron leer")
                + " (escaneados o que no son factura)", noLeidas);
            tp.setExpanded(false);
            cuerpo.getChildren().add(tp);
        }
        Label ayuda = new Label("Se guarda bien por bien, como en el formulario; lo que ya tienen no se toca. Se deshace desde el aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        cuerpo.getChildren().add(ayuda);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ok) return Optional.empty();
        Map<Propuesta, String> prov = new HashMap<>();
        proveedores.forEach((p, tf) -> prov.put(p, tf.getText()));
        List<Propuesta> el = marcas.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        return el.isEmpty() ? Optional.empty() : Optional.of(new Eleccion(el, prov));
    }
}
