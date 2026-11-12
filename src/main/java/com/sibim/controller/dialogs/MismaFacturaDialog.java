package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.MismaFactura;
import com.sibim.service.MismaFactura.Cambio;
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
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Passes the data of one bien to the other bienes of its factura (see {@link MismaFactura}):
 *  right after saving a bien in its form, and from Bienes › «Copiar datos de la misma
 *  factura» for what was already captured. Each line can be left out; undone from the notice. */
public final class MismaFacturaDialog {

    private static final Logger log = LoggerFactory.getLogger(MismaFacturaDialog.class);

    private final List<Cambio> cambios;
    private final boolean deUnBien;
    private final String titulo, subtitulo, explicacion;
    private final Map<Cambio, SimpleBooleanProperty> elegidos = new LinkedHashMap<>();
    private final Button aplicar = new Button();
    private final Runnable alCambiar;

    private MismaFacturaDialog(List<Cambio> cambios, boolean deUnBien, String titulo, String subtitulo,
                               String explicacion, Runnable alCambiar) {
        this.cambios = cambios;
        this.deUnBien = deUnBien;
        this.titulo = titulo;
        this.subtitulo = subtitulo;
        this.explicacion = explicacion;
        this.alCambiar = alCambiar;
    }

    /** After a bien was saved in its form: offers what it got to the bienes of its factura,
     *  if any of them can take it; then runs {@code despues} (also when nothing is offered).
     *  @param antes the bien before the form, null if it was just registered */
    public static void ofrecer(Scene scene, ProductoService servicio, Producto antes, Producto guardado,
                               Runnable alCambiar, Runnable despues) {
        if (guardado == null || !MismaFactura.tieneCompra(guardado)) {
            despues.run();
            return;
        }
        DialogUtil.runAsync(() -> MismaFactura.proponer(antes, guardado, servicio.getAll()),
            propuesta -> {
                if (!propuesta.isEmpty()) {
                    long bienes = propuesta.stream().flatMap(c -> c.bienes().stream()).map(Producto::getId).distinct().count();
                    String factura = MismaFactura.etiqueta(guardado);
                    new MismaFacturaDialog(propuesta, true, "Lo mismo para la factura " + factura,
                        "Lo que capturaste en " + Objects.requireNonNullElse(guardado.getCodigo(), "este bien")
                            + " también les falta a otros " + FormatUtils.plural(bienes, "bien", "bienes") + " de esa factura",
                        "Se compraron juntos: proveedor, folio fiscal y fecha van a todos los de la factura; costo, vida útil, "
                            + "color, marca y modelo, solo a los del mismo nombre. Solo se llenan los que no lo tienen o tenían lo mismo que este; "
                            + "un dato distinto que alguien capturó no se toca. Quita la marca de lo que no quieras pasar.",
                        alCambiar).abrir(scene, servicio);
                }
                despues.run();
            },
            e -> {
                log.warn("No se pudo buscar la misma factura de {}: {}", guardado.getCodigo(), e.getMessage());
                despues.run();
            });
    }

    /** Bienes › «Copiar datos de la misma factura»: what one bien of a factura already
     *  has and its siblings lack. */
    public static void pendientes(Scene scene, ProductoService servicio, List<Producto> todos, Runnable alCambiar) {
        List<Cambio> propuesta = MismaFactura.pendientes(todos);
        if (propuesta.isEmpty()) {
            NotificacionUtil.info(scene, "No hay datos que copiar: los bienes de cada factura ya tienen lo mismo");
            return;
        }
        long facturas = propuesta.stream().map(c -> MismaFactura.clave(c.factura())).distinct().count();
        new MismaFacturaDialog(propuesta, false, "Copiar datos de la misma factura",
            "Lo que un bien ya tiene y a los demás de su factura les falta, en " + FormatUtils.plural(facturas, "factura", "facturas"),
            "Solo se llenan los bienes que no tienen ese dato, y solo cuando todos los de la factura que sí lo tienen dicen lo mismo. "
                + "Proveedor, folio fiscal y fecha van a toda la factura; costo, vida útil, color, marca y modelo, a los del mismo nombre. "
                + "Quita la marca de lo que no quieras pasar.",
            alCambiar).abrir(scene, servicio);
    }

