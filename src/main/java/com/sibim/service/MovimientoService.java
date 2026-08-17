package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.ProductoUtils;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MovimientoService {

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
        Optional<Producto> opt = productoRepo.findById(productoId);
        if (opt.isEmpty()) throw new ValidationException("Producto no encontrado");
        Producto producto = opt.get();

        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(producto.getArea()))
            throw new ValidationException("No tienes acceso a esa area");

        if (cantidad <= 0 && tipo != TipoMovimiento.AJUSTE)
            throw new ValidationException("La cantidad debe ser mayor a cero");
        if (tipo == TipoMovimiento.AJUSTE && cantidad <= 0)
            throw new ValidationException("El ajuste no puede ser cero ni negativo");
        if (tipo == TipoMovimiento.SALIDA && cantidad > producto.getStockActual())
            throw new ValidationException("La cantidad supera el stock disponible (" + producto.getStockActual() + ")");

        if (tipo == TipoMovimiento.TRANSFERENCIA) {
            if (areaDestino == null || areaDestino.isBlank())
                throw new ValidationException("Selecciona el área de destino de la transferencia");
            if (areaDestino.equals(producto.getArea()))
                throw new ValidationException("El área de destino debe ser distinta al área actual");
            if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(areaDestino))
                throw new ValidationException("No tienes acceso al área de destino");
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

        return movimientoRepo.addMovimientoAtomic(m);
    }

    public void eliminar(String movimientoId) throws SQLException, ValidationException {
        Optional<String> productoId = movimientoRepo.findProductoIdById(movimientoId);
        if (productoId.isPresent()) {
            Optional<Producto> producto = productoRepo.findById(productoId.get());
            if (producto.isPresent() && !SessionManager.isAdmin()
                    && !SessionManager.isAreaAccessible(producto.get().getArea()))
                throw new ValidationException("No tienes acceso a esa area");
        }
        movimientoRepo.deleteMovimientoAtomic(movimientoId);
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
