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
 * (Patrimonio). An área asked for one here; the administrator approves it —
 * which creates the document through {@link PrestamoService} or
 * {@link ResguardoService}, with all their rules — or rejects it with a reason.
 *
 * <p>Bajas (5-nov-2026): an área asks Patrimonio to dar de baja a bien
 * ({@link #solicitarBaja}); the administrator gets the notice, looks at the
 * bien and either gives it de baja — from the inbox or straight from Bienes,
 * which closes the request ({@link #bajaRegistrada}) — or says it doesn't
 * proceed, with the reason the área sees. The área still hands in the signed
 * formato at Recursos Materiales y Patrimonio: Patrimonio marks it received
 * with its folio ({@link #recibirFormato}), and the baja asks for that folio.
 */
public class SolicitudService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SolicitudService.class);

    /** Why a bien is asked to be written off: the same boxes as the printed formato. */
    public static final List<String> MOTIVOS_BAJA = List.of("Inservible / descompuesto", "Obsoleto",
        "Desgaste por uso", "Extravío", "Robo (anexar denuncia)", "Siniestro", "Otro");

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

    /** Waiting requests of one kind ({@link Solicitud#TIPO_BAJA}…) in the administrator's inbox. */
    public int countPendientes(String tipo) throws SQLException {
        if (!disponible() || !com.sibim.session.Permisos.atiendeSolicitudes()) return 0;
        return repo.countPendientes(tipo);
    }

    // ── Solicitudes de baja ──────────────────────────────────────────────────

    /** What {@link #solicitarBaja} did: the requests sent, and the bienes that
     *  already had one in wait (nothing new is sent for those). */
    public record EnvioBaja(List<Solicitud> enviadas, List<Producto> yaEnEspera) {}

    /** "Obsoleto: ya no enciende", or just the motivo when nothing else was written. */
    public static String textoMotivo(String motivo, String detalle) {
        String m = motivo == null ? "" : motivo.trim();
        String d = detalle == null ? "" : detalle.trim();
        if (d.isEmpty()) return m;
        return "Otro".equals(m) || m.isEmpty() ? d : m + ": " + d;
    }

    /**
     * An área asks Patrimonio to dar de baja these bienes of its own, one
     * request per bien. Nothing is sent if one of them can't be asked for (not
     * the caller's, or already de baja); a bien that already has a request in
     * wait is skipped and returned in {@link EnvioBaja#yaEnEspera()}.
     */
    public EnvioBaja solicitarBaja(List<Producto> bienes, String motivo, String detalle) throws SQLException {
        if (!disponible())
            throw new IllegalStateException("La solicitud de baja necesita conexión con el servidor: "
                + "imprime el formato y entrégalo firmado a Patrimonio");
        if (com.sibim.session.Permisos.atiendeSolicitudes())
            throw new IllegalStateException("Patrimonio no se pide la baja a sí mismo: usa «Dar de baja»");
        if (bienes == null || bienes.isEmpty()) throw new IllegalArgumentException("Elige el bien que quieres dar de baja");
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Elige el motivo de la baja");
        if ("Otro".equals(motivo.trim()) && (detalle == null || detalle.isBlank()))
            throw new IllegalArgumentException("Escribe cuál es el motivo de la baja");
        if (detalle != null && detalle.trim().length() > 500)
            throw new IllegalArgumentException("La descripción no puede pasar de 500 caracteres");

        // The bienes as they are now, and only the caller's (findById is scoped to its áreas).
        List<Producto> actuales = new java.util.ArrayList<>();
        for (Producto b : bienes) {
            Producto p = productoRepo.findById(b.getId()).orElse(null);
            if (p == null || !com.sibim.session.Permisos.esAreaPropia(p.getArea()))
                throw new SecurityException("Solo puedes pedir la baja de los bienes de tu área: «" + b.getNombre() + "» no es de ella");
            if (p.isDadoDeBaja())
                throw new IllegalArgumentException("«" + p.getNombre() + "» ya está dado de baja");
            actuales.add(p);
        }

        String texto = textoMotivo(motivo, detalle);
        List<Solicitud> enviadas = new java.util.ArrayList<>();
        List<Producto> yaEnEspera = new java.util.ArrayList<>();
        for (Producto p : actuales) {
            if (repo.existePendiente(p.getId(), Solicitud.TIPO_BAJA)) { yaEnEspera.add(p); continue; }
            String quien = yo();
            String responsable = p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante()
                : quien != null ? quien : p.getArea();
            Solicitud s;
            try {
                s = repo.save(new Solicitud(null, Solicitud.TIPO_BAJA, p.getId(), p.getNombre(), p.getCodigo(),
                    p.getArea(), quien, null, responsable, null, texto, null,
                    Solicitud.ESTADO_PENDIENTE, null, null, null, null, null));
            } catch (SQLException e) {
                // Someone else of the área sent it at the same moment (V35's unique index).
                if ("23505".equals(e.getSQLState())) { yaEnEspera.add(p); continue; }
                throw e;
            }
            enviadas.add(s);
            auditRepo.log("solicitud", s.id(), s.productoNombre(), "crear",
                "Solicitud de baja de " + (p.getCodigo() != null ? p.getCodigo() : p.getNombre()) + " · " + texto);
        }
        return new EnvioBaja(enviadas, yaEnEspera);
    }

    /** How the baja form appends the signed formato's folio to the motivo
     *  (ProductoBajasDialog; read back in Bajas as "Solicitud"). */
    public static final String SEPARADOR_FOLIO = " · Solicitud de baja: ";

    /** The signed formato's folio written in a baja's motivo, or null. */
    public static String folioDelFormato(String motivoBaja) {
        if (motivoBaja == null) return null;
        int i = motivoBaja.indexOf(SEPARADOR_FOLIO);
        if (i < 0) return null;
        String folio = motivoBaja.substring(i + SEPARADOR_FOLIO.length()).trim();
        return folio.isEmpty() ? null : folio;
    }

    /**
     * Patrimonio received the área's signed formato de baja at Recursos
     * Materiales y Patrimonio, with this folio (or oficio). The request stays
     * in wait until the bien is given de baja or it doesn't proceed.
     */
    public void recibirFormato(String solicitudId, String folio) throws SQLException {
        if (folio == null || folio.isBlank()) throw new IllegalArgumentException("Anota el folio u oficio del formato firmado");
        if (folio.trim().length() > 100) throw new IllegalArgumentException("El folio no puede pasar de 100 caracteres");
        Solicitud s = pendiente(solicitudId);
        if (!s.esBaja()) throw new IllegalArgumentException("Solo las solicitudes de baja llevan formato firmado");
        if (!repo.recibirFormato(s.id(), folio.trim(), yo()))
            throw new IllegalStateException("Esta solicitud ya fue atendida");
        auditRepo.log("solicitud", s.id(), s.productoNombre(), "actualizar",
            "Formato de solicitud de baja recibido · folio " + folio.trim());
    }

    /**
     * The bien was given de baja (from the inbox, or straight from Bienes):
     * the área's request in wait, if there was one, is closed as attended so
     * the área sees it, and its formato counts as received with the folio the
     * baja asked for. Never fails the baja — at worst the request stays open.
     */
    public static void bajaRegistrada(String productoId, String motivoBaja, String numeroActa) {
        if (productoId == null || !disponible()) return;
        try {
            String documento = numeroActa != null && !numeroActa.isBlank() ? "Acta de baja " + numeroActa.trim()
                : "Baja registrada el " + com.sibim.util.FormatUtils.formatDate(LocalDate.now());
            new SolicitudRepository().resolverBajaDe(productoId, documento, yo(), folioDelFormato(motivoBaja));
        } catch (Exception e) {
            log.warn("No se pudo cerrar la solicitud de baja del bien {}: {}", productoId, e.getMessage());
        }
    }

    /** What the caller's áreas have asked for, with Patrimonio's answer. */
    public List<Solicitud> deMisAreas() throws SQLException {
        if (!disponible()) return List.of();
        return repo.findDeMisAreas();
    }

    /** The áreas no longer send requests through SIBIM (see Permisos): what
     *  they need they ask of Patrimonio outside the system. What is left of
     *  this service is the inbox of requests sent before that. */
    static final String SIN_SOLICITUDES =
        "Las áreas ya no envían solicitudes por SIBIM: los préstamos y resguardos los registra "
        + "Patrimonio (administrador). Pídelos directamente a Patrimonio.";

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
        if (s.esBaja())
            throw new IllegalStateException("Una baja se registra con «Dar de baja»: pide el folio de la solicitud firmada");
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
}
