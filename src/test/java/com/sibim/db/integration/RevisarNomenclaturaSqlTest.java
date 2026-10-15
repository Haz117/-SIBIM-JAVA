package com.sibim.db.integration;

import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.service.CategoriaService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** scripts/sql/revisar_nomenclatura.sql, the read-only check handed to run in
 *  Supabase: it must parse against the real schema, find nothing wrong after
 *  what the app does (alta, transferencia, baja) and find what is wrong. */
class RevisarNomenclaturaSqlTest extends IntegrationTestBase {

    private static final String CANINO = "Control Canino";      // CCAN
    private static final String PARQUE = "Parque Municipal";    // PARQ

    private Producto alta(String area, String categoriaId) throws Exception {
        Producto p = new Producto();
        p.setNombre("Bien " + System.nanoTime());
        p.setCategoriaId(categoriaId);
        p.setArea(area);
        p.setUnidad(UnidadMedida.PIEZA);
        p.setStockActual(1);
        p.setStockMaximo(1);
        p.setPrecioCompra(new BigDecimal("100.00"));
        return new ProductoService().save(p);
    }

    private static List<String> consultas() throws Exception {
        String sql = Files.readString(Path.of(System.getProperty("basedir", "."))
            .resolve("scripts/sql/revisar_nomenclatura.sql"), StandardCharsets.UTF_8);
        List<String> l = new ArrayList<>();
        for (String s : sql.replaceAll("--[^\\n]*", "").split(";"))
            if (!s.isBlank()) l.add(s.trim());
        return l;
    }

    @Test
    void trasAltasTransferenciasYBajas_noEncuentraNadaMal() throws Exception {
        Categoria c = new Categoria();
        c.setNombre("Mobiliario revisión");
        c.setColor("#A2232D");
        String cat = new CategoriaService().save(c).getId();
        Producto uno = alta(CANINO, cat), dos = alta(CANINO, cat);
        alta(CANINO, cat);
        new MovimientoService().registrar(uno.getId(), TipoMovimiento.TRANSFERENCIA, 1, "revisión", null, PARQUE);
        new ProductoService().darDeBaja(dos.getId(), "Obsoleto");
        Producto nuevo = alta(CANINO, cat);
        assertEquals("CCAN/01", nuevo.getCodigo(), "toma el número más bajo que quedó libre");

        List<String> q = consultas();
        assertEquals(7, q.size(), "siete consultas en el script");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            for (int i = 0; i < 6; i++)
                try (ResultSet rs = st.executeQuery(q.get(i))) {
                    assertFalse(rs.next(), "la consulta " + (i + 1) + " debería salir vacía");
                }
            try (ResultSet rs = st.executeQuery(q.get(6))) {
                boolean visto = false;
                while (rs.next()) {
                    if (!"CCAN".equals(rs.getString("prefijo"))) continue;
                    visto = true;
                    assertEquals(2, rs.getInt("activos"));
                    assertEquals(1, rs.getInt("de_baja"));
                    assertEquals(3, rs.getInt("numero_mas_alto"));
                    assertEquals(1, rs.getInt("huecos"), "el 02 quedó libre por la baja");
                }
                assertTrue(visto);
            }
        }
    }

    @Test
    void encuentraUnCodigoFueraDelPrefijoYUnoRepetido() throws Exception {
        Categoria c = new Categoria();
        c.setNombre("Equipo revisión");
        c.setColor("#A2232D");
        String cat = new CategoriaService().save(c).getId();
        Producto p = alta(CANINO, cat);
        Producto r = alta(PARQUE, cat);
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE products SET codigo = 'XX-9' WHERE id = '" + p.getId() + "'");
            st.executeUpdate("UPDATE products SET codigo = 'xx-9' WHERE id = '" + r.getId() + "'");
            List<String> q = consultas();
            try (ResultSet rs = st.executeQuery(q.get(0))) {
                int filas = 0;
                while (rs.next()) filas++;
                assertEquals(2, filas, "los dos códigos fuera de su prefijo");
            }
            try (ResultSet rs = st.executeQuery(q.get(2))) {
                assertTrue(rs.next(), "XX-9 y xx-9 son el mismo código");
            }
        }
    }
}
