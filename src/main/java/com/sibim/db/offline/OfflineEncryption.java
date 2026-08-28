package com.sibim.db.offline;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-256-GCM file-level encryption for the offline SQLite store.
 *
 * <p>Format of an encrypted file: [4-byte magic][12-byte IV][ciphertext+tag]
 * <br>Magic: 0x53 0x49 0x42 0x45 ("SIBE") — lets us detect unencrypted files on migration.
 *
 * <p>The SQLite driver opens a plaintext working copy ({@code offline.db.work}).
 * On first open the encrypted blob ({@code offline.db.enc}) is decrypted to the
 * work file. On JVM shutdown the work file is re-encrypted back to the blob and
 * the work file is deleted.
 */
final class OfflineEncryption {

    private static final byte[] MAGIC    = {0x53, 0x49, 0x42, 0x45};  // "SIBE"
    private static final int    IV_LEN   = 12;   // 96-bit IV for GCM
    private static final int    TAG_BITS = 128;
    private static final String ALGO     = "AES/GCM/NoPadding";

    private OfflineEncryption() {}

    /** Returns a SecretKey from the 64-char hex string produced by OfflineKeyManager. */
    static SecretKey keyFrom(String hexKey) {
        byte[] raw = HexFormat.of().parseHex(hexKey);
        return new SecretKeySpec(raw, "AES");
    }

    /** Returns true if {@code file} starts with the SIBE magic bytes. */
    static boolean isEncrypted(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) < MAGIC.length) return false;
        try (var in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(MAGIC.length);
            if (head.length < MAGIC.length) return false;
            for (int i = 0; i < MAGIC.length; i++) {
                if (head[i] != MAGIC[i]) return false;
            }
        }
        return true;
    }

    /**
     * Decrypts {@code encFile} → {@code plainFile}.
     * If {@code encFile} does not exist, does nothing (first run — plainFile will be created
     * fresh by SQLite).
     */
    static void decryptTo(Path encFile, Path plainFile, SecretKey key) throws IOException {
        if (!Files.exists(encFile)) return;
        byte[] blob = Files.readAllBytes(encFile);
        // Check magic
        if (blob.length < MAGIC.length + IV_LEN) {
            throw new IOException("Archivo cifrado dañado o truncado: " + encFile);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (blob[i] != MAGIC[i]) {
                throw new IOException("El archivo offline.db.enc no tiene la firma esperada — "
                    + "es posible que haya sido creado con una versión anterior sin cifrado.");
            }
        }
        byte[] iv         = new byte[IV_LEN];
        byte[] ciphertext = new byte[blob.length - MAGIC.length - IV_LEN];
        System.arraycopy(blob, MAGIC.length,            iv,         0, IV_LEN);
        System.arraycopy(blob, MAGIC.length + IV_LEN,   ciphertext, 0, ciphertext.length);

        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            // Write atomically via temp file
            Path tmp = plainFile.resolveSibling(plainFile.getFileName() + ".dectemp");
            Files.write(tmp, plain);
            Files.move(tmp, plainFile, StandardCopyOption.REPLACE_EXISTING,
                                       StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new IOException("No se pudo descifrar offline.db.enc", e);
        }
    }

    /**
     * Like {@link #decryptTo} but returns {@code false} instead of throwing when the
     * GCM authentication tag doesn't match (wrong key). Used to detect a key mismatch
     * before attempting migration with the legacy key.
     */
    static boolean tryDecryptTo(Path encFile, Path plainFile, SecretKey key) throws IOException {
        if (!Files.exists(encFile)) return true;
        byte[] blob = Files.readAllBytes(encFile);
        if (blob.length < MAGIC.length + IV_LEN) {
            throw new IOException("Archivo cifrado dañado o truncado: " + encFile);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (blob[i] != MAGIC[i]) {
                throw new IOException("El archivo offline.db.enc no tiene la firma esperada.");
            }
        }
        byte[] iv         = new byte[IV_LEN];
        byte[] ciphertext = new byte[blob.length - MAGIC.length - IV_LEN];
        System.arraycopy(blob, MAGIC.length,          iv,         0, IV_LEN);
        System.arraycopy(blob, MAGIC.length + IV_LEN, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            Path tmp = plainFile.resolveSibling(plainFile.getFileName() + ".dectemp");
            Files.write(tmp, plain);
            Files.move(tmp, plainFile, StandardCopyOption.REPLACE_EXISTING,
                                       StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AEADBadTagException e) {
            return false;
        } catch (Exception e) {
            throw new IOException("No se pudo descifrar offline.db.enc", e);
        }
    }

    /**
     * Encrypts {@code plainFile} → {@code encFile}, then deletes the plain copy.
     * Called from the JVM shutdown hook so the working SQLite file doesn't linger on disk.
     */
    static void encryptFrom(Path plainFile, Path encFile, SecretKey key) throws IOException {
        if (!Files.exists(plainFile)) return;
        byte[] plain = Files.readAllBytes(plainFile);
        byte[] iv    = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain);

            byte[] blob = new byte[MAGIC.length + IV_LEN + ciphertext.length];
            System.arraycopy(MAGIC,      0, blob, 0,                        MAGIC.length);
            System.arraycopy(iv,         0, blob, MAGIC.length,             IV_LEN);
            System.arraycopy(ciphertext, 0, blob, MAGIC.length + IV_LEN,    ciphertext.length);

            Path tmp = encFile.resolveSibling(encFile.getFileName() + ".enctemp");
            Files.write(tmp, blob);
            Files.move(tmp, encFile, StandardCopyOption.REPLACE_EXISTING,
                                     StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(plainFile);
        } catch (Exception e) {
            throw new IOException("No se pudo cifrar offline.db", e);
        }
    }
}
