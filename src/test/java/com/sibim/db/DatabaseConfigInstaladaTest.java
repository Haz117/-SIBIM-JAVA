package com.sibim.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The connection an installer carries (packaging/sibim.env, staged next to
 *  the app; the launcher passes its folder as -Dsibim.config.dir): a fresh PC
 *  connects with it and only asks for the área's user and password, while a
 *  PC's own %APPDATA%\SIBIM\.env (the admin PC's) still wins. */
class DatabaseConfigInstaladaTest {

    @TempDir Path instalada;

    @AfterEach
    void limpiar() {
        System.clearProperty("sibim.config.dir");
    }

    private void instalador(String contenido) throws Exception {
        Files.writeString(instalada.resolve(DatabaseConfig.CONFIG_INSTALADA), contenido);
        System.setProperty("sibim.config.dir", instalada.toString());
    }

    @Test
    void sinEnvPropio_usaLaConexionDelInstalador() throws Exception {
        instalador("DB_URL=jdbc:postgresql://servidor.ejemplo:5432/postgres\n"
            + "DB_USER=sibim_app.ref\nDB_MIGRATE=false\n");
        assertEquals("sibim_app.ref", DatabaseConfig.setting("DB_USER", null));
        assertEquals("false", DatabaseConfig.setting("DB_MIGRATE", "true"));
    }

    @Test
    void unArchivoDelInstaladorSinServidor_seIgnora() throws Exception {
        instalador("DB_USER=sibim_app.ref\n");
        assertNotEquals("sibim_app.ref", DatabaseConfig.setting("DB_USER", null));
    }

    @Test
    void elEnvPropioDeLaPc_ganaAlDelInstalador() throws Exception {
        // Only inside the test sandbox (pom.xml points APPDATA at target/).
        Path propio = Path.of(System.getenv("APPDATA"), "SIBIM", ".env");
        assumeTrue(propio.toString().contains("test-appdata"), "solo con el APPDATA del sandbox de pruebas");
        Files.createDirectories(propio.getParent());
        Files.writeString(propio, "DB_URL=jdbc:postgresql://admin.ejemplo:5432/postgres\nDB_USER=postgres.ref\n");
        try {
            instalador("DB_URL=jdbc:postgresql://servidor.ejemplo:5432/postgres\nDB_USER=sibim_app.ref\n");
            assertEquals("postgres.ref", DatabaseConfig.setting("DB_USER", null));
        } finally {
            Files.deleteIfExists(propio);
        }
    }
}
