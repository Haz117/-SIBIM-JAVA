package com.sibim.controller;

import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.service.ActaService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ActasController extends BaseDocumentController<ActaEntregaRecepcion> {

    @FXML private Label   lblStatTotal;
    @FXML private Label   lblStatValor;
    @FXML private VBox    statCardTotal;
    @FXML private VBox    statCardValor;
    @FXML private TextField searchField;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colNumero;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colSaliente;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colEntrante;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colFecha;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colBienes;
    @FXML private TableColumn<ActaEntregaRecepcion, String> colValor;
    @FXML private Button btnNueva;

    private final ActaService service = new ActaService();
    private List<ActaEntregaRecepcion> allData = List.of();

    // ── BaseDocumentController hooks ─────────────────────────────────────────

    @Override
    protected void setupColumns() {
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
    }

    @Override
    protected void onInitialize() {
        boolean isAdmin = SessionManager.isAdmin();
        if (!isAdmin && btnNueva != null) {
            btnNueva.setVisible(false); btnNueva.setManaged(false);
        }

        if (searchField != null)
            searchField.textProperty().addListener((obs, o, n) -> applyFilter(n));

        table.setOnKeyPressed(ev -> {
            ActaEntregaRecepcion sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (ev.getCode() == KeyCode.ENTER) { mostrarDetalle(sel); ev.consume(); }
        });

        if (rootPane != null && isAdmin) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.N && ev.isControlDown()) {
                    onNuevaActa(); ev.consume();
                }
            });
        }

        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    @Override
    protected List<ActaEntregaRecepcion> fetchAll() throws Exception { return service.getAll(); }

    @Override
    protected void onDataLoaded(List<ActaEntregaRecepcion> list) {
        allData = list;
        applyFilter(searchField != null ? searchField.getText() : "");

        BigDecimal valorTotal = list.stream()
            .map(ActaEntregaRecepcion::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (lblStatTotal != null) AnimationUtils.animateCount(lblStatTotal, (long) list.size(), 700);
        if (lblStatValor != null) lblStatValor.setText(FormatUtils.formatCurrency(valorTotal));

        List<VBox> cards = new java.util.ArrayList<>();
        if (statCardTotal != null) cards.add(statCardTotal);
        if (statCardValor != null) cards.add(statCardValor);
        if (!cards.isEmpty()) AnimationUtils.staggeredFadeInUp(cards, 280, 60);
    }

    @Override
    protected File doExportPdf(ActaEntregaRecepcion item) throws Exception { return service.exportarPdf(item); }

    @Override
    protected String getLoadErrorMessage() { return "No se pudieron cargar las actas"; }
    @Override protected String emptyStateIcon()     { return "mdi2f-file-swap-outline"; }
    @Override protected String emptyStateTitle()    { return "Sin actas de entrega-recepción"; }
    @Override protected String emptyStateSubtitle() { return "Genera actas al iniciar o concluir una administración"; }

    @Override
    protected void onTableDoubleClick(ActaEntregaRecepcion item) { mostrarDetalle(item); }

    @Override
    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miDetalle = new MenuItem("Ver detalle");
        miDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        miDetalle.setOnAction(e -> {
            ActaEntregaRecepcion sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) mostrarDetalle(sel);
        });
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().addAll(miDetalle, miPdf);
        return cm;
    }

    @Override
    protected void exportarPdfAsync(ActaEntregaRecepcion item, Scene scene) {
        DialogUtil.runAsyncWithProgress(scene, "Generando PDF del acta…",
            () -> service.exportarPdf(item),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF")
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void applyFilter(String q) {
        if (q == null || q.isBlank()) {
            data.setAll(allData);
        } else {
            String lq = q.toLowerCase();
            data.setAll(allData.stream().filter(a ->
                (a.getNumero()       != null && a.getNumero().toLowerCase().contains(lq))
                || (a.getAdminSaliente() != null && a.getAdminSaliente().toLowerCase().contains(lq))
                || (a.getAdminEntrante() != null && a.getAdminEntrante().toLowerCase().contains(lq))
            ).toList());
        }
    }

    private void mostrarDetalle(ActaEntregaRecepcion a) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Acta " + a.getNumero());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(500);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2f-file-swap-outline",
            "Acta " + a.getNumero(),
            "Entrega-Recepción · " + (a.getFechaEntrega() != null
                ? FormatUtils.formatDate(a.getFechaEntrega()) : "—"),
            AppColors.INFO, AppColors.INFO_D);

        GridPane g = DialogUtil.formGrid(160);
        String[][] rows = {
            {"Funcionario saliente:", a.getAdminSaliente()},
            {"Cargo saliente:",       a.getCargoSaliente()  != null && !a.getCargoSaliente().isBlank()  ? a.getCargoSaliente()  : "—"},
            {"Funcionario entrante:", a.getAdminEntrante()},
            {"Cargo entrante:",       a.getCargoEntrante()  != null && !a.getCargoEntrante().isBlank()  ? a.getCargoEntrante()  : "—"},
            {"Fecha de entrega:",     a.getFechaEntrega()   != null ? FormatUtils.formatDate(a.getFechaEntrega())              : "—"},
            {"Total de bienes:",      String.valueOf(a.getTotalBienes())},
            {"Valor total:",          FormatUtils.formatCurrency(a.getValorTotal())},
            {"Generado por:",         a.getCreadoPorNombre() != null ? a.getCreadoPorNombre()                                  : "—"},
            {"Observaciones:",        a.getObservaciones()  != null && !a.getObservaciones().isBlank()  ? a.getObservaciones() : "—"},
        };
        int i = 0;
        for (String[] row : rows) {
            Label k = new Label(row[0]); k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(155);
            Label v = new Label(row[1]); v.getStyleClass().add("dlg-detail-value"); v.setWrapText(true);
            g.add(k, 0, i); g.add(v, 1, i++);
        }

        VBox content = new VBox(0, header, g);
        g.setPadding(new Insets(16, 22, 16, 22));
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, g), 260, 70);
        dialog.showAndWait();
    }

    // ── FXML actions ─────────────────────────────────────────────────────────

    @FXML
    private void onNuevaActa() {
        if (!SessionManager.isAdmin()) {
            NotificacionUtil.advertencia(rootPane.getScene(),
                "Solo el administrador puede generar actas de entrega-recepción");
            return;
        }
        Scene scene = rootPane.getScene();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Nueva Acta de Entrega-Recepción");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(580);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal-bold",
            "Acta de Entrega-Recepción",
            "Documento oficial para cambio de administración · captura todo el inventario actual",
            AppColors.INFO, AppColors.INFO_D);

        GridPane form = DialogUtil.formGrid(160);
        int row = 0;

        Label secSal = new Label("FUNCIONARIO SALIENTE");
        secSal.getStyleClass().add("dialog-field-label");
        form.add(secSal, 0, row++);
        form.add(new Separator(), 0, row++, 2, 1);

        TextField fNombreSal = new TextField(); fNombreSal.setPromptText("Nombre completo");
        fNombreSal.getStyleClass().add("form-input");
        TextField fCargoSal  = new TextField(); fCargoSal.setPromptText("Cargo o puesto");
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
        DialogUtil.setScrollableContent(dialog.getDialogPane(), content);

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

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            DialogUtil.runAsyncWithProgress(scene, "Generando acta y capturando inventario…",
                () -> service.generar(
                    fNombreSal.getText().trim(), fCargoSal.getText().trim(),
                    fNombreEnt.getText().trim(), fCargoEnt.getText().trim(),
                    fFecha.getValue(), fObs.getText().trim()),
                acta -> {
                    NotificacionUtil.exito(scene, "Acta " + acta.getNumero() + " generada — "
                        + acta.getTotalBienes() + " bienes registrados");
                    loadData();
                    exportarPdfAsync(acta, scene);
                },
                e -> NotificacionUtil.error(scene, "No se pudo generar el acta: "
                    + (e.getMessage() != null ? e.getMessage() : "Error"))
            )
        );
    }
}
