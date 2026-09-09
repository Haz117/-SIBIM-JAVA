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

    /** Returns a JavaFX Image with black modules on transparent background.
     *  Transparent renders as white on any opaque UI surface. */
    public static Image generateQr(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 1));
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
