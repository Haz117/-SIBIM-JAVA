package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Usuario;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.LoginAttemptRepository;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;

public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository  usuarioRepo;
    private final AuditLogRepository auditRepo;
    private final LoginAttemptRepository intentosRepo;

    public AuthService() { this(new UsuarioRepository(), new AuditLogRepository(), new LoginAttemptRepository()); }
    AuthService(UsuarioRepository usuarioRepo, AuditLogRepository auditRepo) {
        this(usuarioRepo, auditRepo, new LoginAttemptRepository());
    }
    AuthService(UsuarioRepository usuarioRepo, AuditLogRepository auditRepo, LoginAttemptRepository intentosRepo) {
        this.usuarioRepo  = usuarioRepo;
        this.auditRepo    = auditRepo;
        this.intentosRepo = intentosRepo;
    }

    private static final int  MAX_INTENTOS = 5;
    private static final long VENTANA_MS   = 15 * 60_000L; // 15 minutos

    public record LoginResult(Usuario user, String offlineWarning) {
        public boolean hasWarning() { return offlineWarning != null; }
    }

    /**
     * Authenticates user credentials. Returns a LoginResult on success.
     * @throws AuthException if credentials are invalid, locked out, or DB error occurs.
     */
    public LoginResult login(String username, String password) throws AuthException {
        if (username == null || username.isBlank())
            throw new AuthException("El usuario es obligatorio");
        if (password == null || password.isBlank())
            throw new AuthException("La contraseña es obligatoria");
        String key = username.trim().toLowerCase();
        try {
            // ── Rate-limit check (persisted across restarts) ─────────────────
            // Applies before both the offline and online paths below — the
            // offline branch reads a local BCrypt hash cache, so without this
            // check here it would be brute-forceable at unlimited speed
            // against that cached file with no lockout at all.
            long ahora = System.currentTimeMillis();
            long windowStart = AuthAttemptStore.getWindowStart(key);
            int  intentos    = AuthAttemptStore.getCount(key);
            if (windowStart > 0 && ahora - windowStart < VENTANA_MS && intentos >= MAX_INTENTOS) {
                long mins = Math.max(1, (VENTANA_MS - (ahora - windowStart)) / 60_000 + 1);
                throw new AuthException(
                    "Demasiados intentos fallidos. Espera " + mins + " minuto(s) antes de volver a intentar.");
            } else if (windowStart > 0 && ahora - windowStart > VENTANA_MS) {
                AuthAttemptStore.clear(key); // ventana expirada — reiniciar
            }

            if (DatabaseConfig.isOfflineMode()) return loginOffline(key, password);

            // Shared lockout (every PC): the local counter above only covers
            // this computer and lives in a file the user can delete.
            boolean compartido = !DatabaseConfig.isDemoMode();
            Optional<Usuario> opt;
            try {
                if (compartido) {
                    long minsCompartido = minutosBloqueoCompartido(key);
                    if (minsCompartido > 0) throw new AuthException(
                        "Demasiados intentos fallidos. Espera " + minsCompartido + " minuto(s) antes de volver a intentar.");
                }
                opt = usuarioRepo.findByUsername(key);
            } catch (SQLException | com.zaxxer.hikari.pool.HikariPool.PoolInitializationException e) {
                // PoolInitializationException: the pool had to start from scratch with no server.
                if (!esFallaDeConexion(e)) {
                    if (e instanceof SQLException se) throw se;
                    throw (RuntimeException) e;
                }
                // The connection dropped while the app was still online (the
                // login screen after a logout, or the session timeout): the
                // background watcher only notices on its next poll, so until
                // then every login failed with "no se pudo conectar". Go
                // offline now and check this PC's cached credentials instead.
                log.warn("Sin conexión con el servidor al iniciar sesión — cambiando a modo offline: {}", e.getMessage());
                DatabaseConfig.setOfflineMode(true);
                return loginOffline(key, password);
            }
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
            if (!DatabaseConfig.isDemoMode() && !user.isActivo()) {
                throw new AuthException("Esta cuenta ha sido desactivada. Contacta al administrador.");
            }
            AuthAttemptStore.clear(key); // login exitoso — limpiar contadores
            if (compartido) {
                try { intentosRepo.limpiar(key); }
                catch (SQLException e) { log.warn("No se pudo limpiar el contador de intentos de '{}'", key, e); }
            }
            SessionManager.setCurrentUser(user);
            auditRepo.log("sesion", user.getId(), user.getNombre(), "login",
                "Inicio de sesión — " + (DatabaseConfig.isDemoMode() ? "modo demo" : "base de datos"));
            String offlineWarning = null;
            if (!DatabaseConfig.isDemoMode()) {
                try {
                    OfflineStore.cacheUser(user);
                } catch (SQLException ex) {
                    log.error("No se pudo cachear usuario '{}' para modo offline: {}", user.getUsername(), ex.getMessage());
                    offlineWarning = "El acceso sin conexión no pudo guardarse. Si pierdes acceso a internet, "
                        + "no podrás iniciar sesión hasta reconectarte.";
                }
            }
            return new LoginResult(user, offlineWarning);
        } catch (SQLException e) {
            throw new AuthException("No se pudo conectar al servidor. Verifica la conexión a la base de datos.");
        }
    }

    /** Users aren't part of the offline sync scope, but login still has to
     *  work: it verifies against a local read-only cache populated the last
     *  time this user logged in while actually online (see cacheUser in
     *  login). UsuarioRepository has no offline branch of its own. */
    private LoginResult loginOffline(String key, String password) throws AuthException, SQLException {
        Optional<Usuario> cached = OfflineStore.findCachedUserByUsername(key);
        if (cached.isEmpty()) {
            throw new AuthException("No hay conexión. El acceso sin conexión requiere haber iniciado sesión "
                + "en esta PC con conexión activa en los últimos "
                + com.sibim.db.offline.OfflineUserCache.OFFLINE_CACHE_TTL_DAYS + " días.");
        }
        Usuario user = cached.get();
        if (!user.isActivo())
            throw new AuthException("Tu cuenta está desactivada. Contacta al administrador.");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            registrarFallo(key);
            throw new AuthException("Usuario o contraseña incorrectos");
        }
        AuthAttemptStore.clear(key); // login exitoso — limpiar contadores
        SessionManager.setCurrentUser(user);
        return new LoginResult(user, null);
    }

    /** No route to the server (no internet, DNS, pool timeout) as opposed to
     *  an error the server itself returned. SQLState class 08 is "connection
     *  exception"; HikariCP's pool timeout is a SQLTransientConnectionException. */
    static boolean esFallaDeConexion(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLTransientConnectionException
                || t instanceof java.sql.SQLNonTransientConnectionException
                || t instanceof java.net.UnknownHostException
                || t instanceof java.net.SocketException
                || t instanceof java.net.SocketTimeoutException) return true;
            if (t instanceof SQLException se && se.getSQLState() != null && se.getSQLState().startsWith("08")) return true;
        }
        return false;
    }

    /** Bumps the in-memory rate-limit counter AND writes an audit entry —
     *  before this, a failed login only ever touched AuthAttemptStore (purely
     *  in-memory, reset on every app restart), so a brute-force attempt or
     *  someone trying an ex-employee's account left no trace anywhere once
     *  the app closed. There's no real Usuario to attach the entry to when
     *  the username doesn't even exist, so the attempted username goes in
     *  as the entidad name instead, with a null id. */
    private void registrarFallo(String key) {
        AuthAttemptStore.increment(key);
        if (!DatabaseConfig.isOfflineMode() && !DatabaseConfig.isDemoMode()) {
            try { intentosRepo.registrarFallo(key, VENTANA_MS); }
            catch (SQLException e) { log.warn("No se pudo registrar el intento fallido de '{}' en el servidor", key, e); }
        }
        auditRepo.log("sesion", null, key, "login_fallido", "Intento de inicio de sesión fallido");
    }

    /** A failure to read the shared counter (e.g. the table isn't there yet)
     *  must not lock everyone out — the local counter still applies. */
    private long minutosBloqueoCompartido(String key) throws SQLException {
        try { return intentosRepo.minutosBloqueo(key, MAX_INTENTOS, VENTANA_MS); }
        catch (SQLException e) {
            if (esFallaDeConexion(e)) throw e; // no server at all: the caller goes offline
            log.warn("No se pudo consultar el contador de intentos de '{}' en el servidor", key, e);
            return 0;
        }
    }

    public void logout() {
        Usuario me = SessionManager.getCurrentUser();
        if (me != null) {
            try {
                auditRepo.log("sesion", me.getId(), me.getNombre(), "logout", "Cierre de sesión");
            } catch (Exception e) {
                log.warn("No se pudo registrar el cierre de sesión de {} en la bitácora", me.getNombre(), e);
            }
        }
        SessionManager.logout();
    }

    public String hashPassword(String plainPassword) {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
    }

    public static class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }
}
