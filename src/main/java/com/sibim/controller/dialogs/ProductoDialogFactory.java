package com.sibim.controller.dialogs;

import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.ImageUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds and drives the "Nuevo/Editar Bien" dialog.  The form fields for
 *  each tab live in {@link ProductoTabInfoFields}, {@link ProductoTabStockFields},
 *  and {@link ProductoTabPatrimonioFields}; this class only orchestrates the
 *  dialog shell, stepper, dirty-tracking, validation, and result conversion.
 *  Returns the validated {@link Producto} on OK, or empty on cancel — persistence
 *  stays the controller's job. */
public final class ProductoDialogFactory {

    private ProductoDialogFactory() {}

    public static Optional<Producto> show(Producto existing, List<Categoria> cats,
                                           Map<String, Image> thumbnailCache, Logger log,
                                           List<String> existingFotos) {
        boolean isNewProduct = existing == null || existing.getId() == null;

        // Load autocomplete suggestions (fast queries; best-effort)
        ProductoService productoService = new ProductoService();
        List<String> sugestMarcas      = productoService.getMarcas();
        List<String> sugestModelos     = productoService.getModelos();
        List<String> sugestProveedores = productoService.getProveedores();
        List<String> sugestUbicaciones = productoService.getUbicaciones();

        Dialog<Producto> dialog = DialogUtil.create(520);
        DialogUtil.styleOkButton(dialog.getDialogPane(), isNewProduct ? AppColors.PRIMARY_D : AppColors.SUCCESS);

        HBox dialogHeader = DialogUtil.gradientHeader(
            isNewProduct ? "mdi2p-package-variant" : "mdi2p-pencil",
            isNewProduct ? "Nuevo Bien Patrimonial" : "Editar Bien",
            isNewProduct ? "Registra un nuevo bien en el inventario municipal"
                         : "Actualiza los datos de " + existing.getNombre(),
            isNewProduct ? AppColors.PRIMARY_D : AppColors.SUCCESS,
            isNewProduct ? AppColors.PURPLE : AppColors.SUCCESS_D);

        Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());

        // markDirtyRef declared before tabs because InfoTab's gallery/factura
        // buttons need to call it through the ref at construction time.
        Runnable[] markDirtyRef = {null};
        // checkOkRef is filled in after checkOk is created below; passed into
        // InfoTab so the async código-debounce callback can re-run enablement.
        Runnable[] checkOkRef = {null};

        // ── Build tab field objects ──────────────────────────────────────────
        var infoTab = new ProductoTabInfoFields(existing, isNewProduct,
            existing != null ? existing.getId() : null,
            cats, log, dialog, markDirtyRef, checkOkRef, thumbnailCache, existingFotos, productoService);

        var stockTab = new ProductoTabStockFields(existing);

        var patrimonioTab = new ProductoTabPatrimonioFields(existing,
            sugestMarcas, sugestModelos, sugestProveedores, sugestUbicaciones);

        // ── TabPane ──────────────────────────────────────────────────────────
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab tabInfo       = new Tab("Información General", infoTab.grid);
        tabInfo.setGraphic(new FontIcon("mdi2i-information-outline"));
        Tab tabStock      = new Tab("Stock y Precios", stockTab.grid);
        tabStock.setGraphic(new FontIcon("mdi2c-chart-bar"));
        Tab tabPatrimonio = new Tab("Datos Patrimoniales", patrimonioTab.grid);
        tabPatrimonio.setGraphic(new FontIcon("mdi2b-badge-account-outline"));
        tabs.getTabs().addAll(tabInfo, tabStock, tabPatrimonio);
        tabs.getStyleClass().addAll("dlg-tabpane", "dlg-stepper");

