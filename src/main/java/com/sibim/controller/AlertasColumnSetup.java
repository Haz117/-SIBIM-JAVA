package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Static column-setup helpers extracted from AlertasController.
 *  Package-private — only used by AlertasController. */
class AlertasColumnSetup {

    private AlertasColumnSetup() {}

    static void configureAgotados(
            TableColumn<Producto, String> colNombre,
            TableColumn<Producto, String> colCodigo,
            TableColumn<Producto, String> colArea) {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colArea.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArea()));
        colArea.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item); tip.setText(item); setTooltip(tip);
            }
        });
    }

    static void configureBajoStock(
            TableColumn<Producto, String> colNombre,
            TableColumn<Producto, String> colCodigo,
            TableColumn<Producto, Integer> colStock,
            TableColumn<Producto, Integer> colMin) {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colMin.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));

        colStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("stock-low", "stock-warn");
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                getStyleClass().add(p != null && item <= p.getStockMinimo() ? "stock-low" : "stock-warn");
            }
        });
        colMin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("cell-muted");
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                getStyleClass().add("cell-muted");
            }
        });
    }

    /** All columns are nullable — the "Mantenimiento" section is optional in the FXML. */
    static void configureMantenimiento(
            TableColumn<Producto, String> colNombre,
            TableColumn<Producto, String> colCodigo,
            TableColumn<Producto, String> colArea,
            TableColumn<Producto, String> colFecha,
            TableColumn<Producto, String> colNotas) {
        if (colNombre != null) colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        if (colCodigo != null) colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        if (colArea   != null) colArea.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArea() != null ? c.getValue().getArea() : ""));
        if (colFecha  != null) colFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProximaRevision() != null ? FormatUtils.formatDate(c.getValue().getProximaRevision()) : "—"));
        if (colNotas  != null) colNotas.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNotasMantenimiento() != null ? c.getValue().getNotasMantenimiento() : ""));
    }

    static void configureGarantias(
            TableColumn<Producto, String> colNombre,
            TableColumn<Producto, String> colCodigo,
            TableColumn<Producto, String> colFecha,
            TableColumn<Producto, String> colDias) {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colFecha.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDate(c.getValue().getFechaVencimiento())));
        colDias.setCellValueFactory(c -> {
            LocalDate fv = c.getValue().getFechaVencimiento();
            if (fv == null) return new SimpleStringProperty("—");
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), fv);
            if (dias < 0) return new SimpleStringProperty("Vencido");
            if (dias == 0) return new SimpleStringProperty("Vence hoy");
            return new SimpleStringProperty(dias + " días");
        });
        colDias.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("days-critical", "days-warn");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                switch (item) {
                    case "Vencido", "Vence hoy" -> getStyleClass().add("days-critical");
                    default -> {
                        try {
                            if (Integer.parseInt(item.split(" ")[0]) <= 3)
                                getStyleClass().add("days-warn");
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        });
    }
}
