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

    /** {@link AreaRepository#firma()} of the catalog in use, so the periodic
     *  check only reloads when another PC changed the table. */
    private static volatile String firmaCargada;

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
            recargar();
        } catch (Exception e) {
            log.warn("No se pudieron leer las áreas de la base; se usa la lista integrada: {}", e.getMessage());
            AreaCatalog cache = leerCache();
            if (cache != null) Areas.usar(cache);
        }
    }

    /**
     * Picks up areas added or edited on another PC. Called by the connectivity
     * watcher (SyncService) on every online tick; one tiny query when nothing
     * changed. Never throws.
     * @return true when the catalog was reloaded
     */
    public boolean refrescarSiCambio() {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return false;
        try {
            String firma = repo.firma();
            if (firma.equals(firmaCargada)) return false;
            recargar();
            log.info("Catálogo de áreas actualizado desde la base de datos");
            return true;
        } catch (Exception e) {
            log.debug("No se pudo revisar la tabla de áreas: {}", e.getMessage());
            return false;
        }
    }

    private void recargar() throws SQLException {
        String firma = repo.firma();
        AreaCatalog c = repo.cargar();
        Areas.usar(c);
        guardarCache(c);
        firmaCargada = firma;
    }

    /**
     * Adds an area or changes an existing one's group, parent or prefix.
     * @throws IllegalArgumentException when the result would not be a valid organigrama
     *         (duplicate prefix, dirección without a valid parent…).
     */
    public void guardar(AreaCatalog.Entrada cambio) throws SQLException {
        guardar(cambio, false);
    }

    /**
     * Moves a dirección under another parent (Presidencia or a secretaría),
     * keeping its prefix. In demo mode the change only lives in memory, like
     * everything else there.
     */
    public void moverDireccion(String nombre, String nuevoPadre) throws SQLException {
        AreaCatalog.Entrada actual = Areas.catalogo().buscar(nombre)
            .orElseThrow(() -> new IllegalArgumentException("No existe el área \"" + nombre + "\""));
        if (actual.grupo() != AreaCatalog.Grupo.DIRECCION)
            throw new IllegalArgumentException("Solo una dirección puede cambiar de dependencia");
        AreaCatalog.Entrada cambio = new AreaCatalog.Entrada(nombre, AreaCatalog.Grupo.DIRECCION,
            nuevoPadre, actual.prefijo());
        if (DatabaseConfig.isDemoMode()) {
            if (!SessionManager.isAdmin())
                throw new IllegalStateException("Solo un administrador puede editar las áreas");
            Areas.usar(Areas.catalogo().con(cambio));
            return;
        }
        guardar(cambio);
    }

    /** Active bienes that would be renumbered if {@code area}'s prefix changed now. */
    public int bienesConPrefijoActual(String area) throws SQLException {
        return Areas.catalogo().buscar(area)
            .map(e -> { try { return repo.contarBienesConPrefijo(area, e.prefijo()); }
                        catch (SQLException ex) { throw new RuntimeException(ex); } })
            .orElse(0);
    }

    /**
     * @param renumerar when the prefix changes, also move the area's active bienes
     *        to the new prefix keeping their number (TICS/05 → TI/05)
     * @return bienes renumbered
     */
    public int guardar(AreaCatalog.Entrada cambio, boolean renumerar) throws SQLException {
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

        var anterior = Areas.catalogo().buscar(nombre);
        boolean nueva = anterior.isEmpty();
        AreaCatalog resultante = Areas.catalogo().con(limpia);   // validates the whole organigrama
        String prefijoAnterior = anterior.map(AreaCatalog.Entrada::prefijo).orElse(null);
        int renumerados;
        try {
            renumerados = repo.guardar(limpia, renumerar ? prefijoAnterior : null);
        } catch (SQLException e) {
            if (!"23505".equals(e.getSQLState())) throw e;
            throw new IllegalStateException(renumerar
                ? "Otro bien activo ya usa alguno de los códigos " + prefijo + "/…, o el prefijo lo tomó otra área. No se cambió nada."
                : "El prefijo " + prefijo + " ya lo usa otra área. No se cambió nada.", e);
        }
        if (renumerados > 0) ProductosEnMemoria.invalidar();
        Areas.usar(resultante);
        guardarCache(resultante);
        try { firmaCargada = repo.firma(); } catch (SQLException ignored) { firmaCargada = null; }
        auditRepo.log("area", nombre, nombre, nueva ? "crear" : "editar",
            "Prefijo " + prefijo + (limpia.padre() != null ? " · depende de " + limpia.padre() : "")
                + (renumerados > 0 ? " · " + renumerados + " bienes renumerados desde " + prefijoAnterior : ""));
        return renumerados;
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
