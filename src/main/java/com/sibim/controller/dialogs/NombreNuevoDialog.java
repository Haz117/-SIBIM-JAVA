package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.EditarVarios;
import com.sibim.service.NombreNuevo;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/** Right after a bien's name was changed in its form: the other bienes that still have the
 *  name it had, to give them the new one too (see {@link NombreNuevo}). The ones that say they
 *  are of another purchase come unticked; Space ticks the row the keyboard is on; undone from
 *  the notice. Laid out like «Tomar de los del mismo nombre». */
public final class NombreNuevoDialog {

    private static final Logger log = LoggerFactory.getLogger(NombreNuevoDialog.class);

    private NombreNuevoDialog() {}

    /** Offers it, if any bien had that name; then runs {@code despues} (also when nothing is
     *  offered, it is declined or it fails), once the names are saved.
     *  @param antes the bien before the form, null if it was just registered */
    public static void ofrecer(Scene scene, ProductoService servicio, Producto antes, Producto guardado,
                               Runnable alCambiar, Runnable despues) {
        if (antes == null || guardado == null
            || com.sibim.service.CalidadDatosService.claveTexto(antes.getNombre())
                .equals(com.sibim.service.CalidadDatosService.claveTexto(guardado.getNombre()))) {
            despues.run();
            return;
        }
        DialogUtil.runAsync(() -> NombreNuevo.seLlamabanIgual(antes, guardado, servicio.getAll()),
            otros -> {
                if (otros.isEmpty()) { despues.run(); return; }
                Optional<List<Producto>> elegidos = show(antes, guardado, otros);
                if (elegidos.isEmpty()) { despues.run(); return; }
                List<EditarVarios.Renglon> renglones = NombreNuevo.renglones(elegidos.get(), guardado.getNombre());
                DialogUtil.runAsyncWithProgress(scene, "Cambiando el nombre…",
                    () -> EditarVarios.aplicar(servicio, renglones),
                    r -> {
                        if (alCambiar != null && !r.guardados().isEmpty()) alCambiar.run();
                        r.fallas().forEach(f -> log.warn("Nombre nuevo: {}", f));
                        String fallas = r.fallas().isEmpty() ? "" : "; " + FormatUtils.plural(r.fallas().size(), "no se pudo", "no se pudieron")
                            + " (" + r.fallas().get(0) + (r.fallas().size() > 1 ? "…" : "") + ")";
                        if (r.guardados().isEmpty()) {
                            NotificacionUtil.error(scene, "No se cambió ningún nombre" + fallas);
                        } else {
                            NotificacionUtil.exitoConAccion(scene,
                                "El nombre nuevo también en " + FormatUtils.plural(r.guardados().size(), "bien", "bienes") + fallas,
                                "Deshacer", () -> DialogUtil.runAsync(() -> EditarVarios.deshacer(servicio, r),
                                    n -> {
                                        if (alCambiar != null) alCambiar.run();
                                        NotificacionUtil.info(scene, FormatUtils.plural(n, "bien regresó", "bienes regresaron") + " a su nombre de antes");
                                    },
                                    ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
                        }
                        despues.run();
                    },
                    ex -> {
                        NotificacionUtil.error(scene, "No se cambiaron los nombres: " + com.sibim.util.MensajeError.de(ex));
                        despues.run();
                    });
            },
            e -> {
                log.warn("No se pudo buscar los del nombre de antes de {}: {}", guardado.getCodigo(), e.getMessage());
                despues.run();
            });
    }

    /** @return the ticked ones, or empty if declined */
    static Optional<List<Producto>> show(Producto antes, Producto guardado, List<Producto> otros) {
        Set<Producto> marcados = NombreNuevo.marcados(guardado, otros);
        Map<Producto, SimpleBooleanProperty> poner = new LinkedHashMap<>();
        otros.forEach(p -> poner.put(p, new SimpleBooleanProperty(marcados.contains(p))));
        long deOtra = otros.size() - marcados.size();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("El nombre nuevo a los que se llamaban igual");
        ButtonType ok = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        ButtonType soloEste = new ButtonType("Solo a " + Objects.requireNonNullElse(guardado.getCodigo(), "este"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, soloEste);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1000, pantalla.getWidth() * 0.94), Math.min(560, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        HBox header = DialogUtil.gradientHeader("mdi2r-rename-box", "El nombre nuevo a los que se llamaban igual",
            FormatUtils.plural(otros.size(), "bien sigue", "bienes siguen") + " con el nombre que tenía "
                + Objects.requireNonNullElse(guardado.getCodigo(), "este bien"), AppColors.PRIMARY, AppColors.PRIMARY_D);

        Label antesYAhora = new Label("Antes:  " + Objects.requireNonNullElse(antes.getNombre(), "").trim()
            + "\nAhora:  " + Objects.requireNonNullElse(guardado.getNombre(), "").trim());
        antesYAhora.getStyleClass().add("cnt-aviso");
        antesYAhora.setWrapText(true);
        antesYAhora.setMaxWidth(Double.MAX_VALUE);
        antesYAhora.setMinHeight(Region.USE_PREF_SIZE);
        antesYAhora.setPadding(new Insets(8, 12, 8, 12));

        TableView<Producto> tabla = new TableView<>(FXCollections.observableArrayList(otros));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Producto, Boolean> si = new TableColumn<>("Cambiar");
        si.setCellValueFactory(c -> poner.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(90);
        si.setMinWidth(90);
        si.setMaxWidth(96);
        TableColumn<Producto, String> numero = columna("Número", 96, Producto::getCodigo);
        numero.setMinWidth(90);
        TableColumn<Producto, String> area = columna("Área", 230, Producto::getArea);
        TableColumn<Producto, String> quien = columna("Resguardante", 200, Producto::getResguardante);
        TableColumn<Producto, String> compra = columna("Su compra", 260, NombreNuevoDialog::compra);
        tabla.getColumns().addAll(List.of(si, numero, area, quien, compra));
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            Producto p = tabla.getSelectionModel().getSelectedItem();
            if (p == null) return;
            poner.get(p).set(!poner.get(p).get());
            e.consume();
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("Se guarda bien por bien, como en el formulario, y se deshace desde el aviso."
            + (deOtra > 0 ? " Este nombre lo tienen bienes de varias compras: van marcados solo los de la misma factura o folio fiscal; "
                + "marca los demás que también lleven el nombre nuevo." : ""));
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        CheckBox todos = new CheckBox("Marcar todos");
        todos.setSelected(deOtra == 0);
        todos.setOnAction(e -> poner.values().forEach(p -> p.set(todos.isSelected())));
        HBox arriba = new HBox(10, ayuda, todos);
        HBox.setHgrow(ayuda, Priority.ALWAYS);
        todos.setMinWidth(Region.USE_PREF_SIZE);
        arriba.setAlignment(Pos.CENTER_LEFT);

        Label total = new Label();
        total.getStyleClass().add("table-count-label");
        Button boton = (Button) dialog.getDialogPane().lookupButton(ok);
        Runnable contar = () -> {
            long n = poner.values().stream().filter(SimpleBooleanProperty::get).count();
            total.setText(FormatUtils.plural(n, "bien marcado", "bienes marcados") + " de " + otros.size());
            boton.setText("Cambiarles el nombre a " + FormatUtils.plural(n, "bien", "bienes"));
            boton.setDisable(n == 0);
        };
        poner.values().forEach(p -> p.addListener((o, a, b) -> contar.run()));
        contar.run();

        VBox cuerpo = new VBox(10, antesYAhora, arriba, tabla, total);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ok) return Optional.empty();
        List<Producto> elegidos = otros.stream().filter(p -> poner.get(p).get()).toList();
        return elegidos.isEmpty() ? Optional.empty() : Optional.of(elegidos);
    }

    /** What tells one purchase from another: «Factura B 5630 · 29/07/2026 · SIMON…». */
    public static String compra(Producto p) {
        List<String> partes = new ArrayList<>();
        String n = Objects.requireNonNullElse(p.getNumeroFactura(), "").trim();
        String f = Objects.requireNonNullElse(p.getFolioFiscal(), "").trim();
        if (!n.isEmpty() && !Producto.pareceFolioFiscal(n)) partes.add("Factura " + n);
        else if (!f.isEmpty() || !n.isEmpty()) {
            String u = !f.isEmpty() ? f : n;
            partes.add("Folio …" + u.substring(Math.max(0, u.length() - 6)));
        }
        if (p.getFechaAdquisicion() != null) partes.add(FormatUtils.formatDate(p.getFechaAdquisicion()));
        if (p.getProveedor() != null && !p.getProveedor().isBlank()) partes.add(p.getProveedor().trim());
        return partes.isEmpty() ? "Sin datos de compra" : String.join(" · ", partes);
    }

    private static TableColumn<Producto, String> columna(String titulo, double ancho, Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(Objects.requireNonNullElse(valor.apply(cd.getValue()), "")));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
