package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AutocompleteUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Objects;

/** Builds and holds all fields for the "Datos Patrimoniales" tab of the
 *  "Nuevo/Editar Bien" dialog.  Package-private — only used by
 *  {@link ProductoDialogFactory}. */
class ProductoTabPatrimonioFields {

    final GridPane grid;
    final TextField fProveedor;
    final TextField fMarca;
    final TextField fModelo;
    final TextField fNumeroSerie;
    /** "No tiene": the bien has no marca / modelo / serie, instead of typing "SIN MARCA". */
    final CheckBox  fSinMarca  = new CheckBox("No tiene");
    final CheckBox  fSinModelo = new CheckBox("No tiene");
    final CheckBox  fSinSerie  = new CheckBox("No tiene");
    final TextField fUbicacion;
    final TextField fResguardante;
    final AvisoNombreParecido avisoResguardante;
    final Label     lblResguardoActivo;
    final ComboBox<String> fEstadoFisico;
    final TextField fNumeroFactura;
    final TextField fFolioFiscal;
    private final String facturaOriginal;
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
        this(existing, sugestMarcas, sugestModelos, sugestProveedores, sugestUbicaciones, folioResguardoActivo, List.of());
    }

    /** @param resguardantes the names already in use, offered as the name is typed */
    ProductoTabPatrimonioFields(
            Producto existing,
            List<String> sugestMarcas,
            List<String> sugestModelos,
            List<String> sugestProveedores,
            List<String> sugestUbicaciones,
            String folioResguardoActivo,
            List<String> resguardantes) {

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
        noTiene(fMarca, fSinMarca);
        noTiene(fModelo, fSinModelo);
        noTiene(fNumeroSerie, fSinSerie);

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
        List<String> nombres = resguardantes != null ? resguardantes : List.of();
        AutocompleteUtil.attach(fResguardante, nombres);
        avisoResguardante = new AvisoNombreParecido(fResguardante, () -> nombres);

        fEstadoFisico = new ComboBox<>(FXCollections.observableArrayList(
                "", "BUENO", "REGULAR", "MALO", "DEFICIENTE"));
        fEstadoFisico.setValue(existing != null && existing.getEstadoFisico() != null
                ? existing.getEstadoFisico() : "");
        fEstadoFisico.setMaxWidth(Double.MAX_VALUE);
        fEstadoFisico.getStyleClass().add("form-input");

        fNumeroFactura = new TextField(existing != null && existing.getNumeroFactura() != null
                ? existing.getNumeroFactura() : "");
        fNumeroFactura.setPromptText("Ej. B1234, F-XX-000001");
        fNumeroFactura.getStyleClass().add("form-input");

        fFolioFiscal = new TextField(existing != null && existing.getFolioFiscal() != null
                ? existing.getFolioFiscal() : "");
        fFolioFiscal.setPromptText("UUID del CFDI, ej. A1B2C3D4-0000-4000-8000-000000000001");
        fFolioFiscal.getStyleClass().add("form-input");
        // Only 0-9 and A-F go in a folio fiscal: what is typed goes in capitals, an O as a zero
        // and an I or an L as a one. Same length: the caret and Backspace stay where they are.
        fFolioFiscal.setTextFormatter(new TextFormatter<String>(c -> {
            if (c.isContentChange() && !c.getText().isEmpty())
                c.setText(com.sibim.service.CalidadDatosService.escribirFolio(c.getText()));
            return c;
        }));
        facturaOriginal = existing != null ? existing.getNumeroFactura() : null;
        fNumeroFactura.focusedProperty().addListener((o, antes, ahora) -> { if (!ahora) pasarFolioFiscal(); });
        lnkFolioALugar.setPadding(javafx.geometry.Insets.EMPTY);
        lnkFolioALugar.setOnAction(e -> ponerFolioEnSuLugar());
        fNumeroFactura.textProperty().addListener((o, a, b) -> verFolioALugar());
        fFolioFiscal.textProperty().addListener((o, a, b) -> verFolioALugar());
        fNumeroFactura.disabledProperty().addListener((o, a, b) -> verFolioALugar());
        verFolioALugar();

        fEtiquetado = new CheckBox("Bien etiquetado (tiene etiqueta física/QR)");
        fEtiquetado.setSelected(existing != null && existing.isEtiquetado());
        fEtiquetado.getStyleClass().add("form-input");

        // ── Depreciación (línea recta) ────────────────────────────────────────
        fFechaAdq = new DatePicker(existing != null ? existing.getFechaAdquisicion() : null);
        fFechaAdq.setConverter(FormatUtils.datePickerConverter());
        fFechaAdq.setPromptText("Fecha de adquisición");
        fFechaAdq.setMaxWidth(Double.MAX_VALUE);
        fFechaAdq.getStyleClass().add("form-input");

        fVidaUtil = new Spinner<>(new VidaUtilFactory(existing != null ? existing.getVidaUtilAnios() : null));
        fVidaUtil.setEditable(true);
        fVidaUtil.getEditor().setPromptText("Sin definir");
        fVidaUtil.setMaxWidth(Double.MAX_VALUE);
        fVidaUtil.getStyleClass().add("form-input");
        fVidaUtil.focusedProperty().addListener((o, antes, ahora) -> { if (!ahora) vidaUtil(); });

        fValorResidual = new TextField(
            existing != null && existing.getValorResidual() != null
                ? existing.getValorResidual().toPlainString() : "0");
        fValorResidual.setPromptText("0.00");
        fValorResidual.setMaxWidth(Double.MAX_VALUE);
        fValorResidual.getStyleClass().add("form-input");

        // ── Mantenimiento ────────────────────────────────────────────────────
        fProximaRevision = new DatePicker(
            existing != null ? existing.getProximaRevision() : null);
        fProximaRevision.setPromptText("dd/mm/aaaa");
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
        // The format of the inventory and of Categorías (cuenta CONAC + partida).
        fClaveArm.setPromptText("Ej. 1.2.4.1.1.511");
        fClaveArm.getStyleClass().add("form-input");

        fColor = new TextField(existing != null && existing.getColor() != null
                ? existing.getColor() : "");
        fColor.setPromptText("Ej. ACERO INOXIDABLE PLATA, BLANCO, GRIS");
        fColor.getStyleClass().add("form-input");

        fTipoBien = new TextField(existing != null && existing.getTipoBien() != null
                ? existing.getTipoBien() : "");
        fTipoBien.setPromptText("Ej. 3/2 ton, Camioneta, Oficina");
        fTipoBien.getStyleClass().add("form-input");
        // «MUEBLE» was typed by hand 351 times from 28-oct to 13-nov-2026 (and «MUELE», «mueble»,
        // a color): the CONAC account already says it, so an empty tipo comes written, and it
        // follows the account while nobody writes another.
        lblTipoPropuesto.getStyleClass().addAll("field-hint", "muted-sm");
        lblTipoPropuesto.setWrapText(true);
        fClaveArm.textProperty().addListener((o, a, b) -> proponerTipo());
        fTipoBien.textProperty().addListener((o, a, b) -> verTipoPropuesto());
        proponerTipo();
        btnTipos = com.sibim.util.ListaParaElegir.boton(fTipoBien, "el tipo de bien");
        fTipoBien.focusedProperty().addListener((o, a, enfocado) -> { if (!enfocado) escribirTipoComoLaLista(); });

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

        // A folio with another shape is said while typing (12-nov-2026: 25 of 288 had an O for
        // a zero, a character short or the factura's own number); it is not blocked.
        avisoFolio.getStyleClass().addAll("field-hint", "field-hint-warn");
        avisoFolio.setWrapText(true);
        // Wraps under the field instead of ending in «…».
        avisoFolio.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        avisoFolio.prefWidthProperty().bind(fFolioFiscal.widthProperty());
        Runnable revisarFolio = () -> {
            String problema = com.sibim.service.CalidadDatosService.problemaFolio(fFolioFiscal.getText());
            avisoFolio.setText(problema == null ? "" : "Revisa el folio fiscal: " + problema);
            avisoFolio.setVisible(problema != null);
            avisoFolio.setManaged(problema != null);
        };
        fFolioFiscal.textProperty().addListener((o, a, b) -> revisarFolio.run());
        revisarFolio.run();

        // ── Assemble gridPatrimonio ───────────────────────────────────────────
        // In the order the factura is captured (12-nov-2026: since 28-oct, 411 edits,
        // most of them proveedor + folio fiscal + vida útil + color + tipo de bien, and
        // 175 with the costo too): the purchase first, with the costo; then the bien,
        // where it is, its maintenance and the vehicle's papers.
        int rp = 0;
        rp = seccion(grid, "Compra", rp, false);
        // «Copiar de PRES/12: …», beside the title (see ofrecerAnterior); next in the Tab order.
        lnkAnterior.getStyleClass().add("field-hint");
        lnkAnterior.setPadding(javafx.geometry.Insets.EMPTY);
        lnkAnterior.setWrapText(true);
        lnkAnterior.setVisible(false);
        lnkAnterior.setManaged(false);
        GridPane.setHalignment(lnkAnterior, javafx.geometry.HPos.RIGHT);
        grid.add(lnkAnterior, 1, rp - 1);
        grid.add(DialogUtil.fieldLabelWithHelp("N° Factura",
            "Número del documento de compra o factura.\nDistinto de la foto de factura — este es el folio para cruce contable."),
                                                         0, rp); grid.add(new javafx.scene.layout.VBox(2, fNumeroFactura, lnkFolioALugar), 1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Folio fiscal (UUID)",
            "El UUID del CFDI de la factura (36 caracteres, viene en su PDF o XML).\nUn UUID escrito en N° Factura se pasa aquí solo.\n"
                + "La O se escribe sola como 0, y la I o la L como 1.\n«Leer la factura (XML o PDF)…» lo llena desde el archivo."),
                                                         0, rp); grid.add(new javafx.scene.layout.VBox(2, fFolioFiscal, avisoFolio, lnkXml, lblXml), 1, rp++);
        lnkXml.getStyleClass().add("field-hint");
        lnkXml.setPadding(javafx.geometry.Insets.EMPTY);
        lnkXml.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2f-file-code-outline"));
        lblXml.getStyleClass().addAll("field-hint", "muted-sm");
        lblXml.setWrapText(true);
        lblXml.setMinHeight(Region.USE_PREF_SIZE);
        lblXml.prefWidthProperty().bind(fFolioFiscal.widthProperty());
        lblXml.setVisible(false);
        lblXml.setManaged(false);
        grid.add(DialogUtil.fieldLabel("Proveedor"),     0, rp); grid.add(fProveedor,    1, rp++);
        grid.add(DialogUtil.fieldLabelWithHelp("Fecha adquisición",
            "Fecha en que se adquirió el bien.\nBase para el cálculo de depreciación."),
                                                         0, rp); grid.add(fFechaAdq,      1, rp++);
        filaCosto = rp++;   // the costo, from the other tab (ponerCosto)
        grid.add(DialogUtil.fieldLabelWithHelp("Vida útil (años)",
            "Número de años en que el bien se deprecia completamente (depreciación en línea recta)\n(CONAC: equipo de cómputo 3 años, vehículos 5, mobiliario y maquinaria 10;\nse sugiere al elegir la categoría). Vacío: el bien no se deprecia."),
                                                         0, rp); grid.add(new javafx.scene.layout.VBox(2, fVidaUtil, lnkVidaComun), 1, rp++);
        lnkResidual.getStyleClass().add("field-hint");
        lnkResidual.setPadding(javafx.geometry.Insets.EMPTY);
        lnkResidual.setVisible(false);
        lnkResidual.setManaged(false);
        grid.add(DialogUtil.fieldLabelWithHelp("Valor residual",
            "Valor de rescate o residual al final de la vida útil (puede ser $0)."),
                                                         0, rp); grid.add(new javafx.scene.layout.VBox(2, fValorResidual, lnkResidual, lblResidualPropuesto), 1, rp++);
        lblResidualPropuesto.getStyleClass().addAll("field-hint", "muted-sm");
        lblResidualPropuesto.setWrapText(true);
        lblResidualPropuesto.setVisible(false);
        lblResidualPropuesto.setManaged(false);

        GridPane g = gridBien;
        rp = seccion(g, "El bien", 0, false);
        g.add(DialogUtil.fieldLabel("Color / Material"), 0, rp); g.add(new javafx.scene.layout.VBox(2, fColor, lnkColorDelNombre), 1, rp++);
        g.add(DialogUtil.fieldLabel("Tipo de bien"), 0, rp);
        g.add(new javafx.scene.layout.VBox(2, com.sibim.util.ListaParaElegir.junto(fTipoBien, btnTipos), lblTipoPropuesto), 1, rp++);
        g.add(DialogUtil.fieldLabel("Marca"),         0, rp); g.add(conNoTiene(fMarca, fSinMarca),          1, rp++);
        g.add(DialogUtil.fieldLabel("Modelo"),        0, rp); g.add(conNoTiene(fModelo, fSinModelo),        1, rp++);
        g.add(DialogUtil.fieldLabel("N° de Serie"),   0, rp); g.add(conNoTiene(fNumeroSerie, fSinSerie),    1, rp++);
        g.add(DialogUtil.fieldLabelWithHelp("Clave Armonizada",
            "Clave CONAC del bien: la de su categoría (Ej. 1.2.4.1.1.511).\nSe llena al elegir la categoría. Aparece en ANEXO V.4 e Inventario de Parque Vehicular."),
            0, rp); g.add(fClaveArm, 1, rp++);

        g = gridDonde;
        rp = seccion(g, "Dónde está", 0, false);
        g.add(DialogUtil.fieldLabel("Ubicación"),     0, rp); g.add(fUbicacion,    1, rp++);
        g.add(DialogUtil.fieldLabelWithHelp("Resguardante",
            "Persona física responsable del resguardo y custodia del bien.\nNormalmente el jefe de área o el usuario directo."),
                                                         0, rp); g.add(new javafx.scene.layout.VBox(2, fResguardante, avisoResguardante, lblResguardoActivo), 1, rp++);
        g.add(DialogUtil.fieldLabelWithHelp("Estado físico",
            "Condición actual del bien según el último levantamiento físico.\nBUENO = sin daños; REGULAR = desgaste menor; MALO = requiere reparación; DEFICIENTE = fuera de uso."),
                                                         0, rp); g.add(fEstadoFisico, 1, rp++);
        g.add(DialogUtil.fieldLabel("Etiquetado"), 0, rp);
        g.add(fEtiquetado, 1, rp++);

        g = gridMant;
        rp = seccion(g, "Mantenimiento", 0, false);
        g.add(DialogUtil.fieldLabelWithHelp("Próxima revisión",
            "Fecha programada para la próxima revisión o mantenimiento preventivo del bien."),
            0, rp); g.add(fProximaRevision, 1, rp++);
        g.add(DialogUtil.fieldLabel("Notas de mantenimiento"), 0, rp); g.add(fNotasMant, 1, rp++);

        g = gridVeh;
        rp = seccion(g, "Vehículos", 0, false);
        g.add(DialogUtil.fieldLabel("N° de Motor"), 0, rp); g.add(fNoMotor, 1, rp++);
        g.add(DialogUtil.fieldLabel("N° Tarjeta Circ."), 0, rp); g.add(fNoTarjeta, 1, rp++);
        g.add(DialogUtil.fieldLabel("N° Póliza Seguro"), 0, rp); g.add(fNoPoliza, 1, rp);

        vista = armarVista();
    }

    /** «El bien», «Dónde está», «Mantenimiento» and «Vehículos», each its own grid (the first,
     *  «Compra», is {@link #grid}). */
    final GridPane gridBien = DialogUtil.formGrid(140), gridDonde = DialogUtil.formGrid(140),
        gridMant = DialogUtil.formGrid(140), gridVeh = DialogUtil.formGrid(140);

    /** What the tab shows: on a wide screen «Compra» beside «El bien» and, under them, «Dónde
     *  está» beside «Mantenimiento» and «Vehículos» (17-nov-2026: at 580 px the tab showed six
     *  fields at a time, on a 2,560 px screen); on a narrow one, one under the other as before.
     *  Tab follows the order of the factura: Compra, El bien, Dónde está, Mantenimiento, Vehículos. */
    final javafx.scene.layout.Region vista;

    /** Two columns when the screen is wide enough for them at the chosen text size. */
    static boolean dosColumnas() {
        double ancho = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        int escala = com.sibim.util.AccessibilityUtils.getTextScaleIndex();
        return ancho >= 1280 + 220 * escala;
    }

    private javafx.scene.layout.Region armarVista() {
        if (!dosColumnas()) {
            javafx.scene.layout.VBox v = new javafx.scene.layout.VBox();
            for (GridPane gp : List.of(grid, gridBien, gridDonde, gridMant, gridVeh)) {
                if (!v.getChildren().isEmpty()) {
                    gp.setPadding(new javafx.geometry.Insets(6, 22, 20, 22));
                    javafx.scene.control.Separator s = new javafx.scene.control.Separator();
                    javafx.scene.layout.VBox.setMargin(s, new javafx.geometry.Insets(0, 22, 0, 22));
                    v.getChildren().add(s);
                }
                v.getChildren().add(gp);
            }
            return v;
        }
        GridPane c = new GridPane();
        c.getStyleClass().add("form-dos-columnas");
        for (int k = 0; k < 2; k++) {
            javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
            col.setPercentWidth(50);
            col.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            c.getColumnConstraints().add(col);
        }
        javafx.scene.layout.VBox derecha = new javafx.scene.layout.VBox(gridMant, gridVeh);
        gridVeh.setPadding(new javafx.geometry.Insets(6, 22, 20, 22));
        javafx.scene.control.Separator s = new javafx.scene.control.Separator();
        GridPane.setMargin(s, new javafx.geometry.Insets(0, 22, 0, 22));
        c.add(grid, 0, 0);
        c.add(gridBien, 1, 0);
        c.add(s, 0, 1, 2, 1);
        c.add(gridDonde, 0, 2);
        c.add(derecha, 1, 2);
        for (javafx.scene.Node n : List.of(grid, gridBien, gridDonde, derecha)) GridPane.setValignment(n, javafx.geometry.VPos.TOP);
        return c;
    }

    /** Under the folio fiscal: what is wrong with it, while it is typed. */
    final Label avisoFolio = new Label();

    /** Under the folio fiscal: «Leer la factura (XML o PDF)…», and what it filled. */
    final javafx.scene.control.Hyperlink lnkXml = new javafx.scene.control.Hyperlink("Leer la factura (XML o PDF)…");
    final Label lblXml = new Label();

    /** Where the last factura was picked from, to open there the next time. */
    private static java.io.File carpetaXml;

    /** «Leer la factura (XML o PDF)…»: picks the CFDI, or its PDF (19-nov-2026: Patrimonio has
     *  the facturas in PDF), and fills the purchase from it.
     *  @param costo the costo field (the other tab builds it)
     *  @param nombreBien the bien's name as typed, to pick its concepto among several */
    void ofrecerXml(TextField costo, java.util.function.Supplier<String> nombreBien) {
        lnkXml.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Factura (XML o PDF)");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Factura electrónica (XML o PDF)", "*.xml", "*.XML", "*.pdf", "*.PDF"),
                new javafx.stage.FileChooser.ExtensionFilter("Solo XML", "*.xml", "*.XML"),
                new javafx.stage.FileChooser.ExtensionFilter("Solo PDF", "*.pdf", "*.PDF"));
            if (carpetaXml != null && carpetaXml.isDirectory()) fc.setInitialDirectory(carpetaXml);
            java.io.File archivo = fc.showOpenDialog(grid.getScene() != null ? grid.getScene().getWindow() : null);
            if (archivo == null) return;
            carpetaXml = archivo.getParentFile();
            if (archivo.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
                mostrarXml("Leyendo el PDF…", false);
                DialogUtil.runAsync(() -> {
                        String[] receptor = com.sibim.service.FacturasDeCarpeta.receptor();
                        return com.sibim.util.FacturaPdf.leer(archivo.toPath(),
                            receptor[0].isBlank() ? List.of() : List.of(receptor[0]), List.of(receptor[1]));
                    },
                    f -> aplicarFactura(f, costo, nombreBien.get(), true, "PDF"),
                    ex -> mostrarXml(ex instanceof com.sibim.util.FacturaXml.NoEsFactura ? ex.getMessage()
                        : "No se pudo leer el archivo: " + com.sibim.util.MensajeError.de(ex), true));
                return;
            }
            try {
                aplicarFactura(com.sibim.util.FacturaXml.leer(archivo.toPath()), costo, nombreBien.get(), true);
            } catch (com.sibim.util.FacturaXml.NoEsFactura ex) {
                mostrarXml(ex.getMessage(), true);
            } catch (Exception ex) {
                mostrarXml("No se pudo leer el archivo: " + com.sibim.util.MensajeError.de(ex), true);
            }
        });
    }

    /** Fills from the factura what this bien lacks (a folio fiscal with another shape is replaced:
     *  the XML's is the right one) and says under the link what it put and what it left.
     *  @param preguntar whether to ask which concepto when the name doesn't tell (not in tests) */
    void aplicarFactura(com.sibim.util.FacturaXml.Factura f, TextField costo, String nombreBien, boolean preguntar) {
        aplicarFactura(f, costo, nombreBien, preguntar, "XML");
    }

    /** @param de «XML» or «PDF»: what the note under the link says it was read from */
    void aplicarFactura(com.sibim.util.FacturaXml.Factura f, TextField costo, String nombreBien, boolean preguntar, String de) {
        List<String> puso = new java.util.ArrayList<>(), dejo = new java.util.ArrayList<>();
        String folio = fFolioFiscal.getText() == null ? "" : fFolioFiscal.getText().trim();
        if (folio.isEmpty() || com.sibim.service.CalidadDatosService.problemaFolio(folio) != null) {
            fFolioFiscal.setText(f.uuid());
            puso.add("folio fiscal");
        } else if (!folio.equalsIgnoreCase(f.uuid())) {
            dejo.add("el folio fiscal escrito no es el del " + de + " (" + f.uuid() + ")");
        }
        if (f.numero() != null) llenar(fNumeroFactura, f.numero(), "factura " + f.numero(), "la factura", puso, dejo);
        if (f.fecha() != null) {
            if (fFechaAdq.getValue() == null) { fFechaAdq.setValue(f.fecha()); puso.add("fecha " + FormatUtils.formatDate(f.fecha())); }
            else if (!fFechaAdq.getValue().equals(f.fecha())) dejo.add("la fecha (la factura es del " + FormatUtils.formatDate(f.fecha()) + ")");
        }
        if (f.emisor() != null) {
            String prov = f.emisor().toUpperCase(java.util.Locale.forLanguageTag("es-MX"));
            llenar(fProveedor, prov, "proveedor", "el proveedor (la factura dice " + prov + ")", puso, dejo);
        }
        if (costo != null) {
            if (!f.enPesos()) {
                dejo.add("el costo: la factura está en " + f.moneda());
            } else {
                com.sibim.util.FacturaXml.Concepto c = conceptoDelBien(f.conceptos(), nombreBien);
                if (c == null && f.conceptos().size() > 1 && preguntar) c = elegirConcepto(f.conceptos());
                java.math.BigDecimal unitario = c == null ? null : c.costoPorPieza();
                if (unitario != null) {
                    java.math.BigDecimal escrito = null;
                    try { escrito = new java.math.BigDecimal(costo.getText().trim()); } catch (NumberFormatException ignored) { /* empty */ }
                    if (escrito == null || escrito.signum() == 0) {
                        costo.setText(unitario.toPlainString());
                        puso.add("costo " + FormatUtils.formatCurrency(unitario) + " con IVA");
                    } else if (escrito.compareTo(unitario) != 0) {
                        dejo.add("el costo (en la factura, " + FormatUtils.formatCurrency(unitario) + " por pieza con IVA)");
                    }
                }
            }
        }
        String texto = (puso.isEmpty() ? "Del " + de + " no hizo falta nada" : "Del " + de + ": " + enLista(puso))
            + (dejo.isEmpty() ? "." : ". Se dejó como estaba " + enLista(dejo) + ".");
        // A PDF doesn't give its conceptos: the costo is typed from it, with its total as a reference.
        if (f.conceptos().isEmpty() && costo != null && f.enPesos()) {
            java.math.BigDecimal escrito = null;
            try { escrito = new java.math.BigDecimal(costo.getText().trim()); } catch (NumberFormatException ignored) { /* empty */ }
            if (escrito == null || escrito.signum() == 0)
                texto += " El costo por pieza va a mano" + (f.total() != null ? " (el total de la factura es " + FormatUtils.formatCurrency(f.total()) + ")" : "") + ".";
        }
        mostrarXml(texto, false);
    }

    /** The concepto that is this bien: the only one, or the one whose description is clearly its name. */
    static com.sibim.util.FacturaXml.Concepto conceptoDelBien(List<com.sibim.util.FacturaXml.Concepto> conceptos, String nombre) {
        if (conceptos.size() == 1) return conceptos.get(0);
        if (nombre == null || nombre.isBlank()) return null;
        String n = com.sibim.service.CalidadDatosService.claveTexto(nombre);
        com.sibim.util.FacturaXml.Concepto mejor = null;
        double max = 0, segundo = 0;
        for (var c : conceptos) {
            double s = ProductoDialogFactory.diceSimilarity(n, com.sibim.service.CalidadDatosService.claveTexto(c.descripcion()));
            if (s > max) { segundo = max; max = s; mejor = c; } else if (s > segundo) segundo = s;
        }
        return max >= 0.5 && max - segundo >= 0.15 ? mejor : null;
    }

    private static com.sibim.util.FacturaXml.Concepto elegirConcepto(List<com.sibim.util.FacturaXml.Concepto> conceptos) {
        java.util.Map<String, com.sibim.util.FacturaXml.Concepto> porTexto = new java.util.LinkedHashMap<>();
        for (var c : conceptos) {
            java.math.BigDecimal u = c.costoPorPieza();
            String d = Objects.requireNonNullElse(c.descripcion(), "(sin descripción)");
            porTexto.put((d.length() > 70 ? d.substring(0, 70) + "…" : d) + " — "
                + (u == null ? "sin costo" : FormatUtils.formatCurrency(u) + " c/u con IVA"), c);
        }
        String sinCosto = "No poner costo";
        List<String> opciones = new java.util.ArrayList<>(porTexto.keySet());
        opciones.add(sinCosto);
        javafx.scene.control.ChoiceDialog<String> d = new javafx.scene.control.ChoiceDialog<>(opciones.get(0), opciones);
        d.setTitle("¿Cuál es este bien?");
        d.setHeaderText("La factura trae " + conceptos.size() + " conceptos: elige el de este bien para tomar su costo");
        d.setContentText(null);
        DialogUtil.applyOwner(d);
        DialogUtil.applyStylesheet(d.getDialogPane());
        return d.showAndWait().filter(s -> !s.equals(sinCosto)).map(porTexto::get).orElse(null);
    }

    private static void llenar(TextField campo, String valor, String dicePuso, String diceDejo, List<String> puso, List<String> dejo) {
        String actual = campo.getText() == null ? "" : campo.getText().trim();
        if (actual.isEmpty()) { campo.setText(valor); puso.add(dicePuso); }
        else if (!com.sibim.service.CalidadDatosService.claveTexto(actual).equals(com.sibim.service.CalidadDatosService.claveTexto(valor)))
            dejo.add(diceDejo);
    }

    private void mostrarXml(String texto, boolean error) {
        lblXml.setText(texto);
        lblXml.getStyleClass().remove("field-hint-warn");
        if (error) lblXml.getStyleClass().add("field-hint-warn");
        lblXml.setVisible(true);
        lblXml.setManaged(true);
    }

    /** Beside «Compra»: «Copiar de PRES/12: factura, folio…», then «Quitar lo copiado». */
    final javafx.scene.control.Hyperlink lnkAnterior = new javafx.scene.control.Hyperlink();

    /** What «Copiar» wrote, to put back: each field and what it had. */
    private final List<Runnable> deshacerCopia = new java.util.ArrayList<>();

    /** One dato «Copiar del anterior» fills: its name, whether this bien lacks it, whether the
     *  other has it, and how it is copied (returning how to put it back). */
    private record Copiable(String nombre, java.util.function.BooleanSupplier falta,
                            java.util.function.Predicate<Producto> tiene,
                            java.util.function.Function<Producto, Runnable> copiar) {}

    private List<Copiable> copiables() {
        return List.of(
            texto("factura", fNumeroFactura, Producto::getNumeroFactura),
            texto("folio fiscal", fFolioFiscal, Producto::getFolioFiscal),
            texto("proveedor", fProveedor, Producto::getProveedor),
            new Copiable("fecha", () -> fFechaAdq.getValue() == null, a -> a.getFechaAdquisicion() != null, a -> {
                fFechaAdq.setValue(a.getFechaAdquisicion());
                return () -> fFechaAdq.setValue(null);
            }),
            texto("color", fColor, Producto::getColor),
            // The tipo the CONAC account proposed counts as empty: the bien before says it better.
            new Copiable("tipo", () -> {
                    String t = fTipoBien.getText() == null ? "" : fTipoBien.getText().trim();
                    return t.isEmpty() || t.equals(tipoPropuesto);
                }, a -> a.getTipoBien() != null && !a.getTipoBien().isBlank()
                    && !a.getTipoBien().trim().equals(fTipoBien.getText() == null ? "" : fTipoBien.getText().trim()),
                a -> {
                    String antes = fTipoBien.getText();
                    fTipoBien.setText(a.getTipoBien().trim());
                    return () -> fTipoBien.setText(antes);
                }),
            new Copiable("vida útil", () -> vidaUtil() == null, a -> a.getVidaUtilAnios() != null, a -> {
                fVidaUtil.getValueFactory().setValue(a.getVidaUtilAnios());
                return () -> { fVidaUtil.getEditor().setText(""); fVidaUtil.getValueFactory().setValue(null); };
            }));
    }

    private static Copiable texto(String nombre, TextField campo, java.util.function.Function<Producto, String> de) {
        return new Copiable(nombre, () -> campo.getText() == null || campo.getText().isBlank(),
            a -> de.apply(a) != null && !de.apply(a).isBlank(), a -> {
                campo.setText(de.apply(a).trim());
                return () -> campo.setText("");
            });
    }

    /** «Copiar de PRES/12: …» beside «Compra», when the bien saved just before has purchase data
     *  this one lacks; it fills only what is empty, and says from which bien. Ctrl+D does it too.
     *  @param idEste this bien's id (the one just saved is not offered to itself) */
    void ofrecerAnterior(Producto anterior, String idEste) {
        Runnable explicar = () -> {
            if (!deshacerCopia.isEmpty()) return;
            List<String> que = anterior == null || Objects.equals(anterior.getId(), idEste) ? List.of()
                : copiables().stream().filter(c -> c.falta().getAsBoolean() && c.tiene().test(anterior)).map(Copiable::nombre).toList();
            boolean ver = !que.isEmpty();
            lnkAnterior.setVisible(ver);
            lnkAnterior.setManaged(ver);
            if (ver) {
                lnkAnterior.setText("Copiar de " + Objects.requireNonNullElse(anterior.getCodigo(), "el anterior") + ": "
                    + enLista(que) + "  (Ctrl+D)");
                lnkAnterior.setTooltip(new javafx.scene.control.Tooltip("Del bien que guardaste antes: "
                    + Objects.requireNonNullElse(anterior.getNombre(), "") + ". Solo llena lo que está vacío."));
            }
        };
        lnkAnterior.setOnAction(e -> {
            if (!deshacerCopia.isEmpty()) {
                // «Quitar lo copiado»: the fields as they were.
                for (Runnable r : deshacerCopia) r.run();
                deshacerCopia.clear();
                explicar.run();
                return;
            }
            if (anterior == null) return;
            for (Copiable c : copiables())
                if (c.falta().getAsBoolean() && c.tiene().test(anterior)) deshacerCopia.add(c.copiar().apply(anterior));
            if (deshacerCopia.isEmpty()) { explicar.run(); return; }
            lnkAnterior.setText("Quitar lo copiado de " + Objects.requireNonNullElse(anterior.getCodigo(), "el anterior")
                + " (" + com.sibim.util.FormatUtils.plural(deshacerCopia.size(), "dato", "datos") + ")");
            lnkAnterior.setTooltip(new javafx.scene.control.Tooltip("Deja vacío otra vez lo que se copió"));
        });
        explicar.run();
    }

    /** «factura, folio fiscal y proveedor». */
    static String enLista(List<String> cosas) {
        if (cosas.size() <= 1) return String.join("", cosas);
        return String.join(", ", cosas.subList(0, cosas.size() - 1)) + " y " + cosas.get(cosas.size() - 1);
    }

    /** Ctrl+D in the form: copy (or put back) what «Copiar de …» says. */
    void copiarAnterior() {
        if (lnkAnterior.isVisible() && !lnkAnterior.isDisabled()) lnkAnterior.fire();
    }

    /** Under the tipo de bien, while it says what the CONAC account proposed. */
    final Label lblTipoPropuesto = new Label("Propuesto por su clave CONAC; escribe otro si no es");

    /** What the form wrote as tipo by itself (null when nothing): replaced if the account
     *  changes, never once someone wrote another. */
    private String tipoPropuesto;

    /** An empty tipo — or the one this form proposed — takes what the CONAC account says
     *  («MUEBLE», nothing for a vehicle). */
    private void proponerTipo() {
        String sugerido = com.sibim.service.CalidadDatosService.tipoSugerido(fClaveArm.getText());
        String actual = fTipoBien.getText() == null ? "" : fTipoBien.getText().trim();
        if (actual.isEmpty() || actual.equals(tipoPropuesto)) {
            String nuevo = sugerido == null ? "" : sugerido;
            tipoPropuesto = sugerido;
            if (!nuevo.equals(actual)) fTipoBien.setText(nuevo);
        }
        verTipoPropuesto();
    }

    /** Under the tipo: what the CONAC account proposed, or that a color was written there
     *  (18-nov-2026: «NEGRO» as tipo in 6 sillas, «NEGRO ERGONÓMICO» in one). */
    private void verTipoPropuesto() {
        String t = fTipoBien.getText() == null ? "" : fTipoBien.getText().trim();
        boolean color = com.sibim.util.ColorEnNombre.esColor(t);
        boolean ver = color || (tipoPropuesto != null && t.equals(tipoPropuesto));
        lblTipoPropuesto.setText(color ? "Parece un color: el color va en «Color / Material»"
            : "Propuesto por su clave CONAC; escribe otro si no es");
        lblTipoPropuesto.getStyleClass().removeAll("field-hint-warn", "muted-sm");
        lblTipoPropuesto.getStyleClass().add(color ? "field-hint-warn" : "muted-sm");
        lblTipoPropuesto.setVisible(ver);
        lblTipoPropuesto.setManaged(ver);
    }

    /** The button beside the tipo de bien with the tipos to pick from. */
    final javafx.scene.control.MenuButton btnTipos;

    /** The tipos the list offers (empty until {@link #sugerirColorYTipo}). */
    private List<String> tiposEnLista = List.of();

    /** The colors and tipos already written, offered while typing, as the marca and the
     *  proveedor are (13-nov-2026: «NEBRO», «MELEMINA CAFE», «MUELE» were typed by hand); the
     *  tipos also in a list beside the field, each one once ({@link com.sibim.service.CalidadDatosService#tiposParaElegir}). */
    void sugerirColorYTipo(List<String> colores, List<String> tipos) {
        AutocompleteUtil.attach(fColor, colores);
        AutocompleteUtil.attach(fTipoBien, tipos);
        tiposEnLista = tipos == null ? List.of() : tipos;
        com.sibim.util.ListaParaElegir.opciones(btnTipos, fTipoBien, tiposEnLista);
    }

    /** A tipo typed with other capitals or accents than one of the list is written as the list
     *  writes it, on leaving the field («mueble» → «MUEBLE», «camion» → «Camión»). */
    void escribirTipoComoLaLista() {
        String t = fTipoBien.getText() == null ? "" : fTipoBien.getText().trim();
        String enLista = com.sibim.service.CalidadDatosService.comoEnLaLista(t, tiposEnLista);
        if (enLista != null && !enLista.equals(t) && fTipoBien.isEditable() && !fTipoBien.isDisabled()) fTipoBien.setText(enLista);
    }

    /** Under the valor residual: «Poner el 15 % del costo: $X» (see {@link #ofrecerResidual}). */
    final javafx.scene.control.Hyperlink lnkResidual = new javafx.scene.control.Hyperlink();

    /** Under the valor residual when SIBIM wrote it (see {@link #proponerResidual}). */
    final Label lblResidualPropuesto = new Label("El " + com.sibim.util.ProductoUtils.PORCENTAJE_RESIDUAL.toPlainString()
        + " % del costo, como casi todos los bienes; escribe otro si no es");

    /** What SIBIM wrote in the valor residual, or null. */
    private java.math.BigDecimal residualPropuesto;

    private static java.math.BigDecimal numero(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try { return new java.math.BigDecimal(texto.trim().replace(",", "")); }
        catch (NumberFormatException e) { return null; }
    }

    /** The 15 % of the costo typed in {@code costo}, written in the valor residual while it is
     *  empty, $0, what SIBIM wrote or the 15 % of the costo it had (19-nov-2026: 285 of the 296
     *  valores residuales are exactly that, and 180 of 186 in a week were worked out bien by bien;
     *  3 stayed at 12.9 % after «Sumarle el IVA»). A different one is left as it is, with
     *  «Poner el 15 % del costo» one click away. Nothing when the field can't be changed. */
    void ofrecerResidual(TextField costo) {
        Runnable explicar = () -> {
            java.math.BigDecimal sugerido = com.sibim.util.ProductoUtils.residualSugerido(numero(costo.getText()));
            java.math.BigDecimal escrito = numero(fValorResidual.getText());
            boolean ver = sugerido != null && !fValorResidual.isDisabled()
                && !com.sibim.util.ProductoUtils.esResidualSugerido(numero(costo.getText()), escrito);
            lnkResidual.setVisible(ver);
            lnkResidual.setManaged(ver);
            if (ver) {
                lnkResidual.setText("Poner el " + com.sibim.util.ProductoUtils.PORCENTAJE_RESIDUAL.toPlainString()
                    + " % del costo: " + com.sibim.util.FormatUtils.formatCurrency(sugerido));
                lnkResidual.setUserData(sugerido);
            }
            boolean propuesto = residualPropuesto != null && escrito != null && escrito.compareTo(residualPropuesto) == 0;
            lblResidualPropuesto.setVisible(propuesto);
            lblResidualPropuesto.setManaged(propuesto);
        };
        lnkResidual.setOnAction(e -> {
            if (!(lnkResidual.getUserData() instanceof java.math.BigDecimal sugerido)) return;
            fValorResidual.setText(sugerido.toPlainString());
            fValorResidual.positionCaret(fValorResidual.getText().length());
        });
        costo.textProperty().addListener((o, a, b) -> { proponerResidual(a, b); explicar.run(); });
        fValorResidual.textProperty().addListener((o, a, b) -> explicar.run());
        fValorResidual.disabledProperty().addListener((o, a, b) -> explicar.run());
        proponerResidual(costo.getText(), costo.getText());
        explicar.run();
    }

    /** See {@link #ofrecerResidual}: {@code costoAntes} is what the costo said before this change. */
    private void proponerResidual(String costoAntes, String costoAhora) {
        if (fValorResidual.isDisabled() || !fValorResidual.isEditable()
                || !com.sibim.session.Permisos.editaFichaCompleta()) return;
        java.math.BigDecimal sugerido = com.sibim.util.ProductoUtils.residualSugerido(numero(costoAhora));
        java.math.BigDecimal escrito = numero(fValorResidual.getText());
        boolean vacio = escrito == null || escrito.signum() == 0;
        boolean loPuso = residualPropuesto != null && escrito != null && escrito.compareTo(residualPropuesto) == 0;
        boolean eraDelCosto = com.sibim.util.ProductoUtils.esResidualSugerido(numero(costoAntes), escrito);
        if (!vacio && !loPuso && !eraDelCosto) return;
        // The 15 % give or take some centavos, with the costo as it was: left as it is.
        java.math.BigDecimal antes = numero(costoAntes), ahora = numero(costoAhora);
        boolean mismoCosto = antes == null ? ahora == null : ahora != null && antes.compareTo(ahora) == 0;
        if (!vacio && !loPuso && mismoCosto) return;
        if (sugerido != null) {
            // Only what SIBIM writes gets the note: a bien that already had its 15 % says nothing.
            if (escrito == null || escrito.compareTo(sugerido) != 0) {
                residualPropuesto = sugerido;
                fValorResidual.setText(sugerido.toPlainString());
            }
        } else if (loPuso) {
            // The costo was erased (to type it again): what SIBIM wrote goes with it.
            residualPropuesto = null;
            fValorResidual.setText("0");
        }
    }

    /** The row the costo goes in, inside «Compra». */
    private int filaCosto;

    /** A section title, as the tab already had them (a line, then its name). */
    private static int seccion(GridPane g, String titulo, int rp, boolean conLinea) {
        if (conLinea) { g.add(new javafx.scene.control.Separator(), 0, rp, 2, 1); rp++; }
        Label l = new Label(titulo);
        l.getStyleClass().add("dialog-field-label");
        g.add(l, 0, rp, 2, 1);
        return rp + 1;
    }

    /** Puts the costo (its label and its box, built by the other tab with its IVA and lot
     *  hints) in «Compra», between the fecha and the vida útil. */
    void ponerCosto(javafx.scene.Node etiqueta, javafx.scene.Node caja) {
        GridPane.setConstraints(etiqueta, 0, filaCosto);
        GridPane.setConstraints(caja, 1, filaCosto);
        // Right after the fecha among the children too: Tab follows the children's order,
        // not the rows, and added at the end the costo came after the póliza.
        int i = grid.getChildren().indexOf(fFechaAdq) + 1;
        grid.getChildren().addAll(i, List.of(etiqueta, caja));
    }

    /** Attaches dirty-tracking listeners on all editable fields. Call AFTER
     *  all initial {@code setValue()} calls so pre-filled values on edit don't
     *  immediately mark the form dirty. */
    void wireDirty(Runnable markDirty) {
        fProveedor.textProperty().addListener((o, a, b)        -> markDirty.run());
        fMarca.textProperty().addListener((o, a, b)            -> markDirty.run());
        fModelo.textProperty().addListener((o, a, b)           -> markDirty.run());
        fNumeroSerie.textProperty().addListener((o, a, b)      -> markDirty.run());
        for (CheckBox c : List.of(fSinMarca, fSinModelo, fSinSerie))
            c.selectedProperty().addListener((o, a, b)         -> markDirty.run());
        fUbicacion.textProperty().addListener((o, a, b)        -> markDirty.run());
        fResguardante.textProperty().addListener((o, a, b)     -> markDirty.run());
        fEstadoFisico.valueProperty().addListener((o, a, b)    -> markDirty.run());
        fNumeroFactura.textProperty().addListener((o, a, b)    -> markDirty.run());
        fFolioFiscal.textProperty().addListener((o, a, b)      -> markDirty.run());
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

    /** What to save as marca, modelo and serie: empty ("No tiene", or "SIN MARCA" typed) is null. */
    String marca()  { return fSinMarca.isSelected()  ? null : ProductoUtils.dato(fMarca.getText()); }
    String modelo() { return fSinModelo.isSelected() ? null : ProductoUtils.dato(fModelo.getText()); }
    String serie()  { return fSinSerie.isSelected()  ? null : ProductoUtils.dato(fNumeroSerie.getText()); }

    /** "SIN MARCA" saved before shows as the box ticked; ticking it empties the field, and
     *  typing "SIN MARCA" (or "S/N"…) ticks it when the field is left. */
    private static void noTiene(TextField campo, CheckBox caja) {
        caja.getStyleClass().add("form-input");
        caja.setMinWidth(Region.USE_PREF_SIZE);
        caja.setFocusTraversable(false);
        if (ProductoUtils.esSinDato(campo.getText())) {
            campo.setText("");
            caja.setSelected(true);
            campo.setDisable(true);
        }
        caja.selectedProperty().addListener((o, antes, si) -> {
            if (si) campo.setText("");
            campo.setDisable(si);
        });
        campo.focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora && ProductoUtils.esSinDato(campo.getText())) caja.setSelected(true);
        });
    }

    private static HBox conNoTiene(TextField campo, CheckBox caja) {
        HBox fila = new HBox(8, campo, caja);
        fila.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(campo, Priority.ALWAYS);
        campo.setMaxWidth(Double.MAX_VALUE);
        return fila;
    }

    /** A UUID typed as número de factura (51 vehicles on 28/29-oct-2026, losing the
     *  folio they had) goes to Folio fiscal, if that one is empty, and the número the
     *  bien had comes back. Only one typed now: the 700-odd UUIDs the carga put there
     *  stay until someone moves them, or every edit would rewrite the factura. */
    /** Under an empty color, the one the bien's name or description says, one click away
     *  (6 to 18-nov-2026: of 729 colors typed by hand, 136 were exactly the name's and 77 the
     *  name's with more detail). */
    final javafx.scene.control.Hyperlink lnkColorDelNombre = new javafx.scene.control.Hyperlink();
    { lnkColorDelNombre.setVisible(false); lnkColorDelNombre.setManaged(false); }
    private javafx.beans.value.ObservableStringValue nombreDelBien, descripcionDelBien;

    void ofrecerColorDelNombre(javafx.beans.value.ObservableStringValue nombre, javafx.beans.value.ObservableStringValue descripcion) {
        nombreDelBien = nombre;
        descripcionDelBien = descripcion;
        lnkColorDelNombre.getStyleClass().add("field-hint");
        lnkColorDelNombre.setPadding(javafx.geometry.Insets.EMPTY);
        lnkColorDelNombre.setOnAction(e -> {
            String c = colorDelNombre();
            if (c != null) fColor.setText(c);
        });
        nombre.addListener((o, a, b) -> verColorDelNombre());
        if (descripcion != null) descripcion.addListener((o, a, b) -> verColorDelNombre());
        fColor.textProperty().addListener((o, a, b) -> verColorDelNombre());
        fColor.disabledProperty().addListener((o, a, b) -> verColorDelNombre());
        verColorDelNombre();
    }

    private String colorDelNombre() {
        if (nombreDelBien == null) return null;
        return com.sibim.util.ColorEnNombre.de(nombreDelBien.get(), descripcionDelBien == null ? null : descripcionDelBien.get());
    }

    private void verColorDelNombre() {
        String c = colorDelNombre();
        boolean ver = c != null && (fColor.getText() == null || fColor.getText().isBlank()) && !fColor.isDisabled() && fColor.isEditable();
        if (ver) lnkColorDelNombre.setText("Su nombre dice: " + c);
        lnkColorDelNombre.setVisible(ver);
        lnkColorDelNombre.setManaged(ver);
    }

    /** Under the vida útil while it is empty: what most bienes of its CONAC account have, one
     *  click away (not filled by itself: it moves the depreciation). See
     *  {@link com.sibim.service.CalidadDatosService#vidaUtilDeSuClave}. */
    final javafx.scene.control.Hyperlink lnkVidaComun = new javafx.scene.control.Hyperlink();
    private java.util.Map<String, Integer> vidaComun = java.util.Map.of();

    void ofrecerVidaComun(java.util.Map<String, Integer> comun) {
        vidaComun = comun == null ? java.util.Map.of() : comun;
        if (lnkVidaComun.getProperties().putIfAbsent("instalado", Boolean.TRUE) == null) {
            lnkVidaComun.getStyleClass().add("field-hint");
            lnkVidaComun.setPadding(javafx.geometry.Insets.EMPTY);
            lnkVidaComun.setOnAction(e -> {
                Integer anios = vidaDeSuClave();
                if (anios != null) fVidaUtil.getValueFactory().setValue(anios);
                verVidaComun();
            });
            fClaveArm.textProperty().addListener((o, a, b) -> verVidaComun());
            fVidaUtil.valueProperty().addListener((o, a, b) -> verVidaComun());
            fVidaUtil.getEditor().textProperty().addListener((o, a, b) -> verVidaComun());
            fVidaUtil.disabledProperty().addListener((o, a, b) -> verVidaComun());
        }
        verVidaComun();
    }

    /** Null without a CONAC account (Map.of() refuses a null key). */
    private Integer vidaDeSuClave() {
        String k = com.sibim.service.CalidadDatosService.claveCorta(fClaveArm.getText());
        return k == null ? null : vidaComun.get(k);
    }

    private void verVidaComun() {
        Integer anios = vidaDeSuClave();
        String escrito = fVidaUtil.getEditor().getText();
        boolean vacia = fVidaUtil.getValue() == null && (escrito == null || escrito.isBlank());
        boolean ver = anios != null && vacia && !fVidaUtil.isDisabled();
        if (ver) lnkVidaComun.setText("Como la mayoría de su clave CONAC: " + FormatUtils.plural(anios, "año", "años"));
        lnkVidaComun.setVisible(ver);
        lnkVidaComun.setManaged(ver);
    }

    /** Under N° Factura, while it holds a whole folio fiscal and «Folio fiscal» is empty: one
     *  click puts it there (6 to 18-nov-2026, 246 were moved by hand, bien by bien, as part of
     *  editing each one; 442 still were in «Factura»). */
    final javafx.scene.control.Hyperlink lnkFolioALugar = new javafx.scene.control.Hyperlink("Es el folio fiscal: pasarlo a «Folio fiscal»");

    private void verFolioALugar() {
        boolean ver = Producto.pareceFolioFiscal(fNumeroFactura.getText()) && fFolioFiscal.getText().isBlank()
            && !fNumeroFactura.isDisabled() && fNumeroFactura.isEditable() && !fFolioFiscal.isDisabled();
        lnkFolioALugar.setVisible(ver);
        lnkFolioALugar.setManaged(ver);
    }

    /** What the link does: the folio to its field, «Factura» empty for its own number. */
    void ponerFolioEnSuLugar() {
        String uuid = fNumeroFactura.getText().trim();
        if (!Producto.pareceFolioFiscal(uuid) || !fFolioFiscal.getText().isBlank()) return;
        fFolioFiscal.setText(uuid.toUpperCase());
        fNumeroFactura.setText("");
        fNumeroFactura.requestFocus();
    }

    void pasarFolioFiscal() {
        String texto = fNumeroFactura.getText().trim();
        if (texto.equals(facturaOriginal == null ? "" : facturaOriginal.trim())) return;
        if (!Producto.pareceFolioFiscal(texto) || !fFolioFiscal.getText().isBlank()) return;
        fFolioFiscal.setText(texto.toUpperCase());
        fNumeroFactura.setText(facturaOriginal != null && !Producto.pareceFolioFiscal(facturaOriginal)
            ? facturaOriginal : "");
    }

    /** What the vida útil field says now, text being typed included; emptied = none
     *  (the common spinner commit ignores an empty field). */
    Integer vidaUtil() {
        String texto = fVidaUtil.getEditor().getText();
        if (texto == null || texto.isBlank()) fVidaUtil.getValueFactory().setValue(null);
        else DialogUtil.commitSpinner(fVidaUtil);
        return fVidaUtil.getValue();
    }

    /** Years of vida útil, or none: a bien without one stays without one (it is not
     *  depreciated) until someone types it. The spinner used to start at 5 and every
     *  edit saved it — 107 bienes got 5 years that way by 28-oct-2026, among them a
     *  laptop (CONAC: 3) and a frigobar (10). From empty, the arrows start at 5. */
    static final class VidaUtilFactory extends SpinnerValueFactory<Integer> {
        static final int MIN = 1, MAX = 100, AL_EMPEZAR = 5;

        VidaUtilFactory(Integer inicial) {
            setConverter(new javafx.util.converter.IntegerStringConverter());
            valueProperty().addListener((o, antes, v) -> {
                if (v != null && v < MIN) setValue(MIN);
                else if (v != null && v > MAX) setValue(MAX);
            });
            setValue(inicial);
        }

        @Override public void decrement(int pasos) {
            setValue(getValue() == null ? AL_EMPEZAR : Math.max(MIN, getValue() - pasos));
        }

        @Override public void increment(int pasos) {
            setValue(getValue() == null ? AL_EMPEZAR : Math.min(MAX, getValue() + pasos));
        }
    }
}
