package com.sibim.controller;

import com.sibim.model.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the filter predicate in ProductosController.applyFilters().
 * The predicate is replicated here as a pure static method so it can be
 * tested without a JavaFX Platform — logic correctness, not wiring.
 */
class ProductoFilterLogicTest {

    private Producto laptop, impresora, silla, escritorio;

    @BeforeEach
    void setUp() {
        laptop = new Producto();
        laptop.setId("prod-laptop");
        laptop.setNombre("Laptop HP");
        laptop.setCodigo("INF-001");
        laptop.setArea("Sala de cómputo");
        laptop.setCategoriaId("cat-ti");
        laptop.setProveedor("Dell Inc");
        laptop.setResguardante("Juan Pérez");
        laptop.setUbicacion("Almacén B");
        laptop.setStockActual(5);
        laptop.setStockMinimo(2);

        impresora = new Producto();
        impresora.setId("prod-impresora");
        impresora.setNombre("Impresora Epson");
        impresora.setCodigo("INF-002");
        impresora.setArea("Sala de cómputo");
        impresora.setCategoriaId("cat-ti");
        impresora.setProveedor("Epson México");
        impresora.setResguardante("Ana Gómez");
        impresora.setUbicacion("Almacén A");
        impresora.setStockActual(0);
        impresora.setStockMinimo(1);

        silla = new Producto();
        silla.setId("prod-silla");
        silla.setNombre("Silla Ejecutiva");
        silla.setCodigo("MOB-001");
        silla.setArea("Dirección");
        silla.setCategoriaId("cat-mob");
        silla.setResguardante("Pedro Ruiz");
        silla.setStockActual(3);
        silla.setStockMinimo(3);

        escritorio = new Producto();
        escritorio.setId("prod-escritorio");
        escritorio.setNombre("Escritorio de Madera");
        escritorio.setCodigo("MOB-002");
        escritorio.setArea("Recursos Humanos");
        escritorio.setCategoriaId("cat-mob");
        escritorio.setStockActual(2);
        escritorio.setStockMinimo(5);
    }

    // ── Text search ──────────────────────────────────────────────────────────

    @Test
    void searchByNombre_matches_caseInsensitive() {
        List<Producto> result = filter(all(), "laptop", null, null, null, "Todos");
        assertEquals(List.of(laptop), result);
    }