        // ── Step indicator bar ───────────────────────────────────────────────
        String[] stepTitles = {"Datos básicos", "Stock y Precios", "Patrimonio"};
        VBox[] stepNodes = new VBox[3];
        Region[] connectors = new Region[2];
        HBox stepBar = new HBox(0);
        stepBar.setAlignment(Pos.CENTER);
        stepBar.getStyleClass().add("stepper-bar");
        for (int si = 0; si < 3; si++) {
            final int stepIdx = si;
            javafx.scene.layout.StackPane circle = new javafx.scene.layout.StackPane();
            circle.getStyleClass().add("stepper-circle");
            Label numLbl = new Label(String.valueOf(si + 1));
            numLbl.getStyleClass().add("stepper-num");
            circle.getChildren().add(numLbl);
            Label nameLbl = new Label(stepTitles[si]);
            nameLbl.getStyleClass().add("stepper-label");
            VBox step = new VBox(4, circle, nameLbl);
            step.setAlignment(Pos.CENTER);
            step.getStyleClass().add("stepper-step");
            step.setOnMouseClicked(e -> tabs.getSelectionModel().select(stepIdx));
            String[] stepTooltips = {
                "Nombre, Código, Categoría, Área, Ubicación, Imagen",
                "Stock, Precio unitario, Precio total, Fecha de vencimiento",
                "Proveedor, Marca, Modelo, N° serie, Depreciación, Estado"
            };
            Tooltip.install(step, new Tooltip(stepTooltips[si]));
            stepNodes[si] = step;
            if (si < 2) {
                Region conn = new Region();
                conn.getStyleClass().add("stepper-connector");
                conn.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(conn, Priority.ALWAYS);
                connectors[si] = conn;
                stepBar.getChildren().addAll(step, conn);
            } else {
                stepBar.getChildren().add(step);
            }
        }
        stepNodes[0].getStyleClass().add("stepper-step-active");

        Label lblFormError = new Label();
        lblFormError.getStyleClass().add("field-error-label");
        lblFormError.setVisible(false);
        lblFormError.setManaged(false);
        lblFormError.setWrapText(true);

        // Flag set just before firing okBtn so setOnCloseRequest skips the
        // "Descartar cambios?" prompt when the user is intentionally saving.
        boolean[] savingNow = {false};

