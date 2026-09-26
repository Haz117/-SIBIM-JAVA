package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.MigrationRunner;
import com.sibim.db.offline.OfflineStore;
import com.sibim.db.offline.SyncService;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.util.AnimationUtils;
import java.io.File;
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
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

public class SplashController {

    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private StackPane splashRoot;
    @FXML private VBox splashContent;
    @FXML private StackPane markWrap;
    @FXML private Region logoPulse;
    @FXML private Region logoPulse2;
    @FXML private Region splashSep;
    @FXML private StackPane logoBadge;
    @FXML private Label lblTitle;
    @FXML private Label lblSubtitle;
    @FXML private Label lblOrg;
    @FXML private Label lblVersion;
    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;

    private static final double LOGO_SIZE = 68;

    private Timeline dotAnim;
    private final List<Animation> pulseAnims = new ArrayList<>();
    private boolean finishing     = false;
    private boolean animReady     = false;
    private boolean dbReady       = false;
    private boolean firstRunAdmin = false;

    /** Set once initDatabase() has fully finished (success or offline fallback),
     *  so the smoke test can wait for the db-init thread to settle before it
     *  resets the shared DatabaseConfig state. */
    private static volatile boolean dbInitFinished = false;
    static boolean isDbInitFinished() { return dbInitFinished; }

