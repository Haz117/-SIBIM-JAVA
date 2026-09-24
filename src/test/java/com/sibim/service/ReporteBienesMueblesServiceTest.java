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
 * Smoke tests for ReporteBienesMueblesService.exportBienesMueblesPdf.
 *
 * Demo mode is enabled so orgName() (used in the PDF header) reads from
 * ConfiguracionRepository's in-memory demo values instead of a real Postgres
 * connection. Every non-null result test asserts the returned File exists and
 * is non-empty.
 */
class ReporteBienesMueblesServiceTest {

    private ReporteBienesMueblesService service;

    // ── lifecycle ────────────────────────────────────────────────────────────

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteBienesMueblesService();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Producto bienCompleto() {
        Producto p = new Producto();
        p.setId("bm-01");
        p.setNombre("Escritorio Ejecutivo");
        p.setCodigo("MOB-0021");
        p.setDescripcion("Madera de pino, 1.50 m");
        p.setClaveArmonizada("1.2.4.4.541.3");
        p.setCategoriaNombre("Mobiliario");
        p.setUbicacion("Edificio B, Piso 1");
        p.setArea("Dirección General");
        p.setResguardante("María López");
        p.setMarca("Steelcase");
        p.setModelo("Leap V2");
        p.setNumeroSerie("SCE-2024-001");
        p.setNumeroFactura("FAC-20240315");
        p.setPrecioCompra(BigDecimal.valueOf(12500));
        p.setStockActual(3);
        p.setEstadoFisico("BUENO");
        p.setFechaAdquisicion(LocalDate.of(2022, 3, 15));
        p.setVidaUtilAnios(10);
        p.setValorResidual(BigDecimal.valueOf(500));
        return p;
    }

    private static Producto bienMinimo() {
        Producto p = new Producto();
        p.setId("bm-02");
        p.setNombre("Silla de Visita");
        p.setCodigo("MOB-0022");
        p.setClaveArmonizada("1.2.4.4.541.3");
        p.setCategoriaNombre("Mobiliario");
        p.setPrecioCompra(BigDecimal.valueOf(2800));
        p.setStockActual(10);
        return p;
    }

    private static Producto bienSinClave() {
        Producto p = new Producto();
        p.setId("bm-03");
        p.setNombre("Impresora Láser");
        p.setCodigo("INF-0045");
        p.setCategoriaNombre("Equipo de Cómputo");
        p.setPrecioCompra(BigDecimal.valueOf(7200));
        p.setStockActual(2);
        p.setMarca("HP");
        p.setModelo("LaserJet Pro M404");
        p.setEstadoFisico("REGULAR");
        return p;
    }

    // ── tests ────────────────────────────────────────────────────────────────

    /**
     * Lista con 2 bienes que comparten claveArmonizada: ambos deben agruparse
     * en la misma sección del PDF y el archivo resultante debe existir y no estar vacío.
     */
    @Test
    void exportBienesMueblesPdf_conDatos_creaArchivoNoVacio() throws Exception {
        List<Producto> bienes = List.of(bienCompleto(), bienMinimo());

        File resultado = service.exportBienesMueblesPdf(
                bienes, "María López", "Coordinadora", "Dirección General", "RES-2024-001");

        assertFileProduced(resultado);
    }

    /**
     * Bienes sin claveArmonizada pero con categoriaNombre: el service los debe
     * agrupar por nombre de categoría. El archivo debe producirse correctamente.
     */
    @Test
    void exportBienesMueblesPdf_sinClaveArmonizada_agrupaPorCategoria() throws Exception {
        List<Producto> bienes = List.of(bienSinClave());

        File resultado = service.exportBienesMueblesPdf(
                bienes, "Carlos Ruiz", null, "Sistemas", null);

        assertFileProduced(resultado);
    }

    /**
     * Lista vacía → el servicio devuelve null (contrato documentado en la
     * implementación: {@code if (bienes.isEmpty()) return null;}).
     */
    @Test
    void exportBienesMueblesPdf_listaVacia_devuelveNull() throws Exception {
        File resultado = service.exportBienesMueblesPdf(
                List.of(), "Cualquiera", null, null, null);

        assertNull(resultado, "Una lista vacía debe devolver null");
    }

    /**
     * Bien con la mayoría de los campos opcionales nulos (sin marca, modelo,
     * serie, factura, estado físico, etc.): el servicio no debe lanzar excepción
     * y debe producir un archivo válido.
     */
    @Test
    void exportBienesMueblesPdf_camposNulos_noLanzaExcepcion() throws Exception {
        Producto bienNulo = new Producto();
        bienNulo.setId("bm-null");
        bienNulo.setNombre("Bien con campos nulos");
        // precioCompra null → se trata como 0 en la implementación
        bienNulo.setStockActual(1);

        File resultado = service.exportBienesMueblesPdf(
                List.of(bienNulo), null, null, null, null);

        assertFileProduced(resultado);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private static void assertFileProduced(File file) {
        assertNotNull(file, "Se esperaba un File no nulo");
        assertTrue(file.exists(), "El archivo debe existir en disco: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(".pdf"),
                "Se esperaba extensión .pdf pero fue: " + file.getName());
    }
}
