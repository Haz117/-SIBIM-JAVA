package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.EditarVarios;
import com.sibim.service.EditarVarios.Cambio;
import com.sibim.service.EditarVarios.Dato;
import com.sibim.service.EditarVarios.Renglon;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.AutocompleteUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.ProductoUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bienes › select several › «Cambiar datos» (see {@link EditarVarios}): the fields of the
 *  form's «Compra», «El bien» and «Dónde está»; only what is filled in changes. Next to each
 *  field, what the selected bienes have now. Reviewed bien by bien before saving; undone
 *  from the notice. */
public final class EditarVariosDialog {

    private static final Logger log = LoggerFactory.getLogger(EditarVariosDialog.class);

    private final List<Producto> bienes;
    private final List<Producto> todos;
    private final GridPane grid = new GridPane();
    private int fila;

    private final TextField tfFactura = campo("Ej. A 667"), tfFolio = campo("36 caracteres, del PDF o XML de la factura"),
        tfProveedor = campo("Ej. RAUL FICTICIO LOPEZ"), tfCosto = campo("Costo de cada uno, ej. 1500.00"),
        tfVida = campo("Años, ej. 10"), tfResidual = campo("Ej. 225.00"), tfColor = campo("Ej. NEGRO"),
        tfTipo = campo("Ej. MUEBLE"), tfMarca = campo(""), tfModelo = campo(""), tfUbicacion = campo("Ej. OFICINA DE TESORERÍA");
    private final DatePicker dpFecha = new DatePicker();
    private final CheckBox cbIva = new CheckBox("Sumarle el IVA (16 %) al costo de cada uno");
    private final CheckBox cbResidual = new CheckBox("El " + ProductoUtils.PORCENTAJE_RESIDUAL.toPlainString() + " % del costo de cada uno");
    private final Label avisoFolio = new Label();
    private final Label lblError = new Label();

    private EditarVariosDialog(List<Producto> bienes, List<Producto> todos) {
        this.bienes = List.copyOf(bienes);
        this.todos = todos;
    }

    /** @param todos the inventory, for the suggestions (proveedores, colores, ubicaciones…) */
    public static void show(Scene scene, ProductoService servicio, List<Producto> bienes, List<Producto> todos,
                            Runnable alCambiar) {
        if (bienes == null || bienes.size() < 2) return;
        new EditarVariosDialog(bienes, todos != null ? todos : bienes).abrir(scene, servicio, alCambiar);
    }

    private void abrir(Scene scene, ProductoService servicio, Runnable alCambiar) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        String titulo = "Cambiar datos de " + FormatUtils.plural(bienes.size(), "bien", "bienes");
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(860, pantalla.getWidth() * 0.94), Math.min(720, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2p-pencil-box-multiple-outline", titulo,
            "Solo cambia lo que llenes: lo que dejes vacío, cada bien lo conserva", AppColors.PRIMARY, AppColors.PRIMARY_D);

