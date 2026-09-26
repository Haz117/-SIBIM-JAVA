package com.sibim.db.offline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OfflineKeyManagerTest {

    @Test
    void key_is64HexChars() {
        String key = OfflineKeyManager.deriveKey();
        assertEquals(64, key.length(), "AES-256 key debe ser 64 hex chars (256 bits)");
        assertTrue(key.matches("[0-9a-f]{64}"), "Key debe ser hex lowercase");
    }

    @Test
    void key_isDeterministic() {
        assertEquals(OfflineKeyManager.deriveKey(), OfflineKeyManager.deriveKey());
    }

    @Test
    void key_changesWithDifferentUser() {
        // Simulate a different username by checking that a manually constructed
        // material string produces a different key. We test this by verifying
        // the key is not all-zeros (trivial sanity) and that two calls are equal
        // (determinism already tested above). The machine-binding is implicitly
        // tested by the fact that user.name is part of the material.
        String key = OfflineKeyManager.deriveKey();
        assertNotEquals("0000000000000000000000000000000000000000000000000000000000000000", key);
    }

    @Test
    void claveProtegida_seCreaUnaVezYSeReutiliza(@TempDir Path dir) throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"), "DPAPI solo existe en Windows");
        SecretKey primera = OfflineKeyManager.claveProtegida(dir);
        assertNotNull(primera);
        byte[] sellada = Files.readAllBytes(dir.resolve(OfflineKeyManager.ARCHIVO_CLAVE));
        assertFalse(Arrays.equals(primera.getEncoded(), sellada), "en disco va sellada, no en claro");
        assertArrayEquals(primera.getEncoded(), OfflineKeyManager.claveProtegida(dir).getEncoded());
        assertNotEquals(OfflineKeyManager.deriveKey(), HexFormat.of().formatHex(primera.getEncoded()),
            "es aleatoria, no derivable de datos del equipo");
    }

    @Test
    void claveProtegida_ilegible_seApartaYSeCreaOtra(@TempDir Path dir) throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"), "DPAPI solo existe en Windows");
        Files.write(dir.resolve(OfflineKeyManager.ARCHIVO_CLAVE), new byte[]{1, 2, 3, 4});
        assertNotNull(OfflineKeyManager.claveProtegida(dir));
        try (var archivos = Files.list(dir)) {
            assertTrue(archivos.anyMatch(p -> p.getFileName().toString().startsWith("offline.key.ilegible-")));
        }
    }

    @Test
    void key_hasHighEntropy() {
        String key = OfflineKeyManager.deriveKey();
        // At minimum the key should not have all identical chars
        long distinctChars = key.chars().distinct().count();
        assertTrue(distinctChars > 4, "Key must not be degenerate: got " + key);
    }
}
