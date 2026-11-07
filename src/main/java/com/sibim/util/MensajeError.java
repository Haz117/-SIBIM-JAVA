package com.sibim.util;

import java.io.IOException;
import java.sql.SQLException;

/**
 * What a person reads when something fails. SIBIM's own messages
 * ("El número ya lo usa otro bien", "Sin conexión con el servidor…") are
 * written for them and pass as they are; what comes from the database driver,
 * the connection pool or the network ("HikariPool-1 - Connection is not
 * available, request timed out after 8000ms", "ERROR: duplicate key value
 * violates unique constraint…") becomes a sentence that says what happened and
 * what to do. The original stays in the log.
 */
public final class MensajeError {

    private MensajeError() {}

    static final String SIN_CONEXION = "No hay conexión con el servidor. Revisa la red e inténtalo de nuevo.";
    static final String REPETIDO = "Ya existe un registro con esos datos.";
    static final String LIGADO = "Está ligado a otros registros y no se puede cambiar así.";
    static final String SIN_PERMISO = "Tu cuenta no tiene permiso para hacer esto.";
    static final String TARDO = "El servidor tardó demasiado en responder. Inténtalo de nuevo.";
    static final String ARCHIVO = "No se pudo leer o guardar el archivo. Si está abierto en otro programa, ciérralo e inténtalo de nuevo.";
    static final String GENERICO = "Ocurrió un error inesperado. Inténtalo de nuevo; si sigue pasando, avisa a TI.";

    /** The message for {@code e}, never null. */
    public static String de(Throwable e) {
        if (e == null) return GENERICO;
        Throwable t = e;
        for (int i = 0; t != null && i < 10; i++, t = t.getCause()) {
            if (t instanceof SQLException sql) return deBase(sql);
            if (t instanceof java.net.http.HttpTimeoutException || t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.NoRouteToHostException) return SIN_CONEXION;
            if (t instanceof java.nio.file.FileSystemException || t instanceof java.io.FileNotFoundException) return ARCHIVO;
            if (propio(t)) return t.getMessage().strip();
            if (t.getCause() == null || t.getCause() == t) break;
        }
        return legible(e.getMessage()) ? e.getMessage().strip() : GENERICO;
    }

    /** Thrown by SIBIM with a sentence for the person: the services' own
     *  checks, and a plain IOException SIBIM raised itself. */
    private static boolean propio(Throwable t) {
        if (!legible(t.getMessage())) return false;
        return t instanceof IllegalStateException || t instanceof IllegalArgumentException
            || t instanceof SecurityException || t.getClass() == IOException.class
            || t.getClass().getName().startsWith("com.sibim.");
    }

    private static String deBase(SQLException e) {
        String estado = e.getSQLState() != null ? e.getSQLState() : "";
        String texto = e.getMessage() != null ? e.getMessage() : "";
        if (texto.equals(com.sibim.db.DatabaseConfig.SIN_CONEXION)) return texto;
        if (e instanceof java.sql.SQLTransientConnectionException || estado.startsWith("08")
                || texto.contains("Connection is not available") || texto.contains("connection attempt failed"))
            return SIN_CONEXION;
        switch (estado) {
            case "23505": return REPETIDO;
            case "23503": return LIGADO;
            case "42501": return SIN_PERMISO;
            case "57014": return TARDO;
            case "P0001": {   // RAISE EXCEPTION in SIBIM's own triggers: written for the person
                String primera = texto.lines().findFirst().orElse("").replaceFirst("^(ERROR|ERROR FATAL|FATAL):\\s*", "").strip();
                return primera.isEmpty() ? GENERICO : primera;
            }
            default: return GENERICO;
        }
    }

    /** Looks like a sentence, not a stack-trace fragment. */
    static boolean legible(String m) {
        if (m == null || m.isBlank()) return false;
        String s = m.strip();
        return !(s.contains("Exception") || s.contains("java.") || s.contains("javax.") || s.startsWith("ERROR:")
            || s.contains("SQL") || s.contains("Hikari") || s.contains("org.") || s.contains("com.")
            || s.equals("null") || s.contains("\tat "));
    }
}