        armarCampos();
        Label ayuda = new Label("A la derecha de cada dato, lo que tienen ahora los " + bienes.size()
            + " bienes. Antes de guardar verás bien por bien qué cambia; después se puede deshacer.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        lblError.getStyleClass().add("field-hint-error");
        lblError.setWrapText(true);
        lblError.setMinHeight(Region.USE_PREF_SIZE);
        Button revisar = new Button("Revisar los cambios");
        revisar.setGraphic(new FontIcon("mdi2c-check"));
        revisar.getStyleClass().add("btn-primary");
        revisar.setMinWidth(Region.USE_PREF_SIZE);
        revisar.setDefaultButton(true);
        revisar.setOnAction(e -> revisar(dialog, scene, servicio, alCambiar));
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        // One row of buttons, as in the bien's form: «Cerrar» lived alone in the dialog's
        // own bar under this one and read as a second footer (18-nov-2026).
        Button cerrar = new Button("Cerrar");
        cerrar.getStyleClass().add("btn-secondary");
        cerrar.setMinWidth(Region.USE_PREF_SIZE);
        cerrar.setCancelButton(true);
        cerrar.setOnAction(e -> dialog.close());
        HBox abajo = new HBox(10, lblError, hueco, cerrar, revisar);
        abajo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(lblError, Priority.SOMETIMES);
        // The dialog keeps its Cerrar (Esc and the window's X) but not the bar's height.
        if (dialog.getDialogPane().lookup(".button-bar") instanceof Region barra) {
            barra.setVisible(false);
            barra.setMinHeight(0);
            barra.setPrefHeight(0);
            barra.setMaxHeight(0);
            barra.setPadding(Insets.EMPTY);
        }

        VBox cuerpo = new VBox(10, ayuda, scroll, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        javafx.application.Platform.runLater(tfFactura::requestFocus);
        dialog.showAndWait();
    }

    // ── campos ────────────────────────────────────────────────────────────────

    private static TextField campo(String ejemplo) {
        TextField t = new TextField();
        t.setPromptText(ejemplo);
        t.getStyleClass().add("form-input");
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    private void armarCampos() {
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 4, 8, 0));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c2 = new ColumnConstraints(260, 340, Double.MAX_VALUE);
        c2.setHgrow(Priority.ALWAYS);
        ColumnConstraints c3 = new ColumnConstraints(150, 220, 260);
        grid.getColumnConstraints().addAll(c1, c2, c3);

        dpFecha.setConverter(FormatUtils.datePickerConverter());
        dpFecha.setPromptText("dd/mm/aaaa");
        dpFecha.setMaxWidth(Double.MAX_VALUE);
        dpFecha.getStyleClass().add("form-input");
        avisoFolio.getStyleClass().addAll("field-hint", "field-hint-warn");
        avisoFolio.setWrapText(true);
        avisoFolio.setMinHeight(Region.USE_PREF_SIZE);
        avisoFolio.setVisible(false);
        avisoFolio.setManaged(false);
        tfFolio.textProperty().addListener((o, a, b) -> {
            String problema = CalidadDatosService.problemaFolio(b);
            avisoFolio.setText(problema == null ? "" : "Revisa el folio fiscal: " + problema);
            avisoFolio.setVisible(problema != null);
            avisoFolio.setManaged(problema != null);
        });
        tfCosto.disableProperty().bind(cbIva.selectedProperty());
        tfResidual.disableProperty().bind(cbResidual.selectedProperty());
        tfCosto.textProperty().addListener((o, a, b) -> { if (b != null && !b.isBlank()) cbIva.setSelected(false); });

        AutocompleteUtil.attach(tfProveedor, sugerencias(Producto::getProveedor));
        AutocompleteUtil.attach(tfColor, sugerencias(Producto::getColor));
        // The tipo, each one once and without colors, also in a list (18-nov-2026: «MUEBLE»,
        // «mueble», «MUBLE», «MUELE», «MUEBLES» and «NEGRO» were all tipos).
        List<String> tipos = CalidadDatosService.tiposParaElegir(todos);
        AutocompleteUtil.attach(tfTipo, tipos);
        javafx.scene.control.MenuButton listaTipos = com.sibim.util.ListaParaElegir.boton(tfTipo, "el tipo de bien");
        com.sibim.util.ListaParaElegir.opciones(listaTipos, tfTipo, tipos);
        AutocompleteUtil.attach(tfMarca, sugerencias(Producto::getMarca));
        AutocompleteUtil.attach(tfModelo, sugerencias(Producto::getModelo));
        AutocompleteUtil.attach(tfUbicacion, sugerencias(Producto::getUbicacion));

        seccion("Compra", false);
        renglon(Dato.FACTURA, tfFactura, tfFactura);
        renglon(Dato.FOLIO_FISCAL, tfFolio, new VBox(2, tfFolio, avisoFolio));
        renglon(Dato.PROVEEDOR, tfProveedor, tfProveedor);
        renglon(Dato.FECHA, dpFecha, dpFecha);
        renglon(Dato.COSTO, tfCosto, new VBox(4, tfCosto, cbIva));
        renglon(Dato.VIDA_UTIL, tfVida, tfVida);
        renglon(Dato.VALOR_RESIDUAL, tfResidual, new VBox(4, tfResidual, cbResidual));
        seccion("El bien", true);
        renglon(Dato.COLOR, tfColor, tfColor);
        renglon(Dato.TIPO_BIEN, tfTipo, com.sibim.util.ListaParaElegir.junto(tfTipo, listaTipos));
        renglon(Dato.MARCA, tfMarca, tfMarca);
        renglon(Dato.MODELO, tfModelo, tfModelo);
        seccion("Dónde está", true);
        renglon(Dato.UBICACION, tfUbicacion, tfUbicacion);
    }

