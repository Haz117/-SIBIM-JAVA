package com.sibim.db.integration;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.ActualizacionService;
import com.sibim.session.SessionManager;
import com.sibim.util.UpdateChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Patrimonio publishes an installer; any PC finds it and downloads it intact. */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class ActualizacionServiceIntegrationTest extends IntegrationTestBase {

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void publicarDetectarYDescargar(@TempDir Path dir) throws Exception {
        assertNotEquals("dev", UpdateChecker.currentVersion(), "la prueba corre con la versión del pom");
        byte[] contenido = new byte[12 * 1024 * 1024 + 123];   // 3 partes de 5 MB
        new Random(7).nextBytes(contenido);
        File exe = dir.resolve("SIBIM-Desktop-99.0.0-win64-setup.exe").toFile();
        Files.write(exe.toPath(), contenido);
        var servicio = new ActualizacionService();

        entrarComo(Rol.DIRECCION);
        assertThrows(SecurityException.class, () -> servicio.publicar(exe, "99.0.0", null, null));

        entrarComo(Rol.ADMIN);
        assertEquals("99.0.0", ActualizacionService.versionDelArchivo(exe.getName()).orElseThrow());
        servicio.publicar(exe, "99.0.0", "Pruebas", null);
        assertThrows(IllegalArgumentException.class, () -> servicio.publicar(exe, "98.0.0", null, null),
            "no se publica una versión menor a la última");

        var disponible = servicio.disponible().orElseThrow();
        assertEquals("99.0.0", disponible.version());
        assertEquals(3, disponible.partes());

        File bajado = dir.resolve("bajado.exe").toFile();
        double[] ultimoAvance = {0};
        servicio.descargar(disponible, bajado, a -> ultimoAvance[0] = a);
        assertArrayEquals(contenido, Files.readAllBytes(bajado.toPath()));
        assertEquals(1.0, ultimoAvance[0], 1e-9);
    }

    @Test
    void soloSeConservanLasDosUltimasVersiones(@TempDir Path dir) throws Exception {
        File exe = dir.resolve("SIBIM-Desktop-99.0.0-win64-setup.exe").toFile();
        Files.write(exe.toPath(), new byte[1024]);
        var servicio = new ActualizacionService();
        entrarComo(Rol.ADMIN);
        for (String v : new String[]{"99.0.0", "99.0.1", "99.0.2"}) servicio.publicar(exe, v, null, null);

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

    private static void entrarComo(Rol rol) {
        Usuario u = new Usuario();
        u.setId("u-" + rol);
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setArea(rol == Rol.ADMIN ? null : "Dirección de Catastro");
        SessionManager.setCurrentUser(u);
    }
}
