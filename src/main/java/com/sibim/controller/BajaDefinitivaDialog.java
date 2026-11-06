package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bajas › Baja definitiva: when, with what document and what was done with the
 *  bienes (their destino final). Several at once: one acta covers a lot. */
final class BajaDefinitivaDialog {

    record Datos(LocalDate fecha, String documento, String destino) {}

    private static final String[][] DESTINOS = {
        {"DESTRUCCION", "Destrucción"}, {"DONACION", "Donación"}, {"SUBASTA", "Subasta"},
        {"TRANSFERENCIA_ENTE", "Transferencia a otro ente"}, {"OTRO", "Otro"}};

    private BajaDefinitivaDialog() {}

    static Optional<Datos> pedir(List<Producto> bienes) {
        Dialog<Datos> dlg = new Dialog<>();
        dlg.setTitle("Baja definitiva");
        ButtonType registrar = new ButtonType("Registrar baja definitiva", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        String quien = bienes.size() == 1 ? "«" + bienes.get(0).getNombre() + "» (" + bienes.get(0).getCodigo() + ")"
            : FormatUtils.plural(bienes.size(), "bien", "bienes") + " dados de baja";
        HBox header = DialogUtil.gradientHeader("mdi2l-lock-check-outline", "Baja definitiva",
            quien + "\nYa se ejecutó su destino final: después no se podrá reactivar.",
            AppColors.WARNING_D, AppColors.WARNING_DD);

        DatePicker fecha = new DatePicker(LocalDate.now());
        fecha.setPrefWidth(380);
        TextField documento = new TextField();
        documento.setPromptText("Acta de destrucción, contrato de donación, acuerdo de cabildo…");
        documento.setPrefWidth(380);
        ComboBox<String> destino = new ComboBox<>();
        destino.getItems().add(null);
        for (String[] d : DESTINOS) destino.getItems().add(d[0]);
        destino.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String c) { return c == null ? "Como se dictaminó" : BajasController.destino(c); }
            @Override public String fromString(String s) { return null; }
        });
        destino.setPrefWidth(380);
        // When every one was dictaminado with the same destino, that one.
        List<String> dictaminados = bienes.stream().map(Producto::getTipoDestinoBaja).distinct().toList();
        destino.setValue(dictaminados.size() == 1 ? dictaminados.get(0) : null);

        Label ayuda = new Label("Dar de baja saca el bien del inventario activo. La baja definitiva se registra cuando "
            + "ya se destruyó, se donó, se subastó o se entregó, con el documento que lo autoriza.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("muted-sm");

        Node ok = dlg.getDialogPane().lookupButton(registrar);
        ok.getStyleClass().add("dialog-ok-btn-danger");
        Runnable validar = () -> ok.setDisable(fecha.getValue() == null
            || documento.getText() == null || documento.getText().isBlank());
        documento.textProperty().addListener((o, a, b) -> validar.run());
        fecha.valueProperty().addListener((o, a, b) -> validar.run());
        validar.run();

        VBox form = new VBox(8, ayuda,
            new Label("Fecha en que se ejecutó *"), fecha,
            new Label("Documento que la autoriza *"), documento,
            new Label("Destino final"), destino);
        form.setPadding(new Insets(16, 24, 8, 24));
        dlg.getDialogPane().setContent(new VBox(header, form));
        dlg.getDialogPane().setPrefWidth(440);
        javafx.application.Platform.runLater(documento::requestFocus);
        dlg.setResultConverter(bt -> bt != registrar ? null
            : new Datos(fecha.getValue(), Objects.requireNonNullElse(documento.getText(), "").trim(), destino.getValue()));
        return dlg.showAndWait();
    }
}
