package com.sibim.controller;

import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.EmailService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

class ConfigEmailSectionBuilder {

    private final VBox backupSection;
    private final ConfiguracionRepository configRepo;

    ConfigEmailSectionBuilder(VBox backupSection, ConfiguracionRepository configRepo) {
        this.backupSection = backupSection;
        this.configRepo    = configRepo;
    }

    void build(Map<String, String> cfg) {
        if (backupSection == null || !(backupSection.getParent() instanceof VBox rootVBox)) return;

        VBox emailCard = new VBox(12);
        emailCard.getStyleClass().add("card");
        emailCard.setPadding(new Insets(18));

        Label title = new Label("Notificaciones por Email");
        title.getStyleClass().add("card-section-title");
        FontIcon titleIcon = new FontIcon("mdi2e-email-outline");
        titleIcon.setIconSize(18);
        HBox titleRow = new HBox(8, titleIcon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox chkHabilitado = new CheckBox("Activar alertas por email");
        chkHabilitado.setSelected("true".equals(cfg.getOrDefault("alertas_email_habilitado", "false")));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label lSmtpHost = new Label("Servidor SMTP");
        TextField tfSmtpHost = new TextField(cfg.getOrDefault("smtp_host", ""));
        tfSmtpHost.setPromptText("smtp.gmail.com");
        GridPane.setHgrow(tfSmtpHost, Priority.ALWAYS);

        Label lSmtpPort = new Label("Puerto");
        TextField tfSmtpPort = new TextField(cfg.getOrDefault("smtp_port", "587"));
        tfSmtpPort.setPrefWidth(80);

        Label lSmtpUser = new Label("Usuario SMTP");
        TextField tfSmtpUser = new TextField(cfg.getOrDefault("smtp_usuario", ""));
        tfSmtpUser.setPromptText("tu@correo.com");
        GridPane.setHgrow(tfSmtpUser, Priority.ALWAYS);

        Label lSmtpPass = new Label("Contraseña SMTP");
        PasswordField tfSmtpPass = new PasswordField();
        tfSmtpPass.setText(cfg.getOrDefault("smtp_password", ""));
        GridPane.setHgrow(tfSmtpPass, Priority.ALWAYS);

        Label lDest = new Label("Correo destino");
        TextField tfDest = new TextField(cfg.getOrDefault("alertas_correo_destino", ""));
        tfDest.setPromptText("alertas@municipio.gob.mx");
        GridPane.setHgrow(tfDest, Priority.ALWAYS);

        grid.add(lSmtpHost, 0, 0); grid.add(tfSmtpHost, 1, 0);
        grid.add(lSmtpPort, 2, 0); grid.add(tfSmtpPort, 3, 0);
        grid.add(lSmtpUser, 0, 1); grid.add(tfSmtpUser, 1, 1);
        grid.add(lSmtpPass, 0, 2); grid.add(tfSmtpPass, 1, 2);
        grid.add(lDest,     0, 3); grid.add(tfDest,     1, 3);

        Button btnGuardarEmail = new Button("Guardar");
        btnGuardarEmail.getStyleClass().add("btn-primary");
        btnGuardarEmail.setGraphic(new FontIcon("mdi2c-content-save-outline"));
        btnGuardarEmail.setContentDisplay(ContentDisplay.LEFT);

        Button btnProbarSMTP = new Button("Probar conexión");
        btnProbarSMTP.getStyleClass().add("btn-secondary");
        btnProbarSMTP.setGraphic(new FontIcon("mdi2e-email-send-outline"));
        btnProbarSMTP.setContentDisplay(ContentDisplay.LEFT);

        Label lblSmtpResult = new Label();
        lblSmtpResult.getStyleClass().add("muted-sm");

        HBox btnsRow = new HBox(10, btnGuardarEmail, btnProbarSMTP, lblSmtpResult);
        btnsRow.setAlignment(Pos.CENTER_LEFT);

        btnGuardarEmail.setOnAction(ev -> {
            javafx.scene.Scene scene = emailCard.getScene();
            DialogUtil.runAsync(() -> {
                configRepo.set("alertas_email_habilitado", chkHabilitado.isSelected() ? "true" : "false");
                configRepo.set("smtp_host",              tfSmtpHost.getText().strip());
                configRepo.set("smtp_port",              tfSmtpPort.getText().strip());
                configRepo.set("smtp_usuario",           tfSmtpUser.getText().strip());
                configRepo.set("smtp_password",          tfSmtpPass.getText());
                configRepo.set("alertas_correo_destino", tfDest.getText().strip());
                new AuditLogRepository().log("configuracion", "email", "Correo y SMTP",
                    "actualizar", "Configuración de alertas por correo actualizada");
                return null;
            }, v -> NotificacionUtil.exito(scene, "Configuración de email guardada"),
               e -> NotificacionUtil.error(scene, "No se pudo guardar la configuración de email"));
        });

        btnProbarSMTP.setOnAction(ev -> {
            btnProbarSMTP.setDisable(true);
            lblSmtpResult.setText("Enviando…");
            AppExecutor.submit(() -> {
                String err = new EmailService(configRepo).probarConexion();
                javafx.application.Platform.runLater(() -> {
                    btnProbarSMTP.setDisable(false);
                    if (err == null) {
                        lblSmtpResult.setText("Correo enviado correctamente");
                        lblSmtpResult.getStyleClass().removeAll("field-hint-error");
                        lblSmtpResult.getStyleClass().add("field-hint-ok");
                    } else {
                        lblSmtpResult.setText("Error: " + err);
                        lblSmtpResult.getStyleClass().removeAll("field-hint-ok");
                        lblSmtpResult.getStyleClass().add("field-hint-error");
                    }
                });
            });
        });

        emailCard.getChildren().addAll(titleRow, chkHabilitado, grid, btnsRow);
        rootVBox.getChildren().add(emailCard);
        AnimationUtils.fadeInUp(emailCard, 320, 385);
    }
}
