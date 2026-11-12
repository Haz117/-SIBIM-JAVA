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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which PCs each account has signed in on (table {@code accesos_equipo}, V29).
 * An account can only sign in WITHOUT internet on a PC where it signed in
 * before with a connection, so this is how Patrimonio finds, ahead of an
 * outage, the accounts that are not ready to work offline.
 */
public class AccesosEquipoService {

    public record Equipo(String nombre, LocalDateTime ultimoAcceso, String version) {}

    /** One account and the PCs it has signed in on (empty = none yet). */
    public record Cuenta(String username, String nombre, String area, boolean activa, List<Equipo> equipos) {}

    /** This PC's name as its users know it (Windows: the computer name). */
    public static String esteEquipo() {
        String nombre = System.getenv("COMPUTERNAME");
        if (nombre == null || nombre.isBlank()) nombre = System.getenv("HOSTNAME");
        if (nombre == null || nombre.isBlank()) {
            try { nombre = java.net.InetAddress.getLocalHost().getHostName(); }
            catch (java.net.UnknownHostException e) { nombre = "equipo"; }
        }
        return nombre.trim();
    }

    /** Notes that {@code userId} signed in on this PC with a connection. */
    public void registrar(String userId) throws SQLException {
        if (userId == null || DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO accesos_equipo (user_id, equipo, version) VALUES (?, ?, ?) "
                 + "ON CONFLICT (user_id, equipo) DO UPDATE SET ultimo_acceso = NOW(), version = EXCLUDED.version")) {
            ps.setString(1, userId);
            ps.setString(2, esteEquipo());
            ps.setString(3, com.sibim.util.UpdateChecker.currentVersion());
            ps.executeUpdate();
        }
    }

    /** A PC that last opened SIBIM with an older version than this one. */
    public record Atrasado(String equipo, String version, LocalDateTime ultimoAcceso) {}

    /** The PCs used in the last {@code dias} days whose last sign-in was with a version older
     *  than {@code actual}: on 11-nov-2026 a laptop still had 1.0.0 and another PC 1.0.20,
     *  and only the window of equipos said so. Empty offline, in the demo or for an área. */
    public List<Atrasado> desactualizados(String actual, int dias) throws SQLException {
        if (!SessionManager.isAdmin() || DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return List.of();
        List<Atrasado> recientes = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT ON (equipo) equipo, version, ultimo_acceso FROM accesos_equipo "
                 + "ORDER BY equipo, ultimo_acceso DESC");
             ResultSet rs = ps.executeQuery()) {
            LocalDateTime desde = LocalDateTime.now().minusDays(dias);
            while (rs.next()) {
                Timestamp t = rs.getTimestamp("ultimo_acceso");
                String v = rs.getString("version");
                if (t == null || t.toLocalDateTime().isBefore(desde) || v == null || v.isBlank()) continue;
                recientes.add(new Atrasado(rs.getString("equipo"), v.trim(), t.toLocalDateTime()));
            }
        }
        return atrasados(actual, recientes);
    }

    /** Against the newest version in use, this PC's included: on a PC that wasn't updated
     *  yet, the others are not the ones behind. */
    static List<Atrasado> atrasados(String actual, List<Atrasado> recientes) {
        String referencia = actual;
        for (Atrasado a : recientes)
            if (com.sibim.util.UpdateChecker.isNewer(a.version(), referencia == null ? "0.0.0" : referencia)) referencia = a.version();
        String ref = referencia;
        return recientes.stream().filter(a -> ref != null && com.sibim.util.UpdateChecker.isNewer(ref, a.version())).toList();
    }

    /** Every account with its PCs — accounts that never signed in first. */
    public List<Cuenta> porCuenta() throws SQLException {
        if (!SessionManager.isAdmin()) throw new SecurityException("Solo el administrador consulta los accesos");
        DatabaseConfig.exigirServidor("Consultar los accesos por equipo");
        Map<String, Cuenta> cuentas = new LinkedHashMap<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT u.id, u.username, u.nombre, u.area, u.activo, a.equipo, a.ultimo_acceso, a.version "
                 + "FROM users u LEFT JOIN accesos_equipo a ON a.user_id = u.id "
                 + "ORDER BY (a.equipo IS NULL) DESC, u.nombre, a.ultimo_acceso DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Cuenta cuenta = cuentas.get(rs.getString("id"));
                if (cuenta == null) {
                    cuenta = new Cuenta(rs.getString("username"), rs.getString("nombre"), rs.getString("area"),
                        rs.getBoolean("activo"), new ArrayList<>());
                    cuentas.put(rs.getString("id"), cuenta);
                }
                String equipo = rs.getString("equipo");
                if (equipo != null) {
                    Timestamp t = rs.getTimestamp("ultimo_acceso");
                    cuenta.equipos().add(new Equipo(equipo, t != null ? t.toLocalDateTime() : null, rs.getString("version")));
                }
            }
        }
        return new ArrayList<>(cuentas.values());
    }
}
