package com.sibim.controller;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/** One row of the Reportes screen: what the report is, whether it follows the
 *  period chosen above, and the formats it comes in. Every report is described
 *  once in {@link ReportesController} and drawn here, so they all look alike. */
final class ReporteFila {

    private ReporteFila() {}

    /** A way to get the report: a file format, or a variant of the document. */
    record Formato(String texto, String icono, String claseIcono, Runnable accion) {}

    static Formato pdf(Runnable accion)   { return new Formato("PDF",   "mdi2f-file-pdf-box",           "report-export-icon-pdf",   accion); }
    static Formato excel(Runnable accion) { return new Formato("Excel", "mdi2f-file-excel-outline",     "report-export-icon-excel", accion); }
    static Formato csv(Runnable accion)   { return new Formato("CSV",   "mdi2f-file-delimited-outline", "report-export-icon-csv",   accion); }

    /**
     * @param color       suffix of a {@code report-icon-badge-*} class
     * @param periodo     text of the "período" chip, or null when the report always covers everything
     * @param textoBoton  label of the menu when there are several formats
     */
    static HBox crear(String icono, String color, String titulo, String descripcion,
                      ObservableValue<String> periodo, String textoBoton, List<Formato> formatos) {
        FontIcon ic = new FontIcon(icono);
        ic.getStyleClass().add("report-icon");
        StackPane insignia = new StackPane(ic);
        insignia.getStyleClass().addAll("report-icon-badge", "report-icon-badge-" + color);

        Label tituloLbl = new Label(titulo);
        tituloLbl.getStyleClass().add("report-title");
        HBox tituloFila = new HBox(8, tituloLbl);
        tituloFila.setAlignment(Pos.CENTER_LEFT);
        if (periodo != null) {
            Label chip = new Label();
            chip.textProperty().bind(periodo);
            chip.getStyleClass().add("dash-section-badge");
            chip.setGraphic(new FontIcon("mdi2c-calendar-month"));
            chip.setMinWidth(Region.USE_PREF_SIZE);
            chip.setTooltip(new Tooltip("Este reporte solo incluye lo registrado en el período elegido arriba"));
            tituloFila.getChildren().add(chip);
        }
        Label desc = new Label(descripcion);
        desc.getStyleClass().add("report-desc");
        desc.setWrapText(true);
        VBox texto = new VBox(3, tituloFila, desc);
        HBox.setHgrow(texto, Priority.ALWAYS);

        HBox fila = new HBox(14, insignia, texto, boton(titulo, textoBoton, formatos));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("report-row");
        return fila;
    }

    private static ButtonBase boton(String titulo, String textoBoton, List<Formato> formatos) {
        ButtonBase b;
        if (formatos.size() == 1) {
            Formato f = formatos.get(0);
            Button unico = new Button(f.texto(), icono(f));
            unico.setOnAction(e -> f.accion().run());
            unico.setTooltip(new Tooltip("Generar " + titulo + " en " + f.texto()));
            b = unico;
        } else {
            FontIcon descarga = new FontIcon("mdi2d-download-outline");
            descarga.getStyleClass().add("btn-icon");
            MenuButton menu = new MenuButton(textoBoton, descarga);
            for (Formato f : formatos) {
                MenuItem item = new MenuItem(f.texto(), icono(f));
                item.setOnAction(e -> f.accion().run());
                menu.getItems().add(item);
            }
            b = menu;
        }
        b.getStyleClass().add("btn-secondary");
        b.setContentDisplay(ContentDisplay.LEFT);
        b.setGraphicTextGap(6);
        // Fixed width: the buttons line up in a column and never get cut to "…".
        b.setMinWidth(118);
        b.setPrefWidth(118);
        return b;
    }

    private static FontIcon icono(Formato f) {
        FontIcon ic = new FontIcon(f.icono());
        ic.getStyleClass().add(f.claseIcono());
        return ic;
    }
}
