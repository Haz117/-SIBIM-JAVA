package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;

/** Manages the alert and loan badge labels in the sidebar — pulse animation,
 *  async load and visibility updates. Extracted from MainController. */
class MainBadgeManager {

    private final Label alertBadge;
    private final Label loanBadge;
    private final ProductoService alertProductoService;
    private final PrestamoService prestamoService;

    MainBadgeManager(Label alertBadge, Label loanBadge,
                     ProductoService alertProductoService, PrestamoService prestamoService) {
        this.alertBadge          = alertBadge;
        this.loanBadge           = loanBadge;
        this.alertProductoService = alertProductoService;
        this.prestamoService      = prestamoService;
    }

    /** The badge used to pulse forever, which read as an alarm; it now pops in once. */
    void stopBadgePulse() {
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
