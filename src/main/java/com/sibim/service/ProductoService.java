package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoFiltro;
import com.sibim.config.AreaCodigos;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.PriceHistoryRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

    /** The most a bien can cost per piece (the dearest of the inventory: $4.3 million). */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("500000000");

    private final ProductoRepository productoRepo;
    private final AuditLogRepository auditRepo;
    private final PriceHistoryRepository priceHistoryRepo;
    private final ResguardoActivo resguardoActivo;
    private final Compromiso compromiso;

    /** Folio of the active resguardo covering a bien, if any. */
    @FunctionalInterface
    interface ResguardoActivo { Optional<String> folio(String productoId) throws SQLException; }

    /** Why a bien can't be written off right now (it is out on loan, in comodato
     *  or has a transfer waiting), or empty when nothing holds it. */
    @FunctionalInterface
    interface Compromiso { Optional<String> de(String productoId) throws SQLException; }

    /** Run after a baja: closes the área's solicitud de baja in wait, if the
     *  bien had one ({@link SolicitudService#bajaRegistrada}). Nothing in the
     *  constructors the tests use. */
    @FunctionalInterface
    interface AlDarDeBaja { void hecho(String productoId, String motivo, String numeroActa); }
    private AlDarDeBaja alDarDeBaja = (id, motivo, acta) -> { };

    public ProductoService() {
        this(new ProductoRepository(), new AuditLogRepository(), new PriceHistoryRepository(),
            id -> new com.sibim.repository.ResguardoRepository().findActivoByProductoId(id)
                .map(com.sibim.model.Resguardo::getNumero),
            ProductoService::compromisoEnBase);
        this.alDarDeBaja = SolicitudService::bajaRegistrada;
    }

    /** The signed document that says where a bien is or who holds it — a
     *  préstamo, a comodato or a resguardo still in force. While one stands the
     *  bien can't be transferred or written off: the document would go on
     *  saying something the inventory no longer does. */
    static Optional<String> documentoVigente(String id) throws SQLException {
        Optional<String> prestado = prestamoOComodato(id);
        if (prestado.isPresent()) return prestado;
        Optional<com.sibim.model.Resguardo> resguardo =
            new com.sibim.repository.ResguardoRepository().findActivoByProductoId(id);
        if (resguardo.isPresent())
            return Optional.of("está en el resguardo vigente " + resguardo.get().getNumero()
                + "; quítalo primero de ese resguardo (Resguardos › abre el resguardo › Quitar del resguardo)");
        return Optional.empty();
    }

    /** A préstamo or comodato in force: unlike a resguardo, SIBIM can't just take
     *  the bien out of it on the way (the bien isn't here to move). */
    static Optional<String> prestamoOComodato(String id) throws SQLException {
        if (new com.sibim.repository.PrestamoRepository().existsActivoForProducto(id))
            return Optional.of("tiene un préstamo activo; registra primero su devolución");
        if (new com.sibim.repository.ComodatoRepository().existsVigenteForProducto(id))
            return Optional.of("tiene un comodato vigente; concluye o rescinde primero el comodato");
        return Optional.empty();
    }

    private static Optional<String> compromisoEnBase(String id) throws SQLException {
        Optional<String> documento = documentoVigente(id);
        if (documento.isPresent()) return documento;
        boolean pendiente = new com.sibim.repository.MovimientoRepository().findPendientesTransferencias()
            .stream().anyMatch(m -> id.equals(m.getProductoId()));
        if (pendiente)
            return Optional.of("tiene una transferencia pendiente de aprobación; apruébala o recházala primero");
        return Optional.empty();
    }
    ProductoService(ProductoRepository productoRepo, AuditLogRepository auditRepo) {
        this(productoRepo, auditRepo, new PriceHistoryRepository());
    }
    ProductoService(ProductoRepository productoRepo, AuditLogRepository auditRepo,
                    PriceHistoryRepository priceHistoryRepo) {
        this(productoRepo, auditRepo, priceHistoryRepo, id -> Optional.empty());
    }
    ProductoService(ProductoRepository productoRepo, AuditLogRepository auditRepo,
                    PriceHistoryRepository priceHistoryRepo, ResguardoActivo resguardoActivo) {
        this(productoRepo, auditRepo, priceHistoryRepo, resguardoActivo, id -> Optional.empty());
    }
    ProductoService(ProductoRepository productoRepo, AuditLogRepository auditRepo,
                    PriceHistoryRepository priceHistoryRepo, ResguardoActivo resguardoActivo,
                    Compromiso compromiso) {
        this.productoRepo     = productoRepo;
        this.auditRepo        = auditRepo;
        this.priceHistoryRepo = priceHistoryRepo;
        this.resguardoActivo  = resguardoActivo;
        this.compromiso       = compromiso;
    }

    /** Every active bien in the caller's areas. Shared for a few seconds between
     *  screens and dialogs while connected — see {@link ProductosEnMemoria}. */
    public List<Producto> getAll() throws SQLException {
        return ProductosEnMemoria.obtener(productoRepo::findAll);
    }

    /** Drops the shared list so the next {@link #getAll()} asks the database
     *  (Actualizar / F5, or a change this class doesn't make itself). */
    public static void invalidarListaEnMemoria() {
        ProductosEnMemoria.invalidar();
    }

    // ── API principal con ProductoFiltro ─────────────────────────────────────

    public List<Producto> getPaginated(ProductoFiltro f, int limit, int offset) throws SQLException {
        return productoRepo.findPaginated(f, limit, offset);
    }

    public int countFiltrado(ProductoFiltro f) throws SQLException {
        return productoRepo.countFiltrado(f);
    }

    public List<Producto> getAllFiltrado(ProductoFiltro f) throws SQLException {
        return productoRepo.findAllFiltrado(f);
    }

    // ── Overloads legacy — delegan a los métodos con ProductoFiltro ───────────

    public List<Producto> getPaginated(String busqueda, String categoriaId, String area,
            String resguardante, EstadoProducto estado,
            boolean soloSinEtiquetar, int limit, int offset,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return getPaginated(new ProductoFiltro(busqueda, categoriaId, area, resguardante, estado, false, soloSinEtiquetar, desdeReg, hastaReg), limit, offset);
    }

    public int countFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, EstadoProducto estado,
            boolean soloSinEtiquetar, LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return countFiltrado(new ProductoFiltro(busqueda, categoriaId, area, resguardante, estado, false, soloSinEtiquetar, desdeReg, hastaReg));
    }

    /** The physical label is put on, and checked, by Patrimonio. */
    /** The área's answer for its bienes — phase 2 of its avance (Organigrama):
     *  "sí es nuestro" ({@code nota} null) or "no es nuestro / no lo tenemos"
     *  with why, which Patrimonio then looks into (Por hacer). An área answers
     *  for its own bienes; Patrimonio can answer for any (an área that replied
     *  on paper). */
    public void revisarComoArea(List<Producto> bienes, String nota) throws SQLException, ValidationException {
        if (bienes.isEmpty()) return;
        exigirPropios(bienes, "revisar");
        if (com.sibim.db.DatabaseConfig.isOfflineMode())
            throw new ValidationException("La revisión necesita conexión: Patrimonio tiene que verla al momento");
        String n = nota == null || nota.isBlank() ? null : nota.trim();
        if (n != null && n.length() > 300) throw new ValidationException("La nota no puede exceder 300 caracteres");
        String por = SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getNombre() : null;
        productoRepo.marcarRevision(bienes.stream().map(Producto::getId).toList(), java.time.LocalDateTime.now(), por, n);
        ProductosEnMemoria.invalidar();
        String area = bienes.get(0).getArea();
        auditRepo.log("producto", null, area, "revision_area", n == null
            ? FormatUtils.plural(bienes.size(), "bien revisado: sí es del área", "bienes revisados: sí son del área") + " (" + codigos(bienes) + ")"
            : FormatUtils.plural(bienes.size(), "bien que el área dice que no es suyo", "bienes que el área dice que no son suyos")
                + ": «" + n + "» (" + codigos(bienes) + ")");
    }

    /** Takes back the área's review, so it is reviewed again (Patrimonio, once it looked into an aviso). */
    public void quitarRevision(List<Producto> bienes) throws SQLException, ValidationException {
        quitarRevision(bienes, null);
    }

    /** Sends these bienes back to the área's «Por revisar» with what Patrimonio tells it
     *  ("es suyo, está en la bodega"), shown to the área next to the bien until it answers
     *  (Producto#getMensajePatrimonio); {@code mensaje} null or blank for none. */
    public void quitarRevision(List<Producto> bienes, String mensaje) throws SQLException, ValidationException {
        if (bienes.isEmpty()) return;
        exigirPropios(bienes, "corregir la revisión de");
        String m = mensaje == null || mensaje.isBlank() ? null : mensaje.trim();
        if (m != null && m.length() > 300) throw new ValidationException("El mensaje no puede exceder 300 caracteres");
        productoRepo.marcarRevision(bienes.stream().map(Producto::getId).toList(), null, null, m);
        ProductosEnMemoria.invalidar();
        auditRepo.log("producto", null, bienes.get(0).getArea(), "revision_area",
            "Se quitó la revisión del área de " + FormatUtils.plural(bienes.size(), "bien", "bienes") + " (" + codigos(bienes) + ")"
                + (m != null ? "; mensaje al área: «" + m + "»" : ""));
    }

    /** Deshacer of any change to the área's review: puts back each bien's answer
     *  (or Patrimonio's message) exactly as it was in {@code antes}, the bienes as
     *  they were read before the change. */
    public void restaurarRevision(List<Producto> antes) throws SQLException, ValidationException {
        if (antes.isEmpty()) return;
        exigirPropios(antes, "corregir la revisión de");
        // One update for each state they were in: undoing 200 «Sí es» is one round-trip, not 200.
        record Estado(java.time.LocalDateTime en, String por, String nota) {}
        Map<Estado, List<String>> porEstado = new LinkedHashMap<>();
        for (Producto p : antes)
            porEstado.computeIfAbsent(new Estado(p.getRevisadoAreaEn(), p.getRevisadoAreaPor(), p.getRevisionAreaNota()),
                k -> new ArrayList<>()).add(p.getId());
        for (var e : porEstado.entrySet())
            productoRepo.marcarRevision(e.getValue(), e.getKey().en(), e.getKey().por(), e.getKey().nota());
        ProductosEnMemoria.invalidar();
        auditRepo.log("producto", null, antes.get(0).getArea(), "revision_area",
            "Se deshizo el cambio en la revisión del área de " + FormatUtils.plural(antes.size(), "bien", "bienes")
                + " (" + codigos(antes) + ")");
    }

    /** Phase 3: the área says the label is stuck on these bienes (or, with false, that it isn't). */
    public void confirmarEtiquetas(List<Producto> bienes, boolean pegada) throws SQLException, ValidationException {
        if (bienes.isEmpty()) return;
        exigirPropios(bienes, "confirmar las etiquetas de");
        productoRepo.marcarEtiquetado(bienes.stream().map(Producto::getId).toList(), pegada);
        ProductosEnMemoria.invalidar();
        auditRepo.log("producto", null, bienes.get(0).getArea(), "actualizar",
            (pegada ? "Etiqueta pegada en " : "Sin etiqueta: ") + FormatUtils.plural(bienes.size(), "bien", "bienes")
                + " (" + codigos(bienes) + ")");
    }

    private static void exigirPropios(List<Producto> bienes, String que) throws ValidationException {
        for (Producto p : bienes)
            if (!SessionManager.isAreaAccessible(p.getArea()))
                throw new ValidationException("Solo puedes " + que + " los bienes de tu área");
    }

    /** "TICS/01, TICS/02 y 3 más" for the Auditoría. */
    private static String codigos(List<Producto> bienes) {
        List<String> c = bienes.stream().map(Producto::getCodigo).filter(java.util.Objects::nonNull).toList();
        return c.size() <= 6 ? String.join(", ", c) : String.join(", ", c.subList(0, 6)) + " y " + (c.size() - 6) + " más";
    }

    public void marcarEtiquetado(List<String> ids, boolean valor) throws SQLException {
        if (!com.sibim.session.Permisos.editaFichaCompleta())
            throw new SecurityException("Solo Patrimonio (administrador) marca los bienes como etiquetados.");
        ProductosEnMemoria.invalidar();
        productoRepo.marcarEtiquetado(ids, valor);
    }

    public List<Producto> getAllFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, EstadoProducto estado) throws SQLException {
        return getAllFiltrado(new ProductoFiltro(busqueda, categoriaId, area, resguardante, estado, false, false, null, null));
    }

    public List<String> getResguardantes() throws SQLException {
        return productoRepo.findDistinctResguardantes();
    }

    // Suggestions for the Marca/Modelo/Proveedor/Ubicación autocomplete fields.
    public List<String> getMarcas()       { return productoRepo.findDistinctMarcas(); }
    public List<String> getModelos()      { return productoRepo.findDistinctModelos(); }
    public List<String> getProveedores()  { return productoRepo.findDistinctProveedores(); }
    public List<String> getUbicaciones()  { return productoRepo.findDistinctUbicaciones(); }
    public List<String> getColores()      { return productoRepo.findDistinctColores(); }
    public List<String> getTiposBien()    { return productoRepo.findDistinctTiposBien(); }

    public boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        return productoRepo.existsByCodigo(codigo, excludeId);
    }

    /** Taken by an active bien, or used before by another one (números are not reused). */
    public boolean codigoNoDisponible(String codigo, String excludeId) throws SQLException {
        return productoRepo.existsByCodigo(codigo, excludeId) || productoRepo.codigoUsadoPorOtro(codigo, excludeId);
    }

    public long countNuevosEnAnio(int anio) throws SQLException {
        return productoRepo.countNuevosEnAnio(anio);
    }

    public LinkedHashMap<String, Long> countByArea(int limit, LocalDate desde, LocalDate hasta) throws SQLException {
        return productoRepo.countByArea(limit, desde, hasta);
    }

    public List<ProductoRepository.CategoriaValor> getValorPorCategoria(int limit, LocalDate desde, LocalDate hasta) throws SQLException {
        return productoRepo.getValorPorCategoria(limit, desde, hasta);
    }

    public ProductoRepository.InventarioStats getStats() throws SQLException {
        return productoRepo.findStats();
    }

    /** Exact código lookup within the caller's areas (barcode/QR scans). */
    public Optional<Producto> findByCodigo(String codigo) throws SQLException {
        return productoRepo.findByCodigo(codigo);
    }

    /** The bienes whose old inventory number is {@code leido} — the label still stuck on them. */
    public List<Producto> findByInventarioAnterior(String leido) throws SQLException {
        return productoRepo.findByInventarioAnterior(leido);
    }

    public Optional<Producto> findById(String id) throws SQLException {
        return productoRepo.findById(id);
    }

    public List<Producto> getVencidosProximos(int dias) throws SQLException {
        return productoRepo.findVencidosProximos(dias);
    }

    public List<Producto> getProximasRevisiones(int dias) throws SQLException {
        return productoRepo.findProximasRevisiones(dias);
    }

    /** Ids given to a new bien whose insert then failed. The form reopens with
     *  the same object, id included, and the next "Guardar" took it for the
     *  edit of a bien that was never stored: "no existe o no tienes acceso a
     *  él" on every retry, until the capture was cancelled and typed again.
     *  The id is kept rather than cleared: had the insert reached the server
     *  after all, the retry updates that row instead of registering it twice. */
    private static final java.util.Set<String> ALTAS_SIN_CONFIRMAR =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Producto save(Producto recibido) throws SQLException, ValidationException {
        Producto p = recibido;
        boolean isNew = p.getId() == null
            || (ALTAS_SIN_CONFIRMAR.contains(p.getId()) && productoRepo.findById(p.getId()).isEmpty());
        // Secretarías and direcciones keep the data of their bienes current;
        // registering a new bien is Patrimonio's (admin).
        if (isNew && !SessionManager.isAdmin())
            throw new ValidationException("Solo el administrador (Patrimonio) puede dar de alta bienes. "
                + "Tu área puede actualizar los datos de los bienes que tiene asignados.");
        // El código se asigna por área (ver AreaCodigos) al dar de alta un bien
        // nuevo; al editar uno existente el código sigue siendo editable a mano
        // (útil para corregir datos heredados que no siguen este formato).
        if (isNew && p.getArea() != null && AreaCodigos.tienePrefijo(p.getArea()))
            p.setCodigo(asignarCodigo(p.getArea()));
        // Only the name, without "LIC.", "ING."…: from 5-nov-2026.
        if (isNew) p.setResguardante(com.sibim.util.NombrePersona.sinTitulos(p.getResguardante()));

        // A municipal bien has no sale price. The column stays (older clients,
        // imports and reports still read it) but always mirrors the purchase
        // price, which is what every valuation uses (Producto#getValorTotal).
        p.setPrecioVenta(p.getPrecioCompra());

        BigDecimal prevCompra = null;
        Producto antes = null;
        if (!isNew) {
            // findById is already scoped to the caller's áreas, so a bien from
            // another área comes back empty — checking the área the object
            // claims (validate() below) is not enough, it could be anything.
            Producto actual = productoRepo.findById(p.getId()).orElseThrow(() ->
                new ValidationException("El bien no existe o no tienes acceso a él"));
            if (p.getActualizadoEn() != null && actual.getActualizadoEn() != null
                    && actual.getActualizadoEn().isAfter(p.getActualizadoEn()))
                throw new ModificadoPorOtroException();
            // Stock and área only change through movimientos (Entrada/Salida/
            // Ajuste/Transferencia): keep whatever the database has now, not
            // what the form captured when it was opened.
            p.setStockActual(actual.getStockActual());
            p.setArea(actual.getArea());
            prevCompra = actual.getPrecioCompra();
            antes = actual;
            // Secretarías and direcciones update the descriptive data of their
            // bienes; the código patrimonial and the accounting values are
            // Patrimonio's (admin), so theirs stay as the database has them.
            if (!com.sibim.session.Permisos.editaFichaCompleta()) p = soloLoQueEditaUnArea(actual, p);
            // A código corrected by hand stays in the nomenclature of the
            // bien's área (PREFIJO/NN); one left as it was isn't checked, so a
            // legacy código never blocks editing the rest of the bien.
            if (!java.util.Objects.equals(sinBlancos(p.getCodigo()), sinBlancos(actual.getCodigo()))
                    && AreaCodigos.tienePrefijo(actual.getArea())) {
                String prefijo = AreaCodigos.prefijo(actual.getArea());
                p.setCodigo(AreaCodigos.normalizar(actual.getArea(), p.getCodigo()).orElseThrow(() ->
                    new ValidationException("El código de un bien de " + actual.getArea() + " es " + prefijo
                        + "/ y un número (por ejemplo " + prefijo + "/07)")));
                // 5-nov-2026: a número that another bien had is not given again, not even by hand.
                if (productoRepo.codigoUsadoPorOtro(p.getCodigo(), p.getId()))
                    throw new ValidationException("El número " + p.getCodigo() + " ya lo usó otro bien y los números no se repiten; "
                        + "el siguiente de " + actual.getArea() + " es " + asignarCodigo(actual.getArea()));
            }
            // A name typed now goes without its title ("LIC. ANA" → "ANA"); one left
            // as it was isn't touched, so it keeps matching its resguardo.
            if (!java.util.Objects.equals(sinBlancos(p.getResguardante()), sinBlancos(actual.getResguardante())))
                p.setResguardante(com.sibim.util.NombrePersona.sinTitulos(p.getResguardante()));
            // A signed resguardo says who holds the bien (ResguardoRepository
            // keeps products.resguardante in step with it); an edit — single
            // or in bulk — can't contradict the document.
            if (!java.util.Objects.equals(sinBlancos(p.getResguardante()), sinBlancos(actual.getResguardante()))) {
                if (com.sibim.db.DatabaseConfig.isOfflineMode())
                    throw new ValidationException("Sin conexión no se puede comprobar si un resguardo firmado "
                        + "define al resguardante; cámbialo al reconectar");
                Optional<String> folio = resguardoActivo.folio(p.getId());
                if (folio.isPresent())
                    throw new ValidationException("El resguardante lo define el resguardo " + folio.get()
                        + "; para cambiarlo cancélalo o genera uno nuevo en Resguardos");
            }
            BigDecimal sigue = residualQueSigue(actual, p);
            if (sigue != null) p.setValorResidual(sigue);
        }
        validate(p);
        String queCambio = antes != null ? cambios(antes, p) : "";
        if (antes != null) queCambio = conSusFotos(queCambio, p);

        boolean codigoPorArea = isNew && AreaCodigos.tienePrefijo(p.getArea());
        Producto saved;
        for (int intento = 1; ; intento++) {
            try {
                saved = productoRepo.save(p);
                break;
            } catch (SQLException e) {
                // Another PC registered a bien with this código between the
                // moment it was picked and the insert (unique index on active
                // códigos): take the next free one and try again.
                if (!"23505".equals(e.getSQLState()) || !isNew) {
                    if (isNew && p.getId() != null) ALTAS_SIN_CONFIRMAR.add(p.getId());
                    throw e;
                }
                if (!codigoPorArea || intento == 3) {
                    p.setId(null);
                    throw new ValidationException("Otro equipo acaba de usar el código " + p.getCodigo()
                        + "; vuelve a guardar para que se asigne el siguiente");
                }
                p.setCodigo(asignarCodigo(p.getArea()));
            }
        }
        // After the write: dropped before it, a screen reading in between put
        // the old row (and its old photo) back in the shared list for 30 s.
        ProductosEnMemoria.invalidar();
        if (saved.getId() != null) ALTAS_SIN_CONFIRMAR.remove(saved.getId());
        log.info("Bien {} [{}] '{}'", isNew ? "registrado" : "actualizado", saved.getId(), saved.getNombre());
        auditRepo.log("producto", saved.getId(), saved.getNombre(),
            isNew ? "crear" : "actualizar",
            (isNew ? "Bien registrado" : "Datos del bien actualizados")
                + (saved.getCodigo() != null ? " · número " + saved.getCodigo() : "")
                + (saved.getArea() != null ? " · " + saved.getArea() : "")
                + (queCambio.isEmpty() ? "" : " · Cambió: " + queCambio));

        if (!isNew) {
            Usuario u = SessionManager.getCurrentUser();
            String userId   = u != null ? u.getId()     : null;
            String userName = u != null ? u.getNombre() : "Sistema";
            // PriceHistoryRepository keeps the per-bien detail (shown in ProductoDetailDialog),
            // but the central Auditoría screen only reads AuditLogRepository, and the generic
            // "actualizar" entry above doesn't say a price moved — without this, "quién cambió
            // el precio de X" is unanswerable from that screen.
            if (priceChanged(prevCompra, p.getPrecioCompra())) {
                priceHistoryRepo.save(saved.getId(), "precio_compra", prevCompra, p.getPrecioCompra(), userId, userName);
                auditRepo.log("producto", saved.getId(), saved.getNombre(), "cambio_precio",
                    "Precio de compra: " + FormatUtils.formatCurrency(prevCompra)
                        + " → " + FormatUtils.formatCurrency(p.getPrecioCompra()));
            }
        }

        return saved;
    }

    /** A valor residual that was exactly the 15 % of the old costo follows a new costo, unless
     *  this same change sets it (19-nov-2026: 285 of 296 are the 15 %, and «Sumarle el IVA» left
     *  3 at 12.9 %); in «Cambiar datos», «La misma factura» and the form alike.
     *  @return the residual it takes, or null to leave it */
    static BigDecimal residualQueSigue(Producto antes, Producto despues) {
        if (antes == null || despues == null || !priceChanged(antes.getPrecioCompra(), despues.getPrecioCompra())) return null;
        BigDecimal era = antes.getValorResidual();
        if (!com.sibim.util.ProductoUtils.esResidualSugerido(antes.getPrecioCompra(), era)) return null;
        BigDecimal ahora = despues.getValorResidual();
        if (ahora == null || ahora.compareTo(era) != 0) return null;   // this change sets it
        BigDecimal nuevo = com.sibim.util.ProductoUtils.residualSugerido(despues.getPrecioCompra());
        return nuevo != null ? nuevo : BigDecimal.ZERO;
    }

    /** An área keeps four things of a bien current: where it is, its
     *  description, its physical condition and its photos. Everything else
     *  stays as {@code actual} has it, whatever {@code cambios} says — on save,
     *  and when a change made offline is applied over a newer server copy
     *  (SyncService, "usar mi versión"). */
    public static Producto soloLoQueEditaUnArea(Producto actual, Producto cambios) {
        Producto r = actual.copia();
        r.setUbicacion(cambios.getUbicacion());
        r.setDescripcion(cambios.getDescripcion());
        r.setEstadoFisico(cambios.getEstadoFisico());
        r.setFotoUrl(cambios.getFotoUrl());
        r.setFotosUrls(cambios.getFotosUrls());
        r.setActualizadoEn(cambios.getActualizadoEn());
        return r;
    }

    /** El número siguiente al más alto que se haya usado con el prefijo de
     *  {@code area} (AreaCodigos#siguienteCodigo): el de un bien dado de baja,
     *  transferido o borrado no se vuelve a dar. */
    private String asignarCodigo(String area) throws SQLException {
        return productoRepo.siguienteCodigo(area);
    }

    /** El código que recibiría hoy un bien nuevo de {@code area}: el formulario
     *  lo enseña antes de guardar (al guardar se vuelve a calcular). */
    public String siguienteCodigo(String area) throws SQLException {
        return asignarCodigo(area);
    }

    /** Every código ever used, for the transfer board's preview of the new números. */
    public List<String> codigosUsados() throws SQLException {
        return productoRepo.codigosUsados();
    }

    private static String sinBlancos(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** What an edit changed, for the Auditoría: "vida útil (sin dato → 5), foto".
     *  Short data says from what to what; long text (descripción, notas) and files
     *  just say that they changed. The price has its own entry (cambio_precio);
     *  cantidad and área only change through movimientos. Empty when nothing did. */
    static String cambios(Producto antes, Producto despues) {
        List<String> c = new ArrayList<>();
        valor(c, "nombre", antes.getNombre(), despues.getNombre());
        valor(c, "número", antes.getCodigo(), despues.getCodigo());
        if (!java.util.Objects.equals(sinBlancos(antes.getDescripcion()), sinBlancos(despues.getDescripcion())))
            c.add("descripción");
        if (!java.util.Objects.equals(antes.getCategoriaId(), despues.getCategoriaId()))
            valor(c, "categoría", Optional.ofNullable(antes.getCategoriaNombre()).orElse("otra"),
                Optional.ofNullable(despues.getCategoriaNombre()).orElse("otra"));
        valor(c, "unidad", antes.getUnidad() != null ? antes.getUnidad().name() : null,
            despues.getUnidad() != null ? despues.getUnidad().name() : null);
        valor(c, "proveedor", antes.getProveedor(), despues.getProveedor());
        valor(c, "marca", antes.getMarca(), despues.getMarca());
        valor(c, "modelo", antes.getModelo(), despues.getModelo());
        valor(c, "serie", antes.getNumeroSerie(), despues.getNumeroSerie());
        valor(c, "ubicación", antes.getUbicacion(), despues.getUbicacion());
        valor(c, "resguardante", antes.getResguardante(), despues.getResguardante());
        valor(c, "estado físico", antes.getEstadoFisico(), despues.getEstadoFisico());
        valor(c, "factura", antes.getNumeroFactura(), despues.getNumeroFactura());
        valor(c, "folio fiscal", antes.getFolioFiscal(), despues.getFolioFiscal());
        valor(c, "fecha de adquisición", fecha(antes.getFechaAdquisicion()), fecha(despues.getFechaAdquisicion()));
        valor(c, "vida útil", antes.getVidaUtilAnios() != null ? String.valueOf(antes.getVidaUtilAnios()) : null,
            despues.getVidaUtilAnios() != null ? String.valueOf(despues.getVidaUtilAnios()) : null);
        BigDecimal resA = Optional.ofNullable(antes.getValorResidual()).orElse(BigDecimal.ZERO);
        BigDecimal resB = Optional.ofNullable(despues.getValorResidual()).orElse(BigDecimal.ZERO);
        if (resA.compareTo(resB) != 0)
            valor(c, "valor residual", FormatUtils.formatCurrency(resA), FormatUtils.formatCurrency(resB));
        valor(c, "garantía", fecha(antes.getFechaVencimiento()), fecha(despues.getFechaVencimiento()));
        valor(c, "próxima revisión", fecha(antes.getProximaRevision()), fecha(despues.getProximaRevision()));
        if (antes.isEtiquetado() != despues.isEtiquetado())
            c.add(despues.isEtiquetado() ? "marcado como etiquetado" : "marcado sin etiqueta");
        valor(c, "clave armonizada", antes.getClaveArmonizada(), despues.getClaveArmonizada());
        valor(c, "color", antes.getColor(), despues.getColor());
        valor(c, "tipo de bien", antes.getTipoBien(), despues.getTipoBien());
        valor(c, "no. de motor", antes.getNoMotor(), despues.getNoMotor());
        valor(c, "tarjeta de circulación", antes.getNoTarjetaCirculacion(), despues.getNoTarjetaCirculacion());
        valor(c, "póliza de seguro", antes.getNoPolizaSeguro(), despues.getNoPolizaSeguro());
        if (!java.util.Objects.equals(sinBlancos(antes.getNotasMantenimiento()), sinBlancos(despues.getNotasMantenimiento())))
            c.add("notas de mantenimiento");
        if (!java.util.Objects.equals(sinBlancos(antes.getFotoUrl()), sinBlancos(despues.getFotoUrl())))
            c.add(sinBlancos(antes.getFotoUrl()) == null ? "foto agregada" : sinBlancos(despues.getFotoUrl()) == null ? "foto quitada" : "foto");
        if (!java.util.Objects.equals(sinBlancos(antes.getFacturaUrl()), sinBlancos(despues.getFacturaUrl())))
            c.add("archivo de factura");
        return String.join(", ", c);
    }

    /** The photos beside the principal one aren't in the bien's row: adding one while the
     *  principal stayed read «Se guardó sin cambios» in the Auditoría (26-nov-2026: SSP/21 and
     *  SSP/51, each with a new photo). Only for a form that carries its photos; offline, as before. */
    private String conSusFotos(String queCambio, Producto p) {
        if (p.getFotosUrls() == null || p.getFotosUrls().isEmpty() || p.getId() == null
                || com.sibim.db.DatabaseConfig.isOfflineMode()) return queCambio;
        try {
            String fotos = cambioDeFotos(productoRepo.findFotos(p.getId()), p.getFotosUrls(), queCambio);
            if (fotos.isEmpty()) return queCambio;
            // «fotos (1 → 2)» already says it: not also «foto» for the new principal.
            List<String> partes = new ArrayList<>(queCambio.isEmpty() ? List.of() : List.of(queCambio.split(", ")));
            if (!fotos.equals("foto agregada")) partes.remove("foto");
            partes.add(fotos);
            return String.join(", ", partes);
        } catch (SQLException e) {
            log.debug("Sin las fotos de antes de {}: {}", p.getId(), e.getMessage());
            return queCambio;
        }
    }

    /** «fotos (1 → 2)» when there are more or fewer, «una foto por otra» when as many but not the
     *  same ones; nothing when what changed is only which one
     *  is the principal, or a photo added to or taken from a bien without photos (that already
     *  says «foto», «foto agregada» or «foto quitada»). */
    static String cambioDeFotos(List<String> eran, List<String> son, String yaDice) {
        List<String> a = eran == null ? List.of() : eran, b = son == null ? List.of() : son;
        if (new java.util.HashSet<>(a).equals(new java.util.HashSet<>(b)) && a.size() == b.size()) return "";
        if (a.isEmpty() || b.isEmpty()) return yaDice.contains("foto") ? "" : a.isEmpty() ? "foto agregada" : "foto quitada";
        return a.size() == b.size() ? "una foto por otra" : "fotos (" + a.size() + " → " + b.size() + ")";
    }

    private static void valor(List<String> cambios, String campo, String antes, String despues) {
        String a = sinBlancos(antes), b = sinBlancos(despues);
        if (java.util.Objects.equals(a, b)) return;
        cambios.add(campo + " (" + corto(a) + " → " + corto(b) + ")");
    }

    private static String corto(String s) {
        if (s == null) return "sin dato";
        return s.length() > 40 ? s.substring(0, 39) + "…" : s;
    }

    private static String fecha(LocalDate d) { return d != null ? FormatUtils.formatDate(d) : null; }

    private static boolean priceChanged(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    public void delete(String id) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        if (!SessionManager.isAdmin())
            throw new ValidationException("Solo el administrador puede eliminar bienes");
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) return;
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa área");
        try {
            productoRepo.delete(id);
            ProductosEnMemoria.invalidar();
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
    public static final String SOLO_ADMIN_BAJA =
        "Solo el administrador puede dar de baja un bien. Genera la solicitud de baja, fírmala "
        + "y entrégala a Patrimonio para que la registre.";

    public void darDeBaja(String id, String motivo) throws SQLException, ValidationException {
        darDeBaja(id, motivo, null, null, null, null);
    }

    /** Full baja patrimonial with committee dictamen data.
     *  {@code tipoDestino}, {@code dictamen}, {@code numeroActa} and {@code fechaDictamen}
     *  are all optional (null means not filled). */
    public void darDeBaja(String id, String motivo,
                          String tipoDestino, String dictamen,
                          String numeroActa, LocalDate fechaDictamen)
            throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        // Patrimonio (the administrator) registers every baja, against the
        // signed solicitud de baja the área hands in.
        if (!SessionManager.isAdmin())
            throw new ValidationException(SOLO_ADMIN_BAJA);
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa área");
        if (motivo == null || motivo.isBlank())
            throw new ValidationException("El motivo de la baja es obligatorio");
        if (p.isDadoDeBaja())
            throw new ValidationException("Este bien ya está dado de baja");
        // Préstamos and comodatos live only on the server: offline there is no
        // way to know whether this bien is lent out, and the baja would be
        // replayed to the server on reconnection without that check.
        if (com.sibim.db.DatabaseConfig.isOfflineMode())
            throw new ValidationException("La baja requiere conexión: sin ella no se puede comprobar "
                + "si el bien tiene un préstamo o comodato activo");
        Optional<String> ocupado = compromiso.de(id);
        if (ocupado.isPresent())
            throw new ValidationException("No se puede dar de baja: el bien " + ocupado.get());
        productoRepo.darDeBaja(id, motivo.trim(), tipoDestino, dictamen, numeroActa, fechaDictamen);
        ProductosEnMemoria.invalidar();
        FichaPublica.actualizarLuego(id);   // the label must stop saying the bien is active
        log.info("Baja patrimonial bien [{}] '{}' — motivo: {} destino: {}",
            id, p.getNombre(), motivo.trim(), tipoDestino);
        String auditDetail = "Motivo: " + motivo.trim()
            + (tipoDestino != null ? " | Destino: " + tipoDestino : "")
            + (numeroActa  != null ? " | Acta: " + numeroActa     : "");
        auditRepo.log("producto", id, p.getNombre(), "baja", auditDetail);
        alDarDeBaja.hecho(id, motivo.trim(), numeroActa);
    }

    /** Baja definitiva: the destino final of these bienes de baja was carried
     *  out (destroyed, donated, sold…) with {@code documento}. They can no
     *  longer be reactivated. Patrimonio only.
     *  @return how many changed (the ones already definitivas are skipped) */
    public int bajaDefinitiva(List<Producto> bienes, LocalDate fecha, String documento, String tipoDestino)
            throws SQLException, ValidationException {
        if (!SessionManager.isAdmin()) throw new ValidationException("Solo Patrimonio (administrador) registra la baja definitiva");
        if (documento == null || documento.isBlank())
            throw new ValidationException("Anota el documento que autoriza la baja definitiva (acta de destrucción, contrato de donación, acuerdo de cabildo…)");
        if (documento.trim().length() > 300) throw new ValidationException("El documento no puede exceder 300 caracteres");
        if (fecha == null) throw new ValidationException("La fecha de la baja definitiva es obligatoria");
        if (fecha.isAfter(LocalDate.now())) throw new ValidationException("La fecha de la baja definitiva no puede ser futura");
        for (Producto p : bienes) {
            if (!p.isDadoDeBaja()) throw new ValidationException("«" + p.getNombre() + "» no está dado de baja");
            if (p.getFechaBaja() != null && fecha.isBefore(p.getFechaBaja()))
                throw new ValidationException("«" + p.getNombre() + "» se dio de baja el "
                    + p.getFechaBaja().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ": la baja definitiva no puede ser antes");
        }
        List<Producto> pendientes = bienes.stream().filter(p -> !p.isBajaDefinitiva()).toList();
        if (pendientes.isEmpty()) return 0;
        int n = productoRepo.marcarBajaDefinitiva(pendientes.stream().map(Producto::getId).toList(),
            fecha, documento.trim(), tipoDestino);
        ProductosEnMemoria.invalidar();
        for (Producto p : pendientes)
            auditRepo.log("producto", p.getId(), p.getNombre(), "baja_definitiva",
                "Baja definitiva " + fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " · " + documento.trim() + (tipoDestino != null ? " · Destino: " + tipoDestino : ""));
        return n;
    }

    /** Takes back a baja definitiva registered by mistake (the bien stays de baja). */
    public void quitarBajaDefinitiva(List<Producto> bienes) throws SQLException, ValidationException {
        if (!SessionManager.isAdmin()) throw new ValidationException("Solo Patrimonio (administrador) corrige la baja definitiva");
        productoRepo.marcarBajaDefinitiva(bienes.stream().map(Producto::getId).toList(), null, null, null);
        ProductosEnMemoria.invalidar();
        for (Producto p : bienes)
            auditRepo.log("producto", p.getId(), p.getNombre(), "actualizar", "Se quitó la baja definitiva (sigue dado de baja)");
    }

    /** Reverses a baja patrimonial, restoring the bien to active inventory.
     *  Also reassigns its código according to its área — the número it held
     *  before the baja may have since been claimed by a different bien in
     *  that área (see AreaCodigos, asignarCodigo). */
    public void reactivar(String id) throws SQLException, ValidationException {
        ProductosEnMemoria.invalidar();
        if (!SessionManager.isAdmin())
            throw new ValidationException("Solo el administrador puede reactivar un bien dado de baja");
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa área");
        if (p.isBajaDefinitiva())
            throw new ValidationException("«" + p.getNombre() + "» tiene baja definitiva (" + p.getBajaDefinitivaDocumento()
                + "): ya no se puede reactivar");
        if (AreaCodigos.tienePrefijo(p.getArea())) {
            productoRepo.reactivarConCodigo(id, productoRepo.codigoAlReactivar(p));
        } else {
            productoRepo.reactivar(id);
        }
        ProductosEnMemoria.invalidar();
        FichaPublica.actualizarLuego(id);
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

    public List<String> getFotosByProductoId(String id) throws SQLException {
        return productoRepo.findFotos(id);
    }

    public void saveFotos(String productoId, List<String> fotos) throws SQLException {
        // findById is scoped to the caller's áreas: the photos of a bien of
        // another área can't be replaced by knowing its id.
        if (productoRepo.findById(productoId).isEmpty())
            throw new SecurityException("El bien no existe o no tienes acceso a él");
        productoRepo.saveFotos(productoId, fotos);
        ProductosEnMemoria.invalidar();
    }

    private void validate(Producto p) throws ValidationException, SQLException {
        if (p.getNombre() == null || p.getNombre().isBlank())
            throw new ValidationException("El nombre es obligatorio");
        if (p.getNombre().length() > 200)
            throw new ValidationException("El nombre no puede exceder 200 caracteres");
        if (p.getCodigo() == null || p.getCodigo().isBlank())
            throw new ValidationException("El código es obligatorio");
        if (p.getCodigo().length() > 50)
            throw new ValidationException("El código no puede exceder 50 caracteres");
        if (p.getDescripcion() != null && p.getDescripcion().length() > 1000)
            throw new ValidationException("La descripción no puede exceder 1000 caracteres");
        if (p.getProveedor() != null && p.getProveedor().length() > 200)
            throw new ValidationException("El proveedor no puede exceder 200 caracteres");
        if (p.getUbicacion() != null && p.getUbicacion().length() > 200)
            throw new ValidationException("La ubicación no puede exceder 200 caracteres");
        if (p.getResguardante() != null && p.getResguardante().length() > 200)
            throw new ValidationException("El resguardante no puede exceder 200 caracteres");
        if (p.getFolioFiscal() != null && p.getFolioFiscal().length() > 60)
            throw new ValidationException("El folio fiscal no puede exceder 60 caracteres");
        // A camión recolector got $2,880,280,000.00 for $2,880,280.00 on 28-oct-2026:
        // nothing of the municipio costs that much a piece, so no path can save it.
        if (p.getPrecioCompra() != null && p.getPrecioCompra().compareTo(PRECIO_MAXIMO) > 0)
            throw new ValidationException("El costo de adquisición no puede pasar de "
                + FormatUtils.formatCurrency(PRECIO_MAXIMO) + " por pieza: revisa que no le sobren ceros");
        if (productoRepo.existsByCodigo(p.getCodigo(), p.getId()))
            throw new ValidationException("Ya existe un bien con ese código");
        if (p.getCategoriaId() == null || p.getCategoriaId().isBlank())
            throw new ValidationException("La categoría es obligatoria");
        if (p.getArea() == null || p.getArea().isBlank())
            throw new ValidationException("El área es obligatoria");
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa área");
        if (p.getPrecioCompra() == null || p.getPrecioCompra().signum() < 0)
            throw new ValidationException("El precio de compra no puede ser negativo");
        if (p.getPrecioVenta() == null || p.getPrecioVenta().signum() < 0)
            throw new ValidationException("El precio de venta no puede ser negativo");
        if (p.getStockActual() < 0)
            throw new ValidationException("La cantidad no puede ser negativa");
        if (p.getStockMinimo() < 0)
            throw new ValidationException("El mínimo de existencias no puede ser negativo");
        if (p.getStockMaximo() < 0)
            throw new ValidationException("El máximo de existencias no puede ser negativo");
        if (p.getStockMinimo() > p.getStockMaximo())
            throw new ValidationException("El mínimo de existencias no puede ser mayor al máximo");
        // Validaciones patrimoniales de depreciación
        if (p.getVidaUtilAnios() != null && p.getVidaUtilAnios() <= 0)
            throw new ValidationException("La vida útil debe ser mayor a cero");
        if (p.getValorResidual() != null && p.getPrecioCompra() != null
                && p.getValorResidual().compareTo(p.getPrecioCompra()) > 0)
            throw new ValidationException("El valor residual no puede ser mayor al precio de compra");
        if (p.getFechaAdquisicion() != null && p.getFechaAdquisicion().isAfter(LocalDate.now()))
            throw new ValidationException("La fecha de adquisición no puede ser en el futuro");
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }

    /** The bien changed in the database after the form was opened — saving
     *  would silently undo someone else's edit, so the caller should reload
     *  instead of retrying with the same (stale) object. */
    public static class ModificadoPorOtroException extends ValidationException {
        public ModificadoPorOtroException() {
            super("Otro usuario modificó este bien mientras lo editabas. "
                + "Vuelve a abrirlo para ver los datos actuales");
        }
    }
}
