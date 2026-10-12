package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.QrUtils;
import com.sibim.util.SupabaseStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The ficha of a bien that its QR downloads. Scanning a label with any phone
 * opens a PDF kept in Storage, next to the photos: what the bien is, its
 * photos, who holds it, where it is, its condition and its dates. It carries
 * no amounts and no movement history, because whoever stands in front of the
 * label can open it.
 *
 * Without Storage (no public key in the .env, no connection, demo) there is
 * nowhere to keep it: the QR then carries those data as text instead
 * ({@link QrUtils#contenidoBien}), so a label printed either way still says
 * something the label itself does not.
 */
public final class FichaPublica {

    private static final Logger log = LoggerFactory.getLogger(FichaPublica.class);

    /** Bienes whose ficha is written and uploaded at the same time. */
    private static final int EN_PARALELO = 4;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private FichaPublica() {}

    /** Secretarías whose bienes never get a ficha anyone with the link can
     *  open: armamento, the C-2's cameras and antennas, patrol cars, who holds
     *  each radio. Their QR carries the data as text, on the label only. */
    static final java.util.Set<String> RESERVADAS = java.util.Set.of("Secretaría de Seguridad Pública y Tránsito");

    /** True for a bien of a reserved secretaría or of any of its direcciones. */
    static boolean reservado(Producto p) {
        String area = p.getArea();
        if (area == null) return false;
        if (RESERVADAS.contains(area)) return true;
        return com.sibim.config.Areas.catalogo().buscar(area)
            .map(e -> e.padre() != null && RESERVADAS.contains(e.padre())).orElse(false);
    }

    /** Where the ficha of {@code p} lives once published. */
    static String url(Producto p) {
        return SupabaseStorage.publicUrl("fichas/" + p.getId() + ".pdf");
    }

    /**
     * Writes the ficha of {@code p} as it is now and puts it in Storage.
     * @return what its QR must carry — the link, or the data as text when it
     *         could not be published
     */
    public static String publicar(Producto p) {
        if (p.getId() == null || reservado(p) || !SupabaseStorage.isAvailable()) return QrUtils.contenidoBien(p);
        File pdf = null;
        try {
            String url = url(p);
            pdf = new ReporteFichaTecnicaService().exportFichaPublica(p, url);
            SupabaseStorage.upload(pdf, "fichas/" + p.getId() + ".pdf", "application/pdf");
            return QrUtils.enlaceFicha(url, p);
        } catch (Exception e) {
            log.warn("No se pudo publicar la ficha del bien {}: {}", p.getCodigo(), e.getMessage());
            return QrUtils.contenidoBien(p);
        } finally {
            if (pdf != null && !pdf.delete()) pdf.deleteOnExit();
        }
    }

    /** {@link #publicar} for many bienes (a sheet of labels), by id. */
    public static Map<String, String> publicar(List<Producto> bienes) {
        Map<String, String> contenidos = new HashMap<>();
        if (!SupabaseStorage.isAvailable()) {
            for (Producto p : bienes) contenidos.put(p.getId(), QrUtils.contenidoBien(p));
            return contenidos;
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(EN_PARALELO)) {
            Map<String, Future<String>> pendientes = new HashMap<>();
            for (Producto p : bienes) pendientes.put(p.getId(), pool.submit(() -> publicar(p)));
            for (Producto p : bienes) {
                try {
                    contenidos.put(p.getId(), pendientes.get(p.getId()).get());
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    contenidos.put(p.getId(), QrUtils.contenidoBien(p));
                }
            }
        }
        return contenidos;
    }

    /**
     * What the QR of {@code p} carries without publishing anything: the link
     * if its ficha is already in Storage, the data as text otherwise. For the
     * screens that only show the QR.
     */
    public static String contenidoQr(Producto p) {
        return publicada(p) ? QrUtils.enlaceFicha(url(p), p) : QrUtils.contenidoBien(p);
    }

    /** Brings the published ficha up to date after the bien changed. Does
     *  nothing for a bien whose ficha was never published. */
    public static void actualizarSiPublicada(Producto p) {
        if (publicada(p)) publicar(p);
    }

    /** The same after a change made anywhere but the form — a transfer, a
     *  baja, a resguardo: the label still downloads the ficha, and it went on
     *  showing the old área, código or resguardante. Off the caller's thread
     *  and never its problem; nothing happens on a PC without Storage. */
    public static void actualizarLuego(String productoId) {
        if (productoId == null || !SupabaseStorage.isAvailable()) return;
        com.sibim.util.AppExecutor.submit(() -> {
            try {
                new com.sibim.repository.ProductoRepository().findByIdOnline(productoId)
                    .ifPresent(FichaPublica::actualizarSiPublicada);
            } catch (Exception e) {
                log.warn("No se pudo actualizar la ficha publicada del bien {}: {}", productoId, e.getMessage());
            }
        });
    }

    private static boolean publicada(Producto p) {
        if (p.getId() == null || reservado(p) || !SupabaseStorage.isAvailable()) return false;
        try {
            HttpResponse<Void> r = HTTP.send(
                HttpRequest.newBuilder(URI.create(url(p))).timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
