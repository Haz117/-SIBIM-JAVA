package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.ReporteService;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.*;

/** Picks bienes from a search instead of typing their códigos separated by commas,
 *  where a typo was only found out after generating. A código and Enter ticks the
 *  first match and leaves the search ready for the next one. */
public final class ElegirBienesDialog {

    private final List<Producto> bienes;
    private final Set<String> marcados = new LinkedHashSet<>();
    private final TextField buscar = new TextField();
    private final ObservableList<Producto> visibles = FXCollections.observableArrayList();
    private final ListView<Producto> lista = new ListView<>(visibles);
    private final Label resumen = new Label();
    private Button generar;

    private ElegirBienesDialog(List<Producto> bienes) {
        this.bienes = bienes.stream().filter(p -> p.getFechaBaja() == null)
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), ReporteService.POR_CODIGO))
            .toList();
    }

    /** @return the bienes ticked, by número; empty when cancelled */
    public static List<Producto> show(String titulo, String subtitulo, List<Producto> bienes) {
        return new ElegirBienesDialog(bienes).abrir(titulo, subtitulo);
    }

    private List<Producto> abrir(String titulo, String subtitulo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(760, pantalla.getWidth() * 0.94), Math.min(620, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2f-file-remove-outline", titulo, subtitulo,
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        generar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        generar.setText("Generar PDF");
        generar.getStyleClass().add("dialog-ok-btn");
        // Enter is for picking while the search has text; with nothing left to pick, it generates.
        generar.setDefaultButton(false);

        buscar.setId("buscarBien");
        buscar.setPromptText("Código, nombre, serie o resguardante…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        buscar.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;
            e.consume();
            String q = buscar.getText() == null ? "" : buscar.getText().trim();
            if (q.isEmpty()) { if (!generar.isDisabled()) generar.fire(); return; }
            if (visibles.isEmpty()) return;
            marcados.add(visibles.get(0).getId());
            buscar.clear();
            actualizar();
        });

        lista.getStyleClass().addAll("cnt-lista", "rsg-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("Ningún bien coincide con lo escrito"));
        VBox.setVgrow(lista, Priority.ALWAYS);

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);

        VBox cuerpo = new VBox(8, buscar, lista, resumen);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        filtrar();
        actualizar();
        Platform.runLater(buscar::requestFocus);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return List.of();
        return bienes.stream().filter(p -> marcados.contains(p.getId())).toList();
    }

    private void filtrar() {
        visibles.setAll(DialogUtil.coincidencias(bienes, ElegirBienesDialog::textoDe, buscar.getText()));
    }

    private static String textoDe(Producto p) {
        return Objects.requireNonNullElse(p.getCodigo(), "") + " " + Objects.requireNonNullElse(p.getNombre(), "") + " "
            + Objects.requireNonNullElse(p.getNumeroSerie(), "") + " " + Objects.requireNonNullElse(p.getResguardante(), "")
            + " " + Objects.requireNonNullElse(com.sibim.util.ProductoUtils.inventarioAnterior(p), "");
    }

    private void actualizar() {
        int n = marcados.size();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (n > 0) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(n == 0 ? "Escribe un código y pulsa Enter, o haz clic en los bienes"
            : FormatUtils.plural(n, "bien elegido", "bienes elegidos") + " · una página por bien");
        generar.setDisable(n == 0);
        lista.refresh();
    }

    private static boolean dentroDe(Object objetivo, Node nodo) {
        for (Node n = objetivo instanceof Node o ? o : null; n != null; n = n.getParent())
            if (n == nodo) return true;
        return false;
    }

    private final class CeldaBien extends ListCell<Producto> {
        private final CheckBox casilla = new CheckBox();
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
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
            fila = new HBox(10, casilla, textos);
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
            detalle.setText((p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante")
                + (p.getArea() != null ? " · " + p.getArea() : ""));
            casilla.setSelected(marcados.contains(p.getId()));
            casilla.setAccessibleText(p.getCodigo());
            fila.getStyleClass().remove("rsg-fila-sel");
            if (casilla.isSelected()) fila.getStyleClass().add("rsg-fila-sel");
            setGraphic(fila);
        }
    }
}
