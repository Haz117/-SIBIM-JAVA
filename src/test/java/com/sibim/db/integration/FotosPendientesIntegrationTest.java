package com.sibim.db.integration;

import com.sibim.service.FotosPendientesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FotosPendientesIntegrationTest extends IntegrationTestBase {

    @TempDir Path tmp;

    private final List<String> subidos = new ArrayList<>();
    private final FotosPendientesService service = new FotosPendientesService(new FotosPendientesService.Subidor() {
        @Override public boolean disponible() { return true; }
        @Override public String subir(File f, String nombre) {
            subidos.add(nombre);
            return "https://storage.test/" + nombre;
        }
    });

    @BeforeEach
    void bien() throws Exception {
        subidos.clear();   // one instance per class (IntegrationTestBase)
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('cat', 'Cat', '#3B82F6')");
            st.execute("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad) "
                + "VALUES ('p1', 'Silla', 'X/01', 'cat', 'A', 'pieza')");
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection c = getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void subeLasFotosYFacturasQueEstanEnEstaPc_yReenlazaLaBase() throws Exception {
        Path foto = Files.writeString(tmp.resolve("foto.jpg"), "jpg");
        Path factura = Files.writeString(tmp.resolve("factura.jpg"), "jpg");
        String f = foto.toString().replace("'", "''");
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE products SET foto_url = '" + f + "', factura_url = '"
                + factura.toString().replace("'", "''") + "' WHERE id = 'p1'");
            st.execute("INSERT INTO product_fotos (id, producto_id, foto_url, orden) VALUES ('f1', 'p1', '" + f + "', 0)");
        }

        assertEquals(2, service.subirPendientes());

        assertEquals("https://storage.test/p1_f1.jpg", scalar("SELECT foto_url FROM product_fotos WHERE id = 'f1'"));
        assertEquals("https://storage.test/p1_f1.jpg", scalar("SELECT foto_url FROM products WHERE id = 'p1'"),
            "la foto principal sigue a la primera foto");
        assertEquals("https://storage.test/p1_factura.jpg", scalar("SELECT factura_url FROM products WHERE id = 'p1'"));
        assertEquals(0, service.subirPendientes(), "ya no queda nada pendiente");
    }

    @Test
    void unaRutaDeOtraPc_seQuedaParaQueEsaPcLaSuba() throws Exception {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO product_fotos (id, producto_id, foto_url, orden) "
                + "VALUES ('f1', 'p1', 'C:/Users/otra-pc/.sibim/img/x.jpg', 0)");
        }
        assertEquals(0, service.subirPendientes());
        assertTrue(subidos.isEmpty());
        assertEquals("C:/Users/otra-pc/.sibim/img/x.jpg", scalar("SELECT foto_url FROM product_fotos WHERE id = 'f1'"));
    }

    @Test
    void fotoPrincipalSinFilaEnProductFotos_tambienSeSube() throws Exception {
        Path foto = Files.writeString(tmp.resolve("offline.jpg"), "jpg");
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE products SET foto_url = '" + foto.toString().replace("'", "''") + "' WHERE id = 'p1'");
        }
        assertEquals(1, service.subirPendientes());
        assertEquals("https://storage.test/p1_principal.jpg", scalar("SELECT foto_url FROM products WHERE id = 'p1'"));
    }
}
