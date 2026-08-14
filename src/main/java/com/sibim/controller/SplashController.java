package com.sibim.controller;

import com.sibim.MainApp;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class SplashController {

    @FXML private StackPane splashRoot;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;

    @FXML
    public void initialize() {
        progressBar.setProgress(0);

        // Fade in entire splash with slight delay so stage renders first
        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), splashRoot);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setDelay(Duration.millis(80));
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        // Progress bar fills with realistic non-linear curve
        Timeline progressAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(progressBar.progressProperty(), 0.0)),
            new KeyFrame(Duration.millis(350),
                new KeyValue(progressBar.progressProperty(), 0.18, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(900),
                new KeyValue(progressBar.progressProperty(), 0.52, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1550),
                new KeyValue(progressBar.progressProperty(), 0.82, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2000),
                new KeyValue(progressBar.progressProperty(), 1.0, Interpolator.EASE_OUT))
        );

        // Status text changes aligned with progress
        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),   e -> lblStatus.setText("Iniciando sistema...")),
            new KeyFrame(Duration.millis(650),  e -> lblStatus.setText("Conectando base de datos...")),
            new KeyFrame(Duration.millis(1250), e -> lblStatus.setText("Cargando módulos...")),
            new KeyFrame(Duration.millis(1850), e -> lblStatus.setText("Sistema listo  ✓"))
        );

        PauseTransition hold = new PauseTransition(Duration.millis(380));
        hold.setOnFinished(e -> {
            try {
                MainApp.showLogin();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        fadeIn.setOnFinished(e -> {
            ParallelTransition loading = new ParallelTransition(progressAnim, statusAnim);
            loading.setOnFinished(ev -> hold.play());
            loading.play();
        });

        fadeIn.play();
    }
}
