package com.sibim.service;

import com.sibim.model.AuditLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: Auditoría showed «Datos del bien actualizados · número …» and what changed never fit. */
class LecturaAuditoriaTest {

    private static AuditLog entrada(String entidad, String accion, String detalle) {
        AuditLog a = new AuditLog();
        a.setEntidad(entidad); a.setAccion(accion); a.setDetalle(detalle);
        return a;
    }

    @Test
    void unaActualizacion_numeroAreaYCadaCambio() {
        var l = LecturaAuditoria.de(entrada("producto", "actualizar",
            "Datos del bien actualizados · número PRUE/19 · Área de Prueba — Radio · Cambió: proveedor (sin dato → PROVEEDOR FICTICIO SA), "
                + "valor residual ($0.00 → $3,236.40), nombre (SILLA, NEGRA (4 PATAS) → SILLA NEGRA), descripción, foto agregada, "
                + "tipo de bien (sin dato → MUEBLE)"));
        assertEquals("PRUE/19", l.numero());
        assertEquals("Área de Prueba — Radio", l.area());
        assertEquals(List.of(
            new LecturaAuditoria.Cambio("proveedor", null, "PROVEEDOR FICTICIO SA"),
            new LecturaAuditoria.Cambio("valor residual", "$0.00", "$3,236.40"),
            new LecturaAuditoria.Cambio("nombre", "SILLA, NEGRA (4 PATAS)", "SILLA NEGRA"),
            new LecturaAuditoria.Cambio("descripción", null, null),
            new LecturaAuditoria.Cambio("foto agregada", null, null),
            new LecturaAuditoria.Cambio("tipo de bien", null, "MUEBLE")), l.cambios());
        assertTrue(l.resumen().startsWith("proveedor: — → PROVEEDOR FICTICIO SA · valor residual: $0.00 → $3,236.40 · "), l.resumen());
    }

    /** The corrections of the October load: «área», «fotos», a note after the last change, and a
     *  value cut at 40 characters with its «(» left open. */
    @Test
    void lasCorrecciones_yLosValoresRecortados() {
        var l = LecturaAuditoria.de(entrada("producto", "actualizar",
            "Cambió: área (Área Uno → Área Dos), número (UNO/35 → DOS/11), estado físico (BUENO → MALO) · venía de una hoja de 2023"));
        assertEquals(List.of(
            new LecturaAuditoria.Cambio("área", "Área Uno", "Área Dos"),
            new LecturaAuditoria.Cambio("número", "UNO/35", "DOS/11"),
            new LecturaAuditoria.Cambio("estado físico", "BUENO", "MALO"),
            new LecturaAuditoria.Cambio("venía de una hoja de 2023", null, null)), l.cambios());

        var fotos = LecturaAuditoria.de(entrada("producto", "actualizar", "Cambió: fotos · los tenía su copia repetida"));
        assertEquals(List.of(new LecturaAuditoria.Cambio("fotos", null, null),
            new LecturaAuditoria.Cambio("los tenía su copia repetida", null, null)), fotos.cambios());

        var recortado = LecturaAuditoria.de(entrada("producto", "actualizar",
            "Datos del bien actualizados · número PRUE/21 · Área · Cambió: nombre (LAPTOP ACER 1PZA. (LAPTOP AC… → LAPTOP ACER 16 PULGADAS)"));
        assertEquals(new LecturaAuditoria.Cambio("nombre", "LAPTOP ACER 1PZA. (LAPTOP AC…", "LAPTOP ACER 16 PULGADAS"),
            recortado.cambios().get(0));
    }

    @Test
    void losDemasRegistros_sinRepetirLoQueDicenLasColumnas() {
        var alta = LecturaAuditoria.de(entrada("producto", "crear", "Bien registrado · número PRUE/20 · Área de Prueba"));
        assertEquals("PRUE/20", alta.numero());
        assertEquals("Área de Prueba", alta.area());
        assertEquals("Bien registrado", alta.resumen());

        assertEquals("Se guardó sin cambios", LecturaAuditoria.de(entrada("producto", "actualizar", "Datos del bien actualizados")).resumen());
        assertEquals("Precio de compra: $9,778.50 → $11,343.06",
            LecturaAuditoria.de(entrada("producto", "cambio_precio", "Precio de compra: $9,778.50 → $11,343.06")).resumen());

        var resguardo = LecturaAuditoria.de(entrada("resguardo", "crear",
            "Resguardo RSG-2026-000339 · 10 bienes · Área: Tesorería — Cuenta Pública · del inventario 2026"));
        assertEquals("Tesorería — Cuenta Pública", resguardo.area());
        assertNull(resguardo.numero());
        assertTrue(resguardo.cambios().isEmpty());

        var sesion = LecturaAuditoria.de(entrada("sesion", "login", "Inicio de sesión — base de datos"));
        assertNull(sesion.area());
        assertEquals("Inicio de sesión — base de datos", sesion.resumen());
        assertEquals("", LecturaAuditoria.de(entrada("sesion", "logout", null)).resumen());
    }
}
