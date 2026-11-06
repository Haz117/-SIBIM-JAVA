package com.sibim.controller;

import com.sibim.model.Solicitud;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 5-nov-2026: the banner of Bajas and the card of an área's Inicio. */
class BajasSolicitudesAvisoTest {

    @Test
    void patrimonio_cuantasPorRevisar() {
        assertNull(BajasController.textoPendientes(0), "sin solicitudes no hay aviso");
        assertTrue(BajasController.textoPendientes(1).startsWith("1 solicitud de baja por revisar"));
        assertTrue(BajasController.textoPendientes(3).startsWith("3 solicitudes de baja por revisar"));
    }

    @Test
    void area_comoVanLasSuyas() {
        assertNull(BajasController.textoMias(List.of(s(Solicitud.TIPO_PRESTAMO, Solicitud.ESTADO_PENDIENTE))));
        List<Solicitud> suyas = List.of(
            s(Solicitud.TIPO_BAJA, Solicitud.ESTADO_PENDIENTE), s(Solicitud.TIPO_BAJA, Solicitud.ESTADO_PENDIENTE),
            s(Solicitud.TIPO_BAJA, Solicitud.ESTADO_APROBADA), s(Solicitud.TIPO_BAJA, Solicitud.ESTADO_RECHAZADA),
            s(Solicitud.TIPO_RESGUARDO, Solicitud.ESTADO_RECHAZADA));
        assertEquals("2 formatos por entregar · 1 dado de baja · 1 no procede", BajasController.resumenMias(suyas));
        assertEquals("Tus solicitudes de baja: 2 formatos por entregar · 1 dado de baja · 1 no procede",
            BajasController.textoMias(suyas));
        List<Solicitud> unaEntregada = List.of(s(Solicitud.TIPO_BAJA, Solicitud.ESTADO_PENDIENTE),
            new Solicitud("s2", Solicitud.TIPO_BAJA, "b-2", "Silla", "CAT/02", "Catastro", "Ana", null, "Ana", null,
                "Obsoleto", null, Solicitud.ESTADO_PENDIENTE, null, null, null, null, LocalDateTime.now(),
                "SB-9", LocalDateTime.now(), "Patrimonio"));
        assertEquals("1 formato por entregar · 1 en espera", BajasController.resumenMias(unaEntregada));
    }

    @Test
    void porHacer_lasSolicitudesVanPrimero() {
        var resto = List.of(new com.sibim.service.CalidadDatosService.PorHacer(5, "bienes sin foto", "Completar datos", "productos", "completar:FOTO"));
        assertSame(resto, PorHacerPanel.conSolicitudes(0, resto));
        var con = PorHacerPanel.conSolicitudes(2, resto);
        assertEquals(2, con.size());
        assertEquals("bajas", con.get(0).vista());
        assertEquals("solicitudes", con.get(0).abrir());
        assertEquals(2, con.get(0).cantidad());
    }

    private static Solicitud s(String tipo, String estado) {
        return new Solicitud("s", tipo, "b-1", "Laptop", "CAT/01", "Catastro", "Ana", null, "Ana", null, "Obsoleto",
            null, estado, null, null, null, null, LocalDateTime.now());
    }
}
