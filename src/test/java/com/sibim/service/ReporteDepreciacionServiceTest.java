package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReporteDepreciacionService.
 * The service builds files purely from the List<Producto> passed in;
 * ReporteService.tempFile() writes to the JVM temp directory. Demo mode keeps
 * the institutional header (orgName/logo) and folio lookups off the real
 * database, so this never depends on — or writes to — a configured Postgres.
 */
class ReporteDepreciacionServiceTest {

    private ReporteDepreciacionService service;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteDepreciacionService();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a fully-populated Producto with valid depreciation data. */
    private Producto productoConDepreciacion(String nombre, double precioCompra, int vidaUtilAnios) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setCategoriaNombre("Mobiliario");
        p.setFechaAdquisicion(LocalDate.now().minusYears(2));
        p.setVidaUtilAnios(vidaUtilAnios);
        p.setPrecioCompra(BigDecimal.valueOf(precioCompra));
        p.setValorResidual(BigDecimal.ZERO);
        return p;
    }

    // ── exportDepreciacionExcel ───────────────────────────────────────────────

    @Test
    void exportDepreciacionExcel_listaConUnProducto_retornaArchivoNoNulo() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Silla ejecutiva", 5000, 10));
        File result = service.exportDepreciacionExcel(lista);
        assertNotNull(result, "El archivo no debe ser null");
    }

    @Test
    void exportDepreciacionExcel_listaConUnProducto_archivoExisteYNoEstaVacio() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Escritorio", 8000, 5));
        File result = service.exportDepreciacionExcel(lista);
        assertTrue(result.exists(), "El archivo debe existir");
        assertTrue(result.length() > 0, "El archivo no debe estar vacío");
    }

    @Test
    void exportDepreciacionExcel_listaConUnProducto_tieneExtensionXlsx() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Monitor", 12000, 3));
        File result = service.exportDepreciacionExcel(lista);
        assertTrue(result.getName().endsWith(".xlsx"),
            "La extensión debe ser .xlsx, pero fue: " + result.getName());
    }

    @Test
    void exportDepreciacionExcel_listaVacia_retornaArchivoValido() throws Exception {
        File result = service.exportDepreciacionExcel(List.of());
        assertNotNull(result, "Lista vacía debe retornar un archivo, no null");
        assertTrue(result.exists(), "El archivo debe existir incluso sin filas");
        assertTrue(result.length() > 0, "El workbook vacío aún produce bytes de encabezado");
    }

    @Test
    void exportDepreciacionExcel_variosProductos_retornaArchivoNoNulo() throws Exception {
        List<Producto> lista = List.of(
            productoConDepreciacion("Laptop", 15000, 3),
            productoConDepreciacion("Proyector", 7000, 5),
            productoConDepreciacion("Impresora", 4000, 4)
        );
        File result = service.exportDepreciacionExcel(lista);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void exportDepreciacionExcel_productoSinCategoria_noLanzaExcepcion() throws Exception {
        Producto p = new Producto();
        p.setNombre("Bien sin categoria");
        p.setCategoriaNombre(null); // null a propósito
        p.setFechaAdquisicion(LocalDate.now().minusYears(1));
        p.setVidaUtilAnios(5);
        p.setPrecioCompra(BigDecimal.valueOf(3000));
        p.setValorResidual(BigDecimal.ZERO);

        assertDoesNotThrow(() -> service.exportDepreciacionExcel(List.of(p)));
    }

    @Test
    void exportDepreciacionExcel_productoSinPrecioNiFechaAdquisicion_noLanzaExcepcion() throws Exception {
        Producto p = new Producto();
        p.setNombre("Bien incompleto");
        p.setVidaUtilAnios(0); // vidaUtilAnios debe ser no-null; 0 desactiva el cálculo
        // precioCompra = null y fechaAdquisicion = null -> getValorDepreciado() = null -> celdas con 0
        assertDoesNotThrow(() -> service.exportDepreciacionExcel(List.of(p)));
    }

    // ── exportDepreciacionPdf ─────────────────────────────────────────────────

    @Test
    void exportDepreciacionPdf_listaConUnProducto_retornaArchivoNoNulo() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Aire acondicionado", 20000, 8));
        File result = service.exportDepreciacionPdf(lista);
        assertNotNull(result);
    }

    @Test
    void exportDepreciacionPdf_listaConUnProducto_tieneExtensionPdf() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Servidor", 50000, 5));
        File result = service.exportDepreciacionPdf(lista);
        assertTrue(result.getName().endsWith(".pdf"),
            "La extensión debe ser .pdf, pero fue: " + result.getName());
    }

    @Test
    void exportDepreciacionPdf_listaConUnProducto_archivoNoEstaVacio() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("UPS", 3000, 3));
        File result = service.exportDepreciacionPdf(lista);
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
    }

    @Test
    void exportDepreciacionPdf_listaVacia_retornaArchivoValido() throws Exception {
        File result = service.exportDepreciacionPdf(List.of());
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0, "Un PDF vacío aún produce la estructura del documento");
    }

    @Test
    void exportDepreciacionPdf_productoConPorcentajeNulo_noLanzaExcepcion() throws Exception {
        // getValorDepreciado() devuelve null cuando precioCompra es null
        Producto p = new Producto();
        p.setNombre("Bien sin precio");
        p.setFechaAdquisicion(LocalDate.now().minusYears(1));
        p.setVidaUtilAnios(5);
        // precioCompra = null  => getPorcentajeDepreciado() = null => la celda debe mostrar "—"
        assertDoesNotThrow(() -> service.exportDepreciacionPdf(List.of(p)));
    }

    // ── exportDepreciacionCsv ─────────────────────────────────────────────────

    @Test
    void exportDepreciacionCsv_listaConUnProducto_tieneExtensionCsv() throws Exception {
        List<Producto> lista = List.of(productoConDepreciacion("Vehículo", 100000, 10));
        File result = service.exportDepreciacionCsv(lista);
        assertNotNull(result);
        assertTrue(result.getName().endsWith(".csv"),
            "La extensión debe ser .csv, pero fue: " + result.getName());
    }

    @Test
    void exportDepreciacionCsv_listaVacia_retornaArchivoConEncabezado() throws Exception {
        File result = service.exportDepreciacionCsv(List.of());
        assertNotNull(result);
        assertTrue(result.exists());
        // El encabezado CSV siempre se escribe aunque la lista esté vacía
        assertTrue(result.length() > 0, "El CSV vacío debe tener al menos la fila de encabezado");
    }

    @Test
    void exportDepreciacionCsv_productoConVidaUtilCumplida_noLanzaExcepcion() throws Exception {
        Producto p = productoConDepreciacion("Bien amortizado", 5000, 2);
        // 2 años de vida útil, adquirido hace 5 años = vida útil cumplida
        p.setFechaAdquisicion(LocalDate.now().minusYears(5));
        assertDoesNotThrow(() -> service.exportDepreciacionCsv(List.of(p)));
    }
}
