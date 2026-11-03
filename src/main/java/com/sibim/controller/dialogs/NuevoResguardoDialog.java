package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.controller.FiltroArea;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.service.ReporteService;
import com.sibim.service.ResguardoService;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Resguardos › Nuevo Resguardo, laid out like "Transferir arrastrando": who receives
 * on the left, what they receive on the right, what gets registered at the bottom.
 * Picking a person ticks the bienes the inventory has in their name, which was the
 * whole job for the 90 people with bienes and no registered resguardo; a person is
 * picked from the list, so nobody ends up spelled a second way.
 */
public final class NuevoResguardoDialog {

    /** What registers it: the service, or a stand-in in the tests. */
    @FunctionalInterface
    interface Registro {
        Resguardo crear(String nombre, String cargo, String area, List<ResguardoItem> items, String observaciones)
            throws SQLException;
    }

    private final List<Producto> bienes;
    private final List<Resguardo> resguardos;
    private final List<Resguardante> personas;
    /** The bienes in a resguardo in force, with its folio: saving would refuse them. */
    private final Map<String, String> enVigente = new HashMap<>();
    private final Set<String> marcados = new LinkedHashSet<>();
    private final Scene scene;
    private final Registro registro;
    private final Consumer<Resguardo> alCrear;
    private Dialog<ButtonType> dialog;

    // Paso 1: quién lo recibe
    private final TextField buscarPersona = new TextField();
    private final ListView<Resguardante> listaPersonas = new ListView<>();
    private final Button personaNueva = new Button();
    private final TextField cargo = new TextField();
    private final ComboBox<String> area = new ComboBox<>();
    private final List<String> areasCatalogo;
    /** Who receives: from the list, or typed as somebody new. */
    private String nombre = null;

    // Paso 2: qué recibe
    private final ToggleButton fSuyos = new ToggleButton(), fArea = new ToggleButton(), fTodos = new ToggleButton();
    private final TextField buscarBien = new TextField();
    private final ObservableList<Producto> visibles = FXCollections.observableArrayList();
    private final ListView<Producto> listaBienes = new ListView<>(visibles);
    private final Label tituloPersona = new Label();
    private final Label datosPersona = new Label();
    private final Label aviso = new Label();
    private final Hyperlink agregarOtros = new Hyperlink();
    private final HBox filaAviso = new HBox(4, aviso, agregarOtros);
    private final VBox vacio = new VBox(8);
    private final VBox panelBienes = new VBox(8);
    /** The bienes in this person's name without a resguardo. */
    private List<Producto> suyos = List.of();

    // Paso 3: lo que se registra
    private final Label resumen = new Label();
    private final TextField observaciones = new TextField();
    private final Label error = new Label();
    private Button guardar;

