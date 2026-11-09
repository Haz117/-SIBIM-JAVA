package com.sibim.controller;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.service.AvanceAreasService.Avance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The «Avance de las áreas» window (9-nov-2026): groups by dependencia, the orders and
 *  the state of each área in words. Without windows. */
class AvanceAreaViewTest {

    private AreaCatalog antes;

    @BeforeEach void catalogo() { antes = Areas.catalogo(); Areas.usar(AreaCatalog.PREDETERMINADO); }
    @AfterEach void regresar() { Areas.usar(antes); }

    private static Avance sinEntrar(String area, int bienes) { return new Avance(area, null, bienes, 0, 0, 0, 0, false); }

    @Test
    void dependencia_esSuSecretariaLaPresidenciaOLosAutonomos() {
        assertEquals("Secretaría General Municipal", AvanceAreaView.dependenciaDe("Archivo Municipal"));
        assertEquals("Secretaría General Municipal", AvanceAreaView.dependenciaDe("Secretaría General Municipal"));
        assertEquals(Areas.PRESIDENCIA, AvanceAreaView.dependenciaDe("Dirección Jurídica"));
        assertEquals(Areas.PRESIDENCIA, AvanceAreaView.dependenciaDe(Areas.PRESIDENCIA));
        assertEquals(AvanceAreaView.AUTONOMOS, AvanceAreaView.dependenciaDe("Contraloría Municipal"));
        assertEquals(AvanceAreaView.AUTONOMOS, AvanceAreaView.dependenciaDe("Un área que no está en el catálogo"));
    }

    @Test
    void agrupar_enElOrdenDelOrganigrama_conLaCabezaPrimeroYLosAutonomosAlFinal() {
        var grupos = AvanceAreaView.agrupar(List.of(
            sinEntrar("Contraloría Municipal", 3), sinEntrar("Dirección de Catastro", 5), sinEntrar("Archivo Municipal", 4),
            sinEntrar("Tesorería Municipal", 9), sinEntrar("Dirección Jurídica", 2), sinEntrar("Secretaría General Municipal", 1)));
        assertEquals(List.of(Areas.PRESIDENCIA, "Secretaría General Municipal", "Tesorería Municipal", AvanceAreaView.AUTONOMOS),
            List.copyOf(grupos.keySet()));
        assertEquals(List.of("Secretaría General Municipal", "Archivo Municipal"),
            grupos.get("Secretaría General Municipal").stream().map(Avance::area).toList());
        assertEquals(List.of("Tesorería Municipal", "Dirección de Catastro"),
            grupos.get("Tesorería Municipal").stream().map(Avance::area).toList(), "la tesorería encabeza su grupo");
    }

    @Test
    void ordenes_atrasadasYAvanzadas() {
        Avance nada = sinEntrar("A", 10);
        Avance revisando = new Avance("B", LocalDateTime.now(), 10, 3, 0, 0, 0, true);
        Avance etiquetando = new Avance("C", LocalDateTime.now(), 10, 10, 0, 0, 4, true);
        Avance termino = new Avance("D", LocalDateTime.now(), 10, 10, 0, 0, 10, true);
        List<Avance> todas = List.of(termino, revisando, nada, etiquetando);
        assertEquals(List.of("A", "B", "C", "D"),
            todas.stream().sorted(AvanceAreaView.Orden.ATRASADAS.comparador()).map(Avance::area).toList());
        assertEquals(List.of("D", "C", "B", "A"),
            todas.stream().sorted(AvanceAreaView.Orden.AVANZADAS.comparador()).map(Avance::area).toList());
        assertNull(AvanceAreaView.Orden.ORGANIGRAMA.comparador(), "por organigrama se quedan como vienen, en grupos");
        assertEquals(List.of("Ñandú", "Zacatecas"), List.of(sinEntrar("Zacatecas", 1), sinEntrar("Ñandú", 1)).stream()
            .sorted(AvanceAreaView.Orden.NOMBRE.comparador()).map(Avance::area).toList(), "sin acentos ni tildes");
    }

    @Test
    void estadoLargo_diceDondeVaYQueLeFalta() {
        assertEquals("Sin cuenta todavía", AvanceAreaView.estadoLargo(sinEntrar("A", 5)));
        assertEquals("Tiene cuenta · aún no entra", AvanceAreaView.estadoLargo(new Avance("A", null, 5, 0, 0, 0, 0, true)));
        assertEquals("Falta que entre", AvanceAreaView.estadoLargo(new Avance("A", null, 5, 0, 0, 0, 0)));
        assertEquals("Entró · falta revisar sus bienes",
            AvanceAreaView.estadoLargo(new Avance("A", LocalDateTime.now(), 5, 0, 0, 0, 0, true)));
        assertEquals("Revisando sus bienes · 2 de 5",
            AvanceAreaView.estadoLargo(new Avance("A", LocalDateTime.now(), 5, 2, 0, 0, 0, true)));
        assertEquals("Poniendo etiquetas · 1 de 5",
            AvanceAreaView.estadoLargo(new Avance("A", LocalDateTime.now(), 5, 5, 0, 0, 1, true)));
        assertEquals("Terminó los tres pasos",
            AvanceAreaView.estadoLargo(new Avance("A", LocalDateTime.now(), 5, 5, 0, 0, 5, true)));
        assertEquals("Entró · no tiene bienes",
            AvanceAreaView.estadoLargo(new Avance("A", LocalDateTime.now(), 0, 0, 0, 0, 0, true)));
    }

    @Test
    void filtro_porElPasoEnQueVa() {
        assertEquals(AvanceAreaView.Filtro.ENTRAR, AvanceAreaView.Filtro.de(sinEntrar("A", 1)));
        assertEquals(AvanceAreaView.Filtro.TERMINARON,
            AvanceAreaView.Filtro.de(new Avance("A", LocalDateTime.now(), 0, 0, 0, 0, 0, true)));
        assertTrue(AvanceAreaView.Filtro.TODAS.acepta(sinEntrar("A", 1)));
        assertFalse(AvanceAreaView.Filtro.REVISAR.acepta(sinEntrar("A", 1)));
    }
}
