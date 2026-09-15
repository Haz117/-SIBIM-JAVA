package com.sibim.service;

import com.sibim.model.Categoria;
import com.sibim.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock CategoriaRepository mockRepo;

    private CategoriaService service;

    @BeforeEach
    void setUp() {
        service = new CategoriaService(mockRepo);
    }

    // ── save() — validación ───────────────────────────────────────────────────

    @Test
    void save_nombreNulo_lanzaIllegalArgument() {
        Categoria c = new Categoria();
        c.setNombre(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(c));
    }

    @Test
    void save_nombreVacio_lanzaIllegalArgument() {
        Categoria c = new Categoria();
        c.setNombre("  ");
        assertThrows(IllegalArgumentException.class, () -> service.save(c));
    }

    @Test
    void save_nombreVacio_mensajeContieneNombre() {
        Categoria c = new Categoria();
        c.setNombre("");
        var ex = assertThrows(IllegalArgumentException.class, () -> service.save(c));
        assertTrue(ex.getMessage().toLowerCase().contains("nombre"));
    }

    // ── save() — éxito ────────────────────────────────────────────────────────

    @Test
    void save_nombreValido_llama_repoSave_yRetornaCategoria() throws Exception {
        Categoria guardada = new Categoria();
        guardada.setNombre("Equipos de Oficina");
        when(mockRepo.save(any(Categoria.class))).thenReturn(guardada);

        Categoria c = new Categoria();
        c.setNombre("Equipos de Oficina");
        Categoria resultado = service.save(c);

        assertSame(guardada, resultado);
        verify(mockRepo).save(c);
    }

    @Test
    void save_noLanzaExcepcionAntesDeLlamarRepo() throws Exception {
        Categoria c = new Categoria();
        c.setNombre("Mobiliario");
        when(mockRepo.save(any())).thenReturn(c);

        assertDoesNotThrow(() -> service.save(c));
    }

    // ── delete() — FK constraint ──────────────────────────────────────────────

    @Test
    void delete_fkConstraintViolation_lanzaIllegalState() throws Exception {
        SQLException fkEx = new SQLException("FK violation", "23503");
        doThrow(fkEx).when(mockRepo).delete("cat-01");

        assertThrows(IllegalStateException.class, () -> service.delete("cat-01"));
    }

    @Test
    void delete_fkConstraintViolation_mensajeMencionaBienes() throws Exception {
        doThrow(new SQLException("FK", "23503")).when(mockRepo).delete("cat-01");

        var ex = assertThrows(IllegalStateException.class, () -> service.delete("cat-01"));
        assertTrue(ex.getMessage().toLowerCase().contains("bienes")
                || ex.getMessage().toLowerCase().contains("eliminar"));
    }

    @Test
    void delete_otraSqlException_relanzaSqlException() throws Exception {
        SQLException otherEx = new SQLException("Timeout", "08001");
        doThrow(otherEx).when(mockRepo).delete("cat-01");

        var thrown = assertThrows(SQLException.class, () -> service.delete("cat-01"));
        assertSame(otherEx, thrown);
    }

    @Test
    void delete_sinError_llama_repoDelete() throws Exception {
        service.delete("cat-01");
        verify(mockRepo).delete("cat-01");
    }
}
