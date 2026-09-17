package com.sibim.service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * Password-based AES-256-GCM encryption for whole-database backup files.
 *
 * <p>A backup dumps every table — including every user's bcrypt password hash
 * (see {@link BackupService}) — into a single portable file that an admin may
 * copy to a USB drive, email, or shared folder. Unlike the offline SQLite
 * store's encryption ({@code OfflineEncryption}), which derives its key from
 * THIS machine's identity (by design: that file only ever needs to be read on
 * the machine that wrote it), a backup must be restorable on a different
 * machine — e.g. a new server after a hardware failure. So the key here comes
 * from a passphrase the admin chooses at backup time and must supply again to
 * restore, not from anything machine-bound.
 *
 * <p>Format: [4-byte magic "SIBK"][16-byte salt][12-byte IV][ciphertext+tag].
 * A random salt per backup (not the offline store's fixed salt) since many
 * independent backup files may exist; each gets its own key derivation.
 */
public final class BackupEncryption {

    private static final byte[] MAGIC      = { 0x53, 0x49, 0x42, 0x4B }; // "SIBK"
    private static final int    SALT_LEN   = 16;
    private static final int    IV_LEN     = 12;
    private static final int    TAG_BITS   = 128;
    private static final int    KEY_BITS   = 256;
    private static final int    ITERATIONS = 600_000;
    private static final String ALGO       = "AES/GCM/NoPadding";

    private BackupEncryption() {}

    public static boolean isEncrypted(byte[] data) {
        if (data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) return false;
        }
        return true;
    }

    public static byte[] encrypt(byte[] plaintext, char[] password) {
        try {
            byte[] salt = new byte[SALT_LEN];
            byte[] iv   = new byte[IV_LEN];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            random.nextBytes(iv);

            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] out = new byte[MAGIC.length + SALT_LEN + IV_LEN + ciphertext.length];
            System.arraycopy(MAGIC,      0, out, 0,                              MAGIC.length);
            System.arraycopy(salt,       0, out, MAGIC.length,                   SALT_LEN);
            System.arraycopy(iv,         0, out, MAGIC.length + SALT_LEN,        IV_LEN);
            System.arraycopy(ciphertext, 0, out, MAGIC.length + SALT_LEN + IV_LEN, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el respaldo", e);
        }
    }

    /** @throws WrongPasswordException if the passphrase doesn't match — the
     *  GCM tag check fails, distinguishable from any other corruption/error. */
    public static byte[] decrypt(byte[] data, char[] password) throws WrongPasswordException {
        if (data.length < MAGIC.length + SALT_LEN + IV_LEN) {
            throw new IllegalArgumentException("Archivo de respaldo dañado o truncado");
        }
        byte[] salt = new byte[SALT_LEN];
        byte[] iv   = new byte[IV_LEN];
        byte[] ciphertext = new byte[data.length - MAGIC.length - SALT_LEN - IV_LEN];
        System.arraycopy(data, MAGIC.length,             salt,       0, SALT_LEN);
        System.arraycopy(data, MAGIC.length + SALT_LEN,  iv,         0, IV_LEN);
        System.arraycopy(data, MAGIC.length + SALT_LEN + IV_LEN, ciphertext, 0, ciphertext.length);
        try {
            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new WrongPasswordException();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el respaldo", e);
        }
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        byte[] raw = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    public static class WrongPasswordException extends Exception {
        WrongPasswordException() { super("Contraseña incorrecta para este respaldo"); }
    }
}
