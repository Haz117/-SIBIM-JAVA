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

    /** 24-nov-2026: «Completar por grupos» is captured with the bien in view; «Tomar el color» is
     *  proposed by SIBIM and stays among the 4 in view. */
    @Test
    void losGruposSeCapturan_yElColorDelNombreSeVe() {
        assertEquals(PorHacerPanel.Grupo.CAPTURA, PorHacerPanel.grupo(p("grupos")));
        assertEquals(PorHacerPanel.Grupo.RAPIDO, PorHacerPanel.grupo(p("colores")));
        List<String> captura = List.of(p("completar:PRECIO"), p("completar:FOLIO_REVISAR"), p("grupos")).stream()
            .sorted(Comparator.comparingInt(PorHacerPanel::orden)).map(PorHacer::abrir).toList();
        assertEquals(List.of("grupos", "completar:FOLIO_REVISAR", "completar:PRECIO"), captura);
        List<String> rapidos = List.of(p("factura"), p("tipo"), p("mismonombre"), p("proveedores"), p("colores"), p("folio")).stream()
            .sorted(Comparator.comparingInt(PorHacerPanel::orden)).map(PorHacer::abrir).toList();
        assertEquals(List.of("folio", "colores", "proveedores", "mismonombre", "factura", "tipo"), rapidos);
    }

    /** 19-nov-2026: «Se arreglan en un momento» had 9 tasks; 4 show and «Ver 5 más», but one more
     *  than 4 shows whole (a link for one task is no saving), and a kind left open shows all. */
    @Test
    void cuatroALaVista_yVerLasDemas() {
        assertEquals(4, PorHacerPanel.aLaVista(9, false));
        assertEquals(9, PorHacerPanel.aLaVista(9, true));
        assertEquals(5, PorHacerPanel.aLaVista(5, false));
        assertEquals(3, PorHacerPanel.aLaVista(3, false));
    }
}
