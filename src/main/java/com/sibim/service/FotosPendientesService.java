package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.util.SupabaseStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Photos and facturas that ended up saved as a path on one PC — captured
 * offline, or when the upload to Storage failed — are invisible to every
 * other PC. On each online tick this uploads the ones whose file is on THIS
 * PC and points the database at the shared copy, so they converge without
 * anyone having to re-attach them.
 */
public class FotosPendientesService {

    private static final Logger log = LoggerFactory.getLogger(FotosPendientesService.class);

    /** Uploads at most this many per tick, so a backlog never stalls sync. */
    static final int LOTE = 20;

    /** Anything that decides where a file goes; Storage in the app, a fake in tests. */
    public interface Subidor {
        boolean disponible();
        String subir(File archivo, String nombreRemoto) throws Exception;
    }

    private final Subidor subidor;

    public FotosPendientesService() {
        this(new Subidor() {
            @Override public boolean disponible() { return SupabaseStorage.isAvailable(); }
            @Override public String subir(File f, String n) throws Exception { return SupabaseStorage.upload(f, n); }
        });
    }

    public FotosPendientesService(Subidor subidor) { this.subidor = subidor; }

    record Pendiente(String tabla, String id, String productoId, String ruta) {}

    /** @return how many files were uploaded and relinked. Never throws. */
    public int subirPendientes() {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode() || !subidor.disponible()) return 0;
        int subidas = 0;
        try {
            for (Pendiente p : buscar()) {
                if (subidas >= LOTE) break;
                File archivo = new File(p.ruta());
                if (!archivo.isFile()) continue;          // a path from another PC: that PC will do it
                try {
                    String sufijo = switch (p.tabla()) {
                        case "product_fotos" -> "_" + p.id();
                        case "products_foto" -> "_principal";
                        default -> "_factura";
                    };
                    String url = subidor.subir(archivo, p.productoId() + sufijo + ".jpg");
                    reenlazar(p, url);
                    subidas++;
                } catch (Exception e) {
                    log.debug("No se pudo subir {}: {}", p.ruta(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Revisión de fotos pendientes omitida: {}", e.getMessage());
        }
        if (subidas > 0) {
            log.info("Se subieron {} foto(s)/factura(s) que estaban solo en esta PC", subidas);
            ProductosEnMemoria.invalidar();
        }
        return subidas;
    }

    private static List<Pendiente> buscar() throws SQLException {
        List<Pendiente> l = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT 'product_fotos' AS tabla, id, producto_id, foto_url AS ruta FROM product_fotos
                  WHERE foto_url NOT LIKE 'http%'
                 UNION ALL
                 SELECT 'products_foto', p.id, p.id, p.foto_url FROM products p
                  WHERE p.foto_url IS NOT NULL AND p.foto_url <> '' AND p.foto_url NOT LIKE 'http%'
                    AND NOT EXISTS (SELECT 1 FROM product_fotos f WHERE f.producto_id = p.id AND f.foto_url = p.foto_url)
                 UNION ALL
                 SELECT 'products', id, id, factura_url FROM products
                  WHERE factura_url IS NOT NULL AND factura_url <> '' AND factura_url NOT LIKE 'http%'
                 """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                l.add(new Pendiente(rs.getString("tabla"), rs.getString("id"),
                    rs.getString("producto_id"), rs.getString("ruta")));
        }
        return l;
    }

    /** Points every row that referenced the local path at the uploaded copy. */
    private static void reenlazar(Pendiente p, String url) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            try {
                if ("product_fotos".equals(p.tabla())) {
                    actualizar(c, "UPDATE product_fotos SET foto_url = ? WHERE id = ?", url, p.id());
                    // products.foto_url mirrors the first photo.
                    actualizar(c, "UPDATE products SET foto_url = ?, updated_at = NOW() WHERE id = ? AND foto_url = ?",
                        url, p.productoId(), p.ruta());
                } else if ("products_foto".equals(p.tabla())) {
                    actualizar(c, "UPDATE products SET foto_url = ?, updated_at = NOW() WHERE id = ? AND foto_url = ?",
                        url, p.id(), p.ruta());
                } else {
                    actualizar(c, "UPDATE products SET factura_url = ?, updated_at = NOW() WHERE id = ? AND factura_url = ?",
                        url, p.id(), p.ruta());
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void actualizar(Connection c, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            ps.executeUpdate();
        }
    }
}
