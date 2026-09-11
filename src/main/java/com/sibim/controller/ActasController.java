package com.sibim.controller;

import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.service.ActaService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ActasController {

    private static final Logger log = LoggerFactory.getLogger(ActasController.class);

    @FXML private VBox rootPane;
    @FXML private Label lblStatTotal;
    @FXML private VBox statCardTotal;
    @FXML private TableView<ActaEntregaRecepcion> table;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colNumero;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colSaliente;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colEntrante;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colFecha;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colBienes;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colValor;
    @FXML private Button btnNueva;
    @FXML private Button btnExportarPdf;
    @FXML private ProgressIndicator spinner;

    private final ActaService service = new ActaService();
    private final ObservableList<ActaEntregaRecepcion> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (!SessionManager.isAdmin()) {
            if (btnNueva != null) { btnNueva.setVisible(false); btnNueva.setManaged(false); }
        }
        setupTable();
        setupButtonState();
        loadData();
    }

    private void setupTable() {
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        colSaliente.setCellValueFactory(c -> {
            String s = c.getValue().getAdminSaliente();
            String cargo = c.getValue().getCargoSaliente();
            return new SimpleStringProperty(cargo != null && !cargo.isBlank() ? s + " · " + cargo : s);
        });
        colEntrante.setCellValueFactory(c -> {
            String s = c.getValue().getAdminEntrante();
            String cargo = c.getValue().getCargoEntrante();
            return new SimpleStringProperty(cargo != null && !cargo.isBlank() ? s + " · " + cargo : s);
        });
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaEntrega() != null
                ? FormatUtils.formatDate(c.getValue().getFechaEntrega()) : "—"));
        colBienes.setCellValueFactory(c -> new SimpleStringProperty(
            String.valueOf(c.getValue().getTotalBienes())));
        colValor.setCellValueFactory(c -> new SimpleStringProperty(
            FormatUtils.formatCurrency(c.getValue().getValorTotal())));

        table.setItems(data);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                onExportarPdf();
        });

        ContextMenu cm = new ContextMenu();
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().add(miPdf);
        table.setContextMenu(cm);
    }

    private void setupButtonState() {
        if (btnExportarPdf != null)
            btnExportarPdf.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        com.sibim.util.AppExecutor.submit(() -> {
            try {
                List<ActaEntregaRecepcion> list = service.getAll();
                Platform.runLater(() -> {
                    data.setAll(list);
                    if (lblStatTotal != null)
                        AnimationUtils.animateCount(lblStatTotal, (long) list.size(), 700);
                    if (statCardTotal != null)
                        AnimationUtils.staggeredFadeInUp(List.of(statCardTotal), 280, 60);
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                });
            } catch (Exception e) {
                log.error("Error cargando actas", e);
                Platform.runLater(() -> {
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                    NotificacionUtil.error(rootPane.getScene(), "No se pudieron cargar las actas");
                });
            }
        });
    }

    @FXML
    private void onRefresh() { loadData(); }

    @FXML
    private void onNuevaActa() {
        if (!SessionManager.isAdmin()) {
            NotificacionUtil.advertencia(rootPane.getScene(),
                "Solo el administrador puede generar actas de entrega-recepción");
            return;
        }
        javafx.scene.Scene scene = rootPane.getScene();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Nueva Acta de Entrega-Recepción");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(580);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal-bold",
            "Acta de Entrega-Recepción",
            "Documento oficial para cambio de administración · captura todo el inventario actual",
            "#1E40AF", "#1D4ED8");

        GridPane form = DialogUtil.formGrid(160);
        int row = 0;

        Label secSal = new Label("FUNCIONARIO SALIENTE");
        secSal.getStyleClass().add("dialog-field-label");
        form.add(secSal, 0, row++);
        form.add(new Separator(), 0, row++, 2, 1);

        TextField fNombreSal  = new TextField(); fNombreSal.setPromptText("Nombre completo");
        fNombreSal.getStyleClass().add("form-input");
        TextField fCargoSal   = new TextField(); fCargoSal.setPromptText("Cargo o puesto");
        fCargoSal.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Nombre *"), 0, row); form.add(fNombreSal, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"),   0, row); form.add(fCargoSal,  1, row++);

        Label secEnt = new Label("FUNCIONARIO ENTRANTE");
        secEnt.getStyleClass().add("dialog-field-label");
        form.add(new Region(), 0, row++);
        form.add(secEnt, 0, row++);
        form.add(new Separator(), 0, row++, 2, 1);

        TextField fNombreEnt = new TextField(); fNombreEnt.setPromptText("Nombre completo");
        fNombreEnt.getStyleClass().add("form-input");
        TextField fCargoEnt  = new TextField(); fCargoEnt.setPromptText("Cargo o puesto");
        fCargoEnt.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Nombre *"), 0, row); form.add(fNombreEnt, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"),   0, row); form.add(fCargoEnt,  1, row++);

        Label secGen = new Label("DATOS DEL ACTA");
        secGen.getStyleClass().add("dialog-field-label");
        form.add(new Region(), 0, row++);
        form.add(secGen, 0, row++);
        form.add(new Separator(), 0, row++, 2, 1);

        DatePicker fFecha = new DatePicker(LocalDate.now());
        fFecha.setMaxWidth(Double.MAX_VALUE);
        fFecha.getStyleClass().add("form-input");
        fFecha.setConverter(FormatUtils.datePickerConverter());

        TextArea fObs = new TextArea(); fObs.setPromptText("Observaciones generales (opcional)");
        fObs.setMaxHeight(70); fObs.setWrapText(true);
        fObs.getStyleClass().add("form-input");

        form.add(DialogUtil.fieldLabel("Fecha de entrega *"), 0, row); form.add(fFecha, 1, row++);
        form.add(DialogUtil.fieldLabel("Observaciones"),      0, row); form.add(fObs,   1, row++);

        Label infoLbl = new Label("ℹ El acta captura todo el inventario en su estado actual al generarla.");
        infoLbl.getStyleClass().add("muted-sm");
        infoLbl.setWrapText(true);

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error-label");
        lblError.setVisible(false);

        VBox content = new VBox(10, header, form, infoLbl, lblError);
        content.setPadding(new Insets(0, 16, 16, 16));
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Generar Acta");
        okBtn.getStyleClass().add("dialog-ok-btn");

        AnimationUtils.staggeredFadeInUp(List.of(header, form), 280, 70);
        Platform.runLater(fNombreSal::requestFocus);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblError.setVisible(false);
            if (fNombreSal.getText().isBlank()) {
                lblError.setText("El nombre del funcionario saliente es obligatorio");
                lblError.setVisible(true); AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fNombreEnt.getText().isBlank()) {
                lblError.setText("El nombre del funcionario entrante es obligatorio");
                lblError.setVisible(true); AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fFecha.getValue() == null) {
                lblError.setText("La fecha de entrega es obligatoria");
                lblError.setVisible(true); AnimationUtils.shake(lblError); ev.consume(); return;
            }
        });

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            DialogUtil.runAsyncWithProgress(scene, "Generando acta y capturando inventario…",
                () -> service.generar(
                    fNombreSal.getText().trim(), fCargoSal.getText().trim(),
                    fNombreEnt.getText().trim(), fCargoEnt.getText().trim(),
                    fFecha.getValue(), fObs.getText().trim()),
                acta -> {
                    NotificacionUtil.exito(scene, "Acta " + acta.getNumero() + " generada — "
                        + acta.getTotalBienes() + " bienes registrados");
                    loadData();
                    DialogUtil.runAsync(
                        () -> service.exportarPdf(acta),
                        file -> {
                            if (file == null) return;
                            try { Desktop.getDesktop().open(file); }
                            catch (Exception ex) {
                                NotificacionUtil.advertencia(scene, "PDF: " + file.getAbsolutePath());
                            }
                        },
                        e -> NotificacionUtil.error(scene, "No se pudo generar el PDF del acta")
                    );
                },
                e -> NotificacionUtil.error(scene, "No se pudo generar el acta: "
                    + (e.getMessage() != null ? e.getMessage() : "Error"))
            );
        });
    }

    @FXML
    private void onExportarPdf() {
        ActaEntregaRecepcion sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Generando PDF del acta…",
            () -> service.exportarPdf(sel),
            file -> {
                if (file == null) return;
                try { Desktop.getDesktop().open(file); }
                catch (Exception e) { NotificacionUtil.advertencia(scene, "PDF: " + file.getAbsolutePath()); }
            },
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF")
        );
    }
}
