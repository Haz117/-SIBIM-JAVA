package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.FotosRepetidas;
import com.sibim.service.FotosRepetidas.Grupo;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Bienes › Más › «Fotos repetidas»: the same picture on bienes that are not one lot. Each
 *  bien can be opened, have that photo taken off, or be joined with the other when they are
 *  the same bien («Son el mismo bien»). */
public final class FotosRepetidasDialog {

    private static final Logger log = LoggerFactory.getLogger(FotosRepetidasDialog.class);

    private final List<Grupo> grupos;
    private final List<Producto> inventario;
    private final ProductoService servicio;
    private final Runnable alCambiar;
    /** The content (MD5) of each photo URL read: «Quitar la foto» takes off every copy of the
     *  picture the bien has, whatever its file name. */
    private final Map<String, String> huellas;
    private final VBox lista = new VBox(10);
    private Dialog<ButtonType> dialog;

    private record Resultado(List<Producto> bienes, List<Grupo> grupos, Map<String, String> huellas) {}

    private FotosRepetidasDialog(Resultado r, ProductoService servicio, Runnable alCambiar) {
        this(r.grupos(), r.bienes(), r.huellas(), servicio, alCambiar);
    }

    private FotosRepetidasDialog(List<Grupo> grupos, List<Producto> inventario, Map<String, String> huellas,
                                 ProductoService servicio, Runnable alCambiar) {
        this.grupos = new ArrayList<>(grupos);
        this.huellas = huellas;
        this.inventario = inventario;
        this.servicio = servicio;
        this.alCambiar = alCambiar;
    }

    /** Reads the photos (the backup's copy on this PC, or downloaded once) and opens the list. */
    public static void show(Scene scene, ProductoService servicio, Runnable alCambiar) {
        int[] sinLeer = {0};
        DialogUtil.runAsyncWithProgress(scene, "Revisando las fotos (la primera vez puede tardar)…",
            avance -> {
                List<Producto> bienes = servicio.getAll();
                Map<String, List<String>> fotos = new com.sibim.repository.ProductoRepository().todasLasFotos();
                Map<String, String> huellas = FotosRepetidas.huellas(FotosRepetidas.urls(fotos), avance, sinLeer);
                Map<String, Producto> porId = new HashMap<>();
                bienes.forEach(p -> porId.put(p.getId(), p));
                return new Resultado(bienes, FotosRepetidas.agrupar(fotos, huellas, porId), huellas);
            },
            r -> {
                if (sinLeer[0] > 0) NotificacionUtil.advertencia(scene, FormatUtils.plural(sinLeer[0], "foto no se pudo leer", "fotos no se pudieron leer")
                    + "; se revisan la próxima vez");
                if (r.grupos().isEmpty()) {
                    NotificacionUtil.info(scene, "Ninguna foto se repite fuera de un lote de bienes iguales");
                    return;
                }
                new FotosRepetidasDialog(r, servicio, alCambiar).abrir();
            },
            e -> NotificacionUtil.error(scene, "No se pudieron revisar las fotos: " + com.sibim.util.MensajeError.de(e)));
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Fotos repetidas");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1000, pantalla.getWidth() * 0.94), Math.min(740, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2i-image-multiple-outline", "Fotos repetidas",
            FormatUtils.plural(grupos.size(), "foto está", "fotos están") + " en bienes que no son el mismo lote",
            AppColors.PRIMARY, AppColors.PRIMARY_D);
        Label ayuda = new Label("Primero las que están en bienes de distinto nombre: casi siempre es la foto de otro bien. "
            + "Una foto para varios bienes iguales de la misma persona no sale aquí. «Quitar la foto» la quita solo de ese bien; "
            + "si los dos registros son el mismo bien, júntalos.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        pintar();
        ScrollPane sp = new ScrollPane(lista);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(sp, Priority.ALWAYS);
        VBox cuerpo = new VBox(10, ayuda, sp);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.showAndWait();
    }

    private void pintar() {
        lista.getChildren().clear();
        for (Grupo g : grupos) lista.getChildren().add(tarjeta(g));
        if (grupos.isEmpty()) lista.getChildren().add(new Label("Ya no quedan fotos repetidas en esta lista."));
    }

