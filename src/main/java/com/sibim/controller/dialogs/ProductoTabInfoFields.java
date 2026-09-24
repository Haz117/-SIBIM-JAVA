package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.config.AreaCodigos;
import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.ImageUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds and holds all fields for the "Información General" tab of the
 *  "Nuevo/Editar Bien" dialog.  Package-private — only used by
 *  {@link ProductoDialogFactory}. */
class ProductoTabInfoFields {

    final GridPane grid;
    final TextField fNombre;
    final TextField fCodigo;
    final TextArea  fDesc;
    final ComboBox<Categoria> fCat;
    final ComboBox<String>    fArea;
    final Label lblCodigoHint;
    final Label lblNombreHint;
    final Label lblNombreWarn;
    final Label lblCatHint;
    final Label lblAreaHint;
    final List<String> fotosHolder;
    final String[] facturaHolder;

    ProductoTabInfoFields(
            Producto existing,
            boolean isNewProduct,
            String existingId,
            List<Categoria> cats,
            Logger log,
            Dialog<?> dialog,
            Runnable[] markDirtyRef,
            Runnable[] checkOkRef,
            Map<String, Image> thumbnailCache,
            List<String> existingFotos,
            ProductoRepository codigoRepo) {

        grid = DialogUtil.formGrid(120);

        // ── Nombre ──────────────────────────────────────────────────────────
        fNombre = new TextField(existing != null ? existing.getNombre() : "");
        fNombre.setPromptText("Nombre descriptivo del bien");
        fNombre.getStyleClass().add("form-input");

        // ── Código ──────────────────────────────────────────────────────────
        fCodigo = new TextField(existing != null ? existing.getCodigo() : "");
        fCodigo.getStyleClass().add("form-input");
        fCodigo.setPromptText("Código único de inventario");

        lblCodigoHint = new Label();
        lblCodigoHint.getStyleClass().add("field-hint");
        lblCodigoHint.setVisible(false);
        lblCodigoHint.setManaged(false);

        // Inline código uniqueness check — debounced 280 ms
        // checkOkRef is injected by the factory so the async callback can
        // re-run the OK-button enablement check after availability arrives.
        javafx.animation.Timeline[] codigoDebounce = {null};
        fCodigo.textProperty().addListener((obs, old, val) -> {
            if (codigoDebounce[0] != null) codigoDebounce[0].stop();
            lblCodigoHint.setVisible(false);
            lblCodigoHint.setManaged(false);
            if (val.isBlank()) return;
            codigoDebounce[0] = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(280), e -> AppExecutor.submit(() -> {
                    try {
                        boolean exists = codigoRepo.existsByCodigo(val.trim(), existingId);
                        Platform.runLater(() -> {
                            lblCodigoHint.setText(exists ? "✕  Este código ya existe" : "✓  Disponible");
                            lblCodigoHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                            lblCodigoHint.getStyleClass().add(exists ? "field-hint-error" : "field-hint-ok");
                            lblCodigoHint.setVisible(true);
                            lblCodigoHint.setManaged(true);
                            if (exists) fCodigo.getStyleClass().add("field-error");
                            else fCodigo.getStyleClass().remove("field-error");
                            if (checkOkRef[0] != null) checkOkRef[0].run();
                        });
                    } catch (Exception ignored) {
                        log.debug("Código availability check failed", ignored);
                    }
                })));
            codigoDebounce[0].play();
        });

        // ── Descripción ─────────────────────────────────────────────────────
        fDesc = new TextArea(existing != null && existing.getDescripcion() != null
                ? existing.getDescripcion() : "");
        fDesc.setPrefRowCount(2);
        fDesc.setPromptText("Descripción opcional");
        fDesc.getStyleClass().add("form-input");

        // ── Categoría ────────────────────────────────────────────────────────
        ObservableList<Categoria> allCats = FXCollections.observableArrayList(cats);
        fCat = new ComboBox<>(allCats);
        fCat.setMaxWidth(Double.MAX_VALUE);
        fCat.setPromptText("Buscar categoría…");
        fCat.setEditable(true);
        fCat.getStyleClass().add("form-input");
        fCat.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Categoria c) { return c == null ? "" : c.getNombre(); }
            public Categoria fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return cats.stream()
                    .filter(c -> c.getNombre().equalsIgnoreCase(s.trim()))
                    .findFirst().orElse(null);
            }
        });
        if (existing != null) {
            cats.stream()
                .filter(c -> c.getId().equals(existing.getCategoriaId()))
                .findFirst()
                .ifPresent(fCat::setValue);
        }

        // Autocomplete: filter dropdown as user types
        boolean[] catSelecting = {false};
        fCat.valueProperty().addListener((obs, old, val) -> {
            catSelecting[0] = true;
            if (val != null) fCat.setItems(allCats);
            Platform.runLater(() -> catSelecting[0] = false);
        });
        fCat.getEditor().textProperty().addListener((obs, old, val) -> {
            if (catSelecting[0]) return;
            String lower = val == null ? "" : val.toLowerCase();
            if (lower.isBlank()) { fCat.setItems(allCats); return; }
            List<Categoria> filtered = cats.stream()
                .filter(c -> c.getNombre().toLowerCase().contains(lower))
                .toList();
            fCat.setItems(FXCollections.observableArrayList(filtered));
            if (!filtered.isEmpty() && !fCat.isShowing()) fCat.show();
        });

        // ── Área ─────────────────────────────────────────────────────────────
        List<String> areaNames = new ArrayList<>(Areas.getAllAreaNames());
        fArea = new ComboBox<>(FXCollections.observableArrayList(areaNames));
        fArea.setMaxWidth(Double.MAX_VALUE);
        fArea.setPromptText("Área responsable");
        var sessionUser = SessionManager.getCurrentUser();
        fArea.setValue(existing != null ? existing.getArea()
            : sessionUser != null ? sessionUser.getArea() : null);
        if (SessionManager.isDireccion()) fArea.setDisable(true);
        fArea.getStyleClass().add("form-input");

        // For new products: disable código when the area has an auto-prefix
        if (isNewProduct) {
            Runnable syncCodigo = () -> {
                String a = fArea.getValue();
                if (a == null || a.isBlank()) {
                    fCodigo.setDisable(true);
                    fCodigo.setPromptText("Selecciona un área primero");
                    fCodigo.clear();
                } else if (AreaCodigos.tienePrefijo(a)) {
                    fCodigo.setDisable(true);
                    fCodigo.setPromptText("Se asignará automáticamente según el área");
                    fCodigo.clear();
                } else {
                    fCodigo.setDisable(false);
                    fCodigo.setPromptText("Código único de inventario");
                }
            };
            syncCodigo.run();
            fArea.valueProperty().addListener((o, a, b) -> syncCodigo.run());
        }

        // ── Inline blur-validation hints ─────────────────────────────────────
        lblNombreHint = new Label("Campo requerido");
        lblNombreHint.getStyleClass().addAll("field-hint", "field-hint-error");
        lblNombreHint.setVisible(false);
        lblNombreHint.setManaged(false);

        lblNombreWarn = new Label();
        lblNombreWarn.getStyleClass().addAll("field-hint", "field-hint-warn");
        lblNombreWarn.setVisible(false);
        lblNombreWarn.setManaged(false);

        lblCatHint = new Label("Selecciona una categoría");
        lblCatHint.getStyleClass().addAll("field-hint", "field-hint-error");
        lblCatHint.setVisible(false);
        lblCatHint.setManaged(false);

        lblAreaHint = new Label("Campo requerido");
        lblAreaHint.getStyleClass().addAll("field-hint", "field-hint-error");
        lblAreaHint.setVisible(false);
        lblAreaHint.setManaged(false);

        fNombre.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                String typed = fNombre.getText().trim();
                boolean empty = typed.isBlank();
                lblNombreHint.setVisible(empty);
                lblNombreHint.setManaged(empty);
                if (empty) { fNombre.getStyleClass().add("field-error"); return; }
                lblNombreWarn.setVisible(false);
                lblNombreWarn.setManaged(false);
                if (typed.length() < 4) return;
                String typedLow = typed.toLowerCase();
                AppExecutor.submit(() -> {
                    try {
                        List<Producto> all = codigoRepo.findAll();
                        List<String> hits = all.stream()
                            .filter(p -> existingId == null || !existingId.equals(p.getId()))
                            .map(Producto::getNombre)
                            .filter(n -> {
                                String nl = n.toLowerCase();
                                return !nl.equals(typedLow)
                                    && (nl.contains(typedLow) || typedLow.contains(nl)
                                        || ProductoDialogFactory.diceSimilarity(nl, typedLow) >= 0.65);
                            })
                            .limit(2)
                            .toList();
                        Platform.runLater(() -> {
                            if (!hits.isEmpty() && !fNombre.getText().trim().isBlank()) {
                                lblNombreWarn.setText("⚠  Nombre similar a: " + hits.get(0)
                                    + (hits.size() > 1 ? " y otros" : ""));
                                lblNombreWarn.setVisible(true);
                                lblNombreWarn.setManaged(true);
                            }
                        });
                    } catch (Exception ignored) {
                        log.debug("Could not load similar nombre suggestions", ignored);
                    }
                });
            }
        });
        fNombre.textProperty().addListener((o, a, b) -> {
            lblNombreWarn.setVisible(false);
            lblNombreWarn.setManaged(false);
        });
        fCodigo.focusedProperty().addListener((obs, was, now) -> {
            if (!now && fCodigo.getText().isBlank()) {
                lblCodigoHint.setText("Campo requerido");
                lblCodigoHint.getStyleClass().removeAll("field-hint-ok");
                lblCodigoHint.getStyleClass().add("field-hint-error");
                lblCodigoHint.setVisible(true);
                lblCodigoHint.setManaged(true);
                fCodigo.getStyleClass().add("field-error");
            }
        });
        fCat.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                boolean empty = fCat.getValue() == null;
                lblCatHint.setVisible(empty);
                lblCatHint.setManaged(empty);
                if (empty) fCat.getStyleClass().add("field-error");
            }
        });
        fArea.focusedProperty().addListener((obs, was, now) -> {
            if (!now && !fArea.isDisabled()) {
                boolean empty = fArea.getValue() == null || fArea.getValue().isBlank();
                lblAreaHint.setVisible(empty);
                lblAreaHint.setManaged(empty);
                if (empty) fArea.getStyleClass().add("field-error");
            }
        });

        // ── Multi-foto gallery ───────────────────────────────────────────────
        fotosHolder = new ArrayList<>(existingFotos != null ? existingFotos : new ArrayList<>());
        if (fotosHolder.isEmpty()
                && existing != null
                && existing.getFotoUrl() != null
                && !existing.getFotoUrl().isBlank()) {
            fotosHolder.add(existing.getFotoUrl());
        }

        FlowPane galleryPane = new FlowPane(8, 8);
        galleryPane.setAlignment(Pos.CENTER_LEFT);

        Runnable[] rebuildGallery = {null};
        rebuildGallery[0] = () -> {
            galleryPane.getChildren().clear();
            for (int idx = 0; idx < fotosHolder.size(); idx++) {
                final int i = idx;
                final String url = fotosHolder.get(i);
                ImageView iv = new ImageView();
                iv.setFitWidth(110); iv.setFitHeight(80);
                iv.setPreserveRatio(true);
                try {
                    String imgUrl = com.sibim.util.SupabaseStorage.isRemoteUrl(url)
                        ? url
                        : java.nio.file.Path.of(url).toUri().toString();
                    Image img = new Image(imgUrl, 110, 80, true, true, true);
                    iv.setImage(img);
                } catch (Exception ex) {
                    iv.setImage(null);
                }
                Button btnDel = new Button("×");
                btnDel.getStyleClass().add("btn-secondary");
                btnDel.setMinSize(20, 20); btnDel.setMaxSize(20, 20);
                btnDel.setOnAction(ev -> {
                    fotosHolder.remove(i);
                    rebuildGallery[0].run();
                    if (markDirtyRef[0] != null) markDirtyRef[0].run();
                });
                StackPane cell = new StackPane(iv, btnDel);
                StackPane.setAlignment(btnDel, Pos.TOP_RIGHT);
                cell.getStyleClass().add("dlg-img-box");
                cell.setPrefSize(110, 80);
                if (i == 0) {
                    Label badge = new Label("Principal");
                    badge.getStyleClass().addAll("muted-sm");
                    StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
                    cell.getChildren().add(badge);
                }
                galleryPane.getChildren().add(cell);
            }
        };
        rebuildGallery[0].run();

        Button btnAgregarFoto = new Button("Agregar foto");
        btnAgregarFoto.setGraphic(new FontIcon("mdi2c-camera-plus-outline"));
        btnAgregarFoto.getStyleClass().add("btn-secondary");
        btnAgregarFoto.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar imagen del bien");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes",
                    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
            java.util.List<File> files = chooser.showOpenMultipleDialog(dialog.getOwner());
            if (files != null) {
                for (File file : files) {
                    if (ImageUtils.exceedsMaxSize(file)) {
                        Dialog<ButtonType> tooBig = DialogUtil.styledMessage(
                            "mdi2a-alert-circle-outline", "Imagen demasiado pesada",
                            "Elige un archivo más pequeño",
                            AppColors.WARNING, AppColors.WARNING_D,
                            "La imagen '" + file.getName() + "' pesa "
                            + (file.length() / (1024 * 1024)) + " MB — máximo "
                            + (ImageUtils.maxSourceBytes() / (1024 * 1024)) + " MB.",
                            dialog.getOwner());
                        tooBig.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
                        DialogUtil.styleButton(tooBig.getDialogPane(), ButtonType.OK, AppColors.WARNING);
                        tooBig.showAndWait();
                        continue;
                    }
                    fotosHolder.add(file.getAbsolutePath());
                }
                rebuildGallery[0].run();
                if (markDirtyRef[0] != null) markDirtyRef[0].run();
            }
        });

        Label lblGalleryHint = new Label("La primera foto es la imagen principal del bien en la tabla.");
        lblGalleryHint.getStyleClass().add("muted-sm");

        VBox imgSection = new VBox(6, galleryPane, btnAgregarFoto, lblGalleryHint);

        // ── Factura picker ────────────────────────────────────────────────────
        facturaHolder = new String[]{ existing != null ? existing.getFacturaUrl() : null };

        FontIcon docIcon = new FontIcon("mdi2f-file-document-outline");
        docIcon.setIconSize(28);
        Label factPlaceholder = new Label("Sin factura", docIcon);
        factPlaceholder.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        factPlaceholder.getStyleClass().add("dlg-img-placeholder");
        factPlaceholder.setAlignment(Pos.CENTER);

        ImageView factPreview = new ImageView();
        factPreview.setFitWidth(150); factPreview.setFitHeight(112);
        factPreview.setPreserveRatio(true);

        StackPane factBox = new StackPane(factPlaceholder, factPreview);
        factBox.setPrefSize(150, 112);
        factBox.getStyleClass().add("dlg-img-box");

        Runnable loadFact = () -> {
            if (facturaHolder[0] != null && !facturaHolder[0].isBlank()) {
                try {
                    String fu = facturaHolder[0];
                    String factImgUrl = com.sibim.util.SupabaseStorage.isRemoteUrl(fu)
                        ? fu : java.nio.file.Path.of(fu).toUri().toString();
                    Image img = new Image(factImgUrl, 150, 112, true, true, true);
                    factPreview.setImage(img);
                    factPlaceholder.setVisible(false);
                } catch (Exception ex) {
                    factPlaceholder.setVisible(true);
                }
            } else {
                factPreview.setImage(null);
                factPlaceholder.setVisible(true);
            }
        };
        loadFact.run();

        Button btnSelFact    = new Button("Seleccionar");
        btnSelFact.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        Button btnQuitarFact = new Button("Quitar");
        btnQuitarFact.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
        btnSelFact.getStyleClass().add("btn-secondary");
        btnQuitarFact.getStyleClass().add("btn-secondary");
        btnQuitarFact.setDisable(facturaHolder[0] == null || facturaHolder[0].isBlank());

        btnSelFact.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar foto de factura");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes",
                    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
            File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                if (ImageUtils.exceedsMaxSize(file)) {
                    Dialog<ButtonType> tooBig = DialogUtil.styledMessage(
                        "mdi2a-alert-circle-outline", "Imagen demasiado pesada",
                        "Elige un archivo más pequeño",
                        "#D97706", "#B45309",
                        "La imagen pesa " + (file.length() / (1024 * 1024))
                        + " MB — el máximo permitido es "
                        + (ImageUtils.maxSourceBytes() / (1024 * 1024)) + " MB.",
                        dialog.getOwner());
                    tooBig.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
                    DialogUtil.styleButton(tooBig.getDialogPane(), ButtonType.OK, "#D97706");
                    tooBig.showAndWait();
                    return;
                }
                facturaHolder[0] = file.getAbsolutePath();
                loadFact.run();
                btnQuitarFact.setDisable(false);
                if (markDirtyRef[0] != null) markDirtyRef[0].run();
            }
        });
        btnQuitarFact.setOnAction(ev -> {
            facturaHolder[0] = null;
            loadFact.run();
            btnQuitarFact.setDisable(true);
            if (markDirtyRef[0] != null) markDirtyRef[0].run();
        });

        VBox factSection = new VBox(6, factBox, new HBox(6, btnSelFact, btnQuitarFact));

        // ── Assemble gridInfo ─────────────────────────────────────────────────
        VBox codigoBox = new VBox(2, fCodigo, lblCodigoHint);
        Label lblInfoReq = new Label("* Campos obligatorios");
        lblInfoReq.getStyleClass().addAll("muted-sm");

        int r = 0;
        grid.add(DialogUtil.fieldLabel("Nombre *"),    0, r); grid.add(new VBox(2, fNombre, lblNombreHint, lblNombreWarn), 1, r++);
        grid.add(DialogUtil.fieldLabel("Código *"),    0, r); grid.add(codigoBox,  1, r++);
        grid.add(DialogUtil.fieldLabel("Categoría *"), 0, r); grid.add(new VBox(2, fCat, lblCatHint), 1, r++);
        grid.add(DialogUtil.fieldLabelWithHelp("Área *",
            "Secretaría o Dirección responsable del bien.\n" +
            "Solo los usuarios de esa área podrán gestionarlo.\n" +
            "Para DIRECCIÓN el área se fija automáticamente."),
                                                            0, r); grid.add(new VBox(2, fArea, lblAreaHint), 1, r++);
        grid.add(new javafx.scene.control.Separator(), 0, r, 2, 1); r++;
        grid.add(DialogUtil.fieldLabel("Descripción"), 0, r); grid.add(fDesc,      1, r++);
        grid.add(DialogUtil.fieldLabel("Imagen"),      0, r); grid.add(imgSection, 1, r++);
        grid.add(DialogUtil.fieldLabel("Foto factura"), 0, r); grid.add(factSection, 1, r++);
        grid.add(lblInfoReq,                           1, r);
    }

    /** Attaches dirty-tracking listeners on all editable fields. Call AFTER
     *  all initial {@code setValue()} calls so pre-filled values on edit don't
     *  immediately mark the form dirty. */
    void wireDirty(Runnable markDirty) {
        fNombre.textProperty().addListener((o, a, b) -> markDirty.run());
        fCodigo.textProperty().addListener((o, a, b) -> markDirty.run());
        fDesc.textProperty().addListener((o, a, b)   -> markDirty.run());
        fCat.valueProperty().addListener((o, a, b)   -> markDirty.run());
        fArea.valueProperty().addListener((o, a, b)  -> markDirty.run());
    }
}
