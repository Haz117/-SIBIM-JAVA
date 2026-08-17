package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProductoService {

    private final ProductoRepository productoRepo = new ProductoRepository();
    private final AuditLogRepository auditRepo = new AuditLogRepository();

    public List<Producto> getAll() throws SQLException {
        return productoRepo.findAll();
    }

    public Optional<Producto> findById(String id) throws SQLException {
        return productoRepo.findById(id);
    }

    public List<Producto> getAgotados() throws SQLException {
        return productoRepo.findAll().stream()
            .filter(p -> p.getEstado() == EstadoProducto.AGOTADO)
            .collect(Collectors.toList());
    }

    public List<Producto> getBajoStock() throws SQLException {
        return productoRepo.findAll().stream()
            .filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK)
            .collect(Collectors.toList());
    }

    public List<Producto> getVencidosProximos(int dias) throws SQLException {
        return productoRepo.findAll().stream()
            .filter(p -> {
                if (p.getFechaVencimiento() == null) return false;
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(), p.getFechaVencimiento());
                return daysLeft <= dias;
            })
            .sorted((a, b) -> {
                long dA = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), a.getFechaVencimiento());
                long dB = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), b.getFechaVencimiento());
                return Long.compare(dA, dB);
            })
            .collect(Collectors.toList());
    }

    public Producto save(Producto p) throws SQLException, ValidationException {
        validate(p);
        boolean isNew = p.getId() == null || productoRepo.findById(p.getId()).isEmpty();
        Producto saved = productoRepo.save(p);
        auditRepo.log("producto", saved.getId(), saved.getNombre(),
            isNew ? "crear" : "actualizar",
            isNew ? "Bien registrado" : "Datos del bien actualizados");
        return saved;
    }

    public void delete(String id) throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) return;
        Producto p = opt.get();
        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        try {
            productoRepo.delete(id);
            auditRepo.log("producto", id, p.getNombre(), "eliminar", "Bien eliminado permanentemente");
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState()))
                throw new ValidationException("No se puede eliminar: este bien tiene movimientos registrados en su historial");
            throw e;
        }
    }

    /** Formal baja patrimonial — this is the everyday "remove a bien from
     *  active inventory" action; unlike {@link #delete}, the record and its
     *  full movement history stay in the database for audits. */
    public void darDeBaja(String id, String motivo) throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        if (motivo == null || motivo.isBlank())
            throw new ValidationException("El motivo de la baja es obligatorio");
        productoRepo.darDeBaja(id, motivo.trim());
        auditRepo.log("producto", id, p.getNombre(), "baja", "Motivo: " + motivo.trim());
    }

    /** Reverses a baja patrimonial, restoring the bien to active inventory. */
    public void reactivar(String id) throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        productoRepo.reactivar(id);
        auditRepo.log("producto", id, p.getNombre(), "reactivar", "Bien reactivado tras baja");
    }

    /** Includes bienes dados de baja — used only by the "Dados de baja"
     *  history view (see ProductosController). */
    public List<Producto> getAllIncludingBaja() throws SQLException {
        return productoRepo.findAll(true);
    }

    public long countAll() throws SQLException {
        return productoRepo.findAll().size();
    }

    private void validate(Producto p) throws ValidationException, SQLException {
        if (p.getNombre() == null || p.getNombre().isBlank())
            throw new ValidationException("El nombre es obligatorio");
        if (p.getCodigo() == null || p.getCodigo().isBlank())
            throw new ValidationException("El codigo es obligatorio");
        if (productoRepo.existsByCodigo(p.getCodigo(), p.getId()))
            throw new ValidationException("Ya existe un bien con ese codigo");
        if (p.getCategoriaId() == null || p.getCategoriaId().isBlank())
            throw new ValidationException("La categoria es obligatoria");
        if (p.getArea() == null || p.getArea().isBlank())
            throw new ValidationException("El area es obligatoria");
        if (!SessionManager.isAdmin() && !SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        if (p.getPrecioCompra() == null || p.getPrecioCompra().signum() < 0)
            throw new ValidationException("El precio de compra no puede ser negativo");
        if (p.getPrecioVenta() == null || p.getPrecioVenta().signum() < 0)
            throw new ValidationException("El precio de venta no puede ser negativo");
        if (p.getStockActual() < 0)
            throw new ValidationException("El stock no puede ser negativo");
        if (p.getStockMinimo() < 0)
            throw new ValidationException("El stock minimo no puede ser negativo");
        if (p.getStockMaximo() < 0)
            throw new ValidationException("El stock maximo no puede ser negativo");
        if (p.getStockMinimo() > p.getStockMaximo())
            throw new ValidationException("El stock minimo no puede ser mayor al stock maximo");
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
