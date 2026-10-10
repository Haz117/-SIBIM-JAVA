package com.sibim.service;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.sibim.model.Producto;
import com.sibim.util.SupabaseStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A bien's photos, ready to put in a PDF. The main photo comes first, then
 * the gallery, without repeats. Photos in Supabase Storage are downloaded
 * with a timeout, and every photo is scaled down to the size the format
 * needs, so a report with many bienes doesn't carry camera-sized files.
 * A photo that can't be read is skipped (and logged): it never fails the
 * report.
 */
final class FotosPdf {

    private static final Logger log = LoggerFactory.getLogger(FotosPdf.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private FotosPdf() {}

    /** Up to {@code max} photos of {@code p}, each at most {@code maxLado} px on its longest side. */
    static List<ImageData> de(Producto p, int max, int maxLado) {
        List<ImageData> out = new ArrayList<>();
        List<String> rutas = rutas(p);
        if (max > 1) rutas = conGaleria(p, rutas);
        for (String ruta : rutas) {
            if (out.size() >= max) break;
            ImageData img = cargar(ruta, maxLado, p.getCodigo());
            if (img != null) out.add(img);
        }
        return out;
    }

    /** The main photo of each bien (by id), fetched in parallel; bienes without one are absent. */
    static Map<String, ImageData> principales(List<Producto> bienes, int maxLado) {
        Map<String, ImageData> out = new HashMap<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<List<ImageData>>> pendientes = new HashMap<>();
            for (Producto p : bienes)
                if (!rutas(p).isEmpty()) pendientes.put(p.getId(), pool.submit(() -> de(p, 1, maxLado)));
            for (var e : pendientes.entrySet()) {
                try {
                    List<ImageData> fotos = e.getValue().get();
                    if (!fotos.isEmpty()) out.put(e.getKey(), fotos.get(0));
                } catch (Exception ex) {
                    log.warn("No se pudo preparar la foto del bien {}: {}", e.getKey(), ex.getMessage());
                }
            }
        }
        return out;
    }

    private static List<String> rutas(Producto p) {
        LinkedHashSet<String> rutas = new LinkedHashSet<>();
        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) rutas.add(p.getFotoUrl().trim());
        if (p.getFotosUrls() != null)
            for (String u : p.getFotosUrls()) if (u != null && !u.isBlank()) rutas.add(u.trim());
        return new ArrayList<>(rutas);
    }

    /** A bien read from a list (the table, a report) only carries its main
     *  photo: the gallery lives in product_fotos. Formats that show several
     *  photos fetch it here, or they would print the main one alone no matter
     *  how many were added. Best-effort: without it the format keeps what it has. */
    private static List<String> conGaleria(Producto p, List<String> rutas) {
        if (p.getId() == null || (p.getFotosUrls() != null && !p.getFotosUrls().isEmpty())) return rutas;
        try {
            LinkedHashSet<String> todas = new LinkedHashSet<>(rutas);
            for (String u : new com.sibim.repository.ProductoRepository().findFotos(p.getId()))
                if (u != null && !u.isBlank()) todas.add(u.trim());
            return new ArrayList<>(todas);
        } catch (Exception e) {
            log.debug("No se pudo leer la galería del bien {}: {}", p.getCodigo(), e.getMessage());
            return rutas;
        }
    }

    private static ImageData cargar(String ruta, int maxLado, String codigo) {
        try {
            byte[] bytes;
            if (SupabaseStorage.isRemoteUrl(ruta)) {
                HttpResponse<byte[]> r = HTTP.send(
                    HttpRequest.newBuilder(URI.create(ruta)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) {
                    log.warn("La foto {} del bien {} respondió HTTP {}", ruta, codigo, r.statusCode());
                    return null;
                }
                bytes = r.body();
            } else if (new File(ruta).isFile()) {
                bytes = Files.readAllBytes(new File(ruta).toPath());
            } else {
                return null;   // a path from another PC: nothing to show here
            }
            return ImageDataFactory.create(reducir(bytes, maxLado));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("No se pudo cargar la foto '{}' del bien {}: {}", ruta, codigo, e.getMessage());
            return null;
        }
    }

    /** Re-encodes as JPEG no larger than {@code maxLado}; returns the input if it can't be decoded. */
    static byte[] reducir(byte[] bytes, int maxLado) throws java.io.IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) return bytes;   // a format ImageIO doesn't know; let iText try it as is
        int w = src.getWidth(), h = src.getHeight();
        double escala = Math.min(1.0, (double) maxLado / Math.max(w, h));
        if (escala >= 1.0 && bytes.length < 400_000) return bytes;
        int nw = Math.max(1, (int) Math.round(w * escala)), nh = Math.max(1, (int) Math.round(h * escala));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);   // transparent PNGs get a white, not black, background
            g.fillRect(0, 0, nw, nh);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.82f);
            writer.write(null, new IIOImage(dst, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
