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

    private static final java.time.format.DateTimeFormatter FECHA =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * What a bien's QR carries when its ficha can't be downloaded (no Storage,
     * see FichaPublica): what the label does NOT already show. The label
     * prints the descripción, marca, modelo, serie and área, so repeating them
     * told whoever scanned it nothing new; this is who answers for the bien,
     * where it is, its condition and its dates. Readable by any phone camera
     * with no app and no internet. The código goes alone on the first line — a
     * handheld scanner "types" the QR and ends at the first line break, so
     * SIBIM's own scanner still gets just the código (see BarcodeScanner,
     * which ignores the rest of the burst).
     */
    public static String contenidoBien(com.sibim.model.Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append(codigoDe(p));
        if (p.isDadoDeBaja()) {
            linea(sb, null, "DADO DE BAJA" + (p.getFechaBaja() != null ? " el " + p.getFechaBaja().format(FECHA) : ""));
            linea(sb, "Motivo", recortar(p.getMotivoBaja(), 80));
        }
        linea(sb, "Resguardante", p.getResguardante());
        linea(sb, "Ubicación", recortar(p.getUbicacion(), 60));
        linea(sb, "Estado físico", p.getEstadoFisico());
        linea(sb, "Adquirido", p.getFechaAdquisicion() != null ? p.getFechaAdquisicion().format(FECHA) : null);
        linea(sb, "Factura", p.getNumeroFactura());
        if (p.getFechaVencimiento() != null)
            linea(sb, p.getFechaVencimiento().isBefore(java.time.LocalDate.now()) ? "Garantía VENCIDA" : "Garantía hasta",
                p.getFechaVencimiento().format(FECHA));
        linea(sb, "Próxima revisión", p.getProximaRevision() != null ? p.getProximaRevision().format(FECHA) : null);
        // Nothing beyond the código is on record: say what it is, at least.
        if (sb.indexOf("\n") < 0) linea(sb, null, recortar(p.getNombre(), 70));
        return sb.toString();
    }

    /** One line to show under a QR saying what scanning it does, so nobody
     *  prints a sheet of labels expecting the ficha and gets text (or the reverse). */
    public static String queHace(String contenido) {
        return contenido != null && contenido.startsWith("http")
            ? "Al escanearlo con un teléfono se descarga la ficha del bien (PDF)."
            : "Al escanearlo muestra resguardante, ubicación, estado y fechas. Para que descargue la ficha "
                + "hace falta conexión y el almacenamiento de fotos configurado en esta PC.";
    }

    private static String codigoDe(com.sibim.model.Producto p) {
        return p.getCodigo() != null && !p.getCodigo().isBlank() ? p.getCodigo().trim() : "SIN CÓDIGO";
    }

    /**
     * The QR that downloads the bien's ficha: {@code urlFicha} with the código
     * after '#'. It has to be the link and nothing else — a phone camera only
     * offers to open a QR that is entirely a link — and the código rides in the
     * fragment (never sent to the server) so SIBIM's scanner still finds it.
     */
    public static String enlaceFicha(String urlFicha, com.sibim.model.Producto p) {
        return urlFicha + "#" + java.net.URLEncoder.encode(codigoDe(p), java.nio.charset.StandardCharsets.UTF_8)
            .replace("+", "%20").replace("%2F", "/");
    }

    /** Most bytes the QR of the official label may carry: it is printed 1.2 cm wide, and
     *  past this its modules get too fine for a phone to read off an office printout. */
    static final int MAX_BYTES_ETIQUETA = 84;

    /**
     * The short version of {@link #contenidoBien} for the official label, whose QR is tiny
     * (too small for the link to the ficha): código on the first line, then what the label
     * does not print — who holds the bien, its condition and when it was acquired — dropping
     * lines from the end until it fits. The label itself already prints the descripción,
     * marca, modelo, serie and área.
     */
    public static String contenidoEtiqueta(com.sibim.model.Producto p) {
        java.util.List<String> lineas = new java.util.ArrayList<>();
        lineas.add(codigoDe(p));
        if (p.getResguardante() != null && !p.getResguardante().isBlank())
            lineas.add("Resg: " + recortar(p.getResguardante(), 28));
        if (p.getEstadoFisico() != null && !p.getEstadoFisico().isBlank())
            lineas.add("Estado: " + recortar(p.getEstadoFisico(), 12));
        if (p.getFechaAdquisicion() != null) lineas.add("Adq: " + p.getFechaAdquisicion().format(FECHA));
        // Nothing of that on record: at least what it is.
        if (lineas.size() == 1 && p.getNombre() != null && !p.getNombre().isBlank())
            lineas.add(recortar(p.getNombre(), 34));
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
        // The QR that downloads the ficha is a link with the código after '#'.
        if ((t.startsWith("http://") || t.startsWith("https://")) && t.indexOf('#') > 0 && t.indexOf('\n') < 0) {
            try {
                return java.net.URLDecoder.decode(t.substring(t.indexOf('#') + 1).replace("+", "%2B"),
                    java.nio.charset.StandardCharsets.UTF_8).strip();
            } catch (IllegalArgumentException e) {
                return t.substring(t.indexOf('#') + 1).strip();
            }
        }
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
    