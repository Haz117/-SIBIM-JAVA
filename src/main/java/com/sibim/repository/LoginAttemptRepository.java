package com.sibim.repository;

import com.sibim.db.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Failed-login counters in the database (V23), so the lockout applies to the
 * account on every PC — the local file counter (AuthAttemptStore) could be
 * reset by deleting the file or simply by trying from another computer. The
 * lockout window is measured with the server's clock, not each PC's.
 * Only used while connected to the real database.
 */
public class LoginAttemptRepository {

    /** Minutes left in the lockout for {@code username}, or 0 if it can log in. */
    public long minutosBloqueo(String username, int maxIntentos, long ventanaMs) throws SQLException {
        String sql = """
            SELECT intentos,
                   EXTRACT(EPOCH FROM (ventana_inicio + make_interval(secs => ?) - NOW())) AS segundos
            FROM login_attempts WHERE username = ?
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, ventanaMs / 1000.0);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                double segundos = rs.getDouble("segundos");
                if (rs.getInt("intentos") < maxIntentos || segundos <= 0) return 0;
                return Math.max(1, (long) Math.ceil(segundos / 60.0));
            }
        }
    }

    /** Counts one failure; a failure after the window expired starts a new one. */
    public void registrarFallo(String username, long ventanaMs) throws SQLException {
        String sql = """
            INSERT INTO login_attempts (username, intentos, ventana_inicio) VALUES (?, 1, NOW())
            ON CONFLICT (username) DO UPDATE SET
                intentos = CASE WHEN login_attempts.ventana_inicio + make_interval(secs => ?) < NOW()
                                THEN 1 ELSE login_attempts.intentos + 1 END,
                ventana_inicio = CASE WHEN login_attempts.ventana_inicio + make_interval(secs => ?) < NOW()
                                THEN NOW() ELSE login_attempts.ventana_inicio END
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setDouble(2, ventanaMs / 1000.0);
            ps.setDouble(3, ventanaMs / 1000.0);
            ps.executeUpdate();
        }
    }

    public void limpiar(String username) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM login_attempts WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }
}
