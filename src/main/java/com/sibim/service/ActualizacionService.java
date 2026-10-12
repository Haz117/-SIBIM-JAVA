package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * New versions of SIBIM travel through the database (V27), not GitHub: the
 * installer carries the connection settings and the repository is public.
 * Patrimonio publishes the installer once; every other PC finds it at
 * startup, downloads it in parts, checks its SHA-256 and runs it.
 *
 * Every PC can write to the database, so a version is only offered and
 * installed if TI signed it (V31, see {@link FirmaActualizaciones}); rows
 * without a valid signature are ignored as if they weren't there.
 */
public class ActualizacionService {

    private static final Logger log = LoggerFactory.getLogger(ActualizacionService.class);

    /** 5 MB per row: a ~110 MB installer never has to fit in memory whole. */
    static final int PARTE = 5 * 1024 * 1024;

    /** Published installers kept in the database; older ones are deleted. */
    static final int CONSERVAR = 2;

    public record Version(String version, String archivo, long tamano, String sha256, String notas, int partes,
                          String firma) {}

    private final FirmaActualizaciones firmas;

    public ActualizacionService() { this(FirmaActualizaciones.deEstaVersion()); }

    public ActualizacionService(FirmaActualizaciones firmas) { this.firmas = firmas; }

    /** The newest published version, if it is newer than this build. Empty
     *  offline, in demo mode or when nothing newer was published. */
    public Optional<Version> disponible() throws SQLException {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return Optional.empty();
        Optional<Version> ultima = ultima();
        return ultima.filter(v -> UpdateChecker.isNewer(v.version(), UpdateChecker.currentVersion()));
    }

    /** The newest version TI signed. */
    Optional<Version> ultima() throws SQLException {
        Version mejor = null;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT version, archivo, tamano, sha256, notas, partes, firma FROM actualizaciones");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Version v = new Version(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                    rs.getString(5), rs.getInt(6), rs.getString(7));
                if (!firmada(v)) {
                    log.warn("Actualización {} sin firma válida de TI: se ignora", v.version());
                    continue;
                }
                if (mejor == null || UpdateChecker.isNewer(v.version(), mejor.version())) mejor = v;
            }
        }
        return Optional.ofNullable(mejor);
    }

    private boolean firmada(Version v) {
        return firmas.valida(v.version(), v.sha256(), v.firma());
    }

    /** Where the downloaded installer goes: named from the signed version,
     *  never from the file name stored next to it. */
    public static String nombreLocal(Version v) {
        String ext = v.archivo() != null && v.archivo().toLowerCase().endsWith(".msi") ? ".msi" : ".exe";
        return "SIBIM-Desktop-" + v.version() + "-setup" + ext;
    }

    /** The signature build-installer.ps1 left next to {@code instalador},
     *  checked here so nobody uploads 110 MB that no PC would accept. */
    public FirmaActualizaciones.Firma firmaDe(File instalador) throws IOException {
        File archivo = FirmaActualizaciones.archivoFirma(instalador);
        if (!firmas.tieneClave())
            throw new IllegalStateException("Esta versión de SIBIM no trae la clave para comprobar firmas: "
                + "instala una versión generada en la PC de TI");
        if (!archivo.isFile())
            throw new IllegalArgumentException("Falta " + archivo.getName() + " junto al instalador. Se genera al "
                + "crear el instalador con packaging\\build-installer.ps1 en la PC de TI (la que tiene la llave de firma).");
        FirmaActualizaciones.Firma f = FirmaActualizaciones.leer(archivo);
        if (!firmas.valida(f.version(), f.sha256(), f.firma()))
            throw new IllegalArgumentException("La firma de " + instalador.getName() + " no es válida: "
                + "no la generó TI o es de otra compilación");
        if (!sha256(instalador).equals(f.sha256()))
            throw new IllegalArgumentException(instalador.getName() + " no es el instalador que se firmó "
                + "(cambió después): vuelve a generarlo");
        return f;
    }

    /** Uploads a signed installer (admin only); the version comes from its
     *  signature. Returns that version. */
    public String publicar(File instalador, String notas, DoubleConsumer avance) throws SQLException, IOException {
        if (!com.sibim.session.Permisos.publicaActualizaciones())
            throw new SecurityException("Solo el administrador publica actualizaciones");
        if (DatabaseConfig.isOfflineMode()) throw new IllegalStateException("Publicar requiere conexión con el servidor");
        FirmaActualizaciones.Firma firma = firmaDe(instalador);
        String version = firma.version();
        Optional<Version> ultima = ultima();
        if (ultima.isPresent() && !UpdateChecker.isNewer(version, ultima.get().version()))
            throw new IllegalArgumentException("Ya está publicada la versión " + ultima.get().version()
                + ": la nueva debe ser mayor (sube <version> en pom.xml y vuelve a generar el instalador)");
        long tamano = instalador.length();
        int partes = (int) ((tamano + PARTE - 1) / PARTE);
        var yo = SessionManager.getCurrentUser();
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO actualizaciones "
                        + "(version, archivo, tamano, sha256, notas, partes, publicado_por, firma) "
                        + "VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, version);
                    ps.setString(2, instalador.getName());
                    ps.setLong(3, tamano);
                    ps.setString(4, firma.sha256());
                    ps.setString(5, notas);
                    ps.setInt(6, partes);
                    ps.setString(7, yo != null ? yo.getNombre() : null);
                    ps.setString(8, firma.firma());
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
                // 42501: this PC connects with the data-only role, which may
                // only read the updates (scripts/sql/rol_app_minimo.sql).
                if (e instanceof SQLException se && "42501".equals(se.getSQLState()))
                    throw new IllegalStateException("Las actualizaciones se publican desde la computadora de "
                        + "administración (la que se conecta con el usuario dueño de la base).");
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // rol_app_minimo.sql leaves the installers to the database owner.
            if ("42501".equals(e.getSQLState()))
                throw new SQLException("Esta PC entra con el usuario sibim_app, que no puede publicar actualizaciones. "
                    + "Publícala desde la PC de administración (la que entra con el usuario dueño de la base).",
                    e.getSQLState(), e);
            throw e;
        }
        new com.sibim.repository.AuditLogRepository().log("sistema", version, "Actualización " + version,
            "publicar", "Instalador publicado: " + instalador.getName());
        return version;
    }

    /** Downloads {@code v} to {@code destino} and checks it is exactly the
     *  installer TI signed. */
    public void descargar(Version v, File destino, DoubleConsumer avance) throws SQLException, IOException {
        if (!firmada(v)) throw new IOException("La versión " + v.version() + " no trae la firma de TI: no se instala");
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
        if (!sha256(destino).equals(v.sha256())) {
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
