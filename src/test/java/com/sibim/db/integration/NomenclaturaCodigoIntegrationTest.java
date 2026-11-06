package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the área-based código nomenclature
 * (ProductoService#asignarCodigo, AreaCodigos, and the reassignment hooks in
 * ProductoRepository/MovimientoRepository) against a real (embedded)
 * PostgreSQL database — needed because the números depend on V14's partial
 * unique index (idx_products_codigo_activo) and on V34's codigos_usados, kept
 * by a trigger, which unit tests with mocked repositories can't exercise.
 * Since 5-nov-2026 a número that was used is never handed to another bien.
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
    void bajaYNuevaAlta_noReutilizaElNumero() throws Exception {
        Producto primero = productoService.save(nuevoBien("Silla vieja", AREA_SGM));
        assertEquals("SGM/01", primero.getCodigo());

        productoService.darDeBaja(primero.getId(), "Ya no sirve");

        Producto segundo = productoService.save(nuevoBien("Silla nueva", AREA_SGM));
        assertEquals("SGM/02", segundo.getCodigo(), "el número del bien dado de baja no se le da a otro");
    }

    @Test
    void bienBorrado_noLiberaSuNumero() throws Exception {
        Producto p = productoService.save(nuevoBien("Capturado por error", AREA_SGM));
        assertEquals("SGM/01", p.getCodigo());
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setString(1, p.getId());
            ps.executeUpdate();
        }
        assertEquals("SGM/02", productoService.save(nuevoBien("Otro", AREA_SGM)).getCodigo(),
            "codigos_usados recuerda el número aunque el bien ya no exista");
    }

    /** Not even by hand: a número another bien had is refused; its own former one is not. */
    @Test
    void corregirAMano_aUnNumeroQueUsoOtroBien_seRechaza() throws Exception {
        Producto viejo = productoService.save(nuevoBien("Silla vieja", AREA_SGM));     // SGM/01
        productoService.darDeBaja(viejo.getId(), "Rota");
        Producto p = productoService.save(nuevoBien("Silla nueva", AREA_SGM));       // SGM/02

        Producto editado = productoRepo.findById(p.getId()).orElseThrow();
        editado.setCodigo("SGM/01");
        ProductoService.ValidationException e = assertThrows(ProductoService.ValidationException.class,
            () -> productoService.save(editado));
        assertTrue(e.getMessage().contains("SGM/01") && e.getMessage().contains("SGM/03"), e.getMessage());
        assertTrue(productoService.codigoNoDisponible("SGM/01", p.getId()));

        Producto otraVez = productoRepo.findById(p.getId()).orElseThrow();
        otraVez.setCodigo("SGM/07");
        assertEquals("SGM/07", productoService.save(otraVez).getCodigo(), "uno que nadie ha usado sí se puede");
        Producto deRegreso = productoRepo.findById(p.getId()).orElseThrow();
        deRegreso.setCodigo("SGM/02");
        assertEquals("SGM/02", productoService.save(deRegreso).getCodigo(), "su propio número de antes, también");
    }

    @Test
    void codigoCambiadoPorScript_quedaAnotadoComoUsado() throws Exception {
        Producto p = productoService.save(nuevoBien("Bien 1", AREA_SGM));
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE products SET codigo = 'SGM/09' WHERE id = ?")) {
            ps.setString(1, p.getId());
            ps.executeUpdate();
        }
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE products SET codigo = 'SGM/01' WHERE id = ?")) {
            ps.setString(1, p.getId());
            ps.executeUpdate();
        }
        assertEquals("SGM/10", productoService.save(nuevoBien("Bien 2", AREA_SGM)).getCodigo(),
            "el trigger de V34 anotó el SGM/09 aunque ya nadie lo tenga");
    }

    /** Production had V14 in its history but the old global UNIQUE (codigo)
     *  back on the table: reusing a baja's número failed with a duplicate key
     *  there. V32 removes it again; since V34 no número is reused anyway. */
    @Test
    void conLaRestriccionGlobalDeAntes_V32LaQuitaYUnaAltaNoChoca() throws Exception {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD CONSTRAINT products_codigo_key UNIQUE (codigo)");
        }
        try {
            Producto primero = productoService.save(nuevoBien("Silla vieja", AREA_SGM));
            productoService.darDeBaja(primero.getId(), "Ya no sirve");
            assertEquals("SGM/02", productoService.save(nuevoBien("Silla nueva", AREA_SGM)).getCodigo(),
                "con números que no se repiten, ni la restricción global choca");

            String v32;
            try (InputStream in = getClass().getResourceAsStream("/db/migration/V32__codigo_unico_solo_entre_activos.sql")) {
                assertNotNull(in, "falta la migración V32");
                v32 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (Connection c = getConnection(); Statement st = c.createStatement()) {
                st.execute(v32);
                st.execute(v32);   // idempotente
            }

            Producto tercero = productoService.save(nuevoBien("Mesa", AREA_SGM));
            assertEquals("SGM/03", tercero.getCodigo());
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE products SET codigo = 'SGM/02' WHERE id = ?")) {
                ps.setString(1, tercero.getId());
                assertThrows(SQLException.class, ps::executeUpdate, "entre bienes activos el código sigue siendo único");
            }
        } finally {
            try (Connection c = getConnection(); Statement st = c.createStatement()) {
                st.execute("ALTER TABLE products DROP CONSTRAINT IF EXISTS products_codigo_key");
            }
        }
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
    void transferencia_noLiberaElNumeroEnElAreaOrigen() throws Exception {
        Producto p = productoService.save(nuevoBien("Bien a transferir", AREA_SGM));
        assertEquals("SGM/01", p.getCodigo());

        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(), "traspaso", null, AREA_RH);

        Producto siguienteEnSgm = productoService.save(nuevoBien("Nuevo bien en SGM", AREA_SGM));
        assertEquals("SGM/02", siguienteEnSgm.getCodigo(), "SGM/01 ya se usó: no se le da a otro bien");
    }

    @Test
    void huecoEnMedio_noSeRellena() throws Exception {
        productoService.save(nuevoBien("Bien 1", AREA_SGM));
        Producto b2 = productoService.save(nuevoBien("Bien 2", AREA_SGM));
        productoService.save(nuevoBien("Bien 3", AREA_SGM));

        // El 02 se desocupa por transferencia; el 01 y el 03 siguen ocupados.
        movimientoService.registrar(b2.getId(), TipoMovimiento.TRANSFERENCIA, b2.getStockActual(), "traspaso", null, AREA_RH);

        assertEquals("SGM/04", productoService.save(nuevoBien("Nuevo A", AREA_SGM)).getCodigo(),
            "el hueco del 02 no se rellena");
        assertEquals("SGM/05", productoService.save(nuevoBien("Nuevo B", AREA_SGM)).getCodigo());
    }

    /** Two bienes into the same área in one go: each one its own número. */
    @Test
    void dosTransferenciasSeguidas_alMismoDestino_tomanNumerosDistintos() throws Exception {
        Producto a = productoService.save(nuevoBien("A", AREA_SGM));
        Producto b = productoService.save(nuevoBien("B", AREA_SGM));
        movimientoService.registrar(a.getId(), TipoMovimiento.TRANSFERENCIA, a.getStockActual(), "traspaso", null, AREA_RH);
        movimientoService.registrar(b.getId(), TipoMovimiento.TRANSFERENCIA, b.getStockActual(), "traspaso", null, AREA_RH);
        assertEquals("TESRH/01", productoRepo.findById(a.getId()).orElseThrow().getCodigo());
        assertEquals("TESRH/02", productoRepo.findById(b.getId()).orElseThrow().getCodigo());
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
    void reactivarReplicado_vuelveConSuMismoNumero() throws Exception {
        Producto original = productoService.save(nuevoBien("Original", AREA_SGM));
        productoService.darDeBaja(original.getId(), "temporal");
        assertEquals("SGM/02", productoService.save(nuevoBien("Reemplazo", AREA_SGM)).getCodigo());

        productoRepo.reactivarOnline(original.getId());

        assertEquals("SGM/01", productoRepo.findById(original.getId()).orElseThrow().getCodigo());
    }

    @Test
    void reactivar_vuelveConSuMismoNumero() throws Exception {
        Producto original = productoService.save(nuevoBien("Original", AREA_SGM));
        productoService.darDeBaja(original.getId(), "temporal");
        productoService.save(nuevoBien("Otro", AREA_SGM));

        productoService.reactivar(original.getId());

        assertEquals("SGM/01", productoRepo.findById(original.getId()).orElseThrow().getCodigo());
    }

    /** Data from before V34: another active bien was given its número. */
    @Test
    void reactivar_siOtroActivoTieneSuNumero_tomaElSiguienteNuncaUsado() throws Exception {
        Producto original = productoService.save(nuevoBien("Original", AREA_SGM));
        productoService.darDeBaja(original.getId(), "temporal");
        Producto reemplazo = productoService.save(nuevoBien("Reemplazo", AREA_SGM));   // SGM/02
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE products SET codigo = 'SGM/01' WHERE id = ?")) {
            ps.setString(1, reemplazo.getId());
            ps.executeUpdate();
        }

        productoService.reactivar(original.getId());

        assertEquals("SGM/03", productoRepo.findById(original.getId()).orElseThrow().getCodigo(),
            "el SGM/01 lo tiene otro activo y el SGM/02 ya se usó");
    }

    @Test
    void bajaDefinitiva_yaNoSePuedeReactivar() throws Exception {
        Producto p = productoService.save(nuevoBien("Silla rota", AREA_SGM));
        productoService.darDeBaja(p.getId(), "Rota");
        Producto deBaja = productoRepo.findById(p.getId()).orElseThrow();
        assertEquals(1, productoService.bajaDefinitiva(java.util.List.of(deBaja), java.time.LocalDate.now(),
            "Acta de destrucción 01/2026", "DESTRUCCION"));
        Producto definitiva = productoRepo.findById(p.getId()).orElseThrow();
        assertTrue(definitiva.isBajaDefinitiva());
        assertEquals("Acta de destrucción 01/2026", definitiva.getBajaDefinitivaDocumento());
        assertEquals("DESTRUCCION", definitiva.getTipoDestinoBaja());
        assertThrows(ProductoService.ValidationException.class, () -> productoService.reactivar(p.getId()));
        assertEquals(0, productoService.bajaDefinitiva(java.util.List.of(definitiva), java.time.LocalDate.now(), "otra", null),
            "la que ya es definitiva no cambia");

        productoService.quitarBajaDefinitiva(java.util.List.of(definitiva));
        assertFalse(productoRepo.findById(p.getId()).orElseThrow().isBajaDefinitiva());
        productoService.reactivar(p.getId());
        assertNull(productoRepo.findById(p.getId()).orElseThrow().getFechaBaja());
    }

    @Test
    void bajaDefinitiva_pideDocumento_yQueElBienEsteDeBaja() throws Exception {
        Producto activo = productoService.save(nuevoBien("Activo", AREA_SGM));
        assertThrows(ProductoService.ValidationException.class, () -> productoService.bajaDefinitiva(
            java.util.List.of(activo), java.time.LocalDate.now(), "Acta", null), "no está dado de baja");
        productoService.darDeBaja(activo.getId(), "x");
        Producto deBaja = productoRepo.findById(activo.getId()).orElseThrow();
        assertThrows(ProductoService.ValidationException.class, () -> productoService.bajaDefinitiva(
            java.util.List.of(deBaja), java.time.LocalDate.now(), " ", null), "sin documento");
        assertThrows(ProductoService.ValidationException.class, () -> productoService.bajaDefinitiva(
            java.util.List.of(deBaja), java.time.LocalDate.now().plusDays(1), "Acta", null), "fecha futura");
    }

    /** Phase 2 of the área's avance: a transfer clears it (V34's trigger), the new área reviews it again. */
    @Test
    void revisionDelArea_seGuarda_yAlTransferirseBorra() throws Exception {
        Producto p = productoService.save(nuevoBien("Escritorio", AREA_SGM));
        productoService.revisarComoArea(java.util.List.of(p), null);
        Producto revisado = productoRepo.findById(p.getId()).orElseThrow();
        assertTrue(revisado.isRevisadoPorArea());
        assertFalse(revisado.isObservadoPorArea());

        Producto otro = productoService.save(nuevoBien("Silla", AREA_SGM));
        productoService.revisarComoArea(java.util.List.of(otro), "Lo tiene Obras Públicas");
        Producto observado = productoRepo.findById(otro.getId()).orElseThrow();
        assertTrue(observado.isObservadoPorArea());
        assertEquals("Lo tiene Obras Públicas", observado.getRevisionAreaNota());

        movimientoService.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, p.getStockActual(), "traspaso", null, AREA_RH);
        assertFalse(productoRepo.findById(p.getId()).orElseThrow().isRevisadoPorArea(),
            "en su área nueva el bien se revisa otra vez");

        productoService.quitarRevision(java.util.List.of(observado));
        assertFalse(productoRepo.findById(otro.getId()).orElseThrow().isRevisadoPorArea());
    }
}
