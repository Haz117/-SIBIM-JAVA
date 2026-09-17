package com.sibim.db.offline;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Manages the {@code users_cache} table inside the offline SQLite database.
 *
 * Extracted from {@link OfflineStore} because user-cache logic is
 * conceptually distinct from the inventory mirror: it never touches
 * the in-memory PRODUCTOS/MOVIMIENTOS/CATEGORIAS lists, uses no
 * transactions (each call is self-contained), and has its own TTL
 * policy. Both public methods accept a {@link Connection} obtained via
 * {@link OfflineStore#sharedConnection()} so they participate in the
 * same encrypted SQLite file without duplicating connection management.
 *
 * The TTL constant is re-exported as {@link #OFFLINE_CACHE_TTL_DAYS}
 * so callers (AuthService, tests) can reference it from one place.
 */
public final class OfflineUserCache {

    /** Credentials cached for longer than this many days are rejected —
     *  the user must re-authenticate online to refresh the local copy.
     *  This is the one canonical definition — OfflineStore re-exports it
     *  from here; it must not also read from OfflineStore, or the two
     *  fields become a circular reference that resolves to 0 (whichever
     *  class the JVM initializes second reads the other's not-yet-assigned
     *  default), making every cache entry look instantly expired. */
    public static final int OFFLINE_CACHE_TTL_DAYS = 30;

    private OfflineUserCache() {}

    /**
     * Upserts the user's credentials and profile into {@code users_cache}.
     * Called after every successful ONLINE login so the entry stays fresh.
     *
     * @param conn the shared offline SQLite connection
     * @param u    the authenticated user (must have a non-null id, username, and password hash)
     */
    public static void cacheUser(Connection conn, Usuario u) throws SQLException {
        String sql = """
            INSERT INTO users_cache (id, username, password_hash, nombre, rol, area, debe_cambiar_password, activo, cached_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET username=excluded.username, password_hash=excluded.password_hash,
                nombre=excluded.nombre, rol=excluded.rol, area=excluded.area,
                debe_cambiar_password=excluded.debe_cambiar_password,
                activo=excluded.activo, cached_at=excluded.cached_at
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getNombre());
            ps.setString(5, u.getRol().getCodigo());
            ps.setString(6, u.getArea());
            ps.setInt(7, u.isDebeCambiarPassword() ? 1 : 0);
            ps.setInt(8, u.isActivo() ? 1 : 0);
            ps.setString(9, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    /**
     * Looks up a cached user by username and validates the TTL.
     *
     * Returns {@link Optional#empty()} if:
     * <ul>
     *   <li>No entry with that username exists, or</li>
     *   <li>The entry was cached more than {@link #OFFLINE_CACHE_TTL_DAYS} days ago.</li>
     * </ul>
     * Expired entries trigger a "reconnect required" path in AuthService
     * rather than silently accepting stale credentials.
     *
     * @param conn     the shared offline SQLite connection
     * @param username the login username to look up (case-sensitive)
     */
    public static Optional<Usuario> findCachedUserByUsername(Connection conn, String username)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM users_cache WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String cachedAtStr = rs.getString("cached_at");
                if (cachedAtStr != null) {
                    try {
                        LocalDateTime cachedAt = LocalDateTime.parse(cachedAtStr);
                        if (cachedAt.isBefore(LocalDateTime.now().minusDays(OFFLINE_CACHE_TTL_DAYS))) {
                            return Optional.empty(); // caché caducado
                        }
                    } catch (Exception ignored) {}
                }
                Usuario u = new Usuario();
                u.setId(rs.getString("id"));
                u.setUsername(rs.getString("username"));
                u.setPasswordHash(rs.getString("password_hash"));
                u.setNombre(rs.getString("nombre"));
                u.setRol(Rol.fromCodigo(rs.getString("rol")));
                u.setArea(rs.getString("area"));
                u.setDebeCambiarPassword(rs.getInt("debe_cambiar_password") != 0);
                try { u.setActivo(rs.getInt("activo") != 0); } catch (Exception ignored) { u.setActivo(true); }
                return Optional.of(u);
            }
        }
    }
}
