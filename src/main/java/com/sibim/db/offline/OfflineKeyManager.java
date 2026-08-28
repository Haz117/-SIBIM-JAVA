package com.sibim.db.offline;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.HexFormat;

class OfflineKeyManager {

    // Fixed salt — changing this invalidates all existing encrypted DBs
    private static final byte[] SALT =
        "SIBIM-offline-db-2025-v1".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS   = 256;

    private OfflineKeyManager() {}

    /**
     * Derives a 256-bit key from machine-specific identifiers.
     * The key is stable across app restarts on the same machine/user account.
     * Returns the key as a 64-char lowercase hex string.
     */
    static String deriveKey() {
        String material = "SIBIM-v1:" + System.getProperty("user.name", "unknown")
            + ":" + machineId();
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(material.toCharArray(), SALT, ITERATIONS, KEY_BITS);
            byte[] raw = skf.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo derivar la clave de cifrado offline", e);
        }
    }

    private static String machineId() {
        String name = System.getenv("COMPUTERNAME");        // Windows
        if (name != null && !name.isBlank()) return name;
        name = System.getenv("HOSTNAME");                   // Linux/macOS
        if (name != null && !name.isBlank()) return name;
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown-host"; }
    }
}
