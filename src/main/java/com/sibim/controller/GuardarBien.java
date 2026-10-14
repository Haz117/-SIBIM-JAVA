package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;
import org.slf4j.Logger;

/** Saving a bien edited in a form, the same from every screen: the photos
 *  resized and uploaded (or kept on this PC until Storage is reachable), the
 *  record, its photo list, and the ficha its QR downloads. */
final class GuardarBien {

    private GuardarBien() {}

    /** The network part: call it off the FX thread (DialogUtil.runAsync). */
    static Producto enFondo(ProductoService servicio, Producto p, String facturaAnterior, Logger log)
            throws Exception {
        ProductoDialogFactory.procesarArchivos(p, facturaAnterior, ProductosController.THUMBNAIL_CACHE, log);
        Producto saved = servicio.save(p);
        servicio.saveFotos(saved.getId(), p.getFotosUrls());
        // The ficha its QR downloads must say what the bien says now.
        com.sibim.util.AppExecutor.submit(() -> com.sibim.service.FichaPublica.actualizarSiPublicada(saved));
        return saved;
    }

    /** The áreas' short form (ActualizarBienDialog): opens it with the bien's
     *  photos and the known ubicaciones, saves, and hands the saved bien to
     *  {@code alGuardar}. */
    static void actualizarDatos(Scene scene, ProductoService servicio, Producto bien, Logger log,
                                java.util.function.Consumer<Producto> alGuardar) {
        record Datos(java.util.List<String> fotos, java.util.List<String> ubicaciones) {}
        com.sibim.util.DialogUtil.runAsyncWithProgress(scene, "Abriendo el bien…",
            () -> new Datos(servicio.getFotosByProductoId(bien.getId()), servicio.getUbicaciones()),
            d -> com.sibim.controller.dialogs.ActualizarBienDialog.show(bien, d.fotos(), d.ubicaciones())
                .ifPresent(p -> com.sibim.util.DialogUtil.runAsync(
                    () -> enFondo(servicio, p, bien.getFacturaUrl(), log),
                    saved -> {
                        NotificacionUtil.exito(scene, "Datos del bien actualizados");
                        avisarSiFotoQuedoLocal(scene, p);
                        if (alGuardar != null) alGuardar.accept(saved);
                    },
                    e -> {
                        log.warn("No se pudieron actualizar los datos de '{}'", bien.getNombre(), e);
                        NotificacionUtil.error(scene, e instanceof ProductoService.ValidationException
                            ? e.getMessage()
                            : "No se pudieron guardar los datos. Verifica la conexión e inténtalo de nuevo.");
                    })),
            e -> NotificacionUtil.error(scene, "No se pudo abrir el bien. Verifica la conexión."));
    }

    /** Connected, yet a photo stayed as a file on this PC: Storage isn't
     *  configured here (or the upload failed), so no other PC will see it. */
    static void avisarSiFotoQuedoLocal(Scene scene, Producto p) {
        if (com.sibim.db.DatabaseConfig.isOfflineMode() || com.sibim.db.DatabaseConfig.isDemoMode()) return;
        if (p.getFotosUrls().stream().noneMatch(com.sibim.util.ImageUtils::soloEnEstaPc)) return;
        NotificacionUtil.advertencia(scene,
            "La foto se guardó solo en esta computadora: las demás PCs no la verán. "
            + (com.sibim.util.SupabaseStorage.isAvailable()
                ? "No se pudo subir al almacenamiento; se reintentará sola."
                : "Falta SUPABASE_ANON_KEY (la clave pública) en el .env de esta PC."));
    }
}