    /** What the inventory already says, most used first: what the autocomplete offers. */
    private List<String> sugerencias(Function<Producto, String> de) {
        return todos.stream().map(de).filter(s -> s != null && !s.isBlank()).map(String::trim)
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
            .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(Map.Entry::getKey).limit(400).toList();
    }

    private void seccion(String titulo, boolean conLinea) {
        if (conLinea) grid.add(new Separator(), 0, fila++, 3, 1);
        Label l = new Label(titulo);
        l.getStyleClass().add("dialog-field-label");
        grid.add(l, 0, fila++, 3, 1);
    }

    private void renglon(Dato dato, Control campo, Node caja) {
        Label etiqueta = DialogUtil.fieldLabel(dato.etiqueta);
        etiqueta.setLabelFor(campo);
        campo.setAccessibleText(dato.etiqueta + ". Ahora: " + EditarVarios.ahora(dato, bienes));
        Label ahora = new Label(EditarVarios.ahora(dato, bienes));
        ahora.getStyleClass().add("muted-sm");
        ahora.setWrapText(true);
        ahora.setMinHeight(Region.USE_PREF_SIZE);
        ahora.setTooltip(new Tooltip("Lo que tienen ahora los bienes seleccionados"));
        GridPane.setValignment(etiqueta, javafx.geometry.VPos.TOP);
        GridPane.setValignment(ahora, javafx.geometry.VPos.TOP);
        etiqueta.setPadding(new Insets(6, 0, 0, 0));
        ahora.setPadding(new Insets(6, 0, 0, 0));
        grid.add(etiqueta, 0, fila);
        grid.add(caja, 1, fila);
        grid.add(ahora, 2, fila++);
    }

    // ── leer y revisar ───────────────────────────────────────────────────────

    /** Thrown with what to say and the field to go back to. */
    private static final class Invalido extends Exception {
        final Control campo;
        Invalido(String mensaje, Control campo) { super(mensaje); this.campo = campo; }
    }

    private static String texto(TextField t) {
        return CalidadDatosService.normalizarTexto(null, t.getText());
    }

    private static BigDecimal dinero(TextField t, String que) throws Invalido {
        String s = t.getText() == null ? "" : t.getText().replace("$", "").replace(",", "").trim();
        if (s.isEmpty()) return null;
        try {
            BigDecimal b = new BigDecimal(s);
            if (b.signum() < 0) throw new Invalido("El " + que + " no puede ser negativo", t);
            return b;
        } catch (NumberFormatException e) {
            throw new Invalido("Escribe el " + que + " con números, ej. 1500.00", t);
        }
    }

