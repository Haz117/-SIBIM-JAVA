package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepo;
    private final AuditLogRepository auditRepo;

    public ProductoService() { this(new ProductoRepository(), new AuditLogRepository()); }
    ProductoService(ProductoRepository productoRepo, AuditLogRepository auditRepo) {
        this.productoRepo = productoRepo;
        this.auditRepo    = auditRepo;
    }

    public List<Producto> getAll() throws SQLException {
        return productoRepo.findAll();
    }

    public List<Producto> getPaginated(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado, int limit, int offset) throws SQLException {
        return productoRepo.findPaginated(busqueda, categoriaId, area, resguardante, estado, false, limit, offset, null, null);
    }

    public List<Producto> getPaginated(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado, int limit, int offset,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return productoRepo.findPaginated(busqueda, categoriaId, area, resguardante, estado, false, limit, offset, desdeReg, hastaReg);
    }

    public int countFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado) throws SQLException {
        return productoRepo.countFiltrado(busqueda, categoriaId, area, resguardante, estado, false, null, null);
    }

    public int countFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return productoRepo.countFiltrado(busqueda, categoriaId, area, resguardante, estado, false, desdeReg, hastaReg);
    }

    public List<Producto> getAllFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado) throws SQLException {
        return productoRepo.findAllFiltrado(busqueda, categoriaId, area, resguardante, estado, null, null);
    }

    public List<Producto> getAllFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return productoRepo.findAllFiltrado(busqueda, categoriaId, area, resguardante, estado, desdeReg, hastaReg);
    }

    public List<String> getResguardantes() throws SQLException {
        return productoRepo.findDistinctResguardantes();
    }

    public ProductoRepository.InventarioStats getStats() throws SQLException {
        return productoRepo.findStats();
    }

    public Optional<Producto> findById(String id) throws SQLException {
        return productoRepo.findById(id);
    }

    public List<Producto> getAgotados() throws SQLException {
        return productoRepo.findAgotados();
    }

    public List<Producto> getBajoStock() throws SQLException {
        return productoRepo.findBajoStock();
    }

    public List<Producto> getVencidosProximos(int dias) throws SQLException {
        return productoRepo.findVencidosProximos(dias);
    }

    public Producto save(Producto p) throws SQLException, ValidationException {
        validate(p);
        boolean isNew = p.getId() == null;
        Producto saved = productoRepo.save(p);
        log.info("Bien {} [{}] '{}'", isNew ? "registrado" : "actualizado", saved.getId(), saved.getNombre());
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
                throw new ValidationException(
                    "No se puede eliminar: este bien tiene movimientos o conteos registrados en su historial");
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
        log.info("Baja patrimonial bien [{}] '{}' — motivo: {}", id, p.getNombre(), motivo.trim());
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
        log.info("Bien reactivado [{}] '{}'", id, p.getNombre());
        auditRepo.log("producto", id, p.getNombre(), "reactivar", "Bien reactivado tras baja");
    }

    /** Includes bienes dados de baja — used only by the "Dados de baja"
     *  history view (see ProductosController). */
    public List<Producto> getAllIncludingBaja() throws SQLException {
        return productoRepo.findAll(true);
    }

    public long countAll() throws SQLException {
        return productoRepo.countAll();
    }

    private void validate(Producto p) throws ValidationException, SQLException {
        if (p.getNombre() == null || p.getNombre().isBlank())
            throw new ValidationException("El nombre es obligatorio");
        if (p.getNombre().length() > 200)
            throw new ValidationException("El nombre no puede exceder 200 caracteres");
        if (p.getCodigo() == null || p.getCodigo().isBlank())
            throw new ValidationException("El codigo es obligatorio");
        if (p.getCodigo().length() > 50)
            throw new ValidationException("El codigo no puede exceder 50 caracteres");
        if (p.getDescripcion() != null && p.getDescripcion().length() > 1000)
            throw new ValidationException("La descripcion no puede exceder 1000 caracteres");
        if (p.getProveedor() != null && p.getProveedor().length() > 200)
            throw new ValidationException("El proveedor no puede exceder 200 caracteres");
        if (p.getUbicacion() != null && p.getUbicacion().length() > 200)
            throw new ValidationException("La ubicacion no puede exceder 200 caracteres");
        if (p.getResguardante() != null && p.getResguardante().length() > 200)
            throw new ValidationException("El resguardante no puede exceder 200 caracteres");
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
        // Validaciones patrimoniales de depreciación
        if (p.getVidaUtilAnios() != null && p.getVidaUtilAnios() <= 0)
            throw new ValidationException("La vida util debe ser mayor a cero");
        if (p.getValorResidual() != null && p.getPrecioCompra() != null
                && p.getValorResidual().compareTo(p.getPrecioCompra()) > 0)
            throw new ValidationException("El valor residual no puede ser mayor al precio de compra");
        if (p.getFechaAdquisicion() != null && p.getFechaAdquisicion().isAfter(LocalDate.now()))
            throw new ValidationException("La fecha de adquisicion no puede ser en el futuro");
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
