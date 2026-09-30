package com.sibim.contrato;

import com.sibim.config.AreaCodigos;
import com.sibim.model.Categoria;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.CategoriaService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The same battery against every data store the app can run on: the real
 * PostgreSQL (ContratoSqlTest), demo memory (ContratoDemoTest) and the
 * offline SQLite store (ContratoOfflineTest). Each implementing class only
 * switches the mode; every rule is exercised through the services, the way
 * the screens use them. A difference between stores shows up as one of these
 * failing in one class and passing in the others.
 *
 * <p>Demo data can't be reset between tests, so the assertions are relations
 * that must hold on any data ("the código is the next free one", "the counter
 * matches the list"), never absolute counts.
 */
public interface ContratoInventario {

    String AREA_A = "Control Canino";
    String AREA_B = "Parque Municipal";

    default ProductoService productos() { return new ProductoService(); }
    default MovimientoService movimientos() { return new MovimientoService(); }

    default String categoria() throws Exception {
        Categoria c = new Categoria();
        c.setNombre("Contrato " + UUID.randomUUID().toString().substring(0, 8));
        c.setColor("#3B82F6");
        return new CategoriaService().save(c).getId();
    }

    default Producto alta(String area, int stock, int minimo) throws Exception {
        Producto p = new Producto();
        p.setNombre("Bien contrato " + UUID.randomUUID().toString().substring(0, 8));
        p.setCategoriaId(categoria());
        p.setArea(area);
        p.setUnidad(UnidadMedida.PIEZA);
        p.setStockActual(stock);
        p.setStockMinimo(minimo);
        p.setStockMaximo(Math.max(100, minimo));
        p.setPrecioCompra(new BigDecimal("150.00"));
        return productos().save(p);
    }

    default Producto releer(String id) throws Exception {
        return productos().findById(id).orElseThrow();
    }

    default List<String> codigosActivos() throws Exception {
        return productos().getAll().stream().map(Producto::getCodigo).toList();
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    @Test
    default void alta_asignaElSiguienteCodigoLibreDelArea() throws Exception {
        String esperado = AreaCodigos.siguienteCodigo(AREA_A, codigosActivos());
        Producto p = alta(AREA_A, 5, 1);
        assertEquals(esperado, p.getCodigo());
        assertEquals(esperado, releer(p.getId()).getCodigo(), "el código se guarda tal cual");

        Producto q = alta(AREA_A, 5, 1);
        assertNotEquals(p.getCodigo(), q.getCodigo());
        assertEquals(AreaCodigos.prefijo(AREA_A), q.getCodigo().split("/")[0]);
    }

    @Test
    default void alta_precioVentaSiempreIgualAlDeCompra() throws Exception {
        Producto p = alta(AREA_A, 2, 1);
        assertEquals(0, new BigDecimal("150.00").compareTo(releer(p.getId()).getPrecioVenta()));
    }

    // ── Movimientos ─────────────────────────────────────────────────────────

    @Test
    default void entradaYSalida_muevenElStock() throws Exception {
        Producto p = alta(AREA_A, 10, 2);
        movimientos().registrar(p.getId(), TipoMovimiento.ENTRADA, 5, "contrato", null);
        assertEquals(15, releer(p.getId()).getStockActual());
        movimientos().registrar(p.getId(), TipoMovimiento.SALIDA, 4, "contrato", null);
        assertEquals(11, releer(p.getId()).getStockActual());

        List<Movimiento> hist = movimientos().getByProducto(p.getId());
        assertEquals(2, hist.size(), "cada movimiento queda en el historial del bien");
    }

    @Test
    default void salida_mayorAlStock_seRechazaSinTocarNada() throws Exception {
        Producto p = alta(AREA_A, 3, 1);
        assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().registrar(p.getId(), TipoMovimiento.SALIDA, 4, "contrato", null));
        assertEquals(3, releer(p.getId()).getStockActual());
        assertTrue(movimientos().getByProducto(p.getId()).isEmpty());
    }

    @Test
    default void ajuste_fijaElStockAbsoluto() throws Exception {
        Producto p = alta(AREA_A, 7, 1);
        movimientos().registrar(p.getId(), TipoMovimiento.AJUSTE, 2, "conteo", null);
        assertEquals(2, releer(p.getId()).getStockActual());
    }

    // ── Transferencia (admin: se aplica directo) ────────────────────────────

    @Test
    default void transferencia_cambiaAreaYCodigoAlSiguienteLibreDelDestino() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        String esperado = AreaCodigos.siguienteCodigo(AREA_B, codigosActivos());
        movimientos().registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B);

        Producto t = releer(p.getId());
        assertEquals(AREA_B, t.getArea());
        assertEquals(esperado, t.getCodigo());
        assertEquals(1, t.getStockActual(), "una transferencia no cambia la existencia");
    }

    @Test
    default void transferencia_liberaElNumeroEnElOrigen() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        String codigoOrigen = p.getCodigo();
        movimientos().registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B);
        assertFalse(codigosActivos().contains(codigoOrigen));
        assertEquals(AreaCodigos.siguienteCodigo(AREA_A, codigosActivos()), alta(AREA_A, 1, 0).getCodigo());
    }

    /** Runs {@code accion} as a Dirección user of {@code area} (same id as the
     *  admin, so the SQL store's usuario_id foreign key is satisfied). */
    default void comoDireccion(String area, Accion accion) throws Exception {
        Usuario admin = SessionManager.getCurrentUser();
        Usuario dir = new Usuario();
        dir.setId(admin.getId());
        dir.setUsername("direccion.contrato");
        dir.setNombre("Dirección Contrato");
        dir.setRol(Rol.DIRECCION);
        dir.setArea(area);
        SessionManager.setCurrentUser(dir);
        try { accion.run(); } finally { SessionManager.setCurrentUser(admin); }
    }

    interface Accion { void run() throws Exception; }

    @Test
    default void transferenciaDeNoAdmin_quedaPendienteHastaQueElAdminAprueba() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        Movimiento[] pendiente = new Movimiento[1];
        comoDireccion(AREA_A, () -> pendiente[0] = movimientos()
            .registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B));

        assertTrue(pendiente[0].isPendiente());
        assertEquals(AREA_A, releer(p.getId()).getArea(), "nada cambia mientras está pendiente");
        assertEquals(p.getCodigo(), releer(p.getId()).getCodigo());
        assertTrue(movimientos().getPendientesTransferencias().stream()
            .anyMatch(m -> m.getId().equals(pendiente[0].getId())));

        // A second request for the same bien is refused while one is pending.
        comoDireccion(AREA_A, () -> assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "otra", null, AREA_B)));
        // …and it can't be dado de baja either.
        assertThrows(ProductoService.ValidationException.class, () -> productos().darDeBaja(p.getId(), "x"));

        String esperado = AreaCodigos.siguienteCodigo(AREA_B, codigosActivos());
        movimientos().aprobarTransferencia(pendiente[0].getId());
        Producto t = releer(p.getId());
        assertEquals(AREA_B, t.getArea());
        assertEquals(esperado, t.getCodigo());
        assertTrue(movimientos().getPendientesTransferencias().stream()
            .noneMatch(m -> m.getId().equals(pendiente[0].getId())));
    }

    @Test
    default void transferenciaRechazada_noMueveElBien() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        Movimiento[] pendiente = new Movimiento[1];
        comoDireccion(AREA_A, () -> pendiente[0] = movimientos()
            .registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B));
        movimientos().rechazarTransferencia(pendiente[0].getId(), "no procede");
        Producto r = releer(p.getId());
        assertEquals(AREA_A, r.getArea());
        assertEquals(p.getCodigo(), r.getCodigo());
        assertTrue(movimientos().getPendientesTransferencias().stream()
            .noneMatch(m -> m.getId().equals(pendiente[0].getId())));
    }

    // ── Baja y reactivación ─────────────────────────────────────────────────

    @Test
    default void baja_sacaAlBienDelInventarioActivoYReactivarLoRegresa() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        productos().darDeBaja(p.getId(), "contrato");
        assertTrue(productos().getAll().stream().noneMatch(x -> x.getId().equals(p.getId())));
        assertTrue(productos().getAllIncludingBaja().stream().anyMatch(x -> x.getId().equals(p.getId())));

        productos().reactivar(p.getId());
        Producto r = releer(p.getId());
        assertFalse(r.isDadoDeBaja());
        assertTrue(r.getCodigo().startsWith(AreaCodigos.prefijo(AREA_A) + "/"));
    }

    @Test
    default void movimiento_sobreBienDadoDeBaja_seRechaza() throws Exception {
        Producto p = alta(AREA_A, 4, 0);
        productos().darDeBaja(p.getId(), "contrato");
        assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().registrar(p.getId(), TipoMovimiento.ENTRADA, 1, "contrato", null));
    }

    // ── Los números cuadran ─────────────────────────────────────────────────

    @Test
    default void alertas_agotadosYBajoStock_segunElEstadoDeCadaBien() throws Exception {
        Producto agotado = alta(AREA_A, 0, 1);
        Producto bajo    = alta(AREA_A, 1, 3);
        Producto normal  = alta(AREA_A, 9, 3);

        List<String> agotados = productos().getAgotados().stream().map(Producto::getId).toList();
        List<String> bajos    = productos().getBajoStock().stream().map(Producto::getId).toList();
        assertTrue(agotados.contains(agotado.getId()));
        assertTrue(bajos.contains(bajo.getId()));
        assertFalse(agotados.contains(normal.getId()) || bajos.contains(normal.getId()));
        assertFalse(bajos.contains(agotado.getId()), "agotado y bajo stock no se enciman");

        for (Producto x : productos().getAgotados()) assertEquals(EstadoProducto.AGOTADO, x.getEstado());
        for (Producto x : productos().getBajoStock()) assertEquals(EstadoProducto.BAJO_STOCK, x.getEstado());
    }

    @Test
    default void stats_cuadranConLaListaDeBienes() throws Exception {
        alta(AREA_A, 0, 1);
        alta(AREA_A, 2, 5);
        List<Producto> todos = productos().getAll();
        ProductoRepository.InventarioStats s = productos().getStats();

        assertEquals(todos.size(), s.total(), "total = bienes activos");
        assertEquals(todos.stream().filter(p -> p.getEstado() != EstadoProducto.ACTIVO).count(), s.alertas(),
            "alertas = bienes cuyo estado no es Activo");
        assertEquals(todos.stream().filter(p -> !p.isEtiquetado()).count(), s.sinEtiquetar());
        BigDecimal valor = todos.stream().map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, valor.compareTo(Objects.requireNonNullElse(s.valorTotal(), BigDecimal.ZERO)),
            "valor = costo de adquisición × existencia");
    }
}