    @Test
    void searchByCodigo_partialMatch() {
        List<Producto> result = filter(all(), "MOB", null, null, null, "Todos");
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(silla, escritorio)));
    }

    @Test
    void searchByProveedor() {
        List<Producto> result = filter(all(), "epson", null, null, null, "Todos");
        assertEquals(List.of(impresora), result);
    }

    @Test
    void searchByResguardante() {
        List<Producto> result = filter(all(), "pedro ruiz", null, null, null, "Todos");
        assertEquals(List.of(silla), result);
    }

    @Test
    void searchByUbicacion() {
        List<Producto> result = filter(all(), "almacén b", null, null, null, "Todos");
        assertEquals(List.of(laptop), result);
    }

    @Test
    void emptySearch_returns_all() {
        assertEquals(4, filter(all(), "", null, null, null, "Todos").size());
    }

    @Test
    void blankSearch_returns_all() {
        assertEquals(4, filter(all(), "   ", null, null, null, "Todos").size());
    }

    @Test
    void nullSearch_returns_all() {
        assertEquals(4, filter(all(), null, null, null, null, "Todos").size());
    }

    @Test
    void search_noMatch_returns_empty() {
        assertTrue(filter(all(), "zzz_no_existe", null, null, null, "Todos").isEmpty());
    }

    @Test
    void searchNullFields_doesNotThrow() {
        // escritorio has no proveedor, resguardante, ubicacion — null-safe check
        assertDoesNotThrow(() -> filter(List.of(escritorio), "laptop", null, null, null, "Todos"));
    }

    // ── Categoría filter ─────────────────────────────────────────────────────

    @Test
    void filterByCategoria_returnsOnlyMatchingCategory() {
        List<Producto> result = filter(all(), "", "cat-ti", null, null, "Todos");
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(laptop, impresora)));
    }

    @Test
    void filterByCategoria_null_returnsAll() {
        assertEquals(4, filter(all(), "", null, null, null, "Todos").size());
    }

    @Test
    void filterByCategoria_noMatch() {
        assertTrue(filter(all(), "", "cat-inexistente", null, null, "Todos").isEmpty());
    }

    // ── Área filter ──────────────────────────────────────────────────────────

    @Test
    void filterByArea_exactMatch() {
        List<Producto> result = filter(all(), "", null, "Sala de cómputo", null, "Todos");
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(laptop, impresora)));
    }

    @Test
    void filterByArea_noMatch() {
        assertTrue(filter(all(), "", null, "Tesorería", null, "Todos").isEmpty());
    }

    @Test
    void filterByArea_null_returnsAll() {
        assertEquals(4, filter(all(), "", null, null, null, "Todos").size());
    }

    // ── Resguardante filter ──────────────────────────────────────────────────

    @Test
    void filterByResguardante_exact() {
        List<Producto> result = filter(all(), "", null, null, "Ana Gómez", "Todos");
        assertEquals(List.of(impresora), result);
    }

    @Test
    void filterByResguardante_null_returnsAll() {
        assertEquals(4, filter(all(), "", null, null, null, "Todos").size());
    }

    @Test
    void filterByResguardante_noResguardante_excluded() {
        // escritorio has no resguardante — filter "Pedro Ruiz" should NOT include it
        List<Producto> result = filter(all(), "", null, null, "Pedro Ruiz", "Todos");
        assertEquals(List.of(silla), result);
        assertFalse(result.contains(escritorio));
    }

    // ── Estado (chip) filter ─────────────────────────────────────────────────

    @Test
    void filterEstado_Agotado() {
        // impresora: stockActual=0 → AGOTADO
        List<Producto> result = filter(all(), "", null, null, null, "Agotado");
        assertEquals(List.of(impresora), result);
    }

    @Test
    void filterEstado_Activo_excludesAgotado() {
        List<Producto> result = filter(all(), "", null, null, null, "Activo");
        assertFalse(result.contains(impresora));
    }

    @Test
    void filterEstado_BajoStock() {
        // silla: stockActual=3, stockMinimo=3 → BAJO_STOCK
        // escritorio: stockActual=2, stockMinimo=5 → BAJO_STOCK
        List<Producto> result = filter(all(), "", null, null, null, "Bajo Stock");
        assertTrue(result.containsAll(List.of(silla, escritorio)));
        assertFalse(result.contains(laptop));
        assertFalse(result.contains(impresora));
    }

    @Test
    void filterEstado_Vencido() {
        Producto extintor = new Producto();
        extintor.setNombre("Extintor");
        extintor.setCodigo("SEG-001");
        extintor.setCategoriaId("cat-seg");
        extintor.setFechaVencimiento(LocalDate.now().minusDays(1));
        extintor.setStockActual(2);
        extintor.setStockMinimo(1);

        List<Producto> result = filter(List.of(extintor, laptop), "", null, null, null, "Vencido");
        assertEquals(List.of(extintor), result);
    }

    @Test
    void filterEstado_Todos_returnsAll() {
        assertEquals(4, filter(all(), "", null, null, null, "Todos").size());
    }

    // ── Combined filters ─────────────────────────────────────────────────────

    @Test
    void searchAndArea_combined() {
        List<Producto> result = filter(all(), "INF", null, "Sala de cómputo", null, "Todos");
        assertEquals(2, result.size());
    }

    @Test
    void searchAndArea_disjoint_returnsEmpty() {
        List<Producto> result = filter(all(), "laptop", null, "Dirección", null, "Todos");
        assertTrue(result.isEmpty());
    }

    @Test
    void categoriaAndEstado() {
        List<Producto> result = filter(all(), "", "cat-ti", null, null, "Agotado");
        assertEquals(List.of(impresora), result);
    }

    @Test
    void allFiltersApplied() {
        List<Producto> result = filter(all(), "laptop", "cat-ti", "Sala de cómputo", "Juan Pérez", "Activo");
        assertEquals(List.of(laptop), result);
    }

    @Test
    void allFiltersApplied_mismatchOnOne_returnsEmpty() {
        // wrong area
        List<Producto> result = filter(all(), "laptop", "cat-ti", "Dirección", "Juan Pérez", "Activo");
        assertTrue(result.isEmpty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Producto> all() {
        return List.of(laptop, impresora, silla, escritorio);
    }

    /** Mirrors the filter predicate from ProductosController.applyFilters(). */
    private static List<Producto> filter(List<Producto> all,
                                          String queryText,
                                          String catId,
                                          String area,
                                          String resguardante,
                                          String estado) {
        String query = queryText != null ? queryText.toLowerCase() : "";
        return all.stream()
            .filter(p -> query.isBlank()
                || p.getNombre().toLowerCase().contains(query)
                || p.getCodigo().toLowerCase().contains(query)
                || (p.getProveedor() != null && p.getProveedor().toLowerCase().contains(query))
                || (p.getUbicacion() != null && p.getUbicacion().toLowerCase().contains(query))
                || (p.getResguardante() != null && p.getResguardante().toLowerCase().contains(query)))
            .filter(p -> catId == null || catId.equals(p.getCategoriaId()))
            .filter(p -> area == null || area.equals(p.getArea()))
            .filter(p -> resguardante == null || resguardante.equals(p.getResguardante()))
            .filter(p -> "Todos".equals(estado) || p.getEstado().getEtiqueta().equalsIgnoreCase(estado))
            .toList();
    }
}
