package com.sibim;

import atlantafx.base.theme.PrimerLight;
import com.sibim.util.AnimationUtils;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Objects;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        primaryStage = stage;
        showSplash();
    }

    public static void showSplash() throws Exception {
        Parent root = FXMLLoader.load(
            Objects.requireNonNull(MainApp.class.getResource("/fxml/splash.fxml")));
        Scene scene = new Scene(root, 560, 390);
        scene.getStylesheets().add(
            Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm());
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
        Scene scene = new Scene(root, 960, 620);
        scene.getStylesheets().add(
            Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm());
        primaryStage.setTitle("SIBIM — Sistema Integral de Bienes Municipales");

        if (primaryStage.getScene() != null) {
            // Fade-out current scene, then switch
            AnimationUtils.fadeOut(primaryStage.getScene().getRoot(), 160, () -> {
                primaryStage.setWidth(960);
                primaryStage.setHeight(620);
                primaryStage.setScene(scene);
                primaryStage.setResizable(false);
                primaryStage.centerOnScreen();
                primaryStage.show();
            }).play();
        } else {
            primaryStage.setWidth(960);
            primaryStage.setHeight(620);
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();
            primaryStage.show();
        }
    }

    public static void showMain() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Objects.requireNonNull(MainApp.class.getResource("/fxml/main.fxml")));
        Parent root = loader.load();
        Scene newScene = new Scene(root, 1280, 800);
        newScene.getStylesheets().add(
            Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm());

        // Fade out login → fade in main
        AnimationUtils.fadeOut(primaryStage.getScene().getRoot(), 180, () -> {
            root.setOpacity(0);
            primaryStage.setScene(newScene);
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(680);
            primaryStage.centerOnScreen();

            FadeTransition in = new FadeTransition(javafx.util.Duration.millis(340), root);
            in.setFromValue(0); in.setToValue(1);
            in.setInterpolator(Interpolator.EASE_OUT);
            in.play();
        }).play();
    }

    public static Stage getPrimaryStage() { return primaryStage; }
}
