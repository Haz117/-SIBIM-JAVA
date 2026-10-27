package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MovimientoService {

    private static final Logger log = LoggerFactory.getLogger(MovimientoService.class);

    private final MovimientoRepository movimientoRepo;
    private final ProductoRepository productoRepo;
    private final AuditLogRepository auditRepo;
    private final ProductoService.Compromiso documentos;

    public MovimientoService() {
        this(new MovimientoRepository(), new ProductoRepository(), new AuditLogRepository(),
            ProductoService::documentoVigente);
    }

    MovimientoService(MovimientoRepository movimientoRepo, ProductoRepository productoRepo,
                      AuditLogRepository auditRepo) {
        this(movimientoRepo, productoRepo, auditRepo, id -> Optional.empty());
    }

    MovimientoService(MovimientoRepository movimientoRepo, ProductoRepository productoRepo,
                      AuditLogRepository auditRepo, ProductoService.Compromiso documentos) {
        this.movimientoRepo = movimientoRepo;
        this.productoRepo   = productoRepo;
        this.auditRepo      = auditRepo;
        this.documentos     = documentos;
    }

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

    public Map<String, List<Movimiento>> getByProductoIds(List<String> ids) throws SQLException {
        return movimientoRepo.findByProductoIds(ids);
    }

    // ── Server-side pagination ────────────────────────────────────────────────

    public List<Movimiento> getPaginated(LocalDate desde, LocalDate hasta,
            String query, String tipo, String categoriaNombre,
            int limit, int offset) throws SQLException {
        return movimientoRepo.findPaginated(desde, hasta, query, tipo, categoriaNombre, limit, offset);
    }

    public int countFiltrado(LocalDate desde, LocalDate hasta,
            String query, String tipo, String categoriaNombre) throws SQLException {
        return movimientoRepo.countFiltrado(desde, hasta, query, tipo, categoriaNombre);
    }

    /** {@link #getPaginated} for one área ({@code area} null = all). */
    public List<Movimiento> getPaginated(LocalDate desde, LocalDate hasta, String query, String tipo,
            String categoriaNombre, String area, int limit, int offset) throws SQLException {
        return movimientoRepo.findPaginated(desde, hasta, query, tipo, categoriaNombre, area, limit, offset);
    }

    /** {@link #countFiltrado} for one área ({@code area} null = all). */
    public int countFiltrado(LocalDate desde, LocalDate hasta, String query, String tipo,
            String categoriaNombre, String area) throws SQLException {
        return movimientoRepo.countFiltrado(desde, hasta, query, tipo, categoriaNombre, area);
    }

    public MovimientoRepository.MovimientoStats getStats(LocalDate desde, LocalDate hasta) throws SQLException {
        return movimientoRepo.findStats(desde, hasta);
    }

    public List<String> getCategorias(LocalDate desde, LocalDate hasta) throws SQLException {
        return movimientoRepo.findDistinctCategorias(desde, hasta);
    }

    public Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad,
                                String motivo, String referencia) throws SQLException, ValidationException {
        return registrar(productoId, tipo, cantidad, motivo, referencia, null);
    }

    /** @param areaDestino required (and only meaningful) for TRANSFERENCIA —
     *  the area the product is being moved TO. Ignored for other types. */
    public Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad,
                                String motivo, String referencia, String areaDestino) throws SQLException, ValidationException {
        return registrar(productoId, tipo, cantidad, motivo, referencia, areaDestino, null, false);
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
        return registrar(productoId, TipoMovimiento.AJUSTE, nuevoStock, motivo, referencia, null, expectedStockAnterior, false);
    }

    static final String SOLO_ADMIN_MOVIMIENTOS =
        "Solo el administrador (Patrimonio) registra movimientos. Tu área puede actualizar los datos "
        + "de los bienes que tiene asignados.";

    /** @param esReversion undoing an entrada may take the bien back to the 0 it had,
     *  which "Quitar" may not ({@link #QUITAR_TODO}) */
    private Movimiento registrar(String productoId, TipoMovimiento tipo, int cantidad, String motivo,
                                 String referencia, String areaDestino, Integer expectedStockAnterior,
                                 boolean esReversion)
            throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        // Every movement is Patrimonio's, transfers included: the áreas no longer
        // request them through SIBIM (they ask Finanzas).
        if (!SessionManager.isAdmin())
            throw new ValidationException(SOLO_ADMIN_MOVIMIENTOS);
        Optional<Producto> opt = productoRepo.findById(productoId);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto producto = opt.get();

        if (!SessionManager.isAreaAccessible(producto.getArea()))
            throw new ValidationException("No tienes acceso a esa área");
        if (producto.isDadoDeBaja())
            throw new ValidationException(BIEN_DE_BAJA);

        int existencia = producto.getStockActual();
        if (tipo == TipoMovimiento.AJUSTE) {
            if (cantidad < 0)
                throw new ValidationException("El ajuste no puede ser negativo");
            if (cantidad == existencia)
                throw new ValidationException("El ajuste no cambia nada: la cantidad ya es " + existencia);
        } else {
            // A transfer moves the bien's whole existencia: with none, say so instead of
            // asking for a quantity (Transferir arrastrando sends the bien's own, 0).
            if (tipo == TipoMovimiento.TRANSFERENCIA && existencia <= 0)
                throw new ValidationException(SIN_EXISTENCIA);
            if (cantidad <= 0)
                throw new ValidationException("La cantidad debe ser mayor a cero");
        }
        if (tipo == TipoMovimiento.SALIDA && cantidad > existencia)
            throw new ValidationException("La cantidad supera la existencia disponible (" + existencia + ")");
        // A bien left with 0 pieces stays in the inventory worth $0 and can't be
        // transferred; taking it out entirely is a baja, with its motivo and destino.
        if (tipo == TipoMovimiento.SALIDA && cantidad == existencia && !esReversion)
            throw new ValidationException(QUITAR_TODO);

        if (tipo == TipoMovimiento.TRANSFERENCIA) {
            if (areaDestino == null || areaDestino.isBlank())
                throw new ValidationException("Selecciona el área de destino de la transferencia");
            if (areaDestino.equals(producto.getArea()))
                throw new ValidationException("El área de destino debe ser distinta al área actual");
            if (!com.sibim.config.Areas.getAllAreaNames().contains(areaDestino))
                throw new ValidationException("\"" + areaDestino + "\" no es un área del organigrama");
            // A transfer relocates the whole bien (área + código); there is no partial transfer.
            if (cantidad != existencia)
                throw new ValidationException("La transferencia mueve el bien completo: la cantidad debe ser "
                    + existencia + " (su existencia actual)");
            boolean yaPendiente = movimientoRepo.findPendientesTransferencias().stream()
                .anyMatch(p -> productoId.equals(p.getProductoId()));
            if (yaPendiente)
                throw new ValidationException("Este bien ya tiene una transferencia pendiente de aprobación");
            // A préstamo, comodato or resguardo in force names the área or the
            // person that has the bien; moving it would contradict the document.
            Optional<String> documento = documentos.de(productoId);
            if (documento.isPresent())
                throw new ValidationException("No se puede transferir: el bien " + documento.get());
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
        var session = SessionManager.getCurrentUser();
        if (session == null) throw new IllegalStateException("No hay sesión activa");
        m.setUsuarioId(session.getId());
        m.setUsuarioNombre(session.getNombre());

        try {
            Movimiento saved = movimientoRepo.addMovimientoAtomic(m, expectedStockAnterior);
            despuesDeCambiar(productoId);
            auditRepo.log("movimiento", saved.getId(), m.getProductoNombre(), "crear",
                detalleAuditoria(m, producto.getCodigo(), producto.getArea()));
            log.info("Movimiento {} [{}] '{}' {} uds — stock {} → {}",
                tipo, saved.getId(), m.getProductoNombre(), m.getCantidad(),
                m.getStockAnterior(), m.getStockNuevo());
            return saved;
        } catch (SQLException e) {
            if (e instanceof com.sibim.db.ReglaIncumplida) throw new ValidationException(e.getMessage());
            throw e;
        }
    }

    /** Admin: every request. Anyone else: the requests that leave or reach
     *  their áreas, so an área can follow what it asked for. */
    public List<Movimiento> getPendientesTransferencias() throws SQLException {
        List<Movimiento> todas = movimientoRepo.findPendientesTransferencias();
        Set<String> areas = SessionManager.getAccessibleAreas();
        if (areas == null) return todas;
        return todas.stream()
            .filter(m -> areas.contains(m.getAreaOrigen()) || areas.contains(m.getAreaDestino()))
            .toList();
    }

    /** Transfers already applied that the receiving área hasn't confirmed:
     *  for an área, those coming to it; for the admin, all of them (to follow
     *  up). Empty offline — reception is only tracked on the server. */
    public List<Movimiento> getPorRecibir() throws SQLException {
        return movimientoRepo.findPorRecibir(SessionManager.getAccessibleAreas());
    }

    static final String RECIBIR_REQUIERE_CONEXION =
        "Confirmar que recibiste un bien requiere conexión con el servidor. Inténtalo al reconectar.";

    /** The destination área (or the admin, e.g. for an área without an
     *  account) confirms it physically has the bien. */
    public void confirmarRecepcion(Movimiento m) throws SQLException, ValidationException {
        if (com.sibim.db.DatabaseConfig.isOfflineMode()) throw new ValidationException(RECIBIR_REQUIERE_CONEXION);
        if (!SessionManager.isAreaAccessible(m.getAreaDestino()))
            throw new ValidationException("Solo el área que recibe (" + m.getAreaDestino() + ") puede confirmar la recepción");
        var yo = SessionManager.getCurrentUser();
        if (yo == null) throw new IllegalStateException("No hay sesión activa");
        if (!movimientoRepo.confirmarRecepcion(m.getId(), yo.getNombre()))
            throw new ValidationException("Esta transferencia ya se había confirmado como recibida");
        auditRepo.log("movimiento", m.getId(), m.getProductoNombre(), "transferencia_recibida",
            "Recibido en " + m.getAreaDestino() + " por " + yo.getNombre());
    }

    /** The área move and the new código are applied together inside the
     *  repository's transaction (MovimientoRepository#aprobarTransferencia). */
    public void aprobarTransferencia(String movimientoId) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        requireAdminForTransferWorkflow();
        requireConexionParaResolver();
        try {
            movimientoRepo.aprobarTransferencia(movimientoId);
        } catch (SQLException e) {
            if (e instanceof com.sibim.db.ReglaIncumplida) throw new ValidationException(e.getMessage());
            throw e;
        }
        despuesDeCambiar(movimientoRepo.findProductoIdById(movimientoId).orElse(null));
        auditRepo.log("movimiento", movimientoId, movimientoId, "transferencia_aprobada",
            "Transferencia aprobada por administrador");
    }

    public void rechazarTransferencia(String movimientoId) throws SQLException, ValidationException {
        rechazarTransferencia(movimientoId, null);
    }

    public void rechazarTransferencia(String movimientoId, String motivo) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        requireAdminForTransferWorkflow();
        requireConexionParaResolver();
        try {
            movimientoRepo.rechazarTransferencia(movimientoId, motivo);
        } catch (SQLException e) {
            if (e instanceof com.sibim.db.ReglaIncumplida) throw new ValidationException(e.getMessage());
            throw e;
        }
        ProductosEnMemoria.invalidar();
        auditRepo.log("movimiento", movimientoId, movimientoId, "transferencia_rechazada",
            "Transferencia rechazada por administrador"
                + (motivo != null && !motivo.isBlank() ? ": " + motivo : ""));
    }

    /** Creates a compensating movement that undoes the effect of {@code original}.
     *  TRANSFERENCIA cannot be reversed here — use the approve/reject workflow.
     *  @param razon optional reason stored in the new movement's motivo */
    public Movimiento revertirMovimiento(Movimiento original, String razon)
            throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        if (original.getTipo() == TipoMovimiento.TRANSFERENCIA)
            throw new ValidationException(
                "Las transferencias se gestionan con el flujo de aprobación — usa Rechazar en el panel de pendientes.");
        String fechaStr = original.getCreadoEn() != null
            ? FormatUtils.formatDate(original.getCreadoEn().toLocalDate()) : "?";
        String motivoRev = "Reversión de " + original.getTipo().getEtiqueta().toLowerCase()
            + " del " + fechaStr
            + (razon != null && !razon.isBlank() ? ": " + razon : "");
        String refRev = "REV-" + original.getId().substring(0, Math.min(8, original.getId().length()));
        return switch (original.getTipo()) {
            case ENTRADA -> registrar(original.getProductoId(), TipoMovimiento.SALIDA,
                    original.getCantidad(), motivoRev, refRev, null, null, true);
            case SALIDA  -> registrar(original.getProductoId(), TipoMovimiento.ENTRADA,
                    original.getCantidad(), motivoRev, refRev);
            case AJUSTE  -> registrarAjusteVerificado(original.getProductoId(),
                    original.getStockAnterior(), motivoRev, refRev, original.getStockNuevo());
            default -> throw new ValidationException("Tipo de movimiento no reversible");
        };
    }

    /** Once the write is done (not before: a screen that reloaded in between
     *  cached the old cantidad or área for 30 s): the shared list is dropped
     *  and the ficha the bien's QR downloads, if it has one, is brought up to
     *  date with its new área and código. */
    private static void despuesDeCambiar(String productoId) {
        ProductosEnMemoria.invalidar();
        FichaPublica.actualizarLuego(productoId);
    }

    private void requireAdminForTransferWorkflow() {
        if (!SessionManager.isAdmin()) {
            throw new SecurityException("Solo el administrador puede aprobar o rechazar transferencias");
        }
    }

    static final String RESOLVER_REQUIERE_CONEXION =
        "Aprobar o rechazar una transferencia requiere conexión con el servidor: "
        + "otra PC pudo haber movido el bien mientras tanto. Inténtalo al reconectar.";

    /** Whether a transfer can still be applied depends on where the bien is on
     *  the server right now (another PC may have moved it), so an offline copy
     *  can't decide it. The pending request stays queued until reconnection. */
    private static void requireConexionParaResolver() throws ValidationException {
        if (com.sibim.db.DatabaseConfig.isOfflineMode()) throw new ValidationException(RESOLVER_REQUIERE_CONEXION);
    }

    /** Deleting erases the movement from the history, so it's reserved for
     *  the administrator (e.g. a duplicate captured by mistake). Everyone
     *  else undoes a movement with {@link #revertirMovimiento}, which leaves
     *  both the original and its reversal on record. */
    public void eliminar(String movimientoId) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        if (!SessionManager.isAdmin())
            throw new ValidationException("Solo el administrador puede eliminar movimientos. "
                + "Para deshacer uno, ábrelo y usa \"Revertir\".");
        Optional<String> estado = movimientoRepo.findEstadoById(movimientoId);
        if (estado.isPresent() && Movimiento.ESTADO_PENDIENTE.equals(estado.get()))
            throw new ValidationException(
                "No se puede eliminar una transferencia pendiente. Primero apruébala o recházala.");

        String productoId = movimientoRepo.findProductoIdById(movimientoId).orElse("?");
        // Read before it goes: Auditoría used to keep only the ids ("bien 3f2a…").
        Movimiento antes = "?".equals(productoId) ? null : movimientoRepo.findByProducto(productoId).stream()
            .filter(x -> movimientoId.equals(x.getId())).findFirst().orElse(null);
        Producto bien = "?".equals(productoId) ? null : productoRepo.findById(productoId).orElse(null);
        if (antes != null && antes.getTipo() == TipoMovimiento.TRANSFERENCIA) exigirSinDocumento(productoId);
        try {
            movimientoRepo.deleteMovimientoAtomic(movimientoId);
            despuesDeCambiar("?".equals(productoId) ? null : productoId);
            auditRepo.log("movimiento", movimientoId, antes != null ? antes.getProductoNombre() : movimientoId, "eliminar",
                antes != null && antes.getTipo() != null
                    ? "Movimiento eliminado: " + detalleAuditoria(antes, bien != null ? bien.getCodigo() : null,
                        bien != null ? bien.getArea() : null)
                    : "Movimiento eliminado (bien " + productoId + ", estado " + estado.orElse("?") + ")");
            log.info("Movimiento eliminado [{}]", movimientoId);
        } catch (SQLException e) {
            if (e instanceof com.sibim.db.ReglaIncumplida) throw new ValidationException(e.getMessage());
            throw e;
        }
    }

    /** "Deshacer" in Transferir arrastrando: a transfer dropped on the wrong área is
     *  taken back as if it had not happened — the bien returns to its área with its
     *  old número if it is still free — and Auditoría keeps what was undone.
     *  @return the código the bien has now */
    public String deshacerTransferencia(Movimiento t) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        if (!SessionManager.isAdmin()) throw new ValidationException(SOLO_ADMIN_MOVIMIENTOS);
        if (t.getTipo() != TipoMovimiento.TRANSFERENCIA)
            throw new ValidationException("Solo se deshace una transferencia");
        exigirSinDocumento(t.getProductoId());
        try {
            movimientoRepo.deleteMovimientoAtomic(t.getId());
        } catch (SQLException e) {
            if (e instanceof com.sibim.db.ReglaIncumplida) throw new ValidationException(e.getMessage());
            throw e;
        }
        despuesDeCambiar(t.getProductoId());
        String ahora = productoRepo.findById(t.getProductoId()).map(Producto::getCodigo).orElse(t.getCodigoAnterior());
        String antes = t.getCodigoAnterior();
        auditRepo.log("movimiento", t.getId(), t.getProductoNombre(), "transferencia_deshecha",
            "Se deshizo la transferencia " + t.getAreaOrigen() + " → " + t.getAreaDestino()
                + " · número " + t.getCodigoNuevo() + " → " + ahora
                + (antes != null && !antes.equalsIgnoreCase(ahora) ? " (su número " + antes + " ya lo tenía otro bien)" : "")
                + (t.getMotivo() != null && !t.getMotivo().isBlank() ? " · Motivo de la transferencia: " + t.getMotivo().trim() : ""));
        log.info("Transferencia deshecha [{}] '{}' {} → {}", t.getId(), t.getProductoNombre(), t.getCodigoNuevo(), ahora);
        return ahora;
    }

    /** Taking a transfer back moves the bien too: not while a resguardo, préstamo or
     *  comodato in force in the área that received it names who has it. */
    private void exigirSinDocumento(String productoId) throws SQLException, ValidationException {
        Optional<String> documento = documentos.de(productoId);
        if (documento.isPresent())
            throw new ValidationException("No se puede regresar el bien a su área: " + documento.get());
    }

    /** Also raised by MovimientoRepository inside the locked transaction, as a
     *  {@link com.sibim.db.ReglaIncumplida}: the repository's business-rule
     *  rejections reach the user as a ValidationException with their own
     *  message, any other SQLException as a database error. */
    public static final String BIEN_DE_BAJA = "Este bien está dado de baja: ya no admite movimientos";
    /** What Auditoría shows of a movement, enough to tell what happened without
     *  opening it: a transfer used to read "1 uds (cantidad 1 → 1)", with no áreas,
     *  número or motivo. {@code codigo}/{@code area}: the bien's before the movement. */
    static String detalleAuditoria(Movimiento m, String codigo, String area) {
        String antes = m.getCodigoAnterior() != null ? m.getCodigoAnterior() : codigo;
        String cantidad = "cantidad " + m.getStockAnterior() + " → " + m.getStockNuevo();
        String texto = switch (m.getTipo()) {
            case TRANSFERENCIA -> "Transferencia: " + (m.getAreaOrigen() != null ? m.getAreaOrigen() : area)
                + " → " + m.getAreaDestino() + " · número " + antes
                + (m.getCodigoNuevo() != null && !m.getCodigoNuevo().equals(antes) ? " → " + m.getCodigoNuevo() : "");
            case ENTRADA -> "Agregar " + com.sibim.util.FormatUtils.plural(m.getCantidad(), "pieza", "piezas")
                + " a " + antes + " (" + cantidad + ")";
            case SALIDA  -> "Quitar " + com.sibim.util.FormatUtils.plural(m.getCantidad(), "pieza", "piezas")
                + " de " + antes + " (" + cantidad + ")";
            case AJUSTE  -> "Ajuste de " + antes + " (" + cantidad + ")";
        };
        if (m.getMotivo() != null && !m.getMotivo().isBlank()) texto += " · Motivo: " + m.getMotivo().trim();
        if (m.getReferencia() != null && !m.getReferencia().isBlank()) texto += " · Referencia: " + m.getReferencia().trim();
        return texto;
    }

    public static final String QUITAR_TODO =
        "No se pueden quitar todas las piezas: para sacar el bien del inventario dalo de baja (Bienes › Dar de baja)";
    public static final String SIN_EXISTENCIA = "No hay existencia que transferir: la cantidad del bien es 0";
    public static final String YA_ATENDIDA = "Esta transferencia ya fue aprobada o rechazada desde otro equipo";

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
