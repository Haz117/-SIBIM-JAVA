package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * The municipality logo used on the splash screen and every PDF/Excel
 * formato. Sources, in order:
 * <ol>
 *   <li>{@code logo_base64} in configuracion — the image itself, saved from
 *       Configuración, so every PC uses the same logo;</li>
 *   <li>{@code logo_path} — a local path, how the logo was configured before;
 *       only works on the PC that picked the file;</li>
 *   <li>the logo shipped with the app ({@link ReporteService#BUNDLED_LOGO}).</li>
 * </ol>
 */
public final class LogoMunicipal {

    private static final Logger log = LoggerFactory.getLogger(LogoMunicipal.class);

    public static final String CLAVE_IMAGEN = "logo_base64";
    public static final String CLAVE_RUTA   = "logo_path";
    /** A logo is a small image; this keeps the configuracion row reasonable. */
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private static String ultimoBase64;
    private static String ultimoArchivo;

    private LogoMunicipal() {}

    /** A local file with the logo to use, or null when there is none at all. */
    public static String rutaLocal(ConfiguracionRepository config) {
        try {
            String b64 = config.get(CLAVE_IMAGEN, "");
            if (!b64.isBlank()) {
                String archivo = archivoDe(b64);
                if (archivo != null) return archivo;
            }
            String ruta = config.get(CLAVE_RUTA, "");
            if (!ruta.isBlank() && new File(ruta).exists()) return ruta;
        } catch (Exception e) {
            log.warn("No se pudo leer el logo de la configuración: {}", e.getMessage());
        }
        return ReporteService.bundledLogoPath();
    }

    /** Reads {@code archivo} for {@link #CLAVE_IMAGEN}. */
    public static String codificar(Path archivo) throws java.io.IOException {
        long tam = Files.size(archivo);
        if (tam > MAX_BYTES)
            throw new IllegalArgumentException("El logo pesa más de " + (MAX_BYTES / (1024 * 1024))
                + " MB; usa una imagen más pequeña");
        return Base64.getEncoder().encodeToString(Files.readAllBytes(archivo));
    }

    /** Decoded once per distinct image into a temp file (report code wants a path). */
    private static synchronized String archivoDe(String b64) {
        if (b64.equals(ultimoBase64) && ultimoArchivo != null && new File(ultimoArchivo).exists())
            return ultimoArchivo;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            Path tmp = Files.createTempFile("sibim-logo-config-", ".img");
            tmp.toFile().deleteOnExit();
            Files.write(tmp, bytes);
            ultimoBase64 = b64;
            ultimoArchivo = tmp.toString();
            return ultimoArchivo;
        } catch (Exception e) {
            log.warn("El logo guardado en la configuración no se pudo leer: {}", e.getMessage());
            return null;
        }
    }
}
