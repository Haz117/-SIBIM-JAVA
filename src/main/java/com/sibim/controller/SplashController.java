package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.MigrationRunner;
import com.sibim.db.offline.OfflineStore;
import com.sibim.db.offline.SyncService;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.util.AnimationUtils;
import java.io.File;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.UpdateChecker;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class SplashController {

    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private StackPane splashRoot;
    @FXML private VBox splashContent;
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
    private boolean finishing     = false;
    private boolean animReady     = false;
    private boolean dbReady       = false;
    private boolean firstRunAdmin = false;
    /** Why startup fell back to offline when it wasn't just a missing connection. */
    private volatile String motivoOffline;

    /** Set once initDatabase() has fully finished (success or offline fallback),
     *  so the smoke test can wait for the db-init thread to settle before it
     *  resets the shared DatabaseConfig state. */
    private static volatile boolean dbInitFinished = false;
    static boolean isDbInitFinished() { return dbInitFinished; }

    /**
     * The window fades in, the bar fills non-linearly and the status line names each
     * startup phase. The badge's spring, the staggered rise and the "sonar" rings were
     * taken out (decorative, see AnimationUtils).
     */
    @FXML
    public void initialize() {
        progressBar.setProgress(0);
        lblVersion.setText("v" + UpdateChecker.currentVersion());
        dbInitFinished = false;
        Thread.ofVirtual().name("db-init").start(this::initDatabase);

        FadeTransition rootFade = new FadeTransition(Duration.millis(300), splashRoot);
        rootFade.setFromValue(0); rootFade.setToValue(1);
        rootFade.setInterpolator(Interpolator.EASE_OUT);

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
                MigrationRunner.asegurarEsquema();
                firstRunAdmin = seedAdminIfEmpty();
                try {
                    ConfiguracionRepository cr = new ConfiguracionRepository();
                    cr.findAll();   // also refreshes this PC's copy for offline use
                    String org      = cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
                    String mun      = cr.get("municipio",           "Ixmiquilpan, Hidalgo");
                    // Only a logo set in Configuración; otherwise the splash keeps the app's own.
                    boolean configurado = !cr.get(com.sibim.service.LogoMunicipal.CLAVE_IMAGEN, "").isBlank()
                        || !cr.get(com.sibim.service.LogoMunicipal.CLAVE_RUTA, "").isBlank();
                    String logoPath = configurado ? com.sibim.service.LogoMunicipal.rutaLocal(cr) : null;
                    Platform.runLater(() -> {
                        lblOrg.setText(org + "  ·  " + mun);
                        if (logoPath != null) applyLogoIfExists(logoPath);
                    });
                } catch (Exception ignored) {
                    log.debug("Could not load org name/logo from ConfiguracionRepository during startup", ignored);
                }
            }
        } catch (Exception e) {
            // Only a schema problem (pending migrations this PC can't apply, or a
            // history that doesn't match this build) is worth explaining to the
            // user; Flyway wraps plain connection/IO errors in other exceptions.
            motivoOffline = MigrationRunner.motivoEsquema(e);
            if (motivoOffline != null) log.error(motivoOffline);
            else log.warn("No se pudo conectar a la base de datos: {}", e.getMessage());
            DatabaseConfig.close();
            log.info("Iniciando en modo offline.");
            DatabaseConfig.setOfflineMode(true);
            demoRequested = false;
        } finally {
            // The organigrama/prefixes from the areas table (or this PC's last copy when offline).
            new com.sibim.service.AreaService().cargarCatalogo();
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
        String detalle = motivoOffline != null
            ? "La base de datos necesita una actualización que esta computadora no puede aplicar, así que el "
              + "sistema va a funcionar en modo offline: lo que captures se guarda aquí y se subirá cuando la "
              + "base esté al día. Avisa al administrador del sistema.\n\n" + motivoOffline
            : "No se pudo conectar a la base de datos ahora mismo. El sistema va a funcionar en modo offline: "
              + "todo lo que captures se guarda en esta computadora, y se subirá automáticamente al servidor "
              + "en cuanto vuelva la conexión — no necesitas hacer nada.";
        Dialog<ButtonType> dialog = DialogUtil.styledMessage(
            "mdi2c-cloud-off-outline", "Trabajando sin conexión", "Sin conexión a la base de datos",
            com.sibim.util.AppColors.PRIMARY_D, com.sibim.util.AppColors.INDIGO, detalle);
        dialog.setTitle("Sin conexión a la base de datos");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        DialogUtil.styleButton(dialog.getDialogPane(), ButtonType.OK, AppColors.INFO);
        dialog.showAndWait();
    }
}
