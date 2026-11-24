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
            // A transfer moves the whole bien, so its quantity doesn't change ("1 · 1"):
            // what changed is its número and where it went.
            private final Label lblNumeros = new Label();
            private final Label lblAdonde  = new Label();
            private final javafx.scene.layout.VBox transferencia = new javafx.scene.layout.VBox(1, lblNumeros, lblAdonde);
            private final Tooltip tip = new Tooltip();
            {
                lblAntes.getStyleClass().add("stock-before");
                box.setAlignment(Pos.CENTER_LEFT);
                lblNumeros.getStyleClass().add("mov-numeros");
                lblAdonde.getStyleClass().add("org-table-sub");
                lblAdonde.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                transferencia.setAlignment(Pos.CENTER_LEFT);
                transferencia.setMinWidth(0);
                tip.setWrapText(true);
                tip.setMaxWidth(320);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null || getTableRow() == null) return;
                Movimiento m = getTableRow().getItem();
                setTooltip(null);
                if (m == null) return;
                if (m.getTipo() == com.sibim.model.enums.TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
                    boolean conNumeros = m.getCodigoAnterior() != null && m.getCodigoNuevo() != null;
                    lblNumeros.setText(conNumeros ? m.getCodigoAnterior() + " → " + m.getCodigoNuevo() : "Pendiente de aplicar");
                    lblAdonde.setText("a " + m.getAreaDestino());
                    tip.setText((m.getAreaOrigen() != null ? "De " + m.getAreaOrigen() + " a " : "A ") + m.getAreaDestino());
                    setTooltip(tip);
                    setGraphic(transferencia);
                    return;
                }
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

    /** A date, so it sorts as one: as text «31/10/2026» went above «13/11/2026» (19-nov-2026).
     *  Shown short («Hoy 14:40», «13 nov 14:40»), the whole date on hover. */
    static void configureFecha(TableColumn<Movimiento, java.time.LocalDateTime> col) {
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getCreadoEn()));
        col.setCellFactory(column -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(java.time.LocalDateTime t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); setTooltip(null); return; }
                setText(EtiquetasAuditoria.cuando(t, java.time.LocalDate.now()));
                tip.setText(FormatUtils.formatDateTime(t));
                setTooltip(tip);
            }
        });
    }

    /** What the Estado column says on hover and to a screen reader. */
    static String estadoEnPalabras(String estado) {
        return switch (estado == null ? "" : estado) {
            case Movimiento.ESTADO_PENDIENTE -> "Pendiente: espera que el área lo reciba";
            case Movimiento.ESTADO_RECHAZADO -> "Rechazado";
            default -> "Aprobado";
        };
    }

    static void configureEstado(TableColumn<Movimiento, String> col) {
        // The stored value (upper-case) sorts them; the cell is its icon, its word on hover:
        // «Aprobado» on 120 of 123 rows took 120 px from the bien and the motivo (19-nov-2026).
        col.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        col.setCellFactory(column -> new TableCell<>() {
            private final org.kordamp.ikonli.javafx.FontIcon icono = new org.kordamp.ikonli.javafx.FontIcon();
            private final Tooltip tip = new Tooltip();
            { icono.setIconSize(18); setAlignment(Pos.CENTER); }
            @Override protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                setText(null);
                if (empty || estado == null) { setGraphic(null); setTooltip(null); setAccessibleText(null); return; }
                icono.setIconLiteral(switch (estado) {
                    case Movimiento.ESTADO_PENDIENTE -> "mdi2c-clock-outline";
                    case Movimiento.ESTADO_RECHAZADO -> "mdi2c-close-circle-outline";
                    default -> "mdi2c-check-circle-outline";
                });
                icono.getStyleClass().setAll("ikonli-font-icon", switch (estado) {
                    case Movimiento.ESTADO_PENDIENTE -> "mov-estado-pendiente";
                    case Movimiento.ESTADO_RECHAZADO -> "mov-estado-rechazado";
                    default -> "mov-estado-aprobado";
                });
                String palabras = estadoEnPalabras(estado);
                tip.setText(palabras);
                setTooltip(tip);
                setAccessibleText(palabras);
                setGraphic(icono);
            }
        });
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
