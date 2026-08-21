package com.sibim.util;

import io.github.cdimascio.dotenv.Dotenv;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public final class ImageUtils {

    private static final int MAX_DIMENSION = 1200;
    private static final float JPEG_QUALITY = 0.85f;
    private static final long MAX_SOURCE_BYTES = 25L * 1024 * 1024;

    private static Path storageDir;

    private ImageUtils() {}

    /**
     * Where product photos are copied to on save, and read back from (the
     * value stored in {@code producto.foto_url} is an absolute path under
     * this directory). Defaults to a per-machine folder under the user's
     * home — fine for a single-PC/dev setup, but in a real deployment with
     * several PCs sharing one Postgres, a photo saved on one machine would
     * be invisible (broken path) on every other machine unless this points
     * to a shared location (a mapped network drive or UNC path) reachable
     * identically from all of them. Configurable via IMG_DIR in .env / env
     * vars for exactly that reason — see README.
     */
    public static synchronized Path storageDir() {
        if (storageDir == null) {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            String configured = dotenv.get("IMG_DIR");
            if (configured == null || configured.isBlank()) configured = System.getenv("IMG_DIR");
            storageDir = (configured != null && !configured.isBlank())
                ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".sibim", "imagenes");
        }
        return storageDir;
    }

    /** Lets callers reject an oversized file at selection time, before
     *  attempting to load it into memory (a multi-GB or malformed image
     *  could otherwise spike memory or hang the UI thread). */
    public static boolean exceedsMaxSize(File src) {
        return src.length() > MAX_SOURCE_BYTES;
    }

    public static long maxSourceBytes() { return MAX_SOURCE_BYTES; }

    /**
     * Resizes (only if larger than {@link #MAX_DIMENSION} on its longest side) and
     * re-encodes the source image as JPEG at dest. Keeps stored product photos small
     * regardless of the original camera resolution/file size.
     */
    public static void resizeAndSave(File src, File dest) throws IOException {
        if (exceedsMaxSize(src)) {
            throw new IOException("La imagen pesa " + (src.length() / (1024 * 1024))
                + " MB — el máximo permitido es " + (MAX_SOURCE_BYTES / (1024 * 1024)) + " MB");
        }
        BufferedImage original = ImageIO.read(src);
        if (original == null) throw new IOException("Formato de imagen no soportado");

        int w = original.getWidth(), h = original.getHeight();
        BufferedImage rgb;
        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            double scale = MAX_DIMENSION / (double) Math.max(w, h);
            int newW = Math.max(1, (int) Math.round(w * scale));
            int newH = Math.max(1, (int) Math.round(h * scale));
            rgb = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, newW, newH, Color.WHITE, null);
            g.dispose();
        } else {
            // Flatten to RGB (JPEG has no alpha channel) even when no resize is needed.
            rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(original, 0, 0, Color.WHITE, null);
            g.dispose();
        }
        writeJpeg(rgb, dest);
    }

    private static void writeJpeg(BufferedImage img, File dest) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("No hay codificador JPEG disponible");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(dest)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
