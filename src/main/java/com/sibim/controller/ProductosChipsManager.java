package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.model.enums.EstadoProducto;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

class ProductosChipsManager {

    private final FlowPane activeChipsBar;
    private final Button btnClearFilters;
    private final Button btnGuardarPreset;
    private final Label lblTotalAll;
    private final Label emptyStateMsg;
    private final Button btnEmptyLimpiar;
    private final Label emptyStateHint;
    private final BooleanSupplier canEdit;
    private final BooleanSupplier filterSinEtiquetar;
    private final IntSupplier totalFiltered;
    private final ToggleGroup estadoChipGroup;
    private final DatePicker desdeRegFilter;
    private final DatePicker hastaRegFilter;
    private final TextField searchField;
    private final ComboBox<Categoria> categoriaFilter;
    private final ComboBox<String> areaFilter;
    private final ComboBox<String> resguardanteFilter;
    private final Runnable applyFilters;
    private final Runnable onCardSinEtiquetar;

    ProductosChipsManager(
            FlowPane activeChipsBar,
            Button btnClearFilters,
            Button btnGuardarPreset,
            Label lblTotalAll,
            Label emptyStateMsg,
            Button btnEmptyLimpiar,
            Label emptyStateHint,
            BooleanSupplier canEdit,
            BooleanSupplier filterSinEtiquetar,
            IntSupplier totalFiltered,
            ToggleGroup estadoChipGroup,
            DatePicker desdeRegFilter,
            DatePicker hastaRegFilter,
            TextField searchField,
            ComboBox<Categoria> categoriaFilter,
            ComboBox<String> areaFilter,
            ComboBox<String> resguardanteFilter,
            Runnable applyFilters,
            Runnable onCardSinEtiquetar) {
        this.activeChipsBar = activeChipsBar;
        this.btnClearFilters = btnClearFilters;
        this.btnGuardarPreset = btnGuardarPreset;
        this.lblTotalAll = lblTotalAll;
        this.emptyStateMsg = emptyStateMsg;
        this.btnEmptyLimpiar = btnEmptyLimpiar;
        this.emptyStateHint = emptyStateHint;
        this.canEdit = canEdit;
        this.filterSinEtiquetar = filterSinEtiquetar;
        this.totalFiltered = totalFiltered;
        this.estadoChipGroup = estadoChipGroup;
        this.desdeRegFilter = desdeRegFilter;
        this.hastaRegFilter = hastaRegFilter;
        this.searchField = searchField;
        this.categoriaFilter = categoriaFilter;
        this.areaFilter = areaFilter;
        this.resguardanteFilter = resguardanteFilter;
        this.applyFilters = applyFilters;
        this.onCardSinEtiquetar = onCardSinEtiquetar;
    }

    void refresh(String busqueda, String catId, String area,
                 String resguardante, EstadoProducto estado,
                 LocalDate desdeReg, LocalDate hastaReg) {
        boolean sinEtiquetar = filterSinEtiquetar.getAsBoolean();
        boolean hasFilters = !busqueda.isBlank() || catId != null || area != null
                || resguardante != null || estado != null
                || desdeReg != null || hastaReg != null || sinEtiquetar;
        btnClearFilters.setVisible(hasFilters);
        btnClearFilters.setManaged(hasFilters);
        if (btnGuardarPreset != null) {
            btnGuardarPreset.setVisible(hasFilters);
            btnGuardarPreset.setManaged(hasFilters);
        }
        if (hasFilters) {
            lblTotalAll.setText("de " + totalFiltered.getAsInt() + " total");
            lblTotalAll.setVisible(true);
            lblTotalAll.setManaged(true);
        } else {
            lblTotalAll.setVisible(false);
            lblTotalAll.setManaged(false);
        }
        if (emptyStateMsg != null) {
            if (hasFilters) {
                List<String> activeFilters = new ArrayList<>();
                if (!busqueda.isBlank()) activeFilters.add("búsqueda «" + busqueda + "»");
                if (catId != null && categoriaFilter.getValue() != null)
                    activeFilters.add("categoría «" + categoriaFilter.getValue().getNombre() + "»");
                if (area != null) activeFilters.add("área «" + area + "»");
                if (resguardante != null) activeFilters.add("resguardante «" + resguardante + "»");
                if (estado != null) activeFilters.add("estado «" + estado.getEtiqueta() + "»");
                if (desdeReg != null) activeFilters.add("desde " + desdeReg.format(DateTimeFormatter.ofPattern("dd/MM/yy")));
                if (hastaReg != null) activeFilters.add("hasta " + hastaReg.format(DateTimeFormatter.ofPattern("dd/MM/yy")));
                if (sinEtiquetar) activeFilters.add("sin etiquetar");
                String filterDesc = activeFilters.isEmpty() ? "" : " (" + String.join(", ", activeFilters) + ")";
                emptyStateMsg.setText("No se encontraron bienes" + filterDesc);
            } else {
                emptyStateMsg.setText("No hay bienes registrados en el sistema");
            }
        }
        if (btnEmptyLimpiar != null) {
            btnEmptyLimpiar.setVisible(hasFilters);
            btnEmptyLimpiar.setManaged(hasFilters);
        }
        if (emptyStateHint != null) {
            emptyStateHint.setVisible(!hasFilters && canEdit.getAsBoolean());
            emptyStateHint.setManaged(!hasFilters && canEdit.getAsBoolean());
        }
        refreshChips(busqueda, catId, area, resguardante, estado, desdeReg, hastaReg, sinEtiquetar);
    }

