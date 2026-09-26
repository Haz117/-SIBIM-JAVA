package com.sibim.db.integration;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Stock, área and código only change through movimientos, and the
 *  movement history can't be rewritten out of order. */
class IntegridadInventarioIntegrationTest extends IntegrationTestBase {

    private static final String AREA_SGM  = "Secretaría General Municipal";                 // SGM
    private static final String AREA_TICS = "Dirección de Tecnologías de la Información";   // TICS
    private static final String CAT_ID    = "cat-integridad";

    private final ProductoService    productoService   = new ProductoService();
    private final ProductoRepository productoRepo      = new ProductoRepository();
    private final MovimientoService  movimientoService = new MovimientoService();
    private final MovimientoRepository movimientoRepo  = new MovimientoRepository();

    @BeforeEach
    void categoria() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('" + CAT_ID + "', 'Integridad', '#3B82F6')");
        }
    }

    @AfterEach
    void volverAAdmin() {
        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Admin Test");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);
    }

    private Producto nuevoBien(String nombre, String area, int stock) throws Exception {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setCategoriaId(CAT_ID);
        p.setArea(area);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setPrecioCompra(BigDecimal.valueOf(100));
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setStockActual(stock);
        p.setStockMinimo(0);
        p.setStockMaximo(100);
        return productoService.save(p);
    }

    // ── Editar un bien no toca stock ni área ─────────────────────────────────

    @Test
    void editarUnBien_noSobrescribeElStockDeUnMovimientoConcurrente() throws Exception {
        Producto enFormulario = nuevoBien("Silla", AREA_SGM, 10);
        // Someone else registers a salida while the form is open.
        movimientoService.registrar(enFormulario.getId(), TipoMovimiento.SALIDA, 3, "uso", null);

        enFormulario.setDescripcion("Silla ergonómica");
        enFormulario.setActualizadoEn(null); // this test is about stock, not the stale-edit guard
        productoService.save(enFormulario);

        Producto actual = productoRepo.findById(enFormulario.getId()).orElseThrow();
        assertEquals(7, actual.getStockActual(), "la salida de 3 no debe perderse");
        assertEquals("Silla ergonómica", actual.getDescripcion());
    }

    @Test
    void editarUnBien_noLoCambiaDeArea() throws Exception {
        Producto p = nuevoBien("Monitor", AREA_SGM, 1);
        p.setArea(AREA_TICS);
        productoService.save(p);

        Producto actual = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals(AREA_SGM, actual.getArea(), "el área solo cambia con una transferencia");
        assertEquals("SGM/01", actual.getCodigo());
    }

    @Test
    void saveOnline_enActualizacion_ignoraStockYArea() throws Exception {
        Producto p = nuevoBien("Proyector", AREA_SGM, 4);
        p.setStockActual(99);
        p.setArea(AREA_TICS);
        productoRepo.saveOnline(p);

        assertEquals(4, p.getStockActual(), "el objeto refleja lo que quedó en la BD");
        assertEquals(AREA_SGM, p.getArea());
        Producto actual = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals(4, actual.getStockActual());
        assertEquals(AREA_SGM, actual.getArea());
    }

    @Test
    void editarUnBienModificadoPorOtro_seRechaza() throws Exception {
        Producto enFormulario = nuevoBien("Escritorio", AREA_SGM, 1);
        Thread.sleep(5);
        productoService.save(productoRepo.findById(enFormulario.getId()).orElseThrow()); // another user's edit

        enFormulario.setNombre("Escritorio en L");
        assertThrows(ProductoService.ModificadoPorOtroException.class, () -> productoService.save(enFormulario));
    }

    @Test
    void editarDosVecesElMismoObjeto_noSeConfundeConOtroUsuario() throws Exception {
        Producto p = nuevoBien("Archivero", AREA_SGM, 1);
        p.setNombre("Archivero metálico");
        productoService.save(p);
        p.setNombre("Archivero metálico 4 cajones");
        assertDoesNotThrow(() -> productoService.save(p));
    }

    // ── Borrar movimientos ───────────────────────────────────────────────────

    @Test
    void borrarUnMovimientoQueNoEsElUltimo_seRechaza() throws Exception {
        Producto p = nuevoBien("Tóner", AREA_SGM, 10);
        Movimiento primero = movimientoService.registrar(p.getId(), TipoMovimiento.ENTRADA, 5, "compra", null);
        Thread.sleep(5);
        Movimiento segundo = movimientoService.registrar(p.getId(), TipoMovimiento.SALIDA, 2, "uso", null);

        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> movimientoService.eliminar(primero.getId()));
        assertTrue(ex.getMessage().contains("mas reciente"), ex.getMessage());

        movimientoService.eliminar(segundo.getId());
        assertEquals(15, productoRepo.findById(p.getId()).orElseThrow().getStockActual());
    }

    @Test
    void borrarUnaTransferenciaVieja_noRegresaElBienAUnAreaEquivocada() throws Exception {
        Producto p = nuevoBien("Laptop", AREA_SGM, 1);
        Movimiento aTics = movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "x", null, AREA_TICS);
        Thread.sleep(5);
        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "y", null, AREA_SGM);

        assertThrows(MovimientoService.ValidationException.class, () -> movimientoService.eliminar(aTics.getId()));
        assertEquals(AREA_SGM, productoRepo.findById(p.getId()).orElseThrow().getArea());
    }

    @Test
    void soloElAdministradorBorraMovimientos() throws Exception {
        Producto p = nuevoBien("Silla", AREA_SGM, 3);
        Movimiento m = movimientoService.registrar(p.getId(), TipoMovimiento.ENTRADA, 1, "compra", null);

        Usuario secretario = new Usuario();
        secretario.setId("test-admin");
        secretario.setNombre("Secretario");
        secretario.setRol(Rol.SECRETARIO);
        secretario.setArea(AREA_SGM);
        SessionManager.setCurrentUser(secretario);

        assertThrows(MovimientoService.ValidationException.class, () -> movimientoService.eliminar(m.getId()));
        volverAAdmin();
        assertEquals(4, productoRepo.findById(p.getId()).orElseThrow().getStockActual());
    }

    // ── Transferencias ───────────────────────────────────────────────────────

    @Test
    void transferenciaDirecta_guardaElCodigoAnteriorYElNuevo() throws Exception {
        Producto p = nuevoBien("Impresora", AREA_SGM, 1);
        Movimiento m = movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "x", null, AREA_TICS);

        assertEquals("SGM/01", columna(m.getId(), "codigo_anterior"));
        assertEquals("TICS/01", columna(m.getId(), "codigo_nuevo"));
    }

    @Test
    void aprobarTransferencia_asignaCodigoEnLaMismaTransaccionYLoRegistra() throws Exception {
        Producto p = nuevoBien("Escáner", AREA_SGM, 1);
        Movimiento pendiente = movimientoRepo.addMovimientoPendiente(transferencia(p.getId(), AREA_TICS));

        movimientoService.aprobarTransferencia(pendiente.getId());

        Producto actual = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals(AREA_TICS, actual.getArea());
        assertEquals("TICS/01", actual.getCodigo());
        assertEquals("APROBADO", columna(pendiente.getId(), "estado"));
        assertEquals("SGM/01", columna(pendiente.getId(), "codigo_anterior"));
        assertEquals("TICS/01", columna(pendiente.getId(), "codigo_nuevo"));
    }

    @Test
    void aprobarTransferencia_siElBienYaSeMovio_seRechaza() throws Exception {
        String areaRh = "Tesorería — Recursos Humanos y Nómina";
        Producto p = nuevoBien("Silla", AREA_SGM, 1);
        Movimiento pendiente = movimientoRepo.addMovimientoPendiente(transferencia(p.getId(), AREA_TICS));
        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "otra", null, areaRh);

        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> movimientoService.aprobarTransferencia(pendiente.getId()));
        assertTrue(ex.getMessage().contains("ya no está"), ex.getMessage());
        assertEquals(areaRh, productoRepo.findById(p.getId()).orElseThrow().getArea());
        assertEquals("PENDIENTE", columna(pendiente.getId(), "estado"));
    }

    private Movimiento transferencia(String productoId, String destino) {
        Movimiento m = new Movimiento();
        m.setProductoId(productoId);
        m.setTipo(TipoMovimiento.TRANSFERENCIA);
        m.setCantidad(1);
        m.setAreaDestino(destino);
        m.setUsuarioId("test-admin");
        m.setUsuarioNombre("Admin Test");
        return m;
    }

    private String columna(String movimientoId, String columna) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + columna + " FROM movements WHERE id = ?")) {
            ps.setString(1, movimientoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
