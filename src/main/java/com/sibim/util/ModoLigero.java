package com.sibim.util;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.Preferences;

/**
 * Light mode, for computers that struggle to draw SIBIM: no shadows and no animations.
 * The shadows are what takes longest to draw without a good graphics card — measured on
 * 6-nov-2026 drawing in software: nine screen changes froze 5.5 s with them and 2.1 s
 * without; the tutorial's first nine steps, 5.8 s and 0.8 s. With a graphics card both
 * look and move as always.
 *
 * <p>It turns itself on when JavaFX has no graphics acceleration, or when screen changes
 * are seen to come out at a few frames per second (then it stays on in that computer).
 * Whoever prefers otherwise chooses in Configuración; that choice is kept per computer
 * and wins over the detection.
 */
public final class ModoLigero {

    private static final Logger log = LoggerFactory.getLogger(ModoLigero.class);

    /** On the root of every window while light mode is on (see 23-modo-ligero.css). */
    public static final String CLASE = "modo-ligero";

    private static final Preferences PREFS = Preferences.userRoot().node("sibim/ui/rendimiento");
    /** What the person chose in Configuración, if they did. */
    private static final String ELEGIDO = "ligero";
    /** SIBIM saw this computer stutter. */
    private static final String DETECTADO = "lentaDetectada";

    /** A screen change below this many frames per second counts as slow. */
    static final double CUADROS_LENTO = 12;
    /** Slow screen changes, out of the first ones measured, that turn it on. */
    static final int CAMBIOS_LENTOS = 3;
    static final int CAMBIOS_A_MEDIR = 6;

    private static volatile boolean activo = false;
    private static boolean instalado = false;
    private static int medidos = 0, lentos = 0;

    private ModoLigero() {}

    public static boolean activo() { return activo; }

    /** On by SIBIM's own decision: nobody chose in this computer. */
    public static boolean automatico() { return activo && elegido() == null; }

    /** At start, on the JavaFX thread: decides, and follows every window that opens. */
    public static void instalar() {
        if (instalado) return;
        instalado = true;
        cambiar(decidir(elegido(), sinAceleracion(), detectado()));
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) for (Window w : c.getAddedSubList()) seguir(w);
        });
        for (Window w : Window.getWindows()) seguir(w);
        if (activo) log.info("Modo ligero: {}", automatico() ? (sinAceleracion() ? "sin aceleración gráfica" : "computadora lenta") : "elegido");
    }

    static boolean decidir(Boolean elegido, boolean sinAceleracion, boolean detectado) {
        if (elegido != null) return elegido;
        return sinAceleracion || detectado;
    }

    /** Configuración: the person's choice, kept in this computer. */
    public static void elegir(boolean ligero) {
        try { PREFS.putBoolean(ELEGIDO, ligero); } catch (Exception e) { log.debug("Sin preferencias: {}", e.getMessage()); }
        cambiar(ligero);
    }

    /** JavaFX draws in software (no 3D then): no graphics card, remote desktop, old driver. */
    static boolean sinAceleracion() {
        try { return !Platform.isSupported(ConditionalFeature.SCENE3D); }
        catch (Exception e) { return false; }
    }

    private static Boolean elegido() {
        try {
            String v = PREFS.get(ELEGIDO, null);
            return v == null ? null : Boolean.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean detectado() {
        try { return PREFS.getBoolean(DETECTADO, false); } catch (Exception e) { return false; }
    }

    private static void cambiar(boolean v) {
        activo = v;
        AnimationUtils.setLigero(v);
        for (Window w : Window.getWindows()) if (w.getScene() != null) marcar(w.getScene().getRoot());
    }

    private static void seguir(Window w) {
        aplicar(w.getScene());
        w.sceneProperty().addListener((o, antes, s) -> aplicar(s));
    }

    private static void aplicar(Scene s) {
        if (s == null) return;
        marcar(s.getRoot());
        s.rootProperty().addListener((o, antes, r) -> marcar(r));
    }

    private static void marcar(Parent r) {
        if (r == null) return;
        if (!activo) r.getStyleClass().remove(CLASE);
        else if (!r.getStyleClass().contains(CLASE)) r.getStyleClass().add(CLASE);
    }

    // ── Detection ────────────────────────────────────────────────────────────

    /** Called when a screen comes in, as it starts its entrance: counts the frames drawn
     *  while it moves. The first change is left out (classes still loading). */
    public static void medirCambio(Scene scene) {
        if (activo || scene == null || elegido() != null || medidos >= CAMBIOS_A_MEDIR + 1) return;
        if (!AnimationUtils.isEnabled() || Boolean.getBoolean("sibim.test")) return;
        if (medidos++ == 0) return;
        long inicio = System.nanoTime();
        int[] cuadros = {0};
        Runnable oyente = () -> cuadros[0]++;
        scene.addPostLayoutPulseListener(oyente);
        javafx.animation.PauseTransition fin = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        fin.setOnFinished(e -> {
            scene.removePostLayoutPulseListener(oyente);
            double segundos = (System.nanoTime() - inicio) / 1e9;
            registrar(cuadros[0] / segundos);
        });
        fin.play();
    }

    static void registrar(double cuadrosPorSegundo) {
        if (cuadrosPorSegundo < CUADROS_LENTO) lentos++;
        log.debug("Cambio de pantalla a {} cuadros/s ({} lentos)", Math.round(cuadrosPorSegundo), lentos);
        if (lentos >= CAMBIOS_LENTOS && !activo && elegido() == null) {
            try { PREFS.putBoolean(DETECTADO, true); } catch (Exception ignored) { /* on for this session anyway */ }
            log.info("Modo ligero: los cambios de pantalla salen a pocos cuadros por segundo en esta computadora");
            cambiar(true);
        }
    }

    /** For the tests: back to the start. */
    static void reiniciarConteo() { medidos = 0; lentos = 0; }
}
