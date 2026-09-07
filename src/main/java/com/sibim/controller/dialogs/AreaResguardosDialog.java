package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.repository.AreaResguardoRepository;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AreaResguardosDialog {

    private static final Logger log = LoggerFactory.getLogger(AreaResguardosDialog.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AreaResguardosDialog() {}

    public static void show(String preselectedArea, javafx.scene.Scene scene) {
        AreaResguardoRepository repo = new AreaResguardoRepository();

        Dialog<Void> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2f-file-pdf-box",
            "Resguardos por Área",
            "Documentos PDF de resguardo patrimonial por área/dirección",
            "#059669", "#047857");

        // Area selector
        List<String> areaNames = new java.util.ArrayList<>(Areas.getAllAreaNames());
        ComboBox<String> areaCombo = new ComboBox<>(FXCollections.observableArrayList(areaNames));
        areaCombo.setPromptText("Selecciona un área…");
        areaCombo.setMaxWidth(Double.MAX_VALUE);
        areaCombo.getStyleClass().add("form-input");
        if (preselectedArea != null && areaNames.contains(preselectedArea))
            areaCombo.setValue(preselectedArea);

        // List of existing resguardos
        VBox resguardosList = new VBox(8);
        resguardosList.setPadding(new Insets(4, 0, 4, 0));

        Label lblEmptyList = new Label("Selecciona un área para ver sus resguardos.");
        lblEmptyList.getStyleClass().add("muted-sm");
        resguardosList.getChildren().add(lblEmptyList);

        Runnable[] reloadList = {null};
        reloadList[0] = () -> {
            String area = areaCombo.getValue();
            resguardosList.getChildren().clear();
            if (area == null) {
                resguardosList.getChildren().add(lblEmptyList);
                return;
            }
            com.sibim.util.AppExecutor.submit(() -> {
                try {
                    List<AreaResguardoRepository.AreaResguardo> list = repo.findByArea(area);
                    Platform.runLater(() -> {
                        resguardosList.getChildren().clear();
                        if (list.isEmpty()) {
                            Label none = new Label("No hay resguardos registrados para esta área.");
                            none.getStyleClass().add("muted-sm");
                            resguardosList.getChildren().add(none);
                        } else {
                            for (AreaResguardoRepository.AreaResguardo r : list) {
                                HBox row = buildResguardoRow(r, repo, reloadList[0], scene);
                                resguardosList.getChildren().add(row);
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Error cargando resguardos", e);
                    Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudieron cargar los resguardos"));
                }
            });
        };

        areaCombo.valueProperty().addListener((obs, o, n) -> reloadList[0].run());
        if (preselectedArea != null) reloadList[0].run();

        // Upload section
        Label lblSelPdf = new Label("Sin PDF seleccionado");
        lblSelPdf.getStyleClass().add("muted-sm");
        String[] pdfPathHolder = {null};

        Button btnSelPdf = new Button("Seleccionar PDF");
        btnSelPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        btnSelPdf.getStyleClass().add("btn-secondary");
        btnSelPdf.setOnAction(ev -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Seleccionar PDF de resguardo");
            chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
            File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                pdfPathHolder[0] = file.getAbsolutePath();
                lblSelPdf.setText(file.getName());
            }
        });

        TextField fDesc = new TextField();
        fDesc.setPromptText("Descripción / período (ej. Resguardo 2026)");
        fDesc.getStyleClass().add("form-input");

        DatePicker fFecha = new DatePicker(LocalDate.now());
        fFecha.setMaxWidth(Double.MAX_VALUE);
        fFecha.getStyleClass().add("form-input");

        Button btnGuardar = new Button("Guardar resguardo");
        btnGuardar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            String area = areaCombo.getValue();
            if (area == null) { NotificacionUtil.advertencia(scene, "Selecciona un área primero"); return; }
            if (pdfPathHolder[0] == null) { NotificacionUtil.advertencia(scene, "Selecciona un archivo PDF primero"); return; }
            com.sibim.util.AppExecutor.submit(() -> {
                try {
                    Path storageDir = Path.of(System.getProperty("user.home"), ".sibim", "resguardos");
                    Files.createDirectories(storageDir);
                    String fileName = java.util.UUID.randomUUID() + ".pdf";
                    Path dest = storageDir.resolve(fileName);
                    Files.copy(Path.of(pdfPathHolder[0]), dest, StandardCopyOption.REPLACE_EXISTING);
                    repo.save(area, dest.toString(),
                        fDesc.getText().trim().isEmpty() ? null : fDesc.getText().trim(),
                        fFecha.getValue());
                    Platform.runLater(() -> {
                        pdfPathHolder[0] = null;
                        lblSelPdf.setText("Sin PDF seleccionado");
                        fDesc.clear();
                        reloadList[0].run();
                        NotificacionUtil.exito(scene, "Resguardo guardado correctamente");
                    });
                } catch (Exception e) {
                    log.error("Error guardando resguardo", e);
                    Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo guardar el resguardo"));
                }
            });
        });

        Separator sep = new Separator();

        Label lblUploadTitle = new Label("Agregar nuevo resguardo");
        lblUploadTitle.getStyleClass().add("dialog-field-label");

        GridPane uploadGrid = DialogUtil.formGrid(120);
        int ur = 0;
        uploadGrid.add(DialogUtil.fieldLabel("Área"), 0, ur);
        uploadGrid.add(areaCombo, 1, ur++);
        uploadGrid.add(DialogUtil.fieldLabel("Archivo PDF"), 0, ur);
        uploadGrid.add(new HBox(8, btnSelPdf, lblSelPdf), 1, ur++);
        uploadGrid.add(DialogUtil.fieldLabel("Descripción"), 0, ur);
        uploadGrid.add(fDesc, 1, ur++);
        uploadGrid.add(DialogUtil.fieldLabel("Fecha"), 0, ur);
        uploadGrid.add(fFecha, 1, ur++);
        uploadGrid.add(new Label(), 0, ur);
        uploadGrid.add(btnGuardar, 1, ur);

        Label lblResguardosTitle = new Label("Resguardos registrados");
        lblResguardosTitle.getStyleClass().add("dialog-field-label");

        ScrollPane scrollList = new ScrollPane(resguardosList);
        scrollList.setFitToWidth(true);
        scrollList.setMaxHeight(220);
        scrollList.getStyleClass().add("dlg-tabs-scroll");

        VBox content = new VBox(10, header, uploadGrid, sep, lblResguardosTitle, scrollList);
        content.setPadding(new Insets(0, 16, 16, 16));
        dialog.getDialogPane().setContent(content);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, uploadGrid, scrollList), 270, 70);
        Platform.runLater(() -> areaCombo.requestFocus());
        dialog.showAndWait();
    }

    private static HBox buildResguardoRow(AreaResguardoRepository.AreaResguardo r,
            AreaResguardoRepository repo, Runnable reload, javafx.scene.Scene scene) {
        String pdfName = r.pdfUrl() != null ? Path.of(r.pdfUrl()).getFileName().toString() : "—";
        String desc = r.descripcion() != null ? r.descripcion() : pdfName;
        String fechaStr = r.fecha() != null ? r.fecha().format(FMT) : "—";

        Label lblDesc = new Label(desc);
        lblDesc.getStyleClass().add("org-product-name");
        lblDesc.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblDesc, Priority.ALWAYS);

        Label lblFecha = new Label(fechaStr);
        lblFecha.getStyleClass().add("muted-sm");
        lblFecha.setMinWidth(80);

        Button btnAbrir = new Button("Abrir");
        btnAbrir.setGraphic(new FontIcon("mdi2e-eye-outline"));
        btnAbrir.getStyleClass().add("btn-secondary");
        btnAbrir.setOnAction(ev -> {
            try {
                if (r.pdfUrl() != null && Files.exists(Path.of(r.pdfUrl())))
                    Desktop.getDesktop().open(new File(r.pdfUrl()));
                else
                    NotificacionUtil.advertencia(scene, "El archivo PDF ya no existe en la ruta guardada");
            } catch (Exception e) {
                NotificacionUtil.error(scene, "No se pudo abrir el PDF");
            }
        });

        Button btnElim = new Button();
        btnElim.setGraphic(new FontIcon("mdi2d-delete-outline"));
        btnElim.getStyleClass().add("btn-secondary");
        btnElim.setOnAction(ev -> {
            if (ConfirmacionUtil.confirmar("Eliminar resguardo",
                    "¿Eliminar el resguardo \"" + desc + "\"? El archivo PDF también se eliminará.")) {
                com.sibim.util.AppExecutor.submit(() -> {
                    try {
                        if (r.pdfUrl() != null) Files.deleteIfExists(Path.of(r.pdfUrl()));
                        repo.delete(r.id());
                        Platform.runLater(reload);
                    } catch (Exception e) {
                        log.error("Error eliminando resguardo", e);
                        Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo eliminar el resguardo"));
                    }
                });
            }
        });

        HBox row = new HBox(10, lblDesc, lblFecha, btnAbrir, btnElim);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("org-child-header");
        row.setPadding(new Insets(6, 10, 6, 10));
        return row;
    }
}