    private HBox tarjeta(Grupo g) {
        ImageView img = new ImageView();
        img.setFitWidth(120);
        img.setFitHeight(120);
        img.setPreserveRatio(true);
        Image im = imagen(g.url());
        img.setImage(im);
        // Without the backup's copy nor internet the photo can't load: say so instead of a blank.
        Label sinVista = new Label("Sin vista previa", new FontIcon("mdi2i-image-off-outline"));
        sinVista.setContentDisplay(ContentDisplay.TOP);
        sinVista.getStyleClass().add("muted-sm");
        if (im == null) sinVista.setVisible(true);
        else sinVista.visibleProperty().bind(im.errorProperty());
        StackPane marco = new StackPane(sinVista, img);
        marco.setMinSize(124, 124);
        marco.setPrefSize(124, 124);
        // Deciding whether it is the photo of another bien needs it big.
        if (g.url() != null) {
            marco.setOnMouseClicked(e -> DialogUtil.showPhotoViewer(g.url(), g.bienes().get(0).getNombre()));
            marco.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(marco, new Tooltip("Ver la foto en grande"));
            com.sibim.util.AccessibilityUtils.asButton(marco, "Ver la foto en grande");
        }

        VBox filas = new VBox(6);
        for (Producto p : g.bienes()) filas.getChildren().add(fila(g, p));
        HBox.setHgrow(filas, Priority.ALWAYS);
        if (g.bienes().size() == 2) {
            Button juntar = new Button("Son el mismo bien…");
            juntar.setGraphic(new FontIcon("mdi2c-call-merge"));
            juntar.getStyleClass().add("btn-secondary");
            juntar.setOnAction(e -> JuntarBienesDialog.show(inventario, g.bienes().get(0), g.bienes().get(1), () -> {
                grupos.remove(g);
                pintar();
                if (alCambiar != null) alCambiar.run();
            }));
            filas.getChildren().add(juntar);
        }
        HBox t = new HBox(14, marco, filas);
        t.getStyleClass().add("trf-panel");
        t.setPadding(new Insets(10));
        t.setAlignment(Pos.TOP_LEFT);
        return t;
    }

    private HBox fila(Grupo g, Producto p) {
        Label codigo = new Label(p.getCodigo());
        codigo.getStyleClass().add("trf-codigo");
        codigo.setMinWidth(Region.USE_PREF_SIZE);
        Label nombre = new Label(p.getNombre());
        nombre.getStyleClass().add("org-child-label");
        nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
        Label quien = new Label(Objects.requireNonNullElse(p.getArea(), "") + " · "
            + Objects.requireNonNullElse(p.getResguardante(), "sin resguardante"));
        quien.getStyleClass().add("muted-sm");
        VBox textos = new VBox(2, new HBox(8, codigo, nombre), quien);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Button ver = new Button("Ver");
        ver.getStyleClass().add("btn-ghost");
        // Several «Ver» in a row: each one says which bien, for a screen reader.
        ver.setAccessibleText("Ver " + p.getCodigo() + " " + Objects.requireNonNullElse(p.getNombre(), ""));
        ver.setOnAction(e -> ProductoDetailDialog.show(p, dialog.getDialogPane().getScene(), new com.sibim.service.MovimientoService(), log));
        Button quitar = new Button("Quitar la foto");
        quitar.getStyleClass().add("btn-ghost");
        quitar.setAccessibleText("Quitar la foto de " + p.getCodigo());
        quitar.setOnAction(e -> quitarFoto(g, p));
        HBox f = new HBox(8, textos, ver, quitar);
        f.setAlignment(Pos.CENTER_LEFT);
        return f;
    }

    private void quitarFoto(Grupo g, Producto p) {
        if (!ConfirmacionUtil.confirmar("Quitar la foto", "Se quita esta foto de " + p.getCodigo() + " «" + p.getNombre()
                + "». Los demás bienes la conservan.\n\n¿Quitarla?", "Quitar la foto")) return;
        DialogUtil.runAsync(() -> {
                List<String> fotos = new ArrayList<>(servicio.getFotosByProductoId(p.getId()));
                // Every copy of the picture: the same content may be under another file name.
                List<String> quitadas = fotos.stream()
                    .filter(u -> Objects.equals(u, g.url()) || g.huella().equals(huellas.get(u))).toList();
                if (quitadas.isEmpty()) return false;
                fotos.removeAll(quitadas);
                servicio.saveFotos(p.getId(), fotos);
                Producto copia = p.copia();
                // The main photo changes only if it was this picture.
                if (p.getFotoUrl() == null || quitadas.contains(p.getFotoUrl()))
                    copia.setFotoUrl(fotos.isEmpty() ? null : fotos.get(0));
                copia.setFotosUrls(fotos);
                servicio.save(copia);
                return true;
            },
            quitada -> {
                if (!quitada) {
                    NotificacionUtil.advertencia(dialog.getDialogPane().getScene(), p.getCodigo() + " ya no tenía esa foto");
                    g.bienes().remove(p);
                    if (!g.distintos() || g.bienes().size() < 2) grupos.remove(g);
                    pintar();
                    return;
                }
                g.bienes().remove(p);
                if (g.bienes().size() < 2 || !g.distintos()) grupos.remove(g);
                pintar();
                if (alCambiar != null) alCambiar.run();
                NotificacionUtil.exito(dialog.getDialogPane().getScene(), "Foto quitada de " + p.getCodigo());
            },
            ex -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se quitó la foto: " + com.sibim.util.MensajeError.de(ex)));
    }

    private static Image imagen(String url) {
        if (url == null) return null;
        Path local = FotosRepetidas.enRespaldo(url, FotosRepetidas.carpetaRespaldo());
        if (local != null && Files.isRegularFile(local)) return new Image(local.toUri().toString(), 120, 120, true, true, true);
        try {
            String completa = url.startsWith("http") ? url : com.sibim.util.SupabaseStorage.publicUrl(url);
            return new Image(completa, 120, 120, true, true, true);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
