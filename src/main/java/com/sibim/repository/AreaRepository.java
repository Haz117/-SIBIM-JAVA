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

    /** Inserts a new area (at the end of the list) or updates the group, parent
     *  and prefix of an existing one. The name is the key and never changes. */
    public void guardar(AreaCatalog.Entrada e) throws SQLException {
        String sql = """
            INSERT INTO areas (nombre, grupo, padre, prefijo, orden)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(orden), 0) + 1 FROM areas))
            ON CONFLICT (nombre) DO UPDATE SET
                grupo = EXCLUDED.grupo, padre = EXCLUDED.padre,
                prefijo = EXCLUDED.prefijo, actualizado_en = NOW()
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.nombre());
            ps.setString(2, e.grupo().name());
            ps.setString(3, e.padre());
            ps.setString(4, e.prefijo());
            ps.executeUpdate();
        }
    }
}
