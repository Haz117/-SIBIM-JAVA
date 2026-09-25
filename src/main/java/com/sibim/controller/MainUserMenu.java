package com.sibim.controller;

import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

class MainUserMenu {

    private MainUserMenu() {}

    static void setup(VBox userInfoVBox, StackPane contentArea, Runnable onLogout,
                      Runnable onTutorial, Runnable onAbout) {
        if (userInfoVBox == null) return;
        javafx.scene.Node card = userInfoVBox.getParent();
        if (card == null) return;
        card.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(card, new Tooltip("Clic para opciones de cuenta"));

        ContextMenu menu = new ContextMenu();

        MenuItem miPassword = new MenuItem("Cambiar contraseña");
        miPassword.setGraphic(new FontIcon("mdi2l-lock-outline"));
        miPassword.setOnAction(e -> com.sibim.controller.dialogs.CambiarPasswordDialog.mostrar(contentArea.getScene()));

        MenuItem miTutorial = new MenuItem("Tutorial del sistema");
        miTutorial.setGraphic(new FontIcon("mdi2h-help-circle-outline"));
        miTutorial.setOnAction(e -> onTutorial.run());

        MenuItem miAbout = new MenuItem("Acerca de SIBIM");
        miAbout.setGraphic(new FontIcon("mdi2i-information-outline"));
        miAbout.setOnAction(e -> onAbout.run());

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem miLogout = new MenuItem("Cerrar sesión");
        miLogout.setGraphic(new FontIcon("mdi2l-logout"));
        miLogout.setOnAction(e -> onLogout.run());

        menu.getItems().addAll(miPassword, miTutorial, miAbout, sep, miLogout);
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY)
                menu.show(card, e.getScreenX(), e.getScreenY());
        });
    }
}
