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
        TIPO_BIEN("tipo_bien", Producto::getTipoBien, Producto::setTipoBien),
        /** Not in Completar datos: only for «Unir lo escrito de dos formas». */
        MARCA("marca", Producto::getMarca, Producto::setMarca);

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

    /** A bien's «Folio fiscal» as it was and as it is to be. */
    public record CambioDeFolio(String id, String antes, String despues) {}

    /** Each bien takes {@code despues} in «Folio fiscal» only if it still says {@code antes}
     *  (spaces at the ends aside): what somebody changed since is left alone. Applies and undoes.
     *  @return the changes made */
    public List<CambioDeFolio> cambiarFolios(List<CambioDeFolio> cambios) throws SQLException {
        List<CambioDeFolio> hecho = new java.util.ArrayList<>();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            for (CambioDeFolio c : cambios) {
                var o = local.findProductoById(c.id());
                if (o.isEmpty()) continue;
                Producto p = o.get();
                if (p.getFolioFiscal() == null || !p.getFolioFiscal().trim().equals(c.antes().trim())) continue;
                p.setFolioFiscal(c.despues());
                local.saveProducto(p);
                hecho.add(c);
            }
            return hecho;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET folio_fiscal = ?, updated_at = NOW() WHERE id = ? AND btrim(folio_fiscal) = btrim(?)")) {
            for (CambioDeFolio c : cambios) {
                ps.setString(1, c.despues());
                ps.setString(2, c.id());
                ps.setString(3, c.antes());
                ps.addBatch();
            }
            int[] r = ps.executeBatch();
            for (int i = 0; i < r.length; i++) if (r[i] > 0 || r[i] == java.sql.Statement.SUCCESS_NO_INFO) hecho.add(cambios.get(i));
            return hecho;
        }
    }

    /** Every bien whose proveedor is written exactly {@code de} takes {@code a}.
     *  @return the ids that changed, to undo it */
    public List<String> cambiarProveedor(String de, String a) throws SQLException {
        return cambiarTexto(CampoTexto.PROVEEDOR, de, a);
    }

    /** Undo of {@link #cambiarProveedor}: {@code de} back on those bienes that still say {@code a}. */
    public int regresarProveedor(List<String> ids, String de, String a) throws SQLException {
        return regresarTexto(CampoTexto.PROVEEDOR, ids, de, a);
    }

    /** Every bien whose {@code campo} is written exactly {@code de} takes {@code a}
     *  («Unir lo escrito de dos formas»). @return the ids that changed, to undo it */
    public List<String> cambiarTexto(CampoTexto campo, String de, String a) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        List<String> ids = new java.util.ArrayList<>();
        if (local != null) {
            for (Producto p : local.findAllProductos(null)) {
                if (!de.equals(campo.leer.apply(p))) continue;
                campo.poner.accept(p, a);
                local.saveProducto(p);
                ids.add(p.getId());
            }
            return ids;
        }
        // The column comes from the enum, never from what was typed.
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET " + campo.columna + " = ?, updated_at = NOW() WHERE " + campo.columna + " = ? RETURNING id")) {
            ps.setString(1, a);
            ps.setString(2, de);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    /** Undo of {@link #cambiarTexto}: {@code de} back on those bienes that still say {@code a}. */
    public int regresarTexto(CampoTexto campo, List<String> ids, String de, String a) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : ids) {
                var o = local.findProductoById(id);
                if (o.isEmpty() || !a.equals(campo.leer.apply(o.get()))) continue;
                campo.poner.accept(o.get(), de);
                local.saveProducto(o.get());
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET " + campo.columna + " = ?, updated_at = NOW() WHERE id = ANY(?) AND " + campo.columna + " = ?")) {
            ps.setString(1, de);
            ps.setArray(2, conn.createArrayOf("text", ids.toArray(new String[0])));
            ps.setString(3, a);
            return ps.executeUpdate();
        }
    }

    /** Undo of {@link #asignarTextoIds}: empty again on those bienes that still say {@code valor}. */
    public int quitarTexto(CampoTexto campo, String valor, List<String> ids) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : ids) {
                var o = local.findProductoById(id);
                if (o.isEmpty() || !valor.equals(campo.leer.apply(o.get()))) continue;
                campo.poner.accept(o.get(), null);
                local.saveProducto(o.get());
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET " + campo.columna + " = NULL, updated_at = NOW() WHERE id = ANY(?) AND " + campo.columna + " = ?")) {
            ps.setArray(1, conn.createArrayOf("text", ids.toArray(new String[0])));
            ps.setString(2, valor);
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

    /** {@code porcentaje} % of its costo as valor residual, to the bienes in force with a costo
     *  and the residual at $0 (one with its own keeps it). @return the ids that took it */
    public List<String> ponerResidual(List<String> bienes, java.math.BigDecimal porcentaje) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<String> hechos = new java.util.ArrayList<>();
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty()) continue;
                Producto p = o.get();
                if (p.getFechaBaja() != null || p.getPrecioCompra() == null || p.getPrecioCompra().signum() <= 0
                        || (p.getValorResidual() != null && p.getValorResidual().signum() != 0)) continue;
                p.setValorResidual(delCosto(p.getPrecioCompra(), porcentaje));
                local.saveProducto(p);
                hechos.add(id);
            }
            return hechos;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET valor_residual = round(precio_compra * ? / 100, 2), updated_at = NOW() "
                     + "WHERE id = ANY(?) AND fecha_baja IS NULL AND precio_compra > 0 "
                     + "AND (valor_residual IS NULL OR valor_residual = 0) RETURNING id")) {
            ps.setBigDecimal(1, porcentaje);
            ps.setArray(2, conn.createArrayOf("text", bienes.toArray(new String[0])));
            List<String> hechos = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) hechos.add(rs.getString(1));
            }
            return hechos;
        }
    }

    /** Undo of {@link #ponerResidual}: back to $0, only on the bienes still at {@code porcentaje} %
     *  of their costo. @return how many went back */
    public int quitarResidual(List<String> bienes, java.math.BigDecimal porcentaje) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            int n = 0;
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty()) continue;
                Producto p = o.get();
                java.math.BigDecimal era = p.getPrecioCompra() == null ? null : delCosto(p.getPrecioCompra(), porcentaje);
                if (era == null || p.getValorResidual() == null || p.getValorResidual().compareTo(era) != 0) continue;
                p.setValorResidual(java.math.BigDecimal.ZERO);
                local.saveProducto(p);
                n++;
            }
            return n;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET valor_residual = 0, updated_at = NOW() "
                     + "WHERE id = ANY(?) AND valor_residual = round(precio_compra * ? / 100, 2)")) {
            ps.setArray(1, conn.createArrayOf("text", bienes.toArray(new String[0])));
            ps.setBigDecimal(2, porcentaje);
            return ps.executeUpdate();
        }
    }

    private static java.math.BigDecimal delCosto(java.math.BigDecimal costo, java.math.BigDecimal porcentaje) {
        return costo.multiply(porcentaje).divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
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
        return asignarTextoIds(campo, valor, bienes).size();
    }

    /** {@link #asignarTexto}, saying which took it (to undo it with {@link #quitarTexto}). */
    public List<String> asignarTextoIds(CampoTexto campo, String valor, List<String> bienes) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        List<String> ids = new java.util.ArrayList<>();
        if (local != null) {
            for (String id : bienes) {
                var o = local.findProductoById(id);
                if (o.isEmpty()) continue;
                String actual = campo.leer.apply(o.get());
                if (actual != null && !actual.isBlank()) continue;
                Producto p = o.get();
                campo.poner.accept(p, valor);
                local.saveProducto(p);
                ids.add(id);
            }
            return ids;
        }
        // The column comes from the enum, never from what was typed.
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE products SET " + campo.columna + " = ?, updated_at = NOW() WHERE id = ANY(?) AND ("
                     + campo.columna + " IS NULL OR btrim(" + campo.columna + ") = '') RETURNING id")) {
            ps.setString(1, valor);
            ps.setArray(2, conn.createArrayOf("text", bienes.toArray(new String[0])));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        }
        return ids;
    }
}
