package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: at 1366 px the Bienes table read «Equipo de D…» and «Equipo de C…». */
class CategoriaCortaTest {

    private static final List<String> DEL_INVENTARIO = List.of(
        "Activos Biológicos", "Aparatos Deportivos", "Cámaras Fotográficas y de Video",
        "Equipo de Cómputo y de Tecnologías de la Información", "Equipo de Comunicación y Telecomunicación",
        "Equipo de Defensa y Seguridad", "Equipo Médico y de Laboratorio", "Equipos y Aparatos Audiovisuales",
        "Herramientas y Máquinas-Herramienta", "Maquinaria y Equipo de Construcción", "Maquinaria y Equipo Industrial",
        "Muebles de Oficina y Estantería", "Otro Mobiliario y Equipo Educacional y Recreativo", "Otros Equipos",
        "Otros Equipos de Transporte", "Otros Mobiliarios y Equipos de Administración",
        "Sistemas de Aire Acondicionado, Calefacción y Refrigeración", "Vehículos y Equipo Terrestre");

    @Test
    void lasDelInventario_cortasYSinRepetirse() {
        Set<String> vistos = new HashSet<>();
        for (String n : DEL_INVENTARIO) {
            String c = CategoriaCorta.de(n);
            assertTrue(c.length() <= 14, n + " → " + c);
            assertTrue(vistos.add(c), "repetido: " + c);
        }
        assertEquals("Cómputo", CategoriaCorta.de("Equipo de Cómputo y de Tecnologías de la Información"));
        assertEquals("Defensa", CategoriaCorta.de("Equipo de Defensa y Seguridad"));
        assertEquals("Comunicación", CategoriaCorta.de("  equipo de comunicacion y telecomunicacion "), "sin acentos ni mayúsculas, igual");
    }

    @Test
    void unaNueva_corta_oComoEstaSiYaLoEs() {
        assertEquals("Mobiliario", CategoriaCorta.de("Mobiliario"));
        assertEquals("Bienes Artísticos", CategoriaCorta.de("Bienes Artísticos"));
        assertEquals("Instrumental de precisión", CategoriaCorta.de("Equipo de Instrumental de precisión y medición"));
        assertEquals("", CategoriaCorta.de(null));
    }
}
