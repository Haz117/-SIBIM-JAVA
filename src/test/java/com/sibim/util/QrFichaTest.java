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
    void contenido_codigoPrimero_yLoQueLaEtiquetaNoMuestra() {
        Producto p = bien();
        p.setUbicacion("Planta alta, cubículo 3");
        p.setFechaAdquisicion(java.time.LocalDate.of(2023, 3, 15));
        p.setNumeroFactura("A-1182");
        p.setFechaVencimiento(java.time.LocalDate.now().plusYears(1));
        String qr = QrUtils.contenidoBien(p);
        String[] lineas = qr.split("\n");
        assertEquals("DCAT-0042", lineas[0]);
        assertTrue(qr.contains("Resguardante: Ana Pérez Núñez"));
        assertTrue(qr.contains("Ubicación: Planta alta, cubículo 3"));
        assertTrue(qr.contains("Estado físico: BUENO"));
        assertTrue(qr.contains("Adquirido: 15/03/2023"));
        assertTrue(qr.contains("Factura: A-1182"));
        assertTrue(qr.contains("Garantía hasta: "));
        // Already printed on the label: repeating it told the reader nothing.
        assertFalse(qr.contains("Computadora de escritorio"), qr);
        assertFalse(qr.contains("Dell") || qr.contains("7XK2M93") || qr.contains("Catastro"), qr);
        assertEquals("DCAT-0042", QrUtils.codigoLeido(qr));
        assertEquals("DCAT-0042", QrUtils.codigoLeido("  DCAT-0042 "), "un QR viejo (solo el código) sigue sirviendo");
    }

    @Test
    void bienSinMasDatos_alMenosDiceQueEs() {
        Producto p = new Producto();
        p.setCodigo("MB-003"); p.setNombre("Archivero metálico 4 gavetas");
        assertEquals("MB-003\nArchivero metálico 4 gavetas", QrUtils.contenidoBien(p));
    }

    /** The QR that downloads the ficha is only a link (so a phone offers to
     *  open it) and SIBIM's scanner still finds the código in it. */
    @Test
    void enlaceALaFicha_esSoloUnEnlace_yElEscanerSacaElCodigo() {
        Producto p = bien();
        p.setCodigo("SGM/07");
        String qr = QrUtils.enlaceFicha("https://x.supabase.co/storage/v1/object/public/sibim-fotos/fichas/abc.pdf", p);
        assertEquals("https://x.supabase.co/storage/v1/object/public/sibim-fotos/fichas/abc.pdf#SGM/07", qr);
        assertEquals("SGM/07", QrUtils.codigoLeido(qr));
        p.setCodigo("MB 12-Ñ");
        assertEquals("MB 12-Ñ", QrUtils.codigoLeido(QrUtils.enlaceFicha("https://x/f.pdf", p)));
        assertTrue(QrUtils.queHace(qr).contains("descarga la ficha"));
        assertTrue(QrUtils.queHace(QrUtils.contenidoBien(p)).contains("resguardante"));
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
        assertTrue(qr.contains("Resg: "), "quién lo tiene no viene impreso en la etiqueta: " + qr);
        assertFalse(qr.contains("Computadora"), "la descripción ya está impresa en la etiqueta: " + qr);

        com.sibim.model.Producto completo = new com.sibim.model.Producto();
        completo.setCodigo("MB-003"); completo.setNombre("Archivero metálico 4 gavetas");
        completo.setResguardante("Luis Mora"); completo.setEstadoFisico("REGULAR");
        completo.setFechaAdquisicion(java.time.LocalDate.of(2021, 11, 2));
        assertEquals("MB-003\nResg: Luis Mora\nEstado: REGULAR\nAdq: 02/11/2021", QrUtils.contenidoEtiqueta(completo));

        com.sibim.model.Producto sinDatos = new com.sibim.model.Producto();
        sinDatos.setCodigo("MB-004"); sinDatos.setNombre("Archivero metálico 4 gavetas");
        assertEquals("MB-004\nArchivero metálico 4 gavetas", QrUtils.contenidoEtiqueta(sinDatos));
    }
}
