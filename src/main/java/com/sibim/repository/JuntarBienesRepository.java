package com.sibim.repository;

import com.sibim.db.DatabaseConfig;

import java.sql.*;
import java.util.*;

/** «Son el mismo bien»: one bien stays, the other leaves, in one transaction. What the one
 *  that leaves had (photos, its movements, préstamos, comodatos, conteos, maintenance,
 *  price history and requests) goes on under the one that stays; its resguardo line goes
 *  to it too when the one that stays had none, else it leaves the resguardo. Everything
 *  needed to put it back is kept in a {@link Huella}. Only connected: the change crosses
 *  several tables that the offline store doesn't hold. */
public class JuntarBienesRepository {

    /** Tables whose rows belong to a bien and must not be lost with it. resguardo_items is
     *  last: by then only the lines of resguardos no longer in force are left (the active one
     *  was moved or removed above), and without this their link would be set to null by the
     *  delete (ON DELETE SET NULL) and the undo could not put it back. */
    static final List<String> HISTORIAL = List.of("movements", "comodatos", "prestamos", "conteo_items",
        "price_history", "producto_mantenimiento", "solicitudes", "product_fotos", "resguardo_items");

    /** The columns of products that the merge may write on the bien that stays. */
    public static final Set<String> COLUMNAS = Set.of("numero_serie", "marca", "modelo", "fecha_adquisicion",
        "numero_factura", "estado_fisico", "clave_armonizada", "vida_util_anios", "ubicacion", "proveedor", "color",
        "valor_residual", "folio_fiscal", "precio_compra", "precio_venta", "foto_url", "factura_url", "descripcion",
        "tipo_bien", "no_motor", "no_tarjeta_circulacion", "no_poliza_seguro", "resguardante");

    /** What a merge changed, to undo it. */
    public static final class Huella {
        Map<String, Object> sale, quedaAntes;
        final Set<String> columnasQueda = new LinkedHashSet<>();
        final Map<String, List<String>> movidos = new LinkedHashMap<>();
        final Map<String, String> motivos = new LinkedHashMap<>();
        final List<Map<String, Object>> partidasMovidas = new ArrayList<>(), partidasBorradas = new ArrayList<>(),
            resguardosCancelados = new ArrayList<>();
        public String quedaId() { return (String) quedaAntes.get("id"); }
        public String saleId() { return (String) sale.get("id"); }
        public String saleCodigo() { return (String) sale.get("codigo"); }
        /** The resguardo line went to the bien that stays (it had none). */
        public boolean pasoElResguardo() { return !partidasMovidas.isEmpty(); }
        public int resguardosCancelados() { return resguardosCancelados.size(); }
        public int filasDeHistorial() { return movidos.values().stream().mapToInt(List::size).sum(); }
    }

