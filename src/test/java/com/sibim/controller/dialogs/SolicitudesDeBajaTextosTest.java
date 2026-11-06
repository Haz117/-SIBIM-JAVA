package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Solicitud;
import com.sibim.service.SolicitudService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 5-nov-2026: what the área and Patrimonio read about a solicitud de baja. */
class SolicitudesDeBajaTextosTest {

    @Test
    void motivo_conDescripcion_ySoloConOtro() {
        assertEquals("Obsoleto", SolicitudService.textoMotivo("Obsoleto", "  "));
        assertEquals("Obsoleto: ya no enciende", SolicitudService.textoMotivo("Obsoleto", " ya no enciende "));
        assertEquals("Lo mordió el perro", SolicitudService.textoMotivo("Otro", "Lo mordió el perro"));
    }

    @Test
    void estados_dichosComoBaja() {
        assertEquals("Falta el formato", SolicitudesDialog.estadoTexto(baja(Solicitud.ESTADO_PENDIENTE)));
        assertEquals("En espera", SolicitudesDialog.estadoTexto(conFormato(baja(Solicitud.ESTADO_PENDIENTE))));
        assertEquals("Dado de baja", SolicitudesDialog.estadoTexto(baja(Solicitud.ESTADO_APROBADA)));
        assertEquals("No procede", SolicitudesDialog.estadoTexto(baja(Solicitud.ESTADO_RECHAZADA)));
        assertTrue(SolicitudesDialog.detalleTexto(baja(Solicitud.ESTADO_PENDIENTE))
            .startsWith("Dirección de Catastro · Motivo: Obsoleto"));
    }

    /** The área hands in the signed formato at Recursos Materiales y Patrimonio. */
    @Test
    void formato_porEntregarORecibido() {
        assertEquals("Falta entregar el formato firmado en Recursos Materiales y Patrimonio",
            SolicitudesDialog.formatoTexto(baja(Solicitud.ESTADO_PENDIENTE)));
        String recibido = SolicitudesDialog.formatoTexto(conFormato(baja(Solicitud.ESTADO_PENDIENTE)));
        assertTrue(recibido.startsWith("Formato firmado recibido el ") && recibido.endsWith(" · folio SB-021"), recibido);
        assertNull(SolicitudesDialog.formatoTexto(baja(Solicitud.ESTADO_RECHAZADA)), "un «No procede» ya no pide formato");
        assertEquals("SB-021", SolicitudService.folioDelFormato("Obsoleto · Solicitud de baja: SB-021 "));
        assertNull(SolicitudService.folioDelFormato("Obsoleto"));
    }

    @Test
    void avisoAlArea_alEnviar() {
        Producto silla = new Producto();
        silla.setNombre("Silla");
        String una = SolicitarBajaDialog.mensaje(new SolicitudService.EnvioBaja(List.of(baja(Solicitud.ESTADO_PENDIENTE)), List.of()));
        assertTrue(una.startsWith("Se envió a Patrimonio la solicitud de baja"), una);
        assertTrue(una.contains("entrégalo en Recursos Materiales y Patrimonio"), una);
        String dos = SolicitarBajaDialog.mensaje(new SolicitudService.EnvioBaja(
            List.of(baja(Solicitud.ESTADO_PENDIENTE), baja(Solicitud.ESTADO_PENDIENTE)), List.of(silla)));
        assertTrue(dos.startsWith("Se enviaron a Patrimonio 2 solicitudes de baja"), dos);
        assertTrue(dos.contains("1 bien ya tenía su solicitud en espera"), dos);
        String yaEstaba = SolicitarBajaDialog.mensaje(new SolicitudService.EnvioBaja(List.of(), List.of(silla)));
        assertTrue(yaEstaba.startsWith("Ese bien ya tiene su solicitud de baja en espera: se volvió a generar el formato"), yaEstaba);
    }

    private static Solicitud conFormato(Solicitud s) {
        return new Solicitud(s.id(), s.tipo(), s.productoId(), s.productoNombre(), s.productoCodigo(), s.area(),
            s.solicitante(), s.areaDestino(), s.responsable(), s.cargo(), s.motivo(), s.fechaDevolucion(), s.estado(),
            s.respuesta(), s.documento(), s.resueltoPor(), s.resueltoEn(), s.creadoEn(),
            "SB-021", LocalDateTime.now(), "Patrimonio");
    }

    private static Solicitud baja(String estado) {
        return new Solicitud("s", Solicitud.TIPO_BAJA, "b-1", "Laptop", "CAT/01", "Dirección de Catastro", "Ana Ruiz",
            null, "Ana Ruiz", null, "Obsoleto", null, estado, null, null, null, null, LocalDateTime.now());
    }
}
