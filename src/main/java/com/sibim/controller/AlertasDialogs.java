package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

class AlertasDialogs {

    private AlertasDialogs() {}

    static void showProductoInfo(Producto p, boolean agotado) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        String color1 = agotado ? "#DC2626" : "#D97706";
        String color2 = agotado ? "#B91C1C" : "#B45309";
        String icon   = agotado ? "mdi2a-alert-octagon-outline" : "mdi2a-alert-circle-outline";
        String sub    = agotado ? "Stock agotado — requiere reposición inmediata"
                                : "Stock actual por debajo del mínimo establecido";

        HBox header = DialogUtil.gradientHeader(icon, p.getNombre(), sub, color1, color2);

        GridPane grid = DialogUtil.formGrid(100);
        int r = 0;
        grid.add(DialogUtil.fieldLabel("Código"), 0, r);
        Label codLbl = new Label(p.getCodigo());
        codLbl.getStyleClass().add("codigo-cell");
        grid.add(codLbl, 1, r++);
        grid.add(DialogUtil.fieldLabel("Área"),       0, r); grid.add(new Label(p.getArea() != null ? p.getArea() : "—"), 1, r++);

        Label stockLbl = new Label(String.valueOf(p.getStockActual()));
        stockLbl.getStyleClass().add(agotado ? "stock-low" : "stock-warn");
        grid.add(DialogUtil.fieldLabel("Stock actual"), 0, r); grid.add(stockLbl, 1, r++);

        if (p.getStockMinimo() > 0) {
            grid.add(DialogUtil.fieldLabel("Stock mínimo"), 0, r);
            grid.add(new Label(String.valueOf(p.getStockMinimo())), 1, r++);
        }

        VBox content = new VBox(0, header, grid);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 260, 70);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    static void showGarantiaInfo(Producto p) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        LocalDate fv = p.getFechaVencimiento();
        long dias = fv != null ? ChronoUnit.DAYS.between(LocalDate.now(), fv) : 0;
        String sub = fv == null ? "Sin fecha de vencimiento registrada"
            : dias < 0 ? "Garantía vencida"
            : dias == 0 ? "La garantía vence hoy"
            : "Vence en " + dias + (dias == 1 ? " día" : " días");

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", p.getNombre(), sub, "#2563EB", "#1D4ED8");

        GridPane grid = DialogUtil.formGrid(120);
        int r = 0;
        grid.add(DialogUtil.fieldLabel("Código"), 0, r);
        Label codLbl = new Label(p.getCodigo());
        codLbl.getStyleClass().add("codigo-cell");
        grid.add(codLbl, 1, r++);
        grid.add(DialogUtil.fieldLabel("Área"), 0, r); grid.add(new Label(p.getArea() != null ? p.getArea() : "—"), 1, r++);
        grid.add(DialogUtil.fieldLabel("Fecha de vencimiento"), 0, r);
        grid.add(new Label(fv != null ? FormatUtils.formatDate(fv) : "—"), 1, r++);
        Label diasLbl = new Label(fv == null ? "—" : dias < 0 ? "Vencido" : dias == 0 ? "Vence hoy" : dias + " días");
        diasLbl.getStyleClass().add(fv != null && dias <= 3 ? "days-critical" : "days-warn");
        grid.add(DialogUtil.fieldLabel("Días restantes"), 0, r); grid.add(diasLbl, 1, r);

        VBox content = new VBox(0, header, grid);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 260, 70);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }
}
