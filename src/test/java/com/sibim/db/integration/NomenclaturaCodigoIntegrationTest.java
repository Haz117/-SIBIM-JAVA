package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the área-based código nomenclature
 * (ProductoService#asignarCodigo, AreaCodigos, and the reassignment hooks in
 * ProductoRepository/MovimientoRepository) against a real (embedded)
 * PostgreSQL database — needed because the "reuse a freed número" behavior
 * depends on V14's partial unique index (idx_products_codigo_activo), which
 * unit tests with mocked repositories can't exercise.
 */
class NomenclaturaCodigoIntegrationTest extends IntegrationTestBase {

    private static final String AREA_SGM = "Secretaría General Municipal"; // prefijo SGM
    private static final String AREA_RH  = "Tesorería — Recursos Humanos y Nómina"; // prefijo TESRH
    private static final String CAT_ID   = "cat-nomenclatura-test";

    private final ProductoService     productoService   = new ProductoService();
    private final ProductoRepository  productoRepo      = new ProductoRepository();
    private final MovimientoService   movimientoService = new MovimientoService();

    @BeforeEach
    void insertarCategoria() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')")) {
            ps.setString(1, CAT_ID);
            ps.setString(2, "Categoría Nomenclatura Test");
            ps.executeUpdate();
        }
    }

    private Producto nuevoBien(String nombre, String area) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setCategoriaId(CAT_ID);
        p.setArea(area);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setPrecioCompra(BigDecimal.valueOf(1000));
        p.setPrecioVenta(BigDecimal.valueOf(1200));
        p.setStockActual(5);
        p.setStockMinimo(1);
        p.setStockMaximo(10);
        return p;
    }

    @Test
    void alta_asignaCodigoSegunArea() throws Exception {
        Producto guardado = productoService.save(nuevoBien("Escritorio", AREA_SGM));
        assertEquals("SGM/01", guardado.getCodigo());
    }

    @Test
    void altasSucesivas_mismaArea_incrementanNumero() throws Exception {
        productoService.save(nuevoBien("Bien 1", AREA_SGM));
        Producto b2 = productoService.save(nuevoBien("Bien 2", AREA_SGM));
        Producto b3 = productoService.save(nuevoBien("Bien 3", AREA_SGM));

        assertEquals("SGM/02", b2.getCodigo());
        assertEquals("SGM/03", b3.getCodigo());
    }

    @Test
    void altas_areasDistintas_prefijosIndependientes() throws Exception {
        Producto sgm = productoService.save(nuevoBien("Bien SGM", AREA_SGM));
        Producto rh  = productoService.save(nuevoBien("Bien RH", AREA_RH));

        assertEquals("SGM/01", sgm.getCodigo());
        assertEquals("TESRH/01", rh.getCodigo());
    }

    @Test
    void bajaYNuevaAlta_reutilizaElNumeroLiberado() throws Exception {
        Producto primero = productoService.save(nuevoBien("Silla vieja", AREA_SGM));
        assertEquals("SGM/01", primero.getCodigo());

        productoService.darDeBaja(primero.getId(), "Ya no sirve");

        Producto segundo = productoService.save(nuevoBien("Silla nueva", AREA_SGM));
        assertEquals("SGM/01", segundo.getCodigo(), "el número liberado por la baja debe reutilizarse");
    }

    @Test
    void bajaConservaSuCodigoHistorico() throws Exception {
        Producto p = productoService.save(nuevoBien("Bien a dar de baja", AREA_SGM));
        productoService.darDeBaja(p.getId(), "motivo");

        Producto historico = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals("SGM/01", historico.getCodigo(), "el bien dado de baja conserva su código para auditoría");
    }

    @Test
    void transferencia_reasignaCodigoAlAreaDestino() throws Exception {
        // Ocupa TESRH/01 de antemano para que el bien transferido tenga que caer en TESRH/02
        productoService.save(nuevoBien("Ya estaba en RH", AREA_RH));
        Producto p = productoService.save(nuevoBien("Bien a transferir", AREA_SGM));
        assertEquals("SGM/01", p.getCodigo());

        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(), "traspaso", null, AREA_RH);

        Producto transferido = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals(AREA_RH, transferido.getArea());
        assertEquals("TESRH/02", transferido.getCodigo());
    }

    @Test
    void transferencia_liberaElNumeroEnElAreaOrigen() throws Exception {
        Producto p = productoService.save(nuevoBien("Bien a transferir", AREA_SGM));
        assertEquals("SGM/01", p.getCodigo());

        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(), "traspaso", null, AREA_RH);

        Producto siguienteEnSgm = productoService.save(nuevoBien("Nuevo bien en SGM", AREA_SGM));
        assertEquals("SGM/01", siguienteEnSgm.getCodigo(), "SGM/01 debe quedar libre tras la transferencia");
    }

    @Test
    void huecoEnMedio_elNuevoBienLoOcupa_yLuegoSigueLaNumeracion() throws Exception {
        productoService.save(nuevoBien("Bien 1", AREA_SGM));
        Producto b2 = productoService.save(nuevoBien("Bien 2", AREA_SGM));
        productoService.save(nuevoBien("Bien 3", AREA_SGM));

        // El 02 se desocupa por transferencia; el 01 y el 03 siguen ocupados.
        movimientoService.registrar(b2.getId(), TipoMovimiento.TRANSFERENCIA, b2.getStockActual(), "traspaso", null, AREA_RH);

        assertEquals("SGM/02", productoService.save(nuevoBien("Nuevo A", AREA_SGM)).getCodigo(),
            "el bien nuevo ocupa el número que quedó libre");
        assertEquals("SGM/04", productoService.save(nuevoBien("Nuevo B", AREA_SGM)).getCodigo(),
            "sin huecos, continúa después del último");
    }

    @Test
    void codigoCapturadoEnMinusculas_tambienOcupaSuNumero() throws Exception {
        Producto p = productoService.save(nuevoBien("Bien 1", AREA_SGM));
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET codigo = 'sgm/01' WHERE id = ?")) {
            ps.setString(1, p.getId());
            ps.executeUpdate();
        }
        assertEquals("SGM/02", productoService.save(nuevoBien("Bien 2", AREA_SGM)).getCodigo());
    }

    /** What SyncService replays for a reactivation made offline. */
    @Test
    void reactivarReplicado_tomaElSiguienteLibreSiSuNumeroYaSeOcupo() throws Exception {
        Producto original = productoService.save(nuevoBien("Original", AREA_SGM));
        productoService.darDeBaja(original.getId(), "temporal");
        productoService.save(nuevoBien("Reemplazo", AREA_SGM));   // toma SGM/01

        productoRepo.reactivarOnline(original.getId());

        assertEquals("SGM/02", productoRepo.findById(original.getId()).orElseThrow().getCodigo());
    }

    @Test
    void reactivar_reasignaCodigoSiFueOcupadoPorOtroBien() throws Exception {
        Producto original = productoService.save(nuevoBien("Original", AREA_SGM));
        productoService.darDeBaja(original.getId(), "temporal");

        // Otro bien nuevo reclama el número liberado
        Producto reemplazo = productoService.save(nuevoBien("Reemplazo", AREA_SGM));
        assertEquals("SGM/01", reemplazo.getCodigo());

        productoService.reactivar(original.getId());

        Producto reactivado = productoRepo.findById(original.getId()).orElseThrow();
        assertEquals("SGM/02", reactivado.getCodigo(),
            "no puede recuperar SGM/01 porque ya lo tiene el reemplazo — debe tomar el siguiente libre");
    }
}
