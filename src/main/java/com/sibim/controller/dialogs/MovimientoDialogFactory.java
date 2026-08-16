package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.util.DialogUtil;
import com.sibim.util.ProductoUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/** Builds and drives the "Registrar Movimiento" dialog. Extracted out of
 *  MovimientosController for the same reason as ProductoDialogFactory — the
 *  controller only deals with the table/filters/persistence and this class
 *  only deals with the form. Returns the validated form data on OK (the
 *  caller is responsible for calling MovimientoService.registrar and
 *  refreshing its own list/table), or empty on cancel. */
public final class MovimientoDialogFactory {

    private MovimientoDialogFactory() {}

    /** Plain data carrier for what the user entered — kept separate from
     *  {@link com.sibim.model.Movimiento} since fields like stockAnterior/
     *  stockNuevo/usuario are computed server-side by MovimientoService,
     *  not something this form collects. */
    public record Result(Producto producto, TipoMovimiento tipo, int cantidad,
                          String motivo, String referencia) {}

    public static Optional<Result> show(List<Producto> productos, String preProductoId, TipoMovimiento preTipo) {
        Dialog<ButtonType> dialog = DialogUtil.createButtonDialog(480);
        DialogUtil.styleOkButton(dialog.getDialogPane(), "#6D28D9");

        HBox header = DialogUtil.gradientHeader("↕", "Registrar Movimiento",
            "Registra una nueva entrada, salida, ajuste o transferencia al inventario",
            "#6D28D9", "#A21CAF");

        GridPane grid = DialogUtil.formGrid(128);
        Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());

