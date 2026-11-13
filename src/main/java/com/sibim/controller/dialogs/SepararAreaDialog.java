package com.sibim.controller.dialogs;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.service.AreaService;
import com.sibim.service.ReporteService;
import com.sibim.service.ResguardoService;
import com.sibim.service.SepararArea;
import com.sibim.service.TransferenciaRapidaService;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Configuración › Áreas › «Separar en un área nueva…» (see {@link SepararArea}): the new
 *  área's name, where it goes and its prefix; which bienes of the selected área go to it
 *  (marked by person or by ubicación, or one by one); what will be done, said before
 *  doing it; and the new resguardos to print and sign. */
public final class SepararAreaDialog {

    private final AreaCatalog.Entrada origen;
    private final List<Producto> bienes;
    private final Map<String, Resguardo> resguardoDe;
    private final Map<Producto, SimpleBooleanProperty> marcados = new LinkedHashMap<>();

    private final TextField tfNombre = new TextField();
    private final ComboBox<AreaCatalog.Grupo> cbGrupo = new ComboBox<>();
    private final ComboBox<String> cbPadre = new ComboBox<>();
    private final TextField tfPrefijo = new TextField();
    private final TextField tfBuscar = new TextField();
    private final ComboBox<String> cbMarcar = new ComboBox<>();
    private final Label lblResumen = new Label();
    private final Label lblError = new Label();
    private final Button btnSeparar = new Button();
    private boolean prefijoEscrito;

