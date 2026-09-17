package com.sibim.controller;

import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Backup creation and restore logic — extracted from ConfiguracionController. */
class ConfiguracionBackupManager {

    private final com.sibim.service.BackupService backupService;
    private final VBox backupSection;

    ConfiguracionBackupManager(com.sibim.service.BackupService backupService, VBox backupSection) {
        this.backupService = backupService;
        this.backupSection = backupSection;
    }

    void onGenerarRespaldo() {
        if (com.sibim.db.DatabaseConfig.isOfflineMode() || com.sibim.db.DatabaseConfig.isDemoMode()) {
            NotificacionUtil.advertencia(scene(),
                "La copia de seguridad solo está disponible con la base de datos principal conectada.");
            return;
        }

        if (!ConfirmacionUtil.confirmar("Información sensible en el respaldo",
                "El archivo generado contendrá todos los datos del sistema: bienes, movimientos, "
                + "usuarios y auditoría.\n\nSe cifrará con la contraseña que definas a continuación — "
                + "sin ella, el respaldo no podrá restaurarse. ¿Continuar?"))
            return;

        Optional<char[]> passwordOpt = promptBackupPassword(true);
        if (passwordOpt.isEmpty()) return;
        char[] password = passwordOpt.get();

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Guardar respaldo de la base de datos");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("sibim_backup_"
            + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) + ".json");
        java.io.File destino = chooser.showSaveDialog(com.sibim.MainApp.getPrimaryStage());
        if (destino == null) { java.util.Arrays.fill(password, '\0'); return; }

        DialogUtil.runAsyncWithProgress(scene(), "Generando respaldo…",
            () -> { backupService.backup(destino, password); return destino; },
            file -> { java.util.Arrays.fill(password, '\0');
                NotificacionUtil.exito(scene(), "Respaldo generado: " + file.getName()); },
            e -> { java.util.Arrays.fill(password, '\0');
                NotificacionUtil.error(scene(),
                    e instanceof java.sql.SQLException ? e.getMessage() : "No se pudo generar el respaldo"); }
        );
    }

    void onRestaurar() {
        if (com.sibim.db.DatabaseConfig.isOfflineMode() || com.sibim.db.DatabaseConfig.isDemoMode()) {
            NotificacionUtil.advertencia(scene(),
                "La restauración solo está disponible con la base de datos principal conectada.");
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Restaurar desde un archivo de respaldo");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File origen = chooser.showOpenDialog(com.sibim.MainApp.getPrimaryStage());
        if (origen == null) return;

        if (!ConfirmacionUtil.confirmar("Restaurar base de datos",
                "Esto reemplaza TODOS los datos actuales (bienes, movimientos, usuarios, categorías, "
                + "auditoría y conteos) con el contenido de \"" + origen.getName() + "\". "
                + "Esta acción no se puede deshacer. ¿Continuar?"))
            return;

        Optional<char[]> passwordOpt = promptBackupPassword(false);
        if (passwordOpt.isEmpty()) return;
        char[] password = passwordOpt.get();

        DialogUtil.runAsyncWithProgress(scene(), "Restaurando base de datos…",
            () -> { backupService.restore(origen, password); java.util.Arrays.fill(password, '\0'); return null; },
            v -> {
                NotificacionUtil.restauracionCompletada(scene(), origen.getName());
                javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                delay.setOnFinished(e -> {
                    com.sibim.session.SessionManager.logout();
                    try { com.sibim.MainApp.showLogin(); }
                    catch (Exception ex) { /* toast ya mostrado — usuario reiniciará manualmente */ }
                });
                delay.play();
            },
            e -> {
                java.util.Arrays.fill(password, '\0');
                NotificacionUtil.error(scene(),
                    e instanceof com.sibim.service.BackupEncryption.WrongPasswordException
                        ? "Contraseña incorrecta para este respaldo"
                        : e instanceof java.sql.SQLException ? e.getMessage() : "No se pudo restaurar el respaldo");
            }
        );
    }

    private javafx.scene.Scene scene() {
        return backupSection != null ? backupSection.getScene() : null;
    }

    private Optional<char[]> promptBackupPassword(boolean confirmar) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (com.sibim.MainApp.getPrimaryStage() != null) dialog.initOwner(com.sibim.MainApp.getPrimaryStage());
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.getDialogPane().setPrefWidth(440);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2l-lock-outline",
            confirmar ? "Cifrar respaldo" : "Descifrar respaldo",
            confirmar ? "Esta contraseña será necesaria para restaurar el respaldo"
                      : "Ingresa la contraseña usada al generar este respaldo",
            "#4F46E5", "#7C3AED");

        PasswordField fPass = new PasswordField();
        fPass.setPromptText(confirmar ? "Contraseña (mínimo 8 caracteres)" : "Contraseña del respaldo");
        fPass.getStyleClass().add("form-input");

        PasswordField fConfirmar = new PasswordField();
        fConfirmar.setPromptText("Confirmar contraseña");
        fConfirmar.getStyleClass().add("form-input");

        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("field-error-label");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);
        errorLbl.setWrapText(true);

        VBox form = new VBox(10, DialogUtil.fieldLabel("Contraseña *"), fPass);
        if (confirmar) form.getChildren().addAll(DialogUtil.fieldLabel("Confirmar contraseña *"), fConfirmar);
        form.getChildren().add(errorLbl);
        form.setPadding(new Insets(18, 22, 20, 22));
        dialog.getDialogPane().setContent(new VBox(header, form));

        ButtonType btnOk = new ButtonType(confirmar ? "Cifrar y guardar" : "Restaurar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(btnOk, ButtonType.CANCEL);
        Node okBtn = dialog.getDialogPane().lookupButton(btnOk);
        DialogUtil.styleButton(dialog.getDialogPane(), btnOk, "#4F46E5");

        Runnable hideError = () -> { errorLbl.setVisible(false); errorLbl.setManaged(false); };
        fPass.textProperty().addListener((o, a, b) -> hideError.run());
        fConfirmar.textProperty().addListener((o, a, b) -> hideError.run());

        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (confirmar && fPass.getText().length() < 8) {
                event.consume();
                errorLbl.setText("La contraseña debe tener al menos 8 caracteres");
                errorLbl.setVisible(true); errorLbl.setManaged(true);
                AnimationUtils.shake(fPass);
            } else if (confirmar && !fPass.getText().equals(fConfirmar.getText())) {
                event.consume();
                errorLbl.setText("Las contraseñas no coinciden");
                errorLbl.setVisible(true); errorLbl.setManaged(true);
                AnimationUtils.shake(fConfirmar);
            } else if (fPass.getText().isEmpty()) {
                event.consume();
                errorLbl.setText("La contraseña no puede estar vacía");
                errorLbl.setVisible(true); errorLbl.setManaged(true);
                AnimationUtils.shake(fPass);
            }
        });

        javafx.application.Platform.runLater(fPass::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        return (result.isPresent() && result.get() == btnOk)
            ? Optional.of(fPass.getText().toCharArray())
            : Optional.empty();
    }
}