        ComboBox<Producto> fProducto = new ComboBox<>(FXCollections.observableArrayList(productos));
        fProducto.setMaxWidth(Double.MAX_VALUE);
        fProducto.setPromptText("Seleccionar bien del inventario...");
        fProducto.getStyleClass().add("form-input");
        fProducto.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Producto p) { return p == null ? "" : p.getNombre() + "  [" + p.getCodigo() + "]"; }
            public Producto fromString(String s) { return null; }
        });
        if (preProductoId != null)
            productos.stream().filter(p -> p.getId().equals(preProductoId)).findFirst().ifPresent(fProducto::setValue);

        ComboBox<TipoMovimiento> fTipo = new ComboBox<>(FXCollections.observableArrayList(TipoMovimiento.values()));
        fTipo.setValue(preTipo != null ? preTipo : TipoMovimiento.ENTRADA);
        fTipo.setMaxWidth(Double.MAX_VALUE);
        fTipo.getStyleClass().add("form-input");
        fTipo.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TipoMovimiento t) {
                return t == null ? "" : switch (t) {
                    case ENTRADA       -> "⬆  Entrada (suma stock)";
                    case SALIDA        -> "⬇  Salida (resta stock)";
                    case AJUSTE        -> "⇄  Ajuste manual";
                    case TRANSFERENCIA -> "→  Transferencia entre áreas";
                };
            }
            public TipoMovimiento fromString(String s) { return null; }
        });

        Spinner<Integer> fCantidad = new Spinner<>(0, Integer.MAX_VALUE, 1);
        fCantidad.setEditable(true);
        fCantidad.setMaxWidth(Double.MAX_VALUE);
        fCantidad.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fCantidad);

        // Stock preview card
        HBox stockPreview = new HBox(16);
        stockPreview.setAlignment(Pos.CENTER_LEFT);
        stockPreview.setPadding(new Insets(10, 14, 10, 14));
        stockPreview.getStyleClass().add("dlg-stock-preview");

        VBox stockAntes = new VBox(2);
        Label lblAntesTit = new Label("STOCK ACTUAL");
        lblAntesTit.getStyleClass().add("dlg-stock-tit");
        Label lblStockActual = new Label("—");
        lblStockActual.getStyleClass().add("dlg-stock-val");
        stockAntes.getChildren().addAll(lblAntesTit, lblStockActual);

        Label arrow = new Label("→");
        arrow.getStyleClass().add("dlg-stock-arrow");

        VBox stockDespues = new VBox(2);
        Label lblDespuesTit = new Label("STOCK RESULTANTE");
        lblDespuesTit.getStyleClass().add("dlg-stock-tit");
        Label lblStockNuevo = new Label("—");
        lblStockNuevo.getStyleClass().add("dlg-stock-val");
        stockDespues.getChildren().addAll(lblDespuesTit, lblStockNuevo);

        stockPreview.getChildren().addAll(stockAntes, arrow, stockDespues);

        Runnable updateLabels = () -> {
            Producto p = fProducto.getValue();
            if (p == null) { lblStockActual.setText("—"); lblStockNuevo.setText("—"); return; }
            int current = p.getStockActual();
            int nuevo = ProductoUtils.calcularStockNuevo(
                fTipo.getValue().getCodigo(), current, fCantidad.getValue());
            lblStockActual.setText(String.valueOf(current));
            lblStockNuevo.setText(String.valueOf(nuevo));
            boolean warn = nuevo < p.getStockMinimo();
            lblStockNuevo.getStyleClass().removeAll("dlg-stock-val","dlg-stock-new-ok","dlg-stock-new-warn","dlg-stock-new-empty");
            lblStockNuevo.getStyleClass().add(nuevo <= 0 ? "dlg-stock-new-empty" : warn ? "dlg-stock-new-warn" : "dlg-stock-new-ok");
            arrow.getStyleClass().removeAll("dlg-stock-arrow","dlg-stock-arrow-up","dlg-stock-arrow-down");
            arrow.getStyleClass().add(nuevo > current ? "dlg-stock-arrow-up" : nuevo < current ? "dlg-stock-arrow-down" : "dlg-stock-arrow");
        };
        fProducto.valueProperty().addListener((o, a, b) -> updateLabels.run());
        fTipo.valueProperty().addListener((o, a, b) -> updateLabels.run());
        fCantidad.valueProperty().addListener((o, a, b) -> updateLabels.run());

        TextField fMotivo = new TextField();
        fMotivo.setPromptText("Motivo del movimiento (opcional)");
        fMotivo.setMaxWidth(Double.MAX_VALUE);
        fMotivo.getStyleClass().add("form-input");
        TextField fRef = new TextField();
        fRef.setPromptText("Número de folio, documento, etc.");
        fRef.setMaxWidth(Double.MAX_VALUE);
        fRef.getStyleClass().add("form-input");

        Label lblFormError = new Label();
        lblFormError.getStyleClass().add("field-error-label");
        lblFormError.setVisible(false);
        lblFormError.setManaged(false);
        lblFormError.setWrapText(true);

        // Disable OK until product selected and quantity valid
        if (okBtn != null) {
            Runnable validateOk = () -> {
                boolean noProduct = fProducto.getValue() == null;
                boolean badQty = fCantidad.getValue() <= 0 && fTipo.getValue() != TipoMovimiento.AJUSTE;
                okBtn.setDisable(noProduct || badQty);
                boolean showHint = noProduct || badQty;
                lblFormError.setText(noProduct ? "Selecciona un bien del inventario"
                    : "La cantidad debe ser mayor a cero");
                lblFormError.setVisible(showHint);
                lblFormError.setManaged(showHint);
            };
            validateOk.run();
            fProducto.valueProperty().addListener((obs, o, n) -> validateOk.run());
            fCantidad.valueProperty().addListener((obs, o, n) -> validateOk.run());
            fTipo.valueProperty().addListener((obs, o, n) -> validateOk.run());
        }

        int row = 0;
        grid.add(DialogUtil.fieldLabel("Producto *"),    0, row); grid.add(fProducto,    1, row++);
        grid.add(DialogUtil.fieldLabel("Tipo *"),        0, row); grid.add(fTipo,        1, row++);
        grid.add(DialogUtil.fieldLabel("Cantidad *"),    0, row); grid.add(fCantidad,    1, row++);
        grid.add(DialogUtil.fieldLabel("Movimiento"),    0, row); grid.add(stockPreview, 1, row++);
        grid.add(DialogUtil.fieldLabel("Motivo"),        0, row); grid.add(fMotivo,      1, row++);
        grid.add(DialogUtil.fieldLabel("Referencia"),    0, row); grid.add(fRef,         1, row);

        dialog.getDialogPane().setContent(new VBox(0, header, grid, lblFormError));
        Platform.runLater(() -> fProducto.requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            DialogUtil.commitSpinner(fCantidad);
            return Optional.of(new Result(fProducto.getValue(), fTipo.getValue(), fCantidad.getValue(),
                fMotivo.getText().trim(), fRef.getText().trim()));
        }
        return Optional.empty();
    }
}
