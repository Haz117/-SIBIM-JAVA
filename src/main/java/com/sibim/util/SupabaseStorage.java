package com.sibim.util;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

public final class SupabaseStorage {

    public static final String BUCKET = "sibim-fotos";

    private static volatile boolean initialized = false;
    private static String supabaseUrl;
    private static String serviceKey;
    private static final Object LOCK = new Object();

    private SupabaseStorage() {}

    /** True when Supabase Storage is configured and the app is online. */
    public static boolean isAvailable() {
        ensureInit();
        return supabaseUrl != null && serviceKey != null
            && !com.sibim.db.DatabaseConfig.isOfflineMode()
            && !com.sibim.db.DatabaseConfig.isDemoMode();
    }

    /**
     * Resizes {@code localFile} and uploads it to Supabase Storage under
     * {@code remoteName} (e.g. "abc123_0.jpg").
     * Returns the public URL of the uploaded object.
     */
    public static String upload(File localFile, String remoteName) throws IOException {
        ensureInit();
        if (supabaseUrl == null || serviceKey == null)
            throw new IOException("SUPABASE_URL o SUPABASE_SERVICE_KEY no están en .env");

        byte[] bytes = Files.readAllBytes(localFile.toPath());

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + remoteName))
            .header("apikey", serviceKey)
            .header("Authorization", "Bearer " + serviceKey)
            .header("Content-Type", "image/jpeg")
            .header("x-upsert", "true")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build();

        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300)
                throw new IOException("Storage " + resp.statusCode() + ": " + resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Carga de imagen interrumpida", e);
        }

        return publicUrl(remoteName);
    }

    /** Returns the public URL for an object already in the bucket. */
    public static String publicUrl(String remoteName) {
        ensureInit();
        if (supabaseUrl == null) throw new IllegalStateException("SUPABASE_URL no configurado en .env");
        return supabaseUrl + "/storage/v1/object/public/" + BUCKET + "/" + remoteName;
    }

    /** Whether {@code url} is a Supabase Storage URL (not a local path). */
    public static boolean isRemoteUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static void ensureInit() {
        if (initialized) return;
        synchronized (LOCK) {
            if (initialized) return;
            String appData = System.getenv("APPDATA");
            String prodDir = (appData != null && !appData.isBlank())
                ? appData + File.separator + "SIBIM"
                : System.getProperty("user.home") + File.separator + ".sibim";

            Dotenv prod = Dotenv.configure().directory(prodDir).ignoreIfMissing().load();
            supabaseUrl = prod.get("SUPABASE_URL");
            serviceKey  = prod.get("SUPABASE_SERVICE_KEY");

            if (supabaseUrl == null || supabaseUrl.isBlank()) {
                Dotenv dev = Dotenv.configure().ignoreIfMissing().load();
                supabaseUrl = dev.get("SUPABASE_URL");
                serviceKey  = dev.get("SUPABASE_SERVICE_KEY");
            }
            if (supabaseUrl == null || supabaseUrl.isBlank()) {
                supabaseUrl = System.getenv("SUPABASE_URL");
                serviceKey  = System.getenv("SUPABASE_SERVICE_KEY");
            }
            if (supabaseUrl != null) supabaseUrl = supabaseUrl.trim();
            if (serviceKey  != null) serviceKey  = serviceKey.trim();
            initialized = true;
        }
    }
}
