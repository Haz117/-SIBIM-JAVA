package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Solicitud;
import com.sibim.model.Usuario;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.repository.SolicitudRepository;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Préstamos and resguardos are registered only by the administrator
 * (Patrimonio). An área asks for one here; the administrator approves it —
 * which creates the document through {@link PrestamoService} or
 * {@link ResguardoService}, with all their rules — or rejects it with a reason.
 */
public class SolicitudService {

    private final SolicitudRepository repo;
    private final ProductoRepository  productoRepo;
    private final PrestamoService     prestamos;
    private final ResguardoService    resguardos;
    private final AuditLogRepository  auditRepo;

    public SolicitudService() {
        this(new SolicitudRepository(), new ProductoRepository(), new PrestamoService(), new ResguardoService(),
             new AuditLogRepository());
    }

    SolicitudService(SolicitudRepository repo, ProductoRepository productoRepo, PrestamoService prestamos,
                     ResguardoService resguardos, AuditLogRepository auditRepo) {
        this.repo         = repo;
        this.productoRepo = productoRepo;
        this.prestamos    = prestamos;
        this.resguardos   = resguardos;
        this.auditRepo    = auditRepo;
    }

    /** Requests need the server; in demo or offline mode there are none. */
    public static boolean disponible() {
        return !DatabaseConfig.isDemoMode() && !DatabaseConfig.isOfflineMode();
    }

    /** The administrator's inbox (empty for everyone else and without a connection). */
    public List<Solicitud> pendientes() throws SQLException {
        if (!disponible() || !SessionManager.isAdmin()) return List.of();
        return repo.findPendientes();
    }

    public int countPendientes() throws SQLException {
        if (!disponible() || !SessionManager.isAdmin()) return 0;
        return repo.countPendientes();
    }

    /** What the caller's áreas have asked for, with Patrimonio's answer. */
    public List<Solicitud> deMisAreas() throws SQLException {
        if (!disponible()) return List.of();
        return repo.findDeMisAreas();
    }

    public Solicitud solicitarPrestamo(String productoId, String areaDestino, String responsable, String cargo,
                                       String motivo, LocalDate fechaDevolucion) throws SQLException {
        if (areaDestino == null || areaDestino.isBlank())
            throw new IllegalArgumentException("El área destino es obligatoria");
        if (fechaDevolucion == null || !fechaDevolucion.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha de devolución debe ser posterior a hoy");
        Producto p = bien(productoId, responsable);
        if (areaDestino.trim().equals(p.getArea()))
            throw new IllegalArgumentException("El área destino debe ser distinta al área donde está el bien");
        return guardar(Solicitud.TIPO_PRESTAMO, p, areaDestino.trim(), responsable, cargo, motivo, fechaDevolucion);
    }

    public Solicitud solicitarResguardo(String productoId, String resguardante, String cargo, String observaciones)
            throws SQLException {
        Producto p = bien(productoId, resguardante);
        return guardar(Solicitud.TIPO_RESGUARDO, p, null, resguardante, cargo, observaciones, null);
    }

    private Producto bien(String productoId, String responsable) throws SQLException {
        if (SessionManager.getCurrentUser() == null) throw new SecurityException("Inicia sesión para enviar solicitudes");
        if (productoId == null || productoId.isBlank()) throw new IllegalArgumentException("Debe seleccionar un bien");
        if (responsable == null || responsable.isBlank())
            throw new IllegalArgumentException("El nombre del responsable es obligatorio");
        // findById is scoped to the caller's áreas: nobody asks for someone else's bien.
        Producto p = productoRepo.findById(productoId)
            .orElseThrow(() -> new IllegalArgumentException("Bien no encontrado"));
        PrestamoService.exigirDisponible(p);
        return p;
    }

    private Solicitud guardar(String tipo, Producto p, String areaDestino, String responsable, String cargo,
                              String motivo, LocalDate fecha) throws SQLException {
        if (repo.existePendiente(p.getId(), tipo))
            throw new IllegalArgumentException("Este bien ya tiene una solicitud de "
                + (Solicitud.TIPO_PRESTAMO.equals(tipo) ? "préstamo" : "resguardo") + " esperando respuesta");
        Usuario yo = SessionManager.getCurrentUser();
        Solicitud guardada = repo.save(new Solicitud(null, tipo, p.getId(), p.getNombre(), p.getCodigo(),
            p.getArea() != null ? p.getArea() : "Sin área", yo.getNombre(),
            areaDestino, responsable.trim(), limpio(cargo), limpio(motivo), fecha,
            Solicitud.ESTADO_PENDIENTE, null, null, null, null, null));
        auditRepo.log("solicitud", guardada.id(), p.getNombre(), "crear",
            "Solicitud de " + guardada.tipoTexto().toLowerCase() + " · " + guardada.area()
                + " · Responsable: " + guardada.responsable());
        return guardada;
    }

    /** Creates the préstamo or resguardo that was asked for and returns its folio. */
    public String aprobar(String solicitudId) throws Exception {
        Solicitud s = pendiente(solicitudId);
        String folio;
        if (s.esPrestamo()) {
            folio = prestamos.crear(s.productoId(), s.areaDestino(), s.responsable(), s.cargo(), s.motivo(),
                s.fechaDevolucion()).getNumero();
        } else {
            Producto p = productoRepo.findById(s.productoId())
                .orElseThrow(() -> new IllegalArgumentException("El bien de la solicitud ya no existe"));
            ResguardoItem item = new ResguardoItem();
            item.setProductoId(p.getId());
            item.setProductoNombre(p.getNombre());
            item.setProductoCodigo(p.getCodigo());
            item.setArea(p.getArea());
            item.setValorUnitario(p.getValorUnitario());
            item.setNumeroSerie(p.getNumeroSerie());
            folio = resguardos.crear(s.responsable(), s.cargo(), p.getArea(), List.of(item), s.motivo()).getNumero();
        }
        repo.resolver(s.id(), Solicitud.ESTADO_APROBADA, null, folio, yo());
        auditRepo.log("solicitud", s.id(), s.productoNombre(), "aprobar",
            "Solicitud de " + s.tipoTexto().toLowerCase() + " aprobada · " + folio);
        return folio;
    }

    public void rechazar(String solicitudId, String motivo) throws SQLException {
        if (motivo == null || motivo.isBlank())
            throw new IllegalArgumentException("Escribe el motivo del rechazo: el área lo verá");
        Solicitud s = pendiente(solicitudId);
        if (!repo.resolver(s.id(), Solicitud.ESTADO_RECHAZADA, motivo.trim(), null, yo()))
            throw new IllegalStateException("Esta solicitud ya fue atendida");
        auditRepo.log("solicitud", s.id(), s.productoNombre(), "rechazar",
            "Solicitud de " + s.tipoTexto().toLowerCase() + " rechazada · " + motivo.trim());
    }

    private Solicitud pendiente(String id) throws SQLException {
        if (!SessionManager.isAdmin())
            throw new SecurityException("Solo el administrador (Patrimonio) atiende las solicitudes.");
        DatabaseConfig.exigirServidor("Atender solicitudes");
        Solicitud s = repo.findById(id);
        if (s == null) throw new IllegalArgumentException("Solicitud no encontrada");
        if (!s.isPendiente()) throw new IllegalStateException("Esta solicitud ya fue atendida");
        return s;
    }

    private static String yo() {
        Usuario u = SessionManager.getCurrentUser();
        return u != null ? u.getNombre() : null;
    }

    private static String limpio(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