    /** The changes asked for, in the order of the form. */
    List<Cambio> leer() throws Invalido {
        List<Cambio> c = new ArrayList<>();
        String factura = texto(tfFactura);
        if (factura != null) c.add(Cambio.fijo(Dato.FACTURA, factura));
        String folio = texto(tfFolio);
        if (folio != null) {
            String problema = CalidadDatosService.problemaFolio(folio);
            if (problema != null) throw new Invalido("Revisa el folio fiscal: " + problema, tfFolio);
            c.add(Cambio.fijo(Dato.FOLIO_FISCAL, folio));
        }
        String proveedor = texto(tfProveedor);
        if (proveedor != null) c.add(Cambio.fijo(Dato.PROVEEDOR, proveedor));
        LocalDate fecha = dpFecha.getValue();
        if (dpFecha.getEditor().getText() != null && !dpFecha.getEditor().getText().isBlank()) {
            try { fecha = dpFecha.getConverter().fromString(dpFecha.getEditor().getText()); }
            catch (Exception e) { throw new Invalido("Escribe la fecha como dd/mm/aaaa", dpFecha); }
        }
        if (fecha != null) {
            if (fecha.isAfter(LocalDate.now())) throw new Invalido("La fecha de adquisición no puede ser en el futuro", dpFecha);
            c.add(Cambio.fijo(Dato.FECHA, fecha));
        }
        if (cbIva.isSelected()) c.add(EditarVarios.ivaACadaUno());
        else {
            BigDecimal costo = dinero(tfCosto, "costo");
            if (costo != null) c.add(Cambio.fijo(Dato.COSTO, costo));
        }
        String vida = tfVida.getText() == null ? "" : tfVida.getText().trim();
        if (!vida.isEmpty()) {
            int anios;
            try { anios = Integer.parseInt(vida); }
            catch (NumberFormatException e) { throw new Invalido("Escribe la vida útil en años, con un número", tfVida); }
            if (anios < 1 || anios > 100) throw new Invalido("La vida útil va de 1 a 100 años", tfVida);
            c.add(Cambio.fijo(Dato.VIDA_UTIL, anios));
        }
        if (cbResidual.isSelected()) c.add(EditarVarios.residualDeCadaUno());
        else {
            BigDecimal residual = dinero(tfResidual, "valor residual");
            if (residual != null) c.add(Cambio.fijo(Dato.VALOR_RESIDUAL, residual));
        }
        for (var t : List.of(Map.entry(Dato.COLOR, tfColor), Map.entry(Dato.TIPO_BIEN, tfTipo), Map.entry(Dato.MARCA, tfMarca),
                Map.entry(Dato.MODELO, tfModelo), Map.entry(Dato.UBICACION, tfUbicacion))) {
            String v = texto(t.getValue());
            if (v != null) c.add(Cambio.fijo(t.getKey(), v));
        }
        return c;
    }

