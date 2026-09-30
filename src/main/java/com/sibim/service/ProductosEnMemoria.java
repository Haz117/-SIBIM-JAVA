package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The full list of active bienes, shared by every screen and dialog for a
 * few seconds.
 *
 * <p>Measured against the production Supabase (2026-09-26): the query itself
 * runs in ~3 ms on the server, but bringing 2 515 rows over the pooler takes
 * ~2.5–3 s, and the movement dialog, Alertas, Organigrama, Reportes, the
 * Préstamo/Comodato/Resguardo pickers and the search palette each fetched it
 * again on open. One copy, reused for {@link #VIGENCIA_MS} and dropped as soon
 * as this PC changes a bien or its stock, turns those repeats into no wait.
 * Other PCs' changes show up within that window, or right away with
 * Actualizar/F5 (see {@link #invalidar()}).
 *
 * <p>Only while connected: demo and offline read local memory/SQLite, which
 * is already fast.
 */
final class ProductosEnMemoria {

    static final long VIGENCIA_MS = 30_000;

    @FunctionalInterface
    interface Consulta { List<Producto> ejecutar() throws SQLException; }

    /** Off in the test JVM (surefire sets sibim.test): tests swap repositories and
     *  data between methods and must always read what they just wrote. */
    static boolean activa = !Boolean.getBoolean("sibim.test");

    private static List<Producto> lista;
    private static long cargadaEn;
    private static Object dueno;

    private ProductosEnMemoria() {}

    /** Synchronized on purpose: a caller that arrives while another one is
     *  loading waits for that load instead of starting a second 3-s query. */
    static synchronized List<Producto> obtener(Consulta consulta) throws SQLException {
        if (!activa || DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return consulta.ejecutar();
        Object quien = claveSesion();
        long ahora = System.currentTimeMillis();
        if (lista == null || !Objects.equals(dueno, quien) || ahora - cargadaEn > VIGENCIA_MS) {
            lista = List.copyOf(consulta.ejecutar());
            cargadaEn = System.currentTimeMillis();
            dueno = quien;
        }
        // Callers sometimes add to / sort what they get: hand out a copy.
        return new ArrayList<>(lista);
    }

    static synchronized void invalidar() {
        lista = null;
    }

    /** The list depends on who is logged in (área scope). */
    private static Object claveSesion() {
        Usuario u = SessionManager.getCurrentUser();
        return u == null ? null : u.getId() + "|" + u.getRol() + "|" + u.getArea();
    }
}
