package com.sibim.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 15);

    // ── parseDate ────────────────────────────────────────────────────────────

    @Test
    void parseDate_validIsoString_returnsDate() {
        assertEquals(LocalDate.of(2026, 1, 10), SchedulerService.parseDate("2026-01-10"));
    }

    @Test
    void parseDate_nullString_returnsNull() {
        assertNull(SchedulerService.parseDate(null));
    }

    @Test
    void parseDate_blankString_returnsNull() {
        assertNull(SchedulerService.parseDate("  "));
    }

    @Test
    void parseDate_invalidFormat_returnsNull() {
        assertNull(SchedulerService.parseDate("15/09/2026"));
    }

    // ── esTiempoDeEjecutar — ultima null ────────────────────────────────────

    @Test
    void esTiempoDeEjecutar_ultimaNull_alwaysTrue() {
        assertTrue(SchedulerService.esTiempoDeEjecutar("DIARIO",   HOY, null));
        assertTrue(SchedulerService.esTiempoDeEjecutar("SEMANAL",  HOY, null));
        assertTrue(SchedulerService.esTiempoDeEjecutar("MENSUAL",  HOY, null));
    }

    // ── esTiempoDeEjecutar — DIARIO ──────────────────────────────────────────

    @Test
    void esTiempoDeEjecutar_diario_sameDay_returnsFalse() {
        assertFalse(SchedulerService.esTiempoDeEjecutar("DIARIO", HOY, HOY));
    }

    @Test
    void esTiempoDeEjecutar_diario_yesterday_returnsTrue() {
        assertTrue(SchedulerService.esTiempoDeEjecutar("DIARIO", HOY, HOY.minusDays(1)));
    }

    // ── esTiempoDeEjecutar — SEMANAL ─────────────────────────────────────────

    @Test
    void esTiempoDeEjecutar_semanal_sixDaysAgo_returnsFalse() {
        assertFalse(SchedulerService.esTiempoDeEjecutar("SEMANAL", HOY, HOY.minusDays(6)));
    }

    @Test
    void esTiempoDeEjecutar_semanal_sevenDaysAgo_returnsTrue() {
        assertTrue(SchedulerService.esTiempoDeEjecutar("SEMANAL", HOY, HOY.minusDays(7)));
    }

    // ── esTiempoDeEjecutar — MENSUAL ─────────────────────────────────────────

    @Test
    void esTiempoDeEjecutar_mensual_twentyNineDaysAgo_returnsFalse() {
        assertFalse(SchedulerService.esTiempoDeEjecutar("MENSUAL", HOY, HOY.minusDays(29)));
    }

    @Test
    void esTiempoDeEjecutar_mensual_oneMonthAgo_returnsTrue() {
        assertTrue(SchedulerService.esTiempoDeEjecutar("MENSUAL", HOY, HOY.minusMonths(1)));
    }

    // ── esTiempoDeEjecutar — frecuencia desconocida ──────────────────────────

    @Test
    void esTiempoDeEjecutar_unknownFrecuencia_returnsFalse() {
        assertFalse(SchedulerService.esTiempoDeEjecutar("ANUAL", HOY, HOY.minusYears(1)));
    }
}
