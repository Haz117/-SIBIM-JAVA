package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AutocompleteUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;

/** Builds and holds all fields for the "Datos Patrimoniales" tab of the
 *  "Nuevo/Editar Bien" dialog.  Package-private — only used by
 *  {@link ProductoDialogFactory}. */
class ProductoTabPatrimonioFields {

    final GridPane grid;
    final TextField fProveedor;
    final TextField fMarca;
    final TextField fModelo;
    final TextField fNumeroSerie;
    final TextField fUbicacion;
    final TextField fResguardante;
    final Label     lblResguardoActivo;
    final ComboBox<String> fEstadoFisico;
    final TextField fNumeroFactura;
    final CheckBox  fEtiquetado;
    final DatePicker fFechaAdq;
    final DatePicker fProximaRevision;
    final Spinner<Integer> fVidaUtil;
    final TextField fValorResidual;
    final TextArea  fNotasMant;
    final TextField fClaveArm;
    final TextField fColor;
    final TextField fTipoBien;
    final TextField fNoMotor;
    final TextField fNoTarjeta;
    final TextField fNoPoliza;

    ProductoTabPatrimonioFields(
            Producto existing,
            List<String> sugestMarcas,
            List<String> sugestModelos,
            List<String> sugestProveedores,
            List<String> sugestUbicaciones,
            String folioResguardoActivo) {

        grid = DialogUtil.formGrid(140);

        // ── Fields shared with gridPatrimonio (declared in original factory
        //    before gridPatrimonio but added only to gridPatrimonio) ──────────
        fProveedor = new TextField(existing != null && existing.getProveedor() != null
                ? existing.getProveedor() : "");
        fProveedor.setPromptText("Nombre del proveedor");
        fProveedor.getStyleClass().add("form-input");

        fMarca = new TextField(existing != null && existing.getMarca() != null
                ? existing.getMarca() : "");
        fMarca.setPromptText("Ej. HP, Dell, Brother…");
        fMarca.getStyleClass().add("form-input");

        fModelo = new TextField(existing != null && existing.getModelo() != null
                ? existing.getModelo() : "");
        fModelo.setPromptText("Modelo del bien");
        fModelo.getStyleClass().add("form-input");

        fNumeroSerie = new TextField(existing != null && existing.getNumeroSerie() != null
                ? existing.getNumeroSerie() : "");
        fNumeroSerie.setPromptText("Número de serie o placa");
        fNumeroSerie.getStyleClass().add("form-input");

        AutocompleteUtil.attach(fProveedor, sugestProveedores);
        AutocompleteUtil.attach(fMarca,     sugestMarcas);
        AutocompleteUtil.attach(fModelo,    sugestModelos);

        fUbicacion = new TextField(existing != null && existing.getUbicacion() != null
                ? existing.getUbicacion() : "");
        fUbicacion.setPromptText("Ubicación física");
        fUbicacion.getStyleClass().add("form-input");
        AutocompleteUtil.attach(fUbicacion, sugestUbicaciones);

        fResguardante = new TextField(existing != null && existing.getResguardante() != null
                ? existing.getResguardante() : "");
        fResguardante.setPromptText("Persona responsable del resguardo (nombre completo)");
        fResguardante.getStyleClass().add("form-input");
        // While a signed resguardo covers the bien, that document says who
        // holds it; typing another name here would contradict it.
        lblResguardoActivo = new Label(folioResguardoActivo == null ? "" :
            "Asignado por el resguardo " + folioResguardoActivo
            + ". Para cambiarlo, cancela ese resguardo o genera uno nuevo en Resguardos.");
        lblResguardoActivo.getStyleClass().addAll("field-hint", "muted-sm");
        lblResguardoActivo.setWrapText(true);
        lblResguardoActivo.setVisible(folioResguardoActivo != null);
        lblResguardoActivo.setManaged(folioResguardoActivo != null);
        fResguardante.setDisable(folioResguardoActivo != null);

        fEstadoFisico = new ComboBox<>(FXCollections.observableArrayList(
                "", "BUENO", "REGULAR", "MALO", "DEFICIENTE"));
        fEstadoFisico.setValue(existing != null && existing.getEstadoFisico() != null
                ? existing.getEstadoFisico() : "");
        fEstadoFisico.setMaxWidth(Double.MAX_VALUE);
        fEstadoFisico.getStyleClass().add("form-input");

        fNumeroFactura = new TextField(existing != null && existing.getNumeroFactura() != null
                ? existing.getNumeroFactura() : "");
        fNumeroFactura.setPromptText("Ej. B3623, F-MR-003343");
        fNumeroFactura.getStyleClass().add("form-input");

        fEtiquetado = new CheckBox("Bien etiquetado (tiene etiqueta física/QR)");
        fEtiquetado.setSelected(existing != null && existing.isEtiquetado());
        fEtiquetado.getStyleClass().add("form-input");

        // ── Depreciación (línea recta) ────────────────────────────────────────
        fFechaAdq = new DatePicker(existing != null ? existing.getFechaAdquisicion() : null);
        fFechaAdq.setConverter(FormatUtils.datePickerConverter());
        fFechaAdq.setPromptText("Fecha de adquisición");
        fFechaAdq.setMaxWidth(Double.MAX_VALUE);
        fFechaAdq.getStyleClass().add("form-input");

        fVidaUtil = new Spinner<>(1, 100,
            existing != null && existing.getVidaUtilAnios() != null
                ? existing.getVidaUtilAnios() : 5);
        fVidaUtil.setEditable(true);
        fVidaUtil.setMaxWidth(Double.MAX_VALUE);
        fVidaUtil.getStyleClass().add("form-input");
        DialogUtil.commitOnFocusLoss(fVidaUtil);

        fValorResidual = new TextField(
            existing != null && existing.getValorResidual() != null
                ? existing.getValorResidual().toPlainString() : "0");
        fValorResidual.setPromptText("0.00");
        fValorResidual.setMaxWidth(Double.MAX_VALUE);
        fValorResidual.getStyleClass().add("form-input");

        // ── Mantenimiento ────────────────────────────────────────────────────
        fProximaRevision = new DatePicker(
            existing != null ? existing.getProximaRevision() : null);
        fProximaRevision.setPromptText("dd/MM/yyyy");
        fProximaRevision.getStyleClass().add("form-input");

        fNotasMant = new TextArea(
            existing != null && existing.getNotasMantenimiento() != null
                ? existing.getNotasMantenimiento() : "");
        fNotasMant.setPromptText("Notas sobre el mantenimiento, historial, etc.");
        fNotasMant.setWrapText(true);
        fNotasMant.setPrefRowCount(3);
        fNotasMant.getStyleClass().add("form-input");

        // ── Campos de formatos oficiales ──────────────────────────────────────
        fClaveArm = new TextField(existing != null && existing.getClaveArmonizada() != null
                ? existing.getClaveArmonizada() : "");
        fClaveArm.setPromptText("Ej. 1.2.4.4.541.3");
        fClaveArm.getStyleClass().add("form-input");

        fColor = new TextField(existing != null && existing.getColor() != null
                ? existing.getColor() : "");
        fColor.setPromptText("Ej. ACERO INOXIDABLE PLATA, BLANCO, GRIS");
        fColor.getStyleClass().add("form-input");

        fTipoBien = new TextField(existing != null && existing.getTipoBien() != null
                ? existing.getTipoBien() : "");
        fTipoBien.setPromptText("Ej. 3/2 ton, Camioneta, Oficina");
        fTipoBien.getStyleClass().add("form-input");

        fNoMotor = new TextField(existing != null && existing.getNoMotor() != null
                ? existing.getNoMotor() : "");
        fNoMotor.setPromptText("Número de motor (vehículos)");
        fNoMotor.getStyleClass().add("form-input");

        fNoTarjeta = new TextField(existing != null && existing.getNoTarjetaCirculacion() != null
                ? existing.getNoTarjetaCirculacion() : "");
        fNoTarjeta.setPromptText("Número de tarjeta de circulación");
        fNoTarjeta.getStyleClass().add("form-input");

        fNoPoliza = new TextField(existing != null && existing.getNoPolizaSeguro() != null
                ? existing.getNoPolizaSeguro() : "");
        fNoPoliza.setPromptText("Número de póliza de seguro");
        fNoPoliza.getStyleClass().add("form-input");

        // ── Assemble gridPatrimonio ───────────────────────────────────────────
        int rp = 0;
        grid.add(DialogUtil.fieldLabel("Proveedor"),     0, rp); grid.add(fProveedor,    1, rp++);
        grid.add(DialogUtil.fieldLabel("Marca"),         0, rp); grid.add(fMarca,        1, rp++);
        grid.add(DialogUtil.fieldLabel("Modelo"),        0, rp); grid.add(fModelo,       1, rp++);
        grid.add(DialogUtil.fieldLabel("N° de Serie"),   0, rp); grid.add(fNumeroSerie,  1, rp++);
        grid.add(DialogUtil.fieldLabel("Ubicación"),     0, rp); grid.add(fUbicacion,    1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Resguardante",
            "Persona física responsable del resguardo y custodia del bien.\nNormalmente el jefe de área o el usuario directo."),
                                                         0, rp); grid.add(new javafx.scene.layout.VBox(2, fResguardante, lblResguardoActivo), 1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Estado físico",
            "Condición actual del bien según el último levantamiento físico.\nBUENO = sin daños; REGULAR = desgaste menor; MALO = requiere reparación; DEFICIENTE = fuera de uso."),
                                                         0, rp); grid.add(fEstadoFisico, 1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("N° Factura",
            "Número del documento de compra o factura.\nDistinto de la foto de factura — este es el folio para cruce contable."),
                                                         0, rp); grid.add(fNumeroFactura, 1, rp++);
        grid.add(new javafx.scene.control.Separator(), 0, rp, 2, 1); rp++;
        grid.add(DialogUtil.fieldLabel("Etiquetado"), 0, rp);
        grid.add(fEtiquetado, 1, rp++);
        grid.add(new javafx.scene.control.Separator(), 0, rp, 2, 1); rp++;

        Label lblDepSection = new Label("Depreciación (línea recta)");
        lblDepSection.getStyleClass().add("dialog-field-label");
        grid.add(lblDepSection, 0, rp, 2, 1); rp++;

        grid.add(DialogUtil.fieldLabelWithHelp("Fecha adquisición",
            "Fecha en que se adquirió el bien.\nBase para el cálculo de depreciación."),
                                                         0, rp); grid.add(fFechaAdq,      1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Vida útil (años)",
            "Número de años en que el bien se deprecia completamente\n(SAT México: equipos de cómputo 3 años, vehículos 4, mobiliario 10)."),
                                                         0, rp); grid.add(fVidaUtil,      1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Valor residual",
            "Valor de rescate o residual al final de la vida útil (puede ser $0)."),
                                                         0, rp); grid.add(fValorResidual, 1, rp++);

        grid.add(new javafx.scene.control.Separator(), 0, rp, 2, 1); rp++;
        Label lblMantSection = new Label("Mantenimiento");
        lblMantSection.getStyleClass().add("dialog-field-label");
        grid.add(lblMantSection, 0, rp, 2, 1); rp++;

        grid.add(DialogUtil.fieldLabelWithHelp("Próxima revisión",
            "Fecha programada para la próxima revisión o mantenimiento preventivo del bien."),
            0, rp); grid.add(fProximaRevision, 1, rp++);
        grid.add(DialogUtil.fieldLabel("Notas de mantenimiento"), 0, rp); grid.add(fNotasMant, 1, rp++);

        grid.add(new javafx.scene.control.Separator(), 0, rp, 2, 1); rp++;
        Label lblFormOficial = new Label("Datos para formatos oficiales");
        lblFormOficial.getStyleClass().add("dialog-field-label");
        grid.add(lblFormOficial, 0, rp, 2, 1); rp++;

        grid.add(DialogUtil.fieldLabelWithHelp("Clave Armonizada",
            "Clave LGCG del bien (Ej. 1.2.4.4.541.3.003).\nAparece en ANEXO V.4 e Inventario de Parque Vehicular."),
            0, rp); grid.add(fClaveArm, 1, rp++);
        grid.add(DialogUtil.fieldLabel("Color / Material"), 0, rp); grid.add(fColor, 1, rp++);
        grid.add(DialogUtil.fieldLabel("Tipo de bien"), 0, rp); grid.add(fTipoBien, 1, rp++);
        grid.add(DialogUtil.fieldLabel("N° de Motor"), 0, rp); grid.add(fNoMotor, 1, rp++);
        grid.add(DialogUtil.fieldLabel("N° Tarjeta Circ."), 0, rp); grid.add(fNoTarjeta, 1, rp++);
        grid.add(DialogUtil.fieldLabel("N° Póliza Seguro"), 0, rp); grid.add(fNoPoliza, 1, rp);
    }

    /** Attaches dirty-tracking listeners on all editable fields. Call AFTER
     *  all initial {@code setValue()} calls so pre-filled values on edit don't
     *  immediately mark the form dirty. */
    void wireDirty(Runnable markDirty) {
        fProveedor.textProperty().addListener((o, a, b)        -> markDirty.run());
        fMarca.textProperty().addListener((o, a, b)            -> markDirty.run());
        fModelo.textProperty().addListener((o, a, b)           -> markDirty.run());
        fNumeroSerie.textProperty().addListener((o, a, b)      -> markDirty.run());
        fUbicacion.textProperty().addListener((o, a, b)        -> markDirty.run());
        fResguardante.textProperty().addListener((o, a, b)     -> markDirty.run());
        fEstadoFisico.valueProperty().addListener((o, a, b)    -> markDirty.run());
        fNumeroFactura.textProperty().addListener((o, a, b)    -> markDirty.run());
        fEtiquetado.selectedProperty().addListener((o, a, b)   -> markDirty.run());
        fFechaAdq.valueProperty().addListener((o, a, b)        -> markDirty.run());
        fProximaRevision.valueProperty().addListener((o, a, b) -> markDirty.run());
        fVidaUtil.valueProperty().addListener((o, a, b)        -> markDirty.run());
        fValorResidual.textProperty().addListener((o, a, b)    -> markDirty.run());
        fNotasMant.textProperty().addListener((o, a, b)        -> markDirty.run());
        fClaveArm.textProperty().addListener((o, a, b)         -> markDirty.run());
        fColor.textProperty().addListener((o, a, b)            -> markDirty.run());
        fTipoBien.textProperty().addListener((o, a, b)         -> markDirty.run());
        fNoMotor.textProperty().addListener((o, a, b)          -> markDirty.run());
        fNoTarjeta.textProperty().addListener((o, a, b)        -> markDirty.run());
        fNoPoliza.textProperty().addListener((o, a, b)         -> markDirty.run());
    }
}
