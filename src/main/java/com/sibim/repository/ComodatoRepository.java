package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.OfflineDocs;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Comodato;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ComodatoRepository {

    private final FolioRepository folioRepo = new FolioRepository();

    /** Comodatos involve an external entity — no internal area restriction applies
     *  to the comodato record itself; however we still scope by producto_id's
     *  area ownership so a user can only create/see comodatos for goods they have
     *  access to, matching PrestamoRepository's convention. */
    private static String scopeCondicion(List<Object> params) {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible == null) return null;
        // Join to products table to check area ownership
        String[] areas = accessible.toArray(new String[0]);
        params.add(areas);
        return "c.producto_id IN (SELECT id FROM products WHERE area = ANY(?))";
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params) throws SQLException {
        bindParams(ps, conn, params, 1);
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params, int startIndex) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            int idx = startIndex + i;
            if (p instanceof String[] arr) ps.setArray(idx, conn.createArrayOf("text", arr));
            else ps.setObject(idx, p);
        }
    }

    /** Offline: this PC's copy (see OfflineDocs), scoped by the bien's área
     *  in the offline bienes mirror, like scopeCondicion does on the server. */
    private static List<Comodato> locales() throws SQLException {
        Set<String> acc = SessionManager.getAccessibleAreas();
        List<Comodato> todos = OfflineDocs.todos(OfflineDocs.COMODATO, Comodato.class);
        List<Comodato> visibles = new ArrayList<>();
        for (Comodato c : todos) {
            if (acc == null || OfflineStore.findProductoById(c.getProductoId())
                    .map(p -> acc.contains(p.getArea())).orElse(false))
                visibles.add(c);
        }
        visibles.sort(java.util.Comparator.comparing(Comodato::getCreatedAt,
            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return visibles;
    }

    /** Offline there's no UPDATE turning VIGENTE into VENCIDO: decide by date. */
    private static boolean vencidoLocal(Comodato c) {
        return Comodato.ESTADO_VENCIDO.equals(c.getEstado())
            || (Comodato.ESTADO_VIGENTE.equals(c.getEstado()) && c.getFechaFin() != null
                && c.getFechaFin().isBefore(LocalDate.now()));
    }

    private static boolean abiertoLocal(Comodato c) {
        return Comodato.ESTADO_VIGENTE.equals(c.getEstado()) || Comodato.ESTADO_VENCIDO.equals(c.getEstado());
    }

    private static boolean abiertoParaProducto(String productoId) throws SQLException {
        return OfflineDocs.todos(OfflineDocs.COMODATO, Comodato.class).stream()
            .anyMatch(c -> productoId.equals(c.getProductoId()) && abiertoLocal(c));
    }

    public List<Comodato> findAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return List.of();
        if (OfflineDocs.activo()) return locales();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c"
            + (scope != null ? " WHERE " + scope : "")
            + " ORDER BY c.created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        OfflineDocs.guardarTodos(OfflineDocs.COMODATO, list, Comodato::getId);
        return list;
    }

    public List<Comodato> findVigentes() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return List.of();
        if (OfflineDocs.activo()) return locales().stream()
            .filter(c -> Comodato.ESTADO_VIGENTE.equals(c.getEstado()) && !vencidoLocal(c)).toList();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c WHERE c.estado = 'VIGENTE'"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY c.fecha_fin ASC NULLS LAST";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Comodato> findVencidos() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return List.of();
        if (OfflineDocs.activo()) return locales().stream().filter(ComodatoRepository::vencidoLocal).toList();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c WHERE c.estado = 'VENCIDO'"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY c.fecha_fin ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Comodato findById(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return null;
        if (OfflineDocs.activo())
            return locales().stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "SELECT c.* FROM comodatos c WHERE c.id = ?" + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            bindParams(ps, conn, scopeParams, 2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** True when {@code productoId} already has an open comodato (VIGENTE or VENCIDO) —
     *  used to stop the same bien from being loaned out to two entities at once. */
    public boolean existeVigentePorProducto(String productoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return false;
        if (OfflineDocs.activo()) return abiertoParaProducto(productoId);
        String sql = "SELECT 1 FROM comodatos WHERE producto_id = ? AND estado IN ('VIGENTE','VENCIDO') LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Comodato save(Comodato c) throws SQLException {
        if (DatabaseConfig.isDemoMode())
            throw new IllegalStateException("Comodatos no disponibles en modo demo");
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        if (OfflineDocs.activo()) {
            com.sibim.model.Usuario yo = SessionManager.getCurrentUser();
            if (yo != null) {
                c.setCreadoPorId(yo.getId());
                c.setCreadoPorNombre(yo.getNombre());
            }
            c.setNumero(OfflineDocs.folioProvisional("CDT", c.getId()));
            c.setEstado(Comodato.ESTADO_VIGENTE);
            if (c.getFechaInicio() == null) c.setFechaInicio(LocalDate.now());
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            OfflineDocs.registrar(OfflineDocs.COMODATO, "CREAR", c.getId(), c);
            return c;
        }
        return saveOnline(c);
    }

    /** Server write regardless of the offline flag (SyncService replays offline
     *  comodatos here); a provisional folio is replaced by the next real one. */
    public Comodato saveOnline(Comodato c) throws SQLException {
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        if (c.getNumero() == null || c.getNumero().contains("-PROV-")) c.setNumero(nextNumero());

        com.sibim.model.Usuario u = SessionManager.getCurrentUser();
        if (u != null) {
            c.setCreadoPorId(u.getId());
            c.setCreadoPorNombre(u.getNombre());
        }

        String sql = """
            INSERT INTO comodatos
              (id, numero, producto_id, producto_nombre, producto_codigo,
               entidad_receptora, contacto_nombre, contacto_cargo, domicilio,
               motivo, condiciones, fecha_inicio, fecha_fin, estado,
               creado_por_id, creado_por_nombre, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'VIGENTE',?,?,NOW(),NOW())
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNumero());
            ps.setString(3, c.getProductoId());
            ps.setString(4, c.getProductoNombre());
            ps.setString(5, c.getProductoCodigo());
            ps.setString(6, c.getEntidadReceptora());
            ps.setString(7, c.getContactoNombre());
            ps.setString(8, c.getContactoCargo());
            ps.setString(9, c.getDomicilio());
            ps.setString(10, c.getMotivo());
            ps.setString(11, c.getCondiciones());
            ps.setDate(12, Date.valueOf(c.getFechaInicio() != null ? c.getFechaInicio() : LocalDate.now()));
            if (c.getFechaFin() != null) ps.setDate(13, Date.valueOf(c.getFechaFin()));
            else ps.setNull(13, Types.DATE);
            ps.setString(14, c.getCreadoPorId());
            ps.setString(15, c.getCreadoPorNombre());
            ps.executeUpdate();
        }
        c.setEstado(Comodato.ESTADO_VIGENTE);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    public void concluir(String id, LocalDate fechaReal) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return;
        if (OfflineDocs.activo()) {
            Comodato c = abiertoLocal(id);
            c.setEstado(Comodato.ESTADO_CONCLUIDO);
            c.setFechaDevolucionReal(fechaReal != null ? fechaReal : LocalDate.now());
            c.setUpdatedAt(LocalDateTime.now());
            OfflineDocs.registrar(OfflineDocs.COMODATO, "CONCLUIR", id, c);
            return;
        }
        concluirOnline(id, fechaReal);
    }

    private Comodato abiertoLocal(String id) throws SQLException {
        return locales().stream().filter(x -> x.getId().equals(id) && abiertoLocal(x)).findFirst()
            .orElseThrow(() -> new SQLException("El comodato no existe, ya fue concluido/rescindido, o no tienes acceso a su área (id=" + id + ")"));
    }

    public void concluirOnline(String id, LocalDate fechaReal) throws SQLException {
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "UPDATE comodatos AS c SET estado = 'CONCLUIDO', fecha_devolucion_real = ?, updated_at = NOW() "
            + "WHERE c.id = ? AND c.estado IN ('VIGENTE','VENCIDO')"
            + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fechaReal != null ? fechaReal : LocalDate.now()));
            ps.setString(2, id);
            bindParams(ps, conn, scopeParams, 3);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El comodato no existe, ya fue concluido/rescindido, o no tienes acceso a su área (id=" + id + ")");
        }
    }

    public void rescindir(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return;
        if (OfflineDocs.activo()) {
            Comodato c = abiertoLocal(id);
            c.setEstado(Comodato.ESTADO_RESCINDIDO);
            c.setUpdatedAt(LocalDateTime.now());
            OfflineDocs.registrar(OfflineDocs.COMODATO, "RESCINDIR", id, c);
            return;
        }
        rescindirOnline(id);
    }

    public void rescindirOnline(String id) throws SQLException {
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "UPDATE comodatos AS c SET estado = 'RESCINDIDO', updated_at = NOW() "
            + "WHERE c.id = ? AND c.estado IN ('VIGENTE','VENCIDO')"
            + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            bindParams(ps, conn, scopeParams, 2);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El comodato no existe, ya fue concluido/rescindido, o no tienes acceso a su área (id=" + id + ")");
        }
    }

    public int updateVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return 0;
        String sql = """
            UPDATE comodatos SET estado = 'VENCIDO', updated_at = NOW()
            WHERE estado = 'VIGENTE' AND fecha_fin IS NOT NULL AND fecha_fin < CURRENT_DATE
              AND fecha_devolucion_real IS NULL
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public boolean existsVigenteForProducto(String productoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return false;
        if (OfflineDocs.activo()) return abiertoParaProducto(productoId);
        String sql = "SELECT 1 FROM comodatos WHERE producto_id = ? AND estado IN ('VIGENTE','VENCIDO') LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String nextNumero() throws SQLException {
        return folioRepo.next("CDT");
    }

    private Comodato mapRow(ResultSet rs) throws SQLException {
        Comodato c = new Comodato();
        c.setId(rs.getString("id"));
        c.setNumero(rs.getString("numero"));
        c.setProductoId(rs.getString("producto_id"));
        c.setProductoNombre(rs.getString("producto_nombre"));
        c.setProductoCodigo(rs.getString("producto_codigo"));
        c.setEntidadReceptora(rs.getString("entidad_receptora"));
        c.setContactoNombre(rs.getString("contacto_nombre"));
        c.setContactoCargo(rs.getString("contacto_cargo"));
        c.setDomicilio(rs.getString("domicilio"));
        c.setMotivo(rs.getString("motivo"));
        c.setCondiciones(rs.getString("condiciones"));
        Date fi = rs.getDate("fecha_inicio");
        if (fi != null) c.setFechaInicio(fi.toLocalDate());
        Date ff = rs.getDate("fecha_fin");
        if (ff != null) c.setFechaFin(ff.toLocalDate());
        Date fdr = rs.getDate("fecha_devolucion_real");
        if (fdr != null) c.setFechaDevolucionReal(fdr.toLocalDate());
        c.setEstado(rs.getString("estado"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        Timestamp upd = rs.getTimestamp("updated_at");
        if (upd != null) c.setUpdatedAt(upd.toLocalDateTime());
        c.setCreadoPorId(rs.getString("creado_por_id"));
        c.setCreadoPorNombre(rs.getString("creado_por_nombre"));
        return c;
    }
}
