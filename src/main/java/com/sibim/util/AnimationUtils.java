package com.sibim.util;

import javafx.animation.*;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.util.List;
import java.util.function.LongFunction;

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
     * Floating overlay on a StackPane container that celebrates a successful
     * Transferencia: shows the product name and an animated "Area A → Area B"
     * flow, then fades out automatically after 2.2 s.
     */
    public static void transferCelebration(StackPane container,
                                           String productName, String from, String to) {
        Label iconLbl = new Label("✓");
        iconLbl.getStyleClass().add("transfer-cel-icon");

        Label titleLbl = new Label("Transferencia registrada");
        titleLbl.getStyleClass().add("transfer-cel-title");

        Label productLbl = new Label(productName != null ? productName : "");
        productLbl.getStyleClass().add("transfer-cel-product");
        productLbl.setWrapText(true);

        Label fromLbl = new Label(from != null ? from : "—");
        fromLbl.getStyleClass().add("transfer-cel-chip-from");

        Label arrowLbl = new Label("→");
        arrowLbl.getStyleClass().add("transfer-cel-arrow");
        arrowLbl.setTranslateX(-14);
        arrowLbl.setOpacity(0);

        Label toLbl = new Label(to != null ? to : "—");
        toLbl.getStyleClass().add("transfer-cel-chip-to");
        toLbl.setScaleX(0.72); toLbl.setScaleY(0.72);
        toLbl.setOpacity(0);

        HBox areasRow = new HBox(10, fromLbl, arrowLbl, toLbl);
        areasRow.setAlignment(Pos.CENTER);

        VBox card = new VBox(8, iconLbl, titleLbl, productLbl, areasRow);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("transfer-cel-card");
        card.setPadding(new Insets(28, 44, 28, 44));
        card.setMaxWidth(400);
        card.setScaleX(0.86); card.setScaleY(0.86);

        StackPane overlay = new StackPane(card);
        overlay.setPickOnBounds(false);
        overlay.setOpacity(0);
        container.getChildren().add(overlay);

        // Card fades + scales in
        FadeTransition cardFadeIn = new FadeTransition(Duration.millis(280), overlay);
        cardFadeIn.setToValue(1);
        ScaleTransition cardScaleIn = new ScaleTransition(Duration.millis(280), card);
        cardScaleIn.setToX(1); cardScaleIn.setToY(1);
        cardScaleIn.setInterpolator(Interpolator.EASE_OUT);

        // Arrow slides from left + fades in (delayed 220 ms)
        TranslateTransition arrowSlide = new TranslateTransition(Duration.millis(360), arrowLbl);
        arrowSlide.setToX(0);
        arrowSlide.setDelay(Duration.millis(220));
        arrowSlide.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition arrowFade = new FadeTransition(Duration.millis(280), arrowLbl);
        arrowFade.setToValue(1);
        arrowFade.setDelay(Duration.millis(220));

        // Destination chip pops in (delayed 440 ms)
        ScaleTransition toPop = new ScaleTransition(Duration.millis(260), toLbl);
        toPop.setToX(1); toPop.setToY(1);
        toPop.setDelay(Duration.millis(440));
        toPop.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition toFade = new FadeTransition(Duration.millis(260), toLbl);
        toFade.setToValue(1);
        toFade.setDelay(Duration.millis(440));

        // Hold then fade out
        FadeTransition fadeOut = new FadeTransition(Duration.millis(320), overlay);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> container.getChildren().remove(overlay));

        new SequentialTransition(
            new ParallelTransition(cardFadeIn, cardScaleIn, arrowSlide, arrowFade, toPop, toFade),
            new PauseTransition(Duration.millis(2200)),
            fadeOut
        ).play();
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

    // ── helpers ──────────────────────────────────────────────────
    private static KeyFrame kf(Node node, int ms, double x) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.EASE_BOTH));
    }
}
