package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * How far each área has got with SIBIM (5-nov-2026), in three phases, shown in
 * the Organigrama:
 * <ol>
 *   <li>Sign in: an account of the área has entered SIBIM with a connection
 *       (accesos_equipo, V29).</li>
 *   <li>Review its bienes: the área said of each one whether it is theirs
 *       (Producto#isRevisadoPorArea), and fills in what is missing (photo,
 *       ubicación, estado físico).</li>
 *   <li>Labels: the área confirmed the label is stuck on each bien (etiquetado).</li>
 * </ol>
 */
public class AvanceAreasService {

    /** The three phases of one área, counted over its active bienes.
     *  @param conCuenta whether the área has an account of its own to enter with
     *                   (9-nov-2026: none had one, and that is why none had entered);
     *                   null when it can't be known (offline, or no permission) */
    public record Avance(String area, LocalDateTime entro, int bienes, int revisados, int observados,
                         int conDatos, int etiquetados, Boolean conCuenta) {

        public Avance(String area, LocalDateTime entro, int bienes, int revisados, int observados,
                      int conDatos, int etiquetados) {
            this(area, entro, bienes, revisados, observados, conDatos, etiquetados, null);
        }

        /** It is known that the área has no account of its own yet (one that entered has one). */
        public boolean sinCuenta() { return entro == null && Boolean.FALSE.equals(conCuenta); }

        public boolean fase1() { return entro != null; }
        public boolean fase2() { return revisados >= bienes; }
        public boolean fase3() { return etiquetados >= bienes; }

        /** 0..1 of each phase; with no bienes, 2 and 3 have nothing to do. */
        public double avance(int fase) {
            return switch (fase) {
                case 1 -> fase1() ? 1 : 0;
                case 2 -> bienes == 0 ? 1 : (double) revisados / bienes;
                case 3 -> bienes == 0 ? 1 : (double) etiquetados / bienes;
                default -> throw new IllegalArgumentException("fase " + fase);
            };
        }

        /** The first phase not finished (1, 2 or 3), or 4 when it finished all three. */
        public int faseActual() {
            if (!fase1()) return 1;
            if (!fase2()) return 2;
            if (!fase3()) return 3;
            return 4;
        }

        /** "Paso 2 · 35 %", "Terminó". */
        public String resumen() {
            int f = faseActual();
            if (f == 4) return bienes == 0 && fase1() ? "Sin bienes" : "Terminó";
            if (f == 1) return sinCuenta() ? "Paso 1 · sin cuenta" : "Paso 1 · sin entrar";
            return "Paso " + f + " · " + Math.round(avance(f) * 100) + " %";
        }
    }

    /** The área-fillable data of phase 2: photo, ubicación and estado físico. */
    public static boolean conDatos(Producto p) {
        return lleno(p.getFotoUrl()) && lleno(p.getUbicacion()) && lleno(p.getEstadoFisico());
    }

    private static boolean lleno(String s) { return s != null && !s.isBlank(); }

    /** Each área's avance; every área named in {@code areas} is there, even without bienes. */
    public static Map<String, Avance> calcular(Collection<Producto> bienes, Map<String, LocalDateTime> accesos,
                                               Collection<String> areas) {
        return calcular(bienes, accesos, null, areas);
    }

    /** @param conCuenta the áreas with an account of their own; null when unknown */
    public static Map<String, Avance> calcular(Collection<Producto> bienes, Map<String, LocalDateTime> accesos,
                                               Set<String> conCuenta, Collection<String> areas) {
        Map<String, int[]> cuentas = new LinkedHashMap<>();
        for (String a : areas) cuentas.put(a, new int[5]);
        for (Producto p : bienes) {
            if (p.isDadoDeBaja() || p.getArea() == null) continue;
            int[] c = cuentas.computeIfAbsent(p.getArea(), k -> new int[5]);
            c[0]++;
            if (p.isRevisadoPorArea()) c[1]++;
            if (p.isObservadoPorArea()) c[2]++;
            if (conDatos(p)) c[3]++;
            if (p.isEtiquetado()) c[4]++;
        }
        Map<String, Avance> out = new LinkedHashMap<>();
        cuentas.forEach((a, c) -> out.put(a, new Avance(a, accesos.get(a), c[0], c[1], c[2], c[3], c[4],
            conCuenta == null ? null : conCuenta.contains(a) || accesos.containsKey(a))));
        return out;
    }

    /** The áreas with an active account of their own (Configuración › Cuentas por área
     *  makes one per área); null offline, where it can't be known. */
    public Set<String> areasConCuenta() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            Set<String> out = new HashSet<>();
            for (var u : com.sibim.db.DemoDataStore.findAllUsers())
                if (u.getArea() != null && u.isActivo()) out.add(u.getArea());
            return out;
        }
        if (DatabaseConfig.isOfflineMode()) return null;
        Set<String> out = new HashSet<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT area FROM users WHERE area IS NOT NULL AND activo IS NOT FALSE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /** When an account of each área last entered SIBIM with a connection. Empty
     *  offline (it can't be known there); in demo, the áreas of the demo accounts. */
    public Map<String, LocalDateTime> accesosPorArea() throws SQLException {
        Map<String, LocalDateTime> out = new HashMap<>();
        if (DatabaseConfig.isDemoMode()) {
            for (var u : com.sibim.db.DemoDataStore.findAllUsers())
                if (u.getArea() != null) out.merge(u.getArea(), LocalDateTime.now().minusDays(2), (a, b) -> a);
            return out;
        }
        if (DatabaseConfig.isOfflineMode()) return out;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT u.area, MAX(a.ultimo_acceso) FROM users u JOIN accesos_equipo a ON a.user_id = u.id "
                 + "WHERE u.area IS NOT NULL GROUP BY u.area");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp t = rs.getTimestamp(2);
                if (t != null) out.put(rs.getString(1), t.toLocalDateTime());
            }
        }
        return out;
    }
}
