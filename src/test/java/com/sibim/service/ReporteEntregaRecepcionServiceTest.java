package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReporteEntregaRecepcionService (ANEXO V.4).
 *
 * Demo mode is enabled so orgName() and the folio counter use in-memory demo
 * values (no real Postgres, no real folio consumed). The PDFs are re-read with
 * iText to assert on real content, not just that a file was written.
 */
class ReporteEntregaRecepcionServiceTest {

    private ReporteEntregaRecepcionService service;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteEntregaRecepcionService();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Producto bienCompleto(String codigo) {
        Producto p = new Producto();
        p.setId("er-" + codigo);
        p.setNombre("Escritorio Ejecutivo");
        p.setCodigo(codigo);
        p.setDescripcion("Madera de pino, 1.50 m");
        p.setClaveArmonizada("1.2.4.4.541.3");
        p.setResguardante("María López");
        p.setArea("Dirección General");
        p.setUbicacion("Edificio B, Piso 1");
        p.setMarca("Steelcase");
        p.setModelo("Leap V2");
        p.setNumeroSerie("SCE-2024-001");
        p.setNumeroFactura("FAC-20240315");
        p.setFechaAdquisicion(LocalDate.of(2022, 3, 15));
        p.setPrecioCompra(BigDecimal.valueOf(12500));
        p.setStockActual(1);
        p.setVidaUtilAnios(10);
        p.setValorResidual(BigDecimal.valueOf(500));
        p.setEstadoFisico("BUENO");
        return p;
    }

    private static Producto bienSinValoresNiFechas() {
        Producto p = new Producto();
        p.setId("er-min");
        p.setNombre("Silla");
        p.setCodigo("MOB-0099");
        return p;
    }

