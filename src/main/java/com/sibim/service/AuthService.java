package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Usuario;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private final UsuarioRepository usuarioRepo = new UsuarioRepository();
    private final AuditLogRepository auditRepo = new AuditLogRepository();

    private static final int    MAX_INTENTOS = 5;
    private static final long   VENTANA_MS   = 15 * 60_000L; // 15 minutos
    // username → [intentos_fallidos, timestamp_primer_fallo_de_la_ventana]
    private static final ConcurrentHashMap<String, long[]> fallidos = new ConcurrentHashMap<>();

    /**
     * Authenticates user credentials. Returns the user on success.
     * @throws AuthException if credentials are invalid, locked out, or DB error occurs.
     */
    public Usuario login(String username, String password) throws AuthException {
        String key = username.trim().toLowerCase();

        // ── Rate-limit check ─────────────────────────────────────────────
        long[] estado = fallidos.get(key);
        if (estado != null) {
            long ahora = System.currentTimeMillis();
            if (ahora - estado[1] < VENTANA_MS && estado[0] >= MAX_INTENTOS) {
                long mins = Math.max(1, (VENTANA_MS - (ahora - estado[1])) / 60_000 + 1);
                throw new AuthException(
                    "Demasiados intentos fallidos. Espera " + mins + " minuto(s) antes de volver a intentar.");
            } else if (ahora - estado[1] > VENTANA_MS) {
                fallidos.remove(key); // ventana expirada — reiniciar
            }
        }

        try {
            Optional<Usuario> opt = usuarioRepo.findByUsername(key);
            if (opt.isEmpty()) {
                registrarFallo(key);
                throw new AuthException("Usuario o contraseña incorrectos");
            }
            Usuario user = opt.get();
            boolean credencialesOk;
            if (DatabaseConfig.isDemoMode()) {
                credencialesOk = DemoDataStore.verifyDemoPassword(key, password);
            } else {
                if (user.getPasswordHash() == null) {
                    throw new AuthException("La cuenta no tiene contraseña establecida. Contacta al administrador.");
                }
                credencialesOk = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash()).verified;
            }
            if (!credencialesOk) {
                registrarFallo(key);
                throw new AuthException("Usuario o contraseña incorrectos");
            }
            fallidos.remove(key); // login exitoso — limpiar contadores
            SessionManager.setCurrentUser(user);
            auditRepo.log("sesion", user.getId(), user.getNombre(), "login",
                "Inicio de sesión — " + (DatabaseConfig.isDemoMode() ? "modo demo" : "base de datos"));
            return user;
        } catch (SQLException e) {
            throw new AuthException("No se pudo conectar al servidor. Verifica la conexión a la base de datos.");
        }
    }

    private static void registrarFallo(String key) {
        long ahora = System.currentTimeMillis();
        fallidos.compute(key, (k, v) -> {
            if (v == null || ahora - v[1] > VENTANA_MS) return new long[]{1, ahora};
            v[0]++;
            return v;
        });
    }

    public void logout() {
        SessionManager.logout();
    }

    public String hashPassword(String plainPassword) {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
    }

    public static class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }
}
