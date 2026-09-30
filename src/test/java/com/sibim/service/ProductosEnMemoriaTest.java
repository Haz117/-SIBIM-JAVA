package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProductosEnMemoriaTest {

    private final AtomicInteger consultas = new AtomicInteger();
    private final ProductosEnMemoria.Consulta consulta = () -> {
        consultas.incrementAndGet();
        Producto p = new Producto();
        p.setId("p" + consultas.get());
        return List.of(p);
    };

    @BeforeEach
    void setUp() {
        ProductosEnMemoria.activa = true;
        ProductosEnMemoria.invalidar();
        DatabaseConfig.setDemoMode(false);
        DatabaseConfig.setOfflineMode(false);
        SessionManager.setCurrentUser(usuario("u1"));
    }

    @AfterEach
    void tearDown() {
        ProductosEnMemoria.activa = false;
        ProductosEnMemoria.invalidar();
        SessionManager.logout();
    }

    @Test
    void segundaLectura_noVuelveAConsultar() throws Exception {
        ProductosEnMemoria.obtener(consulta);
        List<Producto> segunda = ProductosEnMemoria.obtener(consulta);
        assertEquals(1, consultas.get());
        assertEquals("p1", segunda.get(0).getId());
    }

    @Test
    void invalidar_obligaAConsultarDeNuevo() throws Exception {
        ProductosEnMemoria.obtener(consulta);
        ProductosEnMemoria.invalidar();
        assertEquals("p2", ProductosEnMemoria.obtener(consulta).get(0).getId());
    }

    @Test
    void otroUsuario_noVeLaListaDelAnterior() throws Exception {
        ProductosEnMemoria.obtener(consulta);
        SessionManager.setCurrentUser(usuario("u2"));
        ProductosEnMemoria.obtener(consulta);
        assertEquals(2, consultas.get(), "cada usuario tiene su propio alcance por área");
    }

    @Test
    void laCopiaEntregada_sePuedeModificarSinTocarLaCompartida() throws Exception {
        ProductosEnMemoria.obtener(consulta).clear();
        assertEquals(1, ProductosEnMemoria.obtener(consulta).size());
    }

    @Test
    void enModoDemo_siempreConsulta() throws Exception {
        DatabaseConfig.setDemoMode(true);
        try {
            ProductosEnMemoria.obtener(consulta);
            ProductosEnMemoria.obtener(consulta);
            assertEquals(2, consultas.get());
        } finally {
            DatabaseConfig.setDemoMode(false);
        }
    }

    private static Usuario usuario(String id) {
        Usuario u = new Usuario();
        u.setId(id); u.setUsername(id); u.setNombre(id); u.setRol(Rol.ADMIN);
        return u;
    }
}
