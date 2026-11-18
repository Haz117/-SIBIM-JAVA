package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.Campo;
import com.sibim.service.CalidadDatosService.Repetido;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** «Unir lo escrito de dos formas» with the marcas, colors and tipos of 13-nov-2026, and the
 *  tipo and color the inventory already says. */
class EscritosDeDosFormasTest {

    private int n;

    private List<Producto> varios(int cuantos, java.util.function.Consumer<Producto> dato) {
        List<Producto> out = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            Producto p = new Producto();
            p.setId("p" + (++n));
            p.setCodigo("PRES/" + n);
            p.setNombre("BIEN");
            dato.accept(p);
            out.add(p);
        }
        return out;
    }

    private List<Producto> marcas(Object... marcaYCuantos) {
        List<Producto> out = new ArrayList<>();
        for (int i = 0; i < marcaYCuantos.length; i += 2) {
            String m = (String) marcaYCuantos[i];
            out.addAll(varios((Integer) marcaYCuantos[i + 1], p -> p.setMarca(m)));
        }
        return out;
    }

    private static Map<String, String> cambios(List<Repetido> r) {
        return r.stream().collect(Collectors.toMap(Repetido::variante, Repetido::queda));
    }

    @Test
    void mismasLetrasSinSignosNiAcentos_seUnen() {
        var r = CalidadDatosService.parecidos(Campo.MARCA, marcas("SMITH & WESSON", 79, "SMITH WESSON", 25,
            "SAMSUNG", 18, "SAMSUNG,", 1, "HECHO EN MEXICO", 9, "HECHO EN MÉXICO", 2));
        assertEquals(Map.of("SMITH WESSON", "SMITH & WESSON", "SAMSUNG,", "SAMSUNG", "HECHO EN MEXICO", "HECHO EN MÉXICO"), cambios(r),
            "con sus acentos aunque los escriban menos bienes");
        assertEquals(25, r.stream().filter(x -> x.variante().equals("SMITH WESSON")).findFirst().orElseThrow().bienes());
    }

    @Test
    void unaLetraDeMasMenosOVolteada_siUnaFormaSeUsaElDoble() {
        var r = CalidadDatosService.parecidos(Campo.MARCA, marcas("TRUPER", 33, "TRUPPER", 1, "ACER", 7, "ACCER", 1, "HACER", 1,
            "STIHL", 14, "STHIL", 4, "STHILL", 1, "OFILINEA", 13, "OFFILINEA", 3));
        assertEquals(Map.of("TRUPPER", "TRUPER", "ACCER", "ACER", "HACER", "ACER", "STHIL", "STIHL", "STHILL", "STIHL",
            "OFFILINEA", "OFILINEA"), cambios(r));
    }

    @Test
    void marcasQueSoloSeParecen_noSeTocan() {
        var r = CalidadDatosService.parecidos(Campo.MARCA, marcas("LENOVO", 55, "GENOVA", 7, "EPSON", 40, "EPCOM", 10,
            "DELL", 15, "VBELL", 5, "PRINTAFORM", 3, "TRINTAFORM", 3, "LG", 9, "HP", 20, "SIN MARCA", 30, "S/M", 2));
        assertTrue(r.isEmpty(), "dos letras de diferencia, un empate o dos letras cortas son otras marcas: " + r);
    }

    @Test
    void coloresYTipos() {
        List<Producto> bienes = new ArrayList<>();
        bienes.addAll(varios(226, p -> p.setColor("NEGRO")));
        bienes.addAll(varios(2, p -> p.setColor("NEBRO")));
        bienes.addAll(varios(54, p -> p.setColor("BLANCO")));
        bienes.addAll(varios(1, p -> p.setColor("BLANCA")));
        bienes.addAll(varios(391, p -> p.setTipoBien("MUEBLE")));
        bienes.addAll(varios(2, p -> p.setTipoBien("mueble")));
        bienes.addAll(varios(1, p -> p.setTipoBien("MUELE")));
        bienes.addAll(varios(1, p -> p.setTipoBien("MUEBLES")));
        bienes.addAll(varios(8, p -> p.setTipoBien("Camion")));
        bienes.addAll(varios(1, p -> p.setTipoBien("Camión")));
        bienes.addAll(varios(66, p -> p.setTipoBien("Camioneta")));

        assertEquals(Map.of("NEBRO", "NEGRO", "BLANCA", "BLANCO"), cambios(CalidadDatosService.parecidos(Campo.COLOR, bienes)));
        assertEquals(Map.of("mueble", "MUEBLE", "MUELE", "MUEBLE", "MUEBLES", "MUEBLE", "Camion", "Camión"),
            cambios(CalidadDatosService.parecidos(Campo.TIPO, bienes)), "«Camioneta» es otro tipo");
    }

    @Test
    void laListaJuntaProveedoresMarcasColoresYTipos() {
        List<Producto> bienes = new ArrayList<>(marcas("TRUPER", 3, "TRUPPER", 1));
        bienes.addAll(varios(1, p -> p.setProveedor("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION")));
        bienes.addAll(varios(1, p -> p.setProveedor("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV")));
        bienes.addAll(varios(1, p -> p.setColor("NEBRO")));
        bienes.addAll(varios(5, p -> p.setColor("NEGRO")));
        var r = CalidadDatosService.escritosDeDosFormas(bienes);
        assertEquals(List.of(Campo.PROVEEDOR, Campo.MARCA, Campo.COLOR), r.stream().map(Repetido::campo).toList());
    }

    @Test
    void unaLetra() {
        assertTrue(CalidadDatosService.unaLetra("STIHL", "STHIL"), "dos letras volteadas");
        assertTrue(CalidadDatosService.unaLetra("ACER", "HACER"));
        assertTrue(CalidadDatosService.unaLetra("NEGRO", "NEBRO"));
        assertFalse(CalidadDatosService.unaLetra("LENOVO", "GENOVA"));
        assertFalse(CalidadDatosService.unaLetra("STIHL", "STHILL"));
    }

    @Test
    void tipoSugerido_noAVehiculosNiBajasNiALosQueYaTienen() {
        Producto silla = varios(1, p -> p.setClaveArmonizada("1.2.4.1.1.511")).get(0);
        Producto camioneta = varios(1, p -> p.setClaveArmonizada("1.2.4.4.1.541")).get(0);
        Producto conTipo = varios(1, p -> { p.setClaveArmonizada("1.2.4.1.1.511"); p.setTipoBien("OFICINA"); }).get(0);
        Producto deBaja = varios(1, p -> { p.setClaveArmonizada("1.2.4.1.1.511"); p.setFechaBaja(java.time.LocalDate.of(2026, 11, 3)); }).get(0);
        Producto sinClave = varios(1, p -> { }).get(0);
        assertEquals(List.of(silla), CalidadDatosService.sinTipoConSugerencia(List.of(silla, camioneta, conTipo, deBaja, sinClave)));
        assertEquals("MUEBLE", CalidadDatosService.tipoSugerido(" 1.2.4.6.7.567 "));
        assertNull(CalidadDatosService.tipoSugerido("1.2.4.4.2"));
    }

    @Test
    void colorDelNombre_soloALosQueNoTienen() {
        Producto silla = varios(1, p -> p.setNombre("SILLA OPERATIVA COLOR NEGRO")).get(0);
        Producto yaTiene = varios(1, p -> { p.setNombre("SILLA COLOR AZUL"); p.setColor("AZUL REY"); }).get(0);
        Producto sinColor = varios(1, p -> p.setNombre("ESCRITORIO DE MADERA")).get(0);
        assertEquals(Map.of(silla, "NEGRO"), CalidadDatosService.coloresDelNombre(List.of(silla, yaTiene, sinColor)));
    }

    @Test
    void porHacer_diceCuantosLlevanMueble_yLoEscritoDeDosFormas() {
        List<Producto> bienes = new ArrayList<>(varios(3, p -> p.setClaveArmonizada("1.2.4.1.1.511")));
        bienes.addAll(marcas("TRUPER", 3, "TRUPPER", 1));
        var porHacer = CalidadDatosService.porHacer(bienes, List.of(), java.util.Set.of(), Map.of());
        var mueble = porHacer.stream().filter(p -> "tipo".equals(p.abrir())).findFirst().orElseThrow();
        assertEquals(3, mueble.cantidad(), "los 3 con clave; los 4 de marca no tienen clave: " + mueble);
        var unir = porHacer.stream().filter(p -> "proveedores".equals(p.abrir())).findFirst().orElseThrow();
        assertEquals(1, unir.cantidad());
        assertEquals("Unirlos", unir.accion());
    }

    /** 18-nov-2026: «NEGRO» was the tipo de bien of 6 sillas and «NEGRO ERGONÓMICO» of one. */
    @Test
    void unColorEscritoComoTipo_quedaConElDeSuClave_siNingunoPierdeSuColor() {
        List<Producto> bienes = new ArrayList<>(varios(2, p -> { p.setClaveArmonizada("1.2.4.1.1.511"); p.setTipoBien("NEGRO"); p.setColor("NEGRO"); }));
        bienes.addAll(varios(1, p -> { p.setClaveArmonizada("1.2.4.1.1.511"); p.setTipoBien("NEGRO ERGONÓMICO"); }));
        bienes.addAll(varios(1, p -> { p.setClaveArmonizada("1.2.4.4.1.541"); p.setTipoBien("BLANCA"); p.setColor("BLANCO"); }));
        bienes.addAll(varios(1, p -> { p.setClaveArmonizada("1.2.4.1.1.511"); p.setTipoBien("CAFETERA"); p.setColor("NEGRO"); }));

        var r = CalidadDatosService.escritosDeDosFormas(bienes);
        assertEquals(Map.of("NEGRO", "MUEBLE"), cambios(r),
            "sin color se perdería («NEGRO ERGONÓMICO»); un vehículo no tiene tipo propuesto; «CAFETERA» no es un color");
        assertEquals(2, r.get(0).bienes());
        assertEquals(Campo.TIPO, r.get(0).campo());
    }

    @Test
    void losTiposParaElegir_unaVezCadaUno_sinColores_losMasUsadosPrimero() {
        List<Producto> bienes = new ArrayList<>();
        for (Object[] t : new Object[][]{{"MUEBLE", 5}, {"mueble", 1}, {"MUBLE", 1}, {"Camion", 2}, {"Camión", 1},
                {"Camioneta", 3}, {"NEGRO", 4}, {" ", 2}, {"N/A", 1}}) {
            String tipo = (String) t[0];
            bienes.addAll(varios((Integer) t[1], p -> p.setTipoBien(tipo)));
        }
        bienes.addAll(varios(9, p -> { p.setTipoBien("Ambulancia"); p.setFechaBaja(java.time.LocalDate.of(2026, 10, 1)); }));

        List<String> lista = CalidadDatosService.tiposParaElegir(bienes);
        assertEquals(List.of("MUEBLE", "Camioneta", "Camión"), lista, "los de baja no cuentan");
        assertEquals("MUEBLE", CalidadDatosService.comoEnLaLista(" mueble", lista));
        assertEquals("Camión", CalidadDatosService.comoEnLaLista("CAMION", lista));
        assertNull(CalidadDatosService.comoEnLaLista("Camionetas", lista), "otra palabra no se cambia sola");
        assertNull(CalidadDatosService.comoEnLaLista("", lista));
    }
}
