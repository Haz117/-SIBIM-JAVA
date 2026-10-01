package com.sibim.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.service.AccesosEquipoService;
import com.sibim.session.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends this PC's ERROR log lines to the database (table
 * {@code errores_equipo}, V30) so Patrimonio can see what is failing on the
 * áreas' computers without going there — each PC's log is a local file.
 *
 * Deliberately modest: at most {@link #MAXIMO_POR_SESION} per run and never
 * the same message twice, only with a live connection, never in demo mode or
 * in tests, and a failure to send is dropped in silence (it must not log,
 * or it would feed itself).
 */
public class ErroresEquipoAppender extends AppenderBase<ILoggingEvent> {

    static final int MAXIMO_POR_SESION = 25;
    private static final int MAX_MENSAJE = 500;
    private static final int MAX_DETALLE = 4000;

    private final AtomicInteger enviados = new AtomicInteger();
    private final Set<String> vistos = ConcurrentHashMap.newKeySet();

    @Override
    protected void append(ILoggingEvent evento) {
        if (!evento.getLevel().isGreaterOrEqual(Level.ERROR)) return;
        if (Boolean.getBoolean("sibim.test")) return;
        String origen = evento.getLoggerName();
        // Connection trouble can't be reported over the connection that is failing.
        if (origen != null && (origen.startsWith("com.zaxxer") || origen.startsWith("org.postgresql"))) return;
        String mensaje = recortar(evento.getFormattedMessage(), MAX_MENSAJE);
        if (mensaje == null || mensaje.isBlank() || !vistos.add(origen + "|" + mensaje)) return;
        if (enviados.incrementAndGet() > MAXIMO_POR_SESION) return;

        String detalle = recortar(pila(evento.getThrowableProxy()), MAX_DETALLE);
        Usuario u = SessionManager.getCurrentUser();
        String usuario = u != null ? u.getUsername() : null;
        Thread.ofVirtual().name("sibim-errores-equipo").start(() -> enviar(origen, mensaje, detalle, usuario));
    }

    private static void enviar(String origen, String mensaje, String detalle, String usuario) {
        try {
            if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
            try (Connection c = DatabaseConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO errores_equipo (id, equipo, usuario, version, origen, mensaje, detalle) "
                     + "VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, AccesosEquipoService.esteEquipo());
                ps.setString(3, usuario);
                ps.setString(4, UpdateChecker.currentVersion());
                ps.setString(5, origen);
                ps.setString(6, mensaje);
                ps.setString(7, detalle);
                ps.executeUpdate();
            }
        } catch (Exception | LinkageError ignored) {
            // By design: reporting an error must never raise or log another one.
        }
    }

    private static String pila(IThrowableProxy t) {
        if (t == null) return null;
        StringBuilder sb = new StringBuilder();
        for (IThrowableProxy causa = t; causa != null && sb.length() < MAX_DETALLE; causa = causa.getCause()) {
            if (causa != t) sb.append("Causado por: ");
            sb.append(causa.getClassName()).append(": ").append(causa.getMessage()).append('\n');
            StackTraceElementProxy[] pasos = causa.getStackTraceElementProxyArray();
            for (int i = 0; pasos != null && i < pasos.length && i < 12; i++)
                sb.append("  ").append(pasos[i].getSTEAsString()).append('\n');
        }
        return sb.toString();
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