    /** @param poner columns of {@link #COLUMNAS} to write on the bien that stays
     *  @param nota added to the motivo of the movimientos that pass to the bien that stays */
    public Huella juntar(String quedaId, String saleId, Map<String, Object> poner, String nota) throws SQLException {
        if (Objects.equals(quedaId, saleId)) throw new SQLException("Es el mismo registro");
        for (String col : poner.keySet()) if (!COLUMNAS.contains(col)) throw new IllegalArgumentException(col);
        try (Connection c = DatabaseConfig.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                Map<String, Object> q = fila(c, "SELECT * FROM products WHERE id = ? FOR UPDATE", quedaId);
                Map<String, Object> s = fila(c, "SELECT * FROM products WHERE id = ? FOR UPDATE", saleId);
                if (q == null || s == null) throw new SQLException("Uno de los dos bienes ya no existe; actualiza la lista");
                if (q.get("fecha_baja") != null || s.get("fecha_baja") != null)
                    throw new SQLException("Uno de los dos está dado de baja");
                Huella h = new Huella();
                h.quedaAntes = q;
                h.sale = s;
                Map<String, Object> ponerFinal = new LinkedHashMap<>(poner);

                List<Map<String, Object>> partidas = filas(c, "SELECT ri.* FROM resguardo_items ri JOIN resguardos r ON r.id = ri.resguardo_id"
                    + " WHERE ri.producto_id = ? AND r.estado = 'ACTIVO'", saleId);
                boolean quedaEnResguardo = fila(c, "SELECT ri.id FROM resguardo_items ri JOIN resguardos r ON r.id = ri.resguardo_id"
                    + " WHERE ri.producto_id = ? AND r.estado = 'ACTIVO' LIMIT 1", quedaId) != null;
                if (!partidas.isEmpty() && !quedaEnResguardo) {
                    // The person's signed resguardo goes on, now naming the bien that stays.
                    for (Map<String, Object> ri : partidas) {
                        actualizar(c, "UPDATE resguardo_items SET producto_id = ?, producto_codigo = ?, producto_nombre = ? WHERE id = ?",
                            quedaId, q.get("codigo"), q.get("nombre"), ri.get("id"));
                        h.partidasMovidas.add(ri);
                    }
                    if (s.get("resguardante") != null) ponerFinal.put("resguardante", s.get("resguardante"));
                } else if (!partidas.isEmpty()) {
                    Set<Object> resguardos = new LinkedHashSet<>();
                    for (Map<String, Object> ri : partidas) {
                        actualizar(c, "DELETE FROM resguardo_items WHERE id = ?", ri.get("id"));
                        h.partidasBorradas.add(ri);
                        resguardos.add(ri.get("resguardo_id"));
                    }
                    for (Object r : resguardos) {
                        if (fila(c, "SELECT id FROM resguardo_items WHERE resguardo_id = ? LIMIT 1", r) != null) continue;
                        Map<String, Object> antes = fila(c, "SELECT id, estado, observaciones FROM resguardos WHERE id = ?", r);
                        h.resguardosCancelados.add(antes);
                        actualizar(c, "UPDATE resguardos SET estado = 'CANCELADO', observaciones = coalesce(observaciones || ' ', '') || ? WHERE id = ?",
                            "Cancelado: su único bien estaba repetido y sigue como " + q.get("codigo") + ".", r);
                    }
                }

                if (!ponerFinal.isEmpty()) {
                    StringBuilder sql = new StringBuilder("UPDATE products SET ");
                    List<Object> valores = new ArrayList<>();
                    for (var e : ponerFinal.entrySet()) {
                        sql.append(e.getKey()).append(" = ?, ");
                        valores.add(e.getValue());
                        h.columnasQueda.add(e.getKey());
                    }
                    sql.append("updated_at = NOW() WHERE id = ?");
                    valores.add(quedaId);
                    actualizar(c, sql.toString(), valores.toArray());
                }

                for (String t : HISTORIAL) {
                    List<String> ids = new ArrayList<>();
                    for (Map<String, Object> r : filas(c, "SELECT id::text AS id" + ("movements".equals(t) ? ", motivo" : "")
                            + " FROM " + t + " WHERE producto_id = ?", saleId)) {
                        ids.add((String) r.get("id"));
                        if ("movements".equals(t)) h.motivos.put((String) r.get("id"), (String) r.get("motivo"));
                    }
                    if (ids.isEmpty()) continue;
                    h.movidos.put(t, ids);
                    actualizar(c, "UPDATE " + t + " SET producto_id = ? WHERE producto_id = ?", quedaId, saleId);
                }
                if (!h.motivos.isEmpty())
                    try (PreparedStatement ps = c.prepareStatement("UPDATE movements SET motivo = coalesce(motivo, '') || ? WHERE id = ANY(?)")) {
                        ps.setString(1, nota);
                        ps.setArray(2, c.createArrayOf("text", h.motivos.keySet().toArray(new String[0])));
                        ps.executeUpdate();
                    }

                if (actualizar(c, "DELETE FROM products WHERE id = ?", saleId) != 1)
                    throw new SQLException("No se pudo quitar " + s.get("codigo"));
                c.commit();
                return h;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    /** Puts back what {@link #juntar} changed. */
    public void deshacer(Huella h) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                insertar(c, "products", h.sale);
                for (var e : h.movidos.entrySet())
                    try (PreparedStatement ps = c.prepareStatement("UPDATE " + e.getKey() + " SET producto_id = ? WHERE id::text = ANY(?)")) {
                        ps.setString(1, h.saleId());
                        ps.setArray(2, c.createArrayOf("text", e.getValue().toArray(new String[0])));
                        ps.executeUpdate();
                    }
                for (var e : h.motivos.entrySet())
                    actualizar(c, "UPDATE movements SET motivo = ? WHERE id = ?", e.getValue(), e.getKey());
                for (Map<String, Object> ri : h.partidasMovidas)
                    actualizar(c, "UPDATE resguardo_items SET producto_id = ?, producto_codigo = ?, producto_nombre = ? WHERE id = ?",
                        ri.get("producto_id"), ri.get("producto_codigo"), ri.get("producto_nombre"), ri.get("id"));
                for (Map<String, Object> r : h.resguardosCancelados)
                    actualizar(c, "UPDATE resguardos SET estado = ?, observaciones = ? WHERE id = ?", r.get("estado"), r.get("observaciones"), r.get("id"));
                for (Map<String, Object> ri : h.partidasBorradas) insertar(c, "resguardo_items", ri);
                if (!h.columnasQueda.isEmpty()) {
                    StringBuilder sql = new StringBuilder("UPDATE products SET ");
                    List<Object> valores = new ArrayList<>();
                    for (String col : h.columnasQueda) {
                        sql.append(col).append(" = ?, ");
                        valores.add(h.quedaAntes.get(col));
                    }
                    sql.append("updated_at = NOW() WHERE id = ?");
                    valores.add(h.quedaId());
                    actualizar(c, sql.toString(), valores.toArray());
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    // ── JDBC ──────────────────────────────────────────────────────────────────

    private static Map<String, Object> fila(Connection c, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> l = filas(c, sql, params);
        return l.isEmpty() ? null : l.get(0);
    }

    private static List<Map<String, Object>> filas(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) m.put(md.getColumnLabel(i), rs.getObject(i));
                    out.add(m);
                }
                return out;
            }
        }
    }

    private static int actualizar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    /** A row back as it was read (its column names come from the database itself). */
    private static void insertar(Connection c, String tabla, Map<String, Object> fila) throws SQLException {
        List<String> cols = new ArrayList<>(fila.keySet());
        String sql = "INSERT INTO " + tabla + " (" + String.join(", ", cols) + ") VALUES ("
            + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < cols.size(); i++) ps.setObject(i + 1, fila.get(cols.get(i)));
            ps.executeUpdate();
        }
    }
}
