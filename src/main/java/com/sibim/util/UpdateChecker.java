package com.sibim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * Queries the GitHub Releases API to detect whether a newer version of SIBIM
 * Desktop is available. All network I/O happens off the FX thread — callers
 * must schedule this in a background executor (e.g. AppExecutor.submit).
 *
 * Version comparison is intentional semver (major.minor.patch). Pre-release
 * suffixes (e.g. "-beta") are ignored.
 */
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    public record UpdateInfo(String latestVersion, String releaseUrl) {}

    /** @return UpdateInfo if a newer version is available, null otherwise. */
    public static UpdateInfo checkForUpdate() {
        try {
            Properties props = loadAppProperties();
            String current = props.getProperty("app.version", "0.0.0").trim();
            String owner   = props.getProperty("app.github.owner", "").trim();
            String repo    = props.getProperty("app.github.repo",  "").trim();

            if (owner.isBlank() || repo.isBlank()) return null;
            // Strip leading slash that repo names sometimes carry
            if (repo.startsWith("/")) repo = repo.substring(1);

            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("GitHub API returned {}: update check skipped", response.statusCode());
                return null;
            }

            String body = response.body();
            String tagName   = extractJsonString(body, "tag_name");
            String htmlUrl   = extractJsonString(body, "html_url");
            if (tagName == null) return null;

            String latest = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            if (isNewer(latest, current)) {
                log.info("Update available: {} → {}", current, latest);
                return new UpdateInfo(latest, htmlUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Update check failed: {}", e.getMessage());
        }
        return null;
    }

    private static Properties loadAppProperties() throws Exception {
        Properties p = new Properties();
        try (InputStream is = UpdateChecker.class.getResourceAsStream("/app.properties")) {
            if (is != null) p.load(is);
        }
        return p;
    }

    /** True if {@code candidate} is strictly newer than {@code current} (semver). */
    static boolean isNewer(String candidate, String current) {
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
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /** Minimal JSON string extractor — avoids a JSON library dependency. */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon == -1) return null;
        int start = json.indexOf('"', colon + 1);
        if (start == -1) return null;
        int end = json.indexOf('"', start + 1);
        if (end == -1) return null;
        return json.substring(start + 1, end);
    }
}
