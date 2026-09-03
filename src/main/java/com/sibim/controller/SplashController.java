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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SplashController {

    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private StackPane splashRoot;
    @FXML private StackPane logoWrapper;
    @FXML private StackPane logoBadge;
    @FXML private VBox brandBox;
    @FXML private Label lblTitle;
    @FXML private Label lblSubtitle;
    @FXML private Region splashDivider;
    @FXML private Label lblOrg;
    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private StackPane ringTr;
    @FXML private StackPane ringBl;

    private final List<Animation> loops = new ArrayList<>();
    private Arc arcRing;
    private RotateTransition arcSpin;
    private boolean animReady     = false;
    private boolean dbReady       = false;
    private boolean firstRunAdmin = false;

    @FXML
    public void initialize() {
        progressBar.setProgress(0);
        Thread.ofVirtual().name("db-init").start(this::initDatabase);

        arcRing = buildArcRing();

        // ── Root fade-in ──────────────────────────────────────────────────────
        FadeTransition rootFade = new FadeTransition(Duration.millis(280), splashRoot);
        rootFade.setFromValue(0); rootFade.setToValue(1);
        rootFade.setInterpolator(Interpolator.EASE_OUT);

        // ── Logo: scale pop (0.6→1.06→1.0) + fade ────────────────────────────
        logoBadge.setOpacity(0);
        logoBadge.setScaleX(0.6); logoBadge.setScaleY(0.6);
        FadeTransition logoFade = new FadeTransition(Duration.millis(360), logoBadge);
        logoFade.setFromValue(0); logoFade.setToValue(1);
        logoFade.setInterpolator(Interpolator.EASE_OUT);
        Timeline logoScale = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(logoBadge.scaleXProperty(), 0.60, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 0.60, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(300),
                new KeyValue(logoBadge.scaleXProperty(), 1.06, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(), 1.06, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(420),
                new KeyValue(logoBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(logoBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        ParallelTransition logoIn = new ParallelTransition(logoFade, logoScale);
        logoIn.setDelay(Duration.millis(100));

        // ── Brand labels: sequential fade-up ─────────────────────────────────
        ParallelTransition titleIn    = labelFadeUp(lblTitle,    200, 340);
        ParallelTransition subtitleIn = labelFadeUp(lblSubtitle, 300, 300);

        // Divider: scale-X from 0 → 1, fade 0 → 1
        splashDivider.setScaleX(0); splashDivider.setOpacity(0);
        Timeline dividerIn = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(splashDivider.scaleXProperty(), 0.0, Interpolator.EASE_OUT),
                new KeyValue(splashDivider.opacityProperty(), 0.0)),
            new KeyFrame(Duration.millis(320),
                new KeyValue(splashDivider.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(splashDivider.opacityProperty(), 1.0))
        );
        dividerIn.setDelay(Duration.millis(380));

        ParallelTransition orgIn = labelFadeUp(lblOrg, 460, 280);

        // ── Progress section ──────────────────────────────────────────────────
        progressBox.setOpacity(0); progressBox.setTranslateY(10);
        FadeTransition progFade = new FadeTransition(Duration.millis(300), progressBox);
        progFade.setFromValue(0); progFade.setToValue(1);
        TranslateTransition progSlide = new TranslateTransition(Duration.millis(300), progressBox);
        progSlide.setFromY(10); progSlide.setToY(0);
        progSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition progressIn = new ParallelTransition(progFade, progSlide);
        progressIn.setDelay(Duration.millis(560));

        // Non-linear progress fill
        Timeline progressAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(progressBar.progressProperty(), 0.0),
                new KeyValue(arcRing.lengthProperty(),       0.0)),
            new KeyFrame(Duration.millis(400),
                new KeyValue(progressBar.progressProperty(), 0.18, Interpolator.EASE_IN),
                new KeyValue(arcRing.lengthProperty(),      -64.8, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(1000),
                new KeyValue(progressBar.progressProperty(), 0.52,   Interpolator.EASE_BOTH),
                new KeyValue(arcRing.lengthProperty(),      -187.2,  Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1650),
                new KeyValue(progressBar.progressProperty(), 0.82,   Interpolator.EASE_BOTH),
                new KeyValue(arcRing.lengthProperty(),      -295.2,  Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2100),
                new KeyValue(progressBar.progressProperty(), 1.0,   Interpolator.EASE_OUT),
                new KeyValue(arcRing.lengthProperty(),      -360.0, Interpolator.EASE_OUT))
        );
        progressAnim.setDelay(Duration.millis(680));

        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),    e -> lblStatus.setText("Iniciando sistema...")),
            new KeyFrame(Duration.millis(650),  e -> lblStatus.setText("Conectando base de datos...")),
            new KeyFrame(Duration.millis(1300), e -> lblStatus.setText("Cargando módulos...")),
            new KeyFrame(Duration.millis(1950), e -> lblStatus.setText("Sistema listo  ✓"))
        );
        statusAnim.setDelay(Duration.millis(680));

        PauseTransition hold = new PauseTransition(Duration.millis(400));
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
            rootFade, logoIn, titleIn, subtitleIn, dividerIn, orgIn,
            progressIn, progressAnim, statusAnim);
        entrance.setOnFinished(e -> hold.play());
        entrance.play();

        startGlowPulse();
    }

    private Arc buildArcRing() {
        Arc track = new Arc(58, 58, 57, 57, 90, -360);
        track.setType(ArcType.OPEN);
        track.setFill(null);
        track.setStroke(Color.web("#3730a3", 0.30));
        track.setStrokeWidth(2.5);

        Arc arc = new Arc(58, 58, 57, 57, 90, 0);
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStroke(Color.web("#818cf8"));
        arc.setStrokeWidth(2.5);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        Pane arcLayer = new Pane(track, arc);
        arcLayer.setPrefSize(116, 116);
        arcLayer.setMouseTransparent(true);
        if (logoWrapper != null) logoWrapper.getChildren().add(arcLayer);
        return arc;
    }

    private void startArcSpin() {
        if (arcRing == null) return;
        arcRing.setLength(-90);
        arcSpin = new RotateTransition(Duration.millis(1100), arcRing);
        arcSpin.setByAngle(-360);
        arcSpin.setCycleCount(Animation.INDEFINITE);
        arcSpin.setInterpolator(Interpolator.LINEAR);
        arcSpin.play();
    }

    /** Single ambient effect: a subtle glow pulse on the logo badge. */
    private void startGlowPulse() {
        DropShadow glow = new DropShadow(36, Color.rgb(99, 102, 241, 0.65));
        glow.setSpread(0); glow.setOffsetY(10);
        logoBadge.setEffect(glow);
        Timeline glowPulse = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(glow.radiusProperty(), 30, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1400), new KeyValue(glow.radiusProperty(), 52, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2800), new KeyValue(glow.radiusProperty(), 30, Interpolator.EASE_BOTH))
        );
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.setDelay(Duration.millis(500));
        glowPulse.play();
        loops.add(glowPulse);
    }

    private static ParallelTransition labelFadeUp(javafx.scene.Node node, int delayMs, int durationMs) {
        node.setOpacity(0);
        if (node instanceof javafx.scene.control.Labeled l) l.setTranslateY(14);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), node);
        tt.setFromY(14); tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setDelay(Duration.millis(delayMs));
        return pt;
    }

    private void stopLoops() {
        for (Animation a : loops) if (a != null) a.stop();
        loops.clear();
        if (arcSpin != null) { arcSpin.stop(); arcSpin = null; }
    }

    private void maybeTransition() {
        if (animReady && dbReady) Platform.runLater(this::afterEntrance);
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
                try {
                    com.sibim.repository.ConfiguracionRepository cr = new com.sibim.repository.ConfiguracionRepository();
                    String org = cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
                    String mun = cr.get("municipio", "Ixmiquilpan, Hidalgo");
                    Platform.runLater(() -> lblOrg.setText(org + "  ·  " + mun));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar a la base de datos: {}", e.getMessage());
            DatabaseConfig.close();
            log.info("Iniciando en modo offline.");
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
        if (DatabaseConfig.isDemoMode() && !confirmDemoMode()) { Platform.exit(); return; }
        if (DatabaseConfig.isOfflineMode()) notifyOfflineMode();
        if (firstRunAdmin) notifyFirstRun();
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
            log.info("Primera ejecución: usuario administrador inicial creado.");
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