        String submitLabel = isNewProduct ? "Guardar bien" : "Guardar cambios";
        Button btnGuardar = new Button(submitLabel);
        btnGuardar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnGuardar.getStyleClass().add("form-submit-btn");
        if (!isNewProduct) btnGuardar.getStyleClass().add("form-submit-btn-edit");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);
        btnGuardar.setDisable(true);
        btnGuardar.setOnAction(e -> {
            boolean inv = false;
            if (infoTab.fNombre.getText().isBlank()) { infoTab.fNombre.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); inv = true; }
            boolean codigoManual = !isNewProduct || (infoTab.fArea.getValue() != null && !com.sibim.config.AreaCodigos.tienePrefijo(infoTab.fArea.getValue()));
            if (codigoManual && infoTab.fCodigo.getText().isBlank()) { infoTab.fCodigo.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); inv = true; }
            if (infoTab.fArea.getValue() == null || infoTab.fArea.getValue().isBlank()) { infoTab.fArea.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); inv = true; }
            if (infoTab.fCat.getValue() == null) { infoTab.fCat.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); inv = true; }
            String pcText = stockTab.fPrecioC.getText().trim(), pvText = stockTab.fPrecioV.getText().trim();
            try { var bd = new java.math.BigDecimal(pcText); if (bd.signum() < 0) throw new NumberFormatException(); stockTab.fPrecioC.getStyleClass().remove("field-error"); }
            catch (Exception ex) { stockTab.fPrecioC.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); inv = true; }
            try { var bd = new java.math.BigDecimal(pvText); if (bd.signum() < 0) throw new NumberFormatException(); stockTab.fPrecioV.getStyleClass().remove("field-error"); }
            catch (Exception ex) { stockTab.fPrecioV.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); inv = true; }
            if (stockTab.fStockMin.getValue() > stockTab.fStockMax.getValue()) { stockTab.fStockMin.getStyleClass().add("field-error"); stockTab.fStockMax.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); inv = true; }
            if (inv) {
                lblFormError.setText("Completa los campos obligatorios marcados en rojo. Los precios deben ser números válidos y no negativos (ej. 1500.00), y el Stock Mínimo no puede superar al Stock Máximo.");
                lblFormError.setVisible(true); lblFormError.setManaged(true);
                AnimationUtils.shake(lblFormError);
                return;
            }
            savingNow[0] = true;
            if (okBtn instanceof Button b) b.fire();
        });

        Runnable hideFormError = () -> { lblFormError.setVisible(false); lblFormError.setManaged(false); };
        infoTab.fNombre.textProperty().addListener((o, a, b) -> {
            if (!b.isBlank()) { infoTab.fNombre.getStyleClass().remove("field-error"); infoTab.lblNombreHint.setVisible(false); infoTab.lblNombreHint.setManaged(false); }
            hideFormError.run();
        });
        infoTab.fCodigo.textProperty().addListener((o, a, b) -> { if (!b.isBlank()) infoTab.fCodigo.getStyleClass().remove("field-error"); hideFormError.run(); });
        infoTab.fArea.valueProperty().addListener((o, a, b) -> {
            if (b != null && !b.isBlank()) { infoTab.fArea.getStyleClass().remove("field-error"); infoTab.lblAreaHint.setVisible(false); infoTab.lblAreaHint.setManaged(false); }
            hideFormError.run();
        });
        infoTab.fCat.valueProperty().addListener((o, a, b) -> {
            if (b != null) { infoTab.fCat.getStyleClass().remove("field-error"); infoTab.lblCatHint.setVisible(false); infoTab.lblCatHint.setManaged(false); }
            hideFormError.run();
        });
        stockTab.fPrecioC.textProperty().addListener((o, a, b) -> { stockTab.fPrecioC.getStyleClass().remove("field-error"); hideFormError.run(); });
        stockTab.fPrecioV.textProperty().addListener((o, a, b) -> { stockTab.fPrecioV.getStyleClass().remove("field-error"); hideFormError.run(); });
        stockTab.fStockMin.valueProperty().addListener((o, a, b) -> { stockTab.fStockMin.getStyleClass().remove("field-error"); stockTab.fStockMax.getStyleClass().remove("field-error"); hideFormError.run(); });
        stockTab.fStockMax.valueProperty().addListener((o, a, b) -> { stockTab.fStockMin.getStyleClass().remove("field-error"); stockTab.fStockMax.getStyleClass().remove("field-error"); hideFormError.run(); });

        // ── checkOk ──────────────────────────────────────────────────────────
        Button[] navNextRef = {null};
        if (okBtn != null) {
            Runnable checkOk = () -> {
                boolean needsCodigo = !isNewProduct || (infoTab.fArea.getValue() != null && !com.sibim.config.AreaCodigos.tienePrefijo(infoTab.fArea.getValue()));
                boolean codigoError = needsCodigo && infoTab.lblCodigoHint.getStyleClass().contains("field-hint-error");
                boolean codigoBlank = needsCodigo && infoTab.fCodigo.getText().isBlank();
                boolean invalid = infoTab.fNombre.getText().isBlank() || codigoBlank
                    || infoTab.fArea.getValue() == null || infoTab.fArea.getValue().isBlank()
                    || infoTab.fCat.getValue() == null || codigoError;
                okBtn.setDisable(invalid);
                btnGuardar.setDisable(invalid);
                if (navNextRef[0] != null && tabs.getSelectionModel().getSelectedIndex() == 2)
                    navNextRef[0].setDisable(invalid);
            };
            checkOk.run();
            checkOkRef[0] = checkOk;
            infoTab.fNombre.textProperty().addListener((o, a, b) -> checkOk.run());
            infoTab.fCodigo.textProperty().addListener((o, a, b) -> checkOk.run());
            infoTab.fArea.valueProperty().addListener((o, a, b)  -> checkOk.run());
            infoTab.fCat.valueProperty().addListener((o, a, b)   -> checkOk.run());
        }

        ScrollPane tabsScroll = new ScrollPane(tabs);
        tabsScroll.setFitToWidth(true);
        tabsScroll.setMaxHeight(420);
        tabsScroll.getStyleClass().add("dlg-tabs-scroll");

        // ── Stepper navigation buttons ───────────────────────────────────────
        Button btnPrev = new Button("Anterior");
        btnPrev.setGraphic(new FontIcon("mdi2c-chevron-left"));
        btnPrev.getStyleClass().add("btn-secondary");
        btnPrev.setVisible(false); btnPrev.setManaged(false);
        btnPrev.setOnAction(e -> tabs.getSelectionModel().select(
            tabs.getSelectionModel().getSelectedIndex() - 1));

        Button btnNextNav = new Button("Siguiente");
        btnNextNav.setGraphic(new FontIcon("mdi2c-chevron-right"));
        btnNextNav.setGraphicTextGap(8);
        btnNextNav.getStyleClass().addAll("btn-primary");
        btnNextNav.setOnAction(e -> {
            int idx = tabs.getSelectionModel().getSelectedIndex();
            if (idx < 2) tabs.getSelectionModel().select(idx + 1);
            else btnGuardar.fire();
        });
        navNextRef[0] = btnNextNav;

        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, ov, nv) -> {
            int idx = nv.intValue();
            for (int si = 0; si < stepNodes.length; si++) {
                stepNodes[si].getStyleClass().removeAll("stepper-step-active", "stepper-step-done");
                if (si < idx) stepNodes[si].getStyleClass().add("stepper-step-done");
                else if (si == idx) stepNodes[si].getStyleClass().add("stepper-step-active");
            }
            for (int ci = 0; ci < connectors.length; ci++) {
                connectors[ci].getStyleClass().remove("stepper-connector-done");
                if (ci < idx) connectors[ci].getStyleClass().add("stepper-connector-done");
            }
            btnPrev.setVisible(idx > 0); btnPrev.setManaged(idx > 0);
            boolean onLast = (idx == 2);
            btnNextNav.setText(onLast ? submitLabel : "Siguiente");
            btnNextNav.setGraphic(new FontIcon(onLast ? "mdi2c-check-circle-outline" : "mdi2c-chevron-right"));
            btnNextNav.setDisable(onLast && btnGuardar.isDisable());
        });

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);
        HBox navBar = new HBox(10, btnPrev, navSpacer, btnNextNav);
        navBar.getStyleClass().add("stepper-nav-bar");

        VBox.setMargin(lblFormError, new Insets(4, 22, 0, 22));
        VBox dialogContent = new VBox(0, dialogHeader, stepBar, tabsScroll, lblFormError, navBar);
        dialog.getDialogPane().setContent(dialogContent);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(dialogHeader, stepBar, tabs), 280, 70);

        // ── Dirty tracking ───────────────────────────────────────────────────
        boolean[] dirty = {false};
        Runnable markDirty = () -> dirty[0] = true;
        markDirtyRef[0] = markDirty;
        infoTab.wireDirty(markDirty);
        stockTab.wireDirty(markDirty);
        patrimonioTab.wireDirty(markDirty);

        javafx.scene.Node cancelBtn = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                if (dirty[0] && !ConfirmacionUtil.confirmar("Descartar cambios",
                        "Tienes cambios sin guardar.\n¿Seguro que deseas descartarlos?"))
                    e.consume();
            });
        }
        dialog.setOnCloseRequest(e -> {
            if (!savingNow[0] && dirty[0] && !ConfirmacionUtil.confirmar("Descartar cambios",
                    "Tienes cambios sin guardar.\n¿Seguro que deseas descartarlos?"))
                e.consume();
        });

        dialogContent.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != javafx.scene.input.KeyCode.ENTER || e.isAltDown()) return;
            javafx.scene.Node t = (javafx.scene.Node) e.getTarget();
            for (javafx.scene.Node n = t; n != null; n = n.getParent()) {
                if (n instanceof ComboBox<?> cb && cb.isShowing()) return;
                if (n instanceof TextArea) return;
            }
            if (okBtn instanceof Button b && !b.isDisabled()) { b.fire(); e.consume(); }
        });
        Platform.runLater(() -> infoTab.fNombre.requestFocus());

        // ── Result converter ─────────────────────────────────────────────────
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            boolean invalid = false;
            Node firstErrField = null;
            if (infoTab.fNombre.getText().isBlank()) { infoTab.fNombre.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = infoTab.fNombre; invalid = true; }
            else infoTab.fNombre.getStyleClass().remove("field-error");
            boolean codigoManual = !isNewProduct || (infoTab.fArea.getValue() != null && !com.sibim.config.AreaCodigos.tienePrefijo(infoTab.fArea.getValue()));
            if (codigoManual && infoTab.fCodigo.getText().isBlank()) { infoTab.fCodigo.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = infoTab.fCodigo; invalid = true; }
            else infoTab.fCodigo.getStyleClass().remove("field-error");
            if (infoTab.fArea.getValue() == null || infoTab.fArea.getValue().isBlank()) { infoTab.fArea.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = infoTab.fArea; invalid = true; }
            else infoTab.fArea.getStyleClass().remove("field-error");
            if (infoTab.fCat.getValue() == null) { infoTab.fCat.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = infoTab.fCat; invalid = true; }
            else infoTab.fCat.getStyleClass().remove("field-error");

            BigDecimal precioCompra = null, precioVenta = null;
            try {
                precioCompra = new BigDecimal(stockTab.fPrecioC.getText().trim());
                if (precioCompra.signum() < 0) throw new NumberFormatException("negativo");
                stockTab.fPrecioC.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                stockTab.fPrecioC.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
            }
            try {
                precioVenta = new BigDecimal(stockTab.fPrecioV.getText().trim());
                if (precioVenta.signum() < 0) throw new NumberFormatException("negativo");
                stockTab.fPrecioV.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                stockTab.fPrecioV.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
            }

            if (stockTab.fStockMin.getValue() > stockTab.fStockMax.getValue()) {
                stockTab.fStockMin.getStyleClass().add("field-error");
                stockTab.fStockMax.getStyleClass().add("field-error");
                tabs.getSelectionModel().select(1);
                invalid = true;
            } else {
                stockTab.fStockMin.getStyleClass().remove("field-error");
                stockTab.fStockMax.getStyleClass().remove("field-error");
            }

            if (invalid) {
                lblFormError.setText("Completa los campos obligatorios marcados en rojo. Los precios deben ser números válidos y no negativos (ej. 1500.00), y el Stock Mínimo no puede superar al Stock Máximo.");
                lblFormError.setVisible(true);
                lblFormError.setManaged(true);
                AnimationUtils.shake(lblFormError);
                if (firstErrField != null) AnimationUtils.shake(firstErrField);
                return null;
            }

            DialogUtil.commitSpinner(stockTab.fStock);
            DialogUtil.commitSpinner(stockTab.fStockMin);
            DialogUtil.commitSpinner(stockTab.fStockMax);

            Producto p = isNewProduct ? new Producto() : existing;
            String photoId = p.getId() != null ? p.getId() : UUID.randomUUID().toString();
            p.setNombre(infoTab.fNombre.getText().trim());
            p.setCodigo(infoTab.fCodigo.getText().trim());
            p.setDescripcion(infoTab.fDesc.getText().trim());
            if (infoTab.fCat.getValue() != null) {
                p.setCategoriaId(infoTab.fCat.getValue().getId());
                p.setCategoriaNombre(infoTab.fCat.getValue().getNombre());
                p.setCategoriaColor(infoTab.fCat.getValue().getColor());
            }
            p.setPrecioCompra(precioCompra);
            p.setPrecioVenta(precioVenta);
            p.setStockActual(stockTab.fStock.getValue());
            p.setStockMinimo(stockTab.fStockMin.getValue());
            p.setStockMaximo(stockTab.fStockMax.getValue());
            p.setUnidad(stockTab.fUnidad.getValue());
            p.setProveedor(patrimonioTab.fProveedor.getText().trim());
            p.setMarca(patrimonioTab.fMarca.getText().trim().isEmpty() ? null : patrimonioTab.fMarca.getText().trim());
            p.setModelo(patrimonioTab.fModelo.getText().trim().isEmpty() ? null : patrimonioTab.fModelo.getText().trim());
            p.setNumeroSerie(patrimonioTab.fNumeroSerie.getText().trim().isEmpty() ? null : patrimonioTab.fNumeroSerie.getText().trim());
            p.setUbicacion(patrimonioTab.fUbicacion.getText().trim());
            p.setResguardante(patrimonioTab.fResguardante.getText().trim());
            p.setFechaVencimiento(stockTab.fVenc.getValue());
            p.setFechaAdquisicion(patrimonioTab.fFechaAdq.getValue());
            p.setVidaUtilAnios(patrimonioTab.fVidaUtil.getValue());
            try {
                String vrText = patrimonioTab.fValorResidual.getText().trim().replace(",", ".");
                p.setValorResidual(vrText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(vrText));
            } catch (NumberFormatException ignored) {
                p.setValorResidual(BigDecimal.ZERO);
            }
            p.setArea(infoTab.fArea.getValue());
            p.setEtiquetado(patrimonioTab.fEtiquetado.isSelected());
            p.setProximaRevision(patrimonioTab.fProximaRevision.getValue());
            String notasMantTxt = patrimonioTab.fNotasMant.getText().trim();
            p.setNotasMantenimiento(notasMantTxt.isEmpty() ? null : notasMantTxt);
            String estadoFisicoVal = patrimonioTab.fEstadoFisico.getValue();
            p.setEstadoFisico(estadoFisicoVal == null || estadoFisicoVal.isBlank() ? null : estadoFisicoVal);
            String numFactTxt = patrimonioTab.fNumeroFactura.getText().trim();
            p.setNumeroFactura(numFactTxt.isEmpty() ? null : numFactTxt);
            p.setClaveArmonizada(patrimonioTab.fClaveArm.getText().trim().isEmpty() ? null : patrimonioTab.fClaveArm.getText().trim());
            p.setColor(patrimonioTab.fColor.getText().trim().isEmpty() ? null : patrimonioTab.fColor.getText().trim());
            p.setTipoBien(patrimonioTab.fTipoBien.getText().trim().isEmpty() ? null : patrimonioTab.fTipoBien.getText().trim());
            p.setNoMotor(patrimonioTab.fNoMotor.getText().trim().isEmpty() ? null : patrimonioTab.fNoMotor.getText().trim());
            p.setNoTarjetaCirculacion(patrimonioTab.fNoTarjeta.getText().trim().isEmpty() ? null : patrimonioTab.fNoTarjeta.getText().trim());
            p.setNoPolizaSeguro(patrimonioTab.fNoPoliza.getText().trim().isEmpty() ? null : patrimonioTab.fNoPoliza.getText().trim());
            dirty[0] = false;

            // ── Process and save photos ──────────────────────────────────────
            java.util.List<String> savedFotos = new java.util.ArrayList<>();
            Path imgDir = imgDir();
            boolean useStorage = com.sibim.util.SupabaseStorage.isAvailable();
            try {
                if (!useStorage) Files.createDirectories(imgDir);
                for (String rawUrl : infoTab.fotosHolder) {
                    try {
                        if (com.sibim.util.SupabaseStorage.isRemoteUrl(rawUrl)) {
                            savedFotos.add(rawUrl);
                            continue;
                        }
                        Path src = java.nio.file.Path.of(rawUrl);
                        String remoteName = photoId + "_" + savedFotos.size() + ".jpg";
                        if (useStorage) {
                            java.io.File tmp = Files.createTempFile("sibim-", ".jpg").toFile();
                            try {
                                ImageUtils.resizeAndSave(src.toFile(), tmp);
                                String uploadedUrl = com.sibim.util.SupabaseStorage.upload(tmp, remoteName);
                                savedFotos.add(uploadedUrl);
                            } catch (Exception uploadEx) {
                                log.warn("Upload a Storage falló para '{}', guardando local: {}", p.getNombre(), uploadEx.getMessage());
                                Files.createDirectories(imgDir);
                                Path dest = imgDir.resolve(remoteName);
                                ImageUtils.resizeAndSave(src.toFile(), dest.toFile());
                                savedFotos.add(dest.toString());
                            } finally { tmp.delete(); }
                        } else {
                            Path dest = imgDir.resolve(remoteName);
                            if (!src.equals(dest)) {
                                ImageUtils.resizeAndSave(src.toFile(), dest.toFile());
                                thumbnailCache.remove(dest.toString());
                            }
                            savedFotos.add(dest.toString());
                        }
                    } catch (Exception ex) {
                        log.error("No se pudo procesar imagen del bien '{}': {}", p.getNombre(), rawUrl, ex);
                    }
                }
            } catch (Exception ex) {
                log.error("No se pudo crear el directorio de imágenes para '{}'", p.getNombre(), ex);
            }
            p.setFotosUrls(savedFotos);
            p.setFotoUrl(savedFotos.isEmpty() ? null : savedFotos.get(0));

            // ── Process factura ──────────────────────────────────────────────
            String factUrlFinal = infoTab.facturaHolder[0];
            if (factUrlFinal != null && !factUrlFinal.isBlank()) {
                if (!com.sibim.util.SupabaseStorage.isRemoteUrl(factUrlFinal)) {
                    try {
                        if (useStorage) {
                            java.io.File tmp = Files.createTempFile("sibim-fact-", ".jpg").toFile();
                            try {
                                ImageUtils.resizeAndSave(Path.of(factUrlFinal).toFile(), tmp);
                                factUrlFinal = com.sibim.util.SupabaseStorage.upload(tmp, photoId + "_factura.jpg");
                            } finally { tmp.delete(); }
                        } else {
                            Path factDir = ImageUtils.storageDir().resolve("facturas");
                            Files.createDirectories(factDir);
                            Path dest = factDir.resolve(photoId + ".jpg");
                            Path src = Path.of(factUrlFinal);
                            if (!src.equals(dest)) {
                                ImageUtils.resizeAndSave(src.toFile(), dest.toFile());
                                thumbnailCache.remove(dest.toString());
                            }
                            factUrlFinal = dest.toString();
                        }
                    } catch (Exception ex) {
                        log.error("No se pudo procesar factura del bien '{}', se conserva la anterior", p.getNombre(), ex);
                        factUrlFinal = existing != null ? existing.getFacturaUrl() : null;
                    }
                }
                p.setFacturaUrl(factUrlFinal);
            } else {
                p.setFacturaUrl(null);
            }
            return p;
        });

        return dialog.showAndWait();
    }

    private static Path imgDir() {
        return ImageUtils.storageDir();
    }

    /** Dice-coefficient bigram similarity. Package-private so
     *  {@link ProductoTabInfoFields} can use it for the name-duplicate warning. */
    static double diceSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0.0;
        java.util.Set<String> bigrams = new java.util.HashSet<>();
        for (int i = 0; i < a.length() - 1; i++) bigrams.add(a.substring(i, i + 2));
        int shared = 0;
        for (int i = 0; i < b.length() - 1; i++) { if (bigrams.contains(b.substring(i, i + 2))) shared++; }
        return (2.0 * shared) / ((a.length() - 1) + (b.length() - 1));
    }
}