    /**
     * Entrance: window fades in, badge springs in (0.78→1.06→1.0 overshoot), then wordmark,
     * subtitle, separator and progress box rise in staggered. Two sonar rings breathe out from
     * the badge. Bar fills non-linearly and status text crossfades through startup phases.
     * With animations disabled everything simply shows at once.
     */
    @FXML
    public void initialize() {
        progressBar.setProgress(0);
        dbInitFinished = false;
        Thread.ofVirtual().name("db-init").start(this::initDatabase);

        FadeTransition rootFade = new FadeTransition(Duration.millis(300), splashRoot);
        rootFade.setFromValue(0); rootFade.setToValue(1);
        rootFade.setInterpolator(Interpolator.EASE_OUT);

        revealSpring(markWrap,   80);
        reveal(lblTitle,        280, 8, 1);
        reveal(lblSubtitle,     370, 8, 1);
        reveal(splashSep,       460, 0, 1);
        reveal(progressBox,     500, 8, 1);
        reveal(lblOrg,          640, 0, 1);
        reveal(lblVersion,      640, 0, 1);

        // Non-linear fill; stops at 88 % so the bar never falsely reaches 100 % before the DB is ready
        Timeline progressAnim = new Timeline(
            new KeyFrame(Duration.ZERO,           new KeyValue(progressBar.progressProperty(), 0.0)),
            new KeyFrame(Duration.millis(400),    new KeyValue(progressBar.progressProperty(), 0.18, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(1000),   new KeyValue(progressBar.progressProperty(), 0.52, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1650),   new KeyValue(progressBar.progressProperty(), 0.82, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(2200),   new KeyValue(progressBar.progressProperty(), 0.88, Interpolator.EASE_OUT))
        );
        progressAnim.setDelay(Duration.millis(500));

        Timeline statusAnim = new Timeline(
            new KeyFrame(Duration.millis(0),    e -> setStatus("Iniciando sistema…")),
            new KeyFrame(Duration.millis(700),  e -> setStatus("Conectando base de datos…")),
            new KeyFrame(Duration.millis(1400), e -> setStatus("Cargando módulos…")),
            new KeyFrame(Duration.millis(2100), e -> setStatus("Preparando interfaz…"))
        );
        statusAnim.setDelay(Duration.millis(500));

        startPulse();

        PauseTransition hold = new PauseTransition(Duration.millis(300));
        hold.setOnFinished(e -> {
            animReady = true;
            if (!dbReady) {
                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                startDotAnimation();
            }
            maybeTransition();
        });

        ParallelTransition entrance = new ParallelTransition(rootFade, progressAnim, statusAnim);
        entrance.setOnFinished(e -> hold.play());
        entrance.play();
    }

    /** Fade a node in with an optional rise after {@code delayMs}. */
    private static void reveal(Node node, int delayMs, double riseFrom, double scaleFrom) {
        if (node == null) return;
        if (!AnimationUtils.isEnabled()) return;
        node.setOpacity(0);
        node.setTranslateY(riseFrom);
        node.setScaleX(scaleFrom); node.setScaleY(scaleFrom);
        FadeTransition fade = new FadeTransition(Duration.millis(420), node);
        fade.setFromValue(0); fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition in = new ParallelTransition(fade);
        if (riseFrom != 0) {
            TranslateTransition rise = new TranslateTransition(Duration.millis(420), node);
            rise.setFromY(riseFrom); rise.setToY(0);
            rise.setInterpolator(Interpolator.EASE_OUT);
            in.getChildren().add(rise);
        }
        in.setDelay(Duration.millis(delayMs));
        in.play();
    }

    /** Scale-spring entrance for the badge: grows slightly past 1.0 then settles, like a physical object dropping in. */
    private static void revealSpring(Node node, int delayMs) {
        if (node == null) return;
        if (!AnimationUtils.isEnabled()) return;
        node.setOpacity(0);
        node.setScaleX(0.78); node.setScaleY(0.78);
        FadeTransition fade = new FadeTransition(Duration.millis(380), node);
        fade.setFromValue(0); fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        Timeline spring = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.scaleXProperty(), 0.78),
                new KeyValue(node.scaleYProperty(), 0.78)),
            new KeyFrame(Duration.millis(430),
                new KeyValue(node.scaleXProperty(), 1.07, Interpolator.EASE_OUT),
                new KeyValue(node.scaleYProperty(), 1.07, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(570),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_IN))
        );
        ParallelTransition in = new ParallelTransition(fade, spring);
        in.setDelay(Duration.millis(delayMs));
        in.play();
    }

    /** Two sonar rings expand and fade from the badge; the second is offset 900 ms for a ripple feel. */
    private void startPulse() {
        if (!AnimationUtils.isEnabled()) return;
        if (logoPulse  != null) pulseAnims.add(buildRing(logoPulse,  800,    0));
        if (logoPulse2 != null) pulseAnims.add(buildRing(logoPulse2, 800, 900));
    }

    private static Animation buildRing(Region ring, int durationMs, int delayMs) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(durationMs), ring);
        grow.setFromX(1); grow.setFromY(1); grow.setToX(2.2); grow.setToY(2.2);
        grow.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), ring);
        fade.setFromValue(0.50); fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition anim = new ParallelTransition(grow, fade);
        anim.setDelay(Duration.millis(delayMs));
        anim.setCycleCount(Animation.INDEFINITE);
        anim.play();
        return anim;
    }

    /** Swap the status line with a quick crossfade instead of a hard text change. */
    private void setStatus(String text) {
        if (!AnimationUtils.isEnabled()) { lblStatus.setText(text); return; }
        FadeTransition out = new FadeTransition(Duration.millis(90), lblStatus);
        out.setToValue(0);
        out.setOnFinished(e -> {
            lblStatus.setText(text);
            FadeTransition in = new FadeTransition(Duration.millis(170), lblStatus);
            in.setToValue(1);
            in.play();
        });
        out.play();
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

    private void stopLoops() {
        if (dotAnim != null) { dotAnim.stop(); dotAnim = null; }
        for (Animation a : pulseAnims) a.stop();
        pulseAnims.clear();
        if (logoPulse  != null) logoPulse.setOpacity(0);
        if (logoPulse2 != null) logoPulse2.setOpacity(0);
    }

    /** Once both the intro and the database are done: close the bar to 100 %, say "Listo", beat, continue. */
    private void maybeTransition() {
        if (!(animReady && dbReady) || finishing) return;
        finishing = true;
        stopLoops();
        setStatus("Listo");
        if (progressBar.getProgress() < 0) progressBar.setProgress(0.9);   // was the indeterminate sweep
        Timeline close = new Timeline(new KeyFrame(Duration.millis(260),
            new KeyValue(progressBar.progressProperty(), 1.0, Interpolator.EASE_BOTH)));
        close.setOnFinished(e -> {
            PauseTransition beat = new PauseTransition(Duration.millis(160));
            beat.setOnFinished(x -> Platform.runLater(this::afterEntrance));
            beat.play();
        });
        close.play();
    }

    private void initDatabase() {
        boolean demoRequested = false;
        try {
            demoRequested = "true".equalsIgnoreCase(DatabaseConfig.setting("DEMO_MODE", "false"));
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
                boolean autoRepair = "true".equalsIgnoreCase(
                    DatabaseConfig.setting(MigrationRunner.AUTO_REPAIR_KEY, "false"));
                boolean migrar = !"false".equalsIgnoreCase(
                    DatabaseConfig.setting(MigrationRunner.MIGRATE_KEY, "true"));
                MigrationRunner.run(flyway, autoRepair, migrar);
                firstRunAdmin = seedAdminIfEmpty();
                try {
                    ConfiguracionRepository cr = new ConfiguracionRepository();
                    String org      = cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
                    String mun      = cr.get("municipio",           "Ixmiquilpan, Hidalgo");
                    String logoPath = cr.get("logo_path",           "");
                    Platform.runLater(() -> {
                        lblOrg.setText(org + "  ·  " + mun);
                        if (!logoPath.isBlank()) applyLogoIfExists(logoPath);
                    });
                } catch (Exception ignored) {
                    log.debug("Could not load org name/logo from ConfiguracionRepository during startup", ignored);
                }
            }
        } catch (Exception e) {
            // Only a validation failure means "history != this build's scripts". Flyway wraps
            // plain connection/IO errors in other FlywayExceptions — those are just "no connection".
            if (e instanceof MigrationRunner.MigracionesPendientesException) {
                log.error(e.getMessage());
            } else if (e instanceof org.flywaydb.core.api.exception.FlywayValidateException) {
                log.error("Las migraciones de la base de datos no coinciden con esta versión del programa; "
                    + "NO se modificó el historial. Si el cambio es intencional agrega {}=true al .env y reinicia. "
                    + "Detalle: {}", MigrationRunner.AUTO_REPAIR_KEY, e.getMessage());
            } else {
                log.warn("No se pudo conectar a la base de datos: {}", e.getMessage());
            }
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
            dbInitFinished = true;
            Platform.runLater(() -> {
                dbReady = true;
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
        if (MainApp.getPrimaryStage() == null) {
            log.debug("Splash finalizado sin Stage principal; se omite la navegación en modo smoke-test");
            return;
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
        } catch (Exception ignored) {
            log.debug("Could not resolve migrations location via URI, falling back to classpath", ignored);
        }
        return "classpath:db/migration";
    }

    private void applyLogoIfExists(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) return;
            javafx.scene.image.Image img =
                new javafx.scene.image.Image(f.toURI().toString(), LOGO_SIZE, LOGO_SIZE, true, true, true);
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(LOGO_SIZE); iv.setFitHeight(LOGO_SIZE); iv.setPreserveRatio(true);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(LOGO_SIZE, LOGO_SIZE);
            clip.setArcWidth(24); clip.setArcHeight(24);
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
            OfflineStore.findCachedUserByUsername("__probe__");
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
