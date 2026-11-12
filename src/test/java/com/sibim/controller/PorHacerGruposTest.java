package com.sibim.controller;

import com.sibim.service.CalidadDatosService.PorHacer;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 12-nov-2026: «Por hacer» goes by kind of work and in the order it is best done. */
class PorHacerGruposTest {

    private static PorHacer p(String abrir) {
        return new PorHacer(1, abrir == null ? "días sin respaldo" : abrir, "Ir", "productos", abrir);
    }

    @Test
    void cadaTareaVaEnSuGrupo_yElFolioFiscalVaPrimero() {
        assertEquals(PorHacerPanel.Grupo.PRIMERO, PorHacerPanel.grupo(p(null)), "el respaldo atrasado");
        assertEquals(PorHacerPanel.Grupo.PRIMERO, PorHacerPanel.grupo(p("solicitudes")));
        assertEquals(PorHacerPanel.Grupo.RAPIDO, PorHacerPanel.grupo(p("proveedores")));
        assertEquals(PorHacerPanel.Grupo.CAPTURA, PorHacerPanel.grupo(p("completar:PRECIO")));
        assertEquals(PorHacerPanel.Grupo.CAPTURA, PorHacerPanel.grupo(p("formatos")));
        assertEquals(PorHacerPanel.Grupo.SEGUIMIENTO, PorHacerPanel.grupo(p("etiquetas")));

        List<String> rapidos = List.of(p("series"), p("unir"), p("factura"), p("proveedores"), p("folio")).stream()
            .sorted(Comparator.comparingInt(PorHacerPanel::orden)).map(PorHacer::abrir).toList();
        assertEquals(List.of("folio", "proveedores", "factura", "unir", "series"), rapidos);
        List<String> captura = List.of(p("completar:PRECIO"), p("completar:FOLIO_REVISAR"), p("formatos")).stream()
            .sorted(Comparator.comparingInt(PorHacerPanel::orden)).map(PorHacer::abrir).toList();
        assertEquals(List.of("completar:FOLIO_REVISAR", "formatos", "completar:PRECIO"), captura);
    }
}
