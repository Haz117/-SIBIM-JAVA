package com.sibim.db.integration;

import com.sibim.repository.ConfiguracionRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionRepositoryIntegrationTest extends IntegrationTestBase {

    private final ConfiguracionRepository repo = new ConfiguracionRepository();

    @Test
    void set_nuevaClave_persisteEnDB() throws SQLException {
        repo.set("org_nombre", "Ayuntamiento Test");

        Map<String, String> all = repo.findAll();
        assertEquals("Ayuntamiento Test", all.get("org_nombre"));
    }

    @Test
    void set_actualizaValorExistente_noDuplica() throws SQLException {
        repo.set("color_tema", "azul");
        repo.set("color_tema", "verde");

        Map<String, String> all = repo.findAll();
        assertEquals("verde", all.get("color_tema"), "ON CONFLICT debe actualizar el valor");
        long count = all.entrySet().stream().filter(e -> "color_tema".equals(e.getKey())).count();
        assertEquals(1, count, "no debe haber filas duplicadas");
    }

    @Test
    void get_claveExistente_retornaValor() throws SQLException {
        repo.set("municipio", "Ixmiquilpan");

        String result = repo.get("municipio", "default");
        assertEquals("Ixmiquilpan", result);
    }

    @Test
    void get_claveDesconocida_retornaDefault() {
        String result = repo.get("clave_que_no_existe_xyz", "mi-default");
        assertEquals("mi-default", result);
    }

    @Test
    void findAll_retornaMultiplesClaves() throws SQLException {
        repo.set("k1", "v1");
        repo.set("k2", "v2");
        repo.set("k3", "v3");

        Map<String, String> all = repo.findAll();
        assertEquals("v1", all.get("k1"));
        assertEquals("v2", all.get("k2"));
        assertEquals("v3", all.get("k3"));
        assertTrue(all.size() >= 3);
    }

    @Test
    void set_valorNulo_guardaCadenaVacia() throws SQLException {
        repo.set("campo_vacio", null);

        String result = repo.get("campo_vacio", "noDefault");
        assertEquals("", result, "null debe guardarse como cadena vacía");
    }
}
