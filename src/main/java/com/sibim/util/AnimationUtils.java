package com.sibim.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;
import java.util.List;

public final class AnimationUtils {

    private AnimationUtils() {}

    /** Fade from transparent to opaque. */
    public static void fadeIn(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setDelay(Duration.millis(delayMs));
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    /** Slide up from below while fading in. */
    public static void fadeInUp(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateY(26);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(26); slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide down from above while fading in. */
    public static void fadeInDown(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateY(-22);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(-22); slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide in from the right while fading in. */
    public static void fadeInRight(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateX(28);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromX(28); slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide in from the left while fading in. */
    public static void fadeInLeft(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateX(-28);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromX(-28); slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Staggered fade-in-up for a collection of nodes. */
    public static void staggeredFadeInUp(List<? extends Node> nodes, int durationMs, int staggerMs) {
        for (int i = 0; i < nodes.size(); i++) {
            fadeInUp(nodes.get(i), durationMs, i * staggerMs);
        }
    }

    /**
     * Page content transition: new page slides in from the right.
     * Call immediately after adding the node to the scene graph.
     */
    public static void pageIn(Node node) {
        fadeInRight(node, 240, 0);
    }

    /**
     * Horizontal shake — error feedback for form fields.
     */
    public static void shake(Node node) {
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

    /**
     * Fade out a node, then run {@code onFinished}. Returns the transition so
     * the caller can play() it at the right moment.
     */
    public static FadeTransition fadeOut(Node node, int durationMs, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(node.getOpacity()); ft.setToValue(0);
        ft.setInterpolator(Interpolator.EASE_IN);
        if (onFinished != null) ft.setOnFinished(e -> onFinished.run());
        return ft;
    }

    // ── helpers ──────────────────────────────────────────────────
    private static KeyFrame kf(Node node, int ms, double x) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.EASE_BOTH));
    }
}
