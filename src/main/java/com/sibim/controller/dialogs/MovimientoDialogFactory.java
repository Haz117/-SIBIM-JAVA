package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.util.DialogUtil;
import com.sibim.util.ProductoUtils;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
                          String motivo, String referencia, String areaDestino) {}

    public static Optional<Result> show(List<Producto> productos, String preProductoId, TipoMovimiento preTipo) {
        return show(productos, preProductoId, preTipo, null);
    }

    /** @param retryFrom when reopening after a failed save, the data the
     *  user already entered — pre-fills every field so a BD/validation
     *  failure doesn't force re-typing cantidad/motivo/referencia from
     *  scratch. Null for a normal fresh open. */
    public static Optional<Result> show(List<Producto> productos, String preProductoId, TipoMovimiento preTipo, Result retryFrom) {
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
        String initialProductoId = retryFrom != null ? retryFrom.producto().getId() : preProductoId;
        if (initialProductoId != null)
            productos.stream().filter(p -> p.getId().equals(initialProductoId)).findFirst().ifPresent(fProducto::setValue);

        ComboBox<TipoMovimiento> fTipo = new ComboBox<>(FXCollections.observableArrayList(TipoMovimiento.values()));
        fTipo.setValue(retryFrom != null ? retryFrom.tipo() : preTipo != null ? preTipo : TipoMovimiento.ENTRADA);
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

        Spinner<Integer> fCantidad = new Spinner<>(0, Integer.MAX_VALUE, retryFrom != null ? retryFrom.cantidad() : 1);
        fCantidad.setEditable(true);
        fCantidad.setMaxWidth(Double.MAX_VALUE);
        fCantidad.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fCantidad);

        // Only relevant/visible for TRANSFERENCIA — the area the product is
        // moving TO. Options exclude the selected product's own current
        // area (can't "transfer" to where it already is).
        ComboBox<String> fAreaDestino = new ComboBox<>();
        fAreaDestino.setMaxWidth(Double.MAX_VALUE);
        fAreaDestino.setPromptText("Área de destino...");
        fAreaDestino.getStyleClass().add("form-input");
        Label lblAreaDestino = DialogUtil.fieldLabel("Área destino *");
        Runnable refreshAreaOptions = () -> {
            Producto p = fProducto.getValue();
            String current = fAreaDestino.getValue();
            List<String> opts = new java.util.ArrayList<>(com.sibim.config.Areas.getAllAreaNames());
            if (p != null) opts.remove(p.getArea());
            fAreaDestino.setItems(FXCollections.observableArrayList(opts));
            if (current != null && opts.contains(current)) fAreaDestino.setValue(current);
        };
        refreshAreaOptions.run();
        if (retryFrom != null && retryFrom.areaDestino() != null) fAreaDestino.setValue(retryFrom.areaDestino());
        fProducto.valueProperty().addListener((o, a, b) -> refreshAreaOptions.run());

        // "Área A → Área B" preview — visual reinforcement (not just the text
        // in the combo box) that a transfer actually moves the item, with a
        // short animation on the destination chip whenever it changes.
        HBox transferPreview = new HBox(12);
        transferPreview.setAlignment(Pos.CENTER_LEFT);
        transferPreview.setPadding(new Insets(12, 14, 12, 14));
        transferPreview.getStyleClass().add("dlg-transfer-preview");

        Label chipOrigenLbl = new Label();
        StackPane chipOrigen = new StackPane(chipOrigenLbl);
        chipOrigen.getStyleClass().add("dlg-transfer-chip");
        chipOrigen.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chipOrigen, Priority.ALWAYS);

        Label transferArrow = new Label("→");
        transferArrow.getStyleClass().add("dlg-transfer-arrow");

        Label chipDestinoLbl = new Label();
        StackPane chipDestino = new StackPane(chipDestinoLbl);
        chipDestino.getStyleClass().add("dlg-transfer-chip");
        chipDestino.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chipDestino, Priority.ALWAYS);

        transferPreview.getChildren().addAll(chipOrigen, transferArrow, chipDestino);
        GridPane.setColumnSpan(transferPreview, 2);

        Runnable updateTransferPreview = () -> {
            Producto p = fProducto.getValue();
            String origen = p != null ? p.getArea() : null;
            String destino = fAreaDestino.getValue();
            boolean hasOrigen = origen != null;
            chipOrigenLbl.setText(hasOrigen ? origen : "Selecciona un bien");
            chipOrigenLbl.getStyleClass().setAll(hasOrigen ? "dlg-transfer-chip-lbl" : "dlg-transfer-chip-empty-lbl");
            boolean hasDestino = destino != null;
            chipDestinoLbl.setText(hasDestino ? destino : "Selecciona destino");
            chipDestinoLbl.getStyleClass().setAll(hasDestino ? "dlg-transfer-chip-lbl" : "dlg-transfer-chip-empty-lbl");
            chipDestino.getStyleClass().remove("dlg-transfer-chip-to");
            if (hasDestino) chipDestino.getStyleClass().add("dlg-transfer-chip-to");
        };
        updateTransferPreview.run();

        // Short arrow-nudge + chip pop, so the destination chip visibly
        // "arrives" instead of just silently updating its text.
        Runnable animateTransferArrival = () -> {
            TranslateTransition nudge = new TranslateTransition(Duration.millis(260), transferArrow);
            nudge.setFromX(-8); nudge.setToX(0);
            FadeTransition fade = new FadeTransition(Duration.millis(220), chipDestino);
            fade.setFromValue(0.25); fade.setToValue(1);
            ScaleTransition pop = new ScaleTransition(Duration.millis(220), chipDestino);
            pop.setFromX(0.88); pop.setFromY(0.88); pop.setToX(1); pop.setToY(1);
            new ParallelTransition(nudge, fade, pop).play();
        };

        fAreaDestino.valueProperty().addListener((o, a, b) -> {
            updateTransferPreview.run();
            if (b != null) animateTransferArrival.run();
        });
        fProducto.valueProperty().addListener((o, a, b) -> updateTransferPreview.run());

        Runnable updateAreaDestinoVisibility = () -> {
            boolean isTransfer = fTipo.getValue() == TipoMovimiento.TRANSFERENCIA;
            lblAreaDestino.setVisible(isTransfer);   lblAreaDestino.setManaged(isTransfer);
            fAreaDestino.setVisible(isTransfer);     fAreaDestino.setManaged(isTransfer);
            transferPreview.setVisible(isTransfer);  transferPreview.setManaged(isTransfer);
        };
        updateAreaDestinoVisibility.run();
        fTipo.valueProperty().addListener((o, a, b) -> updateAreaDestinoVisibility.run());

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

        TextField fMotivo = new TextField(retryFrom != null && retryFrom.motivo() != null ? retryFrom.motivo() : "");
        fMotivo.setPromptText("Motivo del movimiento (opcional)");
        fMotivo.setMaxWidth(Double.MAX_VALUE);
        fMotivo.getStyleClass().add("form-input");
        TextField fRef = new TextField(retryFrom != null && retryFrom.referencia() != null ? retryFrom.referencia() : "");
        fRef.setPromptText("Número de folio, documento, etc.");
        fRef.setMaxWidth(Double.MAX_VALUE);
        fRef.getStyleClass().add("form-input");

        Label lblFormError = new Label();
        lblFormError.getStyleClass().add("field-error-label");
        lblFormError.setVisible(false);
        lblFormError.setManaged(false);
        lblFormError.setWrapText(true);

        // Disable OK until product selected, quantity valid, and (for a
        // transfer) a destination area chosen
        if (okBtn != null) {
            Runnable validateOk = () -> {
                boolean noProduct = fProducto.getValue() == null;
                boolean badQty = fCantidad.getValue() <= 0 && fTipo.getValue() != TipoMovimiento.AJUSTE;
                boolean isTransfer = fTipo.getValue() == TipoMovimiento.TRANSFERENCIA;
                boolean noDestino = isTransfer && fAreaDestino.getValue() == null;
                okBtn.setDisable(noProduct || badQty || noDestino);
                String hint = noProduct ? "Selecciona un bien del inventario"
                    : noDestino ? "Selecciona el área de destino"
                    : "La cantidad debe ser mayor a cero";
                boolean showHint = noProduct || badQty || noDestino;
                lblFormError.setText(hint);
                lblFormError.setVisible(showHint);
                lblFormError.setManaged(showHint);
            };
            validateOk.run();
            fProducto.valueProperty().addListener((obs, o, n) -> validateOk.run());
            fCantidad.valueProperty().addListener((obs, o, n) -> validateOk.run());
            fTipo.valueProperty().addListener((obs, o, n) -> validateOk.run());
            fAreaDestino.valueProperty().addListener((obs, o, n) -> validateOk.run());
        }

        int row = 0;
        grid.add(DialogUtil.fieldLabel("Producto *"),    0, row); grid.add(fProducto,    1, row++);
        grid.add(DialogUtil.fieldLabel("Tipo *"),        0, row); grid.add(fTipo,        1, row++);
        grid.add(lblAreaDestino,                         0, row); grid.add(fAreaDestino, 1, row++);
        grid.add(transferPreview,                        0, row++);
        grid.add(DialogUtil.fieldLabel("Cantidad *"),    0, row); grid.add(fCantidad,    1, row++);
        grid.add(DialogUtil.fieldLabel("Movimiento"),    0, row); grid.add(stockPreview, 1, row++);
        grid.add(DialogUtil.fieldLabel("Motivo"),        0, row); grid.add(fMotivo,      1, row++);
        grid.add(DialogUtil.fieldLabel("Referencia"),    0, row); grid.add(fRef,         1, row);

        dialog.getDialogPane().setContent(new VBox(0, header, grid, lblFormError));
        Platform.runLater(() -> fProducto.requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            DialogUtil.commitSpinner(fCantidad);
            boolean isTransfer = fTipo.getValue() == TipoMovimiento.TRANSFERENCIA;
            return Optional.of(new Result(fProducto.getValue(), fTipo.getValue(), fCantidad.getValue(),
                fMotivo.getText().trim(), fRef.getText().trim(), isTransfer ? fAreaDestino.getValue() : null));
        }
        return Optional.empty();
    }
}
