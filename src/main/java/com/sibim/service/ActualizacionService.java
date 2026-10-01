package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.UpdateChecker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * New versions of SIBIM travel through the database (V27), not GitHub: the
 * installer carries the connection settings and the repository is public.
 * Patrimonio publishes the installer once; every other PC finds it at
 * startup, downloads it in parts, checks its SHA-256 and runs it.
 */
public class ActualizacionService {

    /** 5 MB per row: a ~110 MB installer never has to fit in memory whole. */
    static final int PARTE = 5 * 1024 * 1024;

    /** Published installers kept in the database; older ones are deleted. */
    static final int CONSERVAR = 2;

    private static final Pattern VERSION_EN_NOMBRE = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");

    public record Version(String version, String archivo, long tamano, String sha256, String notas, int partes) {}

    /** The newest published version, if it is newer than this build. Empty
     *  offline, in demo mode or when nothing newer was published. */
    public Optional<Version> disponible() throws SQLException {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return Optional.empty();
        Optional<Version> ultima = ultima();
        return ultima.filter(v -> UpdateChecker.isNewer(v.version(), UpdateChecker.currentVersion()));
    }

    Optional<Version> ultima() throws SQLException {
        Version mejor = null;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT version, archivo, tamano, sha256, notas, partes FROM actualizaciones");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Version v = new Version(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                    rs.getString(5), rs.getInt(6));
                if (mejor == null || UpdateChecker.isNewer(v.version(), mejor.version())) mejor = v;
            }
        }
        return Optional.ofNullable(mejor);
    }

    /** "SIBIM-Desktop-1.2.0-win64-setup.exe" → "1.2.0". */
    public static Optional<String> versionDelArchivo(String nombre) {
        Matcher m = VERSION_EN_NOMBRE.matcher(nombre == null ? "" : nombre);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Uploads an installer as version {@code version} (admin only). */
    public void publicar(File instalador, String version, String notas, DoubleConsumer avance)
            throws SQLException, IOException {
        if (!com.sibim.session.Permisos.publicaActualizaciones())
            throw new SecurityException("Solo el administrador publica actualizaciones");
        if (DatabaseConfig.isOfflineMode()) throw new IllegalStateException("Publicar requiere conexión con el servidor");
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+"))
            throw new IllegalArgumentException("La versión debe ser como 1.2.0");
        Optional<Version> ultima = ultima();
        if (ultima.isPresent() && !UpdateChecker.isNewer(version, ultima.get().version()))
            throw new IllegalArgumentException("Ya está publicada la versión " + ultima.get().version()
                + ": la nueva debe ser mayor (sube <version> en pom.xml y vuelve a generar el instalador)");
        long tamano = instalador.length();
        int partes = (int) ((tamano + PARTE - 1) / PARTE);
        String sha = sha256(instalador);
        var yo = SessionManager.getCurrentUser();
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO actualizaciones "
                        + "(version, archivo, tamano, sha256, notas, partes, publicado_por) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setString(1, version);
                    ps.setString(2, instalador.getName());
                    ps.setLong(3, tamano);
                    ps.setString(4, sha);
                    ps.setString(5, notas);
                    ps.setInt(6, partes);
                    ps.setString(7, yo != null ? yo.getNombre() : null);
                    ps.executeUpdate();
                }
                byte[] buffer = new byte[PARTE];
                try (InputStream in = Files.newInputStream(instalador.toPath());
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO actualizacion_partes (version, n, datos) VALUES (?,?,?)")) {
                    for (int n = 0; n < partes; n++) {
                        int leidos = in.readNBytes(buffer, 0, PARTE);
                        ps.setString(1, version);
                        ps.setInt(2, n);
                        ps.setBytes(3, leidos == PARTE ? buffer : java.util.Arrays.copyOf(buffer, leidos));
                        ps.executeUpdate();
                        if (avance != null) avance.accept((n + 1) / (double) partes);
                    }
                }
                // Each installer is ~110 MB: keep this version and the one before it.
                try (PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM actualizaciones WHERE version NOT IN "
                         + "(SELECT version FROM actualizaciones ORDER BY publicado_en DESC LIMIT " + CONSERVAR + ")")) {
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | IOException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        new com.sibim.repository.AuditLogRepository().log("sistema", version, "Actualización " + version,
            "publicar", "Instalador publicado: " + instalador.getName());
    }

    /** Downloads {@code v} to {@code destino} and checks it arrived intact. */
    public void descargar(Version v, File destino, DoubleConsumer avance) throws SQLException, IOException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT datos FROM actualizacion_partes WHERE version = ? AND n = ?");
             OutputStream out = Files.newOutputStream(destino.toPath())) {
            for (int n = 0; n < v.partes(); n++) {
                ps.setString(1, v.version());
                ps.setInt(2, n);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IOException("Falta la parte " + (n + 1) + " de la actualización " + v.version());
                    out.write(rs.getBytes(1));
                }
                if (avance != null) avance.accept((n + 1) / (double) v.partes());
            }
        }
        if (!sha256(destino).equalsIgnoreCase(v.sha256())) {
            Files.deleteIfExists(destino.toPath());
            throw new IOException("La actualización se descargó incompleta o dañada; vuelve a intentarlo");
        }
    }

    static String sha256(File f) throws IOException {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