    private void revisar(Dialog<ButtonType> dialog, Scene scene, ProductoService servicio, Runnable alCambiar) {
        lblError.setText("");
        List<Cambio> cambios;
        try {
            cambios = leer();
        } catch (Invalido e) {
            lblError.setText(e.getMessage());
            e.campo.requestFocus();
            return;
        }
        if (cambios.isEmpty()) {
            lblError.setText("Llena al menos un dato: lo que dejes vacío no cambia");
            return;
        }
        List<Renglon> renglones = EditarVarios.revisar(bienes, cambios);
        if (renglones.isEmpty()) {
            lblError.setText("Los " + bienes.size() + " bienes ya tienen esos datos: no hay nada que cambiar");
            return;
        }
        if (!confirmar(cambios, renglones)) return;
        Scene donde = scene != null ? scene : dialog.getDialogPane().getScene();
        dialog.close();
        DialogUtil.runAsyncWithProgress(donde, "Guardando " + FormatUtils.plural(renglones.size(), "bien", "bienes") + "…",
            (DialogUtil.TareaConAvance<EditarVarios.Resultado>) avance -> EditarVarios.aplicar(servicio, renglones, avance),
            r -> {
                if (alCambiar != null && !r.guardados().isEmpty()) alCambiar.run();
                r.fallas().forEach(f -> log.warn("Cambiar datos de varios: {}", f));
                String fallas = r.fallas().isEmpty() ? "" : "; " + FormatUtils.plural(r.fallas().size(), "no se pudo", "no se pudieron")
                    + " (" + r.fallas().get(0) + (r.fallas().size() > 1 ? "…" : "") + ")";
                if (r.guardados().isEmpty()) {
                    NotificacionUtil.error(donde, "No se guardó ningún bien" + fallas);
                    return;
                }
                NotificacionUtil.exitoConAccion(donde,
                    FormatUtils.plural(r.guardados().size(), "bien actualizado", "bienes actualizados") + fallas,
                    "Deshacer", () -> DialogUtil.runAsync(() -> EditarVarios.deshacer(servicio, r),
                        n -> {
                            if (alCambiar != null) alCambiar.run();
                            NotificacionUtil.info(donde, FormatUtils.plural(n, "bien regresó", "bienes regresaron") + " a como estaba" + (n == 1 ? "" : "n"));
                        },
                        ex -> NotificacionUtil.error(donde, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> NotificacionUtil.error(donde, "No se guardaron los cambios: " + com.sibim.util.MensajeError.de(ex)));
    }

    /** Bien by bien, what it has and what it gets; true to save. */
    private boolean confirmar(List<Cambio> cambios, List<Renglon> renglones) {
        Dialog<ButtonType> d = new Dialog<>();
        DialogUtil.applyOwner(d);
        d.setTitle("Revisa antes de guardar");
        ButtonType cambiar = new ButtonType("Cambiar " + FormatUtils.plural(renglones.size(), "bien", "bienes"), ButtonBar.ButtonData.OK_DONE);
        ButtonType regresar = new ButtonType("Regresar", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().addAll(regresar, cambiar);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        d.getDialogPane().setPrefSize(Math.min(1000, pantalla.getWidth() * 0.94), Math.min(620, pantalla.getHeight() * 0.88));
        d.setResizable(true);
        DialogUtil.applyStylesheet(d.getDialogPane());
        int iguales = bienes.size() - renglones.size();
        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-check-outline", "Revisa antes de guardar",
            FormatUtils.plural(renglones.size(), "bien cambia", "bienes cambian")
                + (iguales > 0 ? " · " + FormatUtils.plural(iguales, "ya lo tenía", "ya lo tenían") : ""),
            AppColors.PRIMARY, AppColors.PRIMARY_D);
        Label que = new Label(cambios.stream().map(c -> c.dato().etiqueta + ": " + c.como()).collect(Collectors.joining(" · ")));
        que.getStyleClass().add("muted-sm");
        que.setWrapText(true);
        que.setMinHeight(Region.USE_PREF_SIZE);

        TableView<Renglon> tabla = new TableView<>(FXCollections.observableArrayList(renglones));
        tabla.getStyleClass().add("data-table");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getColumns().add(columna("Número", 110, r -> Objects.requireNonNullElse(r.bien().getCodigo(), "")));
        tabla.getColumns().add(columna("Bien", 260, r -> Objects.requireNonNullElse(r.bien().getNombre(), "")));
        TableColumn<Renglon, String> cambia = columna("Qué cambia", 560, Renglon::resumen);
        cambia.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String t, boolean vacio) {
                super.updateItem(t, vacio);
                setText(vacio ? null : t);
                setTooltip(vacio || t == null ? null : new Tooltip(t.replace(" · ", "\n")));
            }
        });
        tabla.getColumns().add(cambia);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox cuerpo = new VBox(10, que, tabla);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        d.getDialogPane().setContent(new VBox(12, header, cuerpo));
        Button ok = (Button) d.getDialogPane().lookupButton(cambiar);
        if (ok != null) ok.getStyleClass().add("dialog-ok-btn");
        return d.showAndWait().filter(b -> b == cambiar).isPresent();
    }

    private static TableColumn<Renglon, String> columna(String titulo, double ancho, Function<Renglon, String> valor) {
        TableColumn<Renglon, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }
}
