package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
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
        try {
            Optional<Usuario> opt = usuarioRepo.findByUsername(username.trim().toLowerCase());
            if (opt.isEmpty()) {
                throw new AuthException("Usuario o contraseña incorrectos");
            }
            Usuario user = opt.get();
            if (DatabaseConfig.isDemoMode()) {
                if (!DemoDataStore.verifyDemoPassword(username.trim().toLowerCase(), password)) {
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
