package com.sibim.service;

import com.sun.jna.platform.win32.Crypt32Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Only installers signed by TI go out through SIBIM's update channel.
 *
 * The installer travels through the database (see {@link ActualizacionService})
 * and every PC's .env can write there, so its SHA-256 alone only proves the
 * download arrived whole — not who published it. TI signs "version + SHA-256"
 * with an Ed25519 private key that never leaves the PC that builds the
 * installers (sealed with Windows DPAPI). Every build carries the public key
 * ({@value #RECURSO_CLAVE_PUBLICA}), and a PC only offers and runs an update
 * whose signature checks out against it.
 *
 * From the command line (packaging\build-installer.ps1 runs "firmar"):
 * <pre>
 *   java -cp sibim-desktop.jar com.sibim.service.FirmaActualizaciones generar [clave.pub] [--reemplazar]
 *   java -cp sibim-desktop.jar com.sibim.service.FirmaActualizaciones firmar &lt;instalador&gt; &lt;versión&gt;
 * </pre>
 */
public final class FirmaActualizaciones {

    private static final Logger log = LoggerFactory.getLogger(FirmaActualizaciones.class);

    static final String RECURSO_CLAVE_PUBLICA = "/actualizaciones-firma.pub";
    private static final String ALGORITMO = "Ed25519";
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** What build-installer.ps1 leaves next to the installer ("&lt;instalador&gt;.firma"). */
    public record Firma(String version, String sha256, String firma) {}

    /** Null when this build carries no public key: then nothing validates. */
    private final PublicKey clavePublica;

    private FirmaActualizaciones(PublicKey clavePublica) { this.clavePublica = clavePublica; }

    /** Checks against {@code clavePublica} (tests use their own pair). */
    public static FirmaActualizaciones conClave(PublicKey clavePublica) {
        return new FirmaActualizaciones(clavePublica);
    }

    /** Checks against the public key this build carries. */
    public static FirmaActualizaciones deEstaVersion() {
        try (InputStream in = FirmaActualizaciones.class.getResourceAsStream(RECURSO_CLAVE_PUBLICA)) {
            if (in == null) return new FirmaActualizaciones(null);
            String base64 = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
            return new FirmaActualizaciones(clavePublica(base64));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.error("La clave pública de las actualizaciones está dañada: no se instalará ninguna", e);
            return new FirmaActualizaciones(null);
        }
    }

    public boolean tieneClave() { return clavePublica != null; }

    /** True only if TI signed exactly this version and this SHA-256. */
    public boolean valida(String version, String sha256, String firma) {
        if (clavePublica == null || version == null || sha256 == null || firma == null) return false;
        if (!VERSION.matcher(version).matches() || !SHA256.matcher(sha256).matches()) return false;
        try {
            Signature s = Signature.getInstance(ALGORITMO);
            s.initVerify(clavePublica);
            s.update(mensaje(version, sha256));
            return s.verify(Base64.getDecoder().decode(firma));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String firmar(PrivateKey clave, String version, String sha256) throws GeneralSecurityException {
        Signature s = Signature.getInstance(ALGORITMO);
        s.initSign(clave);
        s.update(mensaje(version, sha256));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    /** What gets signed: the version too, so an older installer can't be
     *  passed off as a newer one. */
    private static byte[] mensaje(String version, String sha256) {
        return ("SIBIM-actualizacion\n" + version + "\n" + sha256).getBytes(StandardCharsets.UTF_8);
    }

    private static PublicKey clavePublica(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance(ALGORITMO).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    // ── The .firma file next to the installer ──────────────────────────────

    public static File archivoFirma(File instalador) { return new File(instalador.getPath() + ".firma"); }

    public static Firma leer(File archivo) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(archivo.toPath(), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return new Firma(p.getProperty("version"), p.getProperty("sha256"), p.getProperty("firma"));
    }

    public static void escribir(File archivo, Firma f) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", f.version());
        p.setProperty("sha256", f.sha256());
        p.setProperty("firma", f.firma());
        try (Writer w = Files.newBufferedWriter(archivo.toPath(), StandardCharsets.UTF_8)) {
            p.store(w, "Firma de TI: Configuracion > Publicar actualizacion solo acepta el instalador con este archivo al lado");
        }
    }

    // ── The private key, on the PC that builds the installers ──────────────

    /** Sealed with DPAPI: only this Windows account on this PC can sign. */
    static Path archivoClavePrivada() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
            ? Path.of(appData, "SIBIM") : Path.of(System.getProperty("user.home"), ".sibim");
        return base.resolve("firma-actualizaciones.key");
    }

    public static void main(String[] args) throws Exception {
        List<String> a = Arrays.asList(args);
        String comando = a.isEmpty() ? "" : a.get(0);
        switch (comando) {
            case "generar" -> generar(a.size() > 1 && !a.get(1).startsWith("--")
                    ? Path.of(a.get(1)) : Path.of("src", "main", "resources", RECURSO_CLAVE_PUBLICA.substring(1)),
                a.contains("--reemplazar"));
            case "firmar" -> {
                if (a.size() < 3) salir(1, "Uso: firmar <instalador> <versión>");
                firmarInstalador(new File(a.get(1)), a.get(2));
            }
            default -> salir(1, "Uso: generar [clave.pub] [--reemplazar] | firmar <instalador> <versión>");
        }
    }

    private static void generar(Path destinoPublica, boolean reemplazar) throws Exception {
        Path privada = archivoClavePrivada();
        if (Files.exists(privada) && !reemplazar)
            salir(1, "Ya existe " + privada + ". Con una llave nueva, la siguiente versión hay que instalarla "
                + "a mano en cada PC; si es lo que quieres, agrega --reemplazar.");
        KeyPair par = KeyPairGenerator.getInstance(ALGORITMO).generateKeyPair();
        byte[] pkcs8 = par.getPrivate().getEncoded();
        try {
            Files.createDirectories(privada.getParent());
            Files.write(privada, Crypt32Util.cryptProtectData(pkcs8));
        } finally {
            Arrays.fill(pkcs8, (byte) 0);
        }
        Files.createDirectories(destinoPublica.toAbsolutePath().getParent());
        Files.writeString(destinoPublica,
            Base64.getEncoder().encodeToString(par.getPublic().getEncoded()) + System.lineSeparator(),
            StandardCharsets.US_ASCII);
        System.out.println("Llave privada: " + privada + " (solo esta cuenta de Windows en esta PC puede usarla)");
        System.out.println("Clave pública: " + destinoPublica + " (va dentro de la app: súbela al repositorio)");
    }

    private static void firmarInstalador(File instalador, String version) throws Exception {
        if (!VERSION.matcher(version).matches()) salir(1, "Versión inválida: " + version);
        if (!instalador.isFile()) salir(1, "No existe " + instalador);
        Path privada = archivoClavePrivada();
        if (!Files.exists(privada))
            salir(2, "No hay llave de firma en " + privada + ": este instalador no se podrá publicar por SIBIM.");
        // The build being packaged must carry the public key, or the PCs that
        // install it could never verify (and so never take) another update.
        FirmaActualizaciones propia = deEstaVersion();
        if (!propia.tieneClave())
            salir(3, "Esta compilación no trae " + RECURSO_CLAVE_PUBLICA + ": las PCs que la instalen ya no "
                + "podrían actualizarse solas. Genera la llave y vuelve a compilar.");
        byte[] pkcs8 = Crypt32Util.cryptUnprotectData(Files.readAllBytes(privada));
        PrivateKey clave;
        try {
            clave = KeyFactory.getInstance(ALGORITMO).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } finally {
            Arrays.fill(pkcs8, (byte) 0);
        }
        String sha = ActualizacionService.sha256(instalador);
        Firma f = new Firma(version, sha, firmar(clave, version, sha));
        if (!propia.valida(f.version(), f.sha256(), f.firma()))
            salir(3, "La llave de esta PC no corresponde a la clave pública de esta compilación ("
                + RECURSO_CLAVE_PUBLICA + "): las PCs rechazarían la firma.");
        escribir(archivoFirma(instalador), f);
        System.out.println("Firmado: " + archivoFirma(instalador));
    }

    private static void salir(int codigo, String mensaje) {
        System.err.println(mensaje);
        System.exit(codigo);
    }
}
