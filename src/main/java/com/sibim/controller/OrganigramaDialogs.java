package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteOrganigramaService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.time.format.DateTimeFormatter;
import java.util.List;

class OrganigramaDialogs {

    private final MovimientoService       movimientoService;
    private final ReporteOrganigramaService reporteService;
    private final Logger                  log;

    OrganigramaDialogs(MovimientoService movimientoService,
                       ReporteOrganigramaService reporteService,
                       Logger log) {
        this.movimientoService = movimientoService;
        this.reporteService    = reporteService;
        this.log               = log;
    }

    void showAreaProductsDialog(String areaName, List<Producto> allProds, boolean soloAlertas, Scene scene) {
        List<Producto> prods = soloAlertas
            ? allProds.stream()
                .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
                .toList()
            : allProds;

        ButtonType btnVerInventario = new ButtonType("Ver en Inventario →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(areaName);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerInventario, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        javafx.scene.Node verBtn = dialog.getDialogPane().lookupButton(btnVerInventario);
        if (verBtn != null) {
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                com.sibim.session.NavigationContext.setPendingAreaFilter(areaName);
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
            });
        }

        HBox header = soloAlertas
            ? DialogUtil.gradientHeader("mdi2a-alert-circle-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien agotado o con bajo stock" : " bienes agotados o con bajo stock"),
                AppColors.WARNING, AppColors.WARNING_D)
            : DialogUtil.gradientHeader("mdi2f-folder-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien registrado en esta área" : " bienes registrados en esta área"),
                AppColors.INDIGO, AppColors.PURPLE);

        TableView<Producto> tbl = new TableView<>(FXCollections.observableArrayList(prods));
        tbl.setPrefHeight(360);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Producto, String> cCod = new TableColumn<>("Código");
        cCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        cCod.setPrefWidth(90);

        TableColumn<Producto, String> cNombre = new TableColumn<>("Bien");
        cNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        cNombre.setPrefWidth(180);

        TableColumn<Producto, String> cResguard = new TableColumn<>("Resguardante");
        cResguard.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getResguardante() != null ? c.getValue().getResguardante() : "—"));
        cResguard.setPrefWidth(110);

        TableColumn<Producto, String> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStockActual())));
        cStock.setPrefWidth(65);
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); getStyleClass().removeAll("stock-ok", "stock-warn", "stock-low");
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) return;
                setText(item);
                getStyleClass().add(switch (getTableRow().getItem().getEstado()) {
                    case AGOTADO    -> "stock-low";
                    case BAJO_STOCK -> "stock-warn";
                    default         -> "stock-ok";
                });
            }
        });

        TableColumn<Producto, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().getEtiqueta()));
        cEstado.setPrefWidth(100);
        cEstado.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Agotado"    -> "cell-badge-danger";
            case "Bajo Stock" -> "cell-badge-warning";
            case "Vencido"    -> "cell-badge-purple";
            default           -> "cell-badge-success";
        }));

        tbl.getColumns().addAll(cCod, cNombre, cResguard, cStock, cEstado);

        tbl.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tbl.getSelectionModel().getSelectedItem();
                if (sel != null) ProductoDetailDialog.show(sel, scene, movimientoService, log);
            }
        });
        tbl.setOnKeyPressed(e -> {
            Producto sel = tbl.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) {
                ProductoDetailDialog.show(sel, scene, movimientoService, log); e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tbl.getSelectionModel().clearSelection(); e.consume();
            }
        });

        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = tbl.getSelectionModel().getSelectedItem();
            if (sel != null) ProductoDetailDialog.show(sel, scene, movimientoService, log);
        });
        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = tbl.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            DialogUtil.runAsyncWithProgress(scene, "Generando ficha…",
                () -> reporteService.exportFichaTecnica(sel, movimientoService.getByProducto(sel.getId())),
                file -> DialogUtil.showExportResultDialog(scene, file),
                ex -> { log.error("Error ficha técnica desde organigrama", ex); NotificacionUtil.error(scene, "No se pudo generar la ficha técnica"); });
        });
        ContextMenu cm = new ContextMenu(cmDetalle, new SeparatorMenuItem(), cmFicha);
        cm.setOnShowing(e -> {
            boolean none = tbl.getSelectionModel().getSelectedItem() == null;
            cmDetalle.setDisable(none);
            cmFicha.setDisable(none);
        });
        tbl.setContextMenu(cm);

        TextField dlgSearch = new TextField();
        dlgSearch.setPromptText("Buscar por nombre o código…");
        dlgSearch.getStyleClass().add("search-field");
        dlgSearch.setPadding(new Insets(0, 12, 0, 12));
        dlgSearch.textProperty().addListener((obs, o, q) -> {
            String lower = q.toLowerCase();
            List<Producto> filtrado = prods.stream()
                .filter(p -> lower.isBlank()
                    || p.getNombre().toLowerCase().contains(lower)
                    || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(lower)))
                .toList();
            tbl.getItems().setAll(filtrado);
        });

        AnimationUtils.staggeredFadeInUp(List.of(header, dlgSearch, tbl), 270, 70);
        VBox content = new VBox(8, header, dlgSearch, tbl);
        content.setPadding(new Insets(0, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(dlgSearch::requestFocus);
        dialog.showAndWait();
    }

    // TableColumn varargs addAll() triggers generic-array-creation warning — inescapable with this API.
    @SuppressWarnings("unchecked")
    void showResguardosAreaDialog(String areaName, List<Resguardo> resguardos, Scene scene) {
        ButtonType btnVerResguardos = new ButtonType("Ver en Resguardos →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardos activos — " + areaName);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerResguardos, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(620);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        javafx.scene.Node verRsgBtn = dialog.getDialogPane().lookupButton(btnVerResguardos);
        if (verRsgBtn != null) {
            verRsgBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("resguardos");
            });
        }

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-account-outline", areaName,
            resguardos.size() + (resguardos.size() == 1 ? " resguardo activo" : " resguardos activos"),
            AppColors.INDIGO, AppColors.PURPLE);

        TableView<Resguardo> tbl = new TableView<>();
        tbl.getStyleClass().add("data-table");
        tbl.setPrefHeight(320);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        TableColumn<Resguardo, String> cFolio = new TableColumn<>("Folio");
        cFolio.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        cFolio.setPrefWidth(110);

        TableColumn<Resguardo, String> cResguardante = new TableColumn<>("Resguardante");
        cResguardante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getResguardanteNombre()));
        cResguardante.setPrefWidth(180);

        TableColumn<Resguardo, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreadoEn() != null ? c.getValue().getCreadoEn().toLocalDate().format(fmt) : "—"));
        cFecha.setPrefWidth(100);

        TableColumn<Resguardo, String> cItems = new TableColumn<>("Bienes");
        cItems.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getItems() != null ? String.valueOf(c.getValue().getItems().size()) : "—"));
        cItems.setPrefWidth(70);

        tbl.getColumns().addAll(cFolio, cResguardante, cFecha, cItems);
        tbl.getItems().setAll(resguardos);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tbl.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("resguardos");
                dialog.close();
                e.consume();
            }
        });

        AnimationUtils.staggeredFadeInUp(List.of(header, tbl), 270, 70);
        VBox content = new VBox(8, header, tbl);
        content.setPadding(new Insets(0, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
