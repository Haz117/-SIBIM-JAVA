package com.sibim.controller.dialogs;

import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.service.ResguardoService;
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

import java.util.*;
import java.util.function.Consumer;

/**
 * Resguardos › Más › Juntar resguardos: the people with more than one resguardo in
 * force in the same área (3-nov-2026: one with 15, most of one bien each, from transfers
 * made one at a time), and those resguardos with a box each. "Juntar en uno" cancels the
 * marked ones and makes one with all their bienes, whose PDF is the one to sign.
 */
public final class JuntarResguardosDialog {

    /** What joins them: the service, or a stand-in in the tests. */
    @FunctionalInterface
    interface Junta { Resguardo juntar(List<Resguardo> suyos) throws Exception; }

    /** One person in one área with their resguardos in force, newest first. */
    record Grupo(String nombre, String area, List<Resguardo> resguardos) {
        int bienes() { return resguardos.stream().mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum(); }
        String texto() { return nombre + " " + Objects.requireNonNullElse(area, ""); }
    }

    private final List<Grupo> grupos;
    private final ObservableList<Grupo> visibles = FXCollections.observableArrayList();
    private final ListView<Grupo> lista = new ListView<>(visibles);
    private final TextField buscar = new TextField();
    private final Label nombre = new Label();
    private final Label datos = new Label();
    private final VBox filas = new VBox(6);
    private final VBox vacio = new VBox(8);
    private final VBox detalle = new VBox(8);
    private final Label resumen = new Label();
    private final Button juntar = new Button("Juntar en uno");
    private final Set<String> marcados = new LinkedHashSet<>();
    private final Junta junta;
    private final Scene scene;
    private final Consumer<Resguardo> alJuntar;
    private Grupo actual;
    private Dialog<ButtonType> dialog;

    private JuntarResguardosDialog(List<Resguardo> resguardos, Scene scene, Junta junta, Consumer<Resguardo> alJuntar) {
        this.grupos = new ArrayList<>(agrupar(resguardos));
        this.scene = scene;
        this.junta = junta;
        this.alJuntar = alJuntar;
    }

    /** @param alJuntar run with each new resguardo (to refresh the list behind) */
    public static void show(List<Resguardo> resguardos, Scene scene, Consumer<Resguardo> alJuntar) {
        ResguardoService service = new ResguardoService();
        new JuntarResguardosDialog(resguardos, scene, service::juntar, alJuntar).abrir();
    }

    static void show(List<Resguardo> resguardos, Scene scene, Junta junta, Consumer<Resguardo> alJuntar) {
        new JuntarResguardosDialog(resguardos, scene, junta, alJuntar).abrir();
    }

