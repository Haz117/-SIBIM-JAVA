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

/** The número a baja frees is given to the next bien of the área, so a bien
 *  de baja and an active one can share a código. The copy of the server this
 *  PC keeps for working offline must hold both: opening "Dados de baja" used
 *  to erase one of them from it. */
class OfflineCodigoReutilizadoTest {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
    private final String codigo = "REUSO/" + sufijo;
    private final String deBaja = "reuso-baja-" + sufijo;
    private final String activo = "reuso-activo-" + sufijo;
    private final String activoViejo = "reuso-viejo-" + sufijo;

    @AfterEach
    void limpiar() throws Exception {
        try (Statement st = OfflineStore.sharedConnection().createStatement()) {
            st.executeUpdate("DELETE FROM products WHERE codigo = '" + codigo + "'");
        }
        OfflineStore.invalidateCache();
    }

    @Test
    void elBienDeBajaYElActivoQueReusaSuCodigo_quedanLosDos() throws Exception {
        copiar(bien(activo, null));
        copiar(bien(deBaja, LocalDate.of(2026, 1, 5)));
        assertTrue(existe(activo), "copiar el bien de baja no borra al activo con su mismo código");
        assertTrue(existe(deBaja));

        copiar(bien(activo, null));
        assertTrue(existe(deBaja), "copiar el activo no borra al bien de baja");
        assertTrue(existe(activo));
    }

    @Test
    void unActivoConElCodigoDeOtroActivoDesactualizado_loReemplaza() throws Exception {
        copiar(bien(activoViejo, null));
        copiar(bien(activo, null));

        assertTrue(existe(activo));
        assertFalse(existe(activoViejo), "en el servidor ese código ya es de otro bien: la fila vieja estorba");
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

    private Producto bien(String id, LocalDate fechaBaja) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Escritorio " + id);
        p.setCodigo(codigo);
        p.setCategoriaId("cat-x");
        p.setArea("Tesorería Municipal");
        p.setPrecioCompra(new BigDecimal("1500"));
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setFechaBaja(fechaBaja);
        if (fechaBaja != null) p.setMotivoBaja("Inservible");
        p.setCreadoEn(LocalDateTime.now());
        p.setActualizadoEn(LocalDateTime.now());
        return p;
    }
}
