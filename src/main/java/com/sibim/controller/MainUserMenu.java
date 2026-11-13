package com.sibim.controller;

import com.sibim.util.AccessibilityUtils;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

class MainUserMenu {

    private MainUserMenu() {}

    static void setup(VBox userInfoVBox, StackPane contentArea, Runnable onLogout,
                      Runnable onTutorial, Runnable onAbout, Runnable onOperadorCambiado,
                      java.util.function.IntConsumer onTamanoTexto) {
        if (userInfoVBox == null) return;
        javafx.scene.Node card = userInfoVBox.getParent();
        if (card == null) return;
        card.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(card, new Tooltip("Clic para opciones de cuenta"));

        ContextMenu menu = new ContextMenu();

        MenuItem miPassword = new MenuItem("Cambiar contraseña");
        miPassword.setGraphic(new FontIcon("mdi2l-lock-outline"));
        miPassword.setOnAction(e -> com.sibim.controller.dialogs.CambiarPasswordDialog.mostrar(contentArea.getScene()));

        MenuItem miTutorial = new MenuItem("Guías y tutorial (F2)");
        miTutorial.setGraphic(new FontIcon("mdi2h-help-circle-outline"));
        miTutorial.setOnAction(e -> onTutorial.run());

        MenuItem miGuia = new MenuItem("Guía para imprimir (PDF)");
        miGuia.setGraphic(new FontIcon("mdi2b-book-open-page-variant-outline"));
        miGuia.setOnAction(e -> com.sibim.util.DialogUtil.runAsyncWithProgress(contentArea.getScene(),
            "Generando la guía rápida…",
            () -> new com.sibim.service.ReporteGuiaRapidaService().exportGuia(),
            f -> com.sibim.util.DialogUtil.showExportResultDialog(contentArea.getScene(), f),
            ex -> com.sibim.util.NotificacionUtil.error(contentArea.getScene(), "No se pudo generar la guía")));

        MenuItem miAbout = new MenuItem("Acerca de SIBIM");
        miAbout.setGraphic(new FontIcon("mdi2i-information-outline"));
        miAbout.setOnAction(e -> onAbout.run());

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem miLogout = new MenuItem("Cerrar sesión");
        miLogout.setGraphic(new FontIcon("mdi2l-logout"));
        miLogout.setOnAction(e -> onLogout.run());

        // A shared área account's password is Patrimonio's to change: one
        // person changing it would lock the rest of the área out.
        var yo = com.sibim.session.SessionManager.getCurrentUser();
        boolean compartida = yo != null
            && com.sibim.service.CuentasAreaService.CARGO_COMPARTIDA.equals(yo.getCargo());
        if (!compartida) menu.getItems().add(miPassword);
        else {
            // Asked at every sign-in; whoever sits down without signing out changes it here.
            MenuItem miOperador = new MenuItem("Cambiar quién usa la cuenta…");
            miOperador.setGraphic(new FontIcon("mdi2a-account-edit-outline"));
            miOperador.setOnAction(e -> OperadorDialog.preguntar(
                    com.sibim.session.SessionManager.getNombreCuenta(),
                    com.sibim.session.SessionManager.getOperador())
                .ifPresent(n -> {
                    com.sibim.session.SessionManager.setOperador(n);
                    com.sibim.session.OperadorRecordado.recordar(yo.getId(), n);
                    OperadorDialog.registrar(yo.getId(), n);
                    onOperadorCambiado.run();
                    com.sibim.util.NotificacionUtil.exito(contentArea.getScene(),
                        "Desde ahora los cambios quedan a nombre de " + n);
                }));
            menu.getItems().add(miOperador);
        }
        // The text size, where whoever sees badly looks for it first (the button of the
        // status bar is at the very bottom); Ctrl + «+» / «−» do the same.
        Menu tamano = new Menu("Tamaño del texto");
        tamano.setGraphic(new FontIcon("mdi2f-format-size"));
        ToggleGroup tamanos = new ToggleGroup();
        for (int i = 0; i < AccessibilityUtils.TEXT_SCALE_LABELS.length; i++) {
            int indice = i;
            RadioMenuItem r = new RadioMenuItem(AccessibilityUtils.TEXT_SCALE_LABELS[i]);
            r.setToggleGroup(tamanos);
            r.setOnAction(e -> onTamanoTexto.accept(indice));
            tamano.getItems().add(r);
        }
        menu.setOnShowing(e -> tamanos.selectToggle(tamanos.getToggles().get(AccessibilityUtils.getTextScaleIndex())));
        menu.getItems().addAll(miTutorial, miGuia, tamano, miAbout, sep, miLogout);
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY)
                menu.show(card, e.getScreenX(), e.getScreenY());
        });
        AccessibilityUtils.asButton(card, "Menú de usuario");
    }
}