    /** Who has two or more resguardos in force in one área: most resguardos first. */
    static List<Grupo> agrupar(List<Resguardo> resguardos) {
        Map<String, List<Resguardo>> por = new LinkedHashMap<>();
        for (Resguardo r : resguardos) {
            if (!r.isActivo() || r.getResguardanteNombre() == null || r.getResguardanteNombre().isBlank()) continue;
            por.computeIfAbsent(ResguardoService.clavePersona(r.getResguardanteNombre()) + "|" + r.getResguardanteArea(),
                k -> new ArrayList<>()).add(r);
        }
        Comparator<Resguardo> nuevosPrimero = Comparator.comparing(Resguardo::getCreadoEn,
            Comparator.nullsLast(Comparator.reverseOrder()));
        return por.values().stream().filter(l -> l.size() > 1)
            .map(l -> {
                List<Resguardo> orden = l.stream().sorted(nuevosPrimero).toList();
                return new Grupo(orden.get(0).getResguardanteNombre(), orden.get(0).getResguardanteArea(), orden);
            })
            .sorted(Comparator.comparingInt((Grupo g) -> -g.resguardos().size()).thenComparing(Grupo::nombre))
            .toList();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Juntar resguardos");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1100, pantalla.getWidth() * 0.94), Math.min(700, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-call-merge", "Juntar resguardos",
            "Una persona con varios resguardos en la misma área: junta los que elijas en uno solo para firmar",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        buscar.setId("buscarPersona");
        buscar.setPromptText("Nombre o área de la persona…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());

        lista.getStyleClass().addAll("trf-areas", "rsg-lista");
        lista.setCellFactory(lv -> new CeldaGrupo(lv));
        lista.setPlaceholder(new Label("Nadie tiene más de un resguardo vigente en la misma área"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> mostrar(g));
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox izquierda = new VBox(8, TransferenciaArrastreDialog.paso(1, "Elige a la persona"), buscar, lista);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(430);
        izquierda.setMinWidth(340);

        FontIcon icono = new FontIcon("mdi2a-account-search-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige a una persona para ver sus resguardos");
        titulo.getStyleClass().add("empty-state-msg");
        vacio.getChildren().setAll(icono, titulo);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        nombre.getStyleClass().add("org-area-name");
        nombre.setWrapText(true);
        datos.getStyleClass().add("org-table-sub");
        datos.setWrapText(true);
        ScrollPane scroll = new ScrollPane(filas);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        detalle.getChildren().setAll(nombre, datos, scroll);
        VBox.setVgrow(detalle, Priority.ALWAYS);

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        resumen.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resumen, Priority.ALWAYS);
        juntar.setGraphic(new FontIcon("mdi2c-call-merge"));
        juntar.getStyleClass().add("btn-primary");
        juntar.setMinWidth(Region.USE_PREF_SIZE);
        juntar.setOnAction(e -> juntarMarcados());
        HBox pie = new HBox(10, resumen, juntar);
        pie.setAlignment(Pos.CENTER_LEFT);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Marca los que se juntan");
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
        Platform.runLater(buscar::requestFocus);
        dialog.showAndWait();
    }

    private void refiltrar() {
        visibles.setAll(DialogUtil.coincidencias(grupos, Grupo::texto, buscar.getText()));
    }

    private void mostrar(Grupo g) {
        actual = g;
        ver(vacio, g == null);
        ver(detalle, g != null);
        marcados.clear();
        filas.getChildren().clear();
        if (g == null) { actualizar(); return; }
        nombre.setText(g.nombre());
        datos.setText(FormatUtils.plural(g.resguardos().size(), "resguardo vigente", "resguardos vigentes")
            + " · " + FormatUtils.plural(g.bienes(), "bien", "bienes") + " · " + Objects.requireNonNullElse(g.area(), "Sin área")
            + "\nLos que juntes se cancelan (quedan como se firmaron) y se hace uno nuevo con todos sus bienes.");
        for (Resguardo r : g.resguardos()) {
            marcados.add(r.getId());
            filas.getChildren().add(fila(r));
        }
        actualizar();
    }

    private Node fila(Resguardo r) {
        CheckBox casilla = new CheckBox();
        casilla.setSelected(true);
        casilla.setAccessibleText(r.getNumero());
        casilla.setOnAction(e -> {
            if (casilla.isSelected()) marcados.add(r.getId()); else marcados.remove(r.getId());
            actualizar();
        });
        List<ResguardoItem> items = r.getItems() != null ? r.getItems() : List.of();
        Label folio = new Label(r.getNumero());
        folio.getStyleClass().add("org-child-label");
        Label cuando = new Label(FormatUtils.plural(items.size(), "bien", "bienes")
            + (r.getCreadoEn() != null ? " · " + FormatUtils.formatDate(r.getCreadoEn().toLocalDate()) : ""));
        cuando.getStyleClass().add("org-table-sub");
        FlowPane codigos = new FlowPane(5, 5);
        items.stream().limit(8).forEach(it -> {
            Label chip = new Label(Objects.requireNonNullElse(it.getProductoCodigo(), "—"));
            chip.getStyleClass().add("trf-codigo");
            chip.setTooltip(new Tooltip(it.getProductoNombre()));
            codigos.getChildren().add(chip);
        });
        if (items.size() > 8) {
            Label mas = new Label("+" + (items.size() - 8) + " más");
            mas.getStyleClass().add("table-count-label");
            codigos.getChildren().add(mas);
        }
        VBox textos = new VBox(3, new HBox(8, folio, cuando), codigos);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox fila = new HBox(10, casilla, textos);
        fila.setAlignment(Pos.TOP_LEFT);
        fila.getStyleClass().add("rsg-tarjeta");
        fila.setPadding(new Insets(8, 10, 8, 10));
        return fila;
    }

    private List<Resguardo> elegidos() {
        return actual == null ? List.of() : actual.resguardos().stream().filter(r -> marcados.contains(r.getId())).toList();
    }

    private void actualizar() {
        List<Resguardo> e = elegidos();
        int bienes = e.stream().mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (e.size() > 1) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(actual == null ? "Elige a una persona"
            : e.size() < 2 ? "Marca al menos dos resguardos"
            : "Se juntan " + e.size() + " resguardos en uno con " + FormatUtils.plural(bienes, "bien", "bienes"));
        juntar.setDisable(e.size() < 2);
        lista.refresh();
    }

    private void juntarMarcados() {
        List<Resguardo> e = elegidos();
        Grupo g = actual;
        if (g == null || e.size() < 2) return;
        String folios = e.stream().map(Resguardo::getNumero).reduce((a, b) -> a + ", " + b).orElse("");
        if (!ConfirmacionUtil.confirmar("Juntar en uno",
                "Se cancelan " + e.size() + " resguardos de " + g.nombre() + " (" + folios + ") y se hace uno nuevo con "
                + FormatUtils.plural(e.stream().mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum(), "bien", "bienes")
                + ".\n\nLos cancelados quedan como se firmaron. Imprime el nuevo para que lo firme.\n\n¿Continuar?")) return;
        juntar.setDisable(true);
        Scene propia = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(propia, "Juntando resguardos…",
            () -> junta.juntar(e),
            nuevo -> {
                NotificacionUtil.exito(propia, "Resguardo " + nuevo.getNumero() + " con "
                    + FormatUtils.plural(nuevo.getItems() == null ? 0 : nuevo.getItems().size(), "bien", "bienes"));
                // The group now has the new one plus the ones left unmarked.
                List<Resguardo> quedan = new ArrayList<>();
                quedan.add(nuevo);
                g.resguardos().stream().filter(r -> !marcados.contains(r.getId())).forEach(quedan::add);
                int i = grupos.indexOf(g);
                if (quedan.size() > 1 && i >= 0) grupos.set(i, new Grupo(g.nombre(), g.area(), quedan));
                else grupos.remove(g);
                refiltrar();
                mostrar(null);
                if (alJuntar != null) alJuntar.accept(nuevo);
                if (ConfirmacionUtil.confirmar("Resguardo " + nuevo.getNumero(), "¿Abrir el PDF del resguardo nuevo para firmarlo?"))
                    DialogUtil.runAsyncWithProgress(propia, "Generando PDF…",
                        () -> new ResguardoService().exportarPdf(nuevo),
                        file -> DialogUtil.showExportResultDialog(propia, file),
                        ex -> NotificacionUtil.error(propia, "No se pudo generar el PDF del resguardo"));
            },
            ex -> {
                juntar.setDisable(false);
                NotificacionUtil.error(propia, "No se juntaron: " + (ex.getMessage() != null ? ex.getMessage() : "error desconocido"));
            });
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static final class CeldaGrupo extends ListCell<Grupo> {
        private final Label quien = new Label();
        private final Label area = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaGrupo(ListView<Grupo> lv) {
            quien.getStyleClass().add("org-child-label");
            quien.setTextOverrun(OverrunStyle.ELLIPSIS);
            area.getStyleClass().add("org-table-sub");
            area.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox textos = new VBox(1, quien, area);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(Grupo g, boolean empty) {
            super.updateItem(g, empty);
            setText(null);
            if (empty || g == null) { setGraphic(null); return; }
            quien.setText(g.nombre());
            area.setText(Objects.requireNonNullElse(g.area(), "Sin área"));
            cuenta.setText(g.resguardos().size() + " resguardos · " + FormatUtils.plural(g.bienes(), "bien", "bienes"));
            setGraphic(caja);
        }
    }
}
