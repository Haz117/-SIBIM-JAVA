package com.sibim.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.Map;

public final class QrUtils {
    private QrUtils() {}

    public static Image generateQr(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 1));
            WritableImage image = new WritableImage(size, size);
            PixelWriter pw = image.getPixelWriter();
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return image;
        } catch (Exception e) {
            return null;
        }
    }
}
