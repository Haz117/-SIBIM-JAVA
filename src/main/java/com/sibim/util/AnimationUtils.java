package com.sibim.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongFunction;

public final class AnimationUtils {

    private AnimationUtils() {}

    // Slide distances — vertical and horizontal are intentionally different:
    // horizontal travel feels faster at the same pixel count, so it uses a
    // slightly larger value to match the perceived weight of the vertical ones.
    private static final int SLIDE_V_OFFSET   = 24;
    private static final int SLIDE_H_OFFSET   = 28;
    // Stagger cap: animating more than this many rows at once creates visual
    // noise and makes the UI feel slow rather than lively.
    static final int MAX_STAGGER_ROWS = 15;

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
        node.setTranslateY(SLIDE_V_OFFSET);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(SLIDE_V_OFFSET); slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide down from above while fading in. */
    public static void fadeInDown(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateY(-SLIDE_V_OFFSET);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(-SLIDE_V_OFFSET); slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide in from the right while fading in. */
    public static void fadeInRight(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateX(SLIDE_H_OFFSET);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromX(SLIDE_H_OFFSET); slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** Slide in from the left while fading in. */
    public static void fadeInLeft(Node node, int durationMs, int delayMs) {
        node.setOpacity(0);
        node.setTranslateX(-SLIDE_H_OFFSET);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromX(-SLIDE_H_OFFSET); slide.setToX(0);
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


    /** Quick scale-pop on a stat card after its value updates. */
    public static void statCardPop(Node card) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(130), card);
        grow.setToX(1.07); grow.setToY(1.07);
        grow.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(160), card);
        shrink.setToX(1.0); shrink.setToY(1.0);
        shrink.setInterpolator(Interpolator.EASE_IN);
        new SequentialTransition(grow, shrink).play();
    }

    /**
     * Animates a Label's text from 0 up to {@code target} using an ease-both
     * interpolator. Each tick calls {@code formatter} so the caller controls
     * how the number looks (plain integer, currency, percentage, etc.).
     * The last frame always calls formatter(target) for exact precision.
     */
    public static void animateCount(Label label, long target, int durationMs,
                                    LongFunction<String> formatter) {
        label.setText(formatter.apply(0));
        if (target == 0) return;
        SimpleDoubleProperty prop = new SimpleDoubleProperty(0);
        prop.addListener((obs, o, n) -> label.setText(formatter.apply((long) n.doubleValue())));
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(durationMs),
            new KeyValue(prop, (double) target, Interpolator.EASE_BOTH)));
        tl.setOnFinished(e -> label.setText(formatter.apply(target)));
        tl.play();
    }

    /** Overload: plain {@code String.valueOf(long)} formatter. */
    public static void animateCount(Label label, long target, int durationMs) {
        animateCount(label, target, durationMs, String::valueOf);
    }

    /**
     * Reveals {@code bar} (an HBox health bar) with a left-to-right clip
     * that grows from 0 to the bar's actual width over {@code durationMs}.
     * Must be called after the bar has been added to the scene graph (so
     * layoutBounds are available); use a short Timeline delay if needed.
     */
    public static void revealBarLTR(Region bar, int durationMs) {
        double w = bar.getWidth();
        if (w <= 0) w = bar.getPrefWidth() > 0 ? bar.getPrefWidth() : 500;
        Rectangle clip = new Rectangle(0, 0, 0, bar.getBoundsInLocal().getHeight() + 4);
        bar.heightProperty().addListener((o, x, h) -> clip.setHeight(h.doubleValue() + 4));
        bar.setClip(clip);
        new Timeline(new KeyFrame(Duration.millis(durationMs),
            new KeyValue(clip.widthProperty(), w, Interpolator.EASE_OUT))).play();
    }

    /**
     * Heartbeat scale-pulse — good for alert badges and notification chips.
     * Returns the animation so the caller can stop it when the badge hides.
     */
    public static SequentialTransition pulse(Node node, int cycles) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(150), node);
        grow.setToX(1.16); grow.setToY(1.16);
        grow.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(230), node);
        shrink.setToX(1.0); shrink.setToY(1.0);
        shrink.setInterpolator(Interpolator.EASE_IN);
        SequentialTransition beat = new SequentialTransition(grow, shrink);
        beat.setCycleCount(cycles);
        beat.play();
        return beat;
    }

    /**
     * Spring-like pop-in: scale 0.85 → 1.06 → 0.98 → 1.0 while fading in.
     * More satisfying than a plain fadeIn for overlays and dialog entrances.
     */
    public static void springIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.85);
        node.setScaleY(0.85);
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setToValue(1);
        Timeline spring = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.scaleXProperty(), 0.85),
                new KeyValue(node.scaleYProperty(), 0.85)),
            new KeyFrame(Duration.millis(220),
                new KeyValue(node.scaleXProperty(), 1.06, Interpolator.EASE_OUT),
                new KeyValue(node.scaleYProperty(), 1.06, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(330),
                new KeyValue(node.scaleXProperty(), 0.98, Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(), 0.98, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(420),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        new ParallelTransition(fade, spring).play();
    }

    /** Slide out to the right while fading; calls {@code after} when done. */
    public static void slideOutRight(Node node, int durationMs, Runnable after) {
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setToX(SLIDE_H_OFFSET);
        slide.setInterpolator(Interpolator.EASE_IN);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        if (after != null) pt.setOnFinished(e -> after.run());
        pt.play();
    }

    /** Slide out to the left while fading; calls {@code after} when done. */
    public static void slideOutLeft(Node node, int durationMs, Runnable after) {
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setToX(-SLIDE_H_OFFSET);
        slide.setInterpolator(Interpolator.EASE_IN);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        if (after != null) pt.setOnFinished(e -> after.run());
        pt.play();
    }

    /** Fade a node out; calls {@code after} on the FX thread when done. */
    public static void fadeOut(Node node, int durationMs, Runnable after) {
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
        node.getStyleClass().add(cssClass);
        new Timeline(new KeyFrame(Duration.millis(holdMs),
            e -> node.getStyleClass().remove(cssClass))).play();
    }

    /**
     * Staggered fade-in + slide-up for visible table rows after data loads.
     * Must be called on the FX thread; uses Platform.runLater to wait one
     * pulse so JavaFX finishes laying out the new rows before we animate them.
     */
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
        pulse.play();
        table.setPlaceholder(box);
        return pulse;
    }

    public static <T> void staggerTableRows(TableView<T> table) {
        Platform.runLater(() -> {
            var rows = table.lookupAll(".table-row-cell").stream()
                .filter(n -> n instanceof TableRow<?>)
                .map(n -> (TableRow<?>) n)
                .filter(r -> !r.isEmpty())
                .sorted(Comparator.comparingDouble(Node::getLayoutY))
                .limit(MAX_STAGGER_ROWS)
                .toList();
            for (int i = 0; i < rows.size(); i++) {
                Node row = rows.get(i);
                row.setOpacity(0);
                row.setTranslateY(10);
                int delay = i * 22;
                FadeTransition ft = new FadeTransition(Duration.millis(200), row);
                ft.setFromValue(0); ft.setToValue(1); ft.setDelay(Duration.millis(delay));
                TranslateTransition tt = new TranslateTransition(Duration.millis(200), row);
                tt.setFromY(10); tt.setToY(0); tt.setDelay(Duration.millis(delay));
                tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(ft, tt).play();
            }
        });
    }

    // ── helpers ──────────────────────────────────────────────────
    private static KeyFrame kf(Node node, int ms, double x) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.EASE_BOTH));
    }
}
