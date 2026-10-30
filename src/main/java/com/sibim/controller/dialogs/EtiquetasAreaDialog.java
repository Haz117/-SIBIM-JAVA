package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.controller.FiltroArea;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
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
import java.util.stream.Collectors;

/** Bienes › Etiquetas por área: on 30-oct-2026, 0 of 3,062 bienes were labelled, and a
 *  scanner in the Conteo needs them. An área at a time: pick what goes on the sheet,
 *  print, and once they are stuck on, mark them, with how far each área has got. */
public final class EtiquetasAreaDialog {

    @FunctionalInterface
    private interface Exportar { File generar(List<Producto> bienes) throws Exception; }

    private final ProductoService service = new ProductoService();
    private final List<Producto> bienes;
    private final Runnable alCambiar;
    private Dialog<ButtonType> dialog;
    private boolean cambio = false;

    private final ListView<String> areas = new ListView<>();
    private final Map<String, int[]> avance = new HashMap<>();
    private final ToggleButton fSin = new ToggleButton(), fCon = new ToggleButton(), fTodos = new ToggleButton();
    private final TextField buscar = new TextField();
    private final ObservableList<Producto> visibles = FXCollections.observableArrayList();
    private final ListView<Producto> lista = new ListView<>(visibles);
    private final Set<String> marcados = new LinkedHashSet<>();
    private final Label tituloArea = new Label();
    private final ProgressBar progreso = new ProgressBar(0);
    private final Label progresoTexto = new Label();
    private final Label resumen = new Label();
    private final MenuButton imprimir = new MenuButton("Imprimir etiquetas");
    private final Button pegadas = new Button("Ya están pegadas");
    private final VBox vacio = new VBox(8);
    private final VBox panel = new VBox(8);

