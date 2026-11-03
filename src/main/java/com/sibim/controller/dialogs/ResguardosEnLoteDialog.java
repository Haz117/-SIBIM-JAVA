package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.ReporteService;
import com.sibim.service.ResguardoService;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Resguardos › En lote: a resguardo each for the people who hold bienes with no
 *  resguardo registered (30-oct-2026: 120 people, 91 of them with none in force), and one
 *  PDF with all of them to print and sign. Someone also listed under another spelling
 *  has to be unified first, or their resguardo would carry the wrong name. */
public final class ResguardosEnLoteDialog {

    private static final String OBSERVACION = "Registrado en lote desde el inventario.";

    /** What registers one: the service, or a stand-in in the tests. */
    @FunctionalInterface
    interface Registro {
        Resguardo crear(Resguardante persona, List<ResguardoItem> items) throws Exception;
    }

    private final List<Resguardante> personas;
    private final Set<String> repetidas;
    private final Set<String> marcadas = new LinkedHashSet<>();
    private final ObservableList<Resguardante> visibles = FXCollections.observableArrayList();
    private final ListView<Resguardante> lista = new ListView<>(visibles);
    private final ToggleButton fSin = new ToggleButton(), fCon = new ToggleButton();
    private final TextField buscar = new TextField();
    private final Label nombre = new Label();
    private final Label datos = new Label();
    private final FlowPane codigos = new FlowPane(5, 5);
    private final VBox vacio = new VBox(8);
    private final VBox detalle = new VBox(8);
    private final Label resumen = new Label();
    private final Button registrar = new Button("Registrar y generar PDF");
    private final Registro registro;
    private final Scene scene;
    private final Consumer<Integer> alTerminar;
    private Dialog<ButtonType> dialog;

    private ResguardosEnLoteDialog(List<Producto> bienes, List<Resguardo> resguardos, Scene scene, Registro registro, Consumer<Integer> alTerminar) {
        this.personas = ResguardoService.porResguardante(resguardos, bienes).stream()
            .filter(p -> !p.sinResguardo().isEmpty()).toList();
        // Every spelling of someone listed more than once: unified first, see the class.
        this.repetidas = CalidadDatosService.personasRepetidas(resguardos, bienes, Set.of()).stream()
            .flatMap(List::stream).map(Resguardante::nombre).collect(Collectors.toSet());
        this.scene = scene;
        this.registro = registro;
        this.alTerminar = alTerminar;
    }

    /** @param alTerminar run after registering, with how many resguardos */
    public static void show(List<Producto> bienes, List<Resguardo> resguardos, Scene scene, Consumer<Integer> alTerminar) {
        ResguardoService service = new ResguardoService();
        show(bienes, resguardos, scene, (p, items) -> service.crear(p.nombre(), null,
            p.areas().size() == 1 ? p.areas().get(0) : null, items, OBSERVACION), alTerminar);
    }

