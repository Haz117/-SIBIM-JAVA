package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The errors the PCs reported (see {@code ErroresEquipoAppender}), for the administrator. */
public class ErroresEquipoService {

    /** Reports older than this are deleted when the list is opened. */
    static final int DIAS_CONSERVADOS = 90;

    public record ErrorEquipo(String equipo, String usuario, String version, String origen, String mensaje,
                              String detalle, LocalDateTime creadoEn) {}

    /** The latest {@code limite} reports, newest first. */
    public List<ErrorEquipo> recientes(int limite) throws SQLException {
        if (!SessionManager.isAdmin()) throw new SecurityException("Solo el administrador consulta los errores");
        DatabaseConfig.exigirServidor("Consultar los errores de los equipos");
        List<ErrorEquipo> lista = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM errores_equipo WHERE creado_en < NOW() - (? || ' days')::interval")) {
                ps.setString(1, String.valueOf(DIAS_CONSERVADOS));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT equipo, usuario, version, origen, mensaje, detalle, creado_en FROM errores_equipo "
                     + "ORDER BY creado_en DESC LIMIT ?")) {
                ps.setInt(1, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp t = rs.getTimestamp("creado_en");
                        lista.add(new ErrorEquipo(rs.getString("equipo"), rs.getString("usuario"),
                            rs.getString("version"), rs.getString("origen"), rs.getString("mensaje"),
                            rs.getString("detalle"), t != null ? t.toLocalDateTime() : null));
                    }
                }
            }
        }
        return lista;
    }

    public void borrarTodos() throws SQLException {
        if (!SessionManager.isAdmin()) throw new SecurityException("Solo el administrador borra los errores");
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM errores_equipo")) {
            ps.executeUpdate();
        }
    }
}