    private EtiquetasAreaDialog(List<Producto> bienes, Runnable alCambiar) {
        this.bienes = bienes.stream().filter(p -> p.getFechaBaja() == null)
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), ReporteService.POR_CODIGO))
            .toList();
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run on closing when something was marked */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        new EtiquetasAreaDialog(bienes, alCambiar).abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Etiquetas por área");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1160, pantalla.getWidth() * 0.94), Math.min(720, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2t-tag-multiple-outline", "Etiquetas por área",
            "Elige un área, imprime sus etiquetas y márcalas cuando ya estén pegadas",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        areas.getStyleClass().add("trf-areas");
        areas.setCellFactory(lv -> new CeldaArea(lv));
        areas.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> elegir());
        VBox.setVgrow(areas, Priority.ALWAYS);
        VBox izquierda = new VBox(8, TransferenciaArrastreDialog.paso(1, "Elige el área"), areas);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(330);
        izquierda.setMinWidth(270);

        HBox cuerpo = new HBox(16, izquierda, panelDerecho());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        cargarAreas();
        elegir();
        Platform.runLater(areas::requestFocus);
        dialog.showAndWait();
    }

    private static String areaDe(Producto p) {
        return p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área";
    }

    private void cargarAreas() {
        String elegida = areas.getSelectionModel().getSelectedItem();
        avance.clear();
        for (Producto p : bienes) {
            int[] a = avance.computeIfAbsent(areaDe(p), k -> new int[2]);
            if (p.isEtiquetado()) a[0]++;
            a[1]++;
        }
        List<String> orden = new ArrayList<>(Areas.getAllAreaNames().stream().filter(avance::containsKey).toList());
        avance.keySet().stream().filter(a -> !orden.contains(a)).sorted().forEach(orden::add);
        // Only if it changed: replacing the items drops the selection, which restarts the area.
        if (!areas.getItems().equals(orden)) {
            areas.getItems().setAll(orden);
            if (elegida != null) areas.getSelectionModel().select(elegida);
        }
        areas.refresh();
    }

    private VBox panelDerecho() {
        FontIcon icono = new FontIcon("mdi2t-tag-multiple-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige un área");
        titulo.getStyleClass().add("empty-state-msg");
        vacio.getChildren().setAll(icono, titulo);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        tituloArea.getStyleClass().add("trf-paso-titulo");
        Region huecoT = new Region();
        HBox.setHgrow(huecoT, Priority.ALWAYS);
        progreso.setPrefWidth(200);
        progresoTexto.getStyleClass().add("muted-sm");
        progresoTexto.setMinWidth(Region.USE_PREF_SIZE);
        HBox arriba = new HBox(10, tituloArea, huecoT, progreso, progresoTexto);
        arriba.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup filtro = new ToggleGroup();
        for (ToggleButton t : List.of(fSin, fCon, fTodos)) {
            t.setToggleGroup(filtro);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
        }
        fSin.setSelected(true);
        filtro.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            refiltrar();
        });
        Hyperlink todos = new Hyperlink("Marcar todos");
        todos.setTooltip(new Tooltip("Marca todos los de esta lista"));
        todos.setOnAction(e -> { visibles.forEach(p -> marcados.add(p.getId())); actualizar(); });
        Hyperlink ninguno = new Hyperlink("Ninguno");
        ninguno.setOnAction(e -> { marcados.clear(); actualizar(); });
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox pestanas = new HBox(6, fSin, fCon, fTodos, hueco, todos, new Label("·"), ninguno);
        pestanas.setAlignment(Pos.CENTER_LEFT);

        buscar.setId("buscarBien");
        buscar.setPromptText("Buscar por código, nombre o resguardante…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());

        lista.getStyleClass().addAll("cnt-lista", "rsg-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("No hay bienes aquí"));
        VBox.setVgrow(lista, Priority.ALWAYS);

        panel.getChildren().setAll(arriba, pestanas, buscar, lista);
        VBox.setVgrow(panel, Priority.ALWAYS);

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        resumen.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resumen, Priority.ALWAYS);
        MenuItem fisicas = new MenuItem("Etiquetas para pegar");
        fisicas.setGraphic(new FontIcon("mdi2t-tag-outline"));
        fisicas.setOnAction(e -> imprimir("Generando etiquetas…", ReporteService.getInstance()::exportEtiquetaFisicaPdf));
        MenuItem qr = new MenuItem("Solo códigos QR");
        qr.setGraphic(new FontIcon("mdi2q-qrcode"));
        qr.setOnAction(e -> imprimir("Generando códigos QR…", ReporteService.getInstance()::exportEtiquetasQrPdf));
        imprimir.getItems().setAll(fisicas, qr);
        imprimir.setGraphic(new FontIcon("mdi2p-printer"));
        imprimir.getStyleClass().add("btn-secondary");
        imprimir.setMinWidth(Region.USE_PREF_SIZE);
        pegadas.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        pegadas.getStyleClass().add("btn-primary");
        pegadas.setMinWidth(Region.USE_PREF_SIZE);
        pegadas.setTooltip(new Tooltip("Marca los elegidos como etiquetados: dejan de contar en Alertas como «Sin etiquetar»"));
        pegadas.setOnAction(e -> preguntarYMarcar(elegidos()));
        HBox pie = new HBox(10, resumen, imprimir, pegadas);
        pie.setAlignment(Pos.CENTER_LEFT);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Elige los bienes");
        paso2.setPadding(Insets.EMPTY);
        VBox p = new VBox(8, paso2, vacio, panel, new Separator(), pie);
        HBox.setHgrow(p, Priority.ALWAYS);
        return p;
    }

    private void elegir() {
        String area = areas.getSelectionModel().getSelectedItem();
        ver(vacio, area == null);
        ver(panel, area != null);
        marcados.clear();
        buscar.clear();
        fSin.setSelected(true);
        if (area != null) tituloArea.setText(area);
        refiltrar();
        actualizar();
        if (!visibles.isEmpty()) lista.scrollTo(0);
    }

    private List<Producto> delArea() {
        String area = areas.getSelectionModel().getSelectedItem();
        return area == null ? List.of() : bienes.stream().filter(p -> area.equals(areaDe(p))).toList();
    }

    private void refiltrar() {
        List<Producto> base = delArea().stream()
            .filter(p -> fTodos.isSelected() || (fSin.isSelected() ? !p.isEtiquetado() : p.isEtiquetado())).toList();
        visibles.setAll(DialogUtil.coincidencias(base,
            p -> Objects.requireNonNullElse(p.getCodigo(), "") + " " + Objects.requireNonNullElse(p.getNombre(), "")
                + " " + Objects.requireNonNullElse(p.getResguardante(), ""),
            buscar.getText()));
    }

    private List<Producto> elegidos() {
        return bienes.stream().filter(p -> marcados.contains(p.getId())).toList();
    }

    private void actualizar() {
        List<Producto> base = delArea();
        long hechos = base.stream().filter(Producto::isEtiquetado).count();
        fSin.setText("Sin etiquetar (" + (base.size() - hechos) + ")");
        fCon.setText("Etiquetados (" + hechos + ")");
        fTodos.setText("Todos (" + base.size() + ")");
        progreso.setProgress(base.isEmpty() ? 0 : (double) hechos / base.size());
        progresoTexto.setText(hechos + " de " + base.size() + " etiquetados");
        int n = marcados.size();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (n > 0) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(n == 0 ? "Marca los bienes que van en esta hoja" : FormatUtils.plural(n, "bien elegido", "bienes elegidos"));
        imprimir.setDisable(n == 0);
        pegadas.setDisable(n == 0);
        lista.refresh();
    }

    private void imprimir(String aviso, Exportar exportar) {
        List<Producto> hoja = elegidos();
        Scene scene = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(scene, aviso, () -> exportar.generar(hoja),
            file -> {
                if (file == null) { NotificacionUtil.advertencia(scene, "No se generaron etiquetas"); return; }
                DialogUtil.showExportResultDialog(scene, file);
                preguntarYMarcar(hoja);
            },
            e -> NotificacionUtil.error(scene, "No se pudieron generar las etiquetas: " + e.getMessage()));
    }

    /** Asks first because printing is not sticking them on. */
    private void preguntarYMarcar(List<Producto> hoja) {
        List<Producto> pendientes = hoja.stream().filter(p -> !p.isEtiquetado()).toList();
        if (pendientes.isEmpty()) return;
        if (!ConfirmacionUtil.confirmar("¿Marcar como etiquetados?",
                "Cuando las etiquetas de " + FormatUtils.plural(pendientes.size(), "bien", "bienes") + " ya estén pegadas, "
                    + "márcalos para que dejen de contar en Alertas como «Sin etiquetar».\n\n¿Ya están pegadas?")) return;
        Scene scene = dialog.getDialogPane().getScene();
        List<String> ids = pendientes.stream().map(Producto::getId).collect(Collectors.toList());
        DialogUtil.runAsyncWithProgress(scene, "Actualizando etiquetado…",
            () -> { service.marcarEtiquetado(ids, true); return ids.size(); },
            n -> {
                pendientes.forEach(p -> p.setEtiquetado(true));
                cambio = true;
                marcados.removeAll(ids);
                NotificacionUtil.exito(scene, FormatUtils.plural(n, "bien marcado", "bienes marcados") + " como etiquetados");
                cargarAreas();
                refiltrar();
                actualizar();
            },
            e -> NotificacionUtil.error(scene, "No se pudo actualizar el etiquetado: " + e.getMessage()));
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

    private final class CeldaArea extends ListCell<String> {
        private final FontIcon icono = new FontIcon();
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label cuenta = new Label();
        private final ProgressBar barra = new ProgressBar(0);
        private final HBox caja;

        CeldaArea(ListView<String> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMaxWidth(Double.MAX_VALUE);
            barra.setMaxWidth(Double.MAX_VALUE);
            barra.getStyleClass().add("cnt-barra");
            VBox textos = new VBox(3, nombre, barra);
            textos.setMinWidth(0);
            textos.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, textos, cuenta);
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
            nombre.setTooltip(new Tooltip(area));
            int[] a = avance.getOrDefault(area, new int[2]);
            cuenta.setText(a[0] > 0 ? a[0] + " de " + a[1] : String.valueOf(a[1]));
            barra.setProgress(a[1] > 0 ? (double) a[0] / a[1] : 0);
            ver(barra, a[0] > 0);
            setGraphic(caja);
        }
    }

    private final class CeldaBien extends ListCell<Producto> {
        private final CheckBox casilla = new CheckBox();
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final Label hecho = new Label("Etiquetado");
        private final HBox fila;

        CeldaBien(ListView<Producto> lv) {
            codigo.getStyleClass().add("trf-codigo");
            codigo.setMinWidth(Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMinWidth(0);
            HBox linea = new HBox(8, codigo, nombre);
            linea.setAlignment(Pos.CENTER_LEFT);
            detalle.getStyleClass().add("org-table-sub");
            detalle.setTextOverrun(OverrunStyle.ELLIPSIS);
            detalle.setMinWidth(0);
            VBox textos = new VBox(3, linea, detalle);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            hecho.getStyleClass().addAll("cell-badge", "cell-badge-success");
            hecho.setMinWidth(Region.USE_PREF_SIZE);
            fila = new HBox(10, casilla, textos, hecho);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("cnt-fila");
            fila.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            fila.setMinWidth(0);
            casilla.setOnAction(e -> alternar());
            fila.setOnMouseClicked(e -> { if (!dentroDe(e.getTarget(), casilla)) alternar(); });
        }

        private void alternar() {
            Producto p = getItem();
            if (p == null) return;
            if (!marcados.remove(p.getId())) marcados.add(p.getId());
            actualizar();
        }

        @Override protected void updateItem(Producto p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); return; }
            codigo.setText(p.getCodigo());
            nombre.setText(p.getNombre());
            nombre.setTooltip(new Tooltip(p.getNombre()));
            detalle.setText(p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante");
            ver(hecho, p.isEtiquetado());
            casilla.setSelected(marcados.contains(p.getId()));
            casilla.setAccessibleText(p.getCodigo());
            fila.getStyleClass().remove("rsg-fila-sel");
            if (casilla.isSelected()) fila.getStyleClass().add("rsg-fila-sel");
            setGraphic(fila);
        }
    }
}
