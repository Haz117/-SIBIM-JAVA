package com.sibim.service;

import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoMantenimientoRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Alertas de mantenimiento preventivo por producto — ver {@link ProductoMantenimientoRepository}
 *  para el almacenamiento (tabla producto_mantenimiento, migración V13). */
public class MantenimientoService {

    public record Alerta(String id, String descripcion, LocalDate fecha, boolean completada) {}

    /** Whether the caller may see the bien with this id (its área scope). */
    @FunctionalInterface
    interface Acceso { boolean aBien(String productoId) throws SQLException; }

    private final ProductoMantenimientoRepository repo;
    private final AuditLogRepository auditRepo;
    private final Acceso acceso;

    public MantenimientoService() { this(new ProductoMantenimientoRepository()); }
    public MantenimientoService(ProductoMantenimientoRepository repo) {
        // findById is scoped to the caller's áreas.
        this(repo, new AuditLogRepository(),
            id -> new com.sibim.repository.ProductoRepository().findById(id).isPresent());
    }
    MantenimientoService(ProductoMantenimientoRepository repo, AuditLogRepository auditRepo) {
        this(repo, auditRepo, id -> true);
    }
    MantenimientoService(ProductoMantenimientoRepository repo, AuditLogRepository auditRepo, Acceso acceso) {
        this.repo = repo;
        this.auditRepo = auditRepo;
        this.acceso = acceso;
    }

    static final String SIN_ACCESO = "Ese bien no es de tus áreas";

    /** The maintenance of a bien is kept by whoever answers for it: knowing
     *  the id of a bien (or of an alert) of another área is not enough. */
    private void exigirAcceso(String productoId) throws SQLException {
        if (productoId == null || !acceso.aBien(productoId)) throw new SecurityException(SIN_ACCESO);
    }

    public List<Alerta> getAlertas(String productoId) {
        try {
            if (productoId == null || !acceso.aBien(productoId)) return List.of();
        } catch (SQLException e) {
            return List.of();
        }
        return repo.findByProducto(productoId).stream()
            .map(a -> new Alerta(a.id(), a.descripcion(), a.fecha(), a.completada()))
            .toList();
    }

    public void agregarAlerta(String productoId, String descripcion, LocalDate fecha) throws SQLException {
        exigirAcceso(productoId);
        repo.agregar(productoId, descripcion, fecha);
        auditRepo.log("mantenimiento", productoId, descripcion, "crear",
            "Alerta de mantenimiento programada para " + fecha);
    }

    /** An alert that no longer exists has nothing to protect. */
    private void exigirAccesoALaAlerta(String alertaId) throws SQLException {
        String productoId = repo.productoDe(alertaId);
        if (productoId != null) exigirAcceso(productoId);
    }

    public void marcarCompletada(String id) throws SQLException {
        exigirAccesoALaAlerta(id);
        repo.marcarCompletada(id);
        auditRepo.log("mantenimiento", id, null, "completar", "Alerta de mantenimiento marcada como completada");
    }

    public void eliminarAlerta(String id) throws SQLException {
        exigirAccesoALaAlerta(id);
        repo.eliminar(id);
        auditRepo.log("mantenimiento", id, null, "eliminar", "Alerta de mantenimiento eliminada");
    }

    /** Alertas pendientes y próximas a vencer (dentro de {@code days} días) en todo el
     *  inventario — usado por Alertas, Dashboard y el resumen semanal por correo. */
    public List<String[]> getProximasGlobal(int days) {
        return repo.findProximas(days).stream()
            .map(a -> new String[]{ a.productoId(), a.descripcion(), a.fecha().toString() })
            .toList();
    }
}
