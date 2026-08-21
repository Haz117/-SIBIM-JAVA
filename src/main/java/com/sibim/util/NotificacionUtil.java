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
    private static final double TOAST_OFFSET = 64;

    public static void mostrar(Scene scene, String mensaje, Tipo tipo) {
        if (scene == null) return;
        Platform.runLater(() -> show(scene.getWindow(), mensaje, tipo));
    }

    public static void exito(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.EXITO); }
    public static void error(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.ERROR); }
    public static void info(Scene scene, String mensaje)        { mostrar(scene, mensaje, Tipo.INFO); }
    public static void advertencia(Scene scene, String mensaje) { mostrar(scene, mensaje, Tipo.ADVERTENCIA); }

    private static void show(Window owner, String mensaje, Tipo tipo) {
        if (owner == null) return;

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

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), box);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> { popup.hide(); activeToasts--; });

        SequentialTransition seq = new SequentialTransition(
            new ParallelTransition(fadeIn, slideIn), pause, fadeOut);
        seq.play();

        // Guards against a fast double-click: without it, a second click
        // during the 150ms quickFade below re-enters this handler and plays
        // a second fade whose onFinished decrements activeToasts a second
        // time for the same toast, throwing off the vertical stacking
        // offset of whichever toasts show next.
        boolean[] closing = { false };
        closeBtn.setOnMouseClicked(e -> {
            if (closing[0]) return;
            closing[0] = true;
            seq.stop();
            FadeTransition quickFade = new FadeTransition(Duration.millis(150), box);
            quickFade.setFromValue(box.getOpacity()); quickFade.setToValue(0);
            quickFade.setOnFinished(ev -> { popup.hide(); activeToasts--; });
            quickFade.play();
        });
    }
}
