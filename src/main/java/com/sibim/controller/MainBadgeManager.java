package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.DialogUtil;
import javafx.scene.control.Label;

/** Manages the alert and loan badge labels in the sidebar — async load and
 *  visibility updates. Extracted from MainController. */
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

    void loadAlertBadge() {
        DialogUtil.runAsync(
            () -> alertProductoService.getVencidosProximos(30).size(),
            total -> {
                if (alertBadge == null) return;
                if (total > 0) {
                    alertBadge.setText(total > 99 ? "99+" : String.valueOf(total));
                    alertBadge.setVisible(true);
                    alertBadge.setManaged(true);
                } else {
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
                    loanBadge.setVisible(true);
                    loanBadge.setManaged(true);
                } else {
                    loanBadge.setVisible(false);
                    loanBadge.setManaged(false);
                }
            },
            e -> { /* badge is decorative */ }
        );
    }
}
