package com.sibim.controller;

import com.sibim.repository.ProductoRepository;
import com.sibim.util.AnimationUtils;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;

class DashboardStatusSectionBuilder {

    private final HBox  statusCardsRow;
    private final VBox  areasCard;
    private final VBox  areasBarBox;
    private final HBox  areasSectionHdr;
    private final Runnable onVerProductos;
    private final Runnable onVerBajoStock;
    private final Runnable onVerAgotados;
    private final Runnable onVerAlertas;

    DashboardStatusSectionBuilder(HBox statusCardsRow,
                                   VBox areasCard, VBox areasBarBox, HBox areasSectionHdr,
                                   Runnable onVerProductos, Runnable onVerBajoStock,
                                   Runnable onVerAgotados, Runnable onVerAlertas) {
        this.statusCardsRow  = statusCardsRow;
        this.areasCard       = areasCard;
        this.areasBarBox     = areasBarBox;
        this.areasSectionHdr = areasSectionHdr;
        this.onVerProductos  = onVerProductos;
        this.onVerBajoStock  = onVerBajoStock;
        this.onVerAgotados   = onVerAgotados;
        this.onVerAlertas    = onVerAlertas;
    }

    void buildStatusCards(ProductoRepository.ProductoStats stats) {
        if (statusCardsRow == null) return;
        statusCardsRow.getChildren().clear();
        long total = stats.total();
        if (total == 0) { statusCardsRow.setVisible(false); statusCardsRow.setManaged(false); return; }

        record CardDef(String icon, String label, String colorKey, long count, Runnable onClick, String tooltip) {}
        List<CardDef> defs = List.of(
            new CardDef("mdi2c-check-circle-outline",  "Activos",    "green",  stats.activos(),   onVerProductos, "Ver todos los bienes activos del inventario"),
            new CardDef("mdi2a-alert-circle-outline",  "Bajo Stock", "amber",  stats.bajoStock(), onVerBajoStock, "Ver bienes por debajo de su stock mínimo"),
            new CardDef("mdi2a-alert-octagon-outline", "Agotados",   "red",    stats.agotados(),  onVerAgotados,  "Ver bienes con stock en cero — requieren reposición"),
            new CardDef("mdi2c-clock-alert-outline",   "Vencidos",   "violet", stats.vencidos(),  onVerAlertas,   "Ver garantías próximas a vencer o ya vencidas")
        );

        for (int i = 0; i < defs.size(); i++) {
            CardDef def = defs.get(i);
            int pct = (int) Math.round(def.count() * 100.0 / total);

            org.kordamp.ikonli.javafx.FontIcon ico = new org.kordamp.ikonli.javafx.FontIcon(def.icon());
            ico.getStyleClass().add("status-icon-" + def.colorKey());

            javafx.scene.control.Label lbl = new javafx.scene.control.Label(def.label());
            lbl.getStyleClass().add("status-mini-label");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            javafx.scene.control.Label pctLbl = new javafx.scene.control.Label(pct + "%");
            pctLbl.getStyleClass().addAll("status-mini-pct", "status-pct-" + def.colorKey());

            HBox topRow = new HBox(7, ico, lbl, pctLbl);
            topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.control.Label cntLbl = new javafx.scene.control.Label("0 bienes");
            cntLbl.getStyleClass().add("status-mini-count");

            javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().addAll("status-pb", "status-pb-" + def.colorKey());

            VBox card = new VBox(9, topRow, cntLbl, pb);
            card.getStyleClass().addAll("status-mini-card", "status-mini-card-" + def.colorKey());
            card.setPadding(new javafx.geometry.Insets(14, 16, 14, 16));
            HBox.setHgrow(card, Priority.ALWAYS);

            Runnable action = def.onClick();
            card.setOnMouseClicked(e -> action.run());
            card.getStyleClass().add("stat-card-clickable");
            Tooltip.install(card, new Tooltip(def.tooltip()));

            statusCardsRow.getChildren().add(card);

            double targetPct = (double) def.count() / total;
            long cardCount = def.count();
            int delay = i * 100;
            javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(delay + 300));
            wait.setOnFinished(ev -> {
                javafx.animation.Timeline anim = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(pb.progressProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(850),
                        new javafx.animation.KeyValue(pb.progressProperty(), targetPct,
                            javafx.animation.Interpolator.EASE_OUT))
                );
                anim.play();
                AnimationUtils.animateCount(cntLbl, cardCount, 750, v -> v + " bienes");
            });
            wait.play();
        }

        statusCardsRow.setVisible(true);
        statusCardsRow.setManaged(true);
        AnimationUtils.staggeredFadeInUp(statusCardsRow.getChildren(), 280, 50);
    }

    void buildAreasSection(LinkedHashMap<String, Long> byArea, long total) {
        if (areasCard == null || areasBarBox == null || byArea == null || byArea.isEmpty()) return;
        areasBarBox.getChildren().clear();

        int i = 0;
        for (java.util.Map.Entry<String, Long> entry : byArea.entrySet()) {
            double pct = total > 0 ? (double) entry.getValue() / total : 0;

            javafx.scene.control.Label nameLbl = new javafx.scene.control.Label(entry.getKey());
            nameLbl.getStyleClass().add("area-bar-name");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            javafx.scene.control.Label cntLbl = new javafx.scene.control.Label("0 bienes");
            cntLbl.getStyleClass().add("area-bar-count");

            HBox nameRow = new HBox(nameLbl, cntLbl);
            nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().addAll("area-bar-pb",
                DashboardChartBuilder.AREA_BAR_CLASSES[i % DashboardChartBuilder.AREA_BAR_CLASSES.length]);

            VBox item = new VBox(5, nameRow, pb);
            areasBarBox.getChildren().add(item);

            double target = pct;
            long count = entry.getValue();
            int delay = i * 90;
            javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(delay + 400));
            wait.setOnFinished(ev -> {
                javafx.animation.Timeline anim = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(pb.progressProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(900),
                        new javafx.animation.KeyValue(pb.progressProperty(), target,
                            javafx.animation.Interpolator.EASE_OUT))
                );
                anim.play();
                AnimationUtils.animateCount(cntLbl, count, 800, v -> v + " bienes");
            });
            wait.play();
            i++;
        }

        areasCard.setVisible(true);
        areasCard.setManaged(true);
        if (areasSectionHdr != null) { areasSectionHdr.setVisible(true); areasSectionHdr.setManaged(true); }
        AnimationUtils.fadeInUp(areasCard, 300, 0);
    }
}
