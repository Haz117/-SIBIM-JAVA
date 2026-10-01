package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One generic account per área of the organigrama, shared by everyone in that
 * área on any number of PCs (SIBIM doesn't limit simultaneous sessions).
 * Recursos Materiales y Patrimonio gets the admin role (it runs the
 * inventory); Presidencia and the secretarías get secretario (they see their
 * direcciones); direcciones and organismos autónomos get dirección. Accounts
 * that already exist are left untouched, so it can be run again after adding
 * áreas or after reloading the database.
 */
public class CuentasAreaService {

    public record Cuenta(String area, String usuario, String contrasena, Rol rol, boolean nueva) {}

    /** Marks the shared accounts: at login they ask who is using them (see LoginController). */
    public static final String CARGO_COMPARTIDA = "Cuenta compartida del área";

    /** Área that runs the inventory — its account is an administrator. */
    static final String PATRIMONIO = "Recursos Materiales y Patrimonio";

    /** Where the automatic name reads poorly. */
    private static final Map<String, String> NOMBRES = Map.of(
        "Secretaría General Municipal", "secretaria.general",
        PATRIMONIO, "patrimonio",
        "Tesorería — Recursos Humanos y Nómina", "tesoreria.nomina",
        "Oficialía del Registro del Estado Familiar", "registro.familiar",
        "Oficialía Mayor de la Asamblea", "oficialia.mayor",
        "Dirección de Tecnologías de la Información", "tecnologias");

    private static final Set<String> SIN_SIGNIFICADO = Set.of("de", "del", "la", "las", "los", "y", "el", "al",
        "municipal", "direccion", "secretaria", "instancia", "coordinacion", "oficialia", "unidad", "despacho");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final AuditLogRepository audit;

    public CuentasAreaService() { this(new UsuarioRepository(), new AuditLogRepository()); }
    CuentasAreaService(UsuarioRepository usuarios, AuditLogRepository audit) {
        this.usuarios = usuarios;
        this.audit = audit;
    }

    /** Creates the accounts that don't exist yet; returns every área's account,
     *  with the password only for the ones just created. */
    public List<Cuenta> crearFaltantes() throws SQLException {
        if (!SessionManager.isAdmin()) throw new SecurityException("Solo el administrador crea cuentas");
        if (DatabaseConfig.isOfflineMode())
            throw new IllegalStateException("Crear cuentas requiere conexión con el servidor");
        List<Cuenta> resultado = new ArrayList<>();
        for (AreaCatalog.Entrada e : Areas.catalogo().entradas()) {
            String usuario = nombreUsuario(e.nombre());
            Rol rol = rolDe(e);
            if (usuarios.findByUsername(usuario).isPresent()) {
                resultado.add(new Cuenta(e.nombre(), usuario, null, rol, false));
                continue;
            }
            String contrasena = contrasena(usuario);
            Usuario u = new Usuario();
            u.setUsername(usuario);
            u.setNombre(e.nombre());
            u.setCargo(CARGO_COMPARTIDA);
            u.setRol(rol);
            u.setArea(e.nombre());
            u.setActivo(true);
            // Shared by several people: forcing a change at first login would
            // lock out everyone else who was handed the original password.
            u.setDebeCambiarPassword(false);
            u.setPasswordHash(BCrypt.withDefaults().hashToString(12, contrasena.toCharArray()));
            usuarios.save(u);
            resultado.add(new Cuenta(e.nombre(), usuario, contrasena, rol, true));
        }
        long nuevas = resultado.stream().filter(Cuenta::nueva).count();
        if (nuevas > 0)
            audit.log("usuario", null, "Cuentas por área", "crear", nuevas + " cuenta(s) de área creadas");
        return resultado;
    }

    static Rol rolDe(AreaCatalog.Entrada e) {
        if (PATRIMONIO.equals(e.nombre())) return Rol.ADMIN;
        return switch (e.grupo()) {
            case PRESIDENCIA, SECRETARIA -> Rol.SECRETARIO;
            case DIRECCION, AUTONOMO -> Rol.DIRECCION;
        };
    }

    /** "Dirección de Catastro" → "catastro", "Servicios Públicos y Limpias" → "servicios.publicos". */
    static String nombreUsuario(String area) {
        String fijo = NOMBRES.get(area);
        if (fijo != null) return fijo;
        String plano = Normalizer.normalize(area, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        List<String> palabras = Arrays.stream(plano.split("[^a-z0-9]+")).filter(p -> !p.isEmpty()).toList();
        List<String> utiles = palabras.stream().filter(p -> !SIN_SIGNIFICADO.contains(p)).toList();
        List<String> base = utiles.isEmpty() ? palabras : utiles;
        return String.join(".", base.subList(0, Math.min(2, base.size())));
    }

    /** "catastro" → "Catastro-4821": easy to dictate, 8+ characters. */
    static String contrasena(String usuario) {
        String palabra = usuario.split("\\.")[0];
        palabra = Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1);
        return palabra + "-" + String.format("%04d", RANDOM.nextInt(10_000));
    }
}
