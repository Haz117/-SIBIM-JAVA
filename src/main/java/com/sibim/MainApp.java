package com.sibim;

import atlantafx.base.theme.PrimerLight;
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
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;

public class MainApp extends Application {

    private static final String STYLESHEET =
        Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm();

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
            primaryStage.centerOnScreen();
        });
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
        Scene finalScene = new Scene(newRoot, targetWidth, targetHeight);
        finalScene.getStylesheets().add(STYLESHEET);
        primaryStage.setTitle("SIBIM — Sistema Integral de Bienes Municipales");

        if (oldScene == null) {
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
            primaryStage.setScene(finalScene);
        });
        cross.play();
    }

    public static Stage getPrimaryStage() { return primaryStage; }
}
