package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.util.function.Supplier;

/** Static column-setup helpers extracted from MovimientosController.
 *  Package-private — only used by MovimientosController. */
class MovimientosColumnSetup {

    private MovimientosColumnSetup() {}

    static void configureProducto(TableColumn<Movimiento, String> col, Supplier<String> searchText) {
        col.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : ""));
        col.setCellFactory(DialogUtil.highlightCellFactory(searchText));
    }

    static void configureTipo(TableColumn<Movimiento, String> col) {
        col.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo().getEtiqueta()));
        col.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Entrada"       -> "cell-badge-success";
            case "Salida"        -> "cell-badge-danger";
            case "Ajuste"        -> "cell-badge-warning";
            case "Transferencia" -> "cell-badge-blue";
            default              -> "cell-badge-purple";
        }));
    }

    static void configureCantidad(TableColumn<Movimiento, Integer> col) {
        col.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        col.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null); getStyleClass().removeAll("stock-ok", "stock-low", "stock-warn");
                if (empty || value == null) return;
                setText(String.valueOf(value));
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    String tipo = getTableRow().getItem().getTipo().getEtiqueta();
                    if ("Entrada".equals(tipo)) getStyleClass().add("stock-ok");
                    else if ("Salida".equals(tipo)) getStyleClass().add("stock-low");
                    else getStyleClass().add("stock-warn");
                }
            }
        });
    }

    static void configureStock(TableColumn<Movimiento, String> col) {
        col.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStockAnterior() + " → " + c.getValue().getStockNuevo()));
        col.setCellFactory(column -> new TableCell<>() {
            private final Label lblAntes   = new Label();
            private final Label lblArrow   = new Label();
            private final Label lblDespues = new Label();
            private final HBox  box        = new HBox(4, lblAntes, lblArrow, lblDespues);
            {
                lblAntes.getStyleClass().add("stock-before");
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null || getTableRow() == null) return;
                Movimiento m = getTableRow().getItem();
                if (m == null) return;
                int antes = m.getStockAnterior(), despues = m.getStockNuevo();
                lblAntes.setText(String.valueOf(antes));
                lblArrow.setText(antes < despues ? "↑" : (antes > despues ? "↓" : "·"));
                lblArrow.getStyleClass().setAll(
                    antes < despues ? "stock-arrow-up" : (antes > despues ? "stock-arrow-down" : "stock-arrow-neutral"));
                lblDespues.setText(String.valueOf(despues));
                lblDespues.getStyleClass().setAll(
                    despues <= 0 ? "stock-after-empty" : (despues < antes ? "stock-after-warn" : "stock-after-ok"));
                setGraphic(box);
            }
        });
    }

    static void configureMotivo(TableColumn<Movimiento, String> col) {
        col.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getMotivo() != null ? c.getValue().getMotivo() : ""));
        col.setCellFactory(column -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    static void configureUsuario(TableColumn<Movimiento, String> col) {
        col.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuarioNombre()));
    }

    static void configureFecha(TableColumn<Movimiento, String> col) {
        col.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDateTime(c.getValue().getCreadoEn())));
    }

    static void configureEstado(TableColumn<Movimiento, String> col) {
        // Shown as "Aprobado"/"Pendiente"/"Rechazado" to match every other status pill
        // (Bienes: "Activo", "Agotado"…); the stored value stays upper-case.
        col.setCellValueFactory(c -> new SimpleStringProperty(switch (c.getValue().getEstado()) {
            case Movimiento.ESTADO_PENDIENTE -> "Pendiente";
            case Movimiento.ESTADO_RECHAZADO -> "Rechazado";
            default                          -> "Aprobado";
        }));
        col.setCellFactory(DialogUtil.iconBadgeCellFactory(
            estado -> switch (estado) {
                case "Pendiente"  -> "cell-badge-warning";
                case "Rechazado"  -> "cell-badge-danger";
                default           -> "cell-badge-success";
            },
            estado -> switch (estado) {
                case "Pendiente"  -> "mdi2c-clock-outline";
                case "Rechazado"  -> "mdi2c-close-circle-outline";
                default           -> "mdi2c-check-circle-outline";
            }
        ));
    }

    /** Row tints by movement type; new-row flash via pendingHighlightId. */
    static void configureRowFactory(TableView<Movimiento> table, Supplier<String> pendingHighlightId) {
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Movimiento m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("row-entrada", "row-salida", "row-ajuste", "row-transferencia", "row-new");
                if (!empty && m != null) {
                    String clase = switch (m.getTipo().getEtiqueta()) {
                        case "Entrada"       -> "row-entrada";
                        case "Salida"        -> "row-salida";
                        case "Ajuste"        -> "row-ajuste";
                        case "Transferencia" -> "row-transferencia";
                        default              -> null;
                    };
                    if (clase != null) getStyleClass().add(clase);
                    if (m.getId() != null && m.getId().equals(pendingHighlightId.get()))
                        getStyleClass().add("row-new");
                }
            }
        });
    }
}
