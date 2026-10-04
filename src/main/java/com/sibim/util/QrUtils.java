package com.sibim.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class QrUtils {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(QrUtils.class);
    private QrUtils() {}

    /** UTF-8 so accents and ñ read right on any phone. */
    private static final Map<EncodeHintType, Object> OPCIONES =
        Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8");

    /**
     * What a bien's QR carries: its ficha técnica in short, readable by any
     * phone camera with no app and no internet. The código goes alone on the
     * first line — a handheld scanner "types" the QR and ends at the first
     * line break, so SIBIM's own scanner still gets just the código (see
     * BarcodeScanner, which ignores the rest of the burst).
     */
    public static String contenidoBien(com.sibim.model.Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getCodigo() != null && !p.getCodigo().isBlank() ? p.getCodigo().trim() : "SIN CÓDIGO");
        linea(sb, null, recortar(p.getNombre(), 70));
        linea(sb, "Área", p.getArea());
        linea(sb, "Resguardante", p.getResguardante());
        StringBuilder equipo = new StringBuilder();
        for (String[] kv : new String[][]{{"Marca", p.getMarca()}, {"Modelo", p.getModelo()}, {"Serie", p.getNumeroSerie()}}) {
            if (kv[1] != null && !kv[1].isBlank())
                equipo.append(equipo.length() > 0 ? " · " : "").append(kv[0]).append(": ").append(kv[1].trim());
        }
        linea(sb, null, equipo.toString());
        linea(sb, "Estado", p.isDadoDeBaja() ? "DADO DE BAJA" : p.getEstadoFisico());
        sb.append("\nInventario patrimonial · SIBIM");
        return sb.toString();
    }

    /** Most bytes the QR of the official label may carry: it is printed 1.2 cm wide, and
     *  past this its modules get too fine for a phone to read off an office printout. */
    static final int MAX_BYTES_ETIQUETA = 84;

    /**
     * The short version of {@link #contenidoBien} for the official label, whose QR is tiny:
     * código on the first line, then what the bien is and who has it (or its área), dropping
     * lines from the end until it fits. The label itself already prints marca, modelo and serie.
     */
    public static String contenidoEtiqueta(com.sibim.model.Producto p) {
        java.util.List<String> lineas = new java.util.ArrayList<>();
        lineas.add(p.getCodigo() != null && !p.getCodigo().isBlank() ? p.getCodigo().trim() : "SIN CÓDIGO");
        if (p.getNombre() != null && !p.getNombre().isBlank()) lineas.add(recortar(p.getNombre(), 30));
        if (p.getResguardante() != null && !p.getResguardante().isBlank())
            lineas.add("Resg: " + recortar(p.getResguardante(), 28));
        else if (p.getArea() != null && !p.getArea().isBlank())
            lineas.add(recortar(p.getArea(), 34));
        while (lineas.size() > 1
                && String.join("\n", lineas).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES_ETIQUETA)
            lineas.remove(lineas.size() - 1);
        return String.join("\n", lineas);
    }

    private static void linea(StringBuilder sb, String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) return;
        sb.append('\n');
        if (etiqueta != null) sb.append(etiqueta).append(": ");
        sb.append(valor.trim());
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** The código from something a scanner read: the first line of a SIBIM
     *  QR, or the whole text of an old QR / barcode that carried just it. */
    public static String codigoLeido(String leido) {
        if (leido == null) return "";
        String t = leido.strip();
        int salto = t.indexOf('\n');
        return (salto >= 0 ? t.substring(0, salto) : t).strip();
    }

    /** Returns a JavaFX Image with black modules on transparent background.
     *  Transparent renders as white on any opaque UI surface. */
    public static Image generateQr(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                OPCIONES);
            WritableImage image = new WritableImage(size, size);
            PixelWriter pw = image.getPixelWriter();
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.TRANSPARENT);
                }
            }
            return image;
        } catch (Exception e) {
            log.error("Error generando QR para content='{}', size={}", content, size, e);
            return null;
        }
    }

    /** Returns QR code as PNG bytes suitable for embedding in iText PDFs. */
    public static byte[] toPngBytes(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                OPCIONES);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando QR PNG bytes para content='{}'", content, e);
            return null;
        }
    }

    /** Saves a QR Image (transparent = white background) as a white-background PNG.
     *  TYPE_INT_RGB has no alpha channel, so transparent pixels must be mapped
     *  explicitly to white — otherwise they become black (RGB 0,0,0). */
    public static void saveAsPng(Image img, java.io.File dest) throws java.io.IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        javafx.scene.image.PixelReader pr = img.getPixelReader();
        java.awt.image.BufferedImage bi =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = pr.getColor(x, y);
                int rgb = c.getOpacity() < 0.5
                    ? 0xFFFFFF                                       // transparent → white
                    : ((int)(c.getRed()   * 255) << 16)
                    | ((int)(c.getGreen() * 255) << 8)
                    |  (int)(c.getBlue()  * 255);
                bi.setRGB(x, y, rgb);
            }
        }
        javax.imageio.ImageIO.write(bi, "PNG", dest);
    }
}
    