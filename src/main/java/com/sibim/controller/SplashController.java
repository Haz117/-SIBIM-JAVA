package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
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

import java.net.URL;
import java.nio.file.Paths;
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
    private Animation arcSpin;
    private Timeline dotAnim;
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

        // ── Logo: rise from below + spring bounce (0.5→1.12→0.96→1.0) + fade ──
        logoBadge.setOpacity(0);
        logoBadge.setScaleX(0.5);  logoBadge.setScaleY(0.5);
        logoBadge.setTranslateY(24);
        FadeTransition logoFade = new FadeTransition(Duration.millis(380), logoBadge);
        logoFade.setFromValue(0); logoFade.setToValue(1);
        logoFade.setInterpolator(Interpolator.EASE_OUT);
        Timeline logoScale = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(logoBadge.scaleXProperty(),    0.50, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(),    0.50, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.translateYProperty(), 24,  Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(320),
                new KeyValue(logoBadge.scaleXProperty(),    1.12, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.scaleYProperty(),    1.12, Interpolator.EASE_OUT),
                new KeyValue(logoBadge.translateYProperty(),  0,  Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(430),
                new KeyValue(logoBadge.scaleXProperty(),    0.96, Interpolator.EASE_BOTH),
                new KeyValue(logoBadge.scaleYProperty(),    0.96, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(520),
                new KeyValue(logoBadge.scaleXProperty(),    1.0,  Interpolator.EASE_BOTH),
                new KeyValue(logoBadge.scaleYProperty(),    1.0,  Interpolator.EASE_BOTH))
        );
        ParallelTransition logoIn = new ParallelTransition(logoFade, logoScale);
        logoIn.setDelay(Duration.millis(80));

        // ── Brand labels: sequential fade-up con micro-escala en el título ────
        ParallelTransition titleIn    = labelFadeUpScale(lblTitle,    220, 380);
        ParallelTransition subtitleIn = labelFadeUp(lblSubtitle,      340, 300);

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
                // Stop at 88% so the bar never falsely reaches 100% before the DB is ready
                new KeyValue(progressBar.progressProperty(), 0.88,   Interpolator.EASE_OUT),
                new KeyValue(arcRing.lengthProperty(),      -316.8,  Interpolator.EASE_OUT))
        );
        progressAnim.setDelay(Duration.millis(680));

        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),    e -> lblStatus.setText("Iniciando sistema...")),
            new KeyFrame(Duration.millis(650),  e -> lblStatus.setText("Conectando base de datos...")),
            new KeyFrame(Duration.millis(1300), e -> lblStatus.setText("Cargando módulos...")),
            // Don't say "listo" here — the real "Sistema listo ✓" is set when dbReady fires
            new KeyFrame(Duration.millis(1950), e -> lblStatus.setText("Preparando interfaz..."))
        );
        statusAnim.setDelay(Duration.millis(680));

        PauseTransition hold = new PauseTransition(Duration.millis(400));
        hold.setOnFinished(e -> {
            stopLoops();
            animReady = true;
            if (!dbReady) {
                progressBar.setVisible(false);
                startDotAnimation();
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
        startBlobFloat();
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
        // First shrink arc from fill-up length to 90°, then spin by animating startAngle.
        // We animate startAngle (not rotate the node) because a partial Arc's bounding-box
        // center doesn't coincide with the circle's center, making RotateTransition wobble.
        Timeline shrink = new Timeline(new KeyFrame(Duration.millis(280),
            new KeyValue(arcRing.lengthProperty(), -90.0, Interpolator.EASE_BOTH)));
        shrink.setOnFinished(ev -> {
            double start = arcRing.getStartAngle();
            Timeline spin = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(arcRing.startAngleProperty(), start)),
                new KeyFrame(Duration.millis(1100),
                    new KeyValue(arcRing.startAngleProperty(), start - 360.0, Interpolator.LINEAR))
            );
            spin.setCycleCount(Animation.INDEFINITE);
            spin.play();
            arcSpin = spin;
        });
        shrink.play();
    }

    private void startDotAnimation() {
        String base = "Conectando";
        String[] frames = { base, base + ".", base + "..", base + "..." };
        int[] idx = {0};
        dotAnim = new Timeline(new KeyFrame(Duration.millis(520), e -> {
            idx[0] = (idx[0] + 1) % frames.length;
            lblStatus.setText(frames[idx[0]]);
        }));
        dotAnim.setCycleCount(Animation.INDEFINITE);
        lblStatus.setText(frames[0]);
        dotAnim.play();
    }

    /** Slow vertical float on the decorative background blobs — makes the dark background feel alive. */
    private void startBlobFloat() {
        if (ringTr == null || ringBl == null) return;
        Timeline floatTr = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(ringTr.translateYProperty(), -150.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(3800), new KeyValue(ringTr.translateYProperty(), -168.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(7600), new KeyValue(ringTr.translateYProperty(), -150.0, Interpolator.EASE_BOTH))
        );
        floatTr.setCycleCount(Animation.INDEFINITE);
        floatTr.play();
        Timeline floatBl = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(ringBl.translateYProperty(),  110.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(4200), new KeyValue(ringBl.translateYProperty(),  124.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(8400), new KeyValue(ringBl.translateYProperty(),  110.0, Interpolator.EASE_BOTH))
        );
        floatBl.setCycleCount(Animation.INDEFINITE);
        floatBl.setDelay(Duration.millis(1200));
        floatBl.play();
        loops.add(floatTr);
        loops.add(floatBl);
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

    /** Like labelFadeUp but also animates scale 0.94→1.0 for a weightier entrance. */
    private static ParallelTransition labelFadeUpScale(javafx.scene.Node node, int delayMs, int durationMs) {
        node.setOpacity(0);
        node.setScaleX(0.94); node.setScaleY(0.94);
        if (node instanceof javafx.scene.control.Labeled l) l.setTranslateY(12);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), node);
        tt.setFromY(12); tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        Timeline scaleIn = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.scaleXProperty(), 0.94, Interpolator.EASE_OUT),
                new KeyValue(node.scaleYProperty(), 0.94, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(durationMs),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_OUT))
        );
        ParallelTransition pt = new ParallelTransition(ft, tt, scaleIn);
        pt.setDelay(Duration.millis(delayMs));
        return pt;
    }

    private void stopLoops() {
        for (Animation a : loops) if (a != null) a.stop();
        loops.clear();
        if (arcSpin != null) { arcSpin.stop(); arcSpin = null; }
        if (dotAnim  != null) { dotAnim.stop();  dotAnim  = null; }
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
                org.flywaydb.core.Flyway flyway = Flyway.configure()
                    .dataSource(DatabaseConfig.getDataSource())
                    .locations(resolveMigrationsLocation())
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();
                flyway.repair();
                flyway.migrate();
                firstRunAdmin = seedAdminIfEmpty();
                try {
                    com.sibim.repository.ConfiguracionRepository cr = new com.sibim.repository.ConfiguracionRepository();
                    String org      = cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
                    String mun      = cr.get("municipio",           "Ixmiquilpan, Hidalgo");
                    String logoPath = cr.get("logo_path",           "");
                    Platform.runLater(() -> {
                        lblOrg.setText(org + "  ·  " + mun);
                        if (!logoPath.isBlank()) applyLogoIfExists(logoPath);
                    });
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
                    if (arcRing != null) { arcRing.setLength(-360); }
                    progressBar.setProgress(1.0);
                    lblStatus.setText("Sistema listo  ✓");
                    // Brief logo pulse as visual confirmation
                    Timeline confirmPulse = new Timeline(
                        new KeyFrame(Duration.ZERO,
                            new KeyValue(logoBadge.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(logoBadge.scaleYProperty(), 1.0, Interpolator.EASE_OUT)),
                        new KeyFrame(Duration.millis(180),
                            new KeyValue(logoBadge.scaleXProperty(), 1.07, Interpolator.EASE_OUT),
                            new KeyValue(logoBadge.scaleYProperty(), 1.07, Interpolator.EASE_OUT)),
                        new KeyFrame(Duration.millis(340),
                            new KeyValue(logoBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                            new KeyValue(logoBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                    );
                    confirmPulse.play();
                }
                maybeTransition();
            });
        }
    }

    private void afterEntrance() {
        if (DatabaseConfig.isDemoMode() && !confirmDemoMode()) { Platform.exit(); return; }
        if (DatabaseConfig.isOfflineMode()) {
            if (!tryInitOfflineStore()) return; // blocks if another instance holds the DB
            notifyOfflineMode();
        }
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
        Dialog<ButtonType> dialog = DialogUtil.styledMessage(
            "mdi2c-check-decagram", "Credenciales iniciales", "Base de datos configurada correctamente",
            "#059669", "#047857",
            "El sistema creó un usuario administrador inicial.\n\n"
            + "Credenciales para el primer acceso:\n"
            + "  • Usuario:     superusuario\n"
            + "  • Contraseña:  admin123456\n\n"
            + "Al iniciar sesión el sistema te pedirá cambiar la contraseña. "
            + "Una vez adentro, crea los demás usuarios desde Configuración.");
        dialog.setTitle("Primera ejecución — Credenciales iniciales");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        DialogUtil.styleButton(dialog.getDialogPane(), ButtonType.OK, AppColors.SUCCESS);
        dialog.showAndWait();
    }

    private boolean confirmDemoMode() {
        Dialog<ButtonType> dialog = DialogUtil.styledMessage(
            "mdi2w-wifi-off", "Sin conexión a la base de datos", "No se pudo conectar a la base de datos real",
            AppColors.WARNING, AppColors.WARNING_D,
            "El sistema va a iniciar en modo demo, con datos de práctica que NO se guardan. "
            + "Cualquier bien, movimiento o cambio que captures se perderá al cerrar la aplicación.\n\n"
            + "Esto normalmente indica un problema de conexión (base de datos apagada, credenciales "
            + "incorrectas en el archivo .env, o red caída). Si esto es un equipo de producción, "
            + "verifica la configuración antes de continuar.");
        ButtonType btnSalir     = new ButtonType("Salir", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnContinuar = new ButtonType("Continuar en modo demo", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(btnSalir, btnContinuar);
        DialogUtil.styleButton(dialog.getDialogPane(), btnContinuar, AppColors.WARNING);
        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == btnContinuar;
    }

    // Resolves the Flyway migrations location in a way that bypasses the Java
    // module system's cross-module resource encapsulation.  Calling getResource()
    // from within com.sibim itself always succeeds (a module can read its own
    // resources).  When running exploded (mvn javafx:run / IDE), the URL is a
    // plain file:// path, so we hand Flyway a "filesystem:" location and it reads
    // the SQL files directly without going through ClassLoader — no module barrier.
    // If for some reason the URL isn't a plain file (e.g. inside a JAR), we fall
    // back to the standard classpath location and rely on module opens.
    private static String resolveMigrationsLocation() {
        try {
            URL url = SplashController.class.getResource("/db/migration");
            if (url != null && "file".equals(url.getProtocol())) {
                return "filesystem:" + Paths.get(url.toURI()).toString();
            }
        } catch (Exception ignored) {}
        return "classpath:db/migration";
    }

    private void applyLogoIfExists(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists() || !f.isFile()) return;
            javafx.scene.image.Image img =
                new javafx.scene.image.Image(f.toURI().toString(), 92, 92, true, true, true);
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(92); iv.setFitHeight(92); iv.setPreserveRatio(true);
            javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(46, 46, 46);
            iv.setClip(clip);
            logoBadge.getChildren().setAll(iv);
        } catch (Exception e) {
            log.debug("No se pudo cargar logo del ayuntamiento: {}", e.getMessage());
        }
    }

    /** Probes the offline SQLite store. Returns true when accessible; if it is
     *  locked by another running instance, shows a clear error and exits. */
    private boolean tryInitOfflineStore() {
        try {
            com.sibim.db.offline.OfflineStore.findCachedUserByUsername("__probe__");
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            boolean otherInstance = msg.contains("OTRA_INSTANCIA");
            Dialog<ButtonType> dialog = otherInstance
                ? DialogUtil.styledMessage("mdi2a-alert-circle-outline",
                    "Sistema ya está ejecutándose", "Ya hay una instancia abierta",
                    "#DC2626", "#991B1B",
                    "El sistema SIBIM ya está abierto en esta computadora. "
                    + "Cierra esa ventana primero y vuelve a intentarlo.")
                : DialogUtil.styledMessage("mdi2a-alert-circle-outline",
                    "Error al abrir datos locales", "No se pudo acceder al almacén offline",
                    "#DC2626", "#991B1B",
                    "Ocurrió un error al abrir la base de datos local: " + msg
                    + "\n\nCierra todas las ventanas del sistema y vuelve a intentarlo.");
            dialog.setTitle(otherInstance ? "Sistema ya está ejecutándose" : "Error al abrir datos locales");
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
            DialogUtil.styleButton(dialog.getDialogPane(), ButtonType.OK, AppColors.DANGER);
            dialog.showAndWait();
            Platform.exit();
            return false;
        }
    }

    private void notifyOfflineMode() {
        Dialog<ButtonType> dialog = DialogUtil.styledMessage(
            "mdi2c-cloud-off-outline", "Trabajando sin conexión", "Sin conexión a la base de datos",
            "#2563EB", "#1D4ED8",
            "No se pudo conectar a la base de datos ahora mismo. El sistema va a funcionar en modo offline: "
            + "todo lo que captures se guarda en esta computadora, y se subirá automáticamente al servidor "
            + "en cuanto vuelva la conexión — no necesitas hacer nada.");
        dialog.setTitle("Sin conexión a la base de datos");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        DialogUtil.styleButton(dialog.getDialogPane(), ButtonType.OK, AppColors.INFO);
        dialog.showAndWait();
    }
}
