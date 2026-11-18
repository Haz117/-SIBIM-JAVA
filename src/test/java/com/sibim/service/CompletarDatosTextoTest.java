package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.Dato;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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

    /** The folios of 12-nov-2026 that weren't a UUID, and what SIBIM says of each. */
    @Test
    void unFolioFiscalMalEscritoDiceQueTieneMal() {
        assertNull(CalidadDatosService.problemaFolio("E2117159-9609-11EE-81C1-00155D012007"));
        assertNull(CalidadDatosService.problemaFolio("B9919A80CF6F11EDAA71AD3327DCEBEF"), "sin guiones también es UUID");
        assertNull(CalidadDatosService.problemaFolio("  "));
        assertEquals("lleva la letra O donde va un cero; tiene 31 de los 32 caracteres: al último grupo le falta uno",
            CalidadDatosService.problemaFolio("AAA1EC97-00B3-4CO7-B2E2-E1A11491D3E"));
        assertEquals("tiene 31 de los 32 caracteres: al primer grupo le falta uno",
            CalidadDatosService.problemaFolio("444b81a-666e-4d4d-a4bd-d60bff0b3de3"));
        assertEquals("tiene 28 de los 32 caracteres: al primer grupo le faltan 4",
            CalidadDatosService.problemaFolio("ED65-589E-4CA0-B469-D7B9C7E69226"));
        // 17-nov-2026: the commonest, the last group a character short; and one too many in the middle.
        assertEquals("tiene 33 de los 32 caracteres: al cuarto grupo le sobra uno",
            CalidadDatosService.problemaFolio("E2117159-9609-11EE-81C1A-00155D012007"));
        assertEquals("tiene 4 grupos separados por guiones y van 5 (8-4-4-4-12)",
            CalidadDatosService.problemaFolio("E2117159-960911EE-81C1-00155D012007"));
        assertEquals("E2117159-9609-11EE-81C1-00155D012007", CalidadDatosService.escribirFolio("e2117159-96o9-11ee-81c1-0Ol55d012007"),
            "la O es cero y la I o la L son uno");
        assertEquals("E2117159-9609-11EE-81C1-00155D012007", CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.FOLIO,
            " e2117159-9609-11ee-81c1 -00155d0120o7 "));
        assertTrue(CalidadDatosService.problemaFolio("01329").startsWith("parece solo el final del folio fiscal"));
        assertTrue(CalidadDatosService.problemaFolio("B 5630").startsWith("parece el número de la factura"), "con espacio no es parte de un UUID");
        assertTrue(CalidadDatosService.problemaFolio("E2117159-9609-11EE-81C1-00155D01200G").contains("«G»"));

        Producto p = new Producto();
        p.setFolioFiscal("01329");
        assertTrue(CalidadDatosService.falta(p, Dato.FOLIO_REVISAR));
        assertFalse(CalidadDatosService.falta(p, Dato.FOLIO), "tiene folio: no sale en «Sin folio fiscal»");
        CalidadDatosService.poner(p, Dato.FOLIO_REVISAR, "E2117159-9609-11EE-81C1-00155D012007");
        assertFalse(CalidadDatosService.falta(p, Dato.FOLIO_REVISAR));
    }

    /** 17-nov-2026: the 14 short folios Patrimonio corrected by hand were the end of their UUID,
     *  whole in another bien of the same purchase; that one completes them, and nothing else. */
    @Test
    void soloElFinalDelFolioSeCompletaConElDeSuCompra() {
        assertEquals("01329", CalidadDatosService.finalDeFolio(" O1329 "), "una O es un cero");
        assertEquals("AAA4B", CalidadDatosService.finalDeFolio("aaa4b"));
        assertNull(CalidadDatosService.finalDeFolio("B 5630"));
        assertNull(CalidadDatosService.finalDeFolio("A1B2C3D4-0000-4000-8000-000000000001"), "ya está entero");
        assertNull(CalidadDatosService.finalDeFolio("AB1"), "muy corto para decir algo");

        LocalDate fecha = LocalDate.of(2026, 7, 23);
        Producto noBreak = compra("1", "NOB/23", "1636b", "PROVEEDOR FICTICIO SA", fecha, "2291.55");
        Producto suHermano = compra("2", "NOB/44", "F0000000-0000-4000-8000-00000001636B", "Proveedor Ficticio, S.A.", fecha, "2291.55");
        // A purchase by the same proveedor, another day and costo: the same end is not enough.
        Producto escritorio = compra("3", "ESC/16", "02059", "PROVEEDOR FICTICIO SA", LocalDate.of(2026, 9, 30), "3385.75");
        Producto silla = compra("4", "SIL/09", "A0000000-0000-4000-8000-000000002059", "PROVEEDOR FICTICIO SA", LocalDate.of(2026, 9, 9), "2198.99");
        // The whole folio left in «Factura» (as the C-2 lists brought it), without dashes.
        Producto ventilador = compra("5", "VEN/05", "01329", "OTRO PROVEEDOR FICTICIO", LocalDate.of(2026, 10, 7), "1900.06");
        Producto suCompra = compra("6", "VEN/25", null, "OTRO PROVEEDOR FICTICIO", LocalDate.of(2026, 10, 7), "1900.06");
        suCompra.setNumeroFactura("b0000000000040008000000000001329");
        // Two folios end alike: neither is proof.
        Producto dudoso = compra("7", "DUD/01", "4D429", "TERCERO FICTICIO", fecha, "100.00");
        Producto uno = compra("8", "DUD/02", "C0000000-0000-4000-8000-00000004D429", "TERCERO FICTICIO", fecha, "100.00");
        Producto otro = compra("9", "DUD/03", "D0000000-0000-4000-8000-00000004D429", "TERCERO FICTICIO", fecha, "100.00");
        Producto deBaja = compra("10", "NOB/24", "1636B", "PROVEEDOR FICTICIO SA", fecha, "2291.55");
        deBaja.setFechaBaja(LocalDate.of(2026, 11, 1));

        List<Producto> todos = List.of(noBreak, suHermano, escritorio, silla, ventilador, suCompra, dudoso, uno, otro, deBaja);
        var folios = CalidadDatosService.foliosDeSuCompra(todos, todos);

        assertEquals(List.of("NOB/23", "VEN/05"), folios.stream().map(f -> f.bien().getCodigo()).toList());
        assertEquals("F0000000-0000-4000-8000-00000001636B", folios.get(0).folio());
        assertEquals("NOB/44", folios.get(0).de().getCodigo());
        assertEquals("B0000000-0000-4000-8000-000000001329", folios.get(1).folio(), "con guiones y en mayúsculas");
    }

    /** 18-nov-2026: 5 of the 24 folios written wrong were a character or two off the folio of
     *  another bien, and the next one was 15 or more away. */
    @Test
    void unFolioConUnCaracterDeMasSeCorrigeConElDeSuCompra() {
        LocalDate fecha = LocalDate.of(2026, 5, 20);
        Producto bien = compra("b", "BIEN/01", "E0000000-1111-4222-8333-444455556666", "PROVEEDOR FICTICIO", fecha, "500.00");
        Producto deMas = compra("1", "MAS/01", "E00000000-1111-4222-8333-444455556666", null, null, "0");
        Producto conOyMenos = compra("2", "MEN/01", "eOOOOOOO-1111-4222-8333-44445555666", "PROVEEDOR FICTICIO", fecha, "500.00");
        // Two right folios a character apart: the wrong one could be either.
        Producto x = compra("x", "X/01", "A1111111-2222-4333-8444-555566667777", null, null, "0");
        Producto y = compra("y", "Y/01", "A1111111-2222-4333-8444-555566667778", null, null, "0");
        Producto ambiguo = compra("3", "AMB/01", "A1111111-2222-4333-8444-55556666777", null, null, "0");
        // Nothing close: it is typed from the factura.
        Producto lejano = compra("4", "LEJ/01", "F0F37FSE-E2-FE4AC6-B459-E4ADBF3469C6", null, null, "0");

        List<Producto> todos = List.of(bien, deMas, conOyMenos, x, y, ambiguo, lejano);
        var folios = CalidadDatosService.foliosDeSuCompra(todos, todos);

        assertEquals(List.of("MAS/01", "MEN/01"), folios.stream().map(f -> f.bien().getCodigo()).toList());
        assertEquals("E0000000-1111-4222-8333-444455556666", folios.get(0).folio());
        assertEquals("BIEN/01", folios.get(1).de().getCodigo());
        assertEquals(1, CalidadDatosService.distancia("ABCD", "ABXD", 10));
        assertEquals(3, CalidadDatosService.distancia("AAAAAAAA", "BBBBBBBB", 3), "se corta en el tope");
    }

    private static Producto compra(String id, String codigo, String folio, String proveedor, LocalDate fecha, String costo) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setFolioFiscal(folio);
        p.setProveedor(proveedor);
        p.setFechaAdquisicion(fecha);
        p.setPrecioCompra(new java.math.BigDecimal(costo));
        return p;
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
