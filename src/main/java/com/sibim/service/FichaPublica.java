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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    // Seguridad Pública's bienes (armamento, C-2, patrullas) went without a
    // published ficha from 1.0.3; asked about it, the municipality wants
    // their labels and QR like every other área's (2026-10-15).

    /** Where the ficha of {@code p} lives once published. */
    static String url(Producto p) {
        return url(p.getId());
    }

    private static String url(String productoId) {
        return SupabaseStorage.publicUrl("fichas/" + productoId + ".pdf");
    }

    /**
     * Writes the ficha of {@code p} as it is now and puts it in Storage.
     * @return what its QR must carry — the link, or the data as text when it
     *         could not be published
     */
    public static String publicar(Producto p) {
        if (p.getId() == null || !SupabaseStorage.isAvailable()) return QrUtils.contenidoBien(p);
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
     *  and never its problem; nothing happens on a PC without Storage.
     *
     *  <p>They wait in line, {@link #AL_FONDO} at a time: Quitar títulos,
     *  Unir personas or Juntar resguardos change hundreds of bienes at once,
     *  and one thread per bien (up to 1.0.22) asked the database for
     *  hundreds of connections at the same moment — on 2026-11-06 Quitar
     *  títulos left 957 fichas showing the old name, and every other screen
     *  waited behind them. */
    public static void actualizarLuego(String productoId) {
        if (productoId == null || !SupabaseStorage.isAvailable()) return;
        COLA.encolar(productoId);
    }

    /** Fichas brought up to date at the same time in the background: the
     *  database lets the PC hold 6 connections, and the screens need the rest. */
    private static final int AL_FONDO = 2;

    private static final Cola COLA = new Cola(AL_FONDO, FichaPublica::actualizarAhora);

    /** Asks Storage first (no database): most bienes have no published ficha
     *  until their label is printed, and those need nothing else. */
    private static void actualizarAhora(String productoId) {
        try {
            if (!publicada(productoId)) return;
            new com.sibim.repository.ProductoRepository().findByIdOnline(productoId)
                .ifPresent(FichaPublica::publicar);
        } catch (Exception e) {
            log.warn("No se pudo actualizar la ficha publicada del bien {}: {}", productoId, e.getMessage());
        }
    }

    /** A line of bienes whose ficha must be written again, worked by a fixed
     *  number of threads. A bien already waiting is not added twice: when its
     *  turn comes it is read as it is by then, with every change made to it. */
    static final class Cola {
        private final ThreadPoolExecutor hilos;
        private final Set<String> esperando = ConcurrentHashMap.newKeySet();
        private final Consumer<String> trabajo;

        Cola(int cuantos, Consumer<String> trabajo) {
            AtomicInteger numero = new AtomicInteger();
            this.hilos = new ThreadPoolExecutor(cuantos, cuantos, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "sibim-fichas-" + numero.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            this.hilos.allowCoreThreadTimeOut(true);   // no threads left over while nothing waits
            this.trabajo = trabajo;
        }

        void encolar(String id) {
            if (!esperando.add(id)) return;
            hilos.execute(() -> {
                esperando.remove(id);   // a change from here on needs its own turn
                try {
                    trabajo.accept(id);
                } catch (RuntimeException e) {
                    log.warn("No se pudo actualizar la ficha publicada del bien {}: {}", id, e.getMessage());
                }
            });
        }
    }

    private static boolean publicada(Producto p) {
        return p.getId() != null && publicada(p.getId());
    }

    private static boolean publicada(String productoId) {
        if (!SupabaseStorage.isAvailable()) return false;
        try {
            HttpResponse<Void> r = HTTP.send(
                HttpRequest.newBuilder(URI.create(url(productoId))).timeout(Duration.ofSeconds(5))
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
