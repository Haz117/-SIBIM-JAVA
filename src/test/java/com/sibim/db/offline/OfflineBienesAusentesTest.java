package com.sibim.db.offline;

import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** After the inventory was reloaded on the server, the copy each PC keeps for
 *  working offline went on showing the old bienes next to the new ones: the
 *  copy only ever added and updated. It now forgets what the server no longer
 *  has — except a bien with a change of this PC still waiting to be sent. */
class OfflineBienesAusentesTest {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
    private final String vigente = "ausentes-vigente-" + sufijo;
    private final String borrado = "ausentes-borrado-" + sufijo;
    private final String conCambio = "ausentes-pendiente-" + sufijo;

    @AfterEach
    void limpiar() throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {
            st.executeUpdate("DELETE FROM products WHERE id LIKE 'ausentes-%-" + sufijo + "'");
            st.executeUpdate("DELETE FROM product_outbox WHERE producto_id LIKE 'ausentes-%-" + sufijo + "'");
        }
        OfflineStore.invalidateCache();
    }

    @Test
    void loQueElServidorYaNoTiene_seQuitaDeLaCopia_salvoLoPendienteDeEnviar() throws Exception {
        copiar(bien(vigente));
        copiar(bien(borrado));
        copiar(bien(conCambio));
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, created_at, status) "
                + "VALUES ('SAVE', ?, 'x', ?, ?, 'PENDING')")) {
            ps.setString(1, conCambio);
            ps.setString(2, "AUS/" + sufijo);
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
        }

        // What the server answers: everything this copy has, minus the two that are gone there.
        Set<String> enServidor = todosLosDeLaCopia();
        enServidor.remove(borrado);
        enServidor.remove(conCambio);

        assertEquals(1, OfflineStore.olvidarBienesAusentes(enServidor));
        assertTrue(existe(vigente));
        assertFalse(existe(borrado), "el servidor ya no lo tiene");
        assertTrue(existe(conCambio), "tiene un cambio de esta PC sin enviar: no se toca");
    }

    @Test
    void unaRespuestaVacia_noBorraLaCopia() throws Exception {
        copiar(bien(vigente));
        assertEquals(0, OfflineStore.olvidarBienesAusentes(Set.of()));
        assertEquals(0, OfflineStore.olvidarBienesAusentes(null));
        assertTrue(existe(vigente));
    }

    private static Set<String> todosLosDeLaCopia() throws Exception {
        Set<String> ids = new HashSet<>();
        try (Statement st = OfflineStore.sharedConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM products")) {
            while (rs.next()) ids.add(rs.getString(1));
        }
        return ids;
    }

    private static void copiar(Producto p) throws Exception {
        Method snapshot = OfflineStore.class.getDeclaredMethod("cacheProductoSnapshot", Producto.class);
        snapshot.setAccessible(true);
        snapshot.invoke(null, p);
    }

    private static boolean existe(String id) throws Exception {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT 1 FROM products WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Producto bien(String id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Escritorio " + id);
        p.setCodigo("AUS/" + id);
        p.setCategoriaId("cat-x");
        p.setArea("Tesorería Municipal");
        p.setPrecioCompra(new BigDecimal("1500"));
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setCreadoEn(LocalDateTime.now());
        p.setActualizadoEn(LocalDateTime.now());
        return p;
    }
}
