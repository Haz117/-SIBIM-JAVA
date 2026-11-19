package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.AvanceAreasService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: what the Organigrama's cards and table say of each secretaría. */
class ResumenSecretariaTest {

    private static Producto bien(String id, String area, boolean completo) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo("PRU/" + id); p.setNombre("Bien " + id); p.setArea(area); p.setStockActual(1);
        if (completo) {
            p.setPrecioCompra(new BigDecimal("1500"));
            p.setFechaAdquisicion(LocalDate.of(2025, 3, 1));
            p.setVidaUtilAnios(5);
        }
        return p;
    }

    @Test
    void datosCompletos_sinResguardo_avance_yDependencias() {
        Producto a = bien("1", "Secretaría Ficticia", true), b = bien("2", "Dirección Ficticia", false),
            c = bien("3", "Dirección Ficticia", true), d = bien("4", "Unidad Ficticia", false);
        Producto deBaja = bien("5", "Dirección Ficticia", false);
        deBaja.setFechaBaja(LocalDate.of(2026, 10, 1));
        Map<String, List<Producto>> porArea = Map.of("Secretaría Ficticia", List.of(a),
            "Dirección Ficticia", List.of(b, c), "Unidad Ficticia", List.of(d));
        Map<String, AvanceAreasService.Avance> avance = Map.of(
            "Secretaría Ficticia", new AvanceAreasService.Avance("Secretaría Ficticia", null, 1, 0, 0, 1, 0, Boolean.FALSE),
            "Dirección Ficticia", new AvanceAreasService.Avance("Dirección Ficticia", LocalDateTime.now(), 2, 1, 0, 1, 0, Boolean.TRUE),
            "Unidad Ficticia", new AvanceAreasService.Avance("Unidad Ficticia", null, 1, 0, 0, 0, 0, Boolean.TRUE));

        ResumenSecretaria r = ResumenSecretaria.de("Secretaría Ficticia", List.of(a, b, c, d, deBaja),
            List.of("Dirección Ficticia", "Unidad Ficticia"), Set.of("1", "3"), avance, porArea);

        assertEquals(4, r.bienes(), "el de baja no cuenta");
        assertEquals(List.of(b, d), r.faltanDatos());
        assertEquals(50, r.pctCompletos());
        assertEquals(List.of(b, d), r.sinResguardo());
        assertEquals("1 de 3 entró · 25 % revisado", r.avanceEnPalabras());
        assertEquals("1 de 3 entró · 25 %", r.avanceCorto());
        assertEquals(List.of(Map.entry("Dirección Ficticia", 2), Map.entry("Unidad Ficticia", 1)), r.dependencias());
    }

    @Test
    void sinCuentas_loDiceEnPalabras() {
        Map<String, AvanceAreasService.Avance> avance = Map.of(
            "Secretaría Ficticia", new AvanceAreasService.Avance("Secretaría Ficticia", null, 3, 0, 0, 0, 0, Boolean.FALSE),
            "Dirección Ficticia", new AvanceAreasService.Avance("Dirección Ficticia", null, 2, 0, 0, 0, 0, Boolean.FALSE));
        ResumenSecretaria r = ResumenSecretaria.de("Secretaría Ficticia", List.of(), List.of("Dirección Ficticia"), null, avance, Map.of());
        assertEquals("Ninguna de sus 2 áreas tiene cuenta", r.avanceEnPalabras());
        assertEquals("0 de 2 con cuenta", r.avanceCorto());
        assertTrue(r.sinResguardo().isEmpty(), "sin saber los resguardos no se acusa a nadie");
    }

    @Test
    void elNombreCorto_paraLaTarjeta() {
        assertEquals("Catastro", OrganigramaController.corto("Dirección de Catastro"));
        assertEquals("Ingresos", OrganigramaController.corto("Tesorería — Ingresos"));
        assertEquals("Mujer", OrganigramaController.corto("Instancia Municipal de la Mujer"));
        assertEquals("Archivo Municipal", OrganigramaController.corto("Archivo Municipal"));
    }
}
