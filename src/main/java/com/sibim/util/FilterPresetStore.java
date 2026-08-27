package com.sibim.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibim.model.FilterPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FilterPresetStore {

    private static final Logger log = LoggerFactory.getLogger(FilterPresetStore.class);
    private static final String FILE_NAME = "presets-bienes.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FilterPresetStore() {}

    public static List<FilterPreset> load() {
        File f = presetFile();
        if (!f.exists()) return new ArrayList<>();
        try {
            return MAPPER.readValue(f, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("No se pudieron cargar los presets de filtros", e);
            return new ArrayList<>();
        }
    }

    public static void save(List<FilterPreset> presets) {
        try {
            File f = presetFile();
            f.getParentFile().mkdirs();
            MAPPER.writeValue(f, presets);
        } catch (IOException e) {
            log.warn("No se pudieron guardar los presets de filtros", e);
        }
    }

    private static File presetFile() {
        return Path.of(System.getProperty("user.home"), ".sibim", FILE_NAME).toFile();
    }
}
