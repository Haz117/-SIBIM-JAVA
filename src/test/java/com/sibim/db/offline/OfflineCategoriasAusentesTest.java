package com.sibim.db.offline;

import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A category deleted on the server went on being offered while working offline,
 *  and a bien saved with it could never be sent. Refreshing the copy with the
 *  server's catalogue now forgets it — unless this PC still has a change to it
 *  waiting to be sent, or a bien of the copy uses it. */
class OfflineCategoriasAusentesTest {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
    private final String vigente = "catausente-vigente-" + sufijo;
    private final String borrada = "catausente-borrada-" + sufijo;
    private final String pendiente = "catausente-pendiente-" + sufijo;
    private final String enUso = "catausente-enuso-" + sufijo;
    private final String bien = "catausente-bien-" + sufijo;

    @AfterEach
    void limpiar() throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {
            st.executeUpdate("DELETE FROM products WHERE id = '" + bien + "'");
            st.executeUpdate("DELETE FROM categories WHERE id LIKE 'catausente-%-" + sufijo + "'");
            st.executeUpdate("DELETE FROM category_outbox WHERE categoria_id LIKE 'catausente-%-" + sufijo + "'");
        }
        OfflineStore.invalidateCache();
    }

    @Test
    void laQueElServidorBorro_seQuitaDeLaCopia_salvoPendienteOEnUso() throws Exception {
        for (String id : List.of(vigente, borrada, pendiente, enUso)) copiar(categoria(id));
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "INSERT INTO category_outbox (operacion, categoria_id, nombre, created_at, status) "
                + "VALUES ('SAVE', ?, 'x', ?, 'PENDING')")) {
            ps.setString(1, pendiente);
            ps.setString(2, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
        copiarBien(enUso);
        OfflineStore.invalidateCache();

        // What the server answers: the whole catalogue this copy has, minus the three gone there.
        List<Categoria> enServidor = OfflineStore.findAllCategorias().stream()
            .filter(c -> !Set.of(borrada, pendiente, enUso).contains(c.getId()))
            .toList();
        sinEspera();
        OfflineStore.cacheCategorias(enServidor);

        assertTrue(existe(vigente));
        assertFalse(existe(borrada), "el servidor ya no la tiene");
        assertTrue(existe(pendiente), "tiene un cambio de esta PC sin enviar: no se toca");
        assertTrue(existe(enUso), "un bien de la copia la usa: no se toca");
        assertTrue(OfflineStore.findAllCategorias().stream().noneMatch(c -> c.getId().equals(borrada)),
            "la lista en memoria tampoco la ofrece");
    }

    /** cacheCategorias skips a refresh that comes seconds after the previous one. */
    private static void sinEspera() throws Exception {
        Field f = OfflineStore.class.getDeclaredField("lastCacheCategoriasAt");
        f.setAccessible(true);
        f.setLong(null, 0L);
    }

    private static void copiar(Categoria c) throws Exception {
        Method snapshot = OfflineStore.class.getDeclaredMethod("cacheCategoriaSnapshot", Categoria.class);
        snapshot.setAccessible(true);
        snapshot.invoke(null, c);
    }

    private void copiarBien(String categoriaId) throws Exception {
        Producto p = new Producto();
        p.setId(bien);
        p.setNombre("Silla " + bien);
        p.setCodigo("CAT/" + sufijo);
        p.setCategoriaId(categoriaId);
        p.setArea("Tesorería Municipal");
        p.setPrecioCompra(new BigDecimal("900"));
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setCreadoEn(LocalDateTime.now());
        p.setActualizadoEn(LocalDateTime.now());
        Method snapshot = OfflineStore.class.getDeclaredMethod("cacheProductoSnapshot", Producto.class);
        snapshot.setAccessible(true);
        snapshot.invoke(null, p);
    }

    private static boolean existe(String id) throws Exception {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT 1 FROM categories WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Categoria categoria(String id) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre("Categoría " + id);
        c.setColor("#A2232D");
        c.setIcono("tag");
        c.setCreadoEn(LocalDateTime.now());
        return c;
    }
}
