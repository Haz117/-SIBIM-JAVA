package com.sibim.controller.dialogs;

import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.ImageUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds and drives the "Nuevo/Editar Bien" dialog. Extracted out of
 *  ProductosController (which had grown a ~300-line inline dialog method) so
 *  the controller only deals with the table/filters/persistence and this
 *  class only deals with the form. Returns the validated {@link Producto}
 *  (with its photo already resized/saved to disk) on OK, or empty on cancel —
 *  persistence (service.save + list/stat refresh) stays the controller's job. */
public final class ProductoDialogFactory {

    private ProductoDialogFactory() {}

    public static Optional<Producto> show(Producto existing, List<Categoria> cats,
                                           Map<String, Image> thumbnailCache, Logger log) {
        boolean isNewProduct = existing == null;
        Dialog<Producto> dialog = DialogUtil.create(520);
        DialogUtil.styleOkButton(dialog.getDialogPane(), isNewProduct ? "#4F46E5" : "#059669");

        HBox dialogHeader = DialogUtil.gradientHeader(
            isNewProduct ? "📦" : "✏",
            isNewProduct ? "Nuevo Bien Patrimonial" : "Editar Bien",
            isNewProduct ? "Registra un nuevo bien en el inventario municipal"
                         : "Actualiza los datos de " + existing.getNombre(),
            isNewProduct ? "#4F46E5" : "#059669",
            isNewProduct ? "#7C3AED" : "#047857");

        Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());
        // The visible submit action is btnGuardar inside the content; hide the bar's OK.
        if (okBtn != null) { okBtn.setVisible(false); okBtn.setManaged(false); }

        // ── Tab: Información General ──
        GridPane gridInfo = DialogUtil.formGrid(120);

        TextField fNombre = new TextField(existing != null ? existing.getNombre() : "");
        fNombre.setPromptText("Nombre descriptivo del bien");
        fNombre.getStyleClass().add("form-input");
        TextField fCodigo = new TextField(existing != null ? existing.getCodigo() : "");
        fCodigo.setPromptText("Código único de inventario");
        fCodigo.getStyleClass().add("form-input");

