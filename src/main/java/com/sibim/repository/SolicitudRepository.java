package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Solicitud;
import com.sibim.session.SessionManager;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Requests for préstamos and resguardos (table {@code solicitudes}, V28).
 *  They only exist on the server: asking and answering need a connection. */
public class SolicitudRepository {

    private static final String COLUMNAS =
        "id, tipo, producto_id, producto_nombre, producto_codigo, area, solicitante, area_destino, "
        + "responsable, cargo, motivo, fecha_devolucion, estado, respuesta, documento, resuelto_por, "
        + "resuelto_en, creado_en";

    public Solicitud save(Solicitud s) throws SQLException {
        DatabaseConfig.exigirServidor("Enviar solicitudes");
        String id = UUID.randomUUID().toString();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO solicitudes (id, tipo, producto_id, producto_nombre, producto_codigo, area, "
                 + "solicitante, area_destino, responsable, cargo, motivo, fecha_devolucion) "
                 + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, s.tipo());
            ps.setString(3, s.productoId());
            ps.setString(4, s.productoNombre());
            ps.setString(5, s.productoCodigo());
            ps.setString(6, s.area());
            ps.setString(7, s.solicitante());
            ps.setString(8, s.areaDestino());
            ps.setString(9, s.responsable());
            ps.setString(10, s.cargo());
            ps.setString(11, s.motivo());
            ps.setDate(12, s.fechaDevolucion() != null ? Date.valueOf(s.fechaDevolucion()) : null);
            ps.executeUpdate();
        }
        return findById(id);
    }

    public Solicitud findById(String id) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNAS + " FROM solicitudes WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The administrator's inbox: every request still waiting, oldest first. */
    public List<Solicitud> findPendientes() throws SQLException {
        return consultar("WHERE estado = 'PENDIENTE' ORDER BY creado_en", null);
    }

    /** What the caller's áreas asked for, newest first (at most 100). */
    public List<Solicitud> findDeMisAreas() throws SQLException {
        Set<String> areas = SessionManager.getAccessibleAreas();
        if (areas == null) return consultar("ORDER BY creado_en DESC LIMIT 100", null);
        if (areas.isEmpty()) return new ArrayList<>();
        return consultar("WHERE area = ANY(?) ORDER BY creado_en DESC LIMIT 100", areas.toArray(new String[0]));
    }

    public int countPendientes() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM solicitudes WHERE estado = 'PENDIENTE'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** True if the bien already has a request of that kind waiting. */
    public boolean existePendiente(String productoId, String tipo) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM solicitudes WHERE producto_id = ? AND tipo = ? AND estado = 'PENDIENTE'")) {
            ps.setString(1, productoId);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Closes a pending request. Returns false if someone else already did. */
    public boolean resolver(String id, String estado, String respuesta, String documento, String resueltoPor)
            throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE solicitudes SET estado = ?, respuesta = ?, documento = ?, resuelto_por = ?, "
                 + "resuelto_en = NOW() WHERE id = ? AND estado = 'PENDIENTE'")) {
            ps.setString(1, estado);
            ps.setString(2, respuesta);
            ps.setString(3, documento);
            ps.setString(4, resueltoPor);
            ps.setString(5, id);
            return ps.executeUpdate() == 1;
        }
    }

    private List<Solicitud> consultar(String filtro, String[] areas) throws SQLException {
        List<Solicitud> lista = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNAS + " FROM solicitudes " + filtro)) {
            if (areas != null) ps.setArray(1, c.createArrayOf("text", areas));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(map(rs));
            }
        }
        return lista;
    }

    private static Solicitud map(ResultSet rs) throws SQLException {
        Date fecha = rs.getDate("fecha_devolucion");
        Timestamp resuelto = rs.getTimestamp("resuelto_en");
        Timestamp creado = rs.getTimestamp("creado_en");
        return new Solicitud(
            rs.getString("id"), rs.getString("tipo"),
            rs.getString("producto_id"), rs.getString("producto_nombre"), rs.getString("producto_codigo"),
            rs.getString("area"), rs.getString("solicitante"),
            rs.getString("area_destino"), rs.getString("responsable"), rs.getString("cargo"),
            rs.getString("motivo"), fecha != null ? fecha.toLocalDate() : null,
            rs.getString("estado"), rs.getString("respuesta"), rs.getString("documento"),
            rs.getString("resuelto_por"), resuelto != null ? resuelto.toLocalDateTime() : null,
            creado != null ? creado.toLocalDateTime() : null);
    }
}
