package com.sibim.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.util.Duration;
import java.util.prefs.Preferences;

/** The motion SIBIM keeps: feedback for something the user just did (a field
 *  shakes on an error, a saved row flashes, a toast slides in and out, a
 *  removed row fades). Decorative motion — numbers counting up from zero,
 *  cards cascading in, pulses and pop-ins — was taken out in 1.0.11 because
 *  it made the system look like a generated template; don't add it back. */
public final class AnimationUtils {

    private AnimationUtils() {}

    // ── Global on/off switch (Configuración → low-end machines) ────────────
    // Every method below checks this first and, when off, jumps the node
    // straight to its END state instead of skipping the call outright — a
    // caller that does node.setOpacity(0) then calls fadeIn() expects the
    // node visible afterward either way, animated or not.
    private static final Preferences ANIM_PREFS =
        Preferences.userRoot().node("sibim/ui/animations");
    private static volatile boolean enabled = inicial();

    /** Until someone ticks the box in Configuración, the PC's own choice: with
     *  "Mostrar animaciones en Windows" off (slow PCs, people bothered by
     *  motion) SIBIM starts without them too. */
    private static boolean inicial() {
        try {
            if (ANIM_PREFS.get("enabled", null) != null) return ANIM_PREFS.getBoolean("enabled", true);
        } catch (Exception ignored) {
            // unreadable preference: fall back to the system's
        }
        return windowsAnima();
    }

    /** Windows keeps "Mostrar animaciones" as MinAnimate = "0"/"1". */
    static boolean windowsAnima() {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) return true;
        try {
            String v = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, "Control Panel\\Desktop\\WindowMetrics", "MinAnimate");
            return v == null || !"0".equals(v.trim());
        } catch (Throwable e) {
            return true;
        }
    }

    public static boolean isEnabled() { return enabled; }

    /** The length of a movement: {@code ms}, or one millisecond with
     *  animations off — the node still ends where it should and onFinished
     *  still runs. For transitions only; a PauseTransition keeps its real
     *  length (a toast's reading time). */
    public static Duration duracion(double ms) {
        return enabled ? Duration.millis(ms) : Duration.ONE;
    }

    /** Plays a decorative animation built elsewhere (a toast sliding in, the
     *  sidebar folding, a tutorial step) — or, with animations off, runs it
     *  to its end at once: same final state, same onFinished. Only for motion:
     *  an animation that includes a pause someone must be able to read through,
     *  or a timer, would be cut short too. */
    public static void reproducir(Animation a) {
        if (!enabled && a.getCycleCount() != Animation.INDEFINITE) a.setRate(a.getRate() * 1000);
        a.play();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        ANIM_PREFS.putBoolean("enabled", value);
    }

    /** Fade from transparent to opaque. */
    public static void fadeIn(Node node, int durationMs, int delayMs) {
        if (!enabled) { node.setOpacity(1); return; }
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setDelay(Duration.millis(delayMs));
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    /**
     * Horizontal shake — error feedback for form fields.
     */
    public static void shake(Node node) {
        if (!enabled) return;
        double x = node.getTranslateX();
        Timeline tl = new Timeline(
            kf(node,   0, x),
            kf(node,  55, x - 11),
            kf(node, 120, x + 10),
            kf(node, 178, x - 7),
            kf(node, 230, x + 5),
            kf(node, 278, x - 3),
            kf(node, 315, x)
        );
        tl.play();
    }


    /** Fade a node out; calls {@code after} on the FX thread when done. */
    public static void fadeOut(Node node, int durationMs, Runnable after) {
        if (!enabled) { node.setOpacity(0); if (after != null) after.run(); return; }
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(node.getOpacity());
        ft.setToValue(0);
        ft.setInterpolator(Interpolator.EASE_IN);
        if (after != null) ft.setOnFinished(e -> after.run());
        ft.play();
    }

    /**
     * Temporarily adds {@code cssClass} to {@code node} for {@code holdMs} then removes it.
     * Useful for flashing a table row green after a save, or red after a delete.
     */
    public static void flashClass(Node node, String cssClass, int holdMs) {
        if (!enabled) return;
        node.getStyleClass().add(cssClass);
        new Timeline(new KeyFrame(Duration.millis(holdMs),
            e -> node.getStyleClass().remove(cssClass))).play();
    }

    /** Builds a pulsing skeleton placeholder, sets it on the table, and returns
     *  the Timeline so the caller can stop it when real data arrives. */
    public static javafx.animation.Timeline buildSkeletonPlaceholder(
            TableView<?> table, int rowCount) {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(4);
        box.setPadding(new javafx.geometry.Insets(8));
        for (int i = 0; i < rowCount; i++) {
            Label bar = new Label();
            bar.getStyleClass().add("skeleton");
            bar.setPrefHeight(44);
            bar.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(bar);
        }
        Timeline pulse = new Timeline(
            new KeyFrame(Duration.millis(0),    new KeyValue(box.opacityProperty(), 0.7)),
            new KeyFrame(Duration.millis(800),  new KeyValue(box.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1600), new KeyValue(box.opacityProperty(), 0.7))
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        // An indefinitely-looping animation is exactly the kind worth
        // skipping on a low-end machine — a static skeleton at a fixed
        // opacity still reads as "loading" just fine without it.
        if (enabled) pulse.play(); else box.setOpacity(0.55);
        table.setPlaceholder(box);
        return pulse;
    }

    // ── helpers ──────────────────────────────────────────────────
    private static KeyFrame kf(Node node, int ms, double x) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.EASE_BOTH));
    }
}
