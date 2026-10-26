package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * "Transferir arrastrando" (Movimientos, Patrimonio only): the bienes of one área
 * on the left, every other área as a card on the right — the same board as
 * Organigrama › Acomodar. Dropping bienes on a card asks for the motivo and, if
 * wanted, who receives them there; TransferenciaRapidaService does the rest (out
 * of the old resguardo, transfer with the next free número, new resguardo).
 */
public final class TransferenciaArrastreDialog {

    private static final DataFormat BIENES = new DataFormat("application/x-sibim-bienes");

    private final ProductoService productoService = new ProductoService();
    private final TransferenciaRapidaService transferencias = new TransferenciaRapidaService();
    private final Runnable alTransferir;

    private Map<String, List<Producto>> porArea = Map.of();
    private final ComboBox<String> origen = new ComboBox<>();
    private final TextField buscar = new TextField();
    private final ObservableList<Producto> visibles = FXCollections.observableArrayList();
    private final ListView<Producto> lista = new ListView<>(visibles);
    private final TextField buscarArea = new TextField();
    private final FlowPane tablero = new FlowPane(12, 12);
    private final Label seleccion = new Label();
    private Dialog<Void> dialog;

    private TransferenciaArrastreDialog(Runnable alTransferir) { this.alTransferir = alTransferir; }

    /** @param alTransferir run after each batch, to refresh the screen behind */
    public static void show(List<Producto> productos, Runnable alTransferir) {
        new TransferenciaArrastreDialog(alTransferir).abrir(productos);
    }

