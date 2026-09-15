package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.session.SessionManager;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

class MainStatusBarManager {

    private final HBox    offlineBanner;
    private final Label   offlineBannerLabel;
    private final Button  offlineBannerSyncBtn;
    private final Label   statusDbLabel;
    private final Tooltip statusDbTooltip;
    private final Label   statusUserLabel;
    private final Label   statusTimeLabel;
    private final FontIcon statusDotIcon;

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

    void update() {
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
                statusDotIcon.getStyleClass().removeAll(
                    "status-dot-icon-ok", "status-dot-icon-demo", "status-dot-icon-offline");
                if (offline) {
                    statusDotIcon.setIconLiteral("mdi2c-close-circle");
                    statusDotIcon.getStyleClass().add("status-dot-icon-offline");
                } else if (demo) {
                    statusDotIcon.setIconLiteral("mdi2c-clock-outline");
                    statusDotIcon.getStyleClass().add("status-dot-icon-demo");
                } else {
                    statusDotIcon.setIconLiteral("mdi2c-check-circle");
                    statusDotIcon.getStyleClass().add("status-dot-icon-ok");
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
}
