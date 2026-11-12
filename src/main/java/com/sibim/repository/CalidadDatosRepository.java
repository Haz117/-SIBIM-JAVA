package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
import com.sibim.model.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** The corrections that touch many rows at once, in one round-trip: going through
 *  ProductoRepository bien by bien is several queries each, minutes for the inventory. */
public class CalidadDatosRepository {

    /** A name for some bienes and resguardos (see {@link #renombrar}). */
    public record Renombre(String nombre, List<String> bienes, List<String> resguardos) {}

    /** Every one of these names, all or none (one transaction online). */
    public void renombrar(List<Renombre> cambios) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            for (Renombre c : cambios) unirPersona(c.nombre(), c.bienes(), c.resguardos());
            return;
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bienes = conn.prepareStatement(
                     "UPDATE products SET resguardante = ?, updated_at = NOW() WHERE id = ANY(?)");
                 PreparedStatement resguardos = conn.prepareStatement(
                     "UPDATE resguardos SET resguardante_nombre = ? WHERE id = ANY(?)")) {
                for (Renombre c : cambios) {
                    if (!c.bienes().isEmpty()) {
                        bienes.setString(1, c.nombre());
                        bienes.setArray(2, conn.createArrayOf("text", c.bienes().toArray(new String[0])));
                        bienes.executeUpdate();
                    }
                    if (!c.resguardos().isEmpty()) {
                        resguardos.setString(1, c.nombre());
                        resguardos.setArray(2, conn.createArrayOf("text", c.resguardos().toArray(new String[0])));
                        resguardos.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** One person under one name, in the bienes and in the resguardos given, or in neither. */
    public void unirPersona(String nombre, List<String> bienes, List<String> resguardos) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            for (String id : bienes) {
                var p = local.findProductoById(id);
                if (p.isPresent()) { p.get().setResguardante(nombre); local.saveProducto(p.get()); }
            }
            return;
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET resguardante = ?, updated_at = NOW() WHERE id = ANY(?)")) {
                    ps.setString(1, nombre);
                    ps.setArray(2, conn.createArrayOf("text", bienes.toArray(new String[0])));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE resguardos SET resguardante_nombre = ? WHERE id = ANY(?)")) {
                    ps.setString(1, nombre);
                    ps.setArray(2, conn.createArrayOf("text", resguardos.toArray(new String[0])));
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** These bienes without número de serie.
     *  @return how many changed */
    public int quitarSerie(List<String> bienes) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : bienes) {
                var p = local.findProductoById(id);
                if (p.isPresent()) { p.get().setNumeroSerie(null); local.saveProducto(p.get()); n++; }
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET numero_serie = NULL, updated_at = NOW() WHERE id = ANY(?)")) {
            ps.setArray(1, conn.createArrayOf("text", bienes.toArray(new String[0])));
            return ps.executeUpdate();
        }
    }

    /** Sets the vida útil of these bienes, except one that got its own meanwhile.
     *  @return how many took it */
    public int asignarVidaUtil(int anios, List<String> bienes) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty() || o.get().getVidaUtilAnios() != null) continue;
                Producto p = o.get();
                p.setVidaUtilAnios(anios);
                local.saveProducto(p);
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET vida_util_anios = ?, updated_at = NOW() WHERE id = ANY(?) AND vida_util_anios IS NULL")) {
            ps.setInt(1, anios);
            ps.setArray(2, conn.createArrayOf("text", bienes.toArray(new String[0])));
            return ps.executeUpdate();
        }
    }

    /** A text dato that several bienes take at once (Completar datos › «Poner a todos»). */
    public enum CampoTexto {
        PROVEEDOR("proveedor", Producto::getProveedor, Producto::setProveedor),
        FOLIO_FISCAL("folio_fiscal", Producto::getFolioFiscal, Producto::setFolioFiscal),
        FACTURA("numero_factura", Producto::getNumeroFactura, Producto::setNumeroFactura),
        COLOR("color", Producto::getColor, Producto::setColor),
        TIPO_BIEN("tipo_bien", Producto::getTipoBien, Producto::setTipoBien);

        final String columna;
        final java.util.function.Function<Producto, String> leer;
        final java.util.function.BiConsumer<Producto, String> poner;

        CampoTexto(String columna, java.util.function.Function<Producto, String> leer,
                   java.util.function.BiConsumer<Producto, String> poner) {
            this.columna = columna;
            this.leer = leer;
            this.poner = poner;
        }
    }

    /** Every bien whose proveedor is written exactly {@code de} takes {@code a}.
     *  @return the ids that changed, to undo it */
    public List<String> cambiarProveedor(String de, String a) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        List<String> ids = new java.util.ArrayList<>();
        if (local != null) {
            for (Producto p : local.findAllProductos(null)) {
                if (!de.equals(p.getProveedor())) continue;
                p.setProveedor(a);
                local.saveProducto(p);
                ids.add(p.getId());
            }
            return ids;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET proveedor = ?, updated_at = NOW() WHERE proveedor = ? RETURNING id")) {
            ps.setString(1, a);
            ps.setString(2, de);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    /** Undo of {@link #cambiarProveedor}: {@code de} back on those bienes that still say {@code a}. */
    public int regresarProveedor(List<String> ids, String de, String a) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : ids) {
                var o = local.findProductoById(id);
                if (o.isEmpty() || !a.equals(o.get().getProveedor())) continue;
                o.get().setProveedor(de);
                local.saveProducto(o.get());
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET proveedor = ?, updated_at = NOW() WHERE id = ANY(?) AND proveedor = ?")) {
            ps.setString(1, de);
            ps.setArray(2, conn.createArrayOf("text", ids.toArray(new String[0])));
            ps.setString(3, a);
            return ps.executeUpdate();
        }
    }

    /** A UUID, with or without its dashes, as {@link Producto#pareceFolioFiscal} says. */
    private static final String ES_UUID =
        "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32})$";

    /** The UUID that was typed in «Factura» goes to «Folio fiscal», on the bienes that still
     *  have it there and no folio of their own. @return how many moved */
    public int pasarFolioFiscal(List<String> bienes) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty()) continue;
                Producto p = o.get();
                if (!Producto.pareceFolioFiscal(p.getNumeroFactura()) || (p.getFolioFiscal() != null && !p.getFolioFiscal().isBlank())) continue;
                p.setFolioFiscal(p.getNumeroFactura().trim().toUpperCase(java.util.Locale.ROOT));
                p.setNumeroFactura(null);
                local.saveProducto(p);
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET folio_fiscal = upper(btrim(numero_factura)), numero_factura = NULL, updated_at = NOW() "
                     + "WHERE id = ANY(?) AND (folio_fiscal IS NULL OR btrim(folio_fiscal) = '') "
                     + "AND lower(btrim(numero_factura)) ~ '" + ES_UUID + "'")) {
            ps.setArray(1, conn.createArrayOf("text", bienes.toArray(new String[0])));
            return ps.executeUpdate();
        }
    }

    /** Undo of {@link #pasarFolioFiscal}: the UUID back in «Factura», only on the bienes
     *  still as it left them. @param facturaPorId what each bien had in «Factura» */
    public int regresarFolioFiscal(Map<String, String> facturaPorId) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (var e : facturaPorId.entrySet()) {
                var o = local.findProductoById(e.getKey());
                if (o.isEmpty()) continue;
                Producto p = o.get();
                if (p.getNumeroFactura() != null && !p.getNumeroFactura().isBlank()) continue;
                if (p.getFolioFiscal() == null || !p.getFolioFiscal().trim().equalsIgnoreCase(e.getValue().trim())) continue;
                p.setNumeroFactura(e.getValue());
                p.setFolioFiscal(null);
                local.saveProducto(p);
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET numero_factura = ?, folio_fiscal = NULL, updated_at = NOW() "
                     + "WHERE id = ? AND (numero_factura IS NULL OR btrim(numero_factura) = '') "
                     + "AND upper(btrim(folio_fiscal)) = upper(btrim(?))")) {
            for (var e : facturaPorId.entrySet()) {
                ps.setString(1, e.getValue());
                ps.setString(2, e.getKey());
                ps.setString(3, e.getValue());
                ps.addBatch();
            }
            int n = 0;
            for (int r : ps.executeBatch()) n += Math.max(r, 0);
            return n;
        }
    }

    /** Writes {@code valor} on these bienes, except one that got its own meanwhile.
     *  @return how many took it */
    public int asignarTexto(CampoTexto campo, String valor, List<String> bienes) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty()) continue;
                String actual = campo.leer.apply(o.get());
                if (actual != null && !actual.isBlank()) continue;
                Producto p = o.get();
                campo.poner.accept(p, valor);
                local.saveProducto(p);
                n++;
            }
            return n;
        }
        // The column comes from the enum, never from what was typed.
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET " + campo.columna + " = ?, updated_at = NOW() WHERE id = ANY(?) AND ("
                     + campo.columna + " IS NULL OR btrim(" + campo.columna + ") = '')")) {
            ps.setString(1, valor);
            ps.setArray(2, conn.createArrayOf("text", bienes.toArray(new String[0])));
            return ps.executeUpdate();
        }
    }
}
