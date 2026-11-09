package com.sibim.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** One SIBIM per user (9-nov-2026): opened again, the running one comes to the front
 *  and the new one closes. Two side by side fought over the offline store. */
class InstanciaUnicaTest {

    @TempDir Path dir;

    @AfterEach
    void soltar() { InstanciaUnica.soltar(); }

    /** What a second SIBIM — another process — finds. */
    private String otra() throws Exception {
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OtraInstancia.class.getName(), dir.toString())
            .redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "la segunda terminó");
        return salida;
    }

    @Test
    void abiertoOtraVez_laQueYaCorreSeMuestra_yLaNuevaNoArranca() throws Exception {
        CountDownLatch pedido = new CountDownLatch(1);
        assertEquals(InstanciaUnica.Resultado.UNICA, InstanciaUnica.adquirir(dir, pedido::countDown));
        String salida = otra();
        assertTrue(salida.contains("RESULTADO=LA_OTRA_SE_MOSTRO"), salida);
        assertTrue(pedido.await(10, TimeUnit.SECONDS), "a la primera le llegó «mostrar»");
    }

    @Test
    void cerradaLaPrimera_laSiguienteArrancaNormal() throws Exception {
        assertEquals(InstanciaUnica.Resultado.UNICA, InstanciaUnica.adquirir(dir, null));
        InstanciaUnica.soltar();
        assertTrue(otra().contains("RESULTADO=UNICA"));
    }

    @Test
    void siLaQueCorreNoContesta_laNuevaLoDice() throws Exception {
        assertEquals(InstanciaUnica.Resultado.UNICA, InstanciaUnica.adquirir(dir, null));
        Files.delete(dir.resolve("sibim.puerto"));   // as if it couldn't open its port
        assertTrue(otra().contains("RESULTADO=LA_OTRA_NO_CONTESTO"));
    }

    @Test
    void elMismoProcesoOtraVez_noSeBloqueaASiMismo() {
        assertEquals(InstanciaUnica.Resultado.UNICA, InstanciaUnica.adquirir(dir, null));
        assertEquals(InstanciaUnica.Resultado.UNICA, InstanciaUnica.adquirir(dir, null));
    }
}
