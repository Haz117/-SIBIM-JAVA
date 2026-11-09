package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.AvanceAreasService.Avance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The three phases of an área in the Organigrama: 1 sign in, 2 review its bienes, 3 labels. */
class AvanceAreasServiceTest {

    private static Producto bien(String area, boolean revisado, String nota, boolean etiquetado, boolean conDatos) {
        Producto p = new Producto();
        p.setId(java.util.UUID.randomUUID().toString());
        p.setArea(area);
        if (revisado) { p.setRevisadoAreaEn(LocalDateTime.now()); p.setRevisionAreaNota(nota); }
        p.setEtiquetado(etiquetado);
        if (conDatos) { p.setFotoUrl("f.jpg"); p.setUbicacion("Oficina"); p.setEstadoFisico("BUENO"); }
        return p;
    }

    @Test
    void cuentaCadaFase_porArea() {
        Producto deBaja = bien("Catastro", false, null, false, false);
        deBaja.setFechaBaja(LocalDate.now());
        List<Producto> bienes = List.of(
            bien("Catastro", true, null, true, true),
            bien("Catastro", true, "Lo tiene Obras", false, false),
            bien("Catastro", false, null, false, true),
            bien("Catastro", false, null, false, false),
            deBaja,
            bien("Contraloría Municipal", true, null, true, true));
        Map<String, Avance> avance = AvanceAreasService.calcular(bienes,
            Map.of("Contraloría Municipal", LocalDateTime.now()), List.of("Catastro", "Contraloría Municipal", "DIF"));

        Avance cat = avance.get("Catastro");
        assertEquals(4, cat.bienes(), "el de baja no cuenta");
        assertEquals(2, cat.revisados());
        assertEquals(1, cat.observados());
        assertEquals(2, cat.conDatos());
        assertEquals(1, cat.etiquetados());
        assertFalse(cat.fase1());
        assertEquals(1, cat.faseActual(), "no ha entrado: sigue en la fase 1 aunque ya revisó algunos");
        assertEquals(0.5, cat.avance(2), 1e-9);
        assertEquals(0.25, cat.avance(3), 1e-9);
        assertEquals("Paso 1 · sin entrar", cat.resumen());

        Avance con = avance.get("Contraloría Municipal");
        assertEquals(4, con.faseActual());
        assertEquals("Terminó", con.resumen());

        Avance dif = avance.get("DIF");
        assertEquals(0, dif.bienes(), "un área sin bienes también aparece");
        assertEquals(1.0, dif.avance(2), 1e-9);
    }

    @Test
    void faseActual_yResumen() {
        Map<String, Avance> avance = AvanceAreasService.calcular(List.of(
                bien("Catastro", true, null, false, true), bien("Catastro", false, null, false, true),
                bien("DIF", true, null, true, true), bien("DIF", true, null, false, true), bien("DIF", true, null, false, true)),
            Map.of("Catastro", LocalDateTime.now(), "DIF", LocalDateTime.now(), "Sin Bienes", LocalDateTime.now()),
            List.of("Catastro", "DIF", "Sin Bienes"));
        assertEquals(2, avance.get("Catastro").faseActual());
        assertEquals("Paso 2 · 50 %", avance.get("Catastro").resumen());
        assertEquals(3, avance.get("DIF").faseActual());
        assertEquals("Paso 3 · 33 %", avance.get("DIF").resumen());
        assertEquals("Sin bienes", avance.get("Sin Bienes").resumen());
    }

    @Test
    void conCuenta_seSabeQuienTieneCuentaYQuienNo() {
        Map<String, Avance> avance = AvanceAreasService.calcular(List.of(bien("Catastro", false, null, false, false)),
            Map.of("DIF", LocalDateTime.now()), java.util.Set.of("Catastro"), List.of("Catastro", "DIF", "Archivo"));
        Avance cat = avance.get("Catastro"), dif = avance.get("DIF"), arch = avance.get("Archivo");
        assertEquals(Boolean.TRUE, cat.conCuenta());
        assertFalse(cat.sinCuenta(), "tiene cuenta aunque no ha entrado");
        assertEquals("Paso 1 · sin entrar", cat.resumen());
        assertEquals(Boolean.TRUE, dif.conCuenta(), "si entró, tiene cuenta aunque no venga en la lista");
        assertFalse(dif.sinCuenta());
        assertEquals(Boolean.FALSE, arch.conCuenta());
        assertTrue(arch.sinCuenta());
        assertEquals("Paso 1 · sin cuenta", arch.resumen());
    }

    @Test
    void sinSaberLasCuentas_noDiceQueFalten() {
        Avance a = AvanceAreasService.calcular(List.of(), Map.of(), List.of("Catastro")).get("Catastro");
        assertNull(a.conCuenta(), "fuera de línea no se sabe");
        assertFalse(a.sinCuenta());
        assertEquals("Paso 1 · sin entrar", a.resumen());
    }
}
