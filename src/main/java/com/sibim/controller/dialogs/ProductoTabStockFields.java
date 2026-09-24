package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/** Builds and holds all fields for the "Stock y Precios" tab of the
 *  "Nuevo/Editar Bien" dialog.  Package-private — only used by
 *  {@link ProductoDialogFactory}. */
class ProductoTabStockFields {

    final GridPane grid;
    final Spinner<Integer>    fStock;
    final Spinner<Integer>    fStockMin;
    final Spinner<Integer>    fStockMax;
    final ComboBox<UnidadMedida> fUnidad;
    final TextField           fPrecioC;
    final TextField           fPrecioV;
    final DatePicker          fVenc;
    final Label               lblPrecioCHint;
    final Label               lblPrecioVHint;

    ProductoTabStockFields(Producto existing) {

        grid = DialogUtil.formGrid(140);

        fStock = new Spinner<>(0, 999_999, existing != null ? existing.getStockActual() : 0);
        fStock.setEditable(true);
        fStock.setMaxWidth(Double.MAX_VALUE);
        fStock.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStock);

        fStockMin = new Spinner<>(0, 999_999, existing != null ? existing.getStockMinimo() : 0);
        fStockMin.setEditable(true);
        fStockMin.setMaxWidth(Double.MAX_VALUE);
        fStockMin.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStockMin);

        fStockMax = new Spinner<>(0, 999_999, existing != null ? existing.getStockMaximo() : 100);
        fStockMax.setEditable(true);
        fStockMax.setMaxWidth(Double.MAX_VALUE);
        fStockMax.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStockMax);

        fUnidad = new ComboBox<>(FXCollections.observableArrayList(UnidadMedida.values()));
        fUnidad.setValue(existing != null ? existing.getUnidad() : UnidadMedida.PIEZA);
        fUnidad.setMaxWidth(Double.MAX_VALUE);
        fUnidad.getStyleClass().add("form-input");

        fPrecioC = new TextField(existing != null && existing.getPrecioCompra() != null
            ? existing.getPrecioCompra().toPlainString() : "0");
        fPrecioC.setPromptText("0.00");
        fPrecioC.getStyleClass().add("form-input");

        lblPrecioCHint = new Label();
        lblPrecioCHint.getStyleClass().add("field-hint");
        lblPrecioCHint.setVisible(false);
        lblPrecioCHint.setManaged(false);
        fPrecioC.textProperty().addListener((obs, old, val) -> {
            try {
                new BigDecimal(val.trim());
                lblPrecioCHint.setVisible(false);
                lblPrecioCHint.setManaged(false);
                fPrecioC.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                lblPrecioCHint.setText("Formato inválido — usa números (ej. 1500.00)");
                lblPrecioCHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                lblPrecioCHint.getStyleClass().add("field-hint-error");
                lblPrecioCHint.setVisible(true);
                lblPrecioCHint.setManaged(true);
                fPrecioC.getStyleClass().add("field-error");
            }
        });

        fPrecioV = new TextField(existing != null && existing.getPrecioVenta() != null
            ? existing.getPrecioVenta().toPlainString() : "0");
        fPrecioV.setPromptText("0.00");
        fPrecioV.getStyleClass().add("form-input");

        lblPrecioVHint = new Label();
        lblPrecioVHint.getStyleClass().add("field-hint");
        lblPrecioVHint.setVisible(false);
        lblPrecioVHint.setManaged(false);
        fPrecioV.textProperty().addListener((obs, old, val) -> {
            try {
                new BigDecimal(val.trim());
                lblPrecioVHint.setVisible(false);
                lblPrecioVHint.setManaged(false);
                fPrecioV.getStyleClass().remove("field-error");
            } catch (Exception ex) {
                lblPrecioVHint.setText("Formato inválido — usa números (ej. 1500.00)");
                lblPrecioVHint.getStyleClass().removeAll("field-hint-ok", "field-hint-error");
                lblPrecioVHint.getStyleClass().add("field-hint-error");
                lblPrecioVHint.setVisible(true);
                lblPrecioVHint.setManaged(true);
                fPrecioV.getStyleClass().add("field-error");
            }
        });

        fVenc = new DatePicker(existing != null ? existing.getFechaVencimiento() : null);
        fVenc.setConverter(FormatUtils.datePickerConverter());
        fVenc.setMaxWidth(Double.MAX_VALUE);
        fVenc.getStyleClass().add("form-input");

        // ── Assemble gridStock ────────────────────────────────────────────────
        int rs = 0;
        grid.add(DialogUtil.fieldLabel("Stock Actual"),    0, rs); grid.add(fStock,    1, rs++);
        grid.add(DialogUtil.fieldLabelWithHelp("Stock Mínimo",
            "Cuando el stock baje de este número se generará\nuna alerta automática en el módulo de Alertas."),
                                                           0, rs); grid.add(fStockMin, 1, rs++);
        grid.add(DialogUtil.fieldLabelWithHelp("Stock Máximo",
            "Límite de referencia para sobre-stock.\n" +
            "No bloquea entradas; sirve para reportes y alertas de exceso."),
                                                           0, rs); grid.add(fStockMax, 1, rs++);
        grid.add(DialogUtil.fieldLabel("Unidad"),          0, rs); grid.add(fUnidad,   1, rs++);
        grid.add(DialogUtil.fieldLabel("Precio Compra"),   0, rs); grid.add(new VBox(2, fPrecioC, lblPrecioCHint), 1, rs++);
        grid.add(DialogUtil.fieldLabel("Precio Venta"),    0, rs); grid.add(new VBox(2, fPrecioV, lblPrecioVHint), 1, rs++);
        grid.add(DialogUtil.fieldLabel("Fecha Venc."),     0, rs); grid.add(fVenc,     1, rs++);
    }

    /** Attaches dirty-tracking listeners on all editable fields. Call AFTER
     *  all initial {@code setValue()} calls so pre-filled values on edit don't
     *  immediately mark the form dirty. */
    void wireDirty(Runnable markDirty) {
        fStock.valueProperty().addListener((o, a, b)    -> markDirty.run());
        fStockMin.valueProperty().addListener((o, a, b) -> markDirty.run());
        fStockMax.valueProperty().addListener((o, a, b) -> markDirty.run());
        fUnidad.valueProperty().addListener((o, a, b)   -> markDirty.run());
        fPrecioC.textProperty().addListener((o, a, b)   -> markDirty.run());
        fPrecioV.textProperty().addListener((o, a, b)   -> markDirty.run());
        fVenc.valueProperty().addListener((o, a, b)     -> markDirty.run());
    }
}
