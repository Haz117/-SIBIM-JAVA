package com.sibim.db;

import com.sibim.config.AreaCodigos;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.util.ProductoUtils;

import java.sql.SQLException;
import java.util.List;

/**
 * The inventory rules the two in-memory stores (demo and offline) apply the
 * same way MovimientoRepository does against PostgreSQL. Before, each store
 * carried its own copy and they drifted apart (a transfer kept its old código,
 * deleting one didn't give it back); the contract tests in
 * {@code com.sibim.contrato} run the same battery against all three.
 */
public final class ReglasLocales {

    private ReglasLocales() {}

    public static final String SOLO_EL_MAS_RECIENTE =
        "Solo se puede eliminar el movimiento más reciente de este bien: "
        + "existen movimientos registrados despues de este.";

    /**
     * Checks {@code m} against the bien as it is now and fills in what the
     * movement records: stock before/after, and for a transfer the origin área
     * and old/new código (next free número in the destination).
     * @param codigosActivos códigos of every active bien, to pick the new one
     */
    public static void aplicar(Movimiento m, Producto p, Integer expectedStockAnterior,
                               List<String> codigosActivos) throws SQLException {
        int stockActual = p.getStockActual();
        if (p.isDadoDeBaja()) throw new SQLException(MovimientoService.BIEN_DE_BAJA);
        if (expectedStockAnterior != null && stockActual != expectedStockAnterior)
            throw new SQLException("La cantidad cambió desde que se capturó el conteo (esperado "
                + expectedStockAnterior + ", actual " + stockActual + ") — no se aplicó el ajuste.");
        if (m.getTipo() == TipoMovimiento.SALIDA && m.getCantidad() > stockActual)
            throw new SQLException("La cantidad supera la existencia disponible (" + stockActual + ")");
        if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && stockActual <= 0)
            throw new SQLException(MovimientoService.SIN_EXISTENCIA);

        m.setStockAnterior(stockActual);
        m.setStockNuevo(ProductoUtils.calcularStockNuevo(m.getTipo().getCodigo(), stockActual, m.getCantidad()));
        m.setEstado(Movimiento.ESTADO_APROBADO);
        if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
            m.setAreaOrigen(p.getArea());
            m.setCodigoAnterior(p.getCodigo());
            m.setCodigoNuevo(AreaCodigos.tienePrefijo(m.getAreaDestino())
                ? AreaCodigos.siguienteCodigo(m.getAreaDestino(), codigosActivos)
                : p.getCodigo());
        }
    }

    /** Whether deleting {@code m} changes the bien: only an applied movement did. */
    public static boolean aplicado(Movimiento m) {
        return m.getEstado() == null || Movimiento.ESTADO_APROBADO.equals(m.getEstado());
    }

    /**
     * Only the most recent APPLIED movement of a bien can be deleted; a later
     * pending or rejected request never touched it, so it doesn't count.
     * @param movimientosRecientesPrimero every movement, newest first
     */
    public static void exigirQueSeaElUltimo(Movimiento m, List<Movimiento> movimientosRecientesPrimero)
            throws SQLException {
        if (!aplicado(m)) return;
        int idx = movimientosRecientesPrimero.indexOf(m);
        boolean hayPosterior = movimientosRecientesPrimero.subList(0, Math.max(idx, 0)).stream()
            .anyMatch(o -> o.getProductoId().equals(m.getProductoId()) && aplicado(o));
        if (hayPosterior) throw new SQLException(SOLO_EL_MAS_RECIENTE);
    }

    /** What the bien looks like after deleting the applied movement {@code m}. */
    public record Deshacer(int stock, String area, String codigo) {}

    /**
     * Stock goes back by the movement's delta; a deleted transfer sends the bien
     * back to its origin área with the next free código there (its old número
     * may have been handed to another bien since).
     */
    public static Deshacer deshacer(Movimiento m, Producto p, List<String> codigosActivos) {
        int stock = p.getStockActual() + (m.getStockAnterior() - m.getStockNuevo());
        String area = m.getAreaOrigen() != null ? m.getAreaOrigen() : p.getArea();
        String codigo = m.getAreaOrigen() != null && AreaCodigos.tienePrefijo(m.getAreaOrigen())
            ? AreaCodigos.siguienteCodigo(m.getAreaOrigen(), codigosActivos)
            : p.getCodigo();
        return new Deshacer(stock, area, codigo);
    }
}
