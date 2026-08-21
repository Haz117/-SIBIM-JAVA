package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Usuario;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.util.Optional;

public class AuthService {

    private final UsuarioRepository usuarioRepo = new UsuarioRepository();

    /**
     * Authenticates user credentials. Returns the user on success.
     * @throws AuthException if credentials are invalid or DB error occurs.
     */
    public Usuario login(String username, String password) throws AuthException {
        String uname = username.trim().toLowerCase();
        try {
            // Users aren't part of the offline sync scope (see the
            // offline-mode plan), but login still has to work — verifies
            // against a local read-only cache populated the last time this
            // user logged in while actually online (see the cacheUser call
            // below). UsuarioRepository itself has no offline branch: it
            // would otherwise fall through to its real-Postgres path here
            // and fail outright since there's no connection.
            if (DatabaseConfig.isOfflineMode()) {
                Optional<Usuario> cached = OfflineStore.findCachedUserByUsername(uname);
                if (cached.isEmpty()) {
                    throw new AuthException("No hay conexión y esta cuenta nunca inició sesión en esta PC estando "
                        + "en línea, así que no se puede verificar sin conexión.");
                }
                Usuario user = cached.get();
                BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
                if (!result.verified) throw new AuthException("Usuario o contraseña incorrectos");
                SessionManager.setCurrentUser(user);
                return user;
            }

            Optional<Usuario> opt = usuarioRepo.findByUsername(uname);
            if (opt.isEmpty()) {
                throw new AuthException("Usuario o contraseña incorrectos");
            }
            Usuario user = opt.get();
            if (DatabaseConfig.isDemoMode()) {
                if (!DemoDataStore.verifyDemoPassword(uname, password)) {
                    throw new AuthException("Usuario o contraseña incorrectos");
                }
            } else {
                if (user.getPasswordHash() == null) {
                    throw new AuthException("La cuenta no tiene contraseña establecida. Contacta al administrador.");
                }
                BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
                if (!result.verified) {
                    throw new AuthException("Usuario o contraseña incorrectos");
                }
                try {
                    OfflineStore.cacheUser(user);
                } catch (SQLException ex) {
                    // Non-fatal: worst case this user can't log in offline
                    // later if this specific write failed; the real login
                    // that's actually happening right now still succeeds.
                }
            }
            SessionManager.setCurrentUser(user);
            return user;
        } catch (SQLException e) {
            throw new AuthException("Error de conexion a la base de datos: " + e.getMessage());
        }
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
