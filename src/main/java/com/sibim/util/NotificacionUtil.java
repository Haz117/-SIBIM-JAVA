package com.sibim.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

public class NotificacionUtil {

    public enum Tipo { EXITO, ERROR, INFO, ADVERTENCIA }

    private static int activeToasts = 0;
    private static final double TOAST_OFFSET  = 64;
    private static final int    MAX_TOASTS    = 4;

    public static void mostrar(Scene scene, String mensaje, Tipo tipo) {
        if (scene == null) return;
        Platform.runLater(() -> show(scene.getWindow(), mensaje, tipo));
    }

    public static void exito(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.EXITO); }
    public static void error(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.ERROR); }
    public static void info(Scene scene, String mensaje)        { mostrar(scene, mensaje, Tipo.INFO); }
    public static void advertencia(Scene scene, String mensaje) { mostrar(scene, mensaje, Tipo.ADVERTENCIA); }

    /** Toast de éxito con botón de acción (p.ej. "↶ Deshacer"). Se oculta a los 8 s o al pulsar el botón. */
    public static void exitoConAccion(Scene scene, String mensaje, String btnLabel, Runnable onAction) {
        if (scene == null) return;
        Platform.runLater(() -> showConAccion(scene.getWindow(), mensaje, btnLabel, onAction));
    }

    private static void show(Window owner, String mensaje, Tipo tipo) {
        if (owner == null) return;
        if (activeToasts >= MAX_TOASTS) return;

        String icon, toastClass, iconClass;
        switch (tipo) {
            case EXITO       -> { icon = "✓"; toastClass = "toast-success"; iconClass = "toast-icon-success"; }
            case ERROR       -> { icon = "✕"; toastClass = "toast-error";   iconClass = "toast-icon-error"; }
            case ADVERTENCIA -> { icon = "!"; toastClass = "toast-warning";  iconClass = "toast-icon-warning"; }
            default          -> { icon = "i"; toastClass = "toast-info";    iconClass = "toast-icon-info"; }
        }

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("toast-icon-text");

        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().addAll("toast-icon-badge", iconClass);

        Label lbl = new Label(mensaje);
        lbl.getStyleClass().add("toast-msg");
        lbl.setWrapText(true);
        lbl.setMaxWidth(390);
        HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);

        Label closeBtn = new Label("×");
        closeBtn.getStyleClass().add("toast-close");

        HBox box = new HBox(12, iconBadge, lbl, closeBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 16, 13, 16));
        box.getStyleClass().addAll("toast-box", toastClass);

        // Popup is not in the main scene, so apply stylesheet directly to the root node
        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setTranslateY(-16);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);

        double x = owner.getX() + (owner.getWidth() - 500) / 2.0;
        double y = owner.getY() + 22 + (activeToasts * TOAST_OFFSET);
        activeToasts++;
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), box);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), box);
        slideIn.setFromY(-16); slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        long ms = (tipo == Tipo.ERROR || tipo == Tipo.ADVERTENCIA) ? 4500 : 2800;
        PauseTransition pause = new PauseTransition(Duration.millis(ms));

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(260), box);
        slideOut.setToX(54);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), box);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        ParallelTransition dismissAnim = new ParallelTransition(slideOut, fadeOut);
        dismissAnim.setOnFinished(e -> { popup.hide(); activeToasts--; });

        SequentialTransition seq = new SequentialTransition(
            new ParallelTransition(fadeIn, slideIn), pause, dismissAnim);
        seq.play();

        closeBtn.setOnMouseClicked(e -> {
            seq.stop();
            TranslateTransition quickSlide = new TranslateTransition(Duration.millis(190), box);
            quickSlide.setToX(54);
            quickSlide.setInterpolator(Interpolator.EASE_IN);
            FadeTransition quickFade = new FadeTransition(Duration.millis(190), box);
            quickFade.setFromValue(box.getOpacity()); quickFade.setToValue(0);
            ParallelTransition quickDismiss = new ParallelTransition(quickSlide, quickFade);
            quickDismiss.setOnFinished(ev -> { popup.hide(); activeToasts--; });
            quickDismiss.play();
        });
    }

    private static void showConAccion(Window owner, String mensaje, String btnLabel, Runnable onAction) {
        if (owner == null) return;
        if (activeToasts >= MAX_TOASTS) return;

        Label iconLbl = new Label("✓");
        iconLbl.getStyleClass().add("toast-icon-text");
        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().addAll("toast-icon-badge", "toast-icon-success");

        Label lbl = new Label(mensaje);
        lbl.getStyleClass().add("toast-msg");
        lbl.setWrapText(true);
        lbl.setMaxWidth(220);
        HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.Button actionBtn = new javafx.scene.control.Button(btnLabel);
        actionBtn.getStyleClass().add("toast-action-btn");

        Label closeBtn = new Label("×");
        closeBtn.getStyleClass().add("toast-close");

        HBox box = new HBox(10, iconBadge, lbl, actionBtn, closeBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 16, 13, 16));
        box.getStyleClass().addAll("toast-box", "toast-success");

        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setTranslateY(-16);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);

        double x = owner.getX() + (owner.getWidth() - 500) / 2.0;
        double y = owner.getY() + 22 + (activeToasts * TOAST_OFFSET);
        activeToasts++;
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), box);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), box);
        slideIn.setFromY(-16); slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);
        PauseTransition pause = new PauseTransition(Duration.millis(8000));
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(260), box);
        slideOut.setToX(54);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), box);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        ParallelTransition dismissAnim = new ParallelTransition(slideOut, fadeOut);
        dismissAnim.setOnFinished(e -> { popup.hide(); activeToasts--; });

        SequentialTransition seq = new SequentialTransition(
            new ParallelTransition(fadeIn, slideIn), pause, dismissAnim);
        seq.play();

        Runnable dismiss = () -> {
            seq.stop();
            TranslateTransition qs = new TranslateTransition(Duration.millis(190), box);
            qs.setToX(54); qs.setInterpolator(Interpolator.EASE_IN);
            FadeTransition qf = new FadeTransition(Duration.millis(190), box);
            qf.setFromValue(box.getOpacity()); qf.setToValue(0);
            ParallelTransition qd = new ParallelTransition(qs, qf);
            qd.setOnFinished(ev -> { popup.hide(); activeToasts--; });
            qd.play();
        };

        actionBtn.setOnAction(e -> { dismiss.run(); onAction.run(); });
        closeBtn.setOnMouseClicked(e -> dismiss.run());
    }
}
