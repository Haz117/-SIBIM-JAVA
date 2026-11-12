package com.sibim.service;

import com.sibim.model.Producto;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

/** Bienes › «Fotos repetidas»: the same picture on bienes that are not the same lot. On
 *  11-nov-2026 there were 85 such pictures: most were one photo for 19 equal chairs, but some
 *  were the photo of another bien (a fan with the picture of two industrial fans) or the same
 *  chairs in two áreas. A picture is told by its content (MD5), not by its file name.
 *
 *  <p>The content comes from the daily backup's copy of the photos when this PC has it, and
 *  otherwise is downloaded once; what was already read is kept on this PC. */
public final class FotosRepetidas {

    private FotosRepetidas() {}

    /** One picture and the bienes that have it. */
    public record Grupo(String huella, String url, List<Producto> bienes) {
        /** Pieces of one lot (same área and person, names that say the same thing: «SWITCH
         *  ADMINISTRABLE 8 PUERTOS» and «REDES SWITCH ADMINISTRABLE 8 PUERTOS») share a photo fine. */
        public boolean distintos() {
            if (bienes.stream().map(p -> texto(p.getArea()) + "|" + texto(p.getResguardante())).distinct().count() > 1) return true;
            for (Producto a : bienes) for (Producto b : bienes) if (!parecidos(a.getNombre(), b.getNombre())) return true;
            return false;
        }

        /** Some names say different things: usually the photo of another bien. */
        boolean nombresDistintos() {
            for (Producto a : bienes) for (Producto b : bienes) if (!parecidos(a.getNombre(), b.getNombre())) return true;
            return false;
        }
    }

    /** Words every name has: «VENTILADOR DE PEDESTAL» and «VENTILADOR INDUSTRIAL DE PISO» are
     *  not alike because both say «DE». */
    private static final Set<String> VACIAS = Set.of("DE", "DEL", "LA", "EL", "LOS", "LAS", "CON", "PARA", "Y", "EN", "A", "P");

    static boolean parecidos(String a, String b) {
        Set<String> x = new HashSet<>(Arrays.asList(texto(a).split(" "))), y = new HashSet<>(Arrays.asList(texto(b).split(" ")));
        x.remove(""); y.remove("");
        x.removeAll(VACIAS); y.removeAll(VACIAS);
        if (x.isEmpty() || y.isEmpty()) return x.equals(y);
        Set<String> comun = new HashSet<>(x);
        comun.retainAll(y);
        return comun.size() >= 0.6 * Math.min(x.size(), y.size());
    }

    /** Groups the photos by content, leaving out a picture shared only inside one lot.
     *  Pictures on bienes with different names first: those are usually a wrong photo. */
    public static List<Grupo> agrupar(Map<String, List<String>> fotosPorBien, Map<String, String> huellaPorUrl,
                                      Map<String, Producto> bienes) {
        Map<String, LinkedHashMap<String, Producto>> porHuella = new LinkedHashMap<>();
        Map<String, String> urlDe = new HashMap<>();
        fotosPorBien.forEach((id, urls) -> {
            Producto p = bienes.get(id);
            if (p == null || p.getFechaBaja() != null) return;
            for (String u : urls) {
                String h = huellaPorUrl.get(u);
                if (h == null) continue;
                porHuella.computeIfAbsent(h, k -> new LinkedHashMap<>()).put(id, p);
                urlDe.putIfAbsent(h, u);
            }
        });
        List<Grupo> out = new ArrayList<>();
        porHuella.forEach((h, ps) -> {
            if (ps.size() < 2) return;
            Grupo g = new Grupo(h, urlDe.get(h), new ArrayList<>(ps.values()));
            if (g.distintos()) out.add(g);
        });
        out.sort(Comparator.comparing((Grupo g) -> !g.nombresDistintos()).thenComparing(g -> -g.bienes().size()));
        return out;
    }

