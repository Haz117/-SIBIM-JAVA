package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ComparacionBienesDialog {

    private ComparacionBienesDialog() {}

    // TableColumn<...,?>... varargs to addAll() triggers Java's inherent generic-array-creation
    // warning — inescapable with this API, not a real risk here.
    @SuppressWarnings("unchecked")
    public static void show(Producto a, Producto b, Scene owner) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Comparar Bienes");
        dlg.setResizable(true);
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(760);
        dlg.getDialogPane().setPrefHeight(620);

        HBox header = DialogUtil.gradientHeader(
            "mdi2c-compare", "Comparación de Bienes",
            a.getNombre() + "  vs  " + b.getNombre(),
            AppColors.PRIMARY, AppColors.INDIGO);

        List<String[]> rows = buildRows(a, b);

        TableView<String[]> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<String[], String> colCampo = new TableColumn<>("Campo");
        colCampo.setPrefWidth(160);
        colCampo.setSortable(false);
        colCampo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[0]));
        colCampo.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                getStyleClass().removeAll("compare-header");
                if (!empty && item != null) getStyleClass().add("compare-header");
            }
        });

        TableColumn<String[], String> colA = new TableColumn<>(a.getNombre());
        colA.setPrefWidth(270);
        colA.setSortable(false);
        colA.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[1]));
        colA.setCellFactory(tc -> diffCell(1));

        TableColumn<String[], String> colB = new TableColumn<>(b.getNombre());
        colB.setPrefWidth(270);
        colB.setSortable(false);
        colB.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[2]));
        colB.setCellFactory(tc -> diffCell(2));

        table.getColumns().addAll(colCampo, colA, colB);
        table.getItems().addAll(rows);

        long diffCount = rows.stream().filter(r -> !Objects.equals(r[1], r[2])).count();
        Label lblSummary = new Label(diffCount == 0
            ? "Los bienes son idénticos en todos los campos comparados."
            : diffCount + " campo(s) difieren — destacados en amarillo");
        lblSummary.getStyleClass().add(diffCount == 0 ? "field-hint-ok" : "field-hint-warn");
        FontIcon summaryIcon = new FontIcon(diffCount == 0 ? "mdi2c-check-circle-outline" : "mdi2a-alert-circle-outline");
        summaryIcon.getStyleClass().add(diffCount == 0 ? "status-icon-green" : "status-icon-amber");
        HBox summaryBar = new HBox(8, summaryIcon, lblSummary);
        summaryBar.setAlignment(Pos.CENTER_LEFT);
        summaryBar.setPadding(new Insets(8, 0, 4, 0));

        VBox content = new VBox(0, header, summaryBar, table);
        content.setPadding(new Insets(0, 18, 0, 18));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        dlg.getDialogPane().setContent(content);
        com.sibim.util.AnimationUtils.staggeredFadeInUp(
            java.util.List.of(header, summaryBar, table), 260, 60);
        dlg.showAndWait();
    }

    @SuppressWarnings("unchecked")
    private static TableCell<String[], String> diffCell(int colIndex) {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("compare-diff");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                TableRow<?> row = getTableRow();
                Object rowItem = row != null ? row.getItem() : null;
                if (rowItem instanceof String[] r && r.length == 3 && !Objects.equals(r[1], r[2])) {
                    getStyleClass().add("compare-diff");
                }
            }
        };
    }

    private static List<String[]> buildRows(Producto a, Producto b) {
        List<String[]> rows = new ArrayList<>();
        rows.add(row("Nombre",              str(a.getNombre()),                   str(b.getNombre())));
        rows.add(row("Código",              str(a.getCodigo()),                   str(b.getCodigo())));
        rows.add(row("Categoría",           str(a.getCategoriaNombre()),          str(b.getCategoriaNombre())));
        rows.add(row("Área",                str(a.getArea()),                     str(b.getArea())));
        rows.add(row("Resguardante",        str(a.getResguardante()),             str(b.getResguardante())));
        rows.add(row("Estado",              estado(a),                            estado(b)));
        rows.add(row("Stock actual",        String.valueOf(a.getStockActual()),   String.valueOf(b.getStockActual())));
        rows.add(row("Stock mínimo",        String.valueOf(a.getStockMinimo()),   String.valueOf(b.getStockMinimo())));
        rows.add(row("Stock máximo",        String.valueOf(a.getStockMaximo()),   String.valueOf(b.getStockMaximo())));
        rows.add(row("Precio de compra",    currency(a.getPrecioCompra()),        currency(b.getPrecioCompra())));
        rows.add(row("Precio de venta",     currency(a.getPrecioVenta()),         currency(b.getPrecioVenta())));
        rows.add(row("Marca",               str(a.getMarca()),                    str(b.getMarca())));
        rows.add(row("Modelo",              str(a.getModelo()),                   str(b.getModelo())));
        rows.add(row("Número de serie",     str(a.getNumeroSerie()),              str(b.getNumeroSerie())));
        rows.add(row("Proveedor",           str(a.getProveedor()),                str(b.getProveedor())));
        rows.add(row("Ubicación",           str(a.getUbicacion()),                str(b.getUbicacion())));
        rows.add(row("Fecha adquisición",   date(a.getFechaAdquisicion()),        date(b.getFechaAdquisicion())));
        rows.add(row("Fecha vencimiento",   date(a.getFechaVencimiento()),        date(b.getFechaVencimiento())));
        rows.add(row("Vida útil (años)",    nullable(a.getVidaUtilAnios()),       nullable(b.getVidaUtilAnios())));
        rows.add(row("Valor residual",      currency(a.getValorResidual()),       currency(b.getValorResidual())));
        rows.add(row("Valor depreciado",    currency(a.getValorDepreciado()),     currency(b.getValorDepreciado())));
        rows.add(row("Prox. revisión",      date(a.getProximaRevision()),         date(b.getProximaRevision())));
        rows.add(row("Notas mantenimiento", str(a.getNotasMantenimiento()),       str(b.getNotasMantenimiento())));
        rows.add(row("Etiquetado",          bool(a.isEtiquetado()),               bool(b.isEtiquetado())));
        return rows;
    }

    private static String[] row(String label, String valA, String valB) {
        return new String[]{ label, valA, valB };
    }

    private static String str(String v)    { return v != null && !v.isBlank() ? v : "—"; }
    private static String estado(Producto p) {
        return p.getEstado() != null ? p.getEstado().getEtiqueta() : "—";
    }
    private static String currency(java.math.BigDecimal v) {
        return v != null ? FormatUtils.formatCurrency(v) : "—";
    }
    private static String date(java.time.LocalDate d) {
        return d != null ? FormatUtils.formatDate(d) : "—";
    }
    private static String nullable(Integer v) { return v != null ? String.valueOf(v) : "—"; }
    private static String bool(boolean v)     { return v ? "Sí" : "No"; }
}
