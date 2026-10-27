package com.sibim.controller.dialogs;

import com.sibim.config.AreaCodigos;
import com.sibim.config.Areas;
import com.sibim.controller.FiltroArea;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.service.TransferenciaRapidaService;
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
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Transferir arrastrando" (Movimientos, Patrimonio only): the bienes of one área
 * on the left, every other área as a card on the right, grouped by secretaría —
 * the same board as Organigrama › Acomodar. Until an origen is picked the left
 * column lists the áreas that have bienes; the cards stay dimmed until there is
 * something selected to take there. Dropping bienes on a card asks for the motivo
 * and, if wanted, who receives them there; TransferenciaRapidaService does the
 * rest (out of the old resguardo, transfer with the next free número, new
 * resguardo). What was moved in this window is listed at the bottom.
 */
public final class TransferenciaArrastreDialog {

    private static final DataFormat BIENES = new DataFormat("application/x-sibim-bienes");

    private final ProductoService productoService = new ProductoService();
    private final TransferenciaRapidaService transferencias = new TransferenciaRapidaService();
    private final Runnable alTransferir;

    private Map<String, List<Producto>> porArea = Map.of();
    private final ComboBox<String> origen = new ComboBox<>();
    /** The áreas with bienes, to pick the origen with one click. */
    private final ListView<String> areasOrigen = new ListView<>();
    private final Hyperlink cambiarOrigen = new Hyperlink("Cambiar de área");
    private final TextField buscar = new TextField();
    private final ObservableList<Producto> visibles = FXCollections.observableArrayList();
    private final ListView<Producto> lista = new ListView<>(visibles);
    private final TextField buscarArea = new TextField();
    private final VBox tablero = new VBox(14);
    private final List<HBox> tarjetas = new ArrayList<>();
    private final Label ayudaDestino = new Label();
    private final Label seleccion = new Label();
    /** Bienes chosen in Bienes that belong to another área: they go in another round. */
    private final Label otraArea = new Label();
    /** What was transferred in this window, newest first. */
    private final VBox historial = new VBox(8);
    private final VBox panelHistorial = new VBox(6);
    private final Label tituloHistorial = new Label();
    private int transferidos = 0;
    private final List<Node> pasoBienes = new ArrayList<>();
    private Dialog<Void> dialog;

    private TransferenciaArrastreDialog(Runnable alTransferir) { this.alTransferir = alTransferir; }

    /** @param alTransferir run after each batch, to refresh the screen behind */
    public static void show(List<Producto> productos, Runnable alTransferir) {
        show(productos, List.of(), alTransferir);
    }

    /** @param preseleccion ids of bienes to start with selected (Bienes › Transferir):
     *  their área becomes the origen; ones from another área are left out */
    public static void show(List<Producto> productos, List<String> preseleccion, Runnable alTransferir) {
        new TransferenciaArrastreDialog(alTransferir).abrir(productos, preseleccion);
    }

