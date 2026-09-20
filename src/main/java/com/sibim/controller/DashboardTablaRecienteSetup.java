package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;

class DashboardTablaRecienteSetup {

    private final TableView<Movimiento> tablaReciente;
    private final ProductoService       productoService;
    private final MovimientoService     movimientoService;
    private final Logger                log;

    DashboardTablaRecienteSetup(TableView<Movimiento> tablaReciente,
                                 ProductoService productoService,
                                 MovimientoService movimientoService,
                                 Logger log) {
        this.tablaReciente    = tablaReciente;
        this.productoService  = productoService;
        this.movimientoService = movimientoService;
        this.log              = log;
    }

    void setup() {
        if (tablaReciente == null) return;
        tablaReciente.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tablaReciente.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Movimiento m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("row-entrada","row-salida","row-ajuste","row-transferencia");
                if (!empty && m != null) {
                    String cls = switch (m.getTipo()) {
                        case ENTRADA      -> "row-entrada";
                        case SALIDA       -> "row-salida";
                        case AJUSTE       -> "row-ajuste";
                        case TRANSFERENCIA -> "row-transferencia";
                        default           -> "";
                    };
                    if (!cls.isEmpty()) getStyleClass().add(cls);
                }
            }
        });

        TableColumn<Movimiento, String> cProd = new TableColumn<>("Bien");
        cProd.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : ""));
        cProd.setPrefWidth(280); cProd.setMinWidth(160);
        cProd.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("recent-bien-cell");
                if (empty || item == null) { setText(null); return; }
                setText(item); getStyleClass().add("recent-bien-cell");
            }
        });

        TableColumn<Movimiento, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getTipo().getEtiqueta()));
        cTipo.setPrefWidth(100); cTipo.setMinWidth(90); cTipo.setMaxWidth(120);
        cTipo.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Entrada"       -> "cell-badge-success";
            case "Salida"        -> "cell-badge-danger";
            case "Ajuste"        -> "cell-badge-warning";
            case "Transferencia" -> "cell-badge-blue";
            default              -> "cell-badge-purple";
        }));

        TableColumn<Movimiento, Integer> cCant = new TableColumn<>("Cant.");
        cCant.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getCantidad()));
        cCant.setPrefWidth(55); cCant.setMinWidth(50); cCant.setMaxWidth(70);
        cCant.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("qty-in", "qty-out", "qty-neutral");
                if (empty || v == null) { setText(null); return; }
                Movimiento row = getTableRow() != null ? getTableRow().getItem() : null;
                String sign = "", cls = "qty-neutral";
                if (row != null) {
                    switch (row.getTipo()) {
                        case ENTRADA -> { sign = "+"; cls = "qty-in"; }
                        case SALIDA  -> { sign = "-"; cls = "qty-out"; }
                        default -> {}
                    }
                }
                setText(sign + v); getStyleClass().add(cls);
            }
        });

        TableColumn<Movimiento, String> cUsuario = new TableColumn<>("Usuario");
        cUsuario.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : ""));
        cUsuario.setPrefWidth(160); cUsuario.setMinWidth(110);

        TableColumn<Movimiento, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            FormatUtils.formatDateTime(c.getValue().getCreadoEn())));
        cFecha.setPrefWidth(140); cFecha.setMinWidth(130); cFecha.setMaxWidth(160);

        tablaReciente.getColumns().add(cProd);
        tablaReciente.getColumns().add(cTipo);
        tablaReciente.getColumns().add(cCant);
        tablaReciente.getColumns().add(cUsuario);
        tablaReciente.getColumns().add(cFecha);

        tablaReciente.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Movimiento sel = tablaReciente.getSelectionModel().getSelectedItem();
                if (sel != null) showMovimientoDetalle(sel);
            }
        });
        tablaReciente.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tablaReciente.getSelectionModel().clearSelection(); e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Movimiento sel = tablaReciente.getSelectionModel().getSelectedItem();
                if (sel != null) { showMovimientoDetalle(sel); e.consume(); }
            }
        });

        MenuItem cmDetalle = new MenuItem("Ver detalle del movimiento");
        cmDetalle.setOnAction(e -> {
            Movimiento sel = tablaReciente.getSelectionModel().getSelectedItem();
            if (sel != null) showMovimientoDetalle(sel);
        });
        MenuItem cmVerBien = new MenuItem("Ver ficha del bien");
        cmVerBien.setOnAction(e -> {
            Movimiento sel = tablaReciente.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getProductoId() == null) return;
            DialogUtil.runAsyncWithProgress(tablaReciente.getScene(), "Cargando bien…",
                () -> productoService.findById(sel.getProductoId()),
                opt -> opt.ifPresent(p -> com.sibim.controller.dialogs.ProductoDetailDialog.show(
                    p, tablaReciente.getScene(), movimientoService, log)),
                ex -> { log.error("Error cargando bien desde dashboard", ex);
                    NotificacionUtil.error(tablaReciente.getScene(), "No se pudo cargar el bien"); });
        });
        ContextMenu cm = new ContextMenu(cmDetalle, new SeparatorMenuItem(), cmVerBien);
        tablaReciente.setContextMenu(cm);
        cm.setOnShowing(e -> {
            boolean none = tablaReciente.getSelectionModel().getSelectedItem() == null;
            cmDetalle.setDisable(none); cmVerBien.setDisable(none);
        });
    }

    private void showMovimientoDetalle(Movimiento m) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(440);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        String icon  = switch (m.getTipo()) { case ENTRADA -> "mdi2a-arrow-up-bold-circle-outline"; case SALIDA -> "mdi2a-arrow-down-bold-circle-outline"; case AJUSTE -> "mdi2s-swap-horizontal"; default -> "mdi2a-arrow-right-bold-circle-outline"; };
        String color = switch (m.getTipo()) { case ENTRADA -> AppColors.SUCCESS; case SALIDA -> AppColors.DANGER; case AJUSTE -> AppColors.WARNING; default -> AppColors.INFO; };
        String color2= switch (m.getTipo()) { case ENTRADA -> AppColors.SUCCESS_D; case SALIDA -> AppColors.DANGER_D; case AJUSTE -> AppColors.WARNING_D; default -> AppColors.INFO_D; };

        HBox header = DialogUtil.gradientHeader(icon,
            m.getTipo().getEtiqueta() + "  —  " + m.getCantidad() + " uds.", m.getProductoNombre(), color, color2);

        javafx.scene.layout.GridPane grid = DialogUtil.formGrid(120);
        int r = 0;
        Label antes   = new Label(String.valueOf(m.getStockAnterior()));
        antes.getStyleClass().add("dlg-stock-val");
        Label arrow   = new Label("→");
        arrow.getStyleClass().add(m.getStockNuevo() > m.getStockAnterior() ? "dlg-stock-arrow-up" : "dlg-stock-arrow-down");
        Label despues = new Label(String.valueOf(m.getStockNuevo()));
        despues.getStyleClass().add(m.getStockNuevo() <= 0 ? "dlg-stock-new-empty"
            : m.getStockNuevo() > m.getStockAnterior() ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
        HBox stockRow = new HBox(8, antes, arrow, despues);
        stockRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label bienLbl = new Label(m.getProductoNombre());
        bienLbl.setWrapText(true);
        Hyperlink linkVerBien = new Hyperlink("Ver ficha →");
        linkVerBien.getStyleClass().add("muted-sm");
        if (m.getProductoId() != null) {
            linkVerBien.setOnAction(ev -> {
                dlg.close();
                DialogUtil.runAsyncWithProgress(tablaReciente.getScene(), "Cargando bien…",
                    () -> productoService.findById(m.getProductoId()),
                    opt -> opt.ifPresent(p -> com.sibim.controller.dialogs.ProductoDetailDialog.show(
                        p, tablaReciente.getScene(), movimientoService, log)),
                    ex -> { log.error("Error cargando bien desde dashboard movimiento", ex);
                        NotificacionUtil.error(tablaReciente.getScene(), "No se pudo cargar el bien"); });
            });
        } else {
            linkVerBien.setDisable(true);
        }
        HBox bienRow = new HBox(10, bienLbl, linkVerBien);
        bienRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        grid.add(DialogUtil.fieldLabel("Bien"),    0, r); grid.add(bienRow,  1, r++);
        grid.add(DialogUtil.fieldLabel("Stock"),   0, r); grid.add(stockRow, 1, r++);
        grid.add(DialogUtil.fieldLabel("Motivo"),  0, r); grid.add(new Label(m.getMotivo() != null ? m.getMotivo() : "—"), 1, r++);
        grid.add(DialogUtil.fieldLabel("Usuario"), 0, r); grid.add(new Label(m.getUsuarioNombre()), 1, r++);
        grid.add(DialogUtil.fieldLabel("Fecha"),   0, r); grid.add(new Label(FormatUtils.formatDateTime(m.getCreadoEn())), 1, r);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 260, 70);
        dlg.getDialogPane().setContent(new javafx.scene.layout.VBox(0, header, grid));
        dlg.showAndWait();
    }
}