    static String texto(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .replaceAll("[^\\p{Alnum}]+", " ").trim().toUpperCase(Locale.ROOT);
    }

    // ── Huellas ───────────────────────────────────────────────────────────────

    /** Where the daily backup keeps the photos on this PC (scripts/respaldo_diario.ps1). */
    public static Path carpetaRespaldo() {
        String appdata = System.getenv("APPDATA");
        return appdata == null ? null : Path.of(appdata, "SIBIM", "respaldos", "fotos");
    }

    private static Path cache() {
        return Path.of(System.getProperty("user.home"), ".sibim", "huellas-fotos.properties");
    }

    /** The MD5 of each photo. Unreadable ones are left out (and counted in {@code sinLeer}). */
    public static Map<String, String> huellas(Collection<String> urls, DoubleConsumer avance, int[] sinLeer) throws IOException {
        Properties guardadas = new Properties();
        Path cache = cache();
        if (Files.exists(cache)) try (Reader r = Files.newBufferedReader(cache)) { guardadas.load(r); }
        Path respaldo = carpetaRespaldo();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
        Map<String, String> out = new LinkedHashMap<>();
        List<String> todas = new ArrayList<>(new LinkedHashSet<>(urls));
        int hechas = 0, nuevas = 0;
        for (String url : todas) {
            String h = guardadas.getProperty(url);
            if (h == null) {
                try {
                    h = md5(abrir(url, respaldo, http));
                    guardadas.setProperty(url, h);
                    nuevas++;
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    sinLeer[0]++;
                }
            }
            if (h != null) out.put(url, h);
            if (avance != null) avance.accept(++hechas / (double) todas.size());
            if (nuevas > 0 && nuevas % 200 == 0) guardar(guardadas, cache);
        }
        if (nuevas > 0) guardar(guardadas, cache);
        return out;
    }

    private static InputStream abrir(String url, Path respaldo, HttpClient http) throws IOException, InterruptedException {
        Path local = enRespaldo(url, respaldo);
        if (local != null && Files.isRegularFile(local) && Files.size(local) > 0) return Files.newInputStream(local);
        Path propio = Path.of(url);
        if (!url.startsWith("http") && Files.isRegularFile(propio)) return Files.newInputStream(propio);
        String completa = url.startsWith("http") ? url : com.sibim.util.SupabaseStorage.publicUrl(url);
        HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(completa)).timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) { r.body().close(); throw new IOException("HTTP " + r.statusCode()); }
        return r.body();
    }

    private static final String BUCKET = "/sibim-fotos/";

    /** Where the daily backup keeps this photo: its name in the bucket, folders included and
     *  decoded, as scripts/comun.ps1 (Ruta-Local) writes it. Null when it is no Storage URL
     *  or the name would leave the folder. */
    public static Path enRespaldo(String url, Path respaldo) {
        if (respaldo == null || url == null) return null;
        int i = url.indexOf(BUCKET);
        if (i < 0) return null;
        String nombre = url.substring(i + BUCKET.length());
        int q = nombre.indexOf('?');
        if (q >= 0) nombre = nombre.substring(0, q);
        try {
            nombre = java.net.URLDecoder.decode(nombre.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (nombre.isBlank() || nombre.contains("..") || nombre.startsWith("/")) return null;
        try {
            Path p = respaldo.resolve(nombre).normalize();
            return p.startsWith(respaldo.normalize()) ? p : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    static String md5(InputStream in) throws IOException {
        try (in) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void guardar(Properties p, Path cache) {
        try {
            Files.createDirectories(cache.getParent());
            try (Writer w = Files.newBufferedWriter(cache)) { p.store(w, "SIBIM: huella (MD5) de cada foto ya revisada"); }
        } catch (IOException ignored) {
            // next time they are read again
        }
    }

    /** All the photo URLs of {@code fotosPorBien}. */
    public static Set<String> urls(Map<String, List<String>> fotosPorBien) {
        return fotosPorBien.values().stream().flatMap(List::stream).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