    private void abrir(List<Producto> productos) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Transferir arrastrando");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(1180, 720);
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-drag-variant", "Transferir arrastrando",
            "Elige el área de origen, selecciona bienes y arrástralos al área que los recibe",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        origen.setPromptText("Área de origen…");
        origen.setMaxWidth(Double.MAX_VALUE);
        origen.getStyleClass().add("form-input");
        buscar.setPromptText("Buscar por código, nombre o resguardante…");
        buscar.getStyleClass().add("form-input");
        lista.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lista.setPlaceholder(new Label("Elige un área de origen"));
        lista.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setGraphic(null); return; }
                Label nombre = new Label(p.getCodigo() + "   " + p.getNombre());
                nombre.getStyleClass().add("org-child-label");
                Label quien = new Label(p.getResguardante() != null && !p.getResguardante().isBlank()
                    ? p.getResguardante() : "Sin resguardante");
                quien.getStyleClass().add("org-area-count");
                setText(null);
                setGraphic(new VBox(1, nombre, quien));
            }
        });
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
            e.consume();
        });

        origen.valueProperty().addListener((o, a, b) -> { filtrar(); construirTablero(); });
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        buscarArea.setPromptText("Buscar área destino…");
        buscarArea.getStyleClass().add("form-input");
        buscarArea.textProperty().addListener((o, a, b) -> construirTablero());

        Label tituloOrigen = new Label("BIENES DEL ÁREA DE ORIGEN");
        tituloOrigen.getStyleClass().add("dash-section-label");
        VBox izquierda = new VBox(8, tituloOrigen, origen, buscar, lista, seleccion);
        izquierda.setPrefWidth(430);
        izquierda.setMinWidth(340);

        Label tituloDestino = new Label("ÁREAS DESTINO · suelta aquí los bienes");
        tituloDestino.getStyleClass().add("dash-section-label");
        tablero.setPadding(new Insets(4, 4, 8, 4));
        ScrollPane scroll = new ScrollPane(tablero);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox derecha = new VBox(8, tituloDestino, buscarArea, scroll);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        HBox cuerpo = new HBox(16, izquierda, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox content = new VBox(12, header, cuerpo);
        content.setPadding(new Insets(0, 16, 12, 16));
        dialog.getDialogPane().setContent(content);

        cargar(productos);
        Platform.runLater(origen::requestFocus);
        dialog.showAndWait();
    }

    private void cargar(List<Producto> productos) {
        String elegido = origen.getValue();
        porArea = productos.stream().filter(p -> p.getArea() != null && p.getFechaBaja() == null)
            .collect(Collectors.groupingBy(Producto::getArea));
        List<String> conBienes = Areas.getAllAreaNames().stream().filter(porArea::containsKey).toList();
        DialogUtil.makeFilterable(origen, new ArrayList<>(conBienes));
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
        actualizarSeleccion();
    }

    private void actualizarSeleccion() {
        int n = lista.getSelectionModel().getSelectedItems().size();
        seleccion.setText(n == 0
            ? "Selecciona uno o varios bienes (Ctrl o Mayús + clic) y arrástralos a un área →"
            : FormatUtils.plural(n, "bien seleccionado", "bienes seleccionados") + " · arrástralos a un área →");
    }

    private void construirTablero() {
        String q = buscarArea.getText() == null ? "" : buscarArea.getText().trim().toLowerCase();
        List<VBox> tarjetas = new ArrayList<>();
        for (String area : Areas.getAllAreaNames()) {
            if (area.equals(origen.getValue())) continue;
            if (!q.isEmpty() && !area.toLowerCase().contains(q)) continue;
            tarjetas.add(tarjeta(area));
        }
        tablero.getChildren().setAll(tarjetas);
    }

    private VBox tarjeta(String area) {
        Label nombre = new Label(area);
        nombre.getStyleClass().add("org-area-name");
        nombre.setWrapText(true);
        int n = porArea.getOrDefault(area, List.of()).size();
        Label cuenta = new Label(n == 0 ? "Sin bienes" : FormatUtils.plural(n, "bien", "bienes"));
        cuenta.getStyleClass().add("org-table-sub");
        VBox t = new VBox(3, nombre, cuenta);
        t.getStyleClass().add("org-acomodo-col");
        t.setPrefWidth(200);
        t.setMinHeight(64);
        t.setAlignment(Pos.TOP_LEFT);
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

    private void confirmar(List<Producto> bienes, String destino) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Transferir a " + destino);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal-bold", "Transferir a " + destino,
            FormatUtils.plural(bienes.size(), "bien", "bienes") + " de " + origen.getValue(),
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        String cuales = bienes.stream().limit(6).map(p -> p.getCodigo() + " " + p.getNombre())
            .collect(Collectors.joining("\n")) + (bienes.size() > 6 ? "\n… y " + (bienes.size() - 6) + " más" : "");
        Label lista = new Label(cuales);
        lista.setWrapText(true);
        lista.getStyleClass().add("table-count-label");

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

        Label nota = new Label("Cada bien sale de su resguardo actual y toma el siguiente número libre de " + destino
            + ". Si escribes quién los recibe, se le crea un resguardo con todos ellos.");
        nota.setWrapText(true);
        nota.getStyleClass().add("table-count-label");

        VBox content = new VBox(10, header, lista, form, nota);
        content.setPadding(new Insets(0, 16, 16, 16));
        dlg.getDialogPane().setContent(content);
        Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Transferir");
        ok.getStyleClass().add("dialog-ok-btn");
        ok.disableProperty().bind(motivo.textProperty().isEmpty());
        Platform.runLater(motivo::requestFocus);

        dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            String quien = resguardante.getEditor().getText();
            Scene scene = dialog.getDialogPane().getScene();
            DialogUtil.runAsyncWithProgress(scene, "Transfiriendo " + FormatUtils.plural(bienes.size(), "bien", "bienes") + "…",
                () -> transferencias.transferir(bienes, destino, motivo.getText(), quien, cargo.getText()),
                r -> terminado(r, destino, scene),
                ex -> NotificacionUtil.error(scene, "No se pudo transferir: "
                    + (ex.getMessage() != null ? ex.getMessage() : "error desconocido")));
        });
    }

    private void terminado(TransferenciaRapidaService.Resultado r, String destino, Scene scene) {
        if (!r.hechos().isEmpty())
            NotificacionUtil.exito(scene, FormatUtils.plural(r.hechos().size(), "bien transferido", "bienes transferidos")
                + " a " + destino + (r.resguardo() != null ? " · resguardo " + r.resguardo().getNumero() : ""));
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
            DialogUtil.runAsyncWithProgress(scene, "Generando PDF…",
                () -> new ResguardoService().exportarPdf(r.resguardo()),
                file -> DialogUtil.showExportResultDialog(scene, file),
                ex -> NotificacionUtil.error(scene, "No se pudo generar el PDF del resguardo"));
        DialogUtil.runAsync(productoService::getAll, this::cargar,
            ex -> NotificacionUtil.error(scene, "No se pudo recargar el inventario"));
    }

    /** "SGM/07" → 7: the list follows the área's numbering. */
    private static int numero(Producto p) {
        String c = p.getCodigo();
        int barra = c != null ? c.lastIndexOf('/') : -1;
        try { return barra >= 0 ? Integer.parseInt(c.substring(barra + 1)) : Integer.MAX_VALUE; }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }
}
