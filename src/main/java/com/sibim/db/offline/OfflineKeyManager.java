package com.sibim.db.offline;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
     * Derives a 256-bit key from stable machine identifiers.
     * Uses the Windows MachineGuid (set at OS install, survives hostname changes)
     * so the key remains valid even if the PC is renamed.
     * Returns the key as a 64-char lowercase hex string.
     */
    static String deriveKey() {
        String material = "SIBIM-v2:" + System.getProperty("user.name", "unknown")
            + ":" + machineGuid();
        return pbkdf2(material);
    }

    /**
     * Derives the OLD key from the hostname-based material (pre-MachineGuid).
     * Used once at startup to migrate encrypted DBs created before this change.
     */
    static String deriveLegacyKey() {
        String material = "SIBIM-v1:" + System.getProperty("user.name", "unknown")
            + ":" + machineId();
        return pbkdf2(material);
    }

    private static String pbkdf2(String material) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(material.toCharArray(), SALT, ITERATIONS, KEY_BITS);
            byte[] raw = skf.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo derivar la clave de cifrado offline", e);
        }
    }

    /**
     * Returns a machine identifier that is stable across hostname/computer-name changes.
     * On Windows reads HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid via reg.exe —
     * this GUID is written once at OS installation and never changes.
     * Falls back to hostname if the registry read fails (non-Windows or permission issue).
     */
    private static String machineGuid() {
        try {
            Process p = new ProcessBuilder(
                    "reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v", "MachineGuid")
                .redirectErrorStream(true)
                .start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String guid = br.lines()
                    .filter(l -> l.contains("MachineGuid"))
                    .map(l -> l.replaceAll(".*REG_SZ\\s+", "").trim())
                    .filter(l -> !l.isBlank())
                    .findFirst()
                    .orElse(null);
                if (guid != null) return guid;
            }
        } catch (Exception ignored) {}
        return machineId();
    }

    private static String machineId() {
        String name = System.getenv("COMPUTERNAME");
        if (name != null && !name.isBlank()) return name;
        name = System.getenv("HOSTNAME");
        if (name != null && !name.isBlank()) return name;
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown-host"; }
    }
}
