package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CostoDeSuCompra;
import com.sibim.service.CostoDeSuCompra.Grupo;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Bienes › Más and Inicio › Por hacer: bienes of one purchase with the same name and different
 *  costos (see {@link CostoDeSuCompra}). The groups where some miss the IVA their twins have come
 *  ticked with the costo with IVA; the rest, unticked, to check against the factura and pick
 *  which costo is right. */
public final class CostoDeSuCompraDialog {

    private CostoDeSuCompraDialog() {}

    /** @return the costo chosen for each ticked group, or empty if declined */
    public static Optional<Map<Grupo, BigDecimal>> show(List<Grupo> grupos) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Costos distintos en la misma compra");
        ButtonType ok = new ButtonType("Igualar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(900, pantalla.getWidth() * 0.94), Math.min(640, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        long conIva = grupos.stream().filter(Grupo::faltaIva).count();
        HBox header = DialogUtil.gradientHeader("mdi2s-scale-balance", "Costos distintos en la misma compra",
            FormatUtils.plural(grupos.size(), "grupo", "grupos") + " de bienes con el mismo nombre y la misma factura o folio fiscal",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        Label aviso = new Label((conIva > 0
            ? "En " + FormatUtils.plural(conIva, "grupo", "grupos") + " unos tienen el IVA y otros no: van marcados con el costo con IVA. "
            : "") + "Los demás tienen otro costo (a veces un número mal tecleado): revísalos con la factura, "
            + "elige cuál es el bueno y márcalos.");
        aviso.getStyleClass().add("cnt-aviso");
        aviso.setWrapText(true);
        aviso.setMaxWidth(Double.MAX_VALUE);
        aviso.setMinHeight(Region.USE_PREF_SIZE);
        aviso.setPadding(new Insets(8, 12, 8, 12));

        Map<Grupo, CheckBox> marcas = new LinkedHashMap<>();
        Map<Grupo, ChoiceBox<BigDecimal>> costos = new LinkedHashMap<>();
        GridPane lista = new GridPane();
        lista.setHgap(14);
        lista.setVgap(4);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        // The costo chosen and why, always whole (at 900 px they were squeezed to «Que…»).
        ColumnConstraints c1 = new ColumnConstraints(), c2 = new ColumnConstraints();
        c1.setMinWidth(Region.USE_PREF_SIZE);
        c2.setMinWidth(Region.USE_PREF_SIZE);
        lista.getColumnConstraints().addAll(c0, c1, c2);
        StringConverter<BigDecimal> moneda = new StringConverter<>() {
            @Override public String toString(BigDecimal b) { return b == null ? "" : FormatUtils.formatCurrency(b); }
            @Override public BigDecimal fromString(String s) { return null; }
        };
        Label total = new Label();
        Button boton = (Button) dialog.getDialogPane().lookupButton(ok);
        Runnable contar = () -> {
            long n = 0;
            for (Grupo g : grupos) if (marcas.get(g).isSelected()) n += g.cambian(costos.get(g).getValue()).size();
            total.setText(FormatUtils.plural(n, "bien cambia", "bienes cambian") + " de costo");
            boton.setText("Igualar " + FormatUtils.plural(n, "bien", "bienes"));
            boton.setDisable(n == 0);
        };
        int fila = 0;
        for (Grupo g : grupos) {
            CheckBox cb = new CheckBox(g.nombre());
            cb.setSelected(g.faltaIva());
            cb.setWrapText(true);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.getStyleClass().add("dialog-field-label");
            Label datos = new Label((g.compra().isBlank() ? "" : "Factura " + g.compra() + " · ") + g.resumen());
            datos.getStyleClass().add("muted-sm");
            datos.setWrapText(true);
            Label cambian = new Label();
            cambian.getStyleClass().add("muted-sm");
            cambian.setWrapText(true);
            ChoiceBox<BigDecimal> cual = new ChoiceBox<>();
            cual.getItems().setAll(g.costos().keySet().stream().sorted(Comparator.reverseOrder()).toList());
            cual.setConverter(moneda);
            cual.setValue(g.propuesto());
            cual.setMinWidth(Region.USE_PREF_SIZE);
            cual.setAccessibleText("Costo que queda para " + g.nombre());
            Label porque = new Label(g.faltaIva() ? "A unos les falta el IVA" : "Otro costo: revisa la factura");
            porque.getStyleClass().add(g.faltaIva() ? "muted-sm" : "field-hint-warn");
            porque.setMinWidth(Region.USE_PREF_SIZE);
            Runnable decir = () -> {
                List<Producto> cambia = g.cambian(cual.getValue());
                cambian.setText("Cambian: " + cambia.stream().map(p -> Objects.requireNonNullElse(p.getCodigo(), "?") + " ("
                    + FormatUtils.formatCurrency(p.getPrecioCompra()) + ")").collect(Collectors.joining(", ")));
            };
            cual.valueProperty().addListener((o, a, b) -> { decir.run(); contar.run(); });
            cb.selectedProperty().addListener((o, a, b) -> contar.run());
            decir.run();
            VBox texto = new VBox(2, cb, datos, cambian);
            texto.setPadding(new Insets(6, 0, 6, 0));
            Label queda = new Label("Queda en");
            queda.getStyleClass().add("muted-sm");
            queda.setMinWidth(Region.USE_PREF_SIZE);
            VBox eleccion = new VBox(2, queda, cual);
            eleccion.setMinWidth(Region.USE_PREF_SIZE);
            lista.add(texto, 0, fila);
            lista.add(eleccion, 1, fila);
            lista.add(porque, 2, fila);
            GridPane.setValignment(eleccion, VPos.CENTER);
            GridPane.setValignment(porque, VPos.CENTER);
            fila++;
            lista.add(new Separator(), 0, fila++, 3, 1);
            marcas.put(g, cb);
            costos.put(g, cual);
        }
        contar.run();
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        total.getStyleClass().add("table-count-label");
        Label ayuda = new Label("Se guarda bien por bien, como en el formulario, con su historial de precios; un valor residual "
            + "que era el 15 % del costo sigue al costo nuevo. Se deshace desde el aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        VBox cuerpo = new VBox(10, aviso, scroll, total, ayuda);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ok) return Optional.empty();
        Map<Grupo, BigDecimal> elegidos = new LinkedHashMap<>();
        for (Grupo g : grupos) if (marcas.get(g).isSelected()) elegidos.put(g, costos.get(g).getValue());
        return elegidos.isEmpty() ? Optional.empty() : Optional.of(elegidos);
    }
}
