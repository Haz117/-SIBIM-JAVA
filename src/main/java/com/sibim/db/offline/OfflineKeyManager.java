package com.sibim.db.offline;

import com.sun.jna.platform.win32.Crypt32Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HexFormat;

class OfflineKeyManager {

    private static final Logger log = LoggerFactory.getLogger(OfflineKeyManager.class);

    // Fixed salt — changing this invalidates all existing encrypted DBs
    private static final byte[] SALT =
        "SIBIM-offline-db-2025-v1".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS   = 256;

    /** The random store key, sealed with DPAPI, next to offline.db.enc. */
    static final String ARCHIVO_CLAVE = "offline.key";

    private OfflineKeyManager() {}

    /**
     * Key for offline.db.enc: 256 random bits generated once per Windows
     * account and stored sealed with DPAPI (CryptProtectData, current-user
     * scope), so only this Windows account can unseal it. The older
     * {@link #deriveKey()} is computed from the user name and the machine's
     * MachineGuid — values any account on the PC can read — so it only
     * obfuscates the file.
     *
     * If the sealed key can't be opened any more (DPAPI keys are lost when an
     * administrator resets the Windows password), it's set aside and a new
     * one is created; OfflineStore then sets aside the store it can't read.
     *
     * @return null where DPAPI isn't available (not Windows, native library
     *         missing) — the caller falls back to {@link #deriveKey()}.
     */
    static SecretKey claveProtegida(Path dir) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        Path archivo = dir.resolve(ARCHIVO_CLAVE);
        try {
            if (Files.exists(archivo)) {
                try {
                    byte[] raw = Crypt32Util.cryptUnprotectData(Files.readAllBytes(archivo));
                    if (raw.length == KEY_BITS / 8) return new SecretKeySpec(raw, "AES");
                    log.error("offline.key tiene un tamaño inesperado ({} bytes)", raw.length);
                } catch (RuntimeException e) {
                    log.error("offline.key no se pudo abrir con la cuenta de Windows actual "
                        + "(¿se restableció la contraseña?)", e);
                }
                Path apartado = archivo.resolveSibling(ARCHIVO_CLAVE + ".ilegible-" + System.currentTimeMillis());
                Files.move(archivo, apartado);
            }
            byte[] raw = new byte[KEY_BITS / 8];
            new SecureRandom().nextBytes(raw);
            Path tmp = archivo.resolveSibling(ARCHIVO_CLAVE + ".tmp");
            Files.write(tmp, Crypt32Util.cryptProtectData(raw));
            Files.move(tmp, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new SecretKeySpec(raw, "AES");
        } catch (LinkageError | Exception e) {
            log.warn("DPAPI no disponible; offline.db.enc se cifra con la clave derivada del equipo", e);
            return null;
        }
    }

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
        } catch (Exception ignored) {
            log.debug("Could not read MachineGuid from registry, falling back to hostname", ignored);
        }
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
