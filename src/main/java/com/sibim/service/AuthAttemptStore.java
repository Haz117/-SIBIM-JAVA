package com.sibim.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists failed-login attempt counters to ~/.sibim/auth_attempts.properties
 * so brute-force lockouts survive JVM restarts.
 * The data is not sensitive (counts + timestamps, no credentials) so the file
 * is not encrypted, but it is stored in the same user-owned directory as the
 * offline DB.
 */
final class AuthAttemptStore {

    private static final Path FILE =
        Path.of(System.getProperty("user.home"), ".sibim", "auth_attempts.properties");
    private static final Object LOCK = new Object();

    private AuthAttemptStore() {}

    static int getCount(String key) {
        synchronized (LOCK) {
            return Integer.parseInt(load().getProperty(key + ".count", "0"));
        }
    }

    static long getWindowStart(String key) {
        synchronized (LOCK) {
            return Long.parseLong(load().getProperty(key + ".ts", "0"));
        }
    }

    static void increment(String key) {
        synchronized (LOCK) {
            Properties p = load();
            long ts = Long.parseLong(p.getProperty(key + ".ts", "0"));
            int count = Integer.parseInt(p.getProperty(key + ".count", "0"));
            long now = System.currentTimeMillis();
            // Reset window if the stored timestamp is 0 (first failure)
            if (ts == 0) ts = now;
            p.setProperty(key + ".count", String.valueOf(count + 1));
            p.setProperty(key + ".ts", String.valueOf(ts));
            save(p);
        }
    }

    static void clear(String key) {
        synchronized (LOCK) {
            Properties p = load();
            p.remove(key + ".count");
            p.remove(key + ".ts");
            save(p);
        }
    }

    static void clearAll() {
        synchronized (LOCK) {
            try { Files.deleteIfExists(FILE); } catch (IOException ignored) {}
        }
    }

    private static Properties load() {
        Properties p = new Properties();
        if (Files.exists(FILE)) {
            try (var in = Files.newInputStream(FILE)) {
                p.load(in);
            } catch (IOException ignored) {}
        }
        return p;
    }

    private static void save(Properties p) {
        try {
            Files.createDirectories(FILE.getParent());
            try (var out = Files.newOutputStream(FILE)) {
                p.store(out, null);
            }
        } catch (IOException ignored) {}
    }
}
