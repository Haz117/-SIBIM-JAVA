package com.sibim.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An área's request for a préstamo or a resguardo of one of its bienes;
 *  Patrimonio approves it (creating the document) or rejects it. */
public record Solicitud(
        String id, String tipo,
        String productoId, String productoNombre, String productoCodigo,
        String area, String solicitante,
        String areaDestino, String responsable, String cargo, String motivo, LocalDate fechaDevolucion,
        String estado, String respuesta, String documento,
        String resueltoPor, LocalDateTime resueltoEn, LocalDateTime creadoEn) {

    public static final String TIPO_PRESTAMO  = "prestamo";
    public static final String TIPO_RESGUARDO = "resguardo";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_APROBADA  = "APROBADA";
    public static final String ESTADO_RECHAZADA = "RECHAZADA";

    public boolean esPrestamo()  { return TIPO_PRESTAMO.equals(tipo); }
    public boolean isPendiente() { return ESTADO_PENDIENTE.equals(estado); }

    /** "Préstamo" / "Resguardo", for titles and messages. */
    public String tipoTexto() { return esPrestamo() ? "Préstamo" : "Resguardo"; }
}
