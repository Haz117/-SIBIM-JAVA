package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/** Manages the alert and loan badge labels in the sidebar — pulse animation,
 *  async load and visibility updates. Extracted from MainController. */
class MainBadgeManager {

    private final Label alertBadge;
    private final Label loanBadge;
    private final ProductoService alertProductoService;
    private final PrestamoService prestamoService;
    private Timeline badgePulse;

    MainBadgeManager(Label alertBadge, Label loanBadge,
                     ProductoService alertProductoService, PrestamoService prestamoService) {
        this.alertBadge          = alertBadge;
        this.loanBadge           = loanBadge;
        this.alertProductoService = alertProductoService;
        this.prestamoService      = prestamoService;
    }

    void startBadgePulse() {
        stopBadgePulse();
        if (alertBadge == null) return;
        badgePulse = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(160),
                new KeyValue(alertBadge.scaleXProperty(), 1.18, Interpolator.EASE_OUT),
                new KeyValue(alertBadge.scaleYProperty(), 1.18, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(360),
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(600),
                new KeyValue(alertBadge.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                new KeyValue(alertBadge.scaleYProperty(), 1.08, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(820),
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_IN))
        );
        badgePulse.setCycleCount(Timeline.INDEFINITE);
        badgePulse.setDelay(Duration.millis(600));
        badgePulse.play();
    }

    void stopBadgePulse() {
        if (badgePulse != null) { badgePulse.stop(); badgePulse = null; }
        if (alertBadge != null) { alertBadge.setScaleX(1.0); alertBadge.setScaleY(1.0); }
    }

    void loadAlertBadge() {
        DialogUtil.runAsync(
            () -> alertProductoService.getAgotados().size()
                + alertProductoService.getBajoStock().size()
                + alertProductoService.getVencidosProximos(30).size(),
            total -> {
                if (alertBadge == null) return;
                if (total > 0) {
                    alertBadge.setText(total > 99 ? "99+" : String.valueOf(total));
                    boolean wasHidden = !alertBadge.isVisible();
                    alertBadge.setVisible(true);
                    alertBadge.setManaged(true);
                    if (wasHidden) {
                        ScaleTransition pop = new ScaleTransition(Duration.millis(320), alertBadge);
                        pop.setFromX(0.3); pop.setFromY(0.3);
                        pop.setToX(1.0);   pop.setToY(1.0);
                        pop.setInterpolator(Interpolator.EASE_OUT);
                        pop.setOnFinished(ev -> startBadgePulse());
                        pop.play();
                    } else {
                        AnimationUtils.pulse(alertBadge, 3);
                    }
                } else {
                    stopBadgePulse();
                    alertBadge.setVisible(false);
                    alertBadge.setManaged(false);
                }
            },
            e -> { /* badge is decorative */ }
        );
    }

    void loadLoanBadge() {
        DialogUtil.runAsync(
            () -> prestamoService.countVencidos(),
            count -> {
                if (loanBadge == null) return;
                if (count > 0) {
                    loanBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                    boolean wasHidden = !loanBadge.isVisible();
                    loanBadge.setVisible(true);
                    loanBadge.setManaged(true);
                    if (wasHidden) {
                        ScaleTransition pop = new ScaleTransition(Duration.millis(320), loanBadge);
                        pop.setFromX(0.3); pop.setFromY(0.3);
                        pop.setToX(1.0);   pop.setToY(1.0);
                        pop.setInterpolator(Interpolator.EASE_OUT);
                        pop.play();
                    } else {
                        AnimationUtils.pulse(loanBadge, 2);
                    }
                } else {
                    loanBadge.setVisible(false);
                    loanBadge.setManaged(false);
                }
            },
            e -> { /* badge is decorative */ }
        );
    }
}