    private void abrir(List<Producto> productos, List<String> preseleccion) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Transferir arrastrando");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // Also on a 1366×768 laptop: never bigger than the screen.
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1180, pantalla.getWidth() * 0.94),
            Math.min(720, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-drag-variant", "Transferir arrastrando",
            "Elige el área de origen, selecciona bienes y arrástralos al área que los recibe",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        origen.setPromptText("Buscar el área de origen…");
        origen.setMaxWidth(Double.MAX_VALUE);
        origen.getStyleClass().add("form-input");
        FiltroArea.conIconos(origen);

        areasOrigen.getStyleClass().add("trf-areas");
        areasOrigen.setCellFactory(lv -> new CeldaArea(lv));
        areasOrigen.setPlaceholder(new Label("No hay áreas con bienes"));
        VBox.setVgrow(areasOrigen, Priority.ALWAYS);
        areasOrigen.setOnMouseClicked(e -> elegirOrigen(areasOrigen.getSelectionModel().getSelectedItem()));
        areasOrigen.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) elegirOrigen(areasOrigen.getSelectionModel().getSelectedItem());
        });
        cambiarOrigen.setOnAction(e -> origen.setValue(null));
        cambiarOrigen.setTooltip(new Tooltip("Volver a la lista de áreas para elegir otro origen"));

        buscar.setPromptText("Filtrar por código, nombre o resguardante…");
        buscar.getStyleClass().add("form-input");
        lista.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lista.getStyleClass().add("trf-lista");
        lista.setPlaceholder(new Label("Ningún bien coincide con el filtro"));
        lista.setCellFactory(lv -> new CeldaBien(lv));
        VBox.setVgrow(lista, Priority.ALWAYS);
        seleccion.getStyleClass().add("table-count-label");
        seleccion.setWrapText(true);
        lista.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<Producto>) c -> actualizarSeleccion());

        lista.setOnDragDetected(e -> {
            List<Producto> sel = new ArrayList<>(lista.getSelectionModel().getSelectedItems());
            if (sel.isEmpty()) return;
            Dragboard db = lista.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.put(BIENES, sel.stream().map(Producto::getId).collect(Collectors.joining("\n")));
            cc.putString(FormatUtils.plural(sel.size(), "bien", "bienes"));
            db.setContent(cc);
            db.setDragView(etiquetaArrastre(sel.size()));
            // While dragging, every card that can receive them is outlined.
            tablero.getStyleClass().add("trf-arrastrando");
            e.consume();
        });
        lista.setOnDragDone(e -> tablero.getStyleClass().remove("trf-arrastrando"));

        origen.valueProperty().addListener((o, a, b) -> { filtrar(); construirTablero(); mostrarPasos(); });
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        buscarArea.setPromptText("Buscar el área o la secretaría que los recibe…");
        buscarArea.getStyleClass().add("form-input");
        buscarArea.textProperty().addListener((o, a, b) -> construirTablero());

        Hyperlink todos = new Hyperlink("Todos");
        todos.setOnAction(e -> lista.getSelectionModel().selectAll());
        todos.setTooltip(new Tooltip("Seleccionar todos los bienes de la lista (los que quedan con el filtro)"));
        Hyperlink ninguno = new Hyperlink("Ninguno");
        ninguno.setOnAction(e -> lista.getSelectionModel().clearSelection());
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barraSeleccion = new HBox(4, seleccion, hueco, todos, new Label("·"), ninguno);
        barraSeleccion.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seleccion, Priority.SOMETIMES);

        otraArea.getStyleClass().addAll("field-hint", "field-hint-warn");
        otraArea.setWrapText(true);
        otraArea.setVisible(false);
        otraArea.setManaged(false);
        HBox paso1 = paso(1, "Área de origen");
        Region hueco1 = new Region();
        HBox.setHgrow(hueco1, Priority.ALWAYS);
        paso1.getChildren().addAll(hueco1, cambiarOrigen);
        HBox paso2 = paso(2, "Selecciona los bienes");
        pasoBienes.addAll(List.of(paso2, buscar, barraSeleccion, lista));
        VBox izquierda = new VBox(8, paso1, origen, areasOrigen, paso2, buscar, barraSeleccion, otraArea, lista);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(430);
        izquierda.setMinWidth(340);

        tablero.setPadding(new Insets(4, 4, 8, 4));
        ScrollPane scroll = new ScrollPane(tablero);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ayudaDestino.getStyleClass().add("table-count-label");
        VBox derecha = new VBox(8, paso(3, "Llévalos al área que los recibe"), ayudaDestino, buscarArea, scroll);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        HBox cuerpo = new HBox(16, izquierda, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);

        // What was moved here, under the board; hidden until the first transfer.
        FontIcon iconoHistorial = new FontIcon("mdi2h-history");
        iconoHistorial.getStyleClass().add("trf-persona-icono");
        tituloHistorial.setGraphic(iconoHistorial);
        tituloHistorial.getStyleClass().add("trf-paso-titulo");
        ScrollPane scrollHistorial = new ScrollPane(historial);
        scrollHistorial.setFitToWidth(true);
        scrollHistorial.getStyleClass().add("dialog-scroll");
        scrollHistorial.setPrefViewportHeight(118);
        scrollHistorial.setMinHeight(Region.USE_PREF_SIZE);
        panelHistorial.getChildren().addAll(tituloHistorial, scrollHistorial);
        panelHistorial.getStyleClass().add("trf-panel");
        // The board above gives up the room, not this list.
        panelHistorial.setMinHeight(Region.USE_PREF_SIZE);
        panelHistorial.setVisible(false);
        panelHistorial.setManaged(false);

        // The margin goes on an inner box: the dialog stylesheet sets the content's
        // own padding to 0 (the header runs edge to edge), and it won over setPadding.
        VBox debajo = new VBox(12, cuerpo, panelHistorial);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        VBox content = new VBox(12, header, debajo);
        dialog.getDialogPane().setContent(content);

        cargar(productos);
        String areaElegida = productos.stream().filter(p -> preseleccion.contains(p.getId()))
            .map(Producto::getArea).filter(porArea::containsKey).findFirst().orElse(null);
        if (areaElegida != null) {
            origen.setValue(areaElegida);
            for (Producto p : visibles)
                if (preseleccion.contains(p.getId())) lista.getSelectionModel().select(p);
            long deOtraArea = preseleccion.size() - lista.getSelectionModel().getSelectedItems().size();
            if (deOtraArea > 0) {
                otraArea.setText(FormatUtils.plural(deOtraArea, "de los bienes que elegiste es", "de los bienes que elegiste son")
                    + " de otra área: transfiérelos en otra vuelta, eligiendo su área como origen.");
                otraArea.setVisible(true);
                otraArea.setManaged(true);
            }
            Platform.runLater(() -> {
                lista.requestFocus();
                if (!lista.getSelectionModel().getSelectedIndices().isEmpty())
                    lista.scrollTo(lista.getSelectionModel().getSelectedIndices().get(0));
            });
        } else {
            // On the list, not on the search: that one opens its drop-down on focus,
            // over this same list. Arrows and Enter pick from here.
            Platform.runLater(areasOrigen::requestFocus);
        }
        mostrarPasos();
        dialog.showAndWait();
    }

    private void elegirOrigen(String area) {
        if (area != null) origen.setValue(area);
    }

    /** Step 1 shows the áreas until one is picked; then step 2, its bienes. */
    private void mostrarPasos() {
        boolean hay = origen.getValue() != null;
        areasOrigen.setVisible(!hay);
        areasOrigen.setManaged(!hay);
        for (Node n : pasoBienes) { n.setVisible(hay); n.setManaged(hay); }
        cambiarOrigen.setVisible(hay);
        cambiarOrigen.setManaged(hay);
        if (!hay) { otraArea.setVisible(false); otraArea.setManaged(false); }
    }

    private void cargar(List<Producto> productos) {
        String elegido = origen.getValue();
        porArea = productos.stream().filter(p -> p.getArea() != null && p.getFechaBaja() == null)
            .collect(Collectors.groupingBy(Producto::getArea));
        List<String> conBienes = Areas.getAllAreaNames().stream().filter(porArea::containsKey).toList();
        DialogUtil.makeFilterable(origen, new ArrayList<>(conBienes));
        areasOrigen.getItems().setAll(conBienes);
        if (elegido != null && conBienes.contains(elegido)) origen.setValue(elegido);
        filtrar();
        construirTablero();
    }

    private void filtrar() {
        String area = origen.getValue();
        String q = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase();
        List<Producto> bienes = area != null ? porArea.getOrDefault(area, List.of()) : List.of();
        visibles.setAll(bienes.stream()
            .filter(p -> q.isEmpty() || (p.getCodigo() + " " + p.getNombre() + " "
                + Objects.requireNonNullElse(p.getResguardante(), "")).toLowerCase().contains(q))
            .sorted(Comparator.comparingInt(TransferenciaArrastreDialog::numero))
            .toList());
        ((Label) lista.getPlaceholder()).setText(area != null && bienes.isEmpty()
            ? "Ya no quedan bienes en " + area : "Ningún bien coincide con el filtro");
        actualizarSeleccion();
    }

    private void actualizarSeleccion() {
        int n = lista.getSelectionModel().getSelectedItems().size();
        seleccion.setText(n == 0
            ? (visibles.isEmpty() ? "" : "Ctrl o Mayús + clic para elegir varios")
            : FormatUtils.plural(n, "seleccionado", "seleccionados") + " de " + visibles.size());
        seleccion.getStyleClass().remove("trf-seleccion-activa");
        if (n > 0) seleccion.getStyleClass().add("trf-seleccion-activa");
        actualizarDestinos();
    }

    /** The cards light up once there is something to take to them. */
    private void actualizarDestinos() {
        int n = lista.getSelectionModel().getSelectedItems().size();
        for (HBox t : tarjetas) {
            t.getStyleClass().remove("trf-tarjeta-inactiva");
            if (n == 0) t.getStyleClass().add("trf-tarjeta-inactiva");
        }
        ayudaDestino.setText(origen.getValue() == null ? "Primero elige a la izquierda el área de donde salen los bienes."
            : n == 0 ? "Selecciona bienes a la izquierda: las áreas se activan para recibirlos."
            : "Arrastra " + (n == 1 ? "el seleccionado" : "los " + n + " seleccionados") + " a una tarjeta, o haz clic en ella.");
    }

    /** "①  Área de origen": the three steps of the board, in order. */
    private static HBox paso(int numero, String texto) {
        Label n = new Label(String.valueOf(numero));
        n.getStyleClass().add("trf-paso");
        Label t = new Label(texto);
        t.getStyleClass().add("trf-paso-titulo");
        HBox fila = new HBox(8, n, t);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setPadding(new Insets(numero == 1 || numero == 3 ? 0 : 6, 0, 0, 0));
        return fila;
    }

    /** What follows the pointer while dragging: "3 bienes". */
    private javafx.scene.image.Image etiquetaArrastre(int n) {
        Label pastilla = new Label(FormatUtils.plural(n, "bien", "bienes"), new FontIcon("mdi2p-package-variant-closed"));
        pastilla.getStyleClass().add("trf-arrastre");
        javafx.scene.Group g = new javafx.scene.Group(pastilla);
        Scene s = new Scene(g);
        s.setFill(javafx.scene.paint.Color.TRANSPARENT);
        s.getStylesheets().setAll(dialog.getDialogPane().getStylesheets());
        g.applyCss();
        g.layout();
        javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
        sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
        return pastilla.snapshot(sp, null);
    }

    private List<Producto> seleccionados() {
        return new ArrayList<>(lista.getSelectionModel().getSelectedItems());
    }

    /** The organigrama's groups: Presidencia with its direcciones, each secretaría
     *  with its own, the organismos autónomos; any área left over goes last. */
    private static List<Map.Entry<String, List<String>>> grupos() {
        List<Map.Entry<String, List<String>>> g = new ArrayList<>();
        Set<String> puestas = new HashSet<>();
        List<String> presidencia = new ArrayList<>();
        presidencia.add(Areas.PRESIDENCIA);
        presidencia.addAll(Areas.direccionesPresidencia());
        g.add(Map.entry(Areas.PRESIDENCIA, presidencia));
        for (Areas.SecretariaInfo s : Areas.secretarias()) {
            List<String> areas = new ArrayList<>();
            areas.add(s.nombre());
            areas.addAll(s.direcciones());
            g.add(Map.entry(s.nombre(), areas));
        }
        if (!Areas.autonomos().isEmpty()) g.add(Map.entry("Organismos autónomos", Areas.autonomos()));
        g.forEach(e -> puestas.addAll(e.getValue()));
        List<String> resto = Areas.getAllAreaNames().stream().filter(a -> !puestas.contains(a)).toList();
        if (!resto.isEmpty()) g.add(Map.entry("Otras áreas", resto));
        return g;
    }

    private void construirTablero() {
        String q = buscarArea.getText() == null ? "" : buscarArea.getText().trim().toLowerCase();
        tablero.getChildren().clear();
        tarjetas.clear();
        for (Map.Entry<String, List<String>> grupo : grupos()) {
            boolean grupoCoincide = grupo.getKey().toLowerCase().contains(q);
            List<String> areas = grupo.getValue().stream()
                .filter(a -> !a.equals(origen.getValue()))
                .filter(a -> q.isEmpty() || grupoCoincide || a.toLowerCase().contains(q))
                .toList();
            if (areas.isEmpty()) continue;
            Label titulo = new Label(grupo.getKey().toUpperCase());
            titulo.getStyleClass().add("trf-grupo");
            FlowPane fichas = new FlowPane(12, 12);
            for (String area : areas) {
                HBox t = tarjeta(area);
                tarjetas.add(t);
                fichas.getChildren().add(t);
            }
            tablero.getChildren().add(new VBox(8, titulo, fichas));
        }
        if (tablero.getChildren().isEmpty()) {
            Label nada = new Label("Ningún área coincide con «" + buscarArea.getText().trim() + "»");
            nada.getStyleClass().add("org-acomodo-vacio");
            tablero.getChildren().add(nada);
        }
        actualizarDestinos();
    }

    private HBox tarjeta(String area) {
        Label nombre = new Label(area);
        nombre.getStyleClass().add("org-area-name");
        nombre.setWrapText(true);
        int n = porArea.getOrDefault(area, List.of()).size();
        Label cuenta = new Label(n == 0 ? "Sin bienes" : FormatUtils.plural(n, "bien", "bienes"));
        cuenta.getStyleClass().add("org-table-sub");
        FontIcon icono = FiltroArea.icono(area);
        icono.getStyleClass().add("trf-tarjeta-icono");
        StackPane fondoIcono = new StackPane(icono);
        fondoIcono.getStyleClass().add("trf-tarjeta-icono-fondo");
        fondoIcono.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox textos = new VBox(2, nombre, cuenta);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox t = new HBox(10, fondoIcono, textos);
        t.getStyleClass().addAll("org-acomodo-col", "trf-tarjeta");
        t.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(t, new Tooltip("Suelta aquí los bienes, o haz clic para llevar los seleccionados a " + area));
        // Click as well as drop: quicker for a whole list, and it works from the keyboard.
        t.setOnMouseClicked(e -> {
            List<Producto> sel = seleccionados();
            if (sel.isEmpty()) NotificacionUtil.info(dialog.getDialogPane().getScene(),
                origen.getValue() == null ? "Primero elige el área de origen y sus bienes"
                    : "Primero selecciona a la izquierda los bienes que van a " + area);
            else confirmar(sel, area);
        });
        com.sibim.util.AccessibilityUtils.asButton(t, "Transferir los bienes seleccionados a " + area);
        t.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(BIENES)) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        t.setOnDragEntered(e -> { if (e.getDragboard().hasContent(BIENES)) t.getStyleClass().add("org-acomodo-col-drop"); });
        t.setOnDragExited(e -> t.getStyleClass().remove("org-acomodo-col-drop"));
        t.setOnDragDropped(e -> {
            Object ids = e.getDragboard().getContent(BIENES);
            e.setDropCompleted(ids != null);
            e.consume();
            if (ids == null) return;
            Set<String> elegidos = new HashSet<>(Arrays.asList(ids.toString().split("\n")));
            List<Producto> bienes = porArea.getOrDefault(origen.getValue(), List.of()).stream()
                .filter(p -> elegidos.contains(p.getId())).toList();
            if (!bienes.isEmpty()) Platform.runLater(() -> confirmar(bienes, area));
        });
        return t;
    }

    private void confirmar(List<Producto> elegidos, String destino) {
        // In the área's own order, whatever order they were clicked in: the lowest
        // número takes the first free one there.
        List<Producto> bienes = elegidos.stream()
            .sorted(Comparator.comparingInt(TransferenciaArrastreDialog::numero).thenComparing(Producto::getCodigo,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Transferir a " + destino);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal-bold", "Transferir a " + destino,
            FormatUtils.plural(bienes.size(), "bien", "bienes") + " de " + origen.getValue(),
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        // The número each one will take there, as the transfer will pick it: the
        // lowest free one, one after the other.
        List<String> ocupados = porArea.values().stream().flatMap(List::stream).map(Producto::getCodigo)
            .collect(Collectors.toCollection(ArrayList::new));
        VBox lista = new VBox(5);
        for (int i = 0; i < bienes.size(); i++) {
            Producto p = bienes.get(i);
            String nuevo = AreaCodigos.tienePrefijo(destino) ? AreaCodigos.siguienteCodigo(destino, ocupados) : p.getCodigo();
            ocupados.add(nuevo);
            if (i >= 6) continue;
            Label antes = new Label(p.getCodigo());
            antes.getStyleClass().add("trf-codigo");
            antes.setMinWidth(Region.USE_PREF_SIZE);
            FontIcon flecha = new FontIcon("mdi2a-arrow-right");
            flecha.getStyleClass().add("trf-persona-icono");
            Label despues = new Label(nuevo);
            despues.getStyleClass().addAll("trf-codigo", "trf-codigo-nuevo");
            despues.setMinWidth(Region.USE_PREF_SIZE);
            Label nombre = new Label(p.getNombre());
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox fila = new HBox(6, antes, flecha, despues, nombre);
            fila.setAlignment(Pos.CENTER_LEFT);
            lista.getChildren().add(fila);
        }
        if (bienes.size() > 6) {
            Label mas = new Label("… y " + (bienes.size() - 6) + " más");
            mas.getStyleClass().add("table-count-label");
            lista.getChildren().add(mas);
        }

        GridPane form = DialogUtil.formGrid(150);
        TextField motivo = new TextField();
        motivo.setPromptText("Ej. reasignación, cambio de oficina");
        motivo.getStyleClass().add("form-input");
        ComboBox<String> resguardante = new ComboBox<>(FXCollections.observableArrayList(
            porArea.getOrDefault(destino, List.of()).stream().map(Producto::getResguardante)
                .filter(r -> r != null && !r.isBlank()).distinct().sorted().toList()));
        resguardante.setEditable(true);
        resguardante.setPromptText("Quién los recibe (opcional)");
        resguardante.setMaxWidth(Double.MAX_VALUE);
        resguardante.getStyleClass().add("form-input");
        TextField cargo = new TextField();
        cargo.setPromptText("Cargo (opcional)");
        cargo.getStyleClass().add("form-input");
        int row = 0;
        form.add(DialogUtil.fieldLabel("Motivo *"), 0, row);              form.add(motivo, 1, row++);
        form.add(DialogUtil.fieldLabel("Nuevo resguardante"), 0, row);    form.add(resguardante, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"), 0, row);                 form.add(cargo, 1, row++);

        Label nota = new Label("Cada bien sale de su resguardo actual y toma el número que ves arriba, el siguiente libre de "
            + destino + ". Si escribes quién los recibe, se le crea un resguardo con todos ellos.");
        nota.setWrapText(true);
        nota.getStyleClass().add("table-count-label");

        // Margins, not the content's padding (the dialog stylesheet sets it to 0);
        // 22 at the sides, like the form.
        VBox.setMargin(lista, new Insets(14, 22, 0, 22));
        VBox.setMargin(nota, new Insets(0, 22, 14, 22));
        VBox content = new VBox(0, header, lista, form, nota);
        dlg.getDialogPane().setContent(content);
        Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Transferir");
        ok.getStyleClass().add("dialog-ok-btn");
        ok.disableProperty().bind(motivo.textProperty().isEmpty());
        Platform.runLater(motivo::requestFocus);

        dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            String quien = resguardante.getEditor().getText();
            String desde = origen.getValue();
            Scene scene = dialog.getDialogPane().getScene();
            DialogUtil.runAsyncWithProgress(scene, "Transfiriendo " + FormatUtils.plural(bienes.size(), "bien", "bienes") + "…",
                () -> transferencias.transferir(bienes, destino, motivo.getText(), quien, cargo.getText()),
                r -> terminado(r, desde, destino, scene),
                ex -> NotificacionUtil.error(scene, "No se pudo transferir: "
                    + (ex.getMessage() != null ? ex.getMessage() : "error desconocido")));
        });
    }

    private void terminado(TransferenciaRapidaService.Resultado r, String desde, String destino, Scene scene) {
        if (!r.hechos().isEmpty()) {
            NotificacionUtil.exito(scene, FormatUtils.plural(r.hechos().size(), "bien transferido", "bienes transferidos")
                + " a " + destino + (r.resguardo() != null ? " · resguardo " + r.resguardo().getNumero() : ""));
            anotar(r, desde, destino, scene);
        }
        if (!r.errores().isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            DialogUtil.applyOwner(a);
            a.setTitle("Algunos bienes no se movieron");
            a.setHeaderText(FormatUtils.plural(r.errores().size(), "bien no se pudo transferir", "bienes no se pudieron transferir"));
            a.setContentText(String.join("\n", r.errores()));
            DialogUtil.applyStylesheet(a.getDialogPane());
            a.showAndWait();
        }
        if (alTransferir != null) alTransferir.run();
        if (r.resguardo() != null && ConfirmacionUtil.confirmar("Resguardo " + r.resguardo().getNumero(),
                "¿Abrir el PDF del resguardo nuevo para firmarlo?"))
            abrirResguardo(r.resguardo(), scene);
        DialogUtil.runAsync(productoService::getAll, this::cargar,
            ex -> NotificacionUtil.error(scene, "No se pudo recargar el inventario"));
    }

    /** One line per batch at the bottom: where they went, número before → after,
     *  the new resguardo to print again later, and Deshacer for a wrong drop. */
    private void anotar(TransferenciaRapidaService.Resultado lote, String desde, String destino, Scene scene) {
        List<Movimiento> hechos = lote.hechos();
        Resguardo resguardo = lote.resguardo();
        contar(hechos.size());
        Label cuando = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        cuando.getStyleClass().add("org-table-sub");
        Label adonde = new Label(FormatUtils.plural(hechos.size(), "bien", "bienes") + " a " + destino);
        adonde.getStyleClass().add("org-area-name");
        Label cambios = new Label(cambios(hechos, false));
        cambios.getStyleClass().add("trf-persona");
        cambios.setWrapText(true);
        VBox textos = new VBox(2, new HBox(8, adonde, cuando), cambios);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox fila = new HBox(8, textos);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("trf-hecho");
        if (resguardo != null) {
            Button pdf = new Button("Resguardo " + resguardo.getNumero(), new FontIcon("mdi2f-file-pdf-box"));
            pdf.getStyleClass().add("btn-secondary");
            pdf.setMinWidth(Region.USE_PREF_SIZE);
            pdf.setTooltip(new Tooltip("Abrir el PDF del resguardo de quien recibió estos bienes"));
            pdf.setOnAction(e -> abrirResguardo(resguardo, scene));
            fila.getChildren().add(pdf);
        }
        Button deshacer = new Button("Deshacer", new FontIcon("mdi2u-undo-variant"));
        deshacer.getStyleClass().add("btn-secondary");
        deshacer.setMinWidth(Region.USE_PREF_SIZE);
        deshacer.setTooltip(new Tooltip("Regresarlos a " + desde + " con sus números de antes"
            + (resguardo != null ? " y cancelar el resguardo " + resguardo.getNumero() : "")));
        deshacer.setOnAction(e -> deshacer(lote, desde, destino, fila, adonde, cambios, scene));
        fila.getChildren().add(deshacer);
        historial.getChildren().add(0, fila);
        panelHistorial.setVisible(true);
        panelHistorial.setManaged(true);
    }

    private void contar(int cuantos) {
        transferidos += cuantos;
        tituloHistorial.setText("Transferidos en esta ventana: " + FormatUtils.plural(transferidos, "bien", "bienes"));
    }

    /** "EC-002 → DCS/01 · AV-005 → DCS/02", or the other way round when undone. */
    private static String cambios(List<Movimiento> hechos, boolean deRegreso) {
        return hechos.stream().limit(8)
            .map(m -> {
                String antes = Objects.requireNonNullElse(m.getCodigoAnterior(), "?");
                String despues = Objects.requireNonNullElse(m.getCodigoNuevo(), "?");
                return deRegreso ? despues + " → " + antes : antes + " → " + despues;
            })
            .collect(Collectors.joining("   ·   ")) + (hechos.size() > 8 ? "   … y " + (hechos.size() - 8) + " más" : "");
    }

    /** Takes a whole batch back to where it came from; the row says so afterwards. */
    private void deshacer(TransferenciaRapidaService.Resultado lote, String desde, String destino,
                          HBox fila, Label adonde, Label cambios, Scene scene) {
        int n = lote.hechos().size();
        if (!ConfirmacionUtil.confirmar("Deshacer la transferencia",
                "¿Regresar " + (n == 1 ? "el bien" : "los " + n + " bienes") + " de " + destino + " a " + desde
                + " con sus números de antes?\n\n" + cambios(lote.hechos(), true)
                + (lote.resguardo() != null ? "\n\nSe cancela el resguardo " + lote.resguardo().getNumero() + "." : "")
                + (lote.salidas().isEmpty() ? "" : "\nCada bien vuelve al resguardo en el que estaba.")))
            return;
        // Not twice: a second click while the first runs would try it again.
        fila.getChildren().forEach(nodo -> { if (nodo instanceof Button b) b.setDisable(true); });
        DialogUtil.runAsyncWithProgress(scene, "Deshaciendo la transferencia…",
            () -> transferencias.deshacer(lote),
            d -> {
                if (d.regresados().isEmpty())
                    fila.getChildren().forEach(nodo -> { if (nodo instanceof Button b) b.setDisable(false); });
                if (!d.regresados().isEmpty()) {
                    contar(-d.regresados().size());
                    NotificacionUtil.exito(scene, FormatUtils.plural(d.regresados().size(), "bien regresó", "bienes regresaron")
                        + " a " + desde);
                    adonde.setText(FormatUtils.plural(d.regresados().size(), "bien regresó", "bienes regresaron")
                        + " de " + destino + " a " + desde);
                    cambios.setText(String.join("   ·   ", d.regresados()));
                    fila.getChildren().removeIf(nodo -> nodo instanceof Button);
                    Label hecho = new Label("Deshecho", new FontIcon("mdi2u-undo-variant"));
                    hecho.getStyleClass().add("trf-deshecho");
                    hecho.setMinWidth(Region.USE_PREF_SIZE);
                    fila.getChildren().add(hecho);
                    fila.getStyleClass().add("trf-hecho-deshecho");
                }
                if (!d.errores().isEmpty()) {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    DialogUtil.applyOwner(a);
                    a.setTitle("No se deshizo todo");
                    a.setHeaderText(d.regresados().isEmpty() ? "No se deshizo la transferencia"
                        : "Algunos bienes no regresaron");
                    a.setContentText(String.join("\n", d.errores()));
                    DialogUtil.applyStylesheet(a.getDialogPane());
                    a.showAndWait();
                }
                if (alTransferir != null) alTransferir.run();
                DialogUtil.runAsync(productoService::getAll, this::cargar,
                    ex -> NotificacionUtil.error(scene, "No se pudo recargar el inventario"));
            },
            ex -> {
                fila.getChildren().forEach(nodo -> { if (nodo instanceof Button b) b.setDisable(false); });
                NotificacionUtil.error(scene, "No se pudo deshacer: "
                    + (ex.getMessage() != null ? ex.getMessage() : "error desconocido"));
            });
    }

    private void abrirResguardo(Resguardo resguardo, Scene scene) {
        DialogUtil.runAsyncWithProgress(scene, "Generando PDF…",
            () -> new ResguardoService().exportarPdf(resguardo),
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex -> NotificacionUtil.error(scene, "No se pudo generar el PDF del resguardo"));
    }

    /** An área that can be the origen: its icon, name and how many bienes it has. */
    private final class CeldaArea extends ListCell<String> {
        private final FontIcon icono = new FontIcon();
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaArea(ListView<String> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, nombre, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(String area, boolean empty) {
            super.updateItem(area, empty);
            setText(null);
            if (empty || area == null) { setGraphic(null); return; }
            icono.setIconLiteral(FiltroArea.icono(area).getIconLiteral());
            nombre.setText(area);
            cuenta.setText(FormatUtils.plural(porArea.getOrDefault(area, List.of()).size(), "bien", "bienes"));
            setGraphic(caja);
        }
    }

    /** A bien in the origin list: its código as a chip, the name, and who holds it. */
    private static final class CeldaBien extends ListCell<Producto> {
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final FontIcon persona = new FontIcon();
        private final Label quien = new Label(null, persona);
        private final VBox caja;

        CeldaBien(ListView<Producto> lv) {
            codigo.getStyleClass().add("trf-codigo");
            codigo.setMinWidth(Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox fila = new HBox(8, codigo, nombre);
            fila.setAlignment(Pos.CENTER_LEFT);
            persona.getStyleClass().add("trf-persona-icono");
            caja = new VBox(3, fila, quien);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(Producto p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); return; }
            codigo.setText(p.getCodigo());
            nombre.setText(p.getNombre());
            boolean tiene = p.getResguardante() != null && !p.getResguardante().isBlank();
            persona.setIconLiteral(tiene ? "mdi2a-account-outline" : "mdi2a-account-off-outline");
            quien.setText(tiene ? p.getResguardante() : "Sin resguardante");
            quien.getStyleClass().removeAll("trf-persona", "trf-sin-persona");
            quien.getStyleClass().add(tiene ? "trf-persona" : "trf-sin-persona");
            setGraphic(caja);
        }
    }

    /** "SGM/07" → 7: the list follows the área's numbering. */
    private static int numero(Producto p) {
        String c = p.getCodigo();
        int barra = c != null ? c.lastIndexOf('/') : -1;
        try { return barra >= 0 ? Integer.parseInt(c.substring(barra + 1)) : Integer.MAX_VALUE; }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }
}