    private SepararAreaDialog(AreaCatalog.Entrada origen, List<Producto> bienes, Map<String, Resguardo> resguardoDe) {
        this.origen = origen;
        this.bienes = bienes.stream().filter(p -> p.getFechaBaja() == null && origen.nombre().equals(p.getArea()))
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), ReporteService.POR_CODIGO)).toList();
        this.resguardoDe = resguardoDe;
    }

    /** Reads the área's bienes and resguardos, then opens.
     *  @param alSeparar after the área was made (to refresh the list of áreas) */
    public static void show(Scene scene, AreaCatalog.Entrada origen, Runnable alSeparar) {
        DialogUtil.runAsyncWithProgress(scene, "Leyendo los bienes de " + origen.nombre() + "…",
            () -> {
                List<Producto> todos = new com.sibim.service.ProductoService().getAll();
                Map<String, Resguardo> vigentes = new HashMap<>();
                for (Resguardo r : new ResguardoService().getAll())
                    if (r.isActivo() && r.getItems() != null)
                        r.getItems().forEach(it -> { if (it.getProductoId() != null) vigentes.putIfAbsent(it.getProductoId(), r); });
                return new SepararAreaDialog(origen, todos, vigentes);
            },
            d -> {
                if (d.bienes.isEmpty()) {
                    NotificacionUtil.info(scene, origen.nombre() + " no tiene bienes activos que separar");
                    return;
                }
                d.abrir(scene, alSeparar);
            },
            ex -> NotificacionUtil.error(scene, "No se pudieron leer los bienes: " + com.sibim.util.MensajeError.de(ex)));
    }

    private Resguardo resguardo(Producto p) { return resguardoDe.get(p.getId()); }

    private void abrir(Scene scene, Runnable alSeparar) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        String titulo = "Separar de " + origen.nombre();
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1080, pantalla.getWidth() * 0.94), Math.min(760, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-call-split", titulo,
            "Una parte de esta área pasa a ser un área aparte, con sus bienes y sus resguardantes",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        VBox cuerpo = new VBox(12, datosDelArea(), queBienes(), pie(dialog, scene, alSeparar));
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        actualizar();
        javafx.application.Platform.runLater(tfNombre::requestFocus);
        dialog.showAndWait();
    }

    // ── 1. el área nueva ─────────────────────────────────────────────────────

    private Node datosDelArea() {
        tfNombre.setPromptText("Ej. Mercado Municipal");
        tfNombre.getStyleClass().add("form-input");
        cbGrupo.getItems().addAll(AreaCatalog.Grupo.DIRECCION, AreaCatalog.Grupo.SECRETARIA, AreaCatalog.Grupo.AUTONOMO);
        cbGrupo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AreaCatalog.Grupo g) {
                return g == null ? "" : switch (g) {
                    case DIRECCION -> "Dirección"; case SECRETARIA -> "Secretaría";
                    case AUTONOMO -> "Organismo autónomo"; case PRESIDENCIA -> "Presidencia";
                };
            }
            @Override public AreaCatalog.Grupo fromString(String s) { return null; }
        });
        cbGrupo.setValue(AreaCatalog.Grupo.DIRECCION);
        Areas.catalogo().entradas().stream()
            .filter(e -> e.grupo() == AreaCatalog.Grupo.PRESIDENCIA || e.grupo() == AreaCatalog.Grupo.SECRETARIA)
            .forEach(e -> cbPadre.getItems().add(e.nombre()));
        // Next to the área it comes from: under its secretaría, or under it when it is one.
        cbPadre.setValue(origen.grupo() == AreaCatalog.Grupo.DIRECCION ? origen.padre()
            : origen.grupo() == AreaCatalog.Grupo.SECRETARIA || origen.grupo() == AreaCatalog.Grupo.PRESIDENCIA ? origen.nombre() : null);
        cbPadre.setMaxWidth(Double.MAX_VALUE);
        cbPadre.disableProperty().bind(cbGrupo.valueProperty().isNotEqualTo(AreaCatalog.Grupo.DIRECCION));
        tfPrefijo.setPromptText("Ej. MM");
        tfPrefijo.getStyleClass().add("form-input");
        tfPrefijo.setPrefColumnCount(8);
        tfPrefijo.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.equals(b.toUpperCase())) tfPrefijo.setText(b.toUpperCase());
        });
        // The prefix follows the name until someone writes their own.
        tfPrefijo.setOnKeyTyped(e -> prefijoEscrito = !tfPrefijo.getText().isBlank());
        tfNombre.textProperty().addListener((o, a, b) -> {
            if (!prefijoEscrito) tfPrefijo.setText(SepararArea.prefijoSugerido(b,
                Areas.catalogo().entradas().stream().map(AreaCatalog.Entrada::prefijo).toList()));
            actualizar();
        });
        tfPrefijo.textProperty().addListener((o, a, b) -> actualizar());

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        Label lNombre = DialogUtil.fieldLabel("Nombre del área nueva"), lGrupo = DialogUtil.fieldLabel("Tipo"),
            lPadre = DialogUtil.fieldLabel("Depende de"), lPrefijo = DialogUtil.fieldLabel("Prefijo");
        lNombre.setLabelFor(tfNombre); lGrupo.setLabelFor(cbGrupo); lPadre.setLabelFor(cbPadre); lPrefijo.setLabelFor(tfPrefijo);
        g.addRow(0, lNombre, tfNombre, lPrefijo, tfPrefijo);
        g.addRow(1, lGrupo, cbGrupo, lPadre, cbPadre);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c2 = new ColumnConstraints(220, 340, Double.MAX_VALUE); c2.setHgrow(Priority.ALWAYS);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c4 = new ColumnConstraints(160, 300, Double.MAX_VALUE); c4.setHgrow(Priority.SOMETIMES);
        g.getColumnConstraints().addAll(c1, c2, c3, c4);
        return g;
    }

    // ── 2. qué bienes pasan ──────────────────────────────────────────────────

    private Node queBienes() {
        Label titulo = new Label("¿Qué bienes pasan? Márcalos (Espacio marca y desmarca). Cada quien se los lleva en un resguardo nuevo, con su mismo cargo.");
        titulo.getStyleClass().add("muted-sm");
        titulo.setWrapText(true);
        titulo.setMinHeight(Region.USE_PREF_SIZE);

        bienes.forEach(p -> {
            SimpleBooleanProperty si = new SimpleBooleanProperty(false);
            si.addListener((o, a, b) -> actualizar());
            marcados.put(p, si);
        });

        tfBuscar.setPromptText("Buscar: número, bien, resguardante o ubicación…");
        tfBuscar.getStyleClass().add("form-input");
        HBox.setHgrow(tfBuscar, Priority.ALWAYS);
        // «Marcar los de…»: every person and every ubicación of the área, with how many bienes.
        Map<String, List<Producto>> grupos = new LinkedHashMap<>();
        contar(grupos, "Resguardante: ", Producto::getResguardante);
        contar(grupos, "Ubicación: ", Producto::getUbicacion);
        grupos.forEach((k, v) -> cbMarcar.getItems().add(k + " (" + v.size() + ")"));
        cbMarcar.setPromptText("Marcar los de…");
        cbMarcar.setAccessibleText("Marcar todos los bienes de un resguardante o de una ubicación");
        cbMarcar.setOnAction(e -> {
            String elegido = cbMarcar.getValue();
            if (elegido == null) return;
            String clave = elegido.replaceFirst(" \\(\\d+\\)$", "");
            grupos.getOrDefault(clave, List.of()).forEach(p -> marcados.get(p).set(true));
            javafx.application.Platform.runLater(() -> cbMarcar.getSelectionModel().clearSelection());
        });
        Button quitar = new Button("Quitar las marcas");
        quitar.getStyleClass().add("btn-secondary");
        quitar.setOnAction(e -> marcados.values().forEach(m -> m.set(false)));
        HBox barra = new HBox(8, tfBuscar, cbMarcar, quitar);
        barra.setAlignment(Pos.CENTER_LEFT);

        ObservableList<Producto> todos = FXCollections.observableArrayList(bienes);
        FilteredList<Producto> visibles = new FilteredList<>(todos);
        tfBuscar.textProperty().addListener((o, a, b) -> {
            String q = plano(b);
            visibles.setPredicate(q.isEmpty() ? null : p -> plano(String.join(" ", Objects.requireNonNullElse(p.getCodigo(), ""),
                Objects.requireNonNullElse(p.getNombre(), ""), Objects.requireNonNullElse(p.getResguardante(), ""),
                Objects.requireNonNullElse(p.getUbicacion(), ""))).contains(q));
        });
        TableView<Producto> tabla = new TableView<>(visibles);
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<Producto, Boolean> pasa = new TableColumn<>("Pasa");
        pasa.setCellValueFactory(c -> marcados.get(c.getValue()));
        pasa.setCellFactory(CheckBoxTableCell.forTableColumn(pasa));
        pasa.setEditable(true);
        pasa.setPrefWidth(64);
        pasa.setMinWidth(64);
        pasa.setMaxWidth(70);
        tabla.getColumns().add(pasa);
        TableColumn<Producto, String> numero = columna("Número", 100, p -> Objects.requireNonNullElse(p.getCodigo(), ""));
        numero.setMinWidth(100);
        tabla.getColumns().add(numero);
        tabla.getColumns().add(columna("Bien", 280, p -> Objects.requireNonNullElse(p.getNombre(), "")));
        tabla.getColumns().add(columna("Resguardante", 210, p -> Objects.requireNonNullElse(p.getResguardante(), "")));
        tabla.getColumns().add(columna("Ubicación", 200, p -> Objects.requireNonNullElse(p.getUbicacion(), "")));
        tabla.getColumns().add(columna("Resguardo", 140, p -> resguardo(p) != null ? resguardo(p).getNumero() : "—"));
        tabla.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            List<Producto> sel = List.copyOf(tabla.getSelectionModel().getSelectedItems());
            if (sel.isEmpty()) return;
            boolean poner = sel.stream().anyMatch(p -> !marcados.get(p).get());
            sel.forEach(p -> marcados.get(p).set(poner));
            e.consume();
        });
        tabla.setPlaceholder(new Label("Ningún bien coincide con la búsqueda"));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox caja = new VBox(8, titulo, barra, tabla);
        VBox.setVgrow(caja, Priority.ALWAYS);
        return caja;
    }

    private void contar(Map<String, List<Producto>> grupos, String que, Function<Producto, String> de) {
        Map<String, List<Producto>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Producto p : bienes) {
            String v = de.apply(p);
            if (v == null || v.isBlank()) continue;
            m.computeIfAbsent(v.trim().replaceAll("\\s+", " "), k -> new ArrayList<>()).add(p);
        }
        m.forEach((k, v) -> grupos.put(que + k, v));
    }

    // ── 3. qué se hará ───────────────────────────────────────────────────────

    private Node pie(Dialog<ButtonType> dialog, Scene scene, Runnable alSeparar) {
        lblResumen.setWrapText(true);
        lblResumen.setMinHeight(Region.USE_PREF_SIZE);
        lblResumen.getStyleClass().add("table-count-label");
        lblError.getStyleClass().add("field-hint-error");
        lblError.setWrapText(true);
        lblError.setMinHeight(Region.USE_PREF_SIZE);
        btnSeparar.setGraphic(new FontIcon("mdi2c-call-split"));
        btnSeparar.getStyleClass().add("btn-primary");
        btnSeparar.setMinWidth(Region.USE_PREF_SIZE);
        btnSeparar.setOnAction(e -> separar(dialog, scene, alSeparar));
        VBox textos = new VBox(4, lblResumen, lblError);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox abajo = new HBox(12, textos, btnSeparar);
        abajo.setAlignment(Pos.CENTER_LEFT);
        return abajo;
    }

    private List<Producto> elegidos() {
        return marcados.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
    }

    private List<SepararArea.Grupo> grupos() {
        return SepararArea.agrupar(elegidos(), this::resguardo);
    }

    private void actualizar() {
        List<Producto> sel = elegidos();
        btnSeparar.setText(sel.isEmpty() ? "Separar" : "Separar " + FormatUtils.plural(sel.size(), "bien", "bienes"));
        btnSeparar.setDisable(sel.isEmpty() || tfNombre.getText().isBlank());
        if (sel.isEmpty()) {
            lblResumen.setText("Marca los bienes que pasan a " + (tfNombre.getText().isBlank() ? "el área nueva" : tfNombre.getText().trim()) + ".");
            return;
        }
        lblResumen.setText("Pasan " + FormatUtils.plural(sel.size(), "bien", "bienes") + " (" + FormatUtils.formatCurrency(valor(sel)) + ") · "
            + quienes(grupos()));
    }

    private static BigDecimal valor(List<Producto> bienes) {
        return bienes.stream().map(p -> p.getPrecioCompra() == null ? BigDecimal.ZERO
            : p.getPrecioCompra().multiply(BigDecimal.valueOf(Math.max(1, p.getStockActual())))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** «resguardo nuevo de JUAN CARLOS FICTICIO CORTES (35) · 1 sin resguardo». */
    private static String quienes(List<SepararArea.Grupo> grupos) {
        List<String> partes = new ArrayList<>();
        List<String> personas = grupos.stream().filter(g -> g.persona() != null)
            .map(g -> g.persona() + " (" + g.bienes().size() + ")").toList();
        if (!personas.isEmpty())
            partes.add((personas.size() == 1 ? "resguardo nuevo de " : "resguardos nuevos de ") + String.join(", ", personas));
        grupos.stream().filter(g -> g.persona() == null).findFirst()
            .ifPresent(g -> partes.add(FormatUtils.plural(g.bienes().size(), "sin resguardo", "sin resguardo")
                + " (sin resguardante o con dos personas)"));
        return String.join(" · ", partes);
    }

    private AreaCatalog.Entrada leerArea() {
        AreaCatalog.Grupo g = cbGrupo.getValue();
        return new AreaCatalog.Entrada(tfNombre.getText().trim().replaceAll("\\s+", " "), g,
            g == AreaCatalog.Grupo.DIRECCION ? cbPadre.getValue() : null, tfPrefijo.getText().trim().toUpperCase());
    }

    private void separar(Dialog<ButtonType> dialog, Scene scene, Runnable alSeparar) {
        lblError.setText("");
        AreaCatalog.Entrada nueva = leerArea();
        List<SepararArea.Grupo> grupos = grupos();
        try {
            if (nueva.nombre().isBlank()) throw new IllegalArgumentException("Escribe el nombre del área nueva");
            if (Areas.catalogo().buscar(nueva.nombre()).isPresent())
                throw new IllegalArgumentException("Ya existe un área que se llama así: para pasarle bienes usa Transferir en Bienes");
            Areas.catalogo().con(nueva);   // a used prefix, a dirección without a secretaría…
            if (grupos.isEmpty()) throw new IllegalArgumentException("Marca los bienes que pasan al área nueva");
        } catch (IllegalArgumentException ex) {
            lblError.setText(ex.getMessage());
            return;
        }
        int n = grupos.stream().mapToInt(g -> g.bienes().size()).sum();
        String mensaje = "Se crea «" + nueva.nombre() + "» (" + nueva.prefijo() + ")"
            + (nueva.padre() != null ? " dentro de " + nueva.padre() : "") + ".\n\n"
            + "Pasan " + FormatUtils.plural(n, "bien", "bienes") + " de " + origen.nombre() + " con números nuevos ("
            + nueva.prefijo() + "/01…); " + quienes(grupos) + ".\n\n"
            + "Los resguardos de " + origen.nombre() + " que se queden sin bienes se cancelan solos. "
            + "Si esos bienes ya tenían etiqueta impresa, hay que reimprimirla: cambia su número.";
        if (!ConfirmacionUtil.confirmar("Separar " + nueva.nombre(), mensaje, "Separar " + FormatUtils.plural(n, "bien", "bienes"))) return;
        Scene donde = scene != null ? scene : dialog.getDialogPane().getScene();
        dialog.close();
        AreaService areas = new AreaService();
        DialogUtil.runAsyncWithProgress(donde, "Separando " + nueva.nombre() + "…",
            () -> SepararArea.aplicar(nueva, grupos, SepararArea.motivo(nueva.nombre(), origen.nombre()),
                areas::guardar, new TransferenciaRapidaService()),
            r -> {
                if (alSeparar != null) alSeparar.run();
                List<Resguardo> nuevos = r.resguardos();
                String errores = r.errores().isEmpty() ? "" : " · " + FormatUtils.plural(r.errores().size(), "no pasó", "no pasaron")
                    + " (" + r.errores().get(0) + (r.errores().size() > 1 ? "…" : "") + ")";
                String texto = "«" + r.area() + "» creada · " + FormatUtils.plural(r.bienes(), "bien pasó", "bienes pasaron")
                    + (nuevos.isEmpty() ? "" : " · " + nuevos.stream().map(Resguardo::getNumero).collect(Collectors.joining(", "))) + errores;
                if (nuevos.isEmpty()) { NotificacionUtil.exito(donde, texto); return; }
                NotificacionUtil.exitoConAccion(donde, texto, "Imprimir resguardos", () -> imprimir(donde, nuevos));
            },
            ex -> NotificacionUtil.error(donde, "No se separó el área: " + com.sibim.util.MensajeError.de(ex)));
    }

    /** The new resguardos in one PDF, to print and sign. */
    private static void imprimir(Scene scene, List<Resguardo> resguardos) {
        DialogUtil.runAsyncWithProgress(scene, "Generando los resguardos…",
            () -> {
                ResguardoService rs = new ResguardoService();
                List<File> partes = new ArrayList<>();
                for (Resguardo r : resguardos) partes.add(rs.exportarPdf(r));
                return partes.size() == 1 ? partes.get(0) : ReporteService.unirPdfs(partes, "resguardos");
            },
            f -> DialogUtil.showExportResultDialog(scene, f),
            ex -> NotificacionUtil.error(scene, "No se pudieron generar los resguardos: " + com.sibim.util.MensajeError.de(ex)));
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
    }

    private static TableColumn<Producto, String> columna(String titulo, double ancho, Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
