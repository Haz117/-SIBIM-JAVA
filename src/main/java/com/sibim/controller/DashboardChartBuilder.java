package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.AnimationUtils;
import com.sibim.util.FormatUtils;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

class DashboardChartBuilder {

    private final LineChart<String, Number>  chartMovimientos;
    private final VBox                       categoriaValorBox;
    private final VBox                       pieEmptyState;
    private final Consumer<String>           navigarA;

    static final String[] AREA_BAR_CLASSES = {
        "area-bar-pb-1", "area-bar-pb-2", "area-bar-pb-3", "area-bar-pb-4", "area-bar-pb-5"
    };

    DashboardChartBuilder(LineChart<String, Number> chartMovimientos,
                          VBox categoriaValorBox,
                          VBox pieEmptyState,
                          Consumer<String> navigarA) {
        this.chartMovimientos  = chartMovimientos;
        this.categoriaValorBox = categoriaValorBox;
        this.pieEmptyState     = pieEmptyState;
        this.navigarA          = navigarA;
    }

    void buildMovimientosChart(List<Movimiento> movimientos) {
        chartMovimientos.getData().clear();
        XYChart.Series<String, Number> entradas = new XYChart.Series<>(); entradas.setName("Entradas");
        XYChart.Series<String, Number> salidas  = new XYChart.Series<>(); salidas.setName("Salidas");

        LocalDate today = LocalDate.now();
        List<String> labels = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String raw = today.minusDays(i).getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.of("es")).replace(".", "");
            labels.add(raw.substring(0, 1).toUpperCase() + raw.substring(1));
        }

        Map<LocalDate, Integer> entMap = new HashMap<>(), salMap = new HashMap<>();
        for (Movimiento m : movimientos) {
            LocalDate d = m.getCreadoEn().toLocalDate();
            switch (m.getTipo()) {
                case ENTRADA -> entMap.merge(d, m.getCantidad(), Integer::sum);
                case SALIDA  -> salMap.merge(d, m.getCantidad(), Integer::sum);
                default -> {}
            }
        }
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String label = labels.get(6 - i);
            entradas.getData().add(new XYChart.Data<>(label, entMap.getOrDefault(day, 0)));
            salidas.getData().add(new XYChart.Data<>(label, salMap.getOrDefault(day, 0)));
        }
        chartMovimientos.getData().addAll(List.of(entradas, salidas));
        for (XYChart.Data<String, Number> d : entradas.getData())
            installTooltipWhenReady(d.nodeProperty(), "Entradas " + d.getXValue() + ": " + d.getYValue());
        for (XYChart.Data<String, Number> d : salidas.getData())
            installTooltipWhenReady(d.nodeProperty(), "Salidas " + d.getXValue() + ": " + d.getYValue());
    }

    void buildCategoriaChart(List<ProductoRepository.CategoriaValor> catValores) {
        if (categoriaValorBox == null) return;
        categoriaValorBox.getChildren().clear();

        BigDecimal total = catValores.stream()
            .map(ProductoRepository.CategoriaValor::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int i = 0;
        for (ProductoRepository.CategoriaValor cv : catValores) {
            double pct = total.compareTo(BigDecimal.ZERO) > 0
                ? cv.valor().doubleValue() / total.doubleValue() : 0;

            Label nameLbl = new Label(cv.nombre());
            nameLbl.getStyleClass().add("area-bar-name");
            javafx.scene.layout.HBox.setHgrow(nameLbl, javafx.scene.layout.Priority.ALWAYS);

            Label valLbl = new Label(FormatUtils.formatCurrency(cv.valor()));
            valLbl.getStyleClass().add("area-bar-count");

            javafx.scene.layout.HBox nameRow = new javafx.scene.layout.HBox(nameLbl, valLbl);
            nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().addAll("area-bar-pb", AREA_BAR_CLASSES[i % AREA_BAR_CLASSES.length]);

            VBox item = new VBox(5, nameRow, pb);
            item.setCursor(javafx.scene.Cursor.HAND);
            item.getStyleClass().add("stat-card-clickable");
            String catName = cv.nombre();
            item.setOnMouseClicked(e -> {
                com.sibim.session.NavigationContext.setPendingCategoryFilter(catName);
                navigarA.accept("Productos");
            });
            Tooltip.install(item, new Tooltip(catName + ": " + FormatUtils.formatCurrency(cv.valor())));
            categoriaValorBox.getChildren().add(item);

            double target = pct;
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
            });
            wait.play();
            i++;
        }

        boolean hasData = !catValores.isEmpty();
        categoriaValorBox.setVisible(hasData);
        categoriaValorBox.setManaged(hasData);
        if (pieEmptyState != null) {
            boolean wasVisible = pieEmptyState.isVisible();
            pieEmptyState.setVisible(!hasData);
            pieEmptyState.setManaged(!hasData);
            if (!hasData && !wasVisible) AnimationUtils.springIn(pieEmptyState);
        }
    }

    private static void installTooltipWhenReady(
            javafx.beans.value.ObservableValue<? extends javafx.scene.Node> nodeProp, String text) {
        javafx.scene.Node node = nodeProp.getValue();
        if (node != null) { Tooltip.install(node, new Tooltip(text)); return; }
        nodeProp.addListener(new javafx.beans.value.ChangeListener<javafx.scene.Node>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.Node> obs,
                                javafx.scene.Node old, javafx.scene.Node n) {
                if (n != null) { Tooltip.install(n, new Tooltip(text)); nodeProp.removeListener(this); }
            }
        });
    }
}
