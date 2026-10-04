package com.sibim.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.sibim.model.Producto;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** A bien's QR carries its ficha (for any phone) with the código on the first
 *  line (for SIBIM's scanner). */
class QrFichaTest {

    @BeforeAll
    static void fx() throws Exception {
        CountDownLatch listo = new CountDownLatch(1);
        try { Platform.startup(listo::countDown); }
        catch (IllegalStateException yaIniciado) { listo.countDown(); }
        assertTrue(listo.await(10, TimeUnit.SECONDS));
    }

    private static Producto bien() {
        Producto p = new Producto();
        p.setCodigo("DCAT-0042");
        p.setNombre("Computadora de escritorio");
        p.setArea("Dirección de Catastro");
        p.setResguardante("Ana Pérez Núñez");
        p.setMarca("Dell"); p.setModelo("OptiPlex 3080"); p.setNumeroSerie("7XK2M93");
        p.setEstadoFisico("BUENO");
        return p;
    }

    @Test
    void contenido_codigoPrimeroYDatosDeLaFicha() {
        String qr = QrUtils.contenidoBien(bien());
        String[] lineas = qr.split("\n");
        assertEquals("DCAT-0042", lineas[0]);
        assertTrue(qr.contains("Área: Dirección de Catastro"));
        assertTrue(qr.contains("Resguardante: Ana Pérez Núñez"));
        assertTrue(qr.contains("Marca: Dell · Modelo: OptiPlex 3080 · Serie: 7XK2M93"));
        assertTrue(qr.contains("Estado: BUENO"));
        assertEquals("DCAT-0042", QrUtils.codigoLeido(qr));
        assertEquals("DCAT-0042", QrUtils.codigoLeido("  DCAT-0042 "), "un QR viejo (solo el código) sigue sirviendo");
    }

    @Test
    void seLeeCompletoAlTamanoDeLaEtiqueta() throws Exception {
        String qr = QrUtils.contenidoBien(bien());
        byte[] png = QrUtils.toPngBytes(qr, 160);
        var img = ImageIO.read(new ByteArrayInputStream(png));
        var leido = new MultiFormatReader().decode(
            new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(img.getWidth(), img.getHeight(),
                img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())))),
            Map.of(DecodeHintType.CHARACTER_SET, "UTF-8"));
        assertEquals(qr, leido.getText(), "acentos y ñ incluidos");
    }

    @Test
    void escaner_soloTomaLaPrimeraLinea_yNoEscribeElResto() throws Exception {
        List<String> leidos = new ArrayList<>();
        List<String> tecleados = new ArrayList<>();
        CountDownLatch fin = new CountDownLatch(1);
        Platform.runLater(() -> {
            Pane raiz = new Pane();
            Scene scene = new Scene(raiz);
            BarcodeScanner.attach(scene, leidos::add);
            raiz.addEventHandler(KeyEvent.KEY_TYPED, e -> tecleados.add(e.getCharacter()));
            // A scanner types "AB1\nXYZ\n" in one fast burst.
            for (String linea : new String[]{"AB1", "XYZ"}) {
                for (char c : linea.toCharArray()) {
                    Event.fireEvent(raiz, tecla(KeyEvent.KEY_PRESSED, String.valueOf(c), KeyCode.getKeyCode(String.valueOf(c))));
                    Event.fireEvent(raiz, tecla(KeyEvent.KEY_TYPED, String.valueOf(c), KeyCode.UNDEFINED));
                }
                Event.fireEvent(raiz, tecla(KeyEvent.KEY_PRESSED, "\r", KeyCode.ENTER));
            }
            fin.countDown();
        });
        assertTrue(fin.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("AB1"), leidos, "solo el código cuenta como lectura");
        assertEquals(List.of("A", "B", "1"), tecleados, "el resto de la ráfaga no se escribe en la pantalla");
    }

    private static KeyEvent tecla(javafx.event.EventType<KeyEvent> tipo, String texto, KeyCode code) {
        return new KeyEvent(tipo, tipo == KeyEvent.KEY_TYPED ? texto : "", texto,
            code == null ? KeyCode.UNDEFINED : code, false, false, false, false);
    }

    @Test
    void etiqueta_llevaCodigoBienYResguardante_yCabeEnElQrChico() {
        com.sibim.model.Producto p = new com.sibim.model.Producto();
        p.setCodigo("SGM-MM-3261-BIS-2024");
        p.setNombre("Computadora de escritorio con monitor de 24 pulgadas, teclado y ratón");
        p.setArea("Dirección de Tecnologías de la Información");
        p.setResguardante("María Guadalupe Hernández Martínez de la Cruz");
        String qr = QrUtils.contenidoEtiqueta(p);
        assertEquals("SGM-MM-3261-BIS-2024", QrUtils.codigoLeido(qr), "el escáner de SIBIM sigue leyendo solo el código");
        assertTrue(qr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= QrUtils.MAX_BYTES_ETIQUETA, qr);
        assertTrue(qr.lines().count() >= 2, "más que el puro código: " + qr);

        com.sibim.model.Producto corto = new com.sibim.model.Producto();
        corto.setCodigo("MB-003"); corto.setNombre("Archivero metálico 4 gavetas");
        corto.setArea("Secretaría General Municipal");
        assertEquals("MB-003\nArchivero metálico 4 gavetas\nSecretaría General Municipal", QrUtils.contenidoEtiqueta(corto));
    }
}
