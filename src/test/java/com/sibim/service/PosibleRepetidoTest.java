package com.sibim.service;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PosibleRepetidoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 11, 12, 0);

    private static Producto bien(String id, String codigo, String nombre, String serie) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre(nombre); p.setNumeroSerie(serie);
        p.setArea("Tesorería Municipal"); p.setResguardante("RUBEN PRUEBA SÁNCHEZ");
        p.setPrecioCompra(new BigDecimal("6500")); p.setStockActual(1);
        return p;
    }

    @Test
    void avisaSiLaSerieYaLaTieneOtroBien() {
        Producto otro = bien("1", "PRES/09", "LAPTOP DELL", "J7Z-2SW3");
        Producto nuevo = bien(null, "", "LAPTOP DELL", "j7z2sw3");
        List<String> avisos = PosibleRepetido.avisos(nuevo, null, List.of(otro), AHORA);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("PRES/09"));
    }

    @Test
    void unaSerieQueTienenMuchosEsDelModelo_yLasQueNoSonSerieNoCuentan() {
        List<Producto> sillas = List.of(bien("1", "A/1", "SILLA", "56112103"), bien("2", "A/2", "SILLA", "56112103"),
            bien("3", "A/3", "SILLA", "56112103"));
        assertTrue(PosibleRepetido.avisos(bien(null, "", "SILLA", "56112103"), null, sillas, AHORA).isEmpty());
        assertTrue(PosibleRepetido.avisos(bien(null, "", "MESA", "S/N"), null, List.of(bien("9", "B/1", "MESA", "S/N")), AHORA).isEmpty());
    }

    @Test
    void alEditarSoloAvisaSiCambioLaSerie() {
        Producto otro = bien("1", "PRES/09", "LAPTOP", "ABC12345");
        Producto antes = bien("2", "DEDU/04", "LAPTOP", "ABC12345");
        Producto despues = antes.copia();
        despues.setColor("NEGRO");
        assertTrue(PosibleRepetido.avisos(despues, antes, List.of(otro, antes), AHORA).isEmpty());
    }

    @Test
    void avisaSiSeAcabaDeRegistrarElMismoBien() {
        Producto hace3 = bien("1", "TES/24", "ARCHIVERO METALICO 5 GAVETAS", null);
        hace3.setCreadoEn(AHORA.minusMinutes(3));
        Producto ayer = bien("2", "TES/20", "ARCHIVERO METALICO 5 GAVETAS", null);
        ayer.setCreadoEn(AHORA.minusDays(1));
        List<String> avisos = PosibleRepetido.avisos(bien(null, "", "Archivero metálico 5 gavetas", null), null, List.of(hace3, ayer), AHORA);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("TES/24") && avisos.get(0).contains("3 minutos"));
    }
}
