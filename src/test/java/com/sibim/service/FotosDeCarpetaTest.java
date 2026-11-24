package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.FotosDeCarpeta.Foto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** «Fotos de una carpeta» and its printed list, with cases like the visits of 20-24 nov 2026. */
class FotosDeCarpetaTest {

    @org.junit.jupiter.api.BeforeAll static void demo()   { com.sibim.db.DatabaseConfig.setDemoMode(true); }
    @org.junit.jupiter.api.AfterAll  static void noDemo() { com.sibim.db.DatabaseConfig.setDemoMode(false); }

    @TempDir Path dir;

    /** The start of a JPEG with an EXIF that says when it was taken (DateTimeOriginal); no image after. */
    private static byte[] jpegConFecha(String fecha, boolean intel) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        java.util.function.BiConsumer<Integer, Integer> num = (v, bytes) -> {
            for (int k = 0; k < bytes; k++) {
                int corrimiento = intel ? 8 * k : 8 * (bytes - 1 - k);
                tiff.write((v >> corrimiento) & 0xFF);
            }
        };
        tiff.writeBytes(intel ? new byte[]{'I', 'I'} : new byte[]{'M', 'M'});
        num.accept(42, 2);
        num.accept(8, 4);                                     // IFD0 right after the header
        num.accept(1, 2);                                     // one entry: the pointer to the EXIF IFD
        num.accept(0x8769, 2); num.accept(4, 2); num.accept(1, 4); num.accept(26, 4);
        num.accept(0, 4);                                     // no next IFD
        num.accept(1, 2);                                     // EXIF IFD at 26: DateTimeOriginal
        num.accept(0x9003, 2); num.accept(2, 2); num.accept(20, 4); num.accept(44, 4);
        num.accept(0, 4);
        tiff.writeBytes((fecha + "\0").getBytes(StandardCharsets.US_ASCII));   // at 44
        byte[] t = tiff.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1});
        int largo = 2 + 6 + t.length;
        out.write(largo >> 8); out.write(largo & 0xFF);
        out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(t);
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD9});
        return out.toByteArray();
    }

    private File archivo(String nombre, byte[] contenido, LocalDateTime modificado) throws Exception {
        Path p = dir.resolve(nombre);
        Files.write(p, contenido);
        p.toFile().setLastModified(modificado.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return p.toFile();
    }

    @Test
    void laFechaEnQueSeTomo_sale_delExif_enLosDosOrdenesDeBytes() throws Exception {
        File android = archivo("a.jpg", jpegConFecha("2026:11:23 10:15:30", true), LocalDateTime.of(2026, 11, 24, 9, 0));
        File otra = archivo("b.jpg", jpegConFecha("2026:11:22 08:01:02", false), LocalDateTime.of(2026, 11, 24, 9, 0));
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 15, 30), FotosDeCarpeta.fechaExif(android));
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 1, 2), FotosDeCarpeta.fechaExif(otra));
        File whatsapp = archivo("c.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2, (byte) 0xFF, (byte) 0xD9},
            LocalDateTime.of(2026, 11, 21, 18, 30));
        assertNull(FotosDeCarpeta.fechaExif(whatsapp), "WhatsApp la quita");
        assertEquals(LocalDateTime.of(2026, 11, 21, 18, 30), FotosDeCarpeta.tomada(whatsapp), "entonces, la del archivo");
    }

    @Test
    void laCarpeta_soloFotosQueSeLeen_enOrdenNaturalODeFecha() throws Exception {
        LocalDateTime hoy = LocalDateTime.of(2026, 11, 24, 9, 0);
        archivo("IMG_10.jpg", jpegConFecha("2026:11:23 10:00:00", true), hoy);
        archivo("IMG_9.JPG", jpegConFecha("2026:11:23 11:00:00", true), hoy);
        archivo("IMG_11.png", new byte[]{1}, LocalDateTime.of(2026, 11, 20, 8, 0));
        archivo("IMG_12.heic", new byte[]{1}, hoy);
        archivo("notas.txt", new byte[]{1}, hoy);
        Files.createDirectory(dir.resolve("otra"));

        FotosDeCarpeta.Contenido porNombre = FotosDeCarpeta.leer(dir.toFile(), FotosDeCarpeta.Orden.POR_NOMBRE);
        assertEquals(List.of("IMG_9.JPG", "IMG_10.jpg", "IMG_11.png"), porNombre.fotos().stream().map(Foto::nombre).toList(),
            "el 9 antes del 10, como el teléfono las numera");
        assertEquals(1, porNombre.sinLeer(), "la HEIC se cuenta para decirlo");
        assertEquals(List.of("IMG_11.png", "IMG_10.jpg", "IMG_9.JPG"),
            FotosDeCarpeta.leer(dir.toFile(), FotosDeCarpeta.Orden.POR_FECHA).fotos().stream().map(Foto::nombre).toList());
        assertTrue(FotosDeCarpeta.leer(null, FotosDeCarpeta.Orden.POR_NOMBRE).fotos().isEmpty());
    }

    @Test
    void ordenNatural() {
        assertTrue(FotosDeCarpeta.natural("IMG_9.jpg", "IMG_10.jpg") < 0);
        assertTrue(FotosDeCarpeta.natural("IMG-20261123-WA0012.jpg", "IMG-20261123-WA0102.jpg") < 0);
        assertTrue(FotosDeCarpeta.natural("foto 007.jpg", "foto 7b.jpg") < 0);
        assertEquals(0, FotosDeCarpeta.natural("a.jpg", "a.jpg"));
    }

    @Test
    void laSiguienteFoto_esLaPrimeraLibreDespuesDeLaUltimaPuesta() {
        List<Foto> fotos = new ArrayList<>();
        for (int i = 0; i < 6; i++) fotos.add(new Foto(new File("f" + i + ".jpg"), LocalDateTime.of(2026, 11, 23, 10, i)));
        Set<File> usadas = Set.of(fotos.get(0).archivo(), fotos.get(1).archivo(), fotos.get(3).archivo());
        assertEquals(2, FotosDeCarpeta.siguienteLibre(fotos, usadas, -1));
        assertEquals(4, FotosDeCarpeta.siguienteLibre(fotos, usadas, 3), "después de la patrulla (fotos 0-3) sigue la 4");
        assertEquals(-1, FotosDeCarpeta.siguienteLibre(fotos, usadas, 5));
    }

    @Test
    void lasNuevasVanPrimero_yLasQueTeniaSeQuedanOSeQuitan() {
        List<String> tenia = List.of("https://x/vieja.jpg");
        assertEquals(List.of("C:/f/1.jpg", "C:/f/2.jpg", "https://x/vieja.jpg"),
            FotosDeCarpeta.conLasNuevas(List.of("C:/f/1.jpg", "C:/f/2.jpg"), tenia, false));
        assertEquals(List.of("C:/f/1.jpg"), FotosDeCarpeta.conLasNuevas(List.of("C:/f/1.jpg"), tenia, true));
    }

    private static Producto bien(String codigo, String area, String ubicacion, String foto) {
        Producto p = new Producto();
        p.setId(codigo);
        p.setCodigo(codigo);
        p.setNombre("BIEN " + codigo);
        p.setArea(area);
        p.setUbicacion(ubicacion);
        p.setFotoUrl(foto);
        p.setStockActual(1);
        return p;
    }

    @Test
    void laListaParaTomarlas_porAreaUbicacionYNumero_soloLosQueNoTienen() throws Exception {
        Producto deBaja = bien("SSPAO/1", "Seguridad Pública — Área Operativa", "ALMACÉN", null);
        deBaja.setFechaBaja(java.time.LocalDate.of(2026, 1, 1));
        Producto conDescripcion = bien("SSPAO/10", "Seguridad Pública — Área Operativa", "Almacén", null);
        conDescripcion.setDescripcion("ESPOSAS. Inventario anterior: SSP-AO-045.");
        List<Producto> bienes = List.of(
            bien("SSPAO/2", "Seguridad Pública — Área Operativa", null, null),
            conDescripcion,
            bien("SSPAO/3", "Seguridad Pública — Área Operativa", "ALMACEN", null),
            bien("SSPAO/4", "Seguridad Pública — Área Operativa", "ARMERÍA", "https://x/a.jpg"),
            bien("BIB/7", "Coordinación de Bibliotecas", "SALA DE LECTURA", null),
            deBaja);
        Map<String, List<Producto>> porArea = ReporteFotosPorTomarService.porArea(bienes);
        assertEquals(Set.of("Seguridad Pública — Área Operativa", "Coordinación de Bibliotecas"), porArea.keySet());
        assertEquals(List.of("SSPAO/3", "SSPAO/10", "SSPAO/2"),
            porArea.get("Seguridad Pública — Área Operativa").stream().map(Producto::getCodigo).toList(),
            "«Almacén» y «ALMACEN» son la misma ubicación (y ahí, por número); sin ubicación al final");

        File pdf = new ReporteFotosPorTomarService().exportPdf(bienes);
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            assertEquals(2, d.getNumberOfPages(), "una hoja por área");
            String texto = "";
            for (int i = 1; i <= 2; i++) texto += com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(i));
            assertTrue(texto.contains("SSP-AO-045"), "el número anterior, el que trae pegado");
            assertTrue(texto.contains("3 bienes sin foto"), texto);
            assertFalse(texto.contains("SSPAO/4"), "ya tiene foto");
        } finally {
            pdf.delete();
        }
        assertNull(new ReporteFotosPorTomarService().exportPdf(List.of(bien("X/1", "Tesorería Municipal", null, "https://x/b.jpg"))));
    }
}
