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
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepo;
    private final AuditLogRepository auditRepo;
    private final PriceHistoryRepository priceHistoryRepo;
    private final ResguardoActivo resguardoActivo;

    /** Folio of the active resguardo covering a bien, if any. */
    @FunctionalInterface
    interface ResguardoActivo { Optional<String> folio(String productoId) throws SQLException; }

    public ProductoService() {
        this(new ProductoRepository(), new AuditLogRepository(), new PriceHistoryRepository(),
            id -> new com.sibim.repository.ResguardoRepository().findActivoByProductoId(id)
                .map(com.sibim.model.Resguardo::getNumero));
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
        this.productoRepo     = productoRepo;
        this.auditRepo        = auditRepo;
        this.priceHistoryRepo = priceHistoryRepo;
        this.resguardoActivo  = resguardoActivo;
    }

    public List<Producto> getAll() throws SQLException {
        return productoRepo.findAll();
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

    public void marcarEtiquetado(List<String> ids, boolean valor) throws SQLException {
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

    public boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        return productoRepo.existsByCodigo(codigo, excludeId);
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

    public List<Producto> getProximasRevisiones(int dias) throws SQLException {
        return productoRepo.findProximasRevisiones(dias);
    }

    public Producto save(Producto p) throws SQLException, ValidationException {
        boolean isNew = p.getId() == null;
        // El código se asigna por área (ver AreaCodigos) al dar de alta un bien
        // nuevo; al editar uno existente el código sigue siendo editable a mano
        // (útil para corregir datos heredados que no siguen este formato).
        if (isNew && p.getArea() != null && AreaCodigos.tienePrefijo(p.getArea()))
            p.setCodigo(asignarCodigo(p.getArea()));

        // A municipal bien has no sale price. The column stays (older clients,
        // imports and reports still read it) but always mirrors the purchase
        // price, which is what every valuation uses (Producto#getValorTotal).
        p.setPrecioVenta(p.getPrecioCompra());

        BigDecimal prevCompra = null;
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
            // A signed resguardo says who holds the bien (ResguardoRepository
            // keeps products.resguardante in step with it); an edit — single
            // or in bulk — can't contradict the document.
            if (!java.util.Objects.equals(sinBlancos(p.getResguardante()), sinBlancos(actual.getResguardante()))) {
                Optional<String> folio = resguardoActivo.folio(p.getId());
                if (folio.isPresent())
                    throw new ValidationException("El resguardante lo define el resguardo " + folio.get()
                        + "; para cambiarlo cancélalo o genera uno nuevo en Resguardos");
            }
        }
        validate(p);

        Producto saved = productoRepo.save(p);
        log.info("Bien {} [{}] '{}'", isNew ? "registrado" : "actualizado", saved.getId(), saved.getNombre());
        auditRepo.log("producto", saved.getId(), saved.getNombre(),
            isNew ? "crear" : "actualizar",
            isNew ? "Bien registrado" : "Datos del bien actualizados");

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

    /** Menor número positivo no usado por ningún bien activo con el prefijo
     *  de {@code area} — no hay un contador persistido: dado que un bien
     *  dado de baja o transferido deja de contar como activo, su número
     *  vuelve a aparecer libre automáticamente en el próximo cálculo. */
    private String asignarCodigo(String area) throws SQLException {
        return productoRepo.siguienteCodigo(area);
    }

    private static String sinBlancos(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean priceChanged(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    public void delete(String id) throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) return;
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
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
        darDeBaja(id, motivo, null, null, null, null);
    }

    /** Full baja patrimonial with committee dictamen data.
     *  {@code tipoDestino}, {@code dictamen}, {@code numeroActa} and {@code fechaDictamen}
     *  are all optional (null means not filled). */
    public void darDeBaja(String id, String motivo,
                          String tipoDestino, String dictamen,
                          String numeroActa, LocalDate fechaDictamen)
            throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        if (motivo == null || motivo.isBlank())
            throw new ValidationException("El motivo de la baja es obligatorio");
        productoRepo.darDeBaja(id, motivo.trim(), tipoDestino, dictamen, numeroActa, fechaDictamen);
        log.info("Baja patrimonial bien [{}] '{}' — motivo: {} destino: {}",
            id, p.getNombre(), motivo.trim(), tipoDestino);
        String auditDetail = "Motivo: " + motivo.trim()
            + (tipoDestino != null ? " | Destino: " + tipoDestino : "")
            + (numeroActa  != null ? " | Acta: " + numeroActa     : "");
        auditRepo.log("producto", id, p.getNombre(), "baja", auditDetail);
    }

    /** Reverses a baja patrimonial, restoring the bien to active inventory.
     *  Also reassigns its código according to its área — the número it held
     *  before the baja may have since been claimed by a different bien in
     *  that área (see AreaCodigos, asignarCodigo). */
    public void reactivar(String id) throws SQLException, ValidationException {
        Optional<Producto> opt = productoRepo.findById(id);
        if (opt.isEmpty()) throw new ValidationException("Bien no encontrado");
        Producto p = opt.get();
        if (!SessionManager.isAreaAccessible(p.getArea()))
            throw new ValidationException("No tienes acceso a esa area");
        if (AreaCodigos.tienePrefijo(p.getArea())) {
            productoRepo.reactivarConCodigo(id, asignarCodigo(p.getArea()));
        } else {
            productoRepo.reactivar(id);
        }
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
        productoRepo.saveFotos(productoId, fotos);
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
        if (!SessionManager.isAreaAccessible(p.getArea()))
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
