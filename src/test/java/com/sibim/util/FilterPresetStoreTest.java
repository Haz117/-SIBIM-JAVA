package com.sibim.util;

import com.sibim.model.FilterPreset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilterPresetStoreTest {

    private String originalUserHome;
    private Path tempHome;

    @BeforeEach
    void setUp() throws IOException {
        originalUserHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("sibim-test-home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        System.setProperty("user.home", originalUserHome);
        Files.walk(tempHome)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> p.toFile().delete());
    }

    @Test
    void load_noFile_returnsEmptyList() {
        List<FilterPreset> result = FilterPresetStore.load();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void saveAndLoad_allFields_roundTrip() {
        FilterPreset preset = new FilterPreset("TI Activo", "laptop", "cat-ti", "Sala de cómputo", "Juan", "Activo");
        FilterPresetStore.save(List.of(preset));

        List<FilterPreset> loaded = FilterPresetStore.load();
        assertEquals(1, loaded.size());
        FilterPreset p = loaded.get(0);
        assertEquals("TI Activo", p.name());
        assertEquals("laptop", p.search());
        assertEquals("cat-ti", p.categoriaId());
        assertEquals("Sala de cómputo", p.area());
        assertEquals("Juan", p.resguardante());
        assertEquals("Activo", p.estado());
    }

    @Test
    void saveAndLoad_nullOptionalFields_roundTrip() {
        FilterPreset preset = new FilterPreset("Solo nombre", "", null, null, null, "Todos");
        FilterPresetStore.save(List.of(preset));

        List<FilterPreset> loaded = FilterPresetStore.load();
        assertEquals(1, loaded.size());
        FilterPreset p = loaded.get(0);
        assertEquals("Solo nombre", p.name());
        assertNull(p.categoriaId());
        assertNull(p.area());
        assertNull(p.resguardante());
    }

    @Test
    void load_corruptJson_returnsEmptyListWithoutThrowing() throws IOException {
        Path sibimDir = tempHome.resolve(".sibim");
        Files.createDirectories(sibimDir);
        Files.writeString(sibimDir.resolve("presets-bienes.json"), "{ not: valid json [[[");

        List<FilterPreset> result = assertDoesNotThrow(FilterPresetStore::load);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void save_multiplePresets_allRoundTripCorrectly() {
        List<FilterPreset> presets = List.of(
            new FilterPreset("Agotados",       "",           null,     null,               null, "Agotado"),
            new FilterPreset("TI completo",    "inf",        "cat-ti", "Sala de cómputo",  null, "Todos"),
            new FilterPreset("RH escritorios", "escritorio", null,     "Recursos Humanos", null, "Todos")
        );
        FilterPresetStore.save(presets);

        List<FilterPreset> loaded = FilterPresetStore.load();
        assertEquals(3, loaded.size());
        assertEquals("Agotados",       loaded.get(0).name());
        assertEquals("TI completo",    loaded.get(1).name());
        assertEquals("RH escritorios", loaded.get(2).name());
        assertEquals("cat-ti", loaded.get(1).categoriaId());
    }

    @Test
    void save_emptyList_overwritesPreviousPresets() {
        FilterPresetStore.save(List.of(new FilterPreset("Temp", "", null, null, null, "Todos")));
        FilterPresetStore.save(List.of());

        assertTrue(FilterPresetStore.load().isEmpty());
    }

    @Test
    void save_idempotent_overwritesPreviousSave() {
        FilterPresetStore.save(List.of(new FilterPreset("V1", "query1", null, null, null, "Todos")));
        FilterPresetStore.save(List.of(new FilterPreset("V2", "query2", null, null, null, "Activo")));

        List<FilterPreset> loaded = FilterPresetStore.load();
        assertEquals(1, loaded.size());
        assertEquals("V2", loaded.get(0).name());
        assertEquals("query2", loaded.get(0).search());
    }
}
