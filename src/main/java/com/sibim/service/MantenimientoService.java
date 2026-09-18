package com.sibim.service;

import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoMantenimientoRepository;

import java.time.LocalDate;
import java.util.List;

/** Alertas de mantenimiento preventivo por producto — ver {@link ProductoMantenimientoRepository}
 *  para el almacenamiento (tabla producto_mantenimiento, migración V13). */
public class MantenimientoService {

    public record Alerta(String id, String descripcion, LocalDate fecha, boolean completada) {}

    private final ProductoMantenimientoRepository repo;
    private final AuditLogRepository auditRepo;

    public MantenimientoService() { this(new ProductoMantenimientoRepository(), new AuditLogRepository()); }
    public MantenimientoService(ProductoMantenimientoRepository repo) { this(repo, new AuditLogRepository()); }
    MantenimientoService(ProductoMantenimientoRepository repo, AuditLogRepository auditRepo) {
        this.repo = repo;
        this.auditRepo = auditRepo;
    }

    public List<Alerta> getAlertas(String productoId) {
        return repo.findByProducto(productoId).stream()
            .map(a -> new Alerta(a.id(), a.descripcion(), a.fecha(), a.completada()))
            .toList();
    }

    public void agregarAlerta(String productoId, String descripcion, LocalDate fecha) {
        repo.agregar(productoId, descripcion, fecha);
        auditRepo.log("mantenimiento", productoId, descripcion, "crear",
            "Alerta de mantenimiento programada para " + fecha);
    }

    public void marcarCompletada(String id) {
        repo.marcarCompletada(id);
        auditRepo.log("mantenimiento", id, null, "completar", "Alerta de mantenimiento marcada como completada");
    }

    public void eliminarAlerta(String id) {
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
