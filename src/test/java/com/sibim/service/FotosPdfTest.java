package com.sibim.service;

import com.itextpdf.io.image.ImageData;
import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FotosPdfTest {

    @TempDir Path dir;

    static Path imagen(Path dir, String nombre, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x += 7) for (int y = 0; y < h; y += 5) img.setRGB(x, y, (x * 31 + y * 17) & 0xFFFFFF);
        Path f = dir.resolve(nombre);
        ImageIO.write(img, nombre.endsWith(".png") ? "png" : "jpg", f.toFile());
        return f;
    }

    @Test void reducir_escalaAlLadoMaximo() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB), "png", out);
        BufferedImage r = ImageIO.read(new ByteArrayInputStream(FotosPdf.reducir(out.toByteArray(), 600)));
        assertEquals(600, r.getWidth());
        assertEquals(400, r.getHeight());
    }

    @Test void reducir_dejaIgualUnaFotoChica() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "jpg", out);
        byte[] original = out.toByteArray();
        assertArrayEquals(original, FotosPdf.reducir(original, 600));
    }

    @Test void de_principalPrimero_sinRepetidas_ySinRutasInexistentes() throws Exception {
        Path principal = imagen(dir, "principal.jpg", 800, 400);
        Path galeria = imagen(dir, "galeria.png", 300, 600);
        Producto p = new Producto();
        p.setCodigo("X-1");
        p.setFotoUrl(principal.toString());
        p.setFotosUrls(new ArrayList<>(List.of(principal.toString(), "C:/no/existe.jpg", galeria.toString())));

        List<ImageData> fotos = FotosPdf.de(p, 4, 500);
        assertEquals(2, fotos.size());
        assertEquals(500, fotos.get(0).getWidth(), 0.5);    // the main one, scaled to 500 wide
        assertEquals(500, fotos.get(1).getHeight(), 0.5);   // then the gallery one, 500 high
    }

    @Test void principales_soloLosQueTienenFoto() throws Exception {
        Producto con = new Producto(); con.setId("a"); con.setFotoUrl(imagen(dir, "a.jpg", 100, 100).toString());
        Producto sin = new Producto(); sin.setId("b");
        Producto rota = new Producto(); rota.setId("c"); rota.setFotoUrl(dir.resolve("no-esta.jpg").toString());
        Map<String, ImageData> m = FotosPdf.principales(List.of(con, sin, rota), 300);
        assertEquals(java.util.Set.of("a"), m.keySet());
    }
}
