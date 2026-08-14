package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Categoria;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CategoriaRepository {

    public List<Categoria> findAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findAllCategorias();
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
        return list;
    }

    public Optional<Categoria> findById(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findCategoriaById(id);
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
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        if (DatabaseConfig.isDemoMode()) {
            if (c.getCreadoEn() == null) c.setCreadoEn(java.time.LocalDateTime.now());
            DemoDataStore.saveCategoria(c);
            return c;
        }
        String sql = """
            INSERT INTO categories (id, nombre, descripcion, color, icono, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                descripcion = EXCLUDED.descripcion,
                color = EXCLUDED.color,
                icono = EXCLUDED.icono
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getDescripcion());
            ps.setString(4, c.getColor());
            ps.setString(5, c.getIcono());
            ps.setTimestamp(6, c.getCreadoEn() != null
                ? Timestamp.valueOf(c.getCreadoEn())
                : Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
        return c;
    }

    public void delete(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.deleteCategoria(id);
            return;
        }
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public boolean tieneProductos(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.tieneProductosEnCategoria(id);
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
        try { c.setTotalProductos(rs.getInt("total_productos")); } catch (SQLException ignored) {}
        return c;
    }
}
