package com.sibim.db.offline;

import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Depreciation / vehicle / inventario físico fields survive the offline
 *  mirror and the outbox (they used to be dropped, then synced as NULL). */
class OfflineProductoExtrasTest {

    private final String id = "extras-" + UUID.randomUUID();

    @AfterEach
    void limpiar() throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {
            st.executeUpdate("DELETE FROM products WHERE id = '" + id + "'");
            st.executeUpdate("DELETE FROM product_outbox WHERE producto_id = '" + id + "'");
        }
        OfflineStore.invalidateCache();
    }

    @Test
    void copiaDelServidor_conservaLosCamposExtendidos() throws Exception {
        Method snapshot = OfflineStore.class.getDeclaredMethod("cacheProductoSnapshot", Producto.class);
        snapshot.setAccessible(true);
        snapshot.invoke(null, bien());
        OfflineStore.invalidateCache();

        Producto leido = OfflineStore.findProductoById(id).orElseThrow();
        assertEquals(LocalDate.of(2019, 1, 10), leido.getFechaAdquisicion());
        assertEquals(8, leido.getVidaUtilAnios());
        assertEquals(0, new BigDecimal("250.50").compareTo(leido.getValorResidual()));
        assertEquals("MTR-1", leido.getNoMotor());
        assertEquals("REGULAR", leido.getEstadoFisico());
        assertNotNull(leido.getValorDepreciado(), "con estos datos Depreciación ya puede calcular sin conexión");
    }

    @Test
    void edicionOffline_losGuardaYLosEncola() throws Exception {
        OfflineStore.saveProducto(bien());
        OfflineStore.invalidateCache();

        assertEquals("Rojo", OfflineStore.findProductoById(id).orElseThrow().getColor());
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT fecha_adquisicion, vida_util_anios, color FROM product_outbox WHERE producto_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("2019-01-10", rs.getString("fecha_adquisicion"));
                assertEquals("8", rs.getString("vida_util_anios"));
                assertEquals("Rojo", rs.getString("color"));
            }
        }
    }

    private Producto bien() {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Camioneta de prueba");
        p.setCodigo("EXT-" + id.substring(id.length() - 6));
        p.setCategoriaId("cat-x");
        p.setArea("Parque Vehicular");
        p.setPrecioCompra(new BigDecimal("350000"));
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setFechaAdquisicion(LocalDate.of(2019, 1, 10));
        p.setVidaUtilAnios(8);
        p.setValorResidual(new BigDecimal("250.50"));
        p.setNoMotor("MTR-1");
        p.setColor("Rojo");
        p.setEstadoFisico("REGULAR");
        p.setCreadoEn(LocalDateTime.now());
        p.setActualizadoEn(LocalDateTime.now());
        return p;
    }
}
