package com.sibim.db.integration;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.AreaService;
import com.sibim.service.ResguardoService;
import com.sibim.service.SepararArea;
import com.sibim.service.TransferenciaRapidaService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Separar en un área nueva…» against the real schema, as the Mercado Municipal left
 *  Reglamentos on 13-nov-2026: the área is made, Juan Carlos's bienes take MM/01… in
 *  a new resguardo of his with his cargo, his old resguardo is cancelled once empty,
 *  and what stays in Reglamentos is not touched. */
class SepararAreaIntegrationTest extends IntegrationTestBase {

    private static final String ORIGEN = "Reglamentos, Comercio y Espectáculos";

    @BeforeEach
    void datos() throws SQLException {
        Usuario u = new Usuario();
        u.setId("test-admin"); u.setUsername("admin-test"); u.setNombre("Admin Test"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            bien("j1", "ARCHIVERO", "RCE/01", "JUAN CARLOS FICTICIO CORTES", 1),
            bien("j2", "ESCRITORIO", "RCE/02", "JUAN CARLOS FICTICIO CORTES", 1),
            bien("j41", "BOCINA AMBIENTAL", "RCE/41", "JUAN CARLOS FICTICIO CORTES", 18),
            bien("v38", "ESCRITORIO METALICO", "RCE/38", "VICTOR PRUEBA MARTINEZ", 1),
            "INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_cargo, resguardante_area, estado) "
                + "VALUES ('r-177', 'RSG-2026-000177', 'JUAN CARLOS FICTICIO CORTES', 'TESORERO DEL MERCADO', '" + ORIGEN + "', 'ACTIVO')",
            "INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_cargo, resguardante_area, estado) "
                + "VALUES ('r-213', 'RSG-2026-000213', 'VICTOR PRUEBA MARTINEZ', 'DIRECTOR', '" + ORIGEN + "', 'ACTIVO')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i1', 'r-177', 'j1', 'ARCHIVERO', 'RCE/01')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i2', 'r-177', 'j2', 'ESCRITORIO', 'RCE/02')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i38', 'r-213', 'v38', 'ESCRITORIO METALICO', 'RCE/38')");
    }

    @AfterEach
    void quitarArea() throws SQLException {
        sql("DELETE FROM areas WHERE nombre = 'Mercado Municipal'");
        Areas.usar(AreaCatalog.PREDETERMINADO);
    }

    private static String bien(String id, String nombre, String codigo, String resguardante, int piezas) {
        return "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, precio_venta, stock_actual, resguardante) "
            + "VALUES ('" + id + "', '" + nombre + "', '" + codigo + "', 'cat-1', '" + ORIGEN + "', 'pieza', 1000, 1000, " + piezas + ", '" + resguardante + "')";
    }

    @Test
    void separarElMercado_creaElArea_yJuanCarlosSeLlevaSusBienesEnUnResguardoNuevo() throws Exception {
        Areas.usar(AreaCatalog.PREDETERMINADO);
        ProductoRepository productos = new ProductoRepository();
        ResguardoService resguardos = new ResguardoService();
        List<Producto> suyos = List.of(productos.findById("j41").orElseThrow(), productos.findById("j1").orElseThrow(),
            productos.findById("j2").orElseThrow());
        List<SepararArea.Grupo> grupos = SepararArea.agrupar(suyos, p -> {
            try { return resguardos.getByProductoId(p.getId()).stream().filter(Resguardo::isActivo).findFirst().orElse(null); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
        AreaCatalog.Entrada mercado = new AreaCatalog.Entrada("Mercado Municipal", AreaCatalog.Grupo.DIRECCION,
            "Secretaría General Municipal", "MM");

        SepararArea.Resultado r = SepararArea.aplicar(mercado, grupos, SepararArea.motivo("Mercado Municipal", ORIGEN),
            new AreaService()::guardar, new TransferenciaRapidaService());

        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals("MM|Secretaría General Municipal|DIRECCION",
            valor("SELECT prefijo || '|' || padre || '|' || grupo FROM areas WHERE nombre = 'Mercado Municipal'"));
        assertEquals("MM/01", valor("SELECT codigo FROM products WHERE id = 'j1'"), "en el orden de sus números");
        assertEquals("MM/02", valor("SELECT codigo FROM products WHERE id = 'j2'"));
        assertEquals("MM/03", valor("SELECT codigo FROM products WHERE id = 'j41'"), "el que no tenía resguardo también");
        assertEquals("3", valor("SELECT count(*) FROM products WHERE area = 'Mercado Municipal' AND resguardante = 'JUAN CARLOS FICTICIO CORTES'"));
        assertEquals("CANCELADO", valor("SELECT estado FROM resguardos WHERE id = 'r-177'"), "su resguardo viejo quedó vacío");
        assertEquals(1, r.resguardos().size());
        String nuevo = r.resguardos().get(0).getId();
        assertEquals("ACTIVO|Mercado Municipal|TESORERO DEL MERCADO|3", valor("SELECT estado || '|' || resguardante_area || '|' || resguardante_cargo"
            + " || '|' || (SELECT count(*) FROM resguardo_items i WHERE i.resguardo_id = r.id) FROM resguardos r WHERE id = '" + nuevo + "'"));
        assertEquals("RCE/38|" + ORIGEN, valor("SELECT codigo || '|' || area FROM products WHERE id = 'v38'"), "lo de Víctor se queda");
        assertEquals("ACTIVO", valor("SELECT estado FROM resguardos WHERE id = 'r-213'"));
        assertEquals("3", valor("SELECT count(*) FROM movements WHERE tipo = 'transferencia'"));
    }

    private void sql(String... sentencias) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String s : sentencias) st.execute(s);
        }
    }

    private String valor(String consulta) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(consulta)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
