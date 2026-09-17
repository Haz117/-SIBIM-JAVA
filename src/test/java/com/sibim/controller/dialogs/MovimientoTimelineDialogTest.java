package com.sibim.controller.dialogs;

import com.sibim.controller.dialogs.MovimientoTimelineDialog.Entrada;
import com.sibim.controller.dialogs.MovimientoTimelineDialog.Fuente;
import com.sibim.controller.dialogs.MovimientoTimelineDialog.TimelineData;
import com.sibim.model.AuditLog;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.TipoMovimiento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for MovimientoTimelineDialog.mergeEntradas — the "cadena de
 *  custodia" logic that merges movimientos, resguardos, préstamos and
 *  auditoría into one chronological list. */
class MovimientoTimelineDialogTest {

    @Test
    void listasVacias_retornaVacio() {
        var data = new TimelineData(List.of(), List.of(), List.of(), List.of());
        assertTrue(MovimientoTimelineDialog.mergeEntradas(data).isEmpty());
    }

    @Test
    void unaEntradaPorFuente_lasIncluyeATodas() {
        Movimiento m = movimiento(TipoMovimiento.ENTRADA, LocalDateTime.of(2026, 1, 1, 9, 0));
        Resguardo r = resguardo(LocalDateTime.of(2026, 1, 2, 9, 0));
        Prestamo p = prestamo(LocalDate.of(2026, 1, 3));
        AuditLog a = auditLog(LocalDateTime.of(2026, 1, 4, 9, 0));

        var data = new TimelineData(List.of(m), List.of(r), List.of(p), List.of(a));
        List<Entrada> result = MovimientoTimelineDialog.mergeEntradas(data);

        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(e -> e.fuente() == Fuente.MOVIMIENTO));
        assertTrue(result.stream().anyMatch(e -> e.fuente() == Fuente.RESGUARDO));
        assertTrue(result.stream().anyMatch(e -> e.fuente() == Fuente.PRESTAMO));
        assertTrue(result.stream().anyMatch(e -> e.fuente() == Fuente.AUDITORIA));
    }

    @Test
    void ordenaCronologicamenteDeMasRecienteAMasAntiguo_entreFuentesDistintas() {
        Movimiento viejo = movimiento(TipoMovimiento.ENTRADA, LocalDateTime.of(2025, 1, 1, 0, 0));
        AuditLog reciente = auditLog(LocalDateTime.of(2026, 6, 1, 0, 0));
        Resguardo medio = resguardo(LocalDateTime.of(2025, 6, 1, 0, 0));

        var data = new TimelineData(List.of(viejo), List.of(medio), List.of(), List.of(reciente));
        List<Entrada> result = MovimientoTimelineDialog.mergeEntradas(data);

        assertEquals(Fuente.AUDITORIA, result.get(0).fuente());
        assertEquals(Fuente.RESGUARDO, result.get(1).fuente());
        assertEquals(Fuente.MOVIMIENTO, result.get(2).fuente());
    }

    @Test
    void prestamo_usaFechaPrestamo_noCreadoEn() {
        Prestamo p = new Prestamo();
        p.setNumero("PR-01");
        p.setFechaPrestamo(LocalDate.of(2026, 3, 15));
        p.setCreadoEn(LocalDateTime.of(2020, 1, 1, 0, 0)); // muy distinto, no debe usarse

        var data = new TimelineData(List.of(), List.of(), List.of(p), List.of());
        Entrada entrada = MovimientoTimelineDialog.mergeEntradas(data).get(0);

        assertEquals(LocalDateTime.of(2026, 3, 15, 0, 0), entrada.fecha());
    }

    @Test
    void fechaNula_seOrdenaAlFinal() {
        Movimiento conFecha = movimiento(TipoMovimiento.SALIDA, LocalDateTime.of(2026, 1, 1, 0, 0));
        AuditLog sinFecha = new AuditLog();
        sinFecha.setAccion("crear");
        sinFecha.setCreadoEn(null);

        var data = new TimelineData(List.of(conFecha), List.of(), List.of(), List.of(sinFecha));
        List<Entrada> result = MovimientoTimelineDialog.mergeEntradas(data);

        assertEquals(2, result.size());
        assertEquals(Fuente.MOVIMIENTO, result.get(0).fuente());
        assertEquals(Fuente.AUDITORIA, result.get(1).fuente());
    }

    @Test
    void auditoriaVacia_noAparecePeroNoRompeElMerge() {
        Movimiento m = movimiento(TipoMovimiento.AJUSTE, LocalDateTime.now());
        var data = new TimelineData(List.of(m), List.of(), List.of(), List.of());
        assertEquals(1, MovimientoTimelineDialog.mergeEntradas(data).size());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Movimiento movimiento(TipoMovimiento tipo, LocalDateTime fecha) {
        Movimiento m = new Movimiento();
        m.setTipo(tipo);
        m.setCantidad(5);
        m.setStockAnterior(0);
        m.setStockNuevo(5);
        m.setUsuarioNombre("Admin Test");
        m.setCreadoEn(fecha);
        return m;
    }

    private static Resguardo resguardo(LocalDateTime fecha) {
        Resguardo r = new Resguardo();
        r.setNumero("RSG-01");
        r.setResguardanteNombre("Juan Pérez");
        r.setEstado("ACTIVO");
        r.setCreadoEn(fecha);
        return r;
    }

    private static Prestamo prestamo(LocalDate fecha) {
        Prestamo p = new Prestamo();
        p.setNumero("PR-01");
        p.setResponsableNombre("Ana Gómez");
        p.setEstado("ACTIVO");
        p.setFechaPrestamo(fecha);
        return p;
    }

    private static AuditLog auditLog(LocalDateTime fecha) {
        AuditLog a = new AuditLog();
        a.setAccion("actualizar");
        a.setDetalle("Datos actualizados");
        a.setUsuarioNombre("Admin Test");
        a.setCreadoEn(fecha);
        return a;
    }
}
