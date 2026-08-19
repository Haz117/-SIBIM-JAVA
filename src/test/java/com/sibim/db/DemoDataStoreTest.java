package com.sibim.db;

import com.sibim.model.Categoria;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DemoDataStoreTest {

    // ── verifyDemoPassword ────────────────────────────────────────────────────

    @Test
    void verifyDemoPassword_superusuario_correcto_retornaTrue() {
        assertTrue(DemoDataStore.verifyDemoPassword("superusuario", "admin123456"));
    }

    @Test
    void verifyDemoPassword_passwordIncorrecto_retornaFalse() {
        assertFalse(DemoDataStore.verifyDemoPassword("superusuario", "mal"));
    }

    @Test
    void verifyDemoPassword_usuarioInexistente_retornaFalse() {
        assertFalse(DemoDataStore.verifyDemoPassword("fantasma", "cualquier"));
    }

    @Test
    void verifyDemoPassword_todosLosUsuariosDemo() {
        assertTrue(DemoDataStore.verifyDemoPassword("secretario.demo",     "sec123456"));
        assertTrue(DemoDataStore.verifyDemoPassword("direccion.demo",      "dir123456"));
        assertTrue(DemoDataStore.verifyDemoPassword("secretario.finanzas", "fin123456"));
        assertTrue(DemoDataStore.verifyDemoPassword("director.obras",      "obras1234"));
        assertTrue(DemoDataStore.verifyDemoPassword("director.seguridad",  "seg123456"));
    }

    // ── findUserByUsername ────────────────────────────────────────────────────

    @Test
    void findUserByUsername_superusuario_retornaAdmin() {
        Optional<Usuario> u = DemoDataStore.findUserByUsername("superusuario");
        assertTrue(u.isPresent());
        assertEquals(Rol.ADMIN, u.get().getRol());
        assertNull(u.get().getArea()); // admin tiene área null
    }

    @Test
    void findUserByUsername_insensibleMayusculas() {
        assertTrue(DemoDataStore.findUserByUsername("SUPERUSUARIO").isPresent());
        assertTrue(DemoDataStore.findUserByUsername("Secretario.Demo").isPresent());
    }

    @Test
    void findUserByUsername_inexistente_retornaEmpty() {
        assertTrue(DemoDataStore.findUserByUsername("nadie").isEmpty());
    }

    @Test
    void findAllUsers_retorna6Usuarios() {
        assertEquals(6, DemoDataStore.findAllUsers().size());
    }

    @Test
    void findAllUsers_ninguno_debeCambiarPassword() {
        DemoDataStore.findAllUsers().forEach(u ->
            assertFalse(u.isDebeCambiarPassword(),
                "Usuario " + u.getUsername() + " no debería tener el flag debeCambiarPassword"));
    }

    // ── findAllCategorias ─────────────────────────────────────────────────────

    @Test
    void findAllCategorias_retorna6Categorias() {
        List<Categoria> cats = DemoDataStore.findAllCategorias();
        assertEquals(6, cats.size());
    }

    @Test
    void findAllCategorias_totalProductosMayorACero() {
        DemoDataStore.findAllCategorias().forEach(c ->
            assertTrue(c.getTotalProductos() > 0,
                "Categoría " + c.getNombre() + " debe tener al menos 1 producto"));
    }

    // ── findAllProductos ──────────────────────────────────────────────────────

    @Test
    void findAllProductos_adminNull_retornaTodasLasEntradas() {
        List<Producto> productos = DemoDataStore.findAllProductos(null);
        assertFalse(productos.isEmpty());
        // Incluye dado de baja — el filtro se aplica en el Repository, no aquí
        assertTrue(productos.stream().anyMatch(Producto::isDadoDeBaja));
    }

    @Test
    void findAllProductos_ordenadosPorNombre() {
        List<Producto> productos = DemoDataStore.findAllProductos(null);
        for (int i = 0; i < productos.size() - 1; i++) {
            assertTrue(productos.get(i).getNombre()
                .compareTo(productos.get(i + 1).getNombre()) <= 0);
        }
    }

    @Test
    void findAllProductos_areaFiltrada_retornaSoloEsaArea() {
        String area = "Secretaria General Municipal";
        List<Producto> filtrados = DemoDataStore.findAllProductos(Set.of(area));
        assertFalse(filtrados.isEmpty());
        filtrados.forEach(p -> assertEquals(area, p.getArea()));
    }

    @Test
    void findAllProductos_conjuntoVacio_retornaVacio() {
        assertTrue(DemoDataStore.findAllProductos(Set.of()).isEmpty());
    }

    @Test
    void findProductoById_existente_retornaProducto() {
        Optional<Producto> p = DemoDataStore.findProductoById("p-01");
        assertTrue(p.isPresent());
        assertEquals("MB-001", p.get().getCodigo());
    }

    @Test
    void findProductoById_inexistente_retornaEmpty() {
        assertTrue(DemoDataStore.findProductoById("no-existe").isEmpty());
    }

    @Test
    void findProductoByCodigo_insensibleMayusculas() {
        assertTrue(DemoDataStore.findProductoByCodigo("mb-001").isPresent());
        assertTrue(DemoDataStore.findProductoByCodigo("MB-001").isPresent());
    }

    // ── Estados calculados ────────────────────────────────────────────────────

    @Test
    void producto_extintor_p45_estaVencido() {
        Optional<Producto> p = DemoDataStore.findProductoById("p-45");
        assertTrue(p.isPresent());
        assertNotNull(p.get().getFechaVencimiento());
        assertTrue(p.get().getFechaVencimiento().isBefore(LocalDate.now()));
        assertEquals(EstadoProducto.VENCIDO, p.get().getEstado());
    }

    @Test
    void producto_fax_p46_estaDadoDeBaja() {
        Optional<Producto> p = DemoDataStore.findProductoById("p-46");
        assertTrue(p.isPresent());
        assertTrue(p.get().isDadoDeBaja());
        assertNotNull(p.get().getMotivoBaja());
    }

    @Test
    void productos_conStockCero_sonAgotados() {
        DemoDataStore.findAllProductos(null).stream()
            .filter(p -> p.getStockActual() == 0 && p.getFechaVencimiento() == null && !p.isDadoDeBaja())
            .forEach(p -> assertEquals(EstadoProducto.AGOTADO, p.getEstado(),
                p.getNombre() + " con stock 0 debe ser AGOTADO"));
    }

    @Test
    void existsByCodigo_codigoExistente_retornaTrue() {
        assertTrue(DemoDataStore.existsByCodigo("MB-001", null));
    }

    @Test
    void existsByCodigo_excluyendoElMismoId_retornaFalse() {
        // El producto p-01 tiene código MB-001; excluirlo no debe encontrar duplicados
        assertFalse(DemoDataStore.existsByCodigo("MB-001", "p-01"));
    }

    @Test
    void existsByCodigo_codigoInexistente_retornaFalse() {
        assertFalse(DemoDataStore.existsByCodigo("XX-999", null));
    }

    // ── findAllMovimientos ────────────────────────────────────────────────────

    @Test
    void findAllMovimientos_adminNull_retorna51Movimientos() {
        assertEquals(51, DemoDataStore.findAllMovimientos(null).size());
    }

    @Test
    void findAllMovimientos_ordenadosMasRecientesPrimero() {
        List<Movimiento> movs = DemoDataStore.findAllMovimientos(null);
        for (int i = 0; i < movs.size() - 1; i++) {
            assertFalse(movs.get(i).getCreadoEn().isBefore(movs.get(i + 1).getCreadoEn()),
                "Movimientos deben estar del más reciente al más antiguo");
        }
    }

    @Test
    void findAllMovimientos_areaFiltrada_retornaSoloMovimientosDeEsaArea() {
        List<Movimiento> todos = DemoDataStore.findAllMovimientos(null);
        List<Movimiento> filtrados = DemoDataStore.findAllMovimientos(
            Set.of("Secretaria General Municipal"));
        assertTrue(filtrados.size() < todos.size());
    }

    @Test
    void findMovimientosByProducto_retornaMovimientosDelProducto() {
        // p-08 (cámara) tiene varios movimientos
        List<Movimiento> movs = DemoDataStore.findMovimientosByProducto("p-08", null);
        assertFalse(movs.isEmpty());
        movs.forEach(m -> assertEquals("p-08", m.getProductoId()));
    }

    @Test
    void findMovimientosByDateRange_filtraPorFecha() {
        LocalDate hoy = LocalDate.now();
        List<Movimiento> recientes = DemoDataStore.findMovimientosByDateRange(
            hoy.minusDays(3), hoy, null);
        List<Movimiento> todos = DemoDataStore.findAllMovimientos(null);
        assertTrue(recientes.size() < todos.size());
        recientes.forEach(m ->
            assertFalse(m.getCreadoEn().toLocalDate().isBefore(hoy.minusDays(3))));
    }

    @Test
    void findProductoIdByMovimientoId_existente_retornaId() {
        // m-01 existe en el demo
        Optional<String> id = DemoDataStore.findProductoIdByMovimientoId("m-01");
        assertTrue(id.isPresent());
    }

    @Test
    void findProductoIdByMovimientoId_inexistente_retornaEmpty() {
        assertTrue(DemoDataStore.findProductoIdByMovimientoId("m-999").isEmpty());
    }

    // ── tieneProductosEnCategoria ─────────────────────────────────────────────

    @Test
    void tieneProductosEnCategoria_categoriaConProductos_retornaTrue() {
        assertTrue(DemoDataStore.tieneProductosEnCategoria("cat-mob"));
    }

    @Test
    void tieneProductosEnCategoria_categoriaInexistente_retornaFalse() {
        assertFalse(DemoDataStore.tieneProductosEnCategoria("cat-999"));
    }
}
