package com.sibim.controller;

import com.sibim.controller.dialogs.NuevoResguardoDialog;
import com.sibim.controller.dialogs.ResguardantePdfDialog;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.Permisos;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

import java.util.List;
import java.util.function.BiConsumer;

/** Everything about one person, from wherever their name shows up (Bienes, Resguardos,
 *  Ctrl+K, Reportes): their resguardos, the PDF, what they hold without a resguardo and
 *  their bienes. It was spread across three screens. */
public final class FichaPersona {

    private static final int ESPERA_DOBLE_CLIC_MS = 280;

    private FichaPersona() {}

    /** Loads what it needs, then opens on that person; null opens on nobody yet. */
    public static void abrir(String nombre, Scene scene) {
        if (scene == null) return;
        DialogUtil.runAsyncWithProgress(scene, "Cargando resguardantes…",
            () -> java.util.Map.entry(new ResguardoService().getAll(), new ProductoService().getAll()),
            datos -> mostrar(datos.getKey(), datos.getValue(), nombre, scene),
            e -> NotificacionUtil.error(scene, "No se pudieron cargar los resguardos"));
    }

    static void mostrar(List<Resguardo> resguardos, List<Producto> bienes, String nombre, Scene scene) {
        List<ResguardoService.Resguardante> personas = ResguardoService.porResguardante(resguardos, bienes);
        if (personas.isEmpty()) { NotificacionUtil.advertencia(scene, "No hay bienes con resguardante"); return; }
        ResguardantePdfDialog.show(personas, nombre,
            (persona, elegidos, sueltos) -> generar(persona, elegidos, sueltos, scene),
            !Permisos.creaResguardos() ? null : persona ->
                NuevoResguardoDialog.show(bienes, resguardos, persona.nombre(), scene, resguardo -> {
                    if (ConfirmacionUtil.elegir("Exportar PDF", "¿Deseas abrir el PDF del resguardo ahora?", "Abrir el PDF", "Ahora no"))
                        generarUno(resguardo, scene);
                }),
            persona -> verSusBienes(persona.nombre()),
            !com.sibim.session.SessionManager.isAdmin() ? null : persona -> corregirNombre(persona, bienes, scene));
    }

    /** Patrimonio fixes how the name is written ("GÓNZALEZ"), everywhere at once, and
     *  the ficha opens again on the new name; Deshacer puts it back. */
    private static void corregirNombre(ResguardoService.Resguardante persona, List<Producto> bienes, Scene scene) {
        javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(persona.nombre());
        DialogUtil.applyOwner(d);
        d.setTitle("Corregir nombre");
        d.setHeaderText("Se corrige en sus bienes, en sus resguardos vigentes y donde sea titular de área. "
            + "Los resguardos cancelados y los bienes dados de baja se quedan como se firmaron.");
        d.setContentText("Nombre como debe quedar:");
        DialogUtil.conEncabezado(d, "mdi2a-account-edit-outline");
        d.getEditor().setPrefColumnCount(32);
        javafx.scene.Node ok = d.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        if (ok instanceof javafx.scene.control.Button b) b.setText("Corregir");
        ok.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            String t = d.getEditor().getText() == null ? "" : d.getEditor().getText().trim();
            return t.isEmpty() || t.equals(persona.nombre());
        }, d.getEditor().textProperty()));
        d.showAndWait().map(String::trim).filter(t -> !t.isEmpty() && !t.equals(persona.nombre())).ifPresent(nuevo -> {
            com.sibim.service.CalidadDatosService calidad = new com.sibim.service.CalidadDatosService();
            List<Producto> activos = bienes.stream().filter(p -> p.getFechaBaja() == null).toList();
            DialogUtil.runAsyncWithProgress(scene, "Corrigiendo el nombre…",
                () -> calidad.corregirNombre(persona, nuevo, activos),
                c -> {
                    String nombre = c.union().nombre();
                    NotificacionUtil.exitoConAccion(scene, "Nombre corregido a «" + nombre + "» en "
                            + com.sibim.util.FormatUtils.plural(c.union().cuantosBienes(), "bien", "bienes") + " y "
                            + com.sibim.util.FormatUtils.plural(c.cuantosResguardos(), "resguardo", "resguardos"),
                        "Deshacer", () -> DialogUtil.runAsync(() -> { calidad.deshacerCorreccion(c); return null; },
                            r -> NotificacionUtil.exito(scene, "Se regresó el nombre «" + persona.nombre() + "»"),
                            e -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(e))));
                    abrir(nombre, scene);
                },
                e -> NotificacionUtil.error(scene, "No se pudo corregir el nombre: " + com.sibim.util.MensajeError.de(e)));
        });
    }

    private static void generar(ResguardoService.Resguardante persona, List<Resguardo> elegidos, boolean sueltos, Scene scene) {
        DialogUtil.runAsyncWithProgress(scene, "Generando reporte…",
            () -> new ResguardoService().exportarDePersona(persona, elegidos, sueltos),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF: " + com.sibim.util.MensajeError.de(e)));
    }

    private static void generarUno(Resguardo r, Scene scene) {
        DialogUtil.runAsyncWithProgress(scene, "Generando reporte…",
            () -> new ResguardoService().exportarPdf(r),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF: " + com.sibim.util.MensajeError.de(e)));
    }

    /** Bienes, opened on a search of that name. */
    private static void verSusBienes(String nombre) {
        NavigationContext.setPendingBusqueda(nombre);
        MainController mc = MainController.getInstance();
        if (mc != null) mc.navigateToView("productos");
    }

    /** A column of names: a click on the name opens that person; anywhere else in the
     *  cell selects the row as always. */
    static <S> void columnaDeNombre(TableColumn<S, String> col) {
        columnaDeNombre(col, FichaPersona::abrir);
    }

    static <S> void columnaDeNombre(TableColumn<S, String> col, BiConsumer<String, Scene> alAbrir) {
        col.setCellFactory(c -> new TableCell<>() {
            private final Label nombre = new Label();
            // The table's double click is "ver detalle", also on top of the name: a lone
            // click has to wait a moment to know it is one.
            private final PauseTransition espera = new PauseTransition(Duration.millis(ESPERA_DOBLE_CLIC_MS));
            {
                nombre.getStyleClass().add("celda-persona");
                nombre.setTooltip(new Tooltip("Ver a esta persona: sus resguardos, su PDF y sus bienes"));
                nombre.maxWidthProperty().bind(widthProperty().subtract(16));
                nombre.setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    if (e.getClickCount() > 1) { espera.stop(); return; }
                    e.consume();
                    String quien = nombre.getText();
                    espera.setOnFinished(f -> alAbrir.accept(quien, getScene()));
                    espera.playFromStart();
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                boolean hay = !empty && v != null && !v.isBlank() && !"—".equals(v);
                nombre.setText(hay ? v : "");
                setGraphic(hay ? nombre : null);
                setText(hay || empty ? null : "—");
            }
        });
    }
}
