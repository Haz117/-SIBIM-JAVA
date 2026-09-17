package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.model.enums.EstadoProducto;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductosChipsManager.refresh(), extracted from
 * ProductosController during the chips/filter refactor.
 */
class ProductosChipsManagerRefreshTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // toolkit already running
        }
    }

    private FlowPane activeChipsBar;
    private Button btnClearFilters;
    private Button btnGuardarPreset;
    private Label lblTotalAll;
    private Label emptyStateMsg;
    private Button btnEmptyLimpiar;
    private Label emptyStateHint;
    private ToggleGroup estadoChipGroup;
    private ToggleButton todosToggle;
    private DatePicker desdeRegFilter;
    private DatePicker hastaRegFilter;
    private TextField searchField;
    private ComboBox<Categoria> categoriaFilter;
    private ComboBox<String> areaFilter;
    private ComboBox<String> resguardanteFilter;

    private AtomicBoolean sinEtiquetar;
    private AtomicInteger applyFiltersCalls;
    private AtomicInteger sinEtiquetarCardCalls;

    private ProductosChipsManager manager;

    @BeforeEach
    void setUp() {
        activeChipsBar = new FlowPane();
        btnClearFilters = new Button();
        btnGuardarPreset = new Button();
        lblTotalAll = new Label();
        emptyStateMsg = new Label();
        btnEmptyLimpiar = new Button();
        emptyStateHint = new Label();

        estadoChipGroup = new ToggleGroup();
        todosToggle = new ToggleButton("Todos");
        todosToggle.setToggleGroup(estadoChipGroup);
        ToggleButton agotadoToggle = new ToggleButton("Agotado");
        agotadoToggle.setToggleGroup(estadoChipGroup);
        agotadoToggle.setSelected(true);

        desdeRegFilter = new DatePicker();
        hastaRegFilter = new DatePicker();
        searchField = new TextField("texto previo");
        categoriaFilter = new ComboBox<>();
        areaFilter = new ComboBox<>();
        areaFilter.setValue("Dirección");
        resguardanteFilter = new ComboBox<>();
        resguardanteFilter.setValue("Juan Pérez");

        sinEtiquetar = new AtomicBoolean(false);
        applyFiltersCalls = new AtomicInteger();
        sinEtiquetarCardCalls = new AtomicInteger();

        manager = new ProductosChipsManager(
            activeChipsBar, btnClearFilters, btnGuardarPreset, lblTotalAll,
            emptyStateMsg, btnEmptyLimpiar, emptyStateHint,
            () -> true, sinEtiquetar::get, () -> 10,
            estadoChipGroup, desdeRegFilter, hastaRegFilter, searchField,
            categoriaFilter, areaFilter, resguardanteFilter,
            applyFiltersCalls::incrementAndGet,
            sinEtiquetarCardCalls::incrementAndGet);
    }

    @Test
    void noFilters_hidesControlsAndShowsDefaultEmptyMessage() {
        manager.refresh("", null, null, null, null, null, null);

        assertFalse(btnClearFilters.isVisible());
        assertFalse(btnClearFilters.isManaged());
        assertFalse(btnGuardarPreset.isVisible());
        assertFalse(lblTotalAll.isVisible());
        assertEquals("No hay bienes registrados en el sistema", emptyStateMsg.getText());
        assertFalse(btnEmptyLimpiar.isVisible());
        assertTrue(emptyStateHint.isVisible()); // canEdit == true
        assertFalse(activeChipsBar.isVisible());
        assertTrue(activeChipsBar.getChildren().isEmpty());
    }

    @Test
    void busquedaFilter_showsControlsAndSearchChip() {
        manager.refresh("laptop", null, null, null, null, null, null);

        assertTrue(btnClearFilters.isVisible());
        assertTrue(btnGuardarPreset.isVisible());
        assertEquals("de 10 total", lblTotalAll.getText());
        assertTrue(lblTotalAll.isVisible());
        assertEquals("No se encontraron bienes (búsqueda «laptop»)", emptyStateMsg.getText());
        assertTrue(btnEmptyLimpiar.isVisible());
        assertFalse(emptyStateHint.isVisible());
        assertTrue(activeChipsBar.isVisible());
        assertEquals(1, activeChipsBar.getChildren().size());

        fireChipClose(activeChipsBar.getChildren().get(0));
        assertEquals("", searchField.getText());
        assertEquals(1, applyFiltersCalls.get());
    }

    @Test
    void categoriaFilter_addsChip_onlyWhenValuePresent() {
        Categoria cat = new Categoria();
        cat.setId("cat-ti");
        cat.setNombre("Tecnología");
        categoriaFilter.setValue(cat);

        manager.refresh("", "cat-ti", null, null, null, null, null);

        assertEquals(1, activeChipsBar.getChildren().size());
        assertTrue(emptyStateMsg.getText().contains("categoría «Tecnología»"));

        fireChipClose(activeChipsBar.getChildren().get(0));
        assertNull(categoriaFilter.getValue());
        assertEquals(1, applyFiltersCalls.get());
    }

    @Test
    void categoriaFilter_noChip_whenComboHasNoMatchingValue() {
        // catId set but categoriaFilter combo has no value selected
        manager.refresh("", "cat-ti", null, null, null, null, null);

        assertTrue(activeChipsBar.getChildren().isEmpty());
        assertFalse(activeChipsBar.isVisible());
    }

    @Test
    void areaAndResguardanteFilters_addChipsAndClearOnRemoval() {
        manager.refresh("", null, "Dirección", "Juan Pérez", null, null, null);

        assertEquals(2, activeChipsBar.getChildren().size());

        fireChipClose(activeChipsBar.getChildren().get(0));
        assertNull(areaFilter.getValue());

        fireChipClose(activeChipsBar.getChildren().get(1));
        assertNull(resguardanteFilter.getValue());
        assertEquals(2, applyFiltersCalls.get());
    }

    @Test
    void estadoFilter_chipRemoval_resetsToggleGroupToTodos() {
        manager.refresh("", null, null, null, EstadoProducto.AGOTADO, null, null);

        assertEquals(1, activeChipsBar.getChildren().size());
        assertFalse(todosToggle.isSelected());

        fireChipClose(activeChipsBar.getChildren().get(0));

        assertTrue(todosToggle.isSelected());
        assertEquals(1, applyFiltersCalls.get());
    }

    @Test
    void dateRangeFilters_addChipsWithFormattedDates_andClearOnRemoval() {
        LocalDate desde = LocalDate.of(2026, 1, 15);
        LocalDate hasta = LocalDate.of(2026, 3, 2);
        desdeRegFilter.setValue(desde);
        hastaRegFilter.setValue(hasta);

        manager.refresh("", null, null, null, null, desde, hasta);

        assertEquals(2, activeChipsBar.getChildren().size());
        assertTrue(emptyStateMsg.getText().contains("desde 15/01/26"));
        assertTrue(emptyStateMsg.getText().contains("hasta 02/03/26"));

        fireChipClose(activeChipsBar.getChildren().get(0));
        assertNull(desdeRegFilter.getValue());
        fireChipClose(activeChipsBar.getChildren().get(1));
        assertNull(hastaRegFilter.getValue());
    }

    @Test
    void sinEtiquetarFilter_addsChip_andRemovalRunsCardCallback_notApplyFilters() {
        sinEtiquetar.set(true);

        manager.refresh("", null, null, null, null, null, null);

        assertEquals(1, activeChipsBar.getChildren().size());
        assertTrue(emptyStateMsg.getText().contains("sin etiquetar"));

        fireChipClose(activeChipsBar.getChildren().get(0));
        assertEquals(1, sinEtiquetarCardCalls.get());
        assertEquals(0, applyFiltersCalls.get());
    }

    @Test
    void sinEtiquetar_countsAsHasFilters_evenWithoutOtherCriteria() {
        sinEtiquetar.set(true);

        manager.refresh("", null, null, null, null, null, null);

        assertTrue(btnClearFilters.isVisible());
        assertTrue(lblTotalAll.isVisible());
    }

    /** Simulates a click on the chip's close (X) button built by buildChip(). */
    private static void fireChipClose(Node chipNode) {
        javafx.scene.layout.HBox chip = (javafx.scene.layout.HBox) chipNode;
        Button close = (Button) chip.getChildren().get(1);
        close.fire();
    }
}
