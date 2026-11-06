package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.AvanceAreasService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Phases 2 and 3 of an área's avance (Organigrama): the área goes through its
 * bienes saying which are theirs and which aren't (with why, for Patrimonio),
 * fills in what is missing with "Actualizar datos", and confirms the ones that
 * already have their label stuck on. Patrimonio opens it too, to see an área's
 * answers, answer for an área that replied on paper, or clear an aviso.
 */
public final class RevisarBienesDialog {

    /** What the list shows first. */
    public enum Vista {
        POR_REVISAR("Por revisar", p -> !p.isRevisadoPorArea()),
        REVISADOS("Sí son del área", p -> p.isRevisadoPorArea() && !p.isObservadoPorArea()),
        OBSERVADOS("No son del área", Producto::isObservadoPorArea),
        SIN_ETIQUETA("Sin etiqueta", p -> !p.isEtiquetado()),
        FALTAN_DATOS("Faltan datos", p -> !AvanceAreasService.conDatos(p)),
        TODOS("Todos", p -> true);

        final String texto;
        final Predicate<Producto> filtro;
        Vista(String texto, Predicate<Producto> filtro) { this.texto = texto; this.filtro = filtro; }
    }

    private final ProductoService service = new ProductoService();
    private final String titulo;
    /** The área whose bienes these are, or null for every área of the caller. */
    private final String area;
    private final Runnable alCerrar;
    private final ObservableList<Producto> todos = FXCollections.observableArrayList();
    private final FilteredList<Producto> visibles = new FilteredList<>(todos, p -> true);
    private final TableView<Producto> tabla = new TableView<>(visibles);
    private final ToggleGroup grupo = new ToggleGroup();
    private final Map<Vista, ToggleButton> chips = new EnumMap<>(Vista.class);
    private final TextField buscar = new TextField();
    private final Label resumen = new Label();
    private final com.sibim.util.BarraAvance barraRevision = new com.sibim.util.BarraAvance(0),
        barraEtiquetas = new com.sibim.util.BarraAvance(0);
    private final Label textoRevision = new Label(), textoEtiquetas = new Label();
    private final Button siNuestros = new Button("Sí son de mi área");
    private final Button noNuestros = new Button("No son de mi área…");
    private final Button etiquetados = new Button("Ya tienen etiqueta");
    private final Button actualizar = new Button("Actualizar datos");
    private final Button quitarRevision = new Button("Quitar la revisión");
    private Dialog<ButtonType> dialog;
    private boolean cambio = false;

    private RevisarBienesDialog(String area, Runnable alCerrar) {
        this.area = area;
        this.titulo = area != null ? "Revisar los bienes de " + area : "Revisar los bienes de mis áreas";
        this.alCerrar = alCerrar;
    }

    /** @param area the área, or null for all of the caller's
     *  @param alCerrar run on closing when something changed */
    public static void show(String area, Vista inicial, Runnable alCerrar) {
        new RevisarBienesDialog(area, alCerrar).abrir(inicial);
    }

