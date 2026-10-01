package com.sibim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * The running build's version and semver comparison. Finding and installing
 * a newer version is ActualizacionService's job: it goes through the
 * database, not GitHub Releases, because the installer carries the
 * connection settings and the repository is public.
 *
 * Version comparison is intentional semver (major.minor.patch). Pre-release
 * suffixes (e.g. "-beta") are ignored.
 */
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    /** The running build's version (pom {@code <version>}, filtered into
     *  app.properties). Falls back to "dev" when run unfiltered from an IDE. */
    public static String currentVersion() {
        try {
            String v = loadAppProperties().getProperty("app.version", "").trim();
            if (!v.isEmpty() && !v.startsWith("${")) return v;
        } catch (Exception e) {
            log.debug("app.properties unreadable: {}", e.getMessage());
        }
        return "dev";
    }

    private static Properties loadAppProperties() throws Exception {
        Properties p = new Properties();
        try (InputStream is = UpdateChecker.class.getResourceAsStream("/app.properties")) {
            if (is != null) p.load(is);
        }
        return p;
    }

    /** True if {@code candidate} is strictly newer than {@code current} (semver).
     *  A "dev" build (run from the IDE) is never offered an update. */
    public static boolean isNewer(String candidate, String current) {
        if ("dev".equals(current)) return false;
        int[] c = parseSemver(candidate);
        int[] r = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (c[i] > r[i]) return true;
            if (c[i] < r[i]) return false;
        }
        return false;
    }

    private static int[] parseSemver(String v) {
        if (v == null) return new int[]{0, 0, 0};
        String[] parts = v.split("[.\\-]", 4);
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {
                log.debug("Non-numeric semver component '{}' in version string '{}'", parts[i], v, ignored);
            }
        }
        return result;
    }
}