    private NuevoResguardoDialog(List<Producto> productos, List<Resguardo> resguardos, Scene scene,
                                 Registro registro, Consumer<Resguardo> alCrear) {
        this.bienes = productos.stream().filter(p -> p.getFechaBaja() == null)
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), ReporteService.POR_CODIGO))
            .toList();
        this.resguardos = resguardos;
        this.personas = ResguardoService.porResguardante(resguardos, this.bienes);
        this.scene = scene;
        this.registro = registro;
        this.alCrear = alCrear;
        // An área assigns resguardos to its own people: the list offers only its áreas.
        this.areasCatalogo = new ArrayList<>(Areas.getAllAreaNames().stream()
            .filter(com.sibim.session.Permisos::esAreaPropia).toList());
        for (Resguardo r : resguardos) {
            if (!r.isActivo() || r.getItems() == null) continue;
            for (ResguardoItem it : r.getItems())
                if (it.getProductoId() != null) enVigente.put(it.getProductoId(), r.getNumero());
        }
    }

    /** @param personaInicial who it is for (Reportes › Registrar como resguardo), or null
     *  @param alCrear        run once it was saved, with the new resguardo */
    public static void show(List<Producto> productos, List<Resguardo> resguardos, String personaInicial,
                            Scene scene, Consumer<Resguardo> alCrear) {
        ResguardoService service = new ResguardoService();
        show(productos, resguardos, personaInicial, scene, service::crear, alCrear);
    }

    static void show(List<Producto> productos, List<Resguardo> resguardos, String personaInicial,
                     Scene scene, Registro registro, Consumer<Resguardo> alCrear) {
        new NuevoResguardoDialog(productos, resguardos, scene, registro, alCrear).abrir(personaInicial);
    }

    /** The active bienes the inventory has in {@code nombre}'s name (accents, case and
     *  double spaces ignored) that no resguardo in force covers, by número. */
    static List<Producto> aNombreDe(String nombre, List<Producto> productos, List<Resguardo> resguardos) {
        if (nombre == null || nombre.isBlank()) return List.of();
        String clave = ResguardoService.clavePersona(nombre);
        return ResguardoService.porResguardante(resguardos, productos).stream()
            .filter(r -> ResguardoService.clavePersona(r.nombre()).equals(clave))
            .findFirst().map(Resguardante::sinResguardo).orElse(List.of());
    }

    private void abrir(String personaInicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Nuevo Resguardo de Bienes");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Also on a 1366×768 laptop: never bigger than the screen.
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1180, pantalla.getWidth() * 0.94),
            Math.min(720, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-account-outline", "Nuevo Resguardo",
            "Elige a la persona y marca los bienes que quedan a su cargo",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        guardar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        guardar.setText("Guardar resguardo");
        guardar.getStyleClass().add("dialog-ok-btn");
        // It uses up a folio and asks for a signature: Enter in a search must not do it.
        guardar.setDefaultButton(false);
        guardar.addEventFilter(ActionEvent.ACTION, ev -> { ev.consume(); registrar(); });

        HBox cuerpo = new HBox(16, panelPersona(), panelDerecho());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        // The margin goes on an inner box: the dialog stylesheet sets the content's
        // own padding to 0 (the header runs edge to edge), and it won over setPadding.
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        filtrarPersonas();
        elegir(null);
        if (personaInicial != null) {
            String clave = ResguardoService.clavePersona(personaInicial);
            personas.stream().filter(p -> ResguardoService.clavePersona(p.nombre()).equals(clave)).findFirst()
                .ifPresent(p -> {
                    listaPersonas.getSelectionModel().select(p);
                    listaPersonas.scrollTo(p);
                });
        }
        Platform.runLater(() -> (personaInicial != null ? cargo : buscarPersona).requestFocus());
        dialog.showAndWait();
    }

    // ── Paso 1: quién lo recibe ───────────────────────────────────────────────

    private VBox panelPersona() {
        buscarPersona.setId("buscarPersona");
        buscarPersona.setPromptText("Nombre o área de la persona…");
        buscarPersona.getStyleClass().add("form-input");
        buscarPersona.textProperty().addListener((o, a, b) -> filtrarPersonas());
        // The arrows move the list from the search, which keeps the focus to go on typing.
        buscarPersona.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            int i = listaPersonas.getSelectionModel().getSelectedIndex();
            int total = listaPersonas.getItems().size();
            if (e.getCode() == KeyCode.DOWN && total > 0) {
                listaPersonas.getSelectionModel().select(Math.min(total - 1, i + 1));
                listaPersonas.scrollTo(Math.max(0, listaPersonas.getSelectionModel().getSelectedIndex() - 3));
                e.consume();
            } else if (e.getCode() == KeyCode.UP && total > 0) {
                listaPersonas.getSelectionModel().select(Math.max(0, i - 1));
                listaPersonas.scrollTo(Math.max(0, listaPersonas.getSelectionModel().getSelectedIndex() - 3));
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                buscarBien.requestFocus();
                e.consume();
            }
        });

        listaPersonas.getStyleClass().add("trf-areas");
        listaPersonas.setCellFactory(lv -> new ResguardantePdfDialog.CeldaPersona(lv));
        listaPersonas.setPlaceholder(new Label("Nadie coincide: abajo puedes registrarla como persona nueva"));
        ((Label) listaPersonas.getPlaceholder()).setWrapText(true);
        ((Label) listaPersonas.getPlaceholder()).setPadding(new Insets(12));
        listaPersonas.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (p != null) elegir(p);
            else if (nombre != null && personas.stream().anyMatch(q -> q.nombre().equals(nombre))) elegir(null);
        });
        VBox.setVgrow(listaPersonas, Priority.ALWAYS);

        personaNueva.setGraphic(new FontIcon("mdi2a-account-plus-outline"));
        personaNueva.getStyleClass().add("btn-secondary");
        personaNueva.setMaxWidth(Double.MAX_VALUE);
        personaNueva.setTextOverrun(OverrunStyle.ELLIPSIS);
        personaNueva.setOnAction(e -> elegirNueva(buscarPersona.getText().trim().replaceAll("\\s+", " ")));
        ver(personaNueva, false);

        cargo.setPromptText("Cargo o puesto");
        cargo.getStyleClass().add("form-input");
        area.setPromptText("Buscar área…");
        FiltroArea.conIconos(area);
        DialogUtil.makeFilterable(area, areasCatalogo);
        area.setMaxWidth(Double.MAX_VALUE);
        area.getStyleClass().add("form-input");
        area.valueProperty().addListener((o, a, b) -> { actualizarPestanas(); refiltrar(); });
        Label lblCargo = new Label("Cargo");
        lblCargo.getStyleClass().add("dialog-field-label");
        Label lblArea = new Label("Área / Dirección");
        lblArea.getStyleClass().add("dialog-field-label");

        VBox panel = new VBox(8, TransferenciaArrastreDialog.paso(1, "¿Quién lo recibe?"), buscarPersona, listaPersonas,
            personaNueva, lblCargo, cargo, lblArea, area);
        panel.getStyleClass().add("trf-panel");
        panel.setPrefWidth(360);
        panel.setMinWidth(300);
        return panel;
    }

    private void filtrarPersonas() {
        String q = buscarPersona.getText() == null ? "" : buscarPersona.getText().trim();
        List<Resguardante> hallados = DialogUtil.coincidencias(personas, Resguardante::texto, q);
        listaPersonas.getItems().setAll(hallados);
        if (!q.isEmpty() && !hallados.isEmpty()) {
            listaPersonas.getSelectionModel().select(0);
            listaPersonas.scrollTo(0);
        }
        // Only when nobody matches: offering it next to matches invites a second spelling.
        boolean ofrecer = q.length() >= 3 && hallados.isEmpty();
        ver(personaNueva, ofrecer);
        if (ofrecer) personaNueva.setText("Persona nueva: " + q.replaceAll("\\s+", " "));
    }

    private void elegirNueva(String texto) {
        listaPersonas.getSelectionModel().clearSelection();
        elegir(null);
        nombre = texto;
        mostrarPersona(null);
        Platform.runLater(cargo::requestFocus);
    }

    private void elegir(Resguardante p) {
        nombre = p != null ? p.nombre() : null;
        cargo.clear();
        area.setValue(null);
        area.getEditor().clear();
        if (p != null) {
            // What their newest resguardo says, so it isn't typed again.
            p.resguardos().stream().map(Resguardo::getResguardanteCargo).filter(c -> c != null && !c.isBlank())
                .findFirst().ifPresent(cargo::setText);
            List<String> areas = p.areas();
            if (!areas.isEmpty() && areasCatalogo.contains(areas.get(0))) area.setValue(areas.get(0));
        }
        mostrarPersona(p);
    }

    private void mostrarPersona(Resguardante p) {
        marcados.clear();
        suyos = p != null ? p.sinResguardo() : List.of();
        suyos.forEach(b -> marcados.add(b.getId()));
        boolean hay = nombre != null;
        ver(vacio, !hay);
        ver(panelBienes, hay);
        if (hay) {
            tituloPersona.setText(nombre);
            datosPersona.setText(p == null ? "Persona nueva"
                : FormatUtils.plural((int) p.activos(), "resguardo vigente", "resguardos vigentes")
                    + " · " + FormatUtils.plural(suyos.size(), "bien sin resguardo", "bienes sin resguardo"));
            buscarBien.clear();
            // Nothing in their name: start with where the bienes can come from.
            (suyos.isEmpty() ? (area.getValue() != null ? fArea : fTodos) : fSuyos).setSelected(true);
            avisarOtroNombre(p);
        }
        actualizarPestanas();
        refiltrar();
        actualizar();
    }

    /** A person who is also in the list with the same words in another order. */
    private void avisarOtroNombre(Resguardante p) {
        if (p != null && com.sibim.service.CalidadDatosService.dosPersonas(p.nombre())) {
            aviso.setText("Este nombre trae a dos personas: el resguardo saldría a nombre de ambas.");
            ver(filaAviso, true);
            ver(agregarOtros, false);
            return;
        }
        Optional<Resguardante> otro = p == null ? Optional.empty() : personas.stream()
            .filter(q -> !q.nombre().equals(p.nombre()) && ResguardoService.mismasPalabras(q.nombre(), p.nombre()))
            .findFirst();
        ver(filaAviso, otro.isPresent());
        if (otro.isEmpty()) return;
        Resguardante o = otro.get();
        aviso.setText("También aparece como «" + o.nombre() + "» (" + FormatUtils.plural(o.sinResguardo().size(), "bien sin resguardo", "bienes sin resguardo") + ").");
        ver(agregarOtros, !o.sinResguardo().isEmpty());
        agregarOtros.setText("Si es la misma persona, agrégalos");
        agregarOtros.setOnAction(e -> {
            o.sinResguardo().forEach(b -> marcados.add(b.getId()));
            List<Producto> juntos = new ArrayList<>(suyos);
            juntos.addAll(o.sinResguardo());
            suyos = juntos;
            ver(filaAviso, false);
            actualizarPestanas();
            refiltrar();
            actualizar();
        });
    }

    // ── Paso 2: qué recibe ────────────────────────────────────────────────────

    private VBox panelDerecho() {
        FontIcon iconoVacio = new FontIcon("mdi2a-account-search-outline");
        iconoVacio.getStyleClass().add("empty-icon-lg");
        Label tituloVacio = new Label("Elige a quién se le asignan");
        tituloVacio.getStyleClass().add("empty-state-msg");
        Label pista = new Label("Sus bienes sin resguardo se marcan solos; puedes quitar o agregar los que quieras");
        pista.getStyleClass().add("empty-state-hint");
        vacio.getChildren().setAll(iconoVacio, tituloVacio, pista);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        tituloPersona.getStyleClass().add("org-area-name");
        datosPersona.getStyleClass().add("org-table-sub");
        FontIcon icono = new FontIcon("mdi2a-account-outline");
        icono.getStyleClass().add("trf-tarjeta-icono");
        StackPane fondo = new StackPane(icono);
        fondo.getStyleClass().add("trf-tarjeta-icono-fondo");
        fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox encabezado = new HBox(10, fondo, new VBox(2, tituloPersona, datosPersona));
        encabezado.setAlignment(Pos.CENTER_LEFT);

        aviso.getStyleClass().addAll("field-hint", "field-hint-warn");
        aviso.setWrapText(true);
        filaAviso.setAlignment(Pos.CENTER_LEFT);
        ver(filaAviso, false);

        ToggleGroup origen = new ToggleGroup();
        for (ToggleButton t : List.of(fSuyos, fArea, fTodos)) {
            t.setToggleGroup(origen);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
        }
        fSuyos.setTooltip(new Tooltip("Los que el inventario tiene a su nombre y no están en un resguardo vigente"));
        fArea.setTooltip(new Tooltip("Los bienes del área elegida a la izquierda"));
        origen.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            refiltrar();
        });
        Hyperlink todos = new Hyperlink("Marcar todos");
        todos.setTooltip(new Tooltip("Marca los de esta lista que estén libres"));
        todos.setOnAction(e -> { visibles.stream().filter(b -> !enVigente.containsKey(b.getId())).forEach(b -> marcados.add(b.getId())); listaBienes.refresh(); actualizar(); });
        Hyperlink ninguno = new Hyperlink("Ninguno");
        ninguno.setOnAction(e -> { marcados.clear(); listaBienes.refresh(); actualizar(); });
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox pestanas = new HBox(6, fSuyos, fArea, fTodos, hueco, todos, new Label("·"), ninguno);
        pestanas.setAlignment(Pos.CENTER_LEFT);

        buscarBien.setId("buscarBien");
        buscarBien.setPromptText("Buscar por código, nombre o serie…");
        buscarBien.getStyleClass().add("form-input");
        buscarBien.textProperty().addListener((o, a, b) -> refiltrar());

        listaBienes.getStyleClass().addAll("cnt-lista", "rsg-lista");
        listaBienes.setCellFactory(lv -> new CeldaBien(lv));
        listaBienes.setPlaceholder(new Label("No hay bienes aquí: prueba con otra pestaña"));
        VBox.setVgrow(listaBienes, Priority.ALWAYS);

        panelBienes.getChildren().setAll(encabezado, filaAviso, pestanas, buscarBien, listaBienes);
        VBox.setVgrow(panelBienes, Priority.ALWAYS);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Marca los bienes que recibe");
        paso2.setPadding(Insets.EMPTY);

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        HBox paso3 = TransferenciaArrastreDialog.paso(3, "Se registra");
        paso3.setPadding(Insets.EMPTY);
        paso3.setMinWidth(Region.USE_PREF_SIZE);
        HBox linea3 = new HBox(10, paso3, resumen);
        linea3.setAlignment(Pos.CENTER_LEFT);
        observaciones.setPromptText("Observaciones (opcional)");
        observaciones.getStyleClass().add("form-input");
        error.getStyleClass().add("field-error-label");
        error.setWrapText(true);
        ver(error, false);

        VBox panel = new VBox(8, paso2, vacio, panelBienes, new Separator(), linea3, observaciones, error);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private List<Producto> delArea() {
        String a = area.getValue();
        return a == null ? List.of() : bienes.stream().filter(p -> a.equalsIgnoreCase(p.getArea())).toList();
    }

    private void actualizarPestanas() {
        fSuyos.setText("A su nombre (" + suyos.size() + ")");
        fArea.setText("De su área (" + delArea().size() + ")");
        fTodos.setText("Todos (" + bienes.size() + ")");
    }

    private void refiltrar() {
        List<Producto> base = fSuyos.isSelected() ? suyos : fArea.isSelected() ? delArea() : bienes;
        visibles.setAll(DialogUtil.coincidencias(base, NuevoResguardoDialog::textoDe, buscarBien.getText()));
    }

    private static String textoDe(Producto p) {
        return Objects.requireNonNullElse(p.getCodigo(), "") + " " + Objects.requireNonNullElse(p.getNombre(), "") + " "
            + Objects.requireNonNullElse(p.getNumeroSerie(), "") + " " + Objects.requireNonNullElse(p.getResguardante(), "");
    }

    private void actualizar() {
        int n = marcados.size();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (n > 0 && nombre != null) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(nombre == null ? "Nada todavía"
            : n == 0 ? "Nada: marca al menos un bien"
            : FormatUtils.plural(n, "bien", "bienes") + " a nombre de " + nombre);
        guardar.setDisable(nombre == null || n == 0);
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    /** Stays open until it is saved: an error used to close the window and lose the list. */
    private void registrar() {
        List<ResguardoItem> items = new ArrayList<>();
        for (Producto p : bienes) {
            if (!marcados.contains(p.getId())) continue;
            ResguardoItem item = new ResguardoItem();
            item.setProductoId(p.getId()); item.setProductoNombre(p.getNombre());
            item.setProductoCodigo(p.getCodigo()); item.setArea(p.getArea());
            item.setValorUnitario(p.getValorUnitario()); item.setNumeroSerie(p.getNumeroSerie());
            items.add(item);
        }
        String quien = nombre, puesto = cargo.getText().trim(), donde = area.getValue(), obs = observaciones.getText().trim();
        ver(error, false);
        guardar.setDisable(true);
        DialogUtil.runAsync(
            () -> registro.crear(quien, puesto, donde, items, obs),
            resguardo -> {
                dialog.setResult(ButtonType.OK);
                dialog.close();
                NotificacionUtil.exito(scene, "Resguardo " + resguardo.getNumero() + " creado");
                // After this window is really gone: alCrear asks about the PDF, and a
                // window opened while this one's event loop unwinds freezes the app.
                Platform.runLater(() -> alCrear.accept(resguardo));
            },
            e -> {
                error.setText("No se pudo guardar el resguardo: " + (e.getMessage() != null ? e.getMessage() : "error desconocido"));
                ver(error, true);
                guardar.setDisable(false);
            });
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static boolean dentroDe(Object objetivo, Node nodo) {
        for (Node n = objetivo instanceof Node o ? o : null; n != null; n = n.getParent())
            if (n == nodo) return true;
        return false;
    }

    /** A bien: a click ticks it; one already in a resguardo in force says which and stays out. */
    private final class CeldaBien extends ListCell<Producto> {
        private final CheckBox casilla = new CheckBox();
        private final Label codigo = new Label();
        private final Label nombreBien = new Label();
        private final Label detalle = new Label();
        private final Label ocupado = new Label();
        private final HBox fila;

        CeldaBien(ListView<Producto> lv) {
            codigo.getStyleClass().add("trf-codigo");
            codigo.setMinWidth(Region.USE_PREF_SIZE);
            nombreBien.getStyleClass().add("org-child-label");
            nombreBien.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombreBien.setMinWidth(0);
            HBox linea = new HBox(8, codigo, nombreBien);
            linea.setAlignment(Pos.CENTER_LEFT);
            detalle.getStyleClass().add("org-table-sub");
            detalle.setTextOverrun(OverrunStyle.ELLIPSIS);
            detalle.setMinWidth(0);
            VBox textos = new VBox(3, linea, detalle);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            ocupado.getStyleClass().addAll("cell-badge", "cell-badge-blue");
            ocupado.setMinWidth(Region.USE_PREF_SIZE);
            fila = new HBox(10, casilla, textos, ocupado);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("cnt-fila");
            fila.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            fila.setMinWidth(0);
            casilla.setOnAction(e -> { if (getItem() != null) alternar(getItem()); });
            fila.setOnMouseClicked(e -> {
                Producto p = getItem();
                if (p == null || casilla.isDisabled() || dentroDe(e.getTarget(), casilla)) return;
                alternar(p);
            });
        }

        private void alternar(Producto p) {
            if (!marcados.remove(p.getId())) marcados.add(p.getId());
            listaBienes.refresh();
            actualizar();
        }

        @Override protected void updateItem(Producto p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); return; }
            codigo.setText(p.getCodigo());
            nombreBien.setText(p.getNombre());
            nombreBien.setTooltip(new Tooltip(p.getNombre()));
            List<String> partes = new ArrayList<>();
            partes.add(p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante");
            if (p.getArea() != null) partes.add(p.getArea());
            if (p.getNumeroSerie() != null && !p.getNumeroSerie().isBlank()) partes.add("Serie " + p.getNumeroSerie());
            detalle.setText(String.join(" · ", partes));
            String folio = enVigente.get(p.getId());
            ocupado.setText(folio != null ? "En " + folio : "");
            ver(ocupado, folio != null);
            casilla.setDisable(folio != null);
            casilla.setSelected(marcados.contains(p.getId()));
            casilla.setAccessibleText(p.getCodigo());
            fila.getStyleClass().removeAll("rsg-fila-sel", "rsg-fila-ocupada");
            if (folio != null) fila.getStyleClass().add("rsg-fila-ocupada");
            else if (casilla.isSelected()) fila.getStyleClass().add("rsg-fila-sel");
            setGraphic(fila);
        }
    }
}
