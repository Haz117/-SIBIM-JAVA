package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MantenimientoServiceTest {

    @Mock ConfiguracionRepository mockConfig;

    private MantenimientoService service;

    @BeforeEach
    void setUp() {
        service = new MantenimientoService(mockConfig);
    }

    // ── getAlertas() ─────────────────────────────────────────────────────────

    @Test
    void getAlertas_sinEntradas_retornaListaVacia() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("0");

        List<MantenimientoService.Alerta> result = service.getAlertas("p1");

        assertTrue(result.isEmpty());
    }

    @Test
    void getAlertas_unaEntrada_parsea_descripcionYFecha() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("Cambiar aceite|2026-12-01");

        List<MantenimientoService.Alerta> alertas = service.getAlertas("p1");

        assertEquals(1, alertas.size());
        assertEquals("Cambiar aceite", alertas.get(0).descripcion());
        assertEquals(LocalDate.of(2026, 12, 1), alertas.get(0).fecha());
        assertFalse(alertas.get(0).completada());
    }

    @Test
    void getAlertas_entradaCompletada_retornaFlag() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("Revisión general|2026-06-15|1");

        List<MantenimientoService.Alerta> alertas = service.getAlertas("p1");

        assertEquals(1, alertas.size());
        assertTrue(alertas.get(0).completada());
        assertEquals("Revisión general", alertas.get(0).descripcion());
    }

    @Test
    void getAlertas_entradaMalformada_esOmitida() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("SoloDescripcion");

        List<MantenimientoService.Alerta> alertas = service.getAlertas("p1");

        assertTrue(alertas.isEmpty());
    }

    @Test
    void getAlertas_fechaInvalida_retornaAlertaConFechaNula() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("Mantenimiento|no-es-fecha");

        List<MantenimientoService.Alerta> alertas = service.getAlertas("p1");

        assertEquals(1, alertas.size());
        assertNull(alertas.get(0).fecha());
        assertEquals("Mantenimiento", alertas.get(0).descripcion());
    }

    @Test
    void getAlertas_entradaVacia_esOmitida() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("   ");

        assertTrue(service.getAlertas("p1").isEmpty());
    }

    @Test
    void getAlertas_indiceEsPreservado() {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("1");
        when(mockConfig.get("mant_p1_0", null)).thenReturn("Test|2026-01-01");

        assertEquals(0, service.getAlertas("p1").get(0).index());
    }

    // ── agregarAlerta() ───────────────────────────────────────────────────────

    @Test
    void agregarAlerta_escribeValorEnClaveCorrecta() throws Exception {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("0");

        service.agregarAlerta("p1", "Cambiar aceite", LocalDate.of(2026, 12, 1));

        verify(mockConfig).set("mant_p1_0", "Cambiar aceite|2026-12-01");
    }

    @Test
    void agregarAlerta_incrementaContador() throws Exception {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("2");

        service.agregarAlerta("p1", "Nueva alerta", LocalDate.of(2026, 6, 30));

        verify(mockConfig).set("mant_p1_count", "3");
    }

    @Test
    void agregarAlerta_usaIndiceIgualAlConteo() throws Exception {
        when(mockConfig.get("mant_p1_count", "0")).thenReturn("3");

        service.agregarAlerta("p1", "Revisar", LocalDate.of(2026, 8, 1));

        verify(mockConfig).set("mant_p1_3", "Revisar|2026-08-01");
    }

    // ── marcarCompletada() ────────────────────────────────────────────────────

    @Test
    void marcarCompletada_agrega_flagUno() throws Exception {
        when(mockConfig.get("mant_p1_2", null)).thenReturn("Aceite|2026-12-01");

        service.marcarCompletada("p1", 2);

        verify(mockConfig).set("mant_p1_2", "Aceite|2026-12-01|1");
    }

    @Test
    void marcarCompletada_yaCompletada_sobreescribeCorrectamente() throws Exception {
        when(mockConfig.get("mant_p1_0", null)).thenReturn("Aceite|2026-12-01|0");

        service.marcarCompletada("p1", 0);

        verify(mockConfig).set("mant_p1_0", "Aceite|2026-12-01|1");
    }

    @Test
    void marcarCompletada_claveNoExiste_noLlamaSet() throws Exception {
        when(mockConfig.get("mant_p1_5", null)).thenReturn(null);

        service.marcarCompletada("p1", 5);

        verify(mockConfig, never()).set(anyString(), anyString());
    }

    // ── eliminarAlerta() ──────────────────────────────────────────────────────

    @Test
    void eliminarAlerta_llamaSet_conNull() throws Exception {
        service.eliminarAlerta("p1", 3);
        verify(mockConfig).set("mant_p1_3", null);
    }

    // ── getProximasGlobal() ───────────────────────────────────────────────────

    @Test
    void getProximasGlobal_entradaVencida_incluidaEnResultado() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("mant_p1_0", "Mantenimiento urgente|2020-01-01");
        when(mockConfig.findAll()).thenReturn(data);

        List<String[]> proximas = service.getProximasGlobal(365);

        assertEquals(1, proximas.size());
        assertEquals("p1", proximas.get(0)[0]);
        assertEquals("Mantenimiento urgente", proximas.get(0)[1]);
    }

    @Test
    void getProximasGlobal_entradaCompletada_esExcluida() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("mant_p1_0", "Completada|2020-01-01|1");
        when(mockConfig.findAll()).thenReturn(data);

        assertTrue(service.getProximasGlobal(365).isEmpty());
    }

    @Test
    void getProximasGlobal_entradaFuturaMasAllaDelLimite_esExcluida() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("mant_p1_0", "Lejana|2099-01-01");
        when(mockConfig.findAll()).thenReturn(data);

        assertTrue(service.getProximasGlobal(7).isEmpty());
    }

    @Test
    void getProximasGlobal_claveCount_esIgnorada() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("mant_p1_count", "3");
        when(mockConfig.findAll()).thenReturn(data);

        assertTrue(service.getProximasGlobal(365).isEmpty());
    }

    @Test
    void getProximasGlobal_claveNoMant_esIgnorada() throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("nombre_ayuntamiento", "Ixmiquilpan");
        when(mockConfig.findAll()).thenReturn(data);

        assertTrue(service.getProximasGlobal(365).isEmpty());
    }
}
