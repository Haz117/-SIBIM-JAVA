package com.sibim.db.integration;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.ActualizacionService;
import com.sibim.service.FirmaActualizaciones;
import com.sibim.session.SessionManager;
import com.sibim.util.UpdateChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Patrimonio publishes an installer signed by TI; any PC finds it and
 *  downloads it intact — and nothing unsigned is ever offered. */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class ActualizacionServiceIntegrationTest extends IntegrationTestBase {

    private final KeyPair llaveTi = par();
    private final ActualizacionService servicio =
        new ActualizacionService(FirmaActualizaciones.conClave(llaveTi.getPublic()));

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void publicarDetectarYDescargar(@TempDir Path dir) throws Exception {
        assertNotEquals("dev", UpdateChecker.currentVersion(), "la prueba corre con la versión del pom");
        byte[] contenido = new byte[12 * 1024 * 1024 + 123];   // 3 partes de 5 MB
        new Random(7).nextBytes(contenido);
        File exe = dir.resolve("SIBIM-Desktop-99.0.0-win64-setup.exe").toFile();
        Files.write(exe.toPath(), contenido);
        firmar(exe, "99.0.0", llaveTi);

        entrarComo(Rol.DIRECCION);
        assertThrows(SecurityException.class, () -> servicio.publicar(exe, null, null));

        entrarComo(Rol.ADMIN);
        if (corriendoComoSibimApp()) { rechazadaComoApp(exe); return; }
        assertEquals("99.0.0", servicio.publicar(exe, "Pruebas", null), "la versión sale de la firma");
        firmar(exe, "98.0.0", llaveTi);
        assertThrows(IllegalArgumentException.class, () -> servicio.publicar(exe, null, null),
            "no se publica una versión menor a la última");

        var disponible = servicio.disponible().orElseThrow();
        assertEquals("99.0.0", disponible.version());
        assertEquals(3, disponible.partes());
        assertEquals("SIBIM-Desktop-99.0.0-setup.exe", ActualizacionService.nombreLocal(disponible));

        File bajado = dir.resolve("bajado.exe").toFile();
        double[] ultimoAvance = {0};
        servicio.descargar(disponible, bajado, a -> ultimoAvance[0] = a);
        assertArrayEquals(contenido, Files.readAllBytes(bajado.toPath()));
        assertEquals(1.0, ultimoAvance[0], 1e-9);
    }

    @Test
    void soloSePublicaUnInstaladorConFirmaDeTiSinCambios(@TempDir Path dir) throws Exception {
        File exe = dir.resolve("SIBIM-Desktop-99.0.0-win64-setup.exe").toFile();
        Files.write(exe.toPath(), new byte[2048]);
        entrarComo(Rol.ADMIN);

        IllegalArgumentException sinFirma = assertThrows(IllegalArgumentException.class,
            () -> servicio.publicar(exe, null, null));
        assertTrue(sinFirma.getMessage().contains(".firma"), sinFirma.getMessage());

        firmar(exe, "99.0.0", par());
        assertThrows(IllegalArgumentException.class, () -> servicio.publicar(exe, null, null),
            "firmado con otra llave");

        firmar(exe, "99.0.0", llaveTi);
        Files.write(exe.toPath(), new byte[]{1, 2, 3}, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IllegalArgumentException.class, () -> servicio.publicar(exe, null, null),
            "el instalador cambió después de firmarse");

        var sinClave = new ActualizacionService(FirmaActualizaciones.conClave(null));
        assertThrows(IllegalStateException.class, () -> sinClave.publicar(exe, null, null),
            "una compilación sin clave pública no puede comprobar nada");
        assertTrue(servicio.disponible().isEmpty(), "nada se publicó");
    }

    @Test
    void unaVersionMetidaDirectoEnLaBase_noSeOfreceNiSeInstala(@TempDir Path dir) throws Exception {
        // Someone with a PC's .env writes the tables directly: no signature,
        // or TI's signature from another installer.
        byte[] programa = {9, 9, 9};
        String sha = sha256(programa);
        String firmaDeOtro = FirmaActualizaciones.firmar(llaveTi.getPrivate(), "1.0.0", sha256(new byte[]{1}));
        insertar("99.9.9", sha, null, programa);
        insertar("99.9.8", sha, firmaDeOtro, programa);
        insertar("99.9.7", sha, FirmaActualizaciones.firmar(par().getPrivate(), "99.9.7", sha), programa);

        assertTrue(servicio.disponible().isEmpty());

        var falsa = new ActualizacionService.Version("99.9.9", "..\\..\\x.exe", 3, sha, null, 1, null);
        File destino = dir.resolve("x.exe").toFile();
        assertThrows(IOException.class, () -> servicio.descargar(falsa, destino, null));
        assertFalse(destino.exists(), "ni siquiera se descarga");
        assertEquals("SIBIM-Desktop-99.9.9-setup.exe", ActualizacionService.nombreLocal(falsa),
            "el nombre en la base no decide dónde se guarda");
    }

    @Test
    void soloSeConservanLasDosUltimasVersiones(@TempDir Path dir) throws Exception {
        File exe = dir.resolve("SIBIM-Desktop-99.0.0-win64-setup.exe").toFile();
        Files.write(exe.toPath(), new byte[1024]);
        entrarComo(Rol.ADMIN);
        if (corriendoComoSibimApp()) { firmar(exe, "99.0.0", llaveTi); rechazadaComoApp(exe); return; }
        for (String v : new String[]{"99.0.0", "99.0.1", "99.0.2"}) {
            firmar(exe, v, llaveTi);
            servicio.publicar(exe, null, null);
        }

        try (var c = com.sibim.db.DatabaseConfig.getConnection();
             var rs = c.createStatement().executeQuery("SELECT version FROM actualizaciones ORDER BY version")) {
            java.util.List<String> quedan = new java.util.ArrayList<>();
            while (rs.next()) quedan.add(rs.getString(1));
            assertEquals(java.util.List.of("99.0.1", "99.0.2"), quedan);
        }
        try (var c = com.sibim.db.DatabaseConfig.getConnection();
             var rs = c.createStatement().executeQuery("SELECT COUNT(DISTINCT version) FROM actualizacion_partes")) {
            rs.next();
            assertEquals(2, rs.getInt(1), "las partes de la versión borrada también se van");
        }
    }

    /** A PC installed with the bundled connection is sibim_app, which only reads
     *  the updates: even Patrimonio publishes from the admin PC. */
    private void rechazadaComoApp(File exe) throws java.sql.SQLException {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> servicio.publicar(exe, "Pruebas", null));
        assertTrue(e.getMessage().contains("computadora de administración"), e.getMessage());
        assertTrue(servicio.disponible().isEmpty(), "nada se publicó");
    }

    private void insertar(String version, String sha, String firma, byte[] datos) throws Exception {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO actualizaciones "
                 + "(version, archivo, tamano, sha256, partes, firma) VALUES (?, 'x.exe', ?, ?, 1, ?)");
             PreparedStatement parte = c.prepareStatement(
                 "INSERT INTO actualizacion_partes (version, n, datos) VALUES (?, 0, ?)")) {
            ps.setString(1, version);
            ps.setLong(2, datos.length);
            ps.setString(3, sha);
            ps.setString(4, firma);
            ps.executeUpdate();
            parte.setString(1, version);
            parte.setBytes(2, datos);
            parte.executeUpdate();
        }
    }

    /** What build-installer.ps1 does on TI's PC. */
    private static void firmar(File exe, String version, KeyPair llave) throws Exception {
        String sha = sha256(Files.readAllBytes(exe.toPath()));
        FirmaActualizaciones.escribir(FirmaActualizaciones.archivoFirma(exe),
            new FirmaActualizaciones.Firma(version, sha, FirmaActualizaciones.firmar(llave.getPrivate(), version, sha)));
    }

    private static String sha256(byte[] datos) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
    }

    private static KeyPair par() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void entrarComo(Rol rol) {
        Usuario u = new Usuario();
        u.setId("u-" + rol);
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setArea(rol == Rol.ADMIN ? null : "Dirección de Catastro");
        SessionManager.setCurrentUser(u);
    }
}
