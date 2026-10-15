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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        Set<String> tomados = new HashSet<>();
        for (AreaCatalog.Entrada e : Areas.catalogo().entradas()) {
            String usuario = nombreUsuario(e.nombre());
            Rol rol = rolDe(e);
            Optional<Usuario> existente = usuarios.findByUsername(usuario);
            // Two áreas can shorten alike, and a personal account may already
            // have the name: then it isn't this área's account. The prefix
            // tells them apart (it is unique per área).
            if (!tomados.add(usuario) || existente.filter(u -> !e.nombre().equals(u.getArea())).isPresent()) {
                usuario = usuario + "." + e.prefijo().toLowerCase(Locale.ROOT);
                tomados.add(usuario);
                existente = usuarios.findByUsername(usuario);
                if (existente.filter(u -> !e.nombre().equals(u.getArea())).isPresent())
                    throw new IllegalStateException("El usuario \"" + usuario + "\" ya es de otra área ("
                        + existente.get().getArea() + "); cámbiale el nombre de usuario y vuelve a intentarlo");
            }
            if (existente.isPresent()) {
                resultado.add(new Cuenta(e.nombre(), usuario, null, existente.get().getRol(), false));
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

    /** "Dirección de Catastro" → "catastro", "Servicios Públicos y Limpias" → "servicios.publicos".
     *  A sub-área ("Seguridad Pública — C-2") keeps what sets it apart from
     *  its siblings: "seguridad.c2", not the "seguridad.publica" they all share. */
    static String nombreUsuario(String area) {
        String fijo = NOMBRES.get(area);
        if (fijo != null) return fijo;
        String[] partes = area.split("\\s+[—–-]\\s+", 2);
        if (partes.length == 2) {
            List<String> cabeza = palabras(partes[0]), resto = palabras(partes[1]);
            if (!cabeza.isEmpty() && !resto.isEmpty())
                return cabeza.get(0) + "." + String.join(".", resto.subList(0, Math.min(2, resto.size())));
        }
        List<String> base = palabras(area);
        return String.join(".", base.subList(0, Math.min(2, base.size())));
    }

    /** The words that mean something, accents and case removed ("C-2" is one word: "c2"). */
    private static List<String> palabras(String texto) {
        String plano = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("(?<=[a-z0-9])-(?=[a-z0-9])", "");
        List<String> todas = Arrays.stream(plano.split("[^a-z0-9]+")).filter(p -> !p.isEmpty()).toList();
        List<String> utiles = todas.stream().filter(p -> !SIN_SIGNIFICADO.contains(p)).toList();
        return utiles.isEmpty() ? todas : utiles;
    }

    /** Letters that can't be confused when read aloud or handwritten (no vowels, l or q). */
    private static final String LETRAS = "bcdfghjkmnprstvwxyz";

    /** "catastro" → "Catastro-kdmt-4821": still easy to dictate. The word is
     *  the user name, so only the rest counts: four digits alone were 10 000
     *  combinations, minutes of work for whoever can read the stored hash. */
    static String contrasena(String usuario) {
        String palabra = usuario.split("\\.")[0];
        palabra = Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1);
        StringBuilder letras = new StringBuilder();
        for (int i = 0; i < 4; i++) letras.append(LETRAS.charAt(RANDOM.nextInt(LETRAS.length())));
        return palabra + "-" + letras + "-" + String.format("%04d", RANDOM.nextInt(10_000));
    }
}
