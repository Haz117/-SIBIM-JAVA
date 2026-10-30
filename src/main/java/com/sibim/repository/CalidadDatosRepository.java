package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
import com.sibim.model.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** The corrections that touch many rows at once, in one round-trip: going through
 *  ProductoRepository bien by bien is several queries each, minutes for the inventory. */
public class CalidadDatosRepository {

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
}
