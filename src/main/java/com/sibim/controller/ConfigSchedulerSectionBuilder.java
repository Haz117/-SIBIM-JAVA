package com.sibim.controller;

import com.sibim.repository.ConfiguracionRepository;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

class ConfigSchedulerSectionBuilder {

    private final VBox backupSection;
    private final ConfiguracionRepository configRepo;

    ConfigSchedulerSectionBuilder(VBox backupSection, ConfiguracionRepository configRepo) {
        this.backupSection = backupSection;
        this.configRepo    = configRepo;
    }

    void build(Map<String, String> cfg) {
        if (backupSection == null || !(backupSection.getParent() instanceof VBox rootVBox)) return;

        VBox schedCard = new VBox(12);
        schedCard.getStyleClass().add("card");
        schedCard.setPadding(new Insets(18));

        Label title = new Label("Reportes Programados");
        title.getStyleClass().add("card-section-title");
        FontIcon titleIcon = new FontIcon("mdi2c-clock-outline");
        titleIcon.setIconSize(18);
        HBox titleRow = new HBox(8, titleIcon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox chkHabilitado = new CheckBox("Activar reportes programados");
        chkHabilitado.setSelected("true".equals(cfg.getOrDefault("reportes_habilitado", "false")));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label lFrec = new Label("Frecuencia");
        ComboBox<String> cbFrecuencia = new ComboBox<>();
        cbFrecuencia.getItems().addAll("DIARIO", "SEMANAL", "MENSUAL");
        cbFrecuencia.setValue(cfg.getOrDefault("reportes_frecuencia", "MENSUAL"));

        Label lCarpeta = new Label("Carpeta destino");
        TextField tfCarpeta = new TextField(cfg.getOrDefault("reportes_carpeta", ""));
        tfCarpeta.setPromptText("/ruta/a/carpeta");
        GridPane.setHgrow(tfCarpeta, Priority.ALWAYS);
        Button btnExaminar = new Button("Examinar…");
        btnExaminar.getStyleClass().add("btn-secondary");
        btnExaminar.setOnAction(ev -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Seleccionar carpeta para reportes");
            java.io.File dir = dc.showDialog(schedCard.getScene() != null ? schedCard.getScene().getWindow() : null);
            if (dir != null) tfCarpeta.setText(dir.getAbsolutePath());
        });
        HBox carpetaRow = new HBox(8, tfCarpeta, btnExaminar);
        carpetaRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfCarpeta, Priority.ALWAYS);

        Label lTipos = new Label("Tipos de reporte");
        String tiposGuardados = cfg.getOrDefault("reportes_tipos", "INVENTARIO");
        CheckBox chkInventario  = new CheckBox("Inventario");
        CheckBox chkMovimientos = new CheckBox("Movimientos");
        CheckBox chkAlertas     = new CheckBox("Alertas");
        chkInventario.setSelected(tiposGuardados.contains("INVENTARIO"));
        chkMovimientos.setSelected(tiposGuardados.contains("MOVIMIENTOS"));
        chkAlertas.setSelected(tiposGuardados.contains("ALERTAS"));
        HBox tiposRow = new HBox(14, chkInventario, chkMovimientos, chkAlertas);

        String ultimaEjec = cfg.getOrDefault("reportes_ultima_ejecucion", "");
        Label lblUltima = new Label("Último reporte generado: " + (ultimaEjec.isBlank() ? "Nunca" : ultimaEjec));
        lblUltima.getStyleClass().add("muted-sm");

        grid.add(lFrec,    0, 0); grid.add(cbFrecuencia, 1, 0);
        grid.add(lCarpeta, 0, 1); grid.add(carpetaRow,   1, 1);
        grid.add(lTipos,   0, 2); grid.add(tiposRow,     1, 2);

        Button btnGuardarSched = new Button("Guardar configuración");
        btnGuardarSched.getStyleClass().add("btn-primary");
        btnGuardarSched.setGraphic(new FontIcon("mdi2c-content-save-outline"));
        btnGuardarSched.setContentDisplay(ContentDisplay.LEFT);

        btnGuardarSched.setOnAction(ev -> {
            javafx.scene.Scene scene = schedCard.getScene();
            java.util.List<String> tipos = new java.util.ArrayList<>();
            if (chkInventario.isSelected())  tipos.add("INVENTARIO");
            if (chkMovimientos.isSelected()) tipos.add("MOVIMIENTOS");
            if (chkAlertas.isSelected())     tipos.add("ALERTAS");
            String tiposStr = String.join(",", tipos);
            DialogUtil.runAsync(() -> {
                configRepo.set("reportes_habilitado", chkHabilitado.isSelected() ? "true" : "false");
                configRepo.set("reportes_frecuencia", cbFrecuencia.getValue() != null ? cbFrecuencia.getValue() : "MENSUAL");
                configRepo.set("reportes_carpeta",    tfCarpeta.getText().strip());
                configRepo.set("reportes_tipos",      tiposStr.isBlank() ? "INVENTARIO" : tiposStr);
                return null;
            }, v -> NotificacionUtil.exito(scene, "Configuración de reportes guardada"),
               e -> NotificacionUtil.error(scene, "No se pudo guardar la configuración de reportes"));
        });

        schedCard.getChildren().addAll(titleRow, chkHabilitado, grid, lblUltima, btnGuardarSched);
        rootVBox.getChildren().add(schedCard);
        AnimationUtils.fadeInUp(schedCard, 320, 455);
    }
}
