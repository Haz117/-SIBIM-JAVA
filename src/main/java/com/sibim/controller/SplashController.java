package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class SplashController {

    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private StackPane splashRoot;
    @FXML private StackPane logoBadge;
    @FXML private VBox brandBox;
    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private StackPane ringTr;
    @FXML private StackPane ringBl;

    private final java.util.List<Animation> loops = new java.util.ArrayList<>();

    @FXML
    public void initialize() {
        progressBar.setProgress(0);

        // Root itself fades in immediately so the window isn't a blank flash;
        // the actual "entrance" is staggered per-element below.
        FadeTransition rootFade = new FadeTransition(Duration.millis(220), splashRoot);
        rootFade.setFromValue(0); rootFade.setToValue(1);
        rootFade.setDelay(Duration.millis(60));
        rootFade.setInterpolator(Interpolator.EASE_OUT);

        // Logo: pops in with a slight overshoot (scale 0.55 → 1.06 → 1.0)
        // instead of just fading — reads as a deliberate "arrival", not a
        // static block appearing.
        logoBadge.setOpacity(0);
        logoBadge.setScaleX(0.55); logoBadge.setScaleY(0.55);
        FadeTransition logoFade = new FadeTransition(Duration.millis(360), logoBadge);
        logoFade.setFromValue(0); logoFade.setToValue(1);
        Timeline logoScale = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(logoBadge.scaleXProperty(), 0.55, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 0.55, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(300),
                new KeyValue(logoBadge.scaleXProperty(), 1.06, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 1.06, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(420),
                new KeyValue(logoBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(logoBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        ParallelTransition logoIn = new ParallelTransition(logoFade, logoScale);
        logoIn.setDelay(Duration.millis(120));

        // Brand text and progress section: fade-up, staggered after the logo
        brandBox.setOpacity(0); brandBox.setTranslateY(14);
        FadeTransition brandFade = new FadeTransition(Duration.millis(360), brandBox);
        brandFade.setFromValue(0); brandFade.setToValue(1);
        TranslateTransition brandSlide = new TranslateTransition(Duration.millis(360), brandBox);
        brandSlide.setFromY(14); brandSlide.setToY(0);
        brandSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition brandIn = new ParallelTransition(brandFade, brandSlide);
        brandIn.setDelay(Duration.millis(420));

        progressBox.setOpacity(0); progressBox.setTranslateY(10);
        FadeTransition progFade = new FadeTransition(Duration.millis(320), progressBox);
        progFade.setFromValue(0); progFade.setToValue(1);
        TranslateTransition progSlide = new TranslateTransition(Duration.millis(320), progressBox);
        progSlide.setFromY(10); progSlide.setToY(0);
        progSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition progressIn = new ParallelTransition(progFade, progSlide);
        progressIn.setDelay(Duration.millis(640));

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
        progressAnim.setDelay(Duration.millis(700));

        // Status text changes aligned with progress
        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),   e -> lblStatus.setText("Iniciando sistema...")),
            new KeyFrame(Duration.millis(650),  e -> lblStatus.setText("Conectando base de datos...")),
            new KeyFrame(Duration.millis(1250), e -> lblStatus.setText("Cargando módulos...")),
            new KeyFrame(Duration.millis(1850), e -> lblStatus.setText("Sistema listo  ✓"))
        );
        statusAnim.setDelay(Duration.millis(700));

        PauseTransition hold = new PauseTransition(Duration.millis(420));
        hold.setOnFinished(e -> {
            stopLoops();
            // Deferred: this handler runs synchronously inside the
            // animation pulse (Animation.finished -> runHandler), and
            // Dialog.showAndWait() throws IllegalStateException if called
            // from there ("not allowed during animation or layout
            // processing"). Platform.runLater pushes it to a later pulse,
            // outside that callstack, before confirmDemoMode() can open
            // its Alert.
            Platform.runLater(this::afterEntrance);
        });

        ParallelTransition entrance = new ParallelTransition(
            rootFade, logoIn, brandIn, progressIn, progressAnim, statusAnim);
        entrance.setOnFinished(e -> hold.play());
        entrance.play();

        startAmbientLoops();
    }

    private void afterEntrance() {
        if (DatabaseConfig.isDemoMode() && !confirmDemoMode()) {
            Platform.exit();
            return;
        }
        if (DatabaseConfig.isOfflineMode()) {
            notifyOfflineMode();
        }
        try {
            MainApp.showLogin();
        } catch (Exception ex) {
            log.error("No se pudo cargar la pantalla de login tras el splash", ex);
        }
    }

    /** Continuous, low-key motion that runs underneath the one-shot entrance
     *  animations above — a breathing glow on the logo (replaces the static
     *  CSS dropshadow with an animatable one) and a slow pulse on the two
     *  decorative rings, offset in phase so they don't move in lockstep.
     *  Gives the splash a sense of "alive while working" instead of a single
     *  static frame sitting there for ~2.5s. Stopped once we hand off to the
     *  login screen (see {@code hold.setOnFinished} above). */
    private void startAmbientLoops() {
        DropShadow glow = new DropShadow(38, Color.rgb(99, 102, 241, 0.65));
        glow.setSpread(0);
        glow.setOffsetY(10);
        logoBadge.setEffect(glow);
        Timeline glowPulse = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(glow.radiusProperty(), 34, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1100), new KeyValue(glow.radiusProperty(), 52, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2200), new KeyValue(glow.radiusProperty(), 34, Interpolator.EASE_BOTH))
        );
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.setDelay(Duration.millis(500));
        glowPulse.play();
        loops.add(glowPulse);

        loops.add(ringBreathe(ringTr, 0.92, 1.06, 2600, 0));
        loops.add(ringBreathe(ringBl, 0.94, 1.08, 3100, 300));
    }

    private ScaleTransition ringBreathe(StackPane ring, double from, double to, int periodMs, int delayMs) {
        if (ring == null) return null;
        ring.setScaleX(from); ring.setScaleY(from);
        ScaleTransition st = new ScaleTransition(Duration.millis(periodMs), ring);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to); st.setToY(to);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.setAutoReverse(true);
        st.setCycleCount(Animation.INDEFINITE);
        st.setDelay(Duration.millis(delayMs));
        st.play();
        return st;
    }

    private void stopLoops() {
        for (Animation a : loops) if (a != null) a.stop();
        loops.clear();
    }

    /** DatabaseConfig falls back to an in-memory "modo demo" whenever the
     *  real DB connection fails at startup (see Main.main) — necessary for
     *  local development without Postgres, but silently doing that in a
     *  real deployment would let staff capture real inventory data into
     *  data that vanishes on exit without ever being told. This makes the
     *  fallback loud and requires an explicit choice before continuing. */
    private boolean confirmDemoMode() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Sin conexión a la base de datos");
        alert.setHeaderText("No se pudo conectar a la base de datos real");
        alert.setContentText(
            "El sistema va a iniciar en modo demo, con datos de práctica que NO se guardan. "
            + "Cualquier bien, movimiento o cambio que captures se perderá al cerrar la aplicación.\n\n"
            + "Esto normalmente indica un problema de conexión (base de datos apagada, credenciales "
            + "incorrectas en el archivo .env, o red caída). Si esto es un equipo de producción, "
            + "verifica la configuración antes de continuar.");
        ButtonType btnSalir = new ButtonType("Salir", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnContinuar = new ButtonType("Continuar en modo demo", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(btnSalir, btnContinuar);
        alert.getDialogPane().setPrefWidth(440);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (MainApp.getPrimaryStage() != null) alert.initOwner(MainApp.getPrimaryStage());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == btnContinuar;
    }

    /** Unlike modo demo, offline mode doesn't lose anything — writes persist
     *  locally and sync automatically once the connection comes back (see
     *  com.sibim.db.offline). So this is just informational, not a
     *  Salir/Continuar choice: nothing bad happens if the user dismisses it
     *  without reading closely. */
    private void notifyOfflineMode() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sin conexión a la base de datos");
        alert.setHeaderText("Trabajando sin conexión");
        alert.setContentText(
            "No se pudo conectar a la base de datos ahora mismo. El sistema va a funcionar en modo offline: "
            + "todo lo que captures se guarda en esta computadora, y se subirá automáticamente al servidor "
            + "en cuanto vuelva la conexión — no necesitas hacer nada.");
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.getDialogPane().setPrefWidth(440);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (MainApp.getPrimaryStage() != null) alert.initOwner(MainApp.getPrimaryStage());
        alert.showAndWait();
    }
}
