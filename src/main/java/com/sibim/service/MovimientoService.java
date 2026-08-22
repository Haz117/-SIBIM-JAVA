package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.ProductoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MovimientoService {

    private static final Logger log = LoggerFactory.getLogger(MovimientoService.class);

    private final MovimientoRepository movimientoRepo = new MovimientoRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();

    public List<Movimiento> getAll() throws SQLException {
        return movimientoRepo.findAll();
    }

    public List<Movimiento> getByDateRange(LocalDate desde, LocalDate hasta) throws SQLException {
        return movimientoRepo.findByDateRange(desde, hasta);
    }

    public List<Movimiento> getToday() throws SQLException {
        return movimientoRepo.findToday();
    }

    public List<Movimiento> getLastNDays(int days) throws SQLException {
        return movimientoRepo.findLastNDays(days);
    }

    public List<Movimiento> getByProducto(String productoId) throws SQLException {
        return movimientoRepo.findByProducto(productoId);
    }

    public Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad,
                                String motivo, String referencia) throws SQLException, ValidationException {
        return registrar(productoId, tipo, cantidad, motivo, referencia, null);
    }

    /** @param areaDestino required (and only meaningful) for TRANSFERENCIA —
     *  the area the product is being moved TO. Ignored for other types. */
    public Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad,
                                String motivo, String referencia, String areaDestino) throws SQLException, ValidationException {
        return registrar(productoId, tipo, cantidad, motivo, referencia, areaDestino, null);
    }

    /**
     * Registers an AJUSTE only if the product's stock is still exactly
     * {@code expectedStockAnterior} at the moment the row is locked —
     * otherwise it's rejected instead of silently overwriting whatever
     * changed it. AJUSTE's "cantidad" is an absolute new stock value, not a
     * delta, so unlike ENTRADA/SALIDA the row lock alone doesn't stop it
     * from erasing another movement's effect if the snapshot it was
     * computed from (e.g. what a conteo físico captured when it opened) has
     * since gone stale. Use this instead of the plain {@code registrar} for
     * any AJUSTE derived from a stock value read earlier than "just now".
     */
    public Movimiento registrarAjusteVerificado(String productoId, int nuevoStock, String motivo,
                                                String referencia, int expectedStockAnterior)
            throws SQLException, ValidationException {
        return registrar(productoId, TipoMovimiento.AJUSTE, nuevoStock, motivo, referencia, null, expectedStockAnterior);
    }

    private Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad, String motivo,
                                 String referencia, String areaDestino, Integer expectedStockAnterior)
            throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(productoId);
        if (opt.isEmpty()) throw new ValidationException("Producto no encontrado");
        Producto producto = opt.get();

        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(producto.getArea()))
            throw new ValidationException("No tienes acceso a esa area");

        if (tipo == TipoMovimiento.AJUSTE) {
            if (cantidad <= 0)
                throw new ValidationException("El ajuste debe ser mayor a cero");
        } else {
            if (cantidad <= 0)
                throw new ValidationException("La cantidad debe ser mayor a cero");
        }
        if (tipo == TipoMovimiento.SALIDA && cantidad > producto.getStockActual())
            throw new ValidationException("La cantidad supera el stock disponible (" + producto.getStockActual() + ")");

        if (tipo == TipoMovimiento.TRANSFERENCIA) {
            if (areaDestino == null || areaDestino.isBlank())
                throw new ValidationException("Selecciona el área de destino de la transferencia");
            if (areaDestino.equals(producto.getArea()))
                throw new ValidationException("El área de destino debe ser distinta al área actual");
        }

        int stockNuevo = ProductoUtils.calcularStockNuevo(tipo.getCodigo(), producto.getStockActual(), cantidad);

        Movimiento m = new Movimiento();
        m.setProductoId(productoId);
        m.setProductoNombre(producto.getNombre());
        m.setCategoriaColor(producto.getCategoriaColor());
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(producto.getStockActual());
        m.setStockNuevo(stockNuevo);
        if (tipo == TipoMovimiento.TRANSFERENCIA) m.setAreaDestino(areaDestino);
        m.setMotivo(motivo);
        m.setReferencia(referencia);
        m.setUsuarioId(SessionManager.getCurrentUser().getId());
        m.setUsuarioNombre(SessionManager.getCurrentUser().getNombre());

        // Non-admin transfers go through an approval workflow: saved as PENDIENTE,
        // stock and area unchanged until an admin approves.
        if (tipo == TipoMovimiento.TRANSFERENCIA && !SessionManager.isAdmin()) {
            Movimiento saved = movimientoRepo.addMovimientoPendiente(m);
            log.info("Transferencia PENDIENTE [{}] '{}' {} uds → área '{}'",
                saved.getId(), m.getProductoNombre(), m.getCantidad(), m.getAreaDestino());
            return saved;
        }
        try {
            Movimiento saved = movimientoRepo.addMovimientoAtomic(m, expectedStockAnterior);
            log.info("Movimiento {} [{}] '{}' {} uds — stock {} → {}",
                tipo, saved.getId(), m.getProductoNombre(), m.getCantidad(),
                m.getStockAnterior(), m.getStockNuevo());
            return saved;
        } catch (SQLException e) {
            if (isBusinessRuleMessage(e)) throw new ValidationException(e.getMessage());
            throw e;
        }
    }

    public List<Movimiento> getPendientesTransferencias() throws SQLException {
        return movimientoRepo.findPendientesTransferencias();
    }

    public void aprobarTransferencia(String movimientoId) throws SQLException {
        movimientoRepo.aprobarTransferencia(movimientoId);
    }

    public void rechazarTransferencia(String movimientoId) throws SQLException {
        movimientoRepo.rechazarTransferencia(movimientoId);
    }

    public void eliminar(String movimientoId) throws SQLException, ValidationException {
        Optional<String> estado = movimientoRepo.findEstadoById(movimientoId);
        if (estado.isPresent() && Movimiento.ESTADO_PENDIENTE.equals(estado.get()))
            throw new ValidationException(
                "No se puede eliminar una transferencia pendiente. Primero apruébala o recházala.");

        Optional<String> productoId = movimientoRepo.findProductoIdById(movimientoId);
        if (!SessionManager.isAdmin()) {
            // Orphaned movement (product deleted) — can't verify area, deny.
            if (productoId.isEmpty())
                throw new ValidationException("No tienes permiso para eliminar ese movimiento");
            Optional<Producto> producto = productoRepo.findById(productoId.get());
            if (producto.isEmpty() || !SessionManager.isAreaAccessible(producto.get().getArea()))
                throw new ValidationException("No tienes acceso a esa area");
        }
        try {
            movimientoRepo.deleteMovimientoAtomic(movimientoId);
            log.info("Movimiento eliminado [{}]", movimientoId);
        } catch (SQLException e) {
            if (isBusinessRuleMessage(e)) throw new ValidationException(e.getMessage());
            throw e;
        }
    }

    /** Distinguishes the repository's business-rule rejections (raised as
     *  SQLException from inside a transaction so they trigger the rollback)
     *  from genuine database errors, so the former can surface as a
     *  user-facing ValidationException instead of a generic DB error. */
    private static boolean isBusinessRuleMessage(SQLException e) {
        String msg = e.getMessage();
        return msg != null && (msg.startsWith("La cantidad supera el stock disponible")
                || msg.startsWith("Solo se puede eliminar el movimiento mas reciente")
                || msg.startsWith("El stock cambió desde que se capturó el conteo"));
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