    private static String textoDePagina(File pdf, int pagina) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            return PdfTextExtractor.getTextFromPage(doc.getPage(pagina));
        }
    }

    private static int paginas(File pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            return doc.getNumberOfPages();
        }
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void exportEntregaRecepcionPdf_listaVacia_retornaNull() throws Exception {
        assertNull(service.exportEntregaRecepcionPdf(List.of()));
    }

    @Test
    void exportEntregaRecepcionPdf_unBien_creaPdfNoVacio() throws Exception {
        File pdf = service.exportEntregaRecepcionPdf(List.of(bienCompleto("MOB-0021")));

        assertNotNull(pdf);
        assertTrue(pdf.exists(), "El archivo debe existir");
        assertTrue(pdf.length() > 0, "El archivo no debe estar vacío");
        assertTrue(pdf.getName().endsWith(".pdf"), "Debe tener extensión .pdf");
        assertTrue(paginas(pdf) >= 1);
    }

    @Test
    void exportEntregaRecepcionPdf_contieneAnexoFolioDatosYFirmas() throws Exception {
        File pdf = service.exportEntregaRecepcionPdf(List.of(bienCompleto("MOB-0021")));

        String texto = textoDePagina(pdf, 1);
        assertTrue(texto.contains("ANEXO V.4"), "Título del anexo");
        // The official sheet carries only its name above the grid, no banner.
        assertFalse(texto.contains("INVENTARIO DE BIENES MUEBLES PARA ENTREGA"), "sin la banda que el formato no trae");
        assertTrue(texto.contains("ERV-"), "Folio con prefijo ERV");
        assertTrue(texto.contains("MOB-0021"), "Número de inventario del bien");
        assertTrue(texto.contains("Steelcase"), "Marca del bien");
        assertTrue(texto.contains("ENTREG"), "Bloque de firmas");
        assertTrue(texto.contains("TESTIGO"), "Firma del representante de Contraloría");
    }

    @Test
    void exportEntregaRecepcionPdf_calculaDepreciacionAcumuladaYValorEnLibros() throws Exception {
        Producto p = bienCompleto("MOB-0021");
        // Vida útil 10 años, adquirido hace 2: el valor en libros debe ser menor al de adquisición.
        p.setFechaAdquisicion(LocalDate.now().minusYears(2));
        BigDecimal enLibros = p.getValorDepreciado();
        assertNotNull(enLibros);
        assertTrue(enLibros.compareTo(p.getPrecioCompra()) < 0, "Precondición: el bien ya se depreció");

        File pdf = service.exportEntregaRecepcionPdf(List.of(p));

        String texto = textoDePagina(pdf, 1);
        assertTrue(texto.contains("12,500.00"), "Valor de adquisición formateado como moneda");
        assertTrue(texto.contains(com.sibim.util.FormatUtils.formatCurrency(enLibros)),
            "Valor en libros = valor depreciado del bien");
    }

    /** The format has no cantidad column: a lot's row says how many pieces it is
     *  and is valued whole (140 × $517.86), as the municipio's inventory lists it
     *  — not at one chair's cost, which the acta's total would then miss. */
    @Test
    void exportEntregaRecepcionPdf_lote_valeTodoElRenglonYDiceCuantasPiezas() throws Exception {
        Producto lote = bienSinValoresNiFechas();
        lote.setNombre("SILLAS PLEGABLES");
        lote.setPrecioCompra(new BigDecimal("517.86"));
        lote.setStockActual(140);

        File pdf = service.exportEntregaRecepcionPdf(List.of(lote));

        String texto = textoDePagina(pdf, 1);
        assertTrue(texto.contains("140 piezas"), "Cantidad del lote en la descripción");
        assertTrue(texto.contains("72,500.40"), "Valor de adquisición del renglón completo");
        assertFalse(texto.replace("72,500.40", "").contains("517.86"), "No el costo de una sola pieza");
    }

    @Test
    void exportEntregaRecepcionPdf_bienSinValoresNiFechas_noLanza() throws Exception {
        File pdf = service.exportEntregaRecepcionPdf(List.of(bienSinValoresNiFechas()));

        assertNotNull(pdf);
        assertTrue(pdf.length() > 0);
        assertTrue(textoDePagina(pdf, 1).contains("MOB-0099"));
    }

    @Test
    void exportEntregaRecepcionPdf_muchosBienes_paginaLaTabla() throws Exception {
        List<Producto> bienes = new ArrayList<>();
        for (int i = 0; i < 120; i++) bienes.add(bienCompleto(String.format("MOB-%04d", i)));

        File pdf = service.exportEntregaRecepcionPdf(bienes);

        assertNotNull(pdf);
        assertTrue(paginas(pdf) > 1, "120 bienes no caben en una sola hoja apaisada");
    }

    /** The "No. resguardo" column was always printed empty: it says the folio of
     *  the resguardo in force of each bien (a cancelled one doesn't count). */
    @Test
    void foliosVigentes_soloDeLosResguardosVigentes() {
        com.sibim.model.Resguardo vigente = new com.sibim.model.Resguardo();
        vigente.setNumero("RSG-2026-000150"); vigente.setEstado(com.sibim.model.Resguardo.ESTADO_ACTIVO);
        com.sibim.model.ResguardoItem it = new com.sibim.model.ResguardoItem(); it.setProductoId("er-MOB-0021");
        vigente.setItems(List.of(it));
        com.sibim.model.Resguardo cancelado = new com.sibim.model.Resguardo();
        cancelado.setNumero("RSG-2026-000061"); cancelado.setEstado(com.sibim.model.Resguardo.ESTADO_CANCELADO);
        com.sibim.model.ResguardoItem otro = new com.sibim.model.ResguardoItem(); otro.setProductoId("er-MOB-0022");
        cancelado.setItems(List.of(otro));
        assertEquals(java.util.Map.of("er-MOB-0021", "RSG-2026-000150"),
            ReporteEntregaRecepcionService.foliosVigentes(List.of(vigente, cancelado)));
    }

    /** As the official sheet fills it: a bien without fecha de adquisición or vida
     *  útil is not depreciated — $0.00 and its book value is what it cost. */
    @Test
    void sinVidaUtil_depreciacionCeroYLibrosIgualAdquisicion() throws Exception {
        Producto sinVida = bienCompleto("MOB-0023");
        sinVida.setVidaUtilAnios(null);
        BigDecimal[] v = ReporteEntregaRecepcionService.valores(sinVida);
        assertEquals(0, new BigDecimal("12500").compareTo(v[0]));
        assertEquals(0, BigDecimal.ZERO.compareTo(v[1]), "depreciación acumulada $0.00");
        assertEquals(0, v[0].compareTo(v[2]), "valor en libros = valor de adquisición");
        String texto = textoDePagina(service.exportEntregaRecepcionPdf(List.of(sinVida)), 1);
        assertTrue(texto.contains("$0.00"), texto);
        assertFalse(texto.contains("vida útil registrada"), "ya no lleva la nota de los que quedaban fuera");
        BigDecimal[] sinCosto = ReporteEntregaRecepcionService.valores(bienSinValoresNiFechas());
        assertEquals(0, BigDecimal.ZERO.compareTo(sinCosto[0]), "sin costo: $0.00 en las tres");
        assertEquals(0, BigDecimal.ZERO.compareTo(sinCosto[2]));
    }

    /** "Nombre del responsable del resguardo" is the área's titular (Configuración ›
     *  Áreas), not the resguardante again; an área without one leaves it blank. */
    @Test
    void responsableDelResguardo_esElTitularDelArea() throws Exception {
        com.sibim.model.Usuario admin = new com.sibim.model.Usuario();
        admin.setId("v4-admin"); admin.setNombre("Administrador"); admin.setRol(com.sibim.model.enums.Rol.ADMIN);
        com.sibim.session.SessionManager.setCurrentUser(admin);
        var config = new com.sibim.repository.ConfiguracionRepository();
        try {
            TitularesArea.guardar(config, "Dirección General", "Juan Ejemplo Prueba Ficticio");
            String texto = textoDePagina(service.exportEntregaRecepcionPdf(List.of(bienCompleto("MOB-0021"))), 1);
            assertTrue(texto.contains("Ficticio"), texto);   // the name wraps in its column
            assertEquals(1, texto.split("María López", -1).length - 1, "el resguardante sale una vez, no también como responsable");
        } finally {
            TitularesArea.guardar(config, "Dirección General", "");
            com.sibim.session.SessionManager.logout();
        }
    }
}
