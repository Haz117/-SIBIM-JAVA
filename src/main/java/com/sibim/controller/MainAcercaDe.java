package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class MainAcercaDe {

    private MainAcercaDe() {}

    static void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-domain", "Acerca de SIBIM",
            "Sistema Integral de Bienes Municipales",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(10);
        g.setPadding(new Insets(16, 22, 16, 22));
        String[][] rows = {
            { "Versión",          "1.0.0" },
            { "Plataforma",       "Java " + System.getProperty("java.version") + " · JavaFX 21" },
            { "Sistema",          System.getProperty("os.name") + " " + System.getProperty("os.version") },
            { "Modo de datos",    DatabaseConfig.isDemoMode() ? "Demo (sin base de datos)"
                                 : DatabaseConfig.isOfflineMode() ? "Offline (" + SyncService.pendingCount() + " pendiente(s) de sincronizar)"
                                 : "PostgreSQL (conectado)" },
            { "Desarrollado por", "H. Ayuntamiento Municipal" },
            { "Año",              "2026" },
        };
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("dlg-detail-label");
            k.setMinWidth(130);
            Label v = new Label(rows[i][1]);
            v.getStyleClass().add("dlg-detail-value");
            v.setWrapText(true);
            g.add(k, 0, i); g.add(v, 1, i);
        }

        VBox content = new VBox(0, header, g);
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, g), 260, 70);
        dialog.showAndWait();
    }
}
