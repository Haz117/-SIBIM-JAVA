package com.sibim.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BackupEncryptionTest {

    @Test
    void encryptDecrypt_roundTrip_returnsOriginalBytes() throws Exception {
        byte[] original = "{\"tablas\":{\"users\":[]}}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = BackupEncryption.encrypt(original, "correcta-123".toCharArray());
        byte[] decrypted = BackupEncryption.decrypt(encrypted, "correcta-123".toCharArray());
        assertArrayEquals(original, decrypted);
    }

    @Test
    void decrypt_wrongPassword_throwsWrongPasswordException() {
        byte[] original = "datos secretos".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = BackupEncryption.encrypt(original, "correcta-123".toCharArray());
        assertThrows(BackupEncryption.WrongPasswordException.class,
            () -> BackupEncryption.decrypt(encrypted, "incorrecta-456".toCharArray()));
    }

    @Test
    void isEncrypted_encryptedData_returnsTrue() {
        byte[] encrypted = BackupEncryption.encrypt("x".getBytes(StandardCharsets.UTF_8), "pw123456".toCharArray());
        assertTrue(BackupEncryption.isEncrypted(encrypted));
    }

    @Test
    void isEncrypted_plainJson_returnsFalse() {
        byte[] plain = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);
        assertFalse(BackupEncryption.isEncrypted(plain));
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        byte[] original = "misma entrada".getBytes(StandardCharsets.UTF_8);
        byte[] a = BackupEncryption.encrypt(original, "pw123456".toCharArray());
        byte[] b = BackupEncryption.encrypt(original, "pw123456".toCharArray());
        assertFalse(java.util.Arrays.equals(a, b), "salt/IV aleatorios deben producir cifrados distintos");
    }
}
