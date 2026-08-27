package com.sibim.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FilterPreset(
    String name,
    String search,
    String categoriaId,
    String area,
    String resguardante,
    String estado
) {}
