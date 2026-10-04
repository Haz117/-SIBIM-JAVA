package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.Permisos;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a secretaría or a dirección may NOT do (decided 2026-10-01): change more
 * than four fields of a bien, mark labels, print the control reports, take the
 * inventory out as a spreadsheet and — a dirección — see amounts in pesos.
 */
class RestriccionesAreasTest {

    private final ProductoService productos = new ProductoService();
    private final ReporteService reportes = new ReporteService();
    private Producto bien;

    @BeforeEach
    void setUp() {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        bien = DemoDataStore.findAllProductos(null).stream()
            .filter(p -> !p.isDadoDeBaja() && p.getArea() != null && p.getPrecioCompra() != null)
            .findFirst().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        DemoDataStore.reiniciar();
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void unArea_soloActualizaUbicacionDescripcionEstadoFisicoYFotos() throws Exception {
        entrarComo(Rol.DIRECCION, bien.getArea());
        Producto antes = DemoDataStore.findProductoById(bien.getId()).orElseThrow().copia();
        Producto edit = antes.copia();
        edit.setUbicacion("Oficina 3, planta alta");
        edit.setDescripcion("Con rayones en la cubierta");
        edit.setEstadoFisico("Regular");
        edit.setNombre("Otro nombre");
        edit.setMarca("Otra marca");
        edit.setModelo("Otro modelo");
        edit.setNumeroSerie("SERIE-FALSA");
        edit.setNumeroFactura("FAC-FALSA");
        edit.setResguardante("Quien sea");
        edit.setEtiquetado(!antes.isEtiquetado());
        edit.setPrecioCompra(new BigDecimal("1.00"));
        productos.save(edit);

        Producto despues = DemoDataStore.findProductoById(bien.getId()).orElseThrow();
        assertEquals("Oficina 3, planta alta", despues.getUbicacion());
        assertEquals("Con rayones en la cubierta", despues.getDescripcion());
        assertEquals("Regular", despues.getEstadoFisico());
        assertEquals(antes.getNombre(), despues.getNombre(), "el nombre es de Patrimonio");
        assertEquals(antes.getMarca(), despues.getMarca());
        assertEquals(antes.getModelo(), despues.getModelo());
        assertEquals(antes.getNumeroSerie(), despues.getNumeroSerie());
        assertEquals(antes.getNumeroFactura(), despues.getNumeroFactura());
        assertEquals(antes.getResguardante(), despues.getResguardante(), "el resguardante lo dice un resguardo");
        assertEquals(antes.isEtiquetado(), despues.isEtiquetado(), "la etiqueta la verifica Patrimonio");
        assertEquals(0, antes.getPrecioCompra().compareTo(despues.getPrecioCompra()));
    }

    @Test
    void patrimonio_sigueEditandoTodo() throws Exception {
        entrarComo(Rol.ADMIN, null);
        Producto edit = DemoDataStore.findProductoById(bien.getId()).orElseThrow().copia();
        edit.setMarca("Marca corregida");
        productos.save(edit);
        assertEquals("Marca corregida", DemoDataStore.findProductoById(bien.getId()).orElseThrow().getMarca());
    }

    @Test
    void lasAreas_noMarcanEtiquetas() {
        for (Rol rol : new Rol[]{ Rol.SECRETARIO, Rol.DIRECCION }) {
            entrarComo(rol, bien.getArea());
            assertThrows(SecurityException.class, () -> productos.marcarEtiquetado(List.of(bien.getId()), true), rol.name());
        }
    }

    @Test
    void lasAreas_noSacanReportesDeControlNiHojasDeCalculo() throws Exception {
        for (Rol rol : new Rol[]{ Rol.SECRETARIO, Rol.DIRECCION }) {
            entrarComo(rol, bien.getArea());
            assertThrows(SecurityException.class, () -> reportes.exportMovimientosPdf(null, null), rol + ": movimientos");
            assertThrows(SecurityException.class, reportes::exportAuditoriaPdf, rol + ": auditoría");
            assertThrows(SecurityException.class, reportes::exportBajasPdf, rol + ": bajas");
            assertThrows(SecurityException.class, () -> reportes.exportEntregaRecepcionPdf(List.of(bien)), rol + ": V.4");
            assertThrows(SecurityException.class, () -> reportes.exportParqueVehicularPdf(List.of(bien)), rol + ": V.6");
            assertThrows(SecurityException.class, () -> reportes.exportInventarioExcel(null, null), rol + ": Excel");
            assertThrows(SecurityException.class, () -> reportes.exportInventarioCsv(null, null), rol + ": CSV");
            assertThrows(SecurityException.class, reportes::exportAlertasExcel, rol + ": alertas en Excel");

            assertTrue(reportes.exportInventarioPdf(null, null).length() > 0, rol + ": su inventario en PDF sí");
            assertTrue(reportes.exportDistribucionPdf().length() > 0);
            assertTrue(reportes.exportAlertasPdf().length() > 0);
            assertTrue(reportes.exportSolicitudBaja(List.of(bien)).length() > 0, rol + ": los formatos de baja sí");
            assertTrue(reportes.exportDictamenBaja(List.of(bien)).length() > 0);
        }
        entrarComo(Rol.ADMIN, null);
        assertTrue(reportes.exportInventarioExcel(null, null).length() > 0, "Patrimonio sí exporta a Excel");
        assertTrue(reportes.exportMovimientosPdf(null, null).length() > 0);
    }

    @Test
    void unaDireccion_noVeImportes() {
        BigDecimal valor = new BigDecimal("1500");
        entrarComo(Rol.DIRECCION, bien.getArea());
        assertFalse(Permisos.veValores());
        assertEquals("—", Permisos.pesos(valor));
        entrarComo(Rol.SECRETARIO, bien.getArea());
        assertTrue(Permisos.pesos(valor).contains("1,500"));
        entrarComo(Rol.ADMIN, null);
        assertTrue(Permisos.pesos(valor).contains("1,500"));
    }

    private static void entrarComo(Rol rol, String area) {
        Usuario u = new Usuario();
        u.setId("u-" + rol);
        u.setUsername("prueba");
        u.setNombre("Usuario " + rol);
        u.setRol(rol);
        u.setArea(area);
        u.setActivo(true);
        SessionManager.setCurrentUser(u);
    }
}
