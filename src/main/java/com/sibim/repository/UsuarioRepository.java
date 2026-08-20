package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UsuarioRepository {

    private void requireAdmin() {
        if (!SessionManager.isAdmin()) {
            throw new SecurityException("Solo el administrador puede gestionar usuarios");
        }
    }

    public Optional<Usuario> findByUsername(String username) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findUserByUsername(username);
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Usuario> findById(String id) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findAllUsers().stream()
            .filter(u -> u.getId().equals(id)).findFirst();
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public List<Usuario> findAll() throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isDemoMode()) return new ArrayList<>(DemoDataStore.findAllUsers());
        List<Usuario> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY nombre";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Usuario save(Usuario u) throws SQLException {
        requireAdmin();
        boolean isNew = u.getId() == null;
        if (isNew) u.setId(UUID.randomUUID().toString());
        if (DatabaseConfig.isDemoMode()) {
            if (u.getCreadoEn() == null) u.setCreadoEn(java.time.LocalDateTime.now());
            DemoDataStore.saveUsuario(u);
            logUsuario(u, isNew ? "crear" : "actualizar", isNew ? "Usuario registrado" : "Datos del usuario actualizados");
            return u;
        }
        String sql = """
            INSERT INTO users (id, username, password, nombre, cargo, role, area, debe_cambiar_password, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                username = EXCLUDED.username,
                password = EXCLUDED.password,
                nombre = EXCLUDED.nombre,
                cargo = EXCLUDED.cargo,
                role = EXCLUDED.role,
                area = EXCLUDED.area,
                debe_cambiar_password = EXCLUDED.debe_cambiar_password
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getNombre());
            ps.setString(5, u.getCargo());
            ps.setString(6, u.getRol().getCodigo());
            ps.setString(7, u.getArea());
            ps.setBoolean(8, u.isDebeCambiarPassword());
            ps.setTimestamp(9, u.getCreadoEn() != null
                ? Timestamp.valueOf(u.getCreadoEn())
                : Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
        logUsuario(u, isNew ? "crear" : "actualizar", isNew ? "Usuario registrado" : "Datos del usuario actualizados");
        return u;
    }

    /**
     * Admin-initiated password reset. The new password is one the admin
     * knows (they just typed it), so it's treated the same as a seed
     * password: the account is forced to change it again on next login.
     */
    public void updatePassword(String userId, String newHash) throws SQLException {
        requireAdmin();
        String nombre = findById(userId).map(Usuario::getNombre).orElse(userId);
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.updateUsuarioPassword(userId, newHash, true);
            new AuditLogRepository().log("usuario", userId, nombre, "actualizar", "Contraseña restablecida");
            return;
        }
        String sql = "UPDATE users SET password = ?, debe_cambiar_password = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
        new AuditLogRepository().log("usuario", userId, nombre, "actualizar", "Contraseña restablecida");
    }

    /**
     * Self-service password change (typically the forced first-login flow):
     * sets the new hash AND clears debe_cambiar_password in one statement,
     * since the user themself just chose this password.
     */
    public void completarCambioPassword(String userId, String newHash) throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.updateUsuarioPassword(userId, newHash, false);
            return;
        }
        String sql = "UPDATE users SET password = ?, debe_cambiar_password = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public void delete(String userId) throws SQLException {
        requireAdmin();
        String nombre = findById(userId).map(Usuario::getNombre).orElse(userId);
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.deleteUsuario(userId);
            new AuditLogRepository().log("usuario", userId, nombre, "eliminar", "Usuario eliminado");
            return;
        }
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23503".equals(e.getSQLState()))
                    throw new IllegalStateException(
                        "No se puede eliminar: este usuario tiene movimientos registrados en su historial");
                throw e;
            }
        }
        new AuditLogRepository().log("usuario", userId, nombre, "eliminar", "Usuario eliminado");
    }

    private void logUsuario(Usuario u, String accion, String detalle) {
        new AuditLogRepository().log("usuario", u.getId(), u.getNombre(), accion, detalle);
    }

    public boolean existsByUsername(String username, String excludeId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findAllUsers().stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(username)
                            && !u.getId().equals(excludeId != null ? excludeId : ""));
        }
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) AND id != ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, excludeId != null ? excludeId : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Usuario mapRow(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setId(rs.getString("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password"));
        u.setNombre(rs.getString("nombre"));
        u.setCargo(rs.getString("cargo"));
        u.setRol(Rol.fromCodigo(rs.getString("role")));
        u.setArea(rs.getString("area"));
        u.setDebeCambiarPassword(rs.getBoolean("debe_cambiar_password"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) u.setCreadoEn(ts.toLocalDateTime());
        return u;
    }
}
