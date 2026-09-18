package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.session.SessionManager;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

class MainStatusBarManager {

    private final HBox     offlineBanner;
    private final Label    offlineBannerLabel;
    private final Button   offlineBannerSyncBtn;
    private final Label    statusDbLabel;
    private final Tooltip  statusDbTooltip;
    private final Label    statusUserLabel;
    private final Label    statusTimeLabel;
    private final FontIcon statusDotIcon;

    private RotateTransition spinAnim;
    private boolean          connecting = false;

    MainStatusBarManager(HBox offlineBanner, Label offlineBannerLabel, Button offlineBannerSyncBtn,
                         Label statusDbLabel, Tooltip statusDbTooltip,
                         Label statusUserLabel, Label statusTimeLabel, FontIcon statusDotIcon) {
        this.offlineBanner        = offlineBanner;
        this.offlineBannerLabel   = offlineBannerLabel;
        this.offlineBannerSyncBtn = offlineBannerSyncBtn;
        this.statusDbLabel        = statusDbLabel;
        this.statusDbTooltip      = statusDbTooltip;
        this.statusUserLabel      = statusUserLabel;
        this.statusTimeLabel      = statusTimeLabel;
        this.statusDotIcon        = statusDotIcon;
    }

    void setConnecting(boolean value) {
        this.connecting = value;
        if (statusDotIcon == null || statusDbLabel == null) return;
        if (value) {
            applyIcon("mdi2d-database-sync-outline", "status-dot-icon-connecting");
            statusDbLabel.setText("Conectando…");
            startSpin();
        } else {
            stopSpin();
            update();
        }
    }

    void update() {
        if (connecting) return; // don't override while connecting animation is active

        if (statusUserLabel != null && SessionManager.getCurrentUser() != null)
            statusUserLabel.setText(SessionManager.getCurrentUser().getNombre() +
                "  ·  " + SessionManager.getCurrentUser().getRol().getEtiqueta());

        boolean offline = DatabaseConfig.isOfflineMode();
        boolean demo    = DatabaseConfig.isDemoMode();
        int pending     = offline ? SyncService.pendingCount() : 0;

        if (statusDbLabel != null) {
            String text = offline
                ? (pending > 0 ? "Sin conexión · " + pending + " pendiente(s)" : "Sin conexión")
                : demo ? "Modo demo" : "Conectado";
            statusDbLabel.setText(text);

            if (statusDotIcon != null) {
                if (offline) {
                    applyIcon("mdi2d-database-off-outline", "status-dot-icon-offline");
                } else if (demo) {
                    applyIcon("mdi2d-database-clock-outline", "status-dot-icon-demo");
                } else {
                    applyIcon("mdi2d-database-check-outline", "status-dot-icon-ok");
                }
            }

            if (statusDbTooltip != null) {
                String tip = offline
                    ? (pending > 0
                        ? "Sin conexión a la base de datos.\n" + pending + " operación(es) pendiente(s) de sincronizar\ncuando se recupere la conexión."
                        : "Sin conexión a la base de datos.\nTus cambios se guardan localmente\ny se sincronizarán cuando vuelva la conexión.")
                    : demo
                    ? "Modo demostración activo.\nLos datos mostrados no son reales\ny no se almacenan en ninguna base de datos."
                    : "Base de datos conectada (Supabase).\nTus cambios se guardan en tiempo real.\nÚltima verificación: al iniciar la aplicación.";
                statusDbTooltip.setText(tip);
            }
        }

        if (offlineBanner != null) {
            boolean show = offline || demo;
            offlineBanner.setVisible(show);
            offlineBanner.setManaged(show);
            if (show && offlineBannerLabel != null) {
                offlineBannerLabel.setText(offline
                    ? (pending > 0
                        ? "Sin conexión — " + pending + " cambio(s) guardados localmente, se sincronizarán al reconectar"
                        : "Sin conexión — trabajando en modo offline")
                    : "Modo demostración — los datos no se guardan");
            }
            if (offlineBannerSyncBtn != null) {
                offlineBannerSyncBtn.setVisible(offline);
                offlineBannerSyncBtn.setManaged(offline);
            }
            offlineBanner.getStyleClass().removeAll("offline-banner-demo");
            if (demo) offlineBanner.getStyleClass().add("offline-banner-demo");
        }

        updateTime();
    }

    void updateTime() {
        if (statusTimeLabel != null)
            statusTimeLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void applyIcon(String literal, String styleClass) {
        statusDotIcon.setIconLiteral(literal);
        statusDotIcon.getStyleClass().removeAll(
            "status-dot-icon-ok", "status-dot-icon-demo",
            "status-dot-icon-offline", "status-dot-icon-connecting");
        statusDotIcon.getStyleClass().add(styleClass);
    }

    private void startSpin() {
        if (statusDotIcon == null) return;
        if (spinAnim == null) {
            spinAnim = new RotateTransition(Duration.millis(900), statusDotIcon);
            spinAnim.setByAngle(360);
            spinAnim.setCycleCount(Animation.INDEFINITE);
            spinAnim.setInterpolator(Interpolator.LINEAR);
        }
        if (spinAnim.getStatus() != Animation.Status.RUNNING) spinAnim.play();
    }

    private void stopSpin() {
        if (spinAnim != null && spinAnim.getStatus() == Animation.Status.RUNNING) {
            spinAnim.stop();
            if (statusDotIcon != null) statusDotIcon.setRotate(0);
        }
    }
}
