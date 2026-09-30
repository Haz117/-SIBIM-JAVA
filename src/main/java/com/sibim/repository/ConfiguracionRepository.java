package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class ConfiguracionRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionRepository.class);

    private static final Map<String, String> DEMO_VALUES = new LinkedHashMap<>();
    static {
        DEMO_VALUES.put("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
        DEMO_VALUES.put("municipio",           "Ixmiquilpan, Hidalgo");
        DEMO_VALUES.put("responsable",         "Dirección de Bienes Patrimoniales");
        DEMO_VALUES.put("correo_contacto",     "");
    }

    /** Never written to this PC's disk. */
    private static final Set<String> NO_COPIAR = Set.of("smtp_password");

    /** Last values read from the server, so offline the formatos still carry
     *  the municipality's name and logo instead of the defaults. */
    private static Map<String, String> copiaLocal;

    public Map<String, String> findAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return new LinkedHashMap<>(DEMO_VALUES);
        if (DatabaseConfig.isOfflineMode()) return new LinkedHashMap<>(copiaLocal());
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT clave, valor FROM configuracion ORDER BY clave";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.put(rs.getString("clave"), rs.getString("valor"));
        }
        guardarCopiaLocal(result);
        return result;
    }

    public String get(String clave, String defaultValue) {
        try {
            if (DatabaseConfig.isDemoMode()) return DEMO_VALUES.getOrDefault(clave, defaultValue);
            if (DatabaseConfig.isOfflineMode()) {
                String v = copiaLocal().get(clave);
                return v != null ? v : defaultValue;
            }
            String sql = "SELECT valor FROM configuracion WHERE clave = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String v = rs.getString("valor");
                        return v != null ? v : defaultValue;
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("No se pudo leer la configuración '{}', usando valor por defecto", clave, e);
        }
        return defaultValue;
    }

    public void set(String clave, String valor) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DEMO_VALUES.put(clave, valor); return; }
        DatabaseConfig.exigirServidor("Guardar la configuración");
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?) ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor != null ? valor : "");
            ps.executeUpdate();
        }
        Map<String, String> copia = new LinkedHashMap<>(copiaLocal());
        copia.put(clave, valor != null ? valor : "");
        guardarCopiaLocal(copia);
    }

    /**
     * Sets {@code clave} to {@code valor} only if it holds something else, in one
     * statement — so when several PCs race (e.g. the 06:00 scheduled reports)
     * exactly one of them gets {@code true} and does the work.
     */
    public boolean reclamar(String clave, String valor) throws SQLException {
        DatabaseConfig.exigirServidor("Coordinar tareas programadas");
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?) ON CONFLICT (clave) "
            + "DO UPDATE SET valor = EXCLUDED.valor WHERE configuracion.valor IS DISTINCT FROM EXCLUDED.valor";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            return ps.executeUpdate() == 1;
        }
    }

    // ── Copy on this PC (%USERPROFILE%\.sibim\configuracion.properties) ──────

    static Path archivoCopia() {
        return Path.of(System.getProperty("user.home"), ".sibim", "configuracion.properties");
    }

    private static synchronized Map<String, String> copiaLocal() {
        if (copiaLocal != null) return copiaLocal;
        Map<String, String> m = new LinkedHashMap<>();
        Path f = archivoCopia();
        if (Files.exists(f)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
                p.stringPropertyNames().forEach(k -> m.put(k, p.getProperty(k)));
            } catch (IOException e) {
                log.warn("No se pudo leer la copia local de la configuración: {}", e.getMessage());
            }
        }
        copiaLocal = m;
        return m;
    }

    private static synchronized void guardarCopiaLocal(Map<String, String> valores) {
        Map<String, String> m = new LinkedHashMap<>(valores);
        NO_COPIAR.forEach(m::remove);
        copiaLocal = m;
        Properties p = new Properties();
        m.forEach((k, v) -> p.setProperty(k, v != null ? v : ""));
        Path f = archivoCopia();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "SIBIM: copia de la configuración para trabajar sin conexión");
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("No se pudo guardar la copia local de la configuración: {}", e.getMessage());
        }
    }

    /** Tests only: forget the in-memory copy so the next read goes to disk. */
    static synchronized void olvidarCopiaEnMemoria() { copiaLocal = null; }
}
