package com.sibim.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How the bitácora's entidades and acciones read on screen, once for the Auditoría screen and
 *  the «Ver actividad» window of Configuración (each had its own list, and they disagreed:
 *  «Creado» in one, «Alta» in the other). */
public final class EtiquetasAuditoria {

    private EtiquetasAuditoria() {}

    private static final Locale ES = Locale.forLanguageTag("es-MX");

    /** The filter's options: «Bienes», «Sesiones». */
    public static String entidadPlural(String entidad) {
        if (entidad == null || entidad.isBlank()) return "";
        return switch (entidad) {
            case "sesion" -> "Sesiones";
            case "producto" -> "Bienes";
            case "movimiento" -> "Movimientos";
            case "categoria" -> "Categorías";
            case "usuario" -> "Usuarios";
            case "resguardo" -> "Resguardos";
            case "area_resguardo" -> "Resguardos de área";
            case "prestamo" -> "Préstamos";
            case "comodato" -> "Comodatos";
            case "acta" -> "Actas de entrega-recepción";
            case "area" -> "Áreas";
            case "mantenimiento" -> "Mantenimiento";
            case "configuracion" -> "Configuración";
            case "solicitud" -> "Solicitudes";
            case "conteo" -> "Conteos físicos";
            case "backup" -> "Respaldos";
            case "persona" -> "Personas";
            case "sistema" -> "Sistema";
            default -> Character.toUpperCase(entidad.charAt(0)) + entidad.substring(1).replace('_', ' ');
        };
    }

    /** One of them: «Bien», «Resguardo». */
    public static String entidadSingular(String entidad) {
        if (entidad == null || entidad.isBlank()) return "";
        return switch (entidad) {
            case "sesion" -> "Sesión";
            case "producto" -> "Bien";
            case "movimiento" -> "Movimiento";
            case "categoria" -> "Categoría";
            case "usuario" -> "Usuario";
            case "resguardo" -> "Resguardo";
            case "area_resguardo" -> "Resguardo de área";
            case "prestamo" -> "Préstamo";
            case "comodato" -> "Comodato";
            case "acta" -> "Acta";
            case "area" -> "Área";
            case "mantenimiento" -> "Mantenimiento";
            case "configuracion" -> "Configuración";
            case "solicitud" -> "Solicitud";
            case "conteo" -> "Conteo físico";
            case "backup" -> "Respaldo";
            case "persona" -> "Persona";
            case "sistema" -> "Sistema";
            default -> entidadPlural(entidad);
        };
    }

    public static String accion(String accion) {
        if (accion == null || accion.isBlank()) return "";
        return switch (accion.toLowerCase(Locale.ROOT)) {
            case "login" -> "Inicio de sesión";
            case "logout" -> "Cierre de sesión";
            case "login_fallido" -> "Acceso fallido";
            case "operador" -> "Quién usa la cuenta";
            case "crear" -> "Alta";
            case "actualizar", "editar" -> "Actualización";
            case "eliminar" -> "Eliminación";
            case "baja" -> "Baja";
            case "reactivar" -> "Reactivación";
            case "cambio_precio" -> "Cambio de precio";
            case "codigo_reasignado" -> "Código reasignado";
            case "transferencia_aprobada" -> "Transferencia aprobada";
            case "transferencia_rechazada" -> "Transferencia rechazada";
            case "transferencia_recibida" -> "Transferencia recibida";
            case "transferencia_deshecha" -> "Transferencia deshecha";
            case "cancelar" -> "Cancelación";
            case "devolver" -> "Devolución";
            case "concluir" -> "Conclusión";
            case "rescindir" -> "Rescisión";
            case "restaurar" -> "Restauración";
            case "aprobar" -> "Aprobación";
            case "rechazar" -> "Rechazo";
            case "importar" -> "Carga";
            case "publicar" -> "Publicación";
            default -> Character.toUpperCase(accion.charAt(0)) + accion.substring(1).replace('_', ' ');
        };
    }

    /** The pill's tone: done or let in (verde), changed (dorado), taken out or refused (guinda). */
    public static String tono(String accion) {
        if (accion == null) return "audit-pill-neutro";
        return switch (accion.toLowerCase(Locale.ROOT)) {
            case "login", "crear", "reactivar", "operador", "transferencia_recibida", "transferencia_aprobada",
                 "aprobar", "devolver", "concluir", "restaurar" -> "audit-pill-verde";
            case "baja", "eliminar", "login_fallido", "transferencia_rechazada", "transferencia_deshecha",
                 "cancelar", "rescindir", "rechazar" -> "audit-pill-guinda";
            case "actualizar", "editar", "cambio_precio", "codigo_reasignado" -> "audit-pill-dorado";
            default -> "audit-pill-neutro";
        };
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d MMM", ES);
    private static final DateTimeFormatter COMPLETA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    public static final DateTimeFormatter CON_SEGUNDOS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** «Hoy 09:29», «Ayer 21:43», «17 nov 16:19», «17/11/25 16:19». */
    public static String cuando(LocalDateTime t, LocalDate hoy) {
        if (t == null) return "—";
        LocalDate d = t.toLocalDate();
        if (d.equals(hoy)) return "Hoy " + t.format(HORA);
        if (d.equals(hoy.minusDays(1))) return "Ayer " + t.format(HORA);
        if (d.getYear() == hoy.getYear()) return t.format(DIA_MES).replace(".", "") + " " + t.format(HORA);
        return t.format(COMPLETA);
    }
}
