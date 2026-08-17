package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private Label userNameLabel;
    @FXML private Label userRolLabel;
    @FXML private Label userAvatarLabel;
    @FXML private Button btnDashboard;
    @FXML private Button btnOrganigrama;
    @FXML private Button btnProductos;
    @FXML private Button btnCategorias;
    @FXML private Button btnMovimientos;
    @FXML private Button btnAlertas;
    @FXML private Button btnReportes;
    @FXML private Button btnConfiguracion;
    @FXML private Label alertBadge;
    @FXML private Label statusDbLabel;
    @FXML private Label statusUserLabel;
    @FXML private Label statusTimeLabel;

    private Button activeButton;
    private Object currentController;
    private final ProductoService alertProductoService = new ProductoService();

    // Only one MainController is ever active at a time — this lets child
    // views loaded into contentArea (e.g. Organigrama) trigger navigation
    // without needing an injected reference threaded through every FXML load.
    private static MainController instance;

    public static MainController getInstance() { return instance; }

    @FXML
    public void initialize() {
        instance = this;
        if (SessionManager.getCurrentUser() != null) {
            String nombre = SessionManager.getCurrentUser().getNombre();
            userNameLabel.setText(nombre);
            userRolLabel.setText(SessionManager.getCurrentUser().getRol().getEtiqueta());
            if (userAvatarLabel != null && nombre != null && !nombre.isBlank())
                userAvatarLabel.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }
        navigateTo("dashboard", btnDashboard);
        // Load alert count
        loadAlertBadge();
        // Wire keyboard shortcuts once scene is available
        contentArea.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) setupKeyboardShortcuts(scene);
        });
        // Refresh badge every 3 minutes
        Timeline badgeRefresh = new Timeline(new KeyFrame(Duration.minutes(3), e -> loadAlertBadge()));
        badgeRefresh.setCycleCount(Timeline.INDEFINITE);
        badgeRefresh.play();
        // Status bar
        updateStatusBar();
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateStatusTime()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    @FXML private void onDashboard()     { navigateTo("dashboard",     btnDashboard); }
    @FXML private void onOrganigrama()   { navigateTo("organigrama",   btnOrganigrama); }
    @FXML private void onProductos()     { navigateTo("productos",     btnProductos); }
    @FXML private void onCategorias()    { navigateTo("categorias",    btnCategorias); }
    @FXML private void onMovimientos()   { navigateTo("movimientos",   btnMovimientos); }
    @FXML private void onAlertas()       { navigateTo("alertas",       btnAlertas); }
    @FXML private void onReportes()      { navigateTo("reportes",      btnReportes); }
    @FXML private void onConfiguracion() { navigateTo("configuracion", btnConfiguracion); }

    @FXML
    private void onLogout() {
        if (!ConfirmacionUtil.confirmar("Cerrar sesión", "¿Deseas cerrar tu sesión?")) return;
        SessionManager.logout();
        try { MainApp.showLogin(); } catch (Exception e) { log.error("No se pudo volver a la pantalla de login", e); }
    }

    private static final double ACTIVE_PILL_OFFSET_X = 8;

    /** Slides the active nav button {@code ACTIVE_PILL_OFFSET_X}px to the
     *  right so its rounded pill (see .nav-btn.nav-active in styles.css)
     *  visibly overlaps into the content area, and slides the previously
     *  active one back to 0. translateX doesn't affect layout — the sidebar
     *  VBox is drawn above the content row (see main.fxml), so the part of
     *  the button that crosses x=220 paints over the content instead of
     *  being clipped by it. */
    private void animateActivePill(Button newActive, Button oldActive) {
        if (oldActive != null && oldActive != newActive) {
            TranslateTransition back = new TranslateTransition(Duration.millis(180), oldActive);
            back.setInterpolator(Interpolator.EASE_OUT);
            back.setToX(0);
            back.play();
        }
        TranslateTransition forward = new TranslateTransition(Duration.millis(190), newActive);
        forward.setInterpolator(Interpolator.EASE_OUT);
        forward.setToX(ACTIVE_PILL_OFFSET_X);
        forward.play();
    }

    private void navigateTo(String view, Button button) {
        try {
            // Update nav state immediately for instant visual feedback
            if (activeButton != null) activeButton.getStyleClass().remove("nav-active");
            button.getStyleClass().add("nav-active");
            animateActivePill(button, activeButton);
            activeButton = button;

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/" + view + ".fxml")));
            Node node = loader.load();

            if (currentController instanceof AlertasController ac) ac.stopAutoRefresh();
            currentController = loader.getController();

            if (!contentArea.getChildren().isEmpty()) {
                Node current = contentArea.getChildren().get(0);
                FadeTransition out = new FadeTransition(Duration.millis(110), current);
                out.setFromValue(current.getOpacity());
                out.setToValue(0);
                out.setOnFinished(ev -> {
                    contentArea.getChildren().setAll(node);
                    AnimationUtils.pageIn(node);
                });
                out.play();
            } else {
                contentArea.getChildren().setAll(node);
                AnimationUtils.pageIn(node);
            }
        } catch (Exception e) {
            log.error("No se pudo cargar la vista: {}", view, e);
            NotificacionUtil.error(contentArea.getScene(), "No se pudo cargar la vista: " + view);
        }
    }

    private void loadAlertBadge() {
        DialogUtil.runAsync(
            () -> alertProductoService.getAgotados().size() + alertProductoService.getBajoStock().size(),
            total -> {
                if (alertBadge == null) return;
                if (total > 0) {
                    alertBadge.setText(total > 99 ? "99+" : String.valueOf(total));
                    boolean wasHidden = !alertBadge.isVisible();
                    alertBadge.setVisible(true);
                    alertBadge.setManaged(true);
                    if (wasHidden) {
                        ScaleTransition pop = new ScaleTransition(Duration.millis(320), alertBadge);
                        pop.setFromX(0.3); pop.setFromY(0.3);
                        pop.setToX(1.0);   pop.setToY(1.0);
                        pop.setInterpolator(Interpolator.EASE_OUT);
                        pop.play();
                    }
                } else {
                    alertBadge.setVisible(false);
                    alertBadge.setManaged(false);
                }
            },
            e -> { /* silently ignore — badge is decorative */ }
        );
    }

    private void updateStatusBar() {
        if (statusUserLabel != null && SessionManager.getCurrentUser() != null)
            statusUserLabel.setText(SessionManager.getCurrentUser().getNombre() +
                "  ·  " + SessionManager.getCurrentUser().getRol().getEtiqueta());
        if (statusDbLabel != null) {
            boolean demo = DatabaseConfig.isDemoMode();
            statusDbLabel.setText(demo ? "⬤  Modo demo" : "⬤  Base de datos conectada");
            statusDbLabel.getStyleClass().removeAll("status-dot-ok", "status-dot-demo");
            statusDbLabel.getStyleClass().add(demo ? "status-dot-demo" : "status-dot-ok");
        }
        updateStatusTime();
    }

    private void updateStatusTime() {
        if (statusTimeLabel != null)
            statusTimeLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void setupKeyboardShortcuts(javafx.scene.Scene scene) {
        var a = scene.getAccelerators();
        a.put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN), () -> onDashboard());
        a.put(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN), () -> onOrganigrama());
        a.put(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN), () -> onProductos());
        a.put(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN), () -> onCategorias());
        a.put(new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN), () -> onMovimientos());
        a.put(new KeyCodeCombination(KeyCode.DIGIT6, KeyCombination.CONTROL_DOWN), () -> onAlertas());
        a.put(new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.CONTROL_DOWN), () -> onReportes());
        a.put(new KeyCodeCombination(KeyCode.DIGIT8, KeyCombination.CONTROL_DOWN), () -> onConfiguracion());
        a.put(new KeyCodeCombination(KeyCode.F5),                                   () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.F1), () -> showShortcutHelp());
    }

    private void refreshCurrentView() {
        // Re-navigate to the current active view to trigger a refresh
        if (activeButton != null) activeButton.fire();
    }

    @FXML
    private void onShowShortcuts() { showShortcutHelp(); }

    private void showShortcutHelp() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(540);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("⌨", "Atajos de Teclado",
            "Referencia rápida de todos los atajos disponibles en SIBIM",
            "#4338CA", "#3730A3");

        // Section builder helper
        java.util.function.BiFunction<String, String[][], GridPane> makeSection = (title, rows) -> {
            GridPane g = new GridPane();
            g.setHgap(20); g.setVgap(5);
            g.setPadding(new Insets(6, 16, 10, 16));
            Label tit = new Label(title);
            tit.getStyleClass().add("nav-section-label");
            tit.setPadding(new Insets(0, 0, 4, 0));
            g.add(tit, 0, 0, 2, 1);
            for (int i = 0; i < rows.length; i++) {
                Label key = new Label(rows[i][0]);
                key.getStyleClass().add("shortcut-key");
                Label desc = new Label(rows[i][1]);
                desc.getStyleClass().add("shortcut-desc");
                g.add(key, 0, i + 1);
                g.add(desc, 1, i + 1);
            }
            return g;
        };

        GridPane navGrid = makeSection.apply("NAVEGACIÓN", new String[][]{
            {"Ctrl + 1",  "Dashboard"},
            {"Ctrl + 2",  "Organigrama"},
            {"Ctrl + 3",  "Bienes / Inventario"},
            {"Ctrl + 4",  "Categorías"},
            {"Ctrl + 5",  "Movimientos"},
            {"Ctrl + 6",  "Alertas"},
            {"Ctrl + 7",  "Reportes"},
            {"Ctrl + 8",  "Configuración"},
        });

        GridPane accGrid = makeSection.apply("ACCIONES EN TABLA", new String[][]{
            {"Ctrl + N",  "Nuevo registro (Bienes / Movimientos / Categorías)"},
            {"Ctrl + E",  "Editar fila seleccionada (Bienes / Categorías)"},
            {"Supr",      "Eliminar fila seleccionada"},
            {"Doble clic","Ver detalle del registro"},
            {"F5",        "Actualizar datos de la vista actual"},
        });

        GridPane busqGrid = makeSection.apply("BÚSQUEDA Y FILTROS", new String[][]{
            {"Escribir",  "Búsqueda en tiempo real (con debounce 280 ms)"},
            {"✕ (botón)", "Limpiar campo de búsqueda"},
            {"Hoy / Semana / Mes", "Presets de rango de fechas en Movimientos y Reportes"},
        });

        GridPane sysGrid = makeSection.apply("SISTEMA", new String[][]{
            {"F1",        "Mostrar esta ayuda de atajos"},
            {"F5",        "Actualizar vista actual"},
        });

        VBox content = new VBox(0, header, navGrid, new Separator(),
            accGrid, new Separator(), busqGrid,
            new Separator(), sysGrid);

        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    /** Called from child controllers (e.g. Alertas → Movimientos). */
    public void navigateTo(String view) {
        Button btn = switch (view) {
            case "dashboard"     -> btnDashboard;
            case "organigrama"   -> btnOrganigrama;
            case "productos"     -> btnProductos;
            case "categorias"    -> btnCategorias;
            case "movimientos"   -> btnMovimientos;
            case "alertas"       -> btnAlertas;
            case "reportes"      -> btnReportes;
            case "configuracion" -> btnConfiguracion;
            default              -> btnDashboard;
        };
        navigateTo(view, btn);
    }
}
