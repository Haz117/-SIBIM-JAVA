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

/** Builds and holds all fields for the "Valor y cantidad" tab of the
 *  "Nuevo/Editar Bien" dialog.  Package-private — only used by
 *  {@link ProductoDialogFactory}.
 *
 *  A municipal bien is valued at its acquisition cost; there is no sale
 *  price (ProductoService mirrors it from the purchase price). Minimum and
 *  maximum quantities only matter for bienes managed by quantity (lots,
 *  consumables), so they live in a collapsed "Control de existencias"
 *  section instead of in every bien's form. */
class ProductoTabStockFields {

    final VBox                grid;
    final Spinner<Integer>    fStock;
    final Spinner<Integer>    fStockMin;
    final Spinner<Integer>    fStockMax;
    final ComboBox<UnidadMedida> fUnidad;
    final TextField           fPrecioC;
    final DatePicker          fVenc;
    final Label               lblPrecioCHint;
    final Label               lblStockBloqueado;

    ProductoTabStockFields(Producto existing) {

        GridPane principal = DialogUtil.formGrid(140);
        boolean isEdit = existing != null && existing.getId() != null;

        fStock = new Spinner<>(0, 999_999, existing != null ? existing.getStockActual() : 1);
        // Once a bien exists its quantity only changes through movimientos
        // (Entrada/Salida/Ajuste), so every change is audited and concurrent
        // movements are never overwritten by a stale value from this form.
        fStock.setEditable(!isEdit);
        fStock.setDisable(isEdit);
        fStock.setMaxWidth(Double.MAX_VALUE);
        fStock.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStock);
        lblStockBloqueado = new Label("Para cambiar la cantidad registra una Entrada, Salida o Ajuste en Movimientos.");
        lblStockBloqueado.getStyleClass().addAll("field-hint", "muted-sm");
        lblStockBloqueado.setWrapText(true);
        lblStockBloqueado.setVisible(isEdit);
        lblStockBloqueado.setManaged(isEdit);

        fStockMin = new Spinner<>(0, 999_999, existing != null ? existing.getStockMinimo() : 0);
        fStockMin.setEditable(true);
        fStockMin.setMaxWidth(Double.MAX_VALUE);
        fStockMin.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fStockMin);

        fStockMax = new Spinner<>(0, 999_999, existing != null ? existing.getStockMaximo() : 1);
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

        fVenc = new DatePicker(existing != null ? existing.getFechaVencimiento() : null);
        fVenc.setConverter(FormatUtils.datePickerConverter());
        fVenc.setMaxWidth(Double.MAX_VALUE);
        fVenc.getStyleClass().add("form-input");

        // ── Main fields ───────────────────────────────────────────────────────
        int rs = 0;
        principal.add(DialogUtil.fieldLabelWithHelp("Costo de adquisición",
            "Precio de compra por unidad, según factura.\n"
            + "Es el valor patrimonial del bien en reportes y actas."),
                                                                  0, rs); principal.add(new VBox(2, fPrecioC, lblPrecioCHint), 1, rs++);
        principal.add(DialogUtil.fieldLabel("Cantidad"),          0, rs); principal.add(new VBox(2, fStock, lblStockBloqueado), 1, rs++);
        principal.add(DialogUtil.fieldLabel("Unidad"),            0, rs); principal.add(fUnidad,   1, rs++);
        principal.add(DialogUtil.fieldLabelWithHelp("Garantía hasta",
            "Fecha en que vence la garantía del bien.\n"
            + "Aparece en Alertas 30 días antes."),
                                                                  0, rs); principal.add(fVenc,     1, rs++);

        // ── Optional quantity control ─────────────────────────────────────────
        GridPane existencias = DialogUtil.formGrid(140);
        int re = 0;
        existencias.add(DialogUtil.fieldLabelWithHelp("Existencia mínima",
            "Cuando la cantidad baje de este número se generará\nuna alerta en el módulo de Alertas."),
                                                                  0, re); existencias.add(fStockMin, 1, re++);
        existencias.add(DialogUtil.fieldLabelWithHelp("Existencia máxima",
            "Límite de referencia. No bloquea entradas;\nsirve para reportes y alertas de exceso."),
                                                                  0, re); existencias.add(fStockMax, 1, re++);
        TitledPane control = new TitledPane("Control de existencias (solo para bienes por lote o consumibles)", existencias);
        control.setExpanded(existing != null && existing.getStockMaximo() > 1);
        control.getStyleClass().add("form-optional-section");

        grid = new VBox(12, principal, control);
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
        fVenc.valueProperty().addListener((o, a, b)     -> markDirty.run());
    }
}