    private void refreshChips(String busqueda, String catId, String area,
                               String resguardante, EstadoProducto estado,
                               LocalDate desdeReg, LocalDate hastaReg, boolean sinEtiquetar) {
        if (activeChipsBar == null) return;
        activeChipsBar.getChildren().clear();
        List<Node> chips = new ArrayList<>();

        if (!busqueda.isBlank())
            chips.add(buildChip("Búsqueda: " + busqueda, () -> { searchField.clear(); applyFilters.run(); }));
        if (catId != null && categoriaFilter.getValue() != null)
            chips.add(buildChip("Categoría: " + categoriaFilter.getValue().getNombre(),
                () -> { categoriaFilter.setValue(null); applyFilters.run(); }));
        if (area != null)
            chips.add(buildChip("Área: " + area, () -> { areaFilter.setValue(null); applyFilters.run(); }));
        if (resguardante != null)
            chips.add(buildChip("Resguardante: " + resguardante, () -> { resguardanteFilter.setValue(null); applyFilters.run(); }));
        if (estado != null)
            chips.add(buildChip("Estado: " + estado.getEtiqueta(), () -> {
                if (estadoChipGroup != null)
                    estadoChipGroup.getToggles().stream()
                        .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                        .findFirst().ifPresent(t -> t.setSelected(true));
                applyFilters.run();
            }));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");
        if (desdeReg != null)
            chips.add(buildChip("Desde: " + desdeReg.format(fmt),
                () -> { if (desdeRegFilter != null) desdeRegFilter.setValue(null); applyFilters.run(); }));
        if (hastaReg != null)
            chips.add(buildChip("Hasta: " + hastaReg.format(fmt),
                () -> { if (hastaRegFilter != null) hastaRegFilter.setValue(null); applyFilters.run(); }));
        if (sinEtiquetar)
            chips.add(buildChip("Sin etiquetar", onCardSinEtiquetar));

        activeChipsBar.getChildren().addAll(chips);
        boolean show = !chips.isEmpty();
        activeChipsBar.setVisible(show);
        activeChipsBar.setManaged(show);
    }

    private Node buildChip(String label, Runnable onRemove) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("active-chip-label");
        Button close = new Button();
        close.setGraphic(new FontIcon("mdi2c-close"));
        close.getStyleClass().add("active-chip-close");
        close.setOnAction(e -> onRemove.run());
        close.setTooltip(new Tooltip("Quitar este filtro"));
        HBox chip = new HBox(4, lbl, close);
        chip.getStyleClass().add("active-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    static EstadoProducto parseEstado(String etiqueta) {
        if (etiqueta == null || "Todos".equalsIgnoreCase(etiqueta)) return null;
        for (EstadoProducto e : EstadoProducto.values()) {
            if (e.getEtiqueta().equalsIgnoreCase(etiqueta)) return e;
        }
        return null;
    }
}
