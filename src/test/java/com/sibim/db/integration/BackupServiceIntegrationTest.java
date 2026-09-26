package com.sibim.db.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibim.service.BackupEncryption;
import com.sibim.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Backup/restore against a real Postgres with every migration applied. */
class BackupServiceIntegrationTest extends IntegrationTestBase {

    private static final char[] PASSWORD = "respaldo-de-prueba".toCharArray();

    @TempDir Path tmp;

    private final BackupService service = new BackupService();

    @BeforeEach
    void seed() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM folios");
            st.execute("DELETE FROM filtros_guardados");
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6')");
            st.execute("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra) "
                + "VALUES ('prod-1', 'Escritorio', 'SGM/01', 'cat-1', 'Secretaría General Municipal', 'pieza', 1234.56)");
            st.execute("INSERT INTO product_fotos (id, producto_id, foto_url, orden) VALUES ('foto-1', 'prod-1', 'x.jpg', 0)");
            st.execute("INSERT INTO price_history (producto_id, campo, valor_anterior, valor_nuevo) "
                + "VALUES ('prod-1', 'precio_compra', 1000.10, 1234.56)");
            st.execute("INSERT INTO producto_mantenimiento (id, producto_id, descripcion, fecha) "
                + "VALUES ('mant-1', 'prod-1', 'Revisión', DATE '2026-12-01')");
            st.execute("INSERT INTO comodatos (id, numero, producto_id, producto_nombre, entidad_receptora, "
                + "contacto_nombre, fecha_inicio) VALUES ('com-1', 'COM-2026-001', 'prod-1', 'Escritorio', "
                + "'Escuela Primaria', 'Directora', DATE '2026-01-15')");
            st.execute("INSERT INTO folios (prefijo, anio, ultimo) VALUES ('COM', 2026, 1)");
            st.execute("INSERT INTO filtros_guardados (nombre, filtro_json) VALUES ('Mis bienes', '{}')");
            st.execute("INSERT INTO audit_log (id, entidad, entidad_id, accion, usuario_nombre) "
                + "VALUES ('audit-antes', 'producto', 'prod-1', 'crear', 'Admin Test')");
        }
    }

    @Test
    void cadaTablaDelEsquemaEstaEnElRespaldoOExcluidaAPropósito() throws SQLException {
        Set<String> enEsquema = new TreeSet<>();
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables "
                 + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
            while (rs.next()) enEsquema.add(rs.getString(1));
        }
        Set<String> cubiertas = new HashSet<>(BackupService.TABLAS);
        cubiertas.addAll(BackupService.TABLAS_EXCLUIDAS);
        Set<String> faltantes = new TreeSet<>(enEsquema);
        faltantes.removeAll(cubiertas);
        assertTrue(faltantes.isEmpty(),
            "Tablas nuevas sin decidir si van en el respaldo (BackupService.TABLAS o TABLAS_EXCLUIDAS): " + faltantes);
    }

    @Test
    void restaurarDevuelveLoQueSeBorróYConservaLaBitácora() throws Exception {
        File archivo = tmp.resolve("respaldo.sibim").toFile();
        service.backup(archivo, PASSWORD);

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM comodatos");
            st.execute("DELETE FROM product_fotos");
            st.execute("UPDATE products SET precio_compra = 1 WHERE id = 'prod-1'");
            st.execute("INSERT INTO audit_log (id, entidad, entidad_id, accion, usuario_nombre) "
                + "VALUES ('audit-despues', 'producto', 'prod-1', 'actualizar', 'Admin Test')");
        }

        service.restore(archivo, PASSWORD);

        assertEquals(1, count("comodatos"));
        assertEquals(1, count("product_fotos"));
        assertEquals(1, count("price_history"), "price_history (id UUID) debe restaurarse");
        assertEquals(1, count("producto_mantenimiento"));
        assertEquals(1, count("filtros_guardados"), "filtros_guardados (id UUID) debe restaurarse");
        assertEquals(1, count("folios"));
        assertEquals("1234.56", scalar("SELECT precio_compra::text FROM products WHERE id = 'prod-1'"));
        assertEquals("2026-12-01", scalar("SELECT fecha::text FROM producto_mantenimiento WHERE id = 'mant-1'"));
        assertEquals(1, count("audit_log WHERE id = 'audit-antes'"));
        assertEquals(1, count("audit_log WHERE id = 'audit-despues'"),
            "la bitácora nunca se borra al restaurar");
    }

    @Test
    void unRespaldoViejoQueNoIncluyeComodatosNoSeAplica() throws Exception {
        // Shape of a version-1 backup: only the original 11 tables.
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("version", 1);
        Map<String, List<Object>> tablas = new LinkedHashMap<>();
        for (String t : List.of("users", "categories", "products", "movements", "audit_log",
                "conteos_fisicos", "conteo_items", "resguardos", "resguardo_items", "prestamos",
                "actas_entrega_recepcion")) {
            tablas.put(t, List.of());
        }
        raiz.put("tablas", tablas);
        File archivo = tmp.resolve("viejo.sibim").toFile();
        Files.write(archivo.toPath(),
            BackupEncryption.encrypt(new ObjectMapper().writeValueAsBytes(raiz), PASSWORD));

        IOException ex = assertThrows(IOException.class, () -> service.restore(archivo, PASSWORD));
        assertTrue(ex.getMessage().contains("comodatos"), ex.getMessage());
        assertEquals(1, count("products"), "la restauración rechazada no debe tocar nada");
        assertEquals(1, count("comodatos"));
    }

    private int count(String tablaYFiltro) throws SQLException {
        return Integer.parseInt(scalar("SELECT COUNT(*) FROM " + tablaYFiltro));
    }

    private String scalar(String sql) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
