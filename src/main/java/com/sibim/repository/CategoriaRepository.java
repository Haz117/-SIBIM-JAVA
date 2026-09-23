package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Categoria;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CategoriaRepository {

    private void requireAdmin() {
        if (!SessionManager.isAdmin()) {
            throw new SecurityException("Solo el administrador puede gestionar categorías");
        }
    }

    public List<Categoria> findAll() throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findAllCategorias();
        List<Categoria> list = new ArrayList<>();
        String sql = """
            SELECT c.*, COUNT(p.id) AS total_productos
            FROM categories c
            LEFT JOIN products p ON p.categoria_id = c.id
            GROUP BY c.id
            ORDER BY c.nombre
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        // Keeps OfflineStore's local mirror fresh — see ProductoRepository#findAll.
        OfflineStore.cacheCategorias(list);
        return list;
    }

    public Optional<Categoria> findById(String id) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findCategoriaById(id);
        String sql = """
            SELECT c.*, COUNT(p.id) AS total_productos
            FROM categories c
            LEFT JOIN products p ON p.categoria_id = c.id
            WHERE c.id = ?
            GROUP BY c.id
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public Categoria save(Categoria c) throws SQLException {
        requireAdmin();
        boolean isNew = c.getId() == null || findById(c.getId()).isEmpty();
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            if (c.getCreadoEn() == null) c.setCreadoEn(java.time.LocalDateTime.now());
            local.saveCategoria(c);
            logSave(c, isNew);
            return c;
        }
        Categoria saved = saveOnline(c);
        logSave(c, isNew);
        return saved;
    }

    /** Replay target for SyncService — see ProductoRepository#saveOnline. */
    public Categoria saveOnline(Categoria c) throws SQLException {
        String sql = """
            INSERT INTO categories (id, nombre, descripcion, color, icono, codigo_conac, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                descripcion = EXCLUDED.descripcion,
                color = EXCLUDED.color,
                icono = EXCLUDED.icono,
                codigo_conac = EXCLUDED.codigo_conac
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getDescripcion());
            ps.setString(4, c.getColor());
            ps.setString(5, c.getIcono());
            ps.setString(6, c.getCodigoConac());
            ps.setTimestamp(7, c.getCreadoEn() != null
                ? Timestamp.valueOf(c.getCreadoEn())
                : Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
        return c;
    }

    private void logSave(Categoria c, boolean isNew) {
        new AuditLogRepository().log("categoria", c.getId(), c.getNombre(), isNew ? "crear" : "actualizar",
            isNew ? "Categoría registrada" : "Datos de la categoría actualizados");
    }

    public void delete(String id) throws SQLException {
        requireAdmin();
        String nombre = findById(id).map(Categoria::getNombre).orElse(id);
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            local.deleteCategoria(id);
            new AuditLogRepository().log("categoria", id, nombre, "eliminar", "Categoría eliminada");
            return;
        }
        deleteOnline(id);
        new AuditLogRepository().log("categoria", id, nombre, "eliminar", "Categoría eliminada");
    }

    /** Replay target for SyncService — see ProductoRepository#saveOnline. */
    public void deleteOnline(String id) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public void fusionar(String sourceId, String targetId) throws SQLException {
        requireAdmin();
        String sourceName = findById(sourceId).map(Categoria::getNombre).orElse(sourceId);
        String targetName = findById(targetId).map(Categoria::getNombre).orElse(targetId);
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) throw new UnsupportedOperationException("Fusión no disponible en modo offline");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET categoria_id = ? WHERE categoria_id = ?")) {
                    ps.setString(1, targetId);
                    ps.setString(2, sourceId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM categories WHERE id = ?")) {
                    ps.setString(1, sourceId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        new AuditLogRepository().log("categoria", sourceId, sourceName, "fusionar",
            "Fusionada en \"" + targetName + "\"");
    }

    public boolean tieneProductos(String id) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.tieneProductosEnCategoria(id);
        String sql = "SELECT 1 FROM products WHERE categoria_id = ? LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Categoria mapRow(ResultSet rs) throws SQLException {
        Categoria c = new Categoria();
        c.setId(rs.getString("id"));
        c.setNombre(rs.getString("nombre"));
        c.setDescripcion(rs.getString("descripcion"));
        c.setColor(rs.getString("color"));
        c.setIcono(rs.getString("icono"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreadoEn(ts.toLocalDateTime());
        try {
            c.setCodigoConac(rs.getString("codigo_conac"));
        } catch (SQLException ignored) {
            // Column may not exist yet (migration not run) — ignore gracefully
        }
        try {
            c.setTotalProductos(rs.getInt("total_productos"));
        } catch (SQLException e) {
            // Column only present in list queries that JOIN the count — absent in single-row fetches
            if (!e.getMessage().contains("total_productos")) throw e;
        }
        return c;
    }
}
