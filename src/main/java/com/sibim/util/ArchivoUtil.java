package com.sibim.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * The one way the app opens a file, a folder or a web page with the system.
 *
 * <p>{@code java.awt.Desktop} fails on some Windows PCs (and must not run on
 * the JavaFX thread anyway): every place that called it directly showed a
 * generic error, or nothing at all, while the file was fine. Here the call
 * runs in the background and, if Java can't do it, falls back to the same
 * handler Windows Explorer uses. The real cause goes to the log.
 */
public final class ArchivoUtil {

    private static final Logger log = LoggerFactory.getLogger(ArchivoUtil.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private ArchivoUtil() {}

    /** Opens {@code archivo} with its default program (PDF viewer, Excel…). */
    public static void abrir(File archivo, Scene scene) {
        abrir(archivo, scene, null);
    }

    /** @param alAbrir shown as an info toast once it opened (e.g. how to print); may be null */
    public static void abrir(File archivo, Scene scene, String alAbrir) {
        if (archivo == null || !archivo.exists()) {
            NotificacionUtil.error(scene, "El archivo ya no existe: vuelve a generarlo.");
            return;
        }
        AppExecutor.submit(() -> {
            String error = abrirAhora(archivo);
            Platform.runLater(() -> {
                if (error == null) {
                    if (alAbrir != null) NotificacionUtil.info(scene, alAbrir);
                } else {
                    NotificacionUtil.error(scene, "No se pudo abrir " + archivo.getName() + ". " + error);
                }
            });
        });
    }

    /** Opens the viewer so the user prints from it with its own dialog
     *  (printer, pages, copies) — sending straight to the default printer
     *  with no choice would surprise anyone pressing "Imprimir". */
    public static void imprimir(File archivo, Scene scene) {
        abrir(archivo, scene, "Se abrió el documento: imprímelo desde el visor con Ctrl+P.");
    }

    /** Opens the folder with {@code archivo} selected (Windows) or the folder itself. */
    public static void mostrarEnCarpeta(File archivo, Scene scene) {
        AppExecutor.submit(() -> {
            try {
                if (WINDOWS) {
                    new ProcessBuilder("explorer.exe", "/select,", archivo.getAbsolutePath()).start();
                    return;
                }
                String error = abrirAhora(archivo.getParentFile());
                if (error != null) Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo abrir la carpeta. " + error));
            } catch (Exception e) {
                log.warn("No se pudo mostrar {} en su carpeta", archivo, e);
                Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo abrir la carpeta."));
            }
        });
    }

    /** Opens a web page in the default browser. */
    public static void navegar(String url, Scene scene) {
        AppExecutor.submit(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    return;
                }
            } catch (Exception e) {
                log.debug("Desktop.browse falló para {}: {}", url, e.getMessage());
            }
            try {
                if (WINDOWS) { new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start(); return; }
            } catch (Exception e) {
                log.warn("No se pudo abrir {}", url, e);
            }
            Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo abrir el navegador. Dirección: " + url));
        });
    }

    /** Blocking; call off the FX thread. @return null when it opened, else what to tell the user. */
    static String abrirAhora(File archivo) {
        Exception primero = null;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(archivo);
                return null;
            }
        } catch (Exception e) {
            primero = e;
        }
        if (WINDOWS) {
            try {
                Process p = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", archivo.getAbsolutePath()).start();
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() == 0) return null;
            } catch (Exception e) {
                if (primero == null) primero = e;
            }
        }
        log.warn("No se pudo abrir {} con el programa predeterminado", archivo, primero);
        return "Revisa que haya un programa para abrir archivos ." + extension(archivo)
            + " (por ejemplo un visor de PDF) o ábrelo desde " + archivo.getParent();
    }

    private static String extension(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i + 1).toLowerCase() : "";
    }
}
