package com.sibim;

import atlantafx.base.theme.PrimerLight;
import com.sibim.util.AppExecutor;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    private static final String STYLESHEET =
        Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm();

    /** Remembers the main window's size/position/maximized state between
     *  sessions — it used to always reopen centered at the fixed default
     *  size even if the user had maximized it last time. Only ever read/
     *  written for the main screen (see the resizable+minWidth guard in
     *  saveMainWindowState) — splash and login stay fixed-size by design. */
    private static final Preferences WINDOW_PREFS = Preferences.userNodeForPackage(MainApp.class);

    private static Stage primaryStage;

    /** Multiple resolutions so Windows can pick the sharpest one for each
     *  context (taskbar, alt-tab, title bar) instead of one blurry upscale.
     *  Set once on the Stage — it persists across scene switches (splash →
     *  login → main), unlike getIcons() being unset if never called at all,
     *  which is why the window/taskbar showed the generic Java coffee-cup
     *  icon before. */
    private static void applyAppIcon(Stage stage) {
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                Objects.requireNonNull(MainApp.class.getResourceAsStream("/img/icon-" + size + ".png"))));
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        primaryStage = stage;
        applyAppIcon(primaryStage);
        showSplash();
    }

    public static void showSplash() throws Exception {
        Parent root = FXMLLoader.load(
            Objects.requireNonNull(MainApp.class.getResource("/fxml/splash.fxml")));
        Scene scene = new Scene(root, 560, 390);
        scene.getStylesheets().add(STYLESHEET);
        scene.setFill(Color.web("#0F172A"));
        primaryStage.setTitle("SIBIM — Sistema Integral de Bienes Municipales");
        primaryStage.setScene(scene);
        primaryStage.setWidth(560);
        primaryStage.setHeight(390);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void showLogin() throws Exception {
        Parent root = FXMLLoader.load(
            Objects.requireNonNull(MainApp.class.getResource("/fxml/login.fxml")));
        transitionTo(root, 960, 620, () -> {
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();
        });
    }

    public static void showMain() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Objects.requireNonNull(MainApp.class.getResource("/fxml/main.fxml")));
        Parent root = loader.load();
        transitionTo(root, 1280, 800, () -> {
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(680);
            restoreMainWindowState();
        });
    }

    /** Applies the size/position/maximized state saved by
     *  {@link #saveMainWindowState()} on the previous exit, if any and if
     *  it still fits a currently connected screen (a saved position from a
     *  second monitor that's since been unplugged would otherwise put the
     *  window off-screen with no way to reach it). Falls back to the
     *  centered default (already set as this Scene's width/height by
     *  transitionTo) when there's nothing usable saved. */
    private static void restoreMainWindowState() {
        double w = WINDOW_PREFS.getDouble("main.width", -1);
        double h = WINDOW_PREFS.getDouble("main.height", -1);
        double x = WINDOW_PREFS.getDouble("main.x", Double.NaN);
        double y = WINDOW_PREFS.getDouble("main.y", Double.NaN);
        boolean maximized = WINDOW_PREFS.getBoolean("main.maximized", false);

        boolean hasValidSize = w >= primaryStage.getMinWidth() && h >= primaryStage.getMinHeight();
        boolean hasValidPos = !Double.isNaN(x) && !Double.isNaN(y)
            && !Screen.getScreensForRectangle(x, y, hasValidSize ? w : 1, hasValidSize ? h : 1).isEmpty();

        if (hasValidSize) {
            primaryStage.setWidth(w);
            primaryStage.setHeight(h);
        }
        if (hasValidSize && hasValidPos) {
            primaryStage.setX(x);
            primaryStage.setY(y);
        } else {
            primaryStage.centerOnScreen();
        }
        if (maximized) primaryStage.setMaximized(true);
    }

    /** Called on exit — see {@link #stop()}. No-ops unless the main screen
     *  is actually what's showing (resizable + at least its own min size),
     *  so an exit from the splash or login screen never overwrites a
     *  previously saved main-window state with their own fixed dimensions. */
    private static void saveMainWindowState() {
        if (primaryStage == null || !primaryStage.isResizable()
                || primaryStage.getWidth() < primaryStage.getMinWidth()) return;
        boolean maximized = primaryStage.isMaximized();
        WINDOW_PREFS.putBoolean("main.maximized", maximized);
        // While maximized, width/height/x/y reflect the maximized bounds, not
        // the restored size the user would expect back if they un-maximize —
        // only persist real geometry when not maximized, so restoreMain-
        // WindowState() has a sane un-maximized size/position to fall back to.
        if (!maximized) {
            WINDOW_PREFS.putDouble("main.width", primaryStage.getWidth());
            WINDOW_PREFS.putDouble("main.height", primaryStage.getHeight());
            WINDOW_PREFS.putDouble("main.x", primaryStage.getX());
            WINDOW_PREFS.putDouble("main.y", primaryStage.getY());
        }
    }

    /**
     * Genuinely overlapping cross-fade between whole-window screens (splash →
     * login → main): a Stage can only show one Scene at a time, so a plain
     * fade-out-then-fade-in always has a beat where nothing/blank is on
     * screen. Instead this snapshots the OUTGOING scene into an image and
     * layers it directly on top of the new root in a temporary Scene, so
     * both are genuinely visible and blending at once — the old screen melts
     * away while the new one is already there underneath, settling into
     * place with a slight zoom. Once the animation finishes, the temporary
     * wrapper is discarded and the Stage gets the new content's own clean
     * Scene, so nothing extra lingers in the scene graph afterward.
     */
    private static void transitionTo(Parent newRoot, double targetWidth, double targetHeight, Runnable stageSetup) {
        Scene oldScene = primaryStage.getScene();
        primaryStage.setTitle("SIBIM — Sistema Integral de Bienes Municipales");

        if (oldScene == null) {
            Scene finalScene = new Scene(newRoot, targetWidth, targetHeight);
            finalScene.getStylesheets().add(STYLESHEET);
            primaryStage.setWidth(targetWidth);
            primaryStage.setHeight(targetHeight);
            primaryStage.setScene(finalScene);
            stageSetup.run();
            primaryStage.show();
            return;
        }

        WritableImage snapshot = oldScene.getRoot().snapshot(new SnapshotParameters(), null);
        ImageView outgoing = new ImageView(snapshot);
        outgoing.setMouseTransparent(true);

        StackPane transitionRoot = new StackPane(newRoot, outgoing);
        Scene transitionScene = new Scene(transitionRoot, targetWidth, targetHeight);
        transitionScene.getStylesheets().add(STYLESHEET);

        primaryStage.setWidth(targetWidth);
        primaryStage.setHeight(targetHeight);
        primaryStage.setScene(transitionScene);
        primaryStage.centerOnScreen();
        primaryStage.show();

        newRoot.setOpacity(0);
        newRoot.setScaleX(1.035); newRoot.setScaleY(1.035);

        int duration = 420;
        FadeTransition outgoingFade = new FadeTransition(Duration.millis(duration), outgoing);
        outgoingFade.setFromValue(1); outgoingFade.setToValue(0);
        outgoingFade.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition outgoingShrink = new ScaleTransition(Duration.millis(duration), outgoing);
        outgoingShrink.setToX(0.97); outgoingShrink.setToY(0.97);
        outgoingShrink.setInterpolator(Interpolator.EASE_IN);

        FadeTransition newFade = new FadeTransition(Duration.millis(duration), newRoot);
        newFade.setFromValue(0); newFade.setToValue(1);
        ScaleTransition newSettle = new ScaleTransition(Duration.millis(duration), newRoot);
        newSettle.setFromX(1.035); newSettle.setFromY(1.035);
        newSettle.setToX(1); newSettle.setToY(1);
        newSettle.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition cross = new ParallelTransition(outgoingFade, outgoingShrink, newFade, newSettle);
        cross.setOnFinished(e -> {
            stageSetup.run();
            // newRoot must be detached from the transition wrapper before it
            // can become a Scene's root again — a Node can't simultaneously
            // be a child of another Parent and the root of a Scene.
            transitionRoot.getChildren().remove(newRoot);
            Scene finalScene = new Scene(newRoot, targetWidth, targetHeight);
            finalScene.getStylesheets().add(STYLESHEET);
            primaryStage.setScene(finalScene);
        });
        cross.play();
    }

    @Override
    public void stop() {
        saveMainWindowState();
        AppExecutor.shutdown();
        try { com.sibim.db.offline.SyncService.stopWatching(); } catch (Exception e) { /* ignore on exit */ }
        try { com.sibim.db.DatabaseConfig.close(); } catch (Exception e) { /* ignore on exit */ }
    }

    public static Stage getPrimaryStage() { return primaryStage; }
}
