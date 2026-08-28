package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.model.FilterPreset;
import com.sibim.util.FilterPresetStore;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/** Manages filter preset chips in ProductosController.
 *  Package-private — only used by ProductosController. */
class FilterPresetPanel {

    private final FlowPane presetsBar;
    private final HBox presetsHeader;
    private final ComboBox<Categoria> categoriaFilter;
    private final TextField searchField;
    private final ComboBox<String> areaFilter;
    private final ComboBox<String> resguardanteFilter;
    private final ToggleGroup estadoChipGroup;
    private final Runnable onApply;
    private List<FilterPreset> presets = new ArrayList<>();

    FilterPresetPanel(FlowPane presetsBar, HBox presetsHeader,
                      ComboBox<Categoria> categoriaFilter, TextField searchField,
                      ComboBox<String> areaFilter, ComboBox<String> resguardanteFilter,
                      ToggleGroup estadoChipGroup, Runnable onApply) {
        this.presetsBar        = presetsBar;
        this.presetsHeader     = presetsHeader;
        this.categoriaFilter   = categoriaFilter;
        this.searchField       = searchField;
        this.areaFilter        = areaFilter;
        this.resguardanteFilter = resguardanteFilter;
        this.estadoChipGroup   = estadoChipGroup;
        this.onApply           = onApply;
    }

    void load() {
        presets = FilterPresetStore.load();
        refresh();
    }

    void refresh() {
        if (presetsBar == null) return;
        presetsBar.getChildren().clear();
        for (FilterPreset fp : presets)
            presetsBar.getChildren().add(buildChip(fp));
        boolean hasPresets = !presets.isEmpty();
        presetsBar.setVisible(hasPresets);
        presetsBar.setManaged(hasPresets);
        if (presetsHeader != null) {
            presetsHeader.setVisible(hasPresets);
            presetsHeader.setManaged(hasPresets);
        }
    }

    private HBox buildChip(FilterPreset fp) {
        Button label = new Button(fp.name());
        label.getStyleClass().add("preset-chip");
        label.setOnAction(e -> apply(fp));
        label.setTooltip(new Tooltip(buildTooltip(fp)));

        Button del = new Button();
        del.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2c-close"));
        del.getStyleClass().add("preset-chip-delete");
        del.setTooltip(new Tooltip("Eliminar acceso rápido"));
        del.setOnAction(e -> {
            presets.remove(fp);
            FilterPresetStore.save(presets);
            refresh();
        });

        HBox chip = new HBox(0, label, del);
        chip.getStyleClass().add("preset-chip-box");
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    private String buildTooltip(FilterPreset fp) {
        var sb = new StringBuilder("Aplicar filtros guardados:\n");
        if (fp.search() != null && !fp.search().isBlank())
            sb.append("  Búsqueda: ").append(fp.search()).append("\n");
        if (fp.categoriaId() != null) {
            String catName = categoriaFilter.getItems().stream()
                .filter(c -> c != null && c.getId().equals(fp.categoriaId()))
                .map(Categoria::getNombre).findFirst().orElse(fp.categoriaId());
            sb.append("  Categoría: ").append(catName).append("\n");
        }
        if (fp.area() != null)
            sb.append("  Área: ").append(fp.area()).append("\n");
        if (fp.resguardante() != null)
            sb.append("  Resguardante: ").append(fp.resguardante()).append("\n");
        if (fp.estado() != null && !"Todos".equals(fp.estado()))
            sb.append("  Estado: ").append(fp.estado()).append("\n");
        return sb.toString().stripTrailing();
    }

    void apply(FilterPreset fp) {
        searchField.setText(fp.search() != null ? fp.search() : "");
        if (fp.categoriaId() != null) {
            categoriaFilter.getItems().stream()
                .filter(c -> c != null && c.getId().equals(fp.categoriaId()))
                .findFirst().ifPresent(categoriaFilter::setValue);
        } else {
            categoriaFilter.setValue(null);
        }
        areaFilter.setValue(fp.area());
        if (resguardanteFilter != null) resguardanteFilter.setValue(fp.resguardante());
        if (estadoChipGroup != null && fp.estado() != null) {
            estadoChipGroup.getToggles().stream()
                .filter(t -> fp.estado().equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(estadoChipGroup::selectToggle);
        }
        onApply.run();
    }

    void saveCurrentAs(String name, javafx.scene.Scene scene) {
        String search = searchField.getText().trim();
        Categoria cat = categoriaFilter.getValue();
        String area   = areaFilter.getValue();
        String res    = resguardanteFilter != null ? resguardanteFilter.getValue() : null;
        String estado = estadoChipGroup != null
            ? ((ToggleButton) estadoChipGroup.getSelectedToggle()).getText() : "Todos";
        if (presets.size() >= 10 && presets.stream().noneMatch(p -> p.name().equalsIgnoreCase(name))) {
            NotificacionUtil.advertencia(scene, "Máximo 10 presets — elimina uno antes de guardar otro");
            return;
        }
        presets.removeIf(p -> p.name().equalsIgnoreCase(name));
        presets.add(new FilterPreset(name, search,
            cat != null ? cat.getId() : null, area, res, estado));
        FilterPresetStore.save(presets);
        refresh();
        NotificacionUtil.exito(scene, "Filtro \"" + name + "\" guardado");
    }
}