        // Inline código uniqueness check — debounced 280ms
        ProductoRepository codigoRepo = new ProductoRepository();
        String existingId = existing != null ? existing.getId() : null;
        Label lblCodigoHint = new Label();
        lblCodigoHint.getStyleClass().add("field-hint");
        lblCodigoHint.setVisible(false); lblCodigoHint.setManaged(false);
        javafx.animation.Timeline[] codigoDebounce = {null};
        fCodigo.textProperty().addListener((obs, old, val) -> {
            if (codigoDebounce[0] != null) codigoDebounce[0].stop();
            lblCodigoHint.setVisible(false); lblCodigoHint.setManaged(false);
            if (val.isBlank()) return;
            codigoDebounce[0] = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(280), e -> AppExecutor.submit(() -> {
                    try {
                        boolean exists = codigoRepo.existsByCodigo(val.trim(), existingId);
                        Platform.runLater(() -> {
                            lblCodigoHint.setText(exists ? "✕  Este código ya existe" : "✓  Disponible");
                            lblCodigoHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                            lblCodigoHint.getStyleClass().add(exists ? "field-hint-error" : "field-hint-ok");
                            lblCodigoHint.setVisible(true); lblCodigoHint.setManaged(true);
                            if (exists) fCodigo.getStyleClass().add("field-error");
                            else fCodigo.getStyleClass().remove("field-error");
                        });
                    } catch (Exception ignored) {}
                })));
            codigoDebounce[0].play();
        });
        TextArea fDesc = new TextArea(existing != null && existing.getDescripcion() != null ? existing.getDescripcion() : "");
        fDesc.setPrefRowCount(2); fDesc.setPromptText("Descripción opcional");
        fDesc.getStyleClass().add("form-input");
        ComboBox<Categoria> fCat = new ComboBox<>(FXCollections.observableArrayList(cats));
        fCat.setMaxWidth(Double.MAX_VALUE);
        fCat.setPromptText("Seleccionar categoría");
        fCat.getStyleClass().add("form-input");
        fCat.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Categoria c) { return c == null ? "" : c.getNombre(); }
            public Categoria fromString(String s) { return null; }
        });
        if (existing != null) cats.stream()
            .filter(c -> c.getId().equals(existing.getCategoriaId())).findFirst().ifPresent(fCat::setValue);

        List<String> areaNames = new java.util.ArrayList<>(com.sibim.config.Areas.getAllAreaNames());
        ComboBox<String> fArea = new ComboBox<>(FXCollections.observableArrayList(areaNames));
        fArea.setMaxWidth(Double.MAX_VALUE);
        fArea.setPromptText("Área responsable");
        fArea.setValue(existing != null ? existing.getArea() : SessionManager.getCurrentUser().getArea());
        if (SessionManager.isDireccion()) fArea.setDisable(true);
        fArea.getStyleClass().add("form-input");

        TextField fProveedor = new TextField(existing != null && existing.getProveedor() != null ? existing.getProveedor() : "");
        fProveedor.setPromptText("Nombre del proveedor");
        fProveedor.getStyleClass().add("form-input");
        TextField fUbicacion = new TextField(existing != null && existing.getUbicacion() != null ? existing.getUbicacion() : "");
        fUbicacion.setPromptText("Ubicación física");
        fUbicacion.getStyleClass().add("form-input");
        TextField fResguardante = new TextField(existing != null && existing.getResguardante() != null ? existing.getResguardante() : "");
        fResguardante.setPromptText("Persona responsable del resguardo (nombre completo)");
        fResguardante.getStyleClass().add("form-input");

        // ── Image picker ──
        String[] fotoHolder = { existing != null ? existing.getFotoUrl() : null };

        ImageView imgPreview = new ImageView();
        imgPreview.setFitWidth(150); imgPreview.setFitHeight(112);
        imgPreview.setPreserveRatio(true);

        Label imgPlaceholder = new Label("📷\nSin imagen");
        imgPlaceholder.getStyleClass().add("dlg-img-placeholder");
        imgPlaceholder.setAlignment(Pos.CENTER);

        StackPane imgBox = new StackPane(imgPlaceholder, imgPreview);
        imgBox.setPrefSize(150, 112);
        imgBox.getStyleClass().add("dlg-img-box");

        Runnable loadImg = () -> {
            if (fotoHolder[0] != null && !fotoHolder[0].isBlank()) {
                try {
                    Image img = new Image(Path.of(fotoHolder[0]).toUri().toString(), 150, 112, true, true, true);
                    imgPreview.setImage(img);
                    imgPlaceholder.setVisible(false);
                } catch (Exception ignored) { imgPlaceholder.setVisible(true); }
            } else {
                imgPreview.setImage(null);
                imgPlaceholder.setVisible(true);
            }
        };
        loadImg.run();

        Button btnSelImg    = new Button("📷  Seleccionar");
        Button btnQuitarImg = new Button("✕  Quitar");
        btnSelImg.getStyleClass().add("btn-secondary");
        btnQuitarImg.getStyleClass().add("btn-secondary");
        btnQuitarImg.setDisable(fotoHolder[0] == null || fotoHolder[0].isBlank());

        btnSelImg.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar imagen del bien");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png","*.jpg","*.jpeg","*.gif","*.bmp","*.webp"));
            File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                fotoHolder[0] = file.getAbsolutePath();
                loadImg.run();
                btnQuitarImg.setDisable(false);
            }
        });
        btnQuitarImg.setOnAction(ev -> {
            fotoHolder[0] = null;
            loadImg.run();
            btnQuitarImg.setDisable(true);
        });

        VBox imgSection = new VBox(6, imgBox, new HBox(6, btnSelImg, btnQuitarImg));

        VBox codigoBox = new VBox(2, fCodigo, lblCodigoHint);
        int r = 0;
        gridInfo.add(DialogUtil.fieldLabel("Nombre *"),    0, r); gridInfo.add(fNombre,    1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Código *"),     0, r); gridInfo.add(codigoBox,  1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Descripción"), 0, r); gridInfo.add(fDesc,      1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Categoría *"), 0, r); gridInfo.add(fCat,       1, r++);
        gridInfo.add(DialogUtil.fieldLabelWithHelp("Área *",
            "Secretaría o Dirección responsable del bien.\n" +
            "Solo los usuarios de esa área podrán gestionarlo.\n" +
            "Para DIRECCIÓN el área se fija automáticamente."),
                                                             0, r); gridInfo.add(fArea,      1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Proveedor"),   0, r); gridInfo.add(fProveedor, 1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Ubicación"),   0, r); gridInfo.add(fUbicacion, 1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Resguardante"), 0, r); gridInfo.add(fResguardante, 1, r++);
        gridInfo.add(DialogUtil.fieldLabel("Imagen"),      0, r); gridInfo.add(imgSection, 1, r);

        // ── Tab: Stock & Precios ──
        GridPane gridStock = DialogUtil.formGrid(140);

        Spinner<Integer> fStock    = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockActual() : 0);
        fStock.setEditable(true); fStock.setMaxWidth(Double.MAX_VALUE);
        fStock.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStock);
        Spinner<Integer> fStockMin = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockMinimo() : 0);
        fStockMin.setEditable(true); fStockMin.setMaxWidth(Double.MAX_VALUE);
        fStockMin.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStockMin);
        Spinner<Integer> fStockMax = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockMaximo() : 100);
        fStockMax.setEditable(true); fStockMax.setMaxWidth(Double.MAX_VALUE);
        fStockMax.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStockMax);
        ComboBox<UnidadMedida> fUnidad = new ComboBox<>(
            FXCollections.observableArrayList(UnidadMedida.values()));
        fUnidad.setValue(existing != null ? existing.getUnidad() : UnidadMedida.PIEZA);
        fUnidad.setMaxWidth(Double.MAX_VALUE);
        fUnidad.getStyleClass().add("form-input");
        TextField fPrecioC = new TextField(existing != null && existing.getPrecioCompra() != null
            ? existing.getPrecioCompra().toPlainString() : "0");
        fPrecioC.setPromptText("0.00");
        fPrecioC.getStyleClass().add("form-input");
        Label lblPrecioCHint = new Label();
        lblPrecioCHint.getStyleClass().add("field-hint");
        lblPrecioCHint.setVisible(false); lblPrecioCHint.setManaged(false);
        fPrecioC.textProperty().addListener((obs, old, val) -> {
            try { new BigDecimal(val.trim());
                lblPrecioCHint.setVisible(false); lblPrecioCHint.setManaged(false);
                fPrecioC.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                lblPrecioCHint.setText("Formato inválido — usa números (ej. 1500.00)");
                lblPrecioCHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                lblPrecioCHint.getStyleClass().add("field-hint-error");
                lblPrecioCHint.setVisible(true); lblPrecioCHint.setManaged(true);
                fPrecioC.getStyleClass().add("field-error");
            }
        });

        TextField fPrecioV = new TextField(existing != null && existing.getPrecioVenta() != null
            ? existing.getPrecioVenta().toPlainString() : "0");
        fPrecioV.setPromptText("0.00");
        fPrecioV.getStyleClass().add("form-input");
        Label lblPrecioVHint = new Label();
        lblPrecioVHint.getStyleClass().add("field-hint");
        lblPrecioVHint.setVisible(false); lblPrecioVHint.setManaged(false);
        fPrecioV.textProperty().addListener((obs, old, val) -> {
            try { new BigDecimal(val.trim());
                lblPrecioVHint.setVisible(false); lblPrecioVHint.setManaged(false);
                fPrecioV.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                lblPrecioVHint.setText("Formato inválido — usa números (ej. 1500.00)");
                lblPrecioVHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                lblPrecioVHint.getStyleClass().add("field-hint-error");
                lblPrecioVHint.setVisible(true); lblPrecioVHint.setManaged(true);
                fPrecioV.getStyleClass().add("field-error");
            }
        });
        DatePicker fVenc = new DatePicker(existing != null ? existing.getFechaVencimiento() : null);
        fVenc.setMaxWidth(Double.MAX_VALUE);
        fVenc.getStyleClass().add("form-input");

        int rs = 0;
        gridStock.add(DialogUtil.fieldLabel("Stock Actual"),    0, rs); gridStock.add(fStock,    1, rs++);
        gridStock.add(DialogUtil.fieldLabelWithHelp("Stock Mínimo",
            "Cuando el stock baje de este número se generará\nuna alerta automática en el módulo de Alertas."),
                                                              0, rs); gridStock.add(fStockMin, 1, rs++);
        gridStock.add(DialogUtil.fieldLabelWithHelp("Stock Máximo",
            "Límite de referencia para sobre-stock.\n" +
            "No bloquea entradas; sirve para reportes y alertas de exceso."),
                                                              0, rs); gridStock.add(fStockMax, 1, rs++);
        gridStock.add(DialogUtil.fieldLabel("Unidad"),          0, rs); gridStock.add(fUnidad,   1, rs++);
        gridStock.add(DialogUtil.fieldLabel("Precio Compra"),   0, rs); gridStock.add(new VBox(2, fPrecioC, lblPrecioCHint), 1, rs++);
        gridStock.add(DialogUtil.fieldLabel("Precio Venta"),    0, rs); gridStock.add(new VBox(2, fPrecioV, lblPrecioVHint), 1, rs++);
        gridStock.add(DialogUtil.fieldLabel("Fecha Venc."),     0, rs); gridStock.add(fVenc,     1, rs);

        // ── TabPane ──
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab tabInfo  = new Tab("📋  Información General", gridInfo);
        Tab tabStock = new Tab("📊  Stock y Precios", gridStock);
        tabs.getTabs().addAll(tabInfo, tabStock);
        tabs.getStyleClass().add("dlg-tabpane");

        Label lblFormError = new Label();
        lblFormError.getStyleClass().add("field-error-label");
        lblFormError.setVisible(false);
        lblFormError.setManaged(false);
        lblFormError.setWrapText(true);

        String submitLabel = isNewProduct ? "✓  Guardar bien" : "✓  Guardar cambios";
        Button btnGuardar = new Button(submitLabel);
        btnGuardar.getStyleClass().add("form-submit-btn");
        if (!isNewProduct) btnGuardar.getStyleClass().add("form-submit-btn-edit");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);
        btnGuardar.setDisable(true);
        btnGuardar.setOnAction(e -> { if (okBtn instanceof Button b) b.fire(); });

        Runnable hideFormError = () -> { lblFormError.setVisible(false); lblFormError.setManaged(false); };
        fNombre.textProperty().addListener((o, a, b) -> { if (!b.isBlank()) fNombre.getStyleClass().remove("field-error"); hideFormError.run(); });
        fCodigo.textProperty().addListener((o, a, b) -> { if (!b.isBlank()) fCodigo.getStyleClass().remove("field-error"); hideFormError.run(); });
        fArea.valueProperty().addListener((o, a, b) -> { if (b != null && !b.isBlank()) fArea.getStyleClass().remove("field-error"); hideFormError.run(); });
        fCat.valueProperty().addListener((o, a, b) -> { if (b != null) fCat.getStyleClass().remove("field-error"); hideFormError.run(); });
        fPrecioC.textProperty().addListener((o, a, b) -> { fPrecioC.getStyleClass().remove("field-error"); hideFormError.run(); });
        fPrecioV.textProperty().addListener((o, a, b) -> { fPrecioV.getStyleClass().remove("field-error"); hideFormError.run(); });

        if (okBtn != null) {
            Runnable checkOk = () -> {
                boolean invalid = fNombre.getText().isBlank() || fCodigo.getText().isBlank()
                    || fArea.getValue() == null || fArea.getValue().isBlank()
                    || fCat.getValue() == null;
                okBtn.setDisable(invalid);
                btnGuardar.setDisable(invalid);
            };
            checkOk.run();
            fNombre.textProperty().addListener((o, a, b) -> checkOk.run());
            fCodigo.textProperty().addListener((o, a, b) -> checkOk.run());
            fArea.valueProperty().addListener((o, a, b) -> checkOk.run());
            fCat.valueProperty().addListener((o, a, b) -> checkOk.run());
        }

        VBox.setMargin(lblFormError, new Insets(4, 22, 0, 22));
        VBox.setMargin(btnGuardar,   new Insets(4, 22, 16, 22));
        VBox dialogContent = new VBox(0, dialogHeader, tabs, lblFormError, btnGuardar);
        dialog.getDialogPane().setContent(dialogContent);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(dialogHeader, tabs, btnGuardar), 280, 70);

        Platform.runLater(() -> fNombre.requestFocus());
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            boolean invalid = false;
            Node firstErrField = null;
            if (fNombre.getText().isBlank()) { fNombre.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = fNombre; invalid = true; }
            else fNombre.getStyleClass().remove("field-error");
            if (fCodigo.getText().isBlank()) { fCodigo.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = fCodigo; invalid = true; }
            else fCodigo.getStyleClass().remove("field-error");
            if (fArea.getValue() == null || fArea.getValue().isBlank()) { fArea.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = fArea; invalid = true; }
            else fArea.getStyleClass().remove("field-error");
            if (fCat.getValue() == null) { fCat.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); if (firstErrField == null) firstErrField = fCat; invalid = true; }
            else fCat.getStyleClass().remove("field-error");

            BigDecimal precioCompra = null, precioVenta = null;
            try {
                precioCompra = new BigDecimal(fPrecioC.getText().trim());
                fPrecioC.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                fPrecioC.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
            }
            try {
                precioVenta = new BigDecimal(fPrecioV.getText().trim());
                fPrecioV.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                fPrecioV.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
            }

            if (invalid) {
                lblFormError.setText("Completa los campos obligatorios marcados en rojo. Los precios deben ser números válidos (ej. 1500.00).");
                lblFormError.setVisible(true);
                lblFormError.setManaged(true);
                AnimationUtils.shake(lblFormError);
                if (firstErrField != null) AnimationUtils.shake(firstErrField);
                return null;
            }

            DialogUtil.commitSpinner(fStock);
            DialogUtil.commitSpinner(fStockMin);
            DialogUtil.commitSpinner(fStockMax);

            Producto p = existing != null ? existing : new Producto();
            if (p.getId() == null) p.setId(UUID.randomUUID().toString());
            p.setNombre(fNombre.getText().trim());
            p.setCodigo(fCodigo.getText().trim());
            p.setDescripcion(fDesc.getText().trim());
            if (fCat.getValue() != null) {
                p.setCategoriaId(fCat.getValue().getId());
                p.setCategoriaNombre(fCat.getValue().getNombre());
                p.setCategoriaColor(fCat.getValue().getColor());
            }
            p.setPrecioCompra(precioCompra);
            p.setPrecioVenta(precioVenta);
            p.setStockActual(fStock.getValue());
            p.setStockMinimo(fStockMin.getValue());
            p.setStockMaximo(fStockMax.getValue());
            p.setUnidad(fUnidad.getValue());
            p.setProveedor(fProveedor.getText().trim());
            p.setUbicacion(fUbicacion.getText().trim());
            p.setResguardante(fResguardante.getText().trim());
            p.setFechaVencimiento(fVenc.getValue());
            p.setArea(fArea.getValue());
            if (fotoHolder[0] != null && !fotoHolder[0].isBlank()) {
                try {
                    Path imgDir = imgDir();
                    Files.createDirectories(imgDir);
                    Path dest = imgDir.resolve(p.getId() + ".jpg");
                    Path src = Path.of(fotoHolder[0]);
                    if (!src.equals(dest)) {
                        ImageUtils.resizeAndSave(src.toFile(), dest.toFile());
                        thumbnailCache.remove(dest.toString());
                    }
                    p.setFotoUrl(dest.toString());
                } catch (Exception ex) {
                    log.error("No se pudo procesar la imagen del bien '{}', se conserva la foto anterior", p.getNombre(), ex);
                    p.setFotoUrl(existing != null ? existing.getFotoUrl() : null);
                }
            } else {
                p.setFotoUrl(null);
            }
            return p;
        });

        return dialog.showAndWait();
    }

    private static Path imgDir() {
        return Path.of(System.getProperty("user.home"), ".sibim", "imagenes");
    }
}
