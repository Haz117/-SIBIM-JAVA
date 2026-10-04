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
        if (!disponible() || !com.sibim.session.Permisos.atiendeSolicitudes()) return List.of();
        return repo.findPendientes();
    }

    public int countPendientes() throws SQLException {
        if (!disponible() || !com.sibim.session.Permisos.atiendeSolicitudes()) return 0;
        return repo.countPendientes();
    }

    /** What the caller's áreas have asked for, with Patrimonio's answer. */
    public List<Solicitud> deMisAreas() throws SQLException {
        if (!disponible()) return List.of();
        return repo.findDeMisAreas();
    }

    /** The áreas no longer ask Patrimonio for documents through SIBIM: a
     *  secretario registers his own préstamos and every área its resguardos.
     *  What is left of this service is the inbox of requests sent before that. */
    static final String SIN_SOLICITUDES =
        "Las áreas ya no envían solicitudes por SIBIM: los préstamos los registra el secretario o Patrimonio, "
        + "y los resguardos cada área.";

    public Solicitud solicitarPrestamo(String productoId, String areaDestino, String responsable, String cargo,
                                       String motivo, LocalDate fechaDevolucion) throws SQLException {
        throw new SecurityException(SIN_SOLICITUDES);
    }

    public Solicitud solicitarResguardo(String productoId, String resguardante, String cargo, String observaciones)
            throws SQLException {
        throw new SecurityException(SIN_SOLICITUDES);
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
        if (!com.sibim.session.Permisos.atiendeSolicitudes())
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
