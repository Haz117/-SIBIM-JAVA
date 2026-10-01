package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sun.jna.platform.win32.Crypt32Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A daily encrypted backup made by the administrator's PC on its own (see
 * {@link SchedulerService}), into {@code %APPDATA%\SIBIM\respaldos}.
 *
 * Backups are always encrypted (they hold every password hash), so the
 * password has to be available without anyone typing it: the administrator
 * sets it once and it is kept on this PC sealed with Windows DPAPI — only
 * this Windows account on this PC can read it back. To restore, the
 * administrator types that same password, on any PC.
 */
public final class RespaldoAutomatico {

    private static final Logger log = LoggerFactory.getLogger(RespaldoAutomatico.class);

    /** Daily files kept; older ones are deleted. */
    static final int CONSERVAR = 30;
    private static final String PREFIJO = "sibim_auto_";

    private RespaldoAutomatico() {}

    private static Path base() {
        String appData = System.getenv("APPDATA");
        return appData != null && !appData.isBlank()
            ? Path.of(appData, "SIBIM") : Path.of(System.getProperty("user.home"), ".sibim");
    }

    public static Path carpeta() { return base().resolve("respaldos"); }

    private static Path archivoClave() { return base().resolve("respaldo-auto.bin"); }

    /** DPAPI exists only on Windows; elsewhere the automatic backup is not offered. */
    public static boolean soportado() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean activado() { return soportado() && Files.exists(archivoClave()); }

    /** Turns the daily backup on with {@code password} (kept sealed on this PC). */
    public static void activar(char[] password) throws IOException {
        if (!soportado()) throw new IOException("El respaldo automático solo está disponible en Windows");
        byte[] plano = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(base());
            Files.write(archivoClave(), Crypt32Util.cryptProtectData(plano));
        } finally {
            Arrays.fill(plano, (byte) 0);
        }
    }

    public static void desactivar() throws IOException { Files.deleteIfExists(archivoClave()); }

    /** The newest automatic backup on this PC, or null. */
    public static File ultimo() {
        List<Path> archivos = existentes();
        return archivos.isEmpty() ? null : archivos.get(archivos.size() - 1).toFile();
    }

    /** Makes today's backup if it is on, there is a connection and it isn't done yet. */
    public static void ejecutarSiToca(LocalDate hoy) {
        if (!activado() || DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        Path destino = carpeta().resolve(PREFIJO + hoy + ".json");
        if (Files.exists(destino)) return;
        char[] password = null;
        try {
            password = new String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(archivoClave())),
                StandardCharsets.UTF_8).toCharArray();
            Files.createDirectories(carpeta());
            Path temporal = carpeta().resolve(PREFIJO + hoy + ".tmp");
            new BackupService().backup(temporal.toFile(), password);
            Files.move(temporal, destino);
            log.info("Respaldo automático guardado en {}", destino);
            List<Path> archivos = existentes();
            for (int i = 0; i < archivos.size() - CONSERVAR; i++) Files.deleteIfExists(archivos.get(i));
        } catch (Exception e) {
            log.error("No se pudo hacer el respaldo automático de hoy", e);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    /** This PC's automatic backups, oldest first (the date is in the name). */
    private static List<Path> existentes() {
        if (!Files.isDirectory(carpeta())) return List.of();
        try (Stream<Path> s = Files.list(carpeta())) {
            return s.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith(PREFIJO) && n.endsWith(".json");
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