    static void show(List<Producto> bienes, List<Resguardo> resguardos, Scene scene, Registro registro, Consumer<Integer> alTerminar) {
        new ResguardosEnLoteDialog(bienes, resguardos, scene, registro, alTerminar).abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardos en lote");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1100, pantalla.getWidth() * 0.94), Math.min(700, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-multiple-outline", "Resguardos en lote",
            "Registra de una vez a quienes tienen bienes sin resguardo y genera un solo PDF para firmar",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        ToggleGroup tipo = new ToggleGroup();
        for (ToggleButton t : List.of(fSin, fCon)) {
            t.setToggleGroup(tipo);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
        }
        fSin.setText("Sin ningún resguardo (" + personas.stream().filter(p -> p.activos() == 0).count() + ")");
        fCon.setText("Con bienes sueltos (" + personas.stream().filter(p -> p.activos() > 0).count() + ")");
        fCon.setTooltip(new Tooltip("Ya tienen un resguardo vigente y además otros bienes a su nombre fuera de él"));
        fSin.setSelected(true);
        tipo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            refiltrar();
        });
        HBox tipos = new HBox(6, fSin, fCon);
        tipos.setAlignment(Pos.CENTER_LEFT);

        buscar.setId("buscarPersona");
        buscar.setPromptText("Nombre o área de la persona…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());

        lista.getStyleClass().addAll("trf-areas", "rsg-lista");
        lista.setCellFactory(lv -> new CeldaPersona(lv));
        lista.setPlaceholder(new Label("Nadie aquí"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> mostrar(p));
        VBox.setVgrow(lista, Priority.ALWAYS);

        Hyperlink todas = new Hyperlink("Marcar todas");
        todas.setTooltip(new Tooltip("Marca las de esta lista, salvo las de nombre repetido"));
        todas.setOnAction(e -> {
            visibles.stream().filter(p -> !repetidas.contains(p.nombre())).forEach(p -> marcadas.add(p.nombre()));
            actualizar();
        });
        Hyperlink ninguna = new Hyperlink("Ninguna");
        ninguna.setOnAction(e -> { marcadas.clear(); actualizar(); });
        HBox enlaces = new HBox(4, todas, new Label("·"), ninguna);
        enlaces.setAlignment(Pos.CENTER_LEFT);

        VBox izquierda = new VBox(8, TransferenciaArrastreDialog.paso(1, "Marca a las personas"), tipos, buscar, enlaces, lista);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(430);
        izquierda.setMinWidth(340);

        FontIcon icono = new FontIcon("mdi2a-account-search-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige a una persona para ver sus bienes");
        titulo.getStyleClass().add("empty-state-msg");
        vacio.getChildren().setAll(icono, titulo);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        nombre.getStyleClass().add("org-area-name");
        nombre.setWrapText(true);
        datos.getStyleClass().add("org-table-sub");
        datos.setWrapText(true);
        ScrollPane scroll = new ScrollPane(codigos);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        detalle.getChildren().setAll(nombre, datos, scroll);
        VBox.setVgrow(detalle, Priority.ALWAYS);

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        resumen.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resumen, Priority.ALWAYS);
        registrar.setGraphic(new FontIcon("mdi2c-clipboard-check-outline"));
        registrar.getStyleClass().add("btn-primary");
        registrar.setMinWidth(Region.USE_PREF_SIZE);
        registrar.setOnAction(e -> registrarMarcadas());
        HBox pie = new HBox(10, resumen, registrar);
        pie.setAlignment(Pos.CENTER_LEFT);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Revisa sus bienes");
        paso2.setPadding(Insets.EMPTY);
        VBox derecha = new VBox(8, paso2, vacio, detalle, new Separator(), pie);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        HBox cuerpo = new HBox(16, izquierda, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        refiltrar();
        mostrar(null);
        actualizar();
        Platform.runLater(buscar::requestFocus);
        dialog.showAndWait();
    }

    private void refiltrar() {
        List<Resguardante> base = personas.stream().filter(p -> fSin.isSelected() ? p.activos() == 0 : p.activos() > 0).toList();
        visibles.setAll(DialogUtil.coincidencias(base, Resguardante::texto, buscar.getText()));
    }

    private void mostrar(Resguardante p) {
        ver(vacio, p == null);
        ver(detalle, p != null);
        if (p == null) return;
        nombre.setText(p.nombre());
        datos.setText(FormatUtils.plural(p.sinResguardo().size(), "bien sin resguardo", "bienes sin resguardo")
            + (p.areas().isEmpty() ? "" : " · " + String.join(", ", p.areas()))
            + (repetidas.contains(p.nombre()) ? "\nTambién aparece con otra forma de su nombre: únela primero en Bienes › Más › Unir personas repetidas." : ""));
        codigos.getChildren().clear();
        p.sinResguardo().forEach(b -> {
            Label chip = new Label(b.getCodigo());
            chip.getStyleClass().add("trf-codigo");
            chip.setTooltip(new Tooltip(b.getNombre()));
            codigos.getChildren().add(chip);
        });
    }

    private List<Resguardante> elegidas() {
        return personas.stream().filter(p -> marcadas.contains(p.nombre())).toList();
    }

    private void actualizar() {
        List<Resguardante> e = elegidas();
        int bienes = e.stream().mapToInt(p -> p.sinResguardo().size()).sum();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (!e.isEmpty()) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(e.isEmpty() ? "Marca a las personas a registrar"
            : FormatUtils.plural(e.size(), "resguardo", "resguardos") + " · " + FormatUtils.plural(bienes, "bien", "bienes"));
        registrar.setDisable(e.isEmpty());
        lista.refresh();
    }

    private static List<ResguardoItem> itemsDe(Resguardante p) {
        List<ResguardoItem> items = new ArrayList<>();
        for (Producto b : p.sinResguardo()) {
            ResguardoItem it = new ResguardoItem();
            it.setProductoId(b.getId()); it.setProductoNombre(b.getNombre());
            it.setProductoCodigo(b.getCodigo()); it.setArea(b.getArea());
            it.setValorUnitario(b.getValorUnitario()); it.setNumeroSerie(b.getNumeroSerie());
            items.add(it);
        }
        return items;
    }

    private void registrarMarcadas() {
        List<Resguardante> e = elegidas();
        if (e.isEmpty()) return;
        if (!ConfirmacionUtil.confirmar("Registrar resguardos",
                "Se registran " + FormatUtils.plural(e.size(), "resguardo", "resguardos") + ", cada uno con su folio RSG, y quedan vigentes.\n\n"
                    + "Después se genera un solo PDF para imprimir y firmar.\n\n¿Continuar?")) return;
        registrar.setDisable(true);
        lista.setDisable(true);
        Scene propia = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(propia, "Registrando resguardos…",
            () -> {
                List<Resguardo> hechos = new ArrayList<>();
                List<String> fallidos = new ArrayList<>();
                // One failing (a bien taken meanwhile) doesn't stop the others.
                for (Resguardante p : e) {
                    try {
                        hechos.add(registro.crear(p, itemsDe(p)));
                    } catch (Exception ex) {
                        fallidos.add(p.nombre() + ": " + ex.getMessage());
                    }
                    Platform.runLater(() -> resumen.setText(hechos.size() + " de " + e.size() + " registrados…"));
                }
                return Map.entry(hechos, fallidos);
            },
            r -> terminar(r.getKey(), r.getValue()),
            ex -> {
                NotificacionUtil.error(propia, "No se pudo registrar: " + ex.getMessage());
                registrar.setDisable(false);
                lista.setDisable(false);
            });
    }

    private void terminar(List<Resguardo> hechos, List<String> fallidos) {
        dialog.close();
        if (alTerminar != null) alTerminar.accept(hechos.size());
        // After this window is really gone: one opened while its event loop unwinds freezes the app.
        Platform.runLater(() -> avisarYGenerar(hechos, fallidos));
    }

    private void avisarYGenerar(List<Resguardo> hechos, List<String> fallidos) {
        if (!fallidos.isEmpty()) {
            Alert al = new Alert(Alert.AlertType.WARNING);
            DialogUtil.applyOwner(al);
            al.setTitle("Algunos no se registraron");
            al.setHeaderText(FormatUtils.plural(fallidos.size(), "persona no se registró", "personas no se registraron"));
            al.setContentText(String.join("\n", fallidos.stream().limit(8).toList()));
            DialogUtil.applyStylesheet(al.getDialogPane());
            al.showAndWait();
        }
        if (hechos.isEmpty()) return;
        NotificacionUtil.exito(scene, FormatUtils.plural(hechos.size(), "resguardo registrado", "resguardos registrados"));
        DialogUtil.runAsyncWithProgress(scene, "Generando el PDF para firmar…",
            () -> {
                ResguardoService service = new ResguardoService();
                List<File> partes = new ArrayList<>();
                for (Resguardo r : hechos) partes.add(service.exportarPdf(r));
                return partes.size() == 1 ? partes.get(0) : ReporteService.unirPdfs(partes, "resguardos");
            },
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "Los resguardos quedaron registrados, pero no se pudo generar el PDF: " + e.getMessage()));
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private final class CeldaPersona extends ListCell<Resguardante> {
        private final CheckBox casilla = new CheckBox();
        private final Label quien = new Label();
        private final Label area = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaPersona(ListView<Resguardante> lv) {
            quien.getStyleClass().add("org-child-label");
            quien.setTextOverrun(OverrunStyle.ELLIPSIS);
            area.getStyleClass().add("org-table-sub");
            area.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox textos = new VBox(1, quien, area);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, casilla, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
            casilla.setOnAction(e -> {
                Resguardante p = getItem();
                if (p == null) return;
                if (!marcadas.remove(p.nombre())) marcadas.add(p.nombre());
                actualizar();
            });
        }

        @Override protected void updateItem(Resguardante p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); return; }
            quien.setText(p.nombre());
            List<String> areas = p.areas();
            area.setText(areas.size() == 1 ? areas.get(0) : areas.isEmpty() ? "Sin área" : areas.size() + " áreas");
            boolean repetida = repetidas.contains(p.nombre());
            casilla.setDisable(repetida);
            casilla.setSelected(marcadas.contains(p.nombre()));
            casilla.setAccessibleText(p.nombre());
            boolean dos = !repetida && com.sibim.service.CalidadDatosService.dosPersonas(p.nombre());
            cuenta.getStyleClass().setAll(repetida || dos ? List.of("cell-badge", "cell-badge-warning") : List.of("org-area-count"));
            String bienesDeEl = FormatUtils.plural(p.sinResguardo().size(), "bien", "bienes");
            cuenta.setText(repetida ? "Nombre repetido" : dos ? "2 personas · " + bienesDeEl : bienesDeEl);
            cuenta.setTooltip(dos ? new Tooltip("Este nombre trae a dos personas: el resguardo saldría a nombre de ambas") : null);
            setGraphic(caja);
        }
    }
}
