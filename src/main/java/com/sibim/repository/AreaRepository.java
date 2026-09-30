package com.sibim.repository;

import com.sibim.config.AreaCatalog;
import com.sibim.db.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The {@code areas} table (V24): the organigrama and each area's código prefix.
 *  Only used while connected to the real database. */
public class AreaRepository {

    public AreaCatalog cargar() throws SQLException {
        List<AreaCatalog.Entrada> entradas = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT nombre, grupo, padre, prefijo FROM areas ORDER BY orden, nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entradas.add(new AreaCatalog.Entrada(
                    rs.getString("nombre"),
                    AreaCatalog.Grupo.valueOf(rs.getString("grupo")),
                    rs.getString("padre"),
                    rs.getString("prefijo")));
            }
        }
        return new AreaCatalog(entradas);
    }

    /** Cheap fingerprint of the table: changes whenever an area is added or
     *  edited (every write bumps {@code actualizado_en}; areas are never deleted). */
    public String firma() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) || '|' || COALESCE(MAX(actualizado_en)::text, '') FROM areas");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    /** Inserts a new area (at the end of the list) or updates the group, parent
     *  and prefix of an existing one. The name is the key and never changes. */
    public void guardar(AreaCatalog.Entrada e) throws SQLException {
        guardar(e, null);
    }

    /** Active bienes of {@code area} whose código starts with {@code prefijo + "/"}. */
    public int contarBienesConPrefijo(String area, String prefijo) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM products WHERE area = ? AND fecha_baja IS NULL AND codigo LIKE ?")) {
            ps.setString(1, area);
            ps.setString(2, prefijo + "/%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    /**
     * Same as {@link #guardar(AreaCatalog.Entrada)}; when {@code renumerarDesde}
     * is the area's previous prefix, its active bienes are moved to the new one
     * in the same transaction, keeping their number (TICS/05 → TI/05).
     * @return how many bienes were renumbered
     * @throws SQLException (nothing is saved) when a new código is already used
     *         by another active bien
     */
    public int guardar(AreaCatalog.Entrada e, String renumerarDesde) throws SQLException {
        String sql = """
            INSERT INTO areas (nombre, grupo, padre, prefijo, orden)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(orden), 0) + 1 FROM areas))
            ON CONFLICT (nombre) DO UPDATE SET
                grupo = EXCLUDED.grupo, padre = EXCLUDED.padre,
                prefijo = EXCLUDED.prefijo, actualizado_en = NOW()
            """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, e.nombre());
                    ps.setString(2, e.grupo().name());
                    ps.setString(3, e.padre());
                    ps.setString(4, e.prefijo());
                    ps.executeUpdate();
                }
                int renumerados = 0;
                if (renumerarDesde != null && !renumerarDesde.equals(e.prefijo())) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE products SET codigo = ? || substr(codigo, ?), updated_at = NOW() "
                          + "WHERE area = ? AND fecha_baja IS NULL AND codigo LIKE ?")) {
                        ps.setString(1, e.prefijo());
                        ps.setInt(2, renumerarDesde.length() + 1);
                        ps.setString(3, e.nombre());
                        ps.setString(4, renumerarDesde + "/%");
                        renumerados = ps.executeUpdate();
                    }
                }
                conn.commit();
                return renumerados;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
