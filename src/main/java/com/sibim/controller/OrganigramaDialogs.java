package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.model.Comodato;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteEntregaRecepcionService;
import com.sibim.service.ReporteOrganigramaService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class OrganigramaDialogs {

    private final MovimientoService            movimientoService;
    private final ReporteOrganigramaService    reporteService;
    private final ReporteEntregaRecepcionService entregaService;
    private final Logger                       log;

    OrganigramaDialogs(MovimientoService movimientoService,
                       ReporteOrganigramaService reporteService,
                       ReporteEntregaRecepcionService entregaService,
                       Logger log) {
        this.movimientoService = movimientoService;
        this.reporteService    = reporteService;
        this.entregaService    = entregaService;
        this.log               = log;
    }

    void showAreaProductsDialog(String areaName, List<Producto> allProds, boolean soloAlertas, Scene scene) {
        List<Producto> prods = soloAlertas
            ? allProds.stream()
                .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
                .toList()
            : allProds;

        ButtonType btnVerInventario   = new ButtonType("Ver en Inventario →",        ButtonBar.ButtonData.OTHER);
        ButtonType btnExportarArea    = new ButtonType("Exportar área →",             ButtonBar.ButtonData.OTHER);
        ButtonType btnActaEntrega     = new ButtonType("Acta de Entrega-Recepción →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(areaName);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerInventario, btnExportarArea, btnActaEntrega, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(720);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        javafx.scene.Node verBtn = dialog.getDialogPane().lookupButton(btnVerInventario);
        if (verBtn != null) {
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                com.sibim.session.NavigationContext.setPendingAreaFilter(areaName);
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
            });
        }

        javafx.scene.Node exportBtn = dialog.getDialogPane().lookupButton(btnExportarArea);
        if (exportBtn != null) {
            exportBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                e.consume();
                DialogUtil.runAsyncWithProgress(scene, "Exportando…",
                    () -> reporteService.exportOrganigrama(Map.of(areaName, allProds)),
                    file -> DialogUtil.showExportResultDialog(scene, file),
                    ex -> { log.error("Error al exportar área desde organigrama", ex); NotificacionUtil.error(scene, "No se pudo exportar el área"); });
            });
        }

        javafx.scene.Node actaBtn = dialog.getDialogPane().lookupButton(btnActaEntrega);
        if (actaBtn != null) {
            actaBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                e.consume();
                DialogUtil.runAsyncWithProgress(scene, "Generando acta…",
                    () -> entregaService.exportEntregaRecepcionPdf(allProds),
                    file -> DialogUtil.showExportResultDialog(scene, file),
                    ex -> { log.error("Error al generar acta de entrega-recepción", ex); NotificacionUtil.error(scene, "No se pudo generar el acta"); });
            });
        }

        HBox header = soloAlertas
            ? DialogUtil.gradientHeader("mdi2a-alert-circle-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien agotado o con bajo stock" : " bienes agotados o con bajo stock"),
                AppColors.WARNING, AppColors.WARNING_D)
            : DialogUtil.gradientHeader("mdi2f-folder-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien registrado en esta área" : " bienes registrados en esta área"),
                AppColors.INDIGO, AppColors.PURPLE);

        // ── Tab: Bienes ──────────────────────────────────────────────
        TableView<Producto> tbl = new TableView<>(FXCollections.observableArrayList(prods));
        tbl.setPrefHeight(320);
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

        VBox bienesTab = new VBox(8, dlgSearch, tbl);

        // ── Tab: Movimientos ─────────────────────────────────────────
        TableView<Movimiento> movTbl = new TableView<>();
        movTbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movTbl.setPrefHeight(320);
        movTbl.setPlaceholder(new Label("Cargando movimientos…"));

        DateTimeFormatter movFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        TableColumn<Movimiento, String> mBien = new TableColumn<>("Bien");
        mBien.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : "—"));
        mBien.setPrefWidth(160);

        TableColumn<Movimiento, String> mTipo = new TableColumn<>("Tipo");
        mTipo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getTipo() != null ? c.getValue().getTipo().getEtiqueta() : "—"));
        mTipo.setPrefWidth(110);

        TableColumn<Movimiento, String> mCant = new TableColumn<>("Cantidad");
        mCant.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        mCant.setPrefWidth(75);

        TableColumn<Movimiento, String> mFecha = new TableColumn<>("Fecha");
        mFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreadoEn() != null ? c.getValue().getCreadoEn().format(movFmt) : "—"));
        mFecha.setPrefWidth(120);

        TableColumn<Movimiento, String> mUsuario = new TableColumn<>("Usuario");
        mUsuario.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : "—"));
        mUsuario.setPrefWidth(130);

        movTbl.getColumns().addAll(mBien, mTipo, mCant, mFecha, mUsuario);

        VBox movTab = new VBox(8, movTbl);

        // ── Tab: Por categoría ───────────────────────────────────────
        VBox catBox = new VBox(10);
        catBox.setPadding(new Insets(8, 0, 0, 0));
        Map<String, Long> catCounts = prods.stream()
            .filter(p -> p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank())
            .collect(Collectors.groupingBy(Producto::getCategoriaNombre, Collectors.counting()));
        var sortedCats = catCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(6)
            .toList();
        long maxCat = sortedCats.isEmpty() ? 1 : sortedCats.get(0).getValue();
        String[] catColors = { "#4338CA", "#0891B2", "#059669", "#D97706", "#DC2626", "#6D28D9" };
        for (int i = 0; i < sortedCats.size(); i++) {
            var entry = sortedCats.get(i);
            Label catName = new Label(entry.getKey());
            catName.getStyleClass().add("area-bar-name");
            catName.setMinWidth(130);
            catName.setMaxWidth(180);
            ProgressBar pb = new ProgressBar(maxCat > 0 ? (double) entry.getValue() / maxCat : 0);
            pb.getStyleClass().add("area-bar-pb");
            pb.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pb, Priority.ALWAYS);
            String color = catColors[i % catColors.length];
            pb.setStyle("-fx-accent: " + color + ";");
            Label cnt = new Label(entry.getValue() + " bienes");
            cnt.getStyleClass().add("area-bar-count");
            cnt.setMinWidth(70);
            HBox row = new HBox(10, catName, pb, cnt);
            row.setAlignment(Pos.CENTER_LEFT);
            catBox.getChildren().add(row);
        }
        if (sortedCats.isEmpty()) {
            catBox.getChildren().add(new Label("Sin datos de categorías"));
        }
        ScrollPane catScroll = new ScrollPane(catBox);
        catScroll.setFitToWidth(true);
        catScroll.setPrefHeight(320);
        catScroll.getStyleClass().add("edge-to-edge");
        VBox catTabContent = new VBox(catScroll);

        // ── TabPane ──────────────────────────────────────────────────
        Tab tabBienes    = new Tab("Bienes (" + prods.size() + ")", bienesTab);
        tabBienes.setClosable(false);
        Tab tabMovs      = new Tab("Movimientos (30 días)", movTab);
        tabMovs.setClosable(false);
        Tab tabCat       = new Tab("Por categoría", catTabContent);
        tabCat.setClosable(false);
        TabPane tabPane  = new TabPane(tabBienes, tabMovs, tabCat);
        tabPane.getStyleClass().add("tab-pane");

        // Load movimientos when that tab is selected
        tabMovs.setOnSelectionChanged(ev -> {
            if (!tabMovs.isSelected()) return;
            if (!movTbl.getItems().isEmpty()) return;
            movTbl.setPlaceholder(new Label("Cargando movimientos…"));
            List<String> ids = prods.stream().map(Producto::getId).toList();
            DialogUtil.runAsyncWithProgress(scene, "Cargando movimientos…",
                () -> {
                    Map<String, List<Movimiento>> byId = movimientoService.getByProductoIds(ids);
                    return byId.values().stream().flatMap(List::stream).toList();
                },
                movs -> {
                    movTbl.getItems().setAll(movs);
                    movTbl.setPlaceholder(new Label("Sin movimientos recientes"));
                    AnimationUtils.staggeredFadeInUp(List.of(movTbl), 270, 0);
                },
                ex -> {
                    log.error("Error cargando movimientos en organigrama dialog", ex);
                    movTbl.setPlaceholder(new Label("No se pudieron cargar los movimientos"));
                });
        });

        AnimationUtils.staggeredFadeInUp(List.of(header, tabPane), 270, 70);
        VBox content = new VBox(8, header, tabPane);
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

    @SuppressWarnings("unchecked")
    void showPrestamosAreaDialog(String areaName, List<Prestamo> prestamos, Scene scene) {
        ButtonType btnVerPrestamos = new ButtonType("Ver en Préstamos →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Préstamos activos — " + areaName);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerPrestamos, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        javafx.scene.Node verBtn = dialog.getDialogPane().lookupButton(btnVerPrestamos);
        if (verBtn != null) {
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("prestamos");
            });
        }

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-arrow-right-outline", areaName,
            prestamos.size() + (prestamos.size() == 1 ? " préstamo activo" : " préstamos activos"),
            AppColors.CYAN, AppColors.CYAN_D);

        TableView<Prestamo> tbl = new TableView<>();
        tbl.getStyleClass().add("data-table");
        tbl.setPrefHeight(320);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate hoy = LocalDate.now();

        TableColumn<Prestamo, String> cBien = new TableColumn<>("Bien");
        cBien.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : "—"));
        cBien.setPrefWidth(160);

        TableColumn<Prestamo, String> cNum = new TableColumn<>("No.");
        cNum.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNumero() != null ? c.getValue().getNumero() : "—"));
        cNum.setPrefWidth(90);

        TableColumn<Prestamo, String> cDest = new TableColumn<>("Destino");
        cDest.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getAreaDestino() != null ? c.getValue().getAreaDestino() : "—"));
        cDest.setPrefWidth(150);

        TableColumn<Prestamo, String> cVence = new TableColumn<>("Vence");
        cVence.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaDevolucionPrevista() != null
                ? c.getValue().getFechaDevolucionPrevista().format(fmt) : "—"));
        cVence.setPrefWidth(95);
        cVence.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                getStyleClass().removeAll("stock-low", "stock-ok");
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) return;
                setText(item);
                Prestamo p = getTableRow().getItem();
                if (p.getFechaDevolucionPrevista() != null && hoy.isAfter(p.getFechaDevolucionPrevista()))
                    getStyleClass().add("stock-low");
                else
                    getStyleClass().add("stock-ok");
            }
        });

        TableColumn<Prestamo, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getEstado() != null ? c.getValue().getEstado() : "—"));
        cEstado.setPrefWidth(90);
        cEstado.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "VENCIDO"   -> "cell-badge-danger";
            case "ACTIVO"    -> "cell-badge-success";
            case "DEVUELTO"  -> "cell-badge-info";
            default          -> "cell-badge-info";
        }));

        tbl.getColumns().addAll(cBien, cNum, cDest, cVence, cEstado);
        tbl.getItems().setAll(prestamos);

        AnimationUtils.staggeredFadeInUp(List.of(header, tbl), 270, 70);
        VBox content = new VBox(8, header, tbl);
        content.setPadding(new Insets(0, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    @SuppressWarnings("unchecked")
    void showComodatosAreaDialog(String areaName, List<Comodato> comodatos, Scene scene) {
        ButtonType btnVerComodatos = new ButtonType("Ver en Comodatos →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Comodatos vigentes — " + areaName);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerComodatos, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        javafx.scene.Node verBtn = dialog.getDialogPane().lookupButton(btnVerComodatos);
        if (verBtn != null) {
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("comodatos");
            });
        }

        HBox header = DialogUtil.gradientHeader("mdi2h-handshake-outline", areaName,
            comodatos.size() + (comodatos.size() == 1 ? " comodato vigente" : " comodatos vigentes"),
            AppColors.WARNING, AppColors.WARNING_D);

        TableView<Comodato> tbl = new TableView<>();
        tbl.getStyleClass().add("data-table");
        tbl.setPrefHeight(320);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        TableColumn<Comodato, String> cBien = new TableColumn<>("Bien");
        cBien.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : "—"));
        cBien.setPrefWidth(160);

        TableColumn<Comodato, String> cEntidad = new TableColumn<>("Entidad");
        cEntidad.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getEntidadReceptora() != null ? c.getValue().getEntidadReceptora() : "—"));
        cEntidad.setPrefWidth(170);

        TableColumn<Comodato, String> cVence = new TableColumn<>("Vence");
        cVence.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaFin() != null ? c.getValue().getFechaFin().format(fmt) : "—"));
        cVence.setPrefWidth(95);

        TableColumn<Comodato, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstadoEfectivo()));
        cEstado.setPrefWidth(100);
        cEstado.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "VENCIDO"    -> "cell-badge-danger";
            case "VIGENTE"    -> "cell-badge-success";
            case "CONCLUIDO"  -> "cell-badge-info";
            case "RESCINDIDO" -> "cell-badge-warning";
            default           -> "cell-badge-info";
        }));

        tbl.getColumns().addAll(cBien, cEntidad, cVence, cEstado);
        tbl.getItems().setAll(comodatos);

        AnimationUtils.staggeredFadeInUp(List.of(header, tbl), 270, 70);
        VBox content = new VBox(8, header, tbl);
        content.setPadding(new Insets(0, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
