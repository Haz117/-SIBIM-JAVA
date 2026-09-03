package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SplashController {

    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private StackPane splashRoot;
    @FXML private StackPane logoWrapper;
    @FXML private StackPane logoBadge;
    @FXML private StackPane pingRing1;
    @FXML private StackPane pingRing2;
    @FXML private VBox brandBox;
    @FXML private Label lblTitle;
    @FXML private Label lblSubtitle;
    @FXML private Label lblOrg;
    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private StackPane ringTr;
    @FXML private StackPane ringBl;

    private final List<Animation> loops  = new ArrayList<>();
    private final List<javafx.scene.Node> particleNodes = new ArrayList<>();
    private Arc arcRing;
    private RotateTransition arcSpin;
    private boolean animReady     = false;
    private boolean dbReady       = false;
    private boolean firstRunAdmin = false;

    @FXML
    public void initialize() {
        progressBar.setProgress(0);
        Thread.ofVirtual().name("db-init").start(this::initDatabase);

        // ── Arc progress ring (built first so progressAnim can reference it) ──
        arcRing = buildArcRing();

        // ── Root fade-in ──────────────────────────────────────────────────────
        FadeTransition rootFade = new FadeTransition(Duration.millis(220), splashRoot);
        rootFade.setFromValue(0); rootFade.setToValue(1);
        rootFade.setDelay(Duration.millis(60));
        rootFade.setInterpolator(Interpolator.EASE_OUT);

        // ── Logo: scale pop (0.55→1.07→1.0) + rotate (-18°→0°) + fade ───────
        logoBadge.setOpacity(0);
        logoBadge.setScaleX(0.55); logoBadge.setScaleY(0.55);
        logoBadge.setRotate(-18);
        FadeTransition logoFade = new FadeTransition(Duration.millis(380), logoBadge);
        logoFade.setFromValue(0); logoFade.setToValue(1);
        Timeline logoScale = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(logoBadge.scaleXProperty(), 0.55, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 0.55, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(310),
                new KeyValue(logoBadge.scaleXProperty(), 1.07, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 1.07, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(430),
                new KeyValue(logoBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(logoBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        RotateTransition logoRotate = new RotateTransition(Duration.millis(430), logoBadge);
        logoRotate.setFromAngle(-18); logoRotate.setToAngle(0);
        logoRotate.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition logoIn = new ParallelTransition(logoFade, logoScale, logoRotate);
        logoIn.setDelay(Duration.millis(110));

        // ── Brand labels: each slides up independently, 80 ms stagger ────────
        ParallelTransition titleIn    = labelFadeUp(lblTitle,    390, 320);
        ParallelTransition subtitleIn = labelFadeUp(lblSubtitle, 470, 290);
        ParallelTransition orgIn      = labelFadeUp(lblOrg,      545, 270);

        // ── Progress section ──────────────────────────────────────────────────
        progressBox.setOpacity(0); progressBox.setTranslateY(10);
        FadeTransition progFade = new FadeTransition(Duration.millis(320), progressBox);
        progFade.setFromValue(0); progFade.setToValue(1);
        TranslateTransition progSlide = new TranslateTransition(Duration.millis(320), progressBox);
        progSlide.setFromY(10); progSlide.setToY(0);
        progSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition progressIn = new ParallelTransition(progFade, progSlide);
        progressIn.setDelay(Duration.millis(650));

        // Non-linear progress fill — arc mirrors progress bar in same timeline
        Timeline progressAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(progressBar.progressProperty(), 0.0),
                new KeyValue(arcRing.lengthProperty(),       0.0)),
            new KeyFrame(Duration.millis(350),
                new KeyValue(progressBar.progressProperty(), 0.18, Interpolator.EASE_IN),
                new KeyValue(arcRing.lengthProperty(),      -64.8, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(900),
                new KeyValue(progressBar.progressProperty(), 0.52,   Interpolator.EASE_BOTH),
                new KeyValue(arcRing.lengthProperty(),      -187.2,  Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1550),
                new KeyValue(progressBar.progressProperty(), 0.82,   Interpolator.EASE_BOTH),
                new KeyValue(arcRing.lengthProperty(),      -295.2,  Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2000),
                new KeyValue(progressBar.progressProperty(), 1.0,   Interpolator.EASE_OUT),
                new KeyValue(arcRing.lengthProperty(),      -360.0, Interpolator.EASE_OUT))
        );
        progressAnim.setDelay(Duration.millis(700));

        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),    e -> lblStatus.setText("Iniciando sistema...")),
            new KeyFrame(Duration.millis(650),  e -> lblStatus.setText("Conectando base de datos...")),
            new KeyFrame(Duration.millis(1250), e -> lblStatus.setText("Cargando módulos...")),
            new KeyFrame(Duration.millis(1850), e -> lblStatus.setText("Sistema listo  ✓"))
        );
        statusAnim.setDelay(Duration.millis(700));

        PauseTransition hold = new PauseTransition(Duration.millis(420));
        hold.setOnFinished(e -> {
            stopLoops();
            animReady = true;
            if (!dbReady) {
                lblStatus.setText("Conectando a la base de datos...");
                progressBar.setProgress(-1);
                startArcSpin();
            }
            maybeTransition();
        });

        ParallelTransition entrance = new ParallelTransition(
            rootFade, logoIn, titleIn, subtitleIn, orgIn, progressIn, progressAnim, statusAnim);
        entrance.setOnFinished(e -> hold.play());
        entrance.play();

        startAmbientLoops();
    }

    /** Creates the circular progress ring overlay and inserts it into logoWrapper. */
    private Arc buildArcRing() {
        // Background track — full dim circle
        Arc track = new Arc(55, 55, 57, 57, 90, -360);
        track.setType(ArcType.OPEN);
        track.setFill(null);
        track.setStroke(Color.web("#3730a3", 0.38));
        track.setStrokeWidth(3);
        // Animated progress arc
        Arc arc = new Arc(55, 55, 57, 57, 90, 0);
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStroke(Color.web("#818cf8"));
        arc.setStrokeWidth(3);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        // Pane overlay — fixed 110×110, doesn't perturb StackPane layout
        Pane arcLayer = new Pane(track, arc);
        arcLayer.setPrefSize(110, 110);
        arcLayer.setMouseTransparent(true);
        if (logoWrapper != null) logoWrapper.getChildren().add(arcLayer);
        return arc;
    }

    /** Spins a quarter-arc to signal indeterminate DB wait. */
    private void startArcSpin() {
        if (arcRing == null) return;
        arcRing.setLength(-90);
        arcSpin = new RotateTransition(Duration.millis(1200), arcRing);
        arcSpin.setByAngle(-360);
        arcSpin.setCycleCount(Animation.INDEFINITE);
        arcSpin.setInterpolator(Interpolator.LINEAR);
        arcSpin.play();
    }

    private static ParallelTransition labelFadeUp(Label lbl, int delayMs, int durationMs) {
        lbl.setOpacity(0); lbl.setTranslateY(18);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), lbl);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), lbl);
        tt.setFromY(18); tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setDelay(Duration.millis(delayMs));
        return pt;
    }

    private void maybeTransition() {
        if (animReady && dbReady) {
            Platform.runLater(this::afterEntrance);
        }
    }

    private void initDatabase() {
        boolean demoRequested = false;
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            demoRequested = "true".equalsIgnoreCase(dotenv.get("DEMO_MODE", System.getenv("DEMO_MODE")));
            if (demoRequested) {
                log.info("DEMO_MODE=true — iniciando en modo demo (datos ficticios en memoria).");
                DatabaseConfig.setDemoMode(true);
            } else {
                DatabaseConfig.init();
                Flyway.configure()
                    .dataSource(DatabaseConfig.getDataSource())
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
                firstRunAdmin = seedAdminIfEmpty();
                // Update org label from DB config (best-effort; fallback is the FXML default)
                try {
                    com.sibim.repository.ConfiguracionRepository cr = new com.sibim.repository.ConfiguracionRepository();
                    String org = cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
                    String mun = cr.get("municipio", "Ixmiquilpan, Hidalgo");
                    Platform.runLater(() -> lblOrg.setText(org + "  ·  " + mun));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar a la base de datos o el esquema no existe: {}", e.getMessage());
            DatabaseConfig.close();
            log.info("Iniciando en modo offline — los cambios se guardan localmente y se sincronizan al reconectar.");
            DatabaseConfig.setOfflineMode(true);
            demoRequested = false;
        } finally {
            if (!demoRequested) {
                try { SyncService.startWatching(); } catch (Exception se) {
                    log.error("Error al iniciar SyncService", se);
                }
            }
            Platform.runLater(() -> {
                dbReady = true;
                if (animReady) {
                    if (arcSpin != null) { arcSpin.stop(); arcSpin = null; }
                    if (arcRing != null) { arcRing.setRotate(0); arcRing.setLength(-360); }
                    progressBar.setProgress(1.0);
                    lblStatus.setText("Sistema listo  ✓");
                }
                maybeTransition();
            });
        }
    }

    private void afterEntrance() {
        if (DatabaseConfig.isDemoMode() && !confirmDemoMode()) {
            Platform.exit();
            return;
        }
        if (DatabaseConfig.isOfflineMode()) {
            notifyOfflineMode();
        }
        if (firstRunAdmin) {
            notifyFirstRun();
        }
        try {
            MainApp.showLogin();
        } catch (Exception ex) {
            log.error("No se pudo cargar la pantalla de login tras el splash", ex);
        }
    }

    private boolean seedAdminIfEmpty() {
        try (java.sql.Connection conn = DatabaseConfig.getConnection()) {
            try (java.sql.PreparedStatement check =
                    conn.prepareStatement("SELECT COUNT(*) FROM users")) {
                java.sql.ResultSet rs = check.executeQuery();
                if (!rs.next() || rs.getLong(1) > 0) return false;
            }
            String hash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj1o.FxRzFNS";
            String id   = java.util.UUID.randomUUID().toString();
            try (java.sql.PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (id, username, password, nombre, cargo, role, debe_cambiar_password) "
                    + "VALUES (?, 'superusuario', ?, 'Administrador del Sistema', 'Superusuario', 'admin', TRUE) "
                    + "ON CONFLICT (username) DO NOTHING")) {
                ins.setString(1, id);
                ins.setString(2, hash);
                ins.executeUpdate();
            }
            log.info("Primera ejecución: usuario administrador inicial creado (superusuario / admin123456).");
            return true;
        } catch (Exception e) {
            log.warn("No se pudo verificar ni sembrar usuario inicial: {}", e.getMessage());
            return false;
        }
    }

    private void notifyFirstRun() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Primera ejecución — Credenciales iniciales");
        alert.setHeaderText("Base de datos configurada correctamente");
        alert.setContentText(
            "El sistema creó un usuario administrador inicial.\n\n"
            + "Credenciales para el primer acceso:\n"
            + "  • Usuario:     superusuario\n"
            + "  • Contraseña:  admin123456\n\n"
            + "Al iniciar sesión el sistema te pedirá cambiar la contraseña. "
            + "Una vez adentro, crea los demás usuarios desde Configuración.");
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.getDialogPane().setPrefWidth(440);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (MainApp.getPrimaryStage() != null) alert.initOwner(MainApp.getPrimaryStage());
        alert.showAndWait();
    }

    /** Ambient loops while DB initialises:
     *  glow pulse · gentle float · sonar ping rings · deco ring breathe · floating particles */
    private void startAmbientLoops() {
        // Glow pulse
        DropShadow glow = new DropShadow(38, Color.rgb(99, 102, 241, 0.70));
        glow.setSpread(0); glow.setOffsetY(10);
        logoBadge.setEffect(glow);
        Timeline glowPulse = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(glow.radiusProperty(), 34, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1100), new KeyValue(glow.radiusProperty(), 56, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2200), new KeyValue(glow.radiusProperty(), 34, Interpolator.EASE_BOTH))
        );
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.setDelay(Duration.millis(500));
        glowPulse.play();
        loops.add(glowPulse);

        // Gentle vertical float
        TranslateTransition floatAnim = new TranslateTransition(Duration.millis(2800), logoBadge);
        floatAnim.setFromY(0); floatAnim.setToY(-7);
        floatAnim.setAutoReverse(true);
        floatAnim.setCycleCount(Animation.INDEFINITE);
        floatAnim.setInterpolator(Interpolator.EASE_BOTH);
        floatAnim.play();
        loops.add(floatAnim);

        // Sonar pings — ring1 leads, ring2 trails by 700 ms
        if (pingRing1 != null) loops.add(pingLoop(pingRing1, 420));
        if (pingRing2 != null) loops.add(pingLoop(pingRing2, 1120));

        // Background deco rings breathe
        loops.add(ringBreathe(ringTr, 0.92, 1.06, 2600,   0));
        loops.add(ringBreathe(ringBl, 0.94, 1.08, 3100, 300));

        // Floating particle dots
        startParticles();
    }

    /** Ring expands from 1× to 2.4×, fading out over 1200 ms, then rests 800 ms. */
    private SequentialTransition pingLoop(StackPane ring, int initialDelayMs) {
        ring.setScaleX(1); ring.setScaleY(1); ring.setOpacity(0);
        Timeline ping = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(ring.scaleXProperty(), 1.0),
                new KeyValue(ring.scaleYProperty(), 1.0),
                new KeyValue(ring.opacityProperty(), 0.0)),
            new KeyFrame(Duration.millis(90),
                new KeyValue(ring.opacityProperty(), 0.65)),
            new KeyFrame(Duration.millis(1200),
                new KeyValue(ring.scaleXProperty(), 2.4, Interpolator.EASE_OUT),
                new KeyValue(ring.scaleYProperty(), 2.4, Interpolator.EASE_OUT),
                new KeyValue(ring.opacityProperty(), 0.0, Interpolator.EASE_IN))
        );
        SequentialTransition seq = new SequentialTransition(
            ping, new PauseTransition(Duration.millis(800)));
        seq.setCycleCount(Animation.INDEFINITE);
        seq.setDelay(Duration.millis(initialDelayMs));
        seq.play();
        return seq;
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

    /** Adds small indigo/violet dots to the splash root that float upward and fade. */
    private void startParticles() {
        if (splashRoot == null) return;
        Random rng = new Random(7);
        String[] colors   = {"#6366f1", "#818cf8", "#a78bfa", "#c4b5fd"};
        double[] maxOpacity = {0.65,    0.55,       0.50,      0.40};
        for (int i = 0; i < 12; i++) {
            double radius = 1.5 + rng.nextDouble() * 2.0;
            int    ci     = rng.nextInt(colors.length);
            Circle dot    = new Circle(radius, Color.web(colors[ci]));
            double tx     = (rng.nextDouble() - 0.5) * 220;
            double ty     = rng.nextDouble() * 160 - 80;
            dot.setTranslateX(tx);
            dot.setTranslateY(ty);
            dot.setOpacity(0);
            dot.setMouseTransparent(true);
            splashRoot.getChildren().add(dot);
            particleNodes.add(dot);

            double dur  = 2200 + rng.nextDouble() * 2000;
            double dly  = rng.nextDouble() * 3000;
            double byY  = -(55 + rng.nextDouble() * 65);
            double maxOp = maxOpacity[ci];

            TranslateTransition rise = new TranslateTransition(Duration.millis(dur), dot);
            rise.setFromY(ty); rise.setToY(ty + byY);
            rise.setCycleCount(Animation.INDEFINITE);
            rise.setInterpolator(Interpolator.EASE_IN);

            Timeline fade = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(dot.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(dur * 0.20),
                    new KeyValue(dot.opacityProperty(), maxOp, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(dur * 0.75),
                    new KeyValue(dot.opacityProperty(), maxOp * 0.8)),
                new KeyFrame(Duration.millis(dur),
                    new KeyValue(dot.opacityProperty(), 0.0, Interpolator.EASE_IN))
            );
            fade.setCycleCount(Animation.INDEFINITE);

            ParallelTransition pt = new ParallelTransition(rise, fade);
            pt.setDelay(Duration.millis(dly));
            pt.play();
            loops.add(pt);
        }
    }

    private void stopLoops() {
        for (Animation a : loops) if (a != null) a.stop();
        loops.clear();
        if (arcSpin != null) { arcSpin.stop(); arcSpin = null; }
        if (pingRing1 != null) { pingRing1.setOpacity(0); pingRing1.setScaleX(1); pingRing1.setScaleY(1); }
        if (pingRing2 != null) { pingRing2.setOpacity(0); pingRing2.setScaleX(1); pingRing2.setScaleY(1); }
        if (logoBadge  != null) logoBadge.setTranslateY(0);
        if (splashRoot != null) splashRoot.getChildren().removeAll(particleNodes);
        particleNodes.clear();
    }

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
        ButtonType btnSalir     = new ButtonType("Salir", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnContinuar = new ButtonType("Continuar en modo demo", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(btnSalir, btnContinuar);
        alert.getDialogPane().setPrefWidth(440);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (MainApp.getPrimaryStage() != null) alert.initOwner(MainApp.getPrimaryStage());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == btnContinuar;
    }

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
