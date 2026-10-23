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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SupabaseStorage.class);

    public static final String BUCKET = "sibim-fotos";

    private static volatile boolean initialized = false;
    private static String supabaseUrl;
    private static String anonKey;
    private static final Object LOCK = new Object();

    /** One client for every upload: a new one per photo opened a new TLS
     *  connection (and its threads) each time — a sheet of labels uploads
     *  dozens of fichas in a row. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private SupabaseStorage() {}

    /** True when Supabase Storage is configured and the app is online. */
    public static boolean isAvailable() {
        ensureInit();
        return supabaseUrl != null && anonKey != null
            && !com.sibim.db.DatabaseConfig.isOfflineMode()
            && !com.sibim.db.DatabaseConfig.isDemoMode();
    }

    /**
     * Uploads a photo (or a factura) to Supabase Storage under {@code remoteName}
     * (e.g. "abc123_0.jpg") and returns its public URL. A photo always gets a
     * new name, so it is only ever added, never replaced: the bucket can refuse
     * replacements (scripts/sql/storage_fotos.sql), and whoever holds the public
     * key that every installer carries can't overwrite the inventory's photos.
     * The same name uploaded again (a retry after an upload that did get
     * through) counts as done.
     */
    public static String upload(File localFile, String remoteName) throws IOException {
        return enviar(localFile, remoteName, "image/jpeg", false);
    }

    /** A file replaced in place under a fixed name: the ficha PDF of a bien
     *  (fichas/&lt;id&gt;.pdf), which its QR code points to. */
    public static String upload(File localFile, String remoteName, String contentType) throws IOException {
        return enviar(localFile, remoteName, contentType, true);
    }

    private static String enviar(File localFile, String remoteName, String contentType, boolean reemplazar)
            throws IOException {
        ensureInit();
        if (supabaseUrl == null || anonKey == null)
            throw new IOException("SUPABASE_URL o SUPABASE_ANON_KEY no están en .env");

        byte[] bytes = Files.readAllBytes(localFile.toPath());

        HttpRequest.Builder req = HttpRequest.newBuilder()
            .uri(URI.create(supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + remoteName))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer " + anonKey)
            .header("Content-Type", contentType)
            // Without it a stalled upload (connected, nothing moving) never ends.
            .timeout(Duration.ofSeconds(90));
        if (reemplazar) req.header("x-upsert", "true").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        else            req.POST(HttpRequest.BodyPublishers.ofByteArray(bytes));

        try {
            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            if (!ok && (reemplazar || !yaExiste(resp.statusCode(), resp.body())))
                throw new IOException("Storage " + resp.statusCode() + ": " + resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Carga de imagen interrumpida", e);
        }

        return publicUrl(remoteName);
    }

    /** Storage's answer when the object is already there: 409, or a 400 whose
     *  body says "Duplicate" (the error code the storage API puts in the JSON). */
    static boolean yaExiste(int status, String cuerpo) {
        return status == 409 || (cuerpo != null && (cuerpo.contains("\"Duplicate\"") || cuerpo.contains("already exists")));
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
            // Same .env the connection uses (this PC's, the installed one or
            // the working directory's), then the environment.
            Dotenv conf = com.sibim.db.DatabaseConfig.dotenv();
            supabaseUrl = com.sibim.db.DatabaseConfig.setting("SUPABASE_URL", null);
            anonKey     = com.sibim.db.DatabaseConfig.setting("SUPABASE_ANON_KEY", null);
            if (supabaseUrl != null) supabaseUrl = supabaseUrl.trim();
            if (anonKey     != null) anonKey     = anonKey.trim();
            if (esClaveSecreta(anonKey)) {
                log.error("SUPABASE_ANON_KEY contiene una clave secreta/service_role, que ignora todas las "
                    + "políticas de Storage y de la base. No se usará: pon la clave pública (anon) y rota la secreta.");
                anonKey = null;
            }
            for (String clave : new String[]{"SUPABASE_SERVICE_KEY", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY"}) {
                if (conf.get(clave) != null) {
                    log.error("El .env de esta PC contiene {}: una clave de servicio de Supabase da control total "
                        + "del proyecto a quien abra el archivo. SIBIM no la usa; bórrala del .env y rótala en Supabase.", clave);
                }
            }
            initialized = true;
        }
    }

    /** True for a Supabase key that bypasses Row Level Security: the new
     *  "sb_secret_…" format, or a legacy JWT whose role is service_role. */
    static boolean esClaveSecreta(String key) {
        if (key == null || key.isBlank()) return false;
        if (key.startsWith("sb_secret_")) return true;
        String[] partes = key.split("\\.");
        if (partes.length != 3) return false;
        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(partes[1]),
                java.nio.charset.StandardCharsets.UTF_8);
            return payload.replace(" ", "").contains("\"role\":\"service_role\"");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