    private void abrir(Vista inicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1180, pantalla.getWidth() * 0.95), Math.min(720, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-check-multiple-outline", titulo,
            "Di si cada bien sí es de tu área, completa lo que le falte y confirma los que ya tienen su etiqueta",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        // ── Avance ──
        Label ayuda = new Label("Selecciona varios con Ctrl o Shift (Ctrl+A: todos los de la lista). "
            + "Doble clic o Enter en uno para actualizar sus datos.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        HBox.setHgrow(ayuda, Priority.ALWAYS);
        ayuda.setMaxWidth(Double.MAX_VALUE);
        HBox avance = new HBox(24, paso("2", "Revisión", barraRevision, textoRevision),
            paso("3", "Etiquetas", barraEtiquetas, textoEtiquetas), ayuda);
        avance.setAlignment(Pos.CENTER_LEFT);

        // ── Filtros ──
        HBox filtros = new HBox(5);
        filtros.setAlignment(Pos.CENTER_LEFT);
        for (Vista v : Vista.values()) {
            ToggleButton chip = new ToggleButton(v.texto);
            chip.getStyleClass().add("filter-chip");
            if (v == Vista.OBSERVADOS) chip.getStyleClass().add("filter-chip-amber");
            if (v == Vista.REVISADOS) chip.getStyleClass().add("filter-chip-teal");
            chip.setToggleGroup(grupo);
            chip.setOnAction(e -> filtrar());
            chips.put(v, chip);
            filtros.getChildren().add(chip);
        }
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> { if (ahora == null && antes != null) antes.setSelected(true); });
        chips.get(inicial != null ? inicial : Vista.POR_REVISAR).setSelected(true);
        buscar.setPromptText("Buscar por código, nombre o resguardante…");
        buscar.getStyleClass().add("search-field");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        HBox barraFiltros = new HBox(10, filtros, buscar);
        barraFiltros.setAlignment(Pos.CENTER_LEFT);

        // ── Tabla ──
        tabla.getStyleClass().add("data-table");
        tabla.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getColumns().add(columna("Código", 105, Producto::getCodigo));
        tabla.getColumns().add(columna("Bien", 250, Producto::getNombre));
        if (area == null) tabla.getColumns().add(columna("Área", 170, Producto::getArea));
        tabla.getColumns().add(columna("Resguardante", 170, Producto::getResguardante));
        tabla.getColumns().add(columna("Revisión", 180, RevisarBienesDialog::revision));
        tabla.getColumns().add(columna("Etiqueta", 80, p -> p.isEtiquetado() ? "Sí" : "No"));
        tabla.getColumns().add(columna("Le falta", 150, RevisarBienesDialog::falta));
        tabla.setPlaceholder(new Label("No hay bienes en esta lista"));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setRowFactory(tv -> {
            TableRow<Producto> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) onActualizar(); });
            return row;
        });
        tabla.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) onActualizar(); });

        // ── Acciones ──
        siNuestros.setGraphic(new FontIcon("mdi2c-check"));
        siNuestros.getStyleClass().add("btn-primary");
        siNuestros.setTooltip(new Tooltip("Los bienes seleccionados sí están en tu área"));
        siNuestros.setOnAction(e -> onRevisar(null));
        noNuestros.setGraphic(new FontIcon("mdi2c-close"));
        noNuestros.getStyleClass().add("btn-secondary");
        noNuestros.setTooltip(new Tooltip("No están en tu área o no los encuentras: Patrimonio lo revisa"));
        noNuestros.setOnAction(e -> pedirNota().ifPresent(this::onRevisar));
        etiquetados.setGraphic(new FontIcon("mdi2t-tag-outline"));
        etiquetados.getStyleClass().add("btn-secondary");
        etiquetados.setTooltip(new Tooltip("Los seleccionados ya tienen su etiqueta pegada"));
        etiquetados.setOnAction(e -> onEtiquetas());
        actualizar.setGraphic(new FontIcon("mdi2p-pencil-outline"));
        actualizar.getStyleClass().add("btn-secondary");
        actualizar.setTooltip(new Tooltip("Foto, ubicación, estado físico y descripción del bien seleccionado (doble clic o Enter)"));
        actualizar.setOnAction(e -> onActualizar());
        quitarRevision.setGraphic(new FontIcon("mdi2u-undo-variant"));
        quitarRevision.getStyleClass().add("btn-ghost");
        quitarRevision.setTooltip(new Tooltip("Borra la respuesta del área para que lo revise de nuevo (por ejemplo, ya que Patrimonio atendió el aviso)"));
        quitarRevision.setOnAction(e -> onQuitarRevision());
        boolean admin = SessionManager.isAdmin();
        quitarRevision.setVisible(admin);
        quitarRevision.setManaged(admin);
        for (Button b : List.of(siNuestros, noNuestros, etiquetados, actualizar, quitarRevision)) b.setMinWidth(Region.USE_PREF_SIZE);
        tabla.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<Producto>) c -> botones());
        botones();

        resumen.getStyleClass().add("table-count-label");
        resumen.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox acciones = new HBox(8, resumen, hueco, quitarRevision, actualizar, etiquetados, noNuestros, siNuestros);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(10, avance, barraFiltros, tabla, acciones);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.setOnHidden(e -> { if (cambio && alCerrar != null) alCerrar.run(); });
        cargar();
        Platform.runLater(tabla::requestFocus);
        dialog.showAndWait();
    }

    private static VBox paso(String numero, String nombre, com.sibim.util.BarraAvance barra, Label texto) {
        Label n = new Label(numero);
        n.getStyleClass().addAll("avance-paso", "avance-paso-hecho");
        Label t = new Label(nombre);
        t.getStyleClass().add("avance-fila-titulo");
        HBox fila = new HBox(6, n, t);
        fila.setAlignment(Pos.CENTER_LEFT);
        barra.setPrefWidth(260);
        barra.setMinWidth(200);
        texto.getStyleClass().add("muted-sm");
        return new VBox(4, fila, barra, texto);
    }

    private void cargar() {
        DialogUtil.runAsync(service::getAll,
            lista -> {
                Set<String> elegidos = new HashSet<>();
                tabla.getSelectionModel().getSelectedItems().forEach(p -> elegidos.add(p.getId()));
                todos.setAll(lista.stream().filter(p -> area == null || area.equals(p.getArea()))
                    .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""),
                        com.sibim.service.ReporteService.POR_CODIGO))
                    .toList());
                filtrar();
                for (int i = 0; i < visibles.size(); i++)
                    if (elegidos.contains(visibles.get(i).getId())) tabla.getSelectionModel().select(i);
            },
            e -> NotificacionUtil.error(escena(), "No se pudieron cargar los bienes"));
    }

    private void filtrar() {
        Vista v = chips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
            .findFirst().orElse(Vista.TODOS);
        List<Producto> base = todos.stream().filter(v.filtro).toList();
        Set<Producto> coinciden = new HashSet<>(DialogUtil.coincidencias(base,
            p -> String.join(" ", Objects.requireNonNullElse(p.getCodigo(), ""), Objects.requireNonNullElse(p.getNombre(), ""),
                Objects.requireNonNullElse(p.getResguardante(), ""), Objects.requireNonNullElse(p.getUbicacion(), "")),
            buscar.getText()));
        visibles.setPredicate(p -> v.filtro.test(p) && coinciden.contains(p));
        for (Vista x : Vista.values()) {
            long n = todos.stream().filter(x.filtro).count();
            chips.get(x).setText(x.texto + " (" + n + ")");
        }
        int total = todos.size();
        long revisados = todos.stream().filter(Producto::isRevisadoPorArea).count();
        long observados = todos.stream().filter(Producto::isObservadoPorArea).count();
        long conDatos = todos.stream().filter(AvanceAreasService::conDatos).count();
        long etiq = todos.stream().filter(Producto::isEtiquetado).count();
        barraRevision.setAvance(total == 0 ? 0 : (double) revisados / total);
        textoRevision.setText(revisados + " de " + total + " revisados"
            + (observados > 0 ? " (" + observados + " no son del área)" : "")
            + " · " + conDatos + " con datos completos");
        barraEtiquetas.setAvance(total == 0 ? 0 : (double) etiq / total);
        textoEtiquetas.setText(etiq + " de " + total + " con etiqueta");
        resumen.setText(FormatUtils.plural(visibles.size(), "bien", "bienes"));
        botones();
    }

    private void botones() {
        List<Producto> sel = seleccion();
        boolean hay = !sel.isEmpty();
        siNuestros.setDisable(!hay);
        noNuestros.setDisable(!hay);
        etiquetados.setDisable(sel.stream().allMatch(Producto::isEtiquetado));
        actualizar.setDisable(sel.size() != 1);
        quitarRevision.setDisable(sel.stream().noneMatch(Producto::isRevisadoPorArea));
        String n = sel.size() > 1 ? " (" + sel.size() + ")" : "";
        siNuestros.setText("Sí son de mi área" + n);
        etiquetados.setText("Ya tienen etiqueta" + n);
    }

    private List<Producto> seleccion() {
        return tabla.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList();
    }

    private void onRevisar(String nota) {
        List<Producto> sel = seleccion();
        if (sel.isEmpty()) return;
        hacer(() -> service.revisarComoArea(sel, nota),
            nota == null ? FormatUtils.plural(sel.size(), "bien marcado como de tu área", "bienes marcados como de tu área")
                : "Avisado a Patrimonio: " + FormatUtils.plural(sel.size(), "bien que no es del área", "bienes que no son del área"));
    }

    private void onEtiquetas() {
        List<Producto> sel = seleccion().stream().filter(p -> !p.isEtiquetado()).toList();
        if (sel.isEmpty()) return;
        hacer(() -> service.confirmarEtiquetas(sel, true),
            FormatUtils.plural(sel.size(), "bien con etiqueta", "bienes con etiqueta"));
    }

    private void onQuitarRevision() {
        List<Producto> sel = seleccion().stream().filter(Producto::isRevisadoPorArea).toList();
        if (sel.isEmpty()) return;
        hacer(() -> service.quitarRevision(sel),
            "Se quitó la revisión de " + FormatUtils.plural(sel.size(), "bien", "bienes"));
    }

    private void onActualizar() {
        List<Producto> sel = seleccion();
        if (sel.size() != 1) return;
        // Same path as "Actualizar datos" everywhere else: it uploads the photos too.
        com.sibim.controller.GuardarBien.actualizarDatos(escena(), service, sel.get(0),
            org.slf4j.LoggerFactory.getLogger(RevisarBienesDialog.class), guardado -> { cambio = true; cargar(); });
    }

    private void hacer(DialogUtil.CheckedRunnable accion, String exito) {
        Scene escena = escena();
        DialogUtil.runAsync(accion,
            () -> { cambio = true; cargar(); NotificacionUtil.exito(escena, exito); },
            e -> NotificacionUtil.error(escena, e instanceof ProductoService.ValidationException || e instanceof SecurityException
                ? e.getMessage() : "No se pudo guardar: " + e.getMessage()));
    }

    private Optional<String> pedirNota() {
        TextInputDialog d = new TextInputDialog();
        DialogUtil.applyOwner(d);
        DialogUtil.applyStylesheet(d.getDialogPane());
        d.setTitle("No son de mi área");
        d.setHeaderText(FormatUtils.plural(seleccion().size(), "bien", "bienes") + " que no son de tu área o no encuentras");
        d.setContentText("¿Por qué? (Patrimonio lo revisa)");
        d.getEditor().setPromptText("Ej. lo tiene Obras Públicas; no está en la oficina; se dio de baja en 2023");
        d.getEditor().setPrefColumnCount(36);
        javafx.scene.Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(d.getEditor().textProperty().isEmpty());
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private Scene escena() {
        return dialog != null ? dialog.getDialogPane().getScene() : null;
    }

    private static String revision(Producto p) {
        if (!p.isRevisadoPorArea()) return "Por revisar";
        if (p.isObservadoPorArea()) return "No es del área: " + p.getRevisionAreaNota();
        return "Sí es del área";
    }

    private static String falta(Producto p) {
        List<String> f = new ArrayList<>();
        if (p.getFotoUrl() == null || p.getFotoUrl().isBlank()) f.add("foto");
        if (p.getUbicacion() == null || p.getUbicacion().isBlank()) f.add("ubicación");
        if (p.getEstadoFisico() == null || p.getEstadoFisico().isBlank()) f.add("estado");
        return f.isEmpty() ? "—" : String.join(", ", f);
    }

    private static TableColumn<Producto, String> columna(String titulo, double ancho, Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(Objects.requireNonNullElse(valor.apply(cd.getValue()), "")));
        c.setPrefWidth(ancho);
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String texto, boolean empty) {
                super.updateItem(texto, empty);
                boolean hay = !empty && texto != null && !texto.isBlank();
                setText(hay ? texto : null);
                setTooltip(hay && texto.length() > 24 ? new Tooltip(texto) : null);
            }
        });
        return c;
    }
}
