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
        Optional<Producto> opt = productoRepo.findById(productoId);
        if (opt.isEmpty()) throw new ValidationException("Producto no encontrado");
        Producto producto = opt.get();

        if (cantidad <= 0 && tipo != TipoMovimiento.AJUSTE)
            throw new ValidationException("La cantidad debe ser mayor a cero");
        if (tipo == TipoMovimiento.AJUSTE && cantidad < 0)
            throw new ValidationException("El ajuste no puede ser negativo");
        if (tipo == TipoMovimiento.SALIDA && cantidad > producto.getStockActual())
            throw new ValidationException("La cantidad supera el stock disponible (" + producto.getStockActual() + ")");

        int stockNuevo = ProductoUtils.calcularStockNuevo(tipo.getCodigo(), producto.getStockActual(), cantidad);

        Movimiento m = new Movimiento();
        m.setProductoId(productoId);
        m.setProductoNombre(producto.getNombre());
        m.setCategoriaColor(producto.getCategoriaColor());
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(producto.getStockActual());
        m.setStockNuevo(stockNuevo);
        m.setMotivo(motivo);
        m.setReferencia(referencia);
        m.setUsuarioId(SessionManager.getCurrentUser().getId());
        m.setUsuarioNombre(SessionManager.getCurrentUser().getNombre());

        return movimientoRepo.addMovimientoAtomic(m);
    }

    public void eliminar(String movimientoId) throws SQLException {
        movimientoRepo.deleteMovimientoAtomic(movimientoId);
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
