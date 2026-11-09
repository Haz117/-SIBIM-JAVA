package com.sibim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * One SIBIM per Windows user. Opened a second time — a double click on the icon
 * while the first one is still loading, or with it minimized — the new one asks
 * the running one to come to the front and closes, instead of starting next to it.
 *
 * <p>9-nov-2026: the only guard was the splash's probe of the offline store, and
 * only in offline mode. Online two SIBIM ran side by side and fought over
 * {@code offline.db.work}: «no se pudo refrescar el caché local» every few
 * seconds, and the offline copy of the computer left behind (30 times in this
 * PC's logs). Now a lock on {@code ~/.sibim/sibim.lock} — the folder of the store
 * it protects — is held while the app runs; the OS frees it if SIBIM dies.
 * The running one listens on a loopback port written next to it, for "mostrar".
 */
public final class InstanciaUnica {

    private static final Logger log = LoggerFactory.getLogger(InstanciaUnica.class);
    static final String MOSTRAR = "SIBIM-MOSTRAR";

    /** What a launch found: alone, or another SIBIM that was (or couldn't be) asked to show itself. */
    public enum Resultado { UNICA, LA_OTRA_SE_MOSTRO, LA_OTRA_NO_CONTESTO }

    private static FileChannel canal;
    private static FileLock candado;
    private static ServerSocket escucha;

    private InstanciaUnica() {}

    /** For the app: the lock in {@code ~/.sibim}. */
    public static Resultado adquirir(Runnable alPedirMostrar) {
        return adquirir(Path.of(System.getProperty("user.home"), ".sibim"), alPedirMostrar);
    }

    /** @param alPedirMostrar run (on the listener's thread) when a later launch asks this one to show */
    static synchronized Resultado adquirir(Path dir, Runnable alPedirMostrar) {
        if (candado != null && candado.isValid()) return Resultado.UNICA;   // this same process, again
        try {
            Files.createDirectories(dir);
            canal = FileChannel.open(dir.resolve("sibim.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                candado = canal.tryLock();
            } catch (OverlappingFileLockException e) {
                return Resultado.UNICA;   // held by this JVM already
            }
            if (candado == null) {
                canal.close();
                canal = null;
                return pedirALaOtra(dir) ? Resultado.LA_OTRA_SE_MOSTRO : Resultado.LA_OTRA_NO_CONTESTO;
            }
            escuchar(dir, alPedirMostrar);
            return Resultado.UNICA;
        } catch (IOException e) {
            // Without a lock SIBIM still opens, as before this guard existed.
            log.warn("No se pudo comprobar si SIBIM ya está abierto: {}", e.getMessage());
            return Resultado.UNICA;
        }
    }

    /** The running one: a loopback port, written to {@code sibim.puerto} (the locked
     *  file itself can't be read by another process on Windows). */
    private static void escuchar(Path dir, Runnable alPedirMostrar) {
        try {
            escucha = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            Files.writeString(dir.resolve("sibim.puerto"), String.valueOf(escucha.getLocalPort()), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            log.warn("SIBIM no podrá traer su ventana al frente si se abre otra vez: {}", e.getMessage());
            return;
        }
        Thread t = new Thread(() -> {
            while (!escucha.isClosed()) {
                try (Socket s = escucha.accept()) {
                    s.setSoTimeout(2000);
                    String linea = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                    if (MOSTRAR.equals(linea) && alPedirMostrar != null) alPedirMostrar.run();
                } catch (IOException e) {
                    if (escucha.isClosed()) return;
                }
            }
        }, "sibim-instancia-unica");
        t.setDaemon(true);
        t.start();
    }

    /** The later launch: asks the running one to show itself. False when it didn't answer. */
    private static boolean pedirALaOtra(Path dir) {
        try {
            int puerto = Integer.parseInt(Files.readString(dir.resolve("sibim.puerto"), StandardCharsets.US_ASCII).trim());
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 1500);
                OutputStream out = s.getOutputStream();
                out.write((MOSTRAR + "\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
            return true;
        } catch (Exception e) {
            log.info("Hay otro SIBIM abierto pero no contestó: {}", e.getMessage());
            return false;
        }
    }

    /** Lets go of the lock (tests; the OS does it when the app ends). */
    static synchronized void soltar() {
        try { if (escucha != null) escucha.close(); } catch (IOException ignored) { /* closing */ }
        try { if (candado != null) candado.release(); } catch (IOException ignored) { /* closing */ }
        try { if (canal != null) canal.close(); } catch (IOException ignored) { /* closing */ }
        escucha = null; candado = null; canal = null;
    }
}
