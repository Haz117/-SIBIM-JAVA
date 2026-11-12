package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.Dato;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The text data Completar datos captures since 1.0.26. */
class CompletarDatosTextoTest {

    private static Producto conClave(String clave) {
        Producto p = new Producto();
        p.setClaveArmonizada(clave);
        return p;
    }

    @Test
    void todoLoQueNoEsVehiculoSugiereMueble() {
        assertEquals("MUEBLE", CalidadDatosService.tipoSugerido(conClave("1.2.4.1.1.511")));
        assertEquals("MUEBLE", CalidadDatosService.tipoSugerido(conClave("1.2.4.1.3.515")));
        assertEquals("MUEBLE", CalidadDatosService.tipoSugerido(conClave("1.2.4.5.1.551")));
        assertNull(CalidadDatosService.tipoSugerido(conClave("1.2.4.4.1.541")), "un vehículo lleva su propio tipo");
        assertNull(CalidadDatosService.tipoSugerido(conClave(null)));
    }

    @Test
    void elTextoSeGuardaEnMayusculasYSinEspaciosDeMas() {
        assertEquals("RAUL FICTICIO LOPEZ", CalidadDatosService.normalizarTexto(Dato.PROVEEDOR, "  raul   ficticio lopez "));
        assertEquals("7EA05B42-4508-4C36", CalidadDatosService.normalizarTexto(Dato.FOLIO, "7ea05b42-4508-4c36"));
        assertNull(CalidadDatosService.normalizarTexto(Dato.COLOR, "   "));
    }

    @Test
    void losDatosNuevosNoCambianLaCuentaDeLoQueFaltaParaLosTotales() {
        Producto p = new Producto();
        p.setPrecioCompra(new java.math.BigDecimal("10"));
        p.setFechaAdquisicion(java.time.LocalDate.of(2020, 1, 1));
        p.setVidaUtilAnios(5);
        p.setFotoUrl("x.jpg");
        assertEquals(0, CalidadDatosService.faltantes(p), "sin proveedor ni color sigue completo para los totales");
        assertTrue(CalidadDatosService.falta(p, Dato.PROVEEDOR));
        CalidadDatosService.poner(p, Dato.PROVEEDOR, "HERCOM");
        assertFalse(CalidadDatosService.falta(p, Dato.PROVEEDOR));
    }

    @Test
    void elRespaldoAtrasadoSeCuentaEnDias_ySinAnotarNoAvisa() {
        java.time.LocalDate hoy = java.time.LocalDate.of(2026, 11, 14);
        assertEquals(3, CalidadDatosService.diasSinRespaldo("2026-11-11T11:00:04|DESKTOP-X|1.03", hoy));
        assertEquals(0, CalidadDatosService.diasSinRespaldo(null, hoy));
        assertEquals(0, CalidadDatosService.diasSinRespaldo("basura", hoy));
        var conRespaldoViejo = CalidadDatosService.porHacer(java.util.List.of(), java.util.List.of(), java.util.Set.of(),
            java.util.Map.of(CalidadDatosService.CLAVE_ULTIMO_RESPALDO, "2020-01-01T11:00:00|PC|1"));
        assertTrue(conRespaldoViejo.get(0).titulo().startsWith("días sin respaldo"), "va primero");
    }

    @Test
    void elFinDeSemanaNoCuentaComoRespaldoPerdido() {
        // Friday 6-nov-2026 → Monday 9-nov: three days, but only one working day.
        String viernes = "2026-11-06T11:00:00|PC|1.0";
        assertEquals(3, CalidadDatosService.diasSinRespaldo(viernes, java.time.LocalDate.of(2026, 11, 9)));
        assertEquals(1, CalidadDatosService.diasHabilesSinRespaldo(viernes, java.time.LocalDate.of(2026, 11, 9)));
        // Wednesday 11-nov → Monday 16-nov: Thursday, Friday and Monday without one.
        assertEquals(3, CalidadDatosService.diasHabilesSinRespaldo("2026-11-11T11:00:00|PC|1.0", java.time.LocalDate.of(2026, 11, 16)));
    }

    @Test
    void lasComputadorasAtrasadasSeComparanConLaVersionMasNuevaEnUso() {
        var t = java.time.LocalDateTime.of(2026, 11, 10, 9, 0);
        var lista = java.util.List.of(new AccesosEquipoService.Atrasado("LAPTOP", "1.0.0", t),
            new AccesosEquipoService.Atrasado("PC-1", "1.0.25", t), new AccesosEquipoService.Atrasado("PC-2", "1.0.20", t));
        var atrasados = AccesosEquipoService.atrasados("1.0.23", lista);
        assertEquals(java.util.Set.of("LAPTOP", "PC-2"), atrasados.stream().map(AccesosEquipoService.Atrasado::equipo)
            .collect(java.util.stream.Collectors.toSet()), "la 1.0.25 de otra PC es la referencia");
    }
}
