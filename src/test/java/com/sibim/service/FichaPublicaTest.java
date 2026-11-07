package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.util.QrUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FichaPublicaTest {

    @BeforeEach
    void demo() { DatabaseConfig.setDemoMode(true); }

    private static Producto bien() {
        Producto p = new Producto();
        p.setId("b-1");
        p.setCodigo("SGM/07");
        p.setNombre("Computadora de escritorio");
        p.setArea("Secretaría General Municipal");
        p.setResguardante("Ana Pérez");
        p.setPrecioCompra(new BigDecimal("18500.00"));
        p.setPrecioVenta(new BigDecimal("18500.00"));
        p.setStockActual(1);
        p.setFechaAdquisicion(LocalDate.of(2023, 3, 15));
        return p;
    }

    /** No Storage in tests (as on a PC without the public key): the QR falls
     *  back to the data, for one bien and for a sheet of labels. */
    @Test
    void sinAlmacenamiento_elQrLlevaLosDatos() {
        Producto p = bien();
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.publicar(p));
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.contenidoQr(p));
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.publicar(List.of(p)).get("b-1"));
    }

    /** Anyone who scans the label can open it: no amounts, no signatures. */
    @Test
    void laFichaQueSeDescarga_noLlevaMontos() throws Exception {
        Producto p = bien();
        String publica = texto(new ReporteFichaTecnicaService().exportFichaPublica(p, "https://x/fichas/b-1.pdf"))
            .toUpperCase();
        assertTrue(publica.contains("SGM/07") && publica.contains("ANA PÉREZ"), publica);
        assertFalse(publica.contains("18,500") || publica.contains("COSTO DE ADQUISICI"), publica);
        assertFalse(publica.contains("VALOR EN LIBROS"), publica);

        String interna = texto(new ReporteFichaTecnicaService().exportFichaTecnica(p, List.of())).toUpperCase();
        assertTrue(interna.contains("COSTO DE ADQUISICI") && interna.contains("18,500"),
            "la ficha interna sí conserva los montos: " + interna);
    }

    /** Quitar títulos changed ~1,000 bienes at once and each asked for a
     *  database connection at the same moment: they wait in line now, two at
     *  a time, and every one of them gets its turn. */
    @Test
    void muchosBienesALaVez_esperanSuTurno_yPasanTodos() throws Exception {
        AtomicInteger aLaVez = new AtomicInteger(), maximo = new AtomicInteger();
        Set<String> hechos = ConcurrentHashMap.newKeySet();
        CountDownLatch todos = new CountDownLatch(300);
        FichaPublica.Cola cola = new FichaPublica.Cola(2, id -> {
            maximo.accumulateAndGet(aLaVez.incrementAndGet(), Math::max);
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            aLaVez.decrementAndGet();
            hechos.add(id);
            todos.countDown();
        });
        for (int i = 0; i < 300; i++) cola.encolar("b-" + i);
        assertTrue(todos.await(30, TimeUnit.SECONDS), "faltaron " + todos.getCount());
        assertEquals(300, hechos.size());
        assertTrue(maximo.get() <= 2, "llegaron a ir " + maximo.get() + " a la vez");
    }

    /** A bien changed again while it waits is written once, as it is by its
     *  turn; changed after its turn, it gets another. A failure does not stop
     *  the line. */
    @Test
    void elMismoBienMientrasEspera_unaVez_yDespuesOtra() throws Exception {
        CountDownLatch ocupado = new CountDownLatch(1), suelta = new CountDownLatch(1);
        Map<String, AtomicInteger> veces = new ConcurrentHashMap<>();
        Map<String, CountDownLatch> fin = new ConcurrentHashMap<>(Map.of("fin-1", new CountDownLatch(1),
            "fin-2", new CountDownLatch(1)));
        FichaPublica.Cola cola = new FichaPublica.Cola(1, id -> {
            if (id.equals("tapon")) {
                ocupado.countDown();
                try { suelta.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return;
            }
            if (id.equals("falla")) throw new IllegalStateException("sin conexión");
            veces.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            if (fin.containsKey(id)) fin.get(id).countDown();
        });
        cola.encolar("tapon");
        assertTrue(ocupado.await(10, TimeUnit.SECONDS));
        cola.encolar("falla");
        for (int i = 0; i < 5; i++) cola.encolar("b-1");
        cola.encolar("fin-1");
        suelta.countDown();
        assertTrue(fin.get("fin-1").await(10, TimeUnit.SECONDS));
        assertEquals(1, veces.get("b-1").get());

        cola.encolar("b-1");
        cola.encolar("fin-2");
        assertTrue(fin.get("fin-2").await(10, TimeUnit.SECONDS));
        assertEquals(2, veces.get("b-1").get());
    }

    private static String texto(File pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)));
            return sb.toString();
        } finally {
            pdf.delete();
        }
    }
}
