package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CompletarPorGrupos.Campo;
import com.sibim.service.CompletarPorGrupos.Grupo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** «Completar por grupos» with cases like the inventory of 24-nov-2026. */
class CompletarPorGruposTest {

    private int n;

    private Producto bien(String nombre, String area, String clave) {
        Producto p = new Producto();
        p.setId("id" + (++n));
        p.setCodigo("SSP/" + n);
        p.setNombre(nombre);
        p.setArea(area);
        p.setClaveArmonizada(clave);
        p.setStockActual(1);
        return p;
    }

    private List<Producto> varios(int cuantos, String nombre, String area, String clave) {
        List<Producto> out = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) out.add(bien(nombre, area, clave));
        return out;
    }

    private static Grupo grupo(List<Grupo> grupos, String nombre) {
        return grupos.stream().filter(g -> g.nombre().equals(nombre)).findFirst().orElseThrow(() -> new AssertionError(nombre));
    }

    @Test
    void cadaNombreConDosOMas_esUnRenglon_conLosQueMasFaltanPrimero() {
        List<Producto> todos = new ArrayList<>();
        todos.addAll(varios(37, "ESPOSAS SEMIRIGIDAS (MANO)", "Seguridad Pública", "1.2.4.6.5.565"));
        todos.addAll(varios(3, "Esposas semirígidas (mano)", "Seguridad Pública", "1.2.4.6.5.565"));
        todos.addAll(varios(16, "UPS DE 1000 VA", "Seguridad Pública", "1.2.4.1.3.515"));
        todos.add(bien("PODIUM CON ATRIL", "Cultura", "1.2.4.1.1.511"));
        Producto deBaja = bien("UPS DE 1000 VA", "Seguridad Pública", "1.2.4.1.3.515");
        deBaja.setFechaBaja(LocalDate.of(2026, 1, 5));
        todos.add(deBaja);

        List<Grupo> grupos = CompletarPorGrupos.grupos(todos);
        assertEquals(2, grupos.size(), "un nombre solo no es grupo; el nombre se compara sin mayúsculas, acentos ni signos");
        assertEquals("ESPOSAS SEMIRIGIDAS (MANO)", grupos.get(0).nombre(), "como lo escribe la mayoría");
        assertEquals(40, grupos.get(0).bienes().size());
        assertEquals(16, grupos.get(1).bienes().size(), "el dado de baja no cuenta");
        assertEquals(Map.of(Campo.VIDA_UTIL, 40, Campo.COLOR, 40, Campo.TIPO, 40), grupos.get(0).faltan());
    }

    @Test
    void loQueYaTienenLosDelNombre_seProponeAlosQueNo_peroLaVidaUtilNuncaPorSuClave() {
        List<Producto> chalecos = varios(6, "CHALECO BALISTICO NIVEL IIIA", "Seguridad Pública", "1.2.4.6.5.565");
        chalecos.get(0).setVidaUtilAnios(7);
        chalecos.get(1).setVidaUtilAnios(7);
        chalecos.get(0).setColor("NEGRO");
        chalecos.get(1).setColor("Negro");
        List<Producto> camaras = varios(5, "CAMARA BALA IP 4 MP", "Seguridad Pública", "1.2.4.2.3.523");
        camaras.get(0).setVidaUtilAnios(3);
        camaras.get(1).setVidaUtilAnios(10);
        List<Producto> todos = new ArrayList<>(chalecos);
        todos.addAll(camaras);

        List<Grupo> grupos = CompletarPorGrupos.grupos(todos);
        Grupo ch = grupo(grupos, "CHALECO BALISTICO NIVEL IIIA");
        assertEquals(7, ch.propuestas().get(Campo.VIDA_UTIL).valor());
        assertEquals("como los otros 2", ch.propuestas().get(Campo.VIDA_UTIL).porQue());
        assertEquals("NEGRO", ch.propuestas().get(Campo.COLOR).valor(), "«Negro» y «NEGRO» son lo mismo");
        assertEquals("MUEBLE", ch.propuestas().get(Campo.TIPO).valor());
        assertEquals("por su clave CONAC", ch.propuestas().get(Campo.TIPO).porQue());
        assertEquals(4, ch.faltan().get(Campo.VIDA_UTIL));

        Grupo cam = grupo(grupos, "CAMARA BALA IP 4 MP");
        assertNull(cam.propuestas().get(Campo.VIDA_UTIL), "3 y 10: lo decide Patrimonio, ni por mayoría ni por su clave");
        assertEquals("3 años en 1, 10 años en 1", cam.tienen().get(Campo.VIDA_UTIL));
        assertEquals("Ninguno tiene", cam.tienen().get(Campo.COLOR));
    }

    @Test
    void elColorQueDiceSuNombre_oSuDescripcion_soloSiEsElMismoEnTodos() {
        List<Producto> sillas = varios(54, "SILLA PLEGADIZA DE COLOR VERDE LIMON", "DIF Municipal", "1.2.4.1.1.511");
        List<Producto> anaqueles = varios(3, "ANAQUEL METALICO", "Archivo Municipal", "1.2.4.1.1.511");
        anaqueles.forEach(a -> a.setDescripcion("ANAQUEL DE 5 ENTREPAÑOS COLOR GRIS"));
        List<Producto> mesas = varios(2, "MESA DE TRABAJO", "Obras Públicas", "1.2.4.1.1.511");
        mesas.get(0).setDescripcion("COLOR BLANCO");
        mesas.get(1).setDescripcion("COLOR NEGRO");
        List<Producto> todos = new ArrayList<>(sillas);
        todos.addAll(anaqueles);
        todos.addAll(mesas);

        List<Grupo> grupos = CompletarPorGrupos.grupos(todos);
        assertEquals(new CompletarPorGrupos.Propuesta("VERDE LIMON", "lo dice su nombre"),
            grupo(grupos, "SILLA PLEGADIZA DE COLOR VERDE LIMON").propuestas().get(Campo.COLOR));
        assertEquals(new CompletarPorGrupos.Propuesta("GRIS", "lo dice su descripción"),
            grupo(grupos, "ANAQUEL METALICO").propuestas().get(Campo.COLOR));
        assertNull(grupo(grupos, "MESA DE TRABAJO").propuestas().get(Campo.COLOR), "blanca y negra: ninguno");
    }

    @Test
    void conUnArea_elRenglonEsDeSusBienes_peroSabeLoDeLosDemas() {
        List<Producto> sillas = new ArrayList<>(varios(3, "SILLA OPERATIVA", "Tesorería Municipal", "1.2.4.1.1.511"));
        List<Producto> otras = varios(15, "SILLA OPERATIVA", "Dirección Jurídica", "1.2.4.1.1.511");
        otras.forEach(o -> o.setVidaUtilAnios(5));
        sillas.addAll(otras);

        List<Grupo> grupos = CompletarPorGrupos.grupos(sillas, p -> "Tesorería Municipal".equals(p.getArea()), EnumSet.allOf(Campo.class));
        assertEquals(1, grupos.size());
        Grupo g = grupos.get(0);
        assertEquals(3, g.bienes().size());
        assertEquals(18, g.conNombre());
        assertEquals("3 de 18 bienes con este nombre", CompletarPorGrupos.cuantos(g));
        assertEquals(5, g.propuestas().get(Campo.VIDA_UTIL).valor(), "de sus iguales en otra área");
        assertEquals(Set.of("Tesorería Municipal"), g.areas());

        Grupo soloVida = CompletarPorGrupos.grupos(sillas, p -> true, EnumSet.of(Campo.VIDA_UTIL)).get(0);
        assertEquals(Map.of(Campo.VIDA_UTIL, 3), soloVida.faltan(), "«Les falta vida útil» no pide el color ni el tipo");
        assertEquals(18, soloVida.bienes().size());
        assertEquals(3, soloVida.incompletos());
    }

    @Test
    void alGuardar_cadaDatoVaSoloALosQueNoLoTienen() {
        List<Producto> esposas = varios(4, "ESPOSAS (MANO)", "Seguridad Pública", "1.2.4.6.5.565");
        esposas.get(0).setColor("PLATA");
        esposas.get(1).setVidaUtilAnios(5);
        Grupo g = CompletarPorGrupos.grupos(esposas).get(0);

        Map<Campo, Object> valores = new EnumMap<>(Campo.class);
        valores.put(Campo.VIDA_UTIL, 10);
        valores.put(Campo.COLOR, "NEGRO");
        List<EditarVarios.Renglon> r = CompletarPorGrupos.renglones(g, valores);
        assertEquals(4, r.size());
        Map<String, Producto> nuevo = new HashMap<>();
        r.forEach(x -> nuevo.put(x.bien().getId(), x.nuevo()));
        assertEquals("PLATA", nuevo.get(esposas.get(0).getId()).getColor(), "el color que ya tenía se queda");
        assertEquals(10, nuevo.get(esposas.get(0).getId()).getVidaUtilAnios());
        assertEquals(5, nuevo.get(esposas.get(1).getId()).getVidaUtilAnios(), "la vida útil que ya tenía se queda");
        assertEquals("NEGRO", nuevo.get(esposas.get(2).getId()).getColor());
        assertNull(nuevo.get(esposas.get(2).getId()).getTipoBien(), "lo que no se escribió no se toca");
        assertTrue(CompletarPorGrupos.renglones(g, Map.of()).isEmpty());
    }

    @Test
    void loEscrito_seLeeComoSeGuarda_yLoQueNoSirveLoDice() {
        List<String> tipos = List.of("MUEBLE", "Camioneta");
        assertEquals(10, CompletarPorGrupos.leer(Campo.VIDA_UTIL, " 10 ", tipos));
        assertEquals(3, CompletarPorGrupos.leer(Campo.VIDA_UTIL, "3 años", tipos));
        assertNull(CompletarPorGrupos.leer(Campo.VIDA_UTIL, "  ", tipos));
        assertThrows(IllegalArgumentException.class, () -> CompletarPorGrupos.leer(Campo.VIDA_UTIL, "0", tipos));
        assertThrows(IllegalArgumentException.class, () -> CompletarPorGrupos.leer(Campo.VIDA_UTIL, "diez", tipos));
        assertEquals("GRIS OSCURO", CompletarPorGrupos.leer(Campo.COLOR, " gris   oscuro", tipos));
        assertEquals("MUEBLE", CompletarPorGrupos.leer(Campo.TIPO, "mueble", tipos));
        assertEquals("Camioneta", CompletarPorGrupos.leer(Campo.TIPO, "CAMIONETA", tipos), "como en la lista de tipos");
        IllegalArgumentException color = assertThrows(IllegalArgumentException.class, () -> CompletarPorGrupos.leer(Campo.TIPO, "negro", tipos));
        assertTrue(color.getMessage().contains("parece un color"), color.getMessage());
    }

    @Test
    void enPorHacer_losGrupos_yLoSeguroConLosColoresQueDiceSuNombre() {
        List<Producto> todos = new ArrayList<>(varios(3, "SILLA PLEGADIZA DE COLOR VERDE LIMON", "DIF Municipal", "1.2.4.1.1.511"));
        todos.add(bien("ESCRITORIO COLOR NOGAL", "Tesorería Municipal", "1.2.4.1.1.511"));
        var lista = CalidadDatosService.porHacer(todos, List.of(), Set.of(), Map.of());
        var grupos = lista.stream().filter(x -> "grupos".equals(x.abrir())).findFirst().orElseThrow();
        assertEquals(1, grupos.cantidad());
        assertTrue(grupos.titulo().contains("(3 bienes)"), grupos.titulo());
        var seguro = lista.stream().filter(x -> "seguro".equals(x.abrir())).findFirst().orElseThrow();
        assertEquals(8, seguro.cantidad(), "MUEBLE a los 4 por su clave y el color de su nombre a los 4");
        assertTrue(seguro.titulo().contains("el color del nombre"), seguro.titulo());
    }

    /** 18-nov-2026: «NEGRO» was the tipo of 6 sillas; it is not passed on to their twins. */
    @Test
    void unColorEscritoComoTipo_noSePropone_vaMUEBLE() {
        List<Producto> sillas = varios(3, "SILLA ERGONOMICA", "Tesorería Municipal", "1.2.4.1.1.511");
        sillas.get(0).setTipoBien("NEGRO");
        Grupo g = CompletarPorGrupos.grupos(sillas).get(0);
        assertEquals(new CompletarPorGrupos.Propuesta("MUEBLE", "por su clave CONAC"), g.propuestas().get(Campo.TIPO));
    }

    @Test
    void cuandoTodosLoTienen_noHayRenglon() {
        List<Producto> bancas = varios(2, "BANCA DE FIERRO FUNDIDO", "Servicios Municipales", "1.2.4.1.1.511");
        bancas.forEach(b -> { b.setVidaUtilAnios(10); b.setColor("NEGRO"); b.setTipoBien("MUEBLE"); });
        assertTrue(CompletarPorGrupos.grupos(bancas).isEmpty());
        bancas.get(1).setColor(null);
        Grupo g = CompletarPorGrupos.grupos(bancas).get(0);
        assertEquals(Map.of(Campo.COLOR, 1), g.faltan());
        assertEquals(1, g.incompletos());
        assertEquals("Todos: 10 años", g.tienen().get(Campo.VIDA_UTIL));
        assertEquals("NEGRO", g.propuestas().get(Campo.COLOR).valor());
        assertEquals("como SSP/" + (n - 1), g.propuestas().get(Campo.COLOR).porQue());
        assertEquals("SSP/" + n, g.bienes().get(0).getCodigo(), "el que le falta, primero");
    }
}
