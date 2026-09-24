package com.sibim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BackupEncryption (AES-256-GCM) and the BackupService's
 * encryption contract.
 *
 * BackupService#backup/restore require a live Postgres connection and are
 * therefore not exercised here (see BackupServiceAuthorizationTest for the
 * authorization guard). These tests focus entirely on the encryption layer:
 *   - round-trip: encrypt → decrypt with correct password returns original bytes
 *   - wrong password: decrypt throws WrongPasswordException
 *   - isEncrypted detection
 *   - random salt/IV: same plaintext produces different ciphertext on every call
 *   - large payload: encryption works on payloads typical of a full backup dump
 *   - empty payload: zero-length plaintext is handled without exception
 */
class BackupServiceTest {

    @TempDir
    Path tempDir;

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    void encryptDecrypt_jsonPayload_retornaPlaintextOriginal() throws Exception {
        byte[] original = "{\"version\":1,\"tablas\":{\"users\":[{\"id\":\"abc\"}]}}".getBytes(StandardCharsets.UTF_8);
        char[] password = "C0ntraseña-Segura!2024".toCharArray();

        byte[] cifrado   = BackupEncryption.encrypt(original, password);
        byte[] descifrado = BackupEncryption.decrypt(cifrado, password);

        assertArrayEquals(original, descifrado, "El round-trip debe restaurar los bytes exactos");
    }

    @Test
    void encryptDecrypt_passwordConCaracteresEspeciales_funciona() throws Exception {
        byte[] data = "datos de prueba".getBytes(StandardCharsets.UTF_8);
        char[] password = "¡Hola_Mundo! @#$%^&*()".toCharArray();

        byte[] cifrado    = BackupEncryption.encrypt(data, password);
        byte[] descifrado = BackupEncryption.decrypt(cifrado, password);

        assertArrayEquals(data, descifrado);
    }

    @Test
    void encryptDecrypt_payloadVacio_nuncaLanzaExcepcion() throws Exception {
        byte[] empty = new byte[0];
        char[] password = "pw-vacio-test".toCharArray();

        assertDoesNotThrow(() -> {
            byte[] cifrado    = BackupEncryption.encrypt(empty, password);
            byte[] descifrado = BackupEncryption.decrypt(cifrado, password);
            assertArrayEquals(empty, descifrado);
        });
    }

    @Test
    void encryptDecrypt_payloadGrande_funcionaCorrectamente() throws Exception {
        // Simula el tamaño aproximado de un dump JSON de varias tablas (~200 KB)
        StringBuilder sb = new StringBuilder("{\"version\":1,\"tablas\":{\"products\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(java.util.UUID.randomUUID()).append("\",")
              .append("\"nombre\":\"Producto ").append(i).append("\",")
              .append("\"precio_compra\":").append(1000 + i).append("}");
        }
        sb.append("]}}");
        byte[] large = sb.toString().getBytes(StandardCharsets.UTF_8);
        char[] password = "respaldo-grande-2024".toCharArray();

        byte[] cifrado    = BackupEncryption.encrypt(large, password);
        byte[] descifrado = BackupEncryption.decrypt(cifrado, password);

