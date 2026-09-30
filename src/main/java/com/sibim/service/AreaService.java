package com.sibim.service;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.repository.AreaRepository;
import com.sibim.repository.AuditLogRepository;
import com.sibim.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Loads the areas table into {@link Areas} and applies edits to it. The last
 * catalog read from the database is also kept on this PC, so offline mode
 * still knows about areas added after the app was built.
 */
public class AreaService {

    private static final Logger log = LoggerFactory.getLogger(AreaService.class);
    private static final String CACHE_NODE = "sibim/areas";
    private static final String CACHE_KEY  = "catalogo";

    private final AreaRepository repo;
    private final AuditLogRepository auditRepo;

    public AreaService() { this(new AreaRepository(), new AuditLogRepository()); }
    AreaService(AreaRepository repo, AuditLogRepository auditRepo) {
        this.repo = repo;
        this.auditRepo = auditRepo;
    }

    /** Called once the database is (or isn't) available at startup. Never throws:
     *  on any problem the built-in catalog stays in use. */
    public void cargarCatalogo() {
        if (DatabaseConfig.isDemoMode()) return;
        if (DatabaseConfig.isOfflineMode()) {
            AreaCatalog cache = leerCache();
            if (cache != null) Areas.usar(cache);
            return;
        }
        try {
            AreaCatalog c = repo.cargar();
            Areas.usar(c);
            guardarCache(c);
        } catch (Exception e) {
            log.warn("No se pudieron leer las áreas de la base; se usa la lista integrada: {}", e.getMessage());
            AreaCatalog cache = leerCache();
            if (cache != null) Areas.usar(cache);
        }
    }

    /**
     * Adds an area or changes an existing one's group, parent or prefix.
     * @throws IllegalArgumentException when the result would not be a valid organigrama
     *         (duplicate prefix, dirección without a valid parent…).
     */
    public void guardar(AreaCatalog.Entrada cambio) throws SQLException {
        if (!SessionManager.isAdmin())
            throw new IllegalStateException("Solo un administrador puede editar las áreas");
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode())
            throw new IllegalStateException("Las áreas solo se pueden editar con conexión a la base de datos");
        String nombre = cambio.nombre() == null ? "" : cambio.nombre().trim();
        String prefijo = cambio.prefijo() == null ? "" : cambio.prefijo().trim().toUpperCase();
        AreaCatalog.Entrada limpia = new AreaCatalog.Entrada(nombre,
            cambio.grupo(), cambio.grupo() == AreaCatalog.Grupo.DIRECCION ? cambio.padre() : null, prefijo);
        if (limpia.grupo() == AreaCatalog.Grupo.PRESIDENCIA
                && !Areas.PRESIDENCIA.equals(nombre))
            throw new IllegalArgumentException("Solo puede existir un Despacho de Presidencia");

        boolean nueva = Areas.catalogo().buscar(nombre).isEmpty();
        AreaCatalog resultante = Areas.catalogo().con(limpia);   // validates the whole organigrama
        repo.guardar(limpia);
        Areas.usar(resultante);
        guardarCache(resultante);
        auditRepo.log("area", nombre, nombre, nueva ? "crear" : "editar",
            "Prefijo " + prefijo + (limpia.padre() != null ? " · depende de " + limpia.padre() : ""));
    }

    // ── Local cache (one line per area: nombre \t grupo \t padre \t prefijo) ──

    private static void guardarCache(AreaCatalog c) {
        StringBuilder sb = new StringBuilder();
        for (AreaCatalog.Entrada e : c.entradas()) {
            sb.append(e.nombre()).append('\t').append(e.grupo().name()).append('\t')
              .append(e.padre() == null ? "" : e.padre()).append('\t').append(e.prefijo()).append('\n');
        }
        try {
            Preferences.userRoot().node(CACHE_NODE).put(CACHE_KEY, sb.toString());
        } catch (Exception e) {
            log.debug("No se pudo guardar la copia local de las áreas: {}", e.getMessage());
        }
    }

    static AreaCatalog leerCache() {
        try {
            String raw = Preferences.userRoot().node(CACHE_NODE).get(CACHE_KEY, null);
            if (raw == null || raw.isBlank()) return null;
            List<AreaCatalog.Entrada> entradas = new ArrayList<>();
            for (String line : raw.split("\n")) {
                String[] f = line.split("\t", -1);
                if (f.length != 4) return null;
                entradas.add(new AreaCatalog.Entrada(f[0], AreaCatalog.Grupo.valueOf(f[1]),
                    f[2].isEmpty() ? null : f[2], f[3]));
            }
            return new AreaCatalog(entradas);
        } catch (Exception e) {
            log.debug("Copia local de áreas ilegible: {}", e.getMessage());
            return null;
        }
    }
}
