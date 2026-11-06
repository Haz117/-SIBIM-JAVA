package com.sibim.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An área's request about one of its bienes — a préstamo, a resguardo or
 *  (from 5-nov-2026) its baja; Patrimonio attends it or rejects it with a reason.
 *
 *  <p>For a baja the área also hands in the signed formato at Recursos Materiales
 *  y Patrimonio: {@code formatoFolio} / {@code formatoRecibidoEn} say when
 *  Patrimonio got it (V35); without it the bien isn't given de baja. */
public record Solicitud(
        String id, String tipo,
        String productoId, String productoNombre, String productoCodigo,
        String area, String solicitante,
        String areaDestino, String responsable, String cargo, String motivo, LocalDate fechaDevolucion,
        String estado, String respuesta, String documento,
        String resueltoPor, LocalDateTime resueltoEn, LocalDateTime creadoEn,
        String formatoFolio, LocalDateTime formatoRecibidoEn, String formatoRecibidoPor) {

    public static final String TIPO_PRESTAMO  = "prestamo";
    public static final String TIPO_RESGUARDO = "resguardo";
    /** The área asks Patrimonio to dar de baja the bien (V35). */
    public static final String TIPO_BAJA      = "baja";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_APROBADA  = "APROBADA";
    public static final String ESTADO_RECHAZADA = "RECHAZADA";

    /** Where the signed formato of a solicitud de baja is handed in. */
    public static final String ENTREGAR_FORMATO_EN = "Recursos Materiales y Patrimonio";

    /** A request without the formato's data (préstamos, resguardos, or a baja not handed in yet). */
    public Solicitud(String id, String tipo, String productoId, String productoNombre, String productoCodigo,
                     String area, String solicitante, String areaDestino, String responsable, String cargo,
                     String motivo, LocalDate fechaDevolucion, String estado, String respuesta, String documento,
                     String resueltoPor, LocalDateTime resueltoEn, LocalDateTime creadoEn) {
        this(id, tipo, productoId, productoNombre, productoCodigo, area, solicitante, areaDestino, responsable, cargo,
            motivo, fechaDevolucion, estado, respuesta, documento, resueltoPor, resueltoEn, creadoEn, null, null, null);
    }

    public boolean esPrestamo()  { return TIPO_PRESTAMO.equals(tipo); }
    public boolean esBaja()      { return TIPO_BAJA.equals(tipo); }
    public boolean isPendiente() { return ESTADO_PENDIENTE.equals(estado); }

    /** A baja in wait whose signed formato hasn't reached Recursos Materiales y Patrimonio. */
    public boolean faltaFormato() { return esBaja() && isPendiente() && formatoRecibidoEn == null; }

    /** "Préstamo" / "Resguardo" / "Baja", for titles and messages. */
    public String tipoTexto() { return esPrestamo() ? "Préstamo" : esBaja() ? "Baja" : "Resguardo"; }
}
