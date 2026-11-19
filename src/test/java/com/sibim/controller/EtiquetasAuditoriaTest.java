package com.sibim.controller;

import com.sibim.model.AuditLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: the Auditoría screen and Configuración › «Ver actividad» each had their own words
 *  («Alta» and «Creado»); the dates read «19/11/2026 09:29:34» on every row. */
class EtiquetasAuditoriaTest {

    @Test
    void cuando_corto_yConLaFechaCompletaSoloSiEsDeOtroAnio() {
        LocalDate hoy = LocalDate.of(2026, 11, 19);
        assertEquals("Hoy 09:29", EtiquetasAuditoria.cuando(LocalDateTime.of(2026, 11, 19, 9, 29, 34), hoy));
        assertEquals("Ayer 21:43", EtiquetasAuditoria.cuando(LocalDateTime.of(2026, 11, 18, 21, 43), hoy));
        assertEquals("17 nov 16:19", EtiquetasAuditoria.cuando(LocalDateTime.of(2026, 11, 17, 16, 19), hoy));
        assertEquals("17/11/25 16:19", EtiquetasAuditoria.cuando(LocalDateTime.of(2025, 11, 17, 16, 19), hoy));
        assertEquals("—", EtiquetasAuditoria.cuando(null, hoy));
    }

    @Test
    void lasMismasPalabrasEnTodasPartes() {
        assertEquals("Alta", EtiquetasAuditoria.accion("crear"));
        assertEquals("Acceso fallido", EtiquetasAuditoria.accion("login_fallido"));
        assertEquals("Actualización", EtiquetasAuditoria.accion("editar"));
        assertEquals("Bienes", EtiquetasAuditoria.entidadPlural("producto"));
        assertEquals("Bien", EtiquetasAuditoria.entidadSingular("producto"));
        assertEquals("Conteo físico", EtiquetasAuditoria.entidadSingular("conteo"));
        assertEquals("audit-pill-guinda", EtiquetasAuditoria.tono("login_fallido"));
    }

    @Test
    void sobreQue_elNumeroDelBienPrimero() {
        AuditLog bien = new AuditLog();
        bien.setEntidad("producto"); bien.setEntidadNombre("SILLA");
        bien.setDetalle("Datos del bien actualizados · número PRUE/02 · Área de Prueba · Cambió: color (sin dato → NEGRO)");
        assertEquals("PRUE/02 · SILLA", AuditoriaColumnSetup.sobre(bien));
        AuditLog resguardo = new AuditLog();
        resguardo.setEntidad("resguardo"); resguardo.setEntidadNombre("PERSONA DE PRUEBA");
        assertEquals("Resguardo · PERSONA DE PRUEBA", AuditoriaColumnSetup.sobre(resguardo));
        assertEquals("2026-11-19", AuditoriaController.fechasDe(AuditoriaController.HOY, LocalDate.of(2026, 11, 19))[0].toString());
        assertEquals("2026-11-13", AuditoriaController.fechasDe(AuditoriaController.SIETE, LocalDate.of(2026, 11, 19))[0].toString());
        assertNull(AuditoriaController.fechasDe(AuditoriaController.TODO, LocalDate.of(2026, 11, 19)));
    }
}
