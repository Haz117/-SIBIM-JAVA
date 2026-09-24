package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.util.AnimationUtils;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

class AlertasComodatosSection {

    private static final String PREF_KEY   = "alertas.comodatos.colapsado";
    private static final String BADGE_ID   = "comodatosCountBadge";
    private static final String TABLE_ID   = "tableComodatos";

    static VBox build(java.util.prefs.Preferences sticky, Supplier<javafx.scene.Node> okPlaceholder) {
        Label countBadge = new Label("0 comodatos");
        countBadge.getStyleClass().add("badge-count-inv");
        countBadge.setId(BADGE_ID);

        FontIcon chevron = new FontIcon("mdi2c-chevron-up");
        chevron.getStyleClass().add("alert-section-chevron");

        HBox header   = buildHeader(countBadge, chevron);
        TableView<Comodato> table = buildTable(okPlaceholder);

        VBox content = new VBox(10, table);
        content.setPadding(new javafx.geometry.Insets(14));
        content.setId("contentComodatos");

        boolean collapsed = sticky.getBoolean(PREF_KEY, false);
        applyCollapsed(content, chevron, collapsed);
        header.setCursor(javafx.scene.Cursor.HAND);
        header.setOnMouseClicked(e -> {
            boolean nowCollapsed = content.isVisible();
            applyCollapsed(content, chevron, nowCollapsed);
            sticky.putBoolean(PREF_KEY, nowCollapsed);
        });

        VBox section = new VBox(0, header, content);
        section.getStyleClass().addAll("alert-section", "alert-warning");
        section.setVisible(false);
        section.setManaged(false);
        return section;
    }

    @SuppressWarnings("unchecked")
    static void update(VBox section, List<Comodato> comodatos) {
        if (section == null) return;
        boolean hasVencidos = !comodatos.isEmpty();
        section.setVisible(hasVencidos);
        section.setManaged(hasVencidos);
        if (!hasVencidos) return;

        section.lookupAll("#" + BADGE_ID).forEach(n -> {
            if (n instanceof Label lbl)
                lbl.setText(comodatos.size() + " comodato" + (comodatos.size() == 1 ? "" : "s"));
        });
        section.lookupAll("#" + TABLE_ID).forEach(n -> {
            if (n instanceof TableView<?> tv)
                ((TableView<Comodato>) tv).getItems().setAll(comodatos);
        });
        AnimationUtils.statCardPop(section);
    }

    private static HBox buildHeader(Label countBadge, FontIcon chevron) {
        FontIcon headerIcon = new FontIcon("mdi2h-handshake-outline");
        headerIcon.getStyleClass().add("alert-section-icon");
        Label titleLbl = new Label("Comodatos Vencidos");
        titleLbl.getStyleClass().add("alert-section-title-inv");
        Label subtitleLbl = new Label("Comodatos cuya fecha límite ha sido superada sin devolución");
        subtitleLbl.getStyleClass().add("alert-section-subtitle");
        VBox titleBox = new VBox(1, titleLbl, subtitleLbl);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        HBox header = new HBox(12, headerIcon, titleBox, countBadge, chevron);
        header.getStyleClass().add("alert-header-warning");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return header;
    }

    private static TableView<Comodato> buildTable(Supplier<javafx.scene.Node> okPlaceholder) {
        TableView<Comodato> table = new TableView<>();
        table.setPrefHeight(185);
        table.getStyleClass().add("data-table");
        table.setTableMenuButtonVisible(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setId(TABLE_ID);
        table.setPlaceholder(okPlaceholder.get());

        TableColumn<Comodato, String> colNombre = new TableColumn<>("Bien");
        colNombre.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getProductoNombre()));
        colNombre.setPrefWidth(220);

        TableColumn<Comodato, String> colEntidad = new TableColumn<>("Entidad Receptora");
        colEntidad.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getEntidadReceptora()));
        colEntidad.setPrefWidth(200);

        TableColumn<Comodato, String> colFechaFin = new TableColumn<>("Fecha Fin");
        colFechaFin.setCellValueFactory(c -> {
            java.time.LocalDate ff = c.getValue().getFechaFin();
            return new javafx.beans.property.SimpleStringProperty(
                ff != null ? ff.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—");
        });
        colFechaFin.setPrefWidth(120);
        colFechaFin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("stock-low");
                if (empty || v == null) { setText(null); return; }
                setText(v);
                getStyleClass().add("stock-low");
            }
        });

        table.getColumns().addAll(colNombre, colEntidad, colFechaFin);
        return table;
    }

    static void applyCollapsed(VBox content, FontIcon chevron, boolean collapsed) {
        content.setVisible(!collapsed);
        content.setManaged(!collapsed);
        chevron.setIconLiteral(collapsed ? "mdi2c-chevron-down" : "mdi2c-chevron-up");
    }
}