        assertArrayEquals(large, descifrado, "Round-trip debe preservar payloads grandes");
    }

    // ── contraseña incorrecta ─────────────────────────────────────────────────

    @Test
    void decrypt_contrasenaIncorrecta_lanzaWrongPasswordException() {
        byte[] data = "información confidencial".getBytes(StandardCharsets.UTF_8);
        byte[] cifrado = BackupEncryption.encrypt(data, "contraseña-correcta".toCharArray());

        assertThrows(BackupEncryption.WrongPasswordException.class,
            () -> BackupEncryption.decrypt(cifrado, "contraseña-incorrecta".toCharArray()),
            "Contraseña incorrecta debe lanzar WrongPasswordException");
    }

    @Test
    void decrypt_passwordVacioSiendoCifradoConPassword_lanzaWrongPasswordException() {
        byte[] data = "secreto".getBytes(StandardCharsets.UTF_8);
        byte[] cifrado = BackupEncryption.encrypt(data, "mi-clave".toCharArray());

        assertThrows(BackupEncryption.WrongPasswordException.class,
            () -> BackupEncryption.decrypt(cifrado, "".toCharArray()));
    }

    @Test
    void decrypt_archivoTruncado_lanzaIllegalArgumentException() {
        // Un array demasiado corto para contener MAGIC+SALT+IV
        byte[] truncado = new byte[5];
        truncado[0] = 0x53; truncado[1] = 0x49; truncado[2] = 0x42; truncado[3] = 0x4B;

        assertThrows(IllegalArgumentException.class,
            () -> BackupEncryption.decrypt(truncado, "cualquiera".toCharArray()),
            "Archivo truncado debe lanzar IllegalArgumentException");
    }

    // ── isEncrypted ───────────────────────────────────────────────────────────

    @Test
    void isEncrypted_datoCifrado_retornaTrue() {
        byte[] cifrado = BackupEncryption.encrypt(
            "test".getBytes(StandardCharsets.UTF_8), "pw".toCharArray());
        assertTrue(BackupEncryption.isEncrypted(cifrado));
    }

    @Test
    void isEncrypted_jsonPlano_retornaFalse() {
        byte[] plain = "{\"version\":1,\"tablas\":{}}".getBytes(StandardCharsets.UTF_8);
        assertFalse(BackupEncryption.isEncrypted(plain));
    }

    @Test
    void isEncrypted_bytesAleatorios_retornaFalse() {
        byte[] random = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};
        assertFalse(BackupEncryption.isEncrypted(random));
    }

    @Test
    void isEncrypted_arrayVacio_retornaFalse() {
        assertFalse(BackupEncryption.isEncrypted(new byte[0]));
    }

    // ── aleatoriedad salt/IV ──────────────────────────────────────────────────

    @Test
    void encrypt_mismoInputDosveces_produceDiferenteCiphertext() {
        byte[] original = "datos".getBytes(StandardCharsets.UTF_8);
        char[] password = "mismaClave".toCharArray();

        byte[] a = BackupEncryption.encrypt(original, password);
        byte[] b = BackupEncryption.encrypt(original, password);

        assertFalse(java.util.Arrays.equals(a, b),
            "Salt e IV aleatorios deben producir ciphertexts distintos cada vez");
    }

    // ── BackupService: restore de JSON plano (sin cifrar) ────────────────────
    // BackupService.restore acepta respaldos legacy (plain JSON, sin "SIBK" magic).
    // Se valida mediante isEncrypted que el flujo de detección funciona.

    @Test
    void isEncrypted_legacyPlainJson_seDetectaComoNoEncriptado() {
        // Un respaldo legacy es JSON puro sin la cabecera SIBK
        String legacyJson = "{\"version\":1,\"exportadoEn\":\"2023-01-15T10:00:00\",\"tablas\":{}}";
        byte[] raw = legacyJson.getBytes(StandardCharsets.UTF_8);
        assertFalse(BackupEncryption.isEncrypted(raw),
            "Un respaldo legacy (JSON plano) no debe detectarse como cifrado");
    }

    // ── Tamaño del output ─────────────────────────────────────────────────────

    @Test
    void encrypt_outputSize_esMayorQueInput() {
        byte[] original = "datos de prueba 1234567890".getBytes(StandardCharsets.UTF_8);
        byte[] cifrado = BackupEncryption.encrypt(original, "clave".toCharArray());

        // MAGIC(4) + SALT(16) + IV(12) + GCM_TAG(16) + ciphertext >= original.length
        int overhead = 4 + 16 + 12 + 16;
        assertTrue(cifrado.length >= original.length + overhead,
            "El output cifrado debe ser mayor que el input por el overhead SIBK+salt+IV+tag");
    }
}