    private void abrir(Scene scene, ProductoService servicio) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(titulo);
        ButtonType soloEste = new ButtonType(deUnBien ? "Solo a este" : "Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(soloEste);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(deUnBien ? 900 : 1100, pantalla.getWidth() * 0.94),
            Math.min(deUnBien ? 540 : 680, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2r-receipt", titulo, subtitulo, AppColors.PRIMARY, AppColors.PRIMARY_D);

        cambios.forEach(c -> elegidos.put(c, new SimpleBooleanProperty(true)));
        TableView<Cambio> tabla = new TableView<>(FXCollections.observableArrayList(cambios));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Cambio, Boolean> si = new TableColumn<>("Pasar");
        si.setCellValueFactory(c -> elegidos.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(78);
        si.setMinWidth(78);
        si.setMaxWidth(84);
        tabla.getColumns().add(si);
        if (!deUnBien) tabla.getColumns().add(columna("Factura", 140, Cambio::factura));
        TableColumn<Cambio, String> dato = columna("Dato", 150, c -> c.dato().etiqueta);
        dato.setMinWidth(140);
        tabla.getColumns().add(dato);
        tabla.getColumns().add(columna("Valor", 250, c -> c.dato().texto(c.nuevo())));
        TableColumn<Cambio, String> aQuienes = columna("Se pone a", 340, MismaFacturaDialog::aQuienes);
        // The whole list on hovering: the column cuts it.
        aQuienes.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                Cambio c = vacio || getTableRow() == null ? null : getTableRow().getItem();
                setText(vacio ? null : texto);
                setTooltip(c == null ? null : new Tooltip(c.bienes().stream().limit(40)
                    .map(p -> Objects.requireNonNullElse(p.getCodigo(), "?") + "  " + Objects.requireNonNullElse(p.getNombre(), ""))
                    .collect(Collectors.joining("\n")) + (c.bienes().size() > 40 ? "\n… y " + (c.bienes().size() - 40) + " más" : "")));
            }
        });
        tabla.getColumns().add(aQuienes);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label(explicacion);
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        aplicar.setGraphic(new FontIcon("mdi2c-check"));
        aplicar.getStyleClass().add("btn-primary");
        aplicar.setMinWidth(Region.USE_PREF_SIZE);
        Runnable contar = () -> {
            long n = elegidos(true).stream().flatMap(c -> c.bienes().stream()).map(Producto::getId).distinct().count();
            aplicar.setText(deUnBien ? "Ponérselo a " + FormatUtils.plural(n, "bien", "bienes")
                : "Copiar a " + FormatUtils.plural(n, "bien", "bienes"));
            aplicar.setDisable(n == 0);
        };
        elegidos.values().forEach(p -> p.addListener((o, a, b) -> contar.run()));
        contar.run();
        aplicar.setOnAction(e -> aplicar(dialog, scene, servicio));

        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label total = new Label(FormatUtils.plural(cambios.size(), "dato", "datos"));
        total.getStyleClass().add("table-count-label");
        HBox abajo = new HBox(10, total, hueco, aplicar);
        abajo.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(10, ayuda, tabla, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        javafx.application.Platform.runLater(aplicar::requestFocus);
        dialog.showAndWait();
    }

    private List<Cambio> elegidos(boolean si) {
        return elegidos.entrySet().stream().filter(e -> e.getValue().get() == si).map(Map.Entry::getKey).toList();
    }

    private void aplicar(Dialog<ButtonType> dialog, Scene scene, ProductoService servicio) {
        List<Cambio> cuales = elegidos(true);
        if (cuales.isEmpty()) return;
        aplicar.setDisable(true);
        Scene donde = scene != null ? scene : dialog.getDialogPane().getScene();
        dialog.close();
        DialogUtil.runAsyncWithProgress(donde, "Guardando en los bienes de la factura…",
            () -> MismaFactura.aplicar(servicio, cuales),
            r -> {
                if (alCambiar != null && !r.guardados().isEmpty()) alCambiar.run();
                String fallas = r.fallas().isEmpty() ? "" : "; " + FormatUtils.plural(r.fallas().size(), "no se pudo", "no se pudieron")
                    + " (" + r.fallas().get(0) + (r.fallas().size() > 1 ? "…" : "") + ")";
                r.fallas().forEach(f -> log.warn("Misma factura: {}", f));
                if (r.guardados().isEmpty()) {
                    NotificacionUtil.error(donde, "No se guardó ningún bien" + fallas);
                    return;
                }
                NotificacionUtil.exitoConAccion(donde,
                    FormatUtils.plural(r.guardados().size(), "bien actualizado", "bienes actualizados") + " con los datos de su factura" + fallas,
                    "Deshacer", () -> DialogUtil.runAsync(() -> MismaFactura.deshacer(servicio, r, cuales),
                        n -> {
                            if (alCambiar != null) alCambiar.run();
                            NotificacionUtil.info(donde, FormatUtils.plural(n, "bien regresó", "bienes regresaron") + " a como estaba" + (n == 1 ? "" : "n"));
                        },
                        ex -> NotificacionUtil.error(donde, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> NotificacionUtil.error(donde, "No se guardaron los datos: " + com.sibim.util.MensajeError.de(ex)));
    }

    /** "SOP/04, SOP/16 y 3 más (SILLA OPERATIVA)", or each one with its name when they differ:
     *  what tells a mistaken factura apart. */
    static String aQuienes(Cambio c) {
        Set<String> nombres = c.bienes().stream().map(p -> Objects.requireNonNullElse(p.getNombre(), "")).collect(Collectors.toCollection(LinkedHashSet::new));
        String nombre = nombres.size() == 1 ? nombres.iterator().next() : null;
        List<String> codigos = c.bienes().stream().map(p -> Objects.requireNonNullElse(p.getCodigo(), "?")
            + (nombre == null ? " " + corto(p.getNombre(), 22) : "")).toList();
        String lista = codigos.size() <= 4 ? unir(codigos)
            : String.join(", ", codigos.subList(0, 3)) + " y " + (codigos.size() - 3) + " más";
        return lista + (nombre == null || nombre.isBlank() ? "" : " (" + corto(nombre, 40) + ")");
    }

    private static String corto(String s, int max) {
        s = Objects.requireNonNullElse(s, "").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String unir(List<String> s) {
        if (s.size() <= 1) return String.join("", s);
        return String.join(", ", s.subList(0, s.size() - 1)) + " y " + s.get(s.size() - 1);
    }

    private static TableColumn<Cambio, String> columna(String titulo, double ancho, Function<Cambio, String> valor) {
        TableColumn<Cambio, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
