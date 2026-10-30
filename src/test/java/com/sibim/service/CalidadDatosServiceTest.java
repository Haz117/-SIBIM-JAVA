package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.CalidadDatosRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalidadDatosServiceTest {

    private final CalidadDatosRepository repo = mock(CalidadDatosRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final ConfiguracionRepository config = mock(ConfiguracionRepository.class);
    private final CalidadDatosService service = new CalidadDatosService(repo, audit, config);

    @BeforeEach
    void entrar() {
        entrarComo(Rol.ADMIN);
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
    }

    private static void entrarComo(Rol rol) {
        Usuario u = new Usuario();
        u.setId("u-" + rol); u.setNombre("Prueba"); u.setRol(rol);
        SessionManager.setCurrentUser(u);
    }

    private static Producto bien(String id, String codigo, String quien) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Bien " + codigo); p.setResguardante(quien);
        p.setArea("Tesorería Municipal"); p.setStockActual(1);
        return p;
    }

    private static Resguardo resguardo(String id, String quien, String estado, String... bienes) {
        Resguardo r = new Resguardo();
        r.setId(id); r.setNumero("RSG-" + id); r.setResguardanteNombre(quien); r.setEstado(estado);
        r.setItems(java.util.Arrays.stream(bienes).map(b -> {
            ResguardoItem i = new ResguardoItem();
            i.setProductoId(b);
            return i;
        }).toList());
        return r;
    }

    // ── Personas ──────────────────────────────────────────────────────────────

    @Test
    void personas_mismasPalabrasEnOtroOrdenOConTitulo_sonUnCaso_ylasDistintasNo() {
        var casos = CalidadDatosService.personasRepetidas(List.of(), List.of(
            bien("1", "A/1", "ANA RUIZ SOTO"), bien("2", "A/2", "RUIZ SOTO ANA"),
            bien("3", "A/3", "LIC. ANA RUIZ SOTO"), bien("4", "A/4", "ANA RUIZ"),
            bien("5", "A/5", "Juan Pérez"), bien("6", "A/6", "JUAN  PEREZ")), Set.of());
        assertEquals(1, casos.size(), "«Juan Pérez» ya es uno solo: acentos y espacios no separan");
        assertEquals(3, casos.get(0).size());
    }

    @Test
    void personas_loYaRespondidoComoDistintas_noSeVuelveAPreguntar() {
        List<Producto> bienes = List.of(bien("1", "A/1", "ANA RUIZ"), bien("2", "A/2", "RUIZ ANA"));
        assertEquals(1, CalidadDatosService.personasRepetidas(List.of(), bienes, Set.of()).size());
        assertTrue(CalidadDatosService.personasRepetidas(List.of(), bienes, Set.of("ANA RUIZ")).isEmpty());
    }

    @Test
    void personas_seProponeElNombreConMasBienes() {
        var casos = CalidadDatosService.personasRepetidas(List.of(), List.of(
            bien("1", "A/1", "RUIZ ANA"), bien("2", "A/2", "ANA RUIZ"), bien("3", "A/3", "ANA RUIZ")), Set.of());
        assertEquals("ANA RUIZ", CalidadDatosService.nombrePropuesto(casos.get(0)));
    }

    /** Both the bienes without a resguardo and those of a resguardo in force change name
     *  (a bien and its resguardo always say the same); a cancelled one stays as it was signed. */
    @Test
    void unir_cambiaBienesYResguardosVigentes_noCancelados_yLoAudita() throws Exception {
        List<Producto> inventario = List.of(
            bien("en-vigente", "A/1", "RUIZ ANA"), bien("suelto", "A/2", "ruiz  ana"),
            bien("ya-bien", "A/3", "ANA RUIZ"), bien("otra", "A/4", "OTRA PERSONA"));
        List<Resguardo> resguardos = List.of(
            resguardo("vig", "RUIZ ANA", Resguardo.ESTADO_ACTIVO, "en-vigente"),
            resguardo("can", "RUIZ ANA", Resguardo.ESTADO_CANCELADO, "viejo"),
            resguardo("bien", "ANA RUIZ", Resguardo.ESTADO_ACTIVO, "ya-bien"));
        List<Resguardante> variantes = CalidadDatosService.personasRepetidas(resguardos, inventario, Set.of()).get(0);

        assertEquals(2, service.unirPersonas("ANA RUIZ", variantes, inventario).cuantosBienes());

        verify(repo).unirPersona("ANA RUIZ", List.of("en-vigente", "suelto"), List.of("vig"));
        verify(audit).log(eq("persona"), anyString(), eq("ANA RUIZ"), eq("actualizar"), contains("RUIZ ANA"));
    }

    /** Each thing goes back to the very name it had, spelled as it was. */
    @Test
    void deshacer_regresaCadaNombre_comoEstaba() throws Exception {
        List<Producto> inventario = List.of(bien("a", "A/1", "RUIZ ANA"), bien("b", "A/2", "ruiz  ana"), bien("c", "A/3", "ANA RUIZ"));
        List<Resguardo> resguardos = List.of(resguardo("vig", "RUIZ ANA", Resguardo.ESTADO_ACTIVO, "a"));
        List<Resguardante> variantes = CalidadDatosService.personasRepetidas(resguardos, inventario, Set.of()).get(0);

        var union = service.unirPersonas("ANA RUIZ", variantes, inventario);
        service.deshacer(union);

        verify(repo).unirPersona("RUIZ ANA", List.of("a"), List.of("vig"));
        verify(repo).unirPersona("ruiz  ana", List.of("b"), List.of());
        verify(audit).log(eq("persona"), anyString(), eq("ANA RUIZ"), eq("actualizar"), contains("deshizo"));
    }

    /** What is done doesn't appear; what isn't says how many and where it is fixed. */
    @Test
    void porHacer_cuentaLoQueFalta_yLoHechoNoAparece() {
        Producto completo = bien("1", "A/1", "ANA RUIZ");
        completo.setPrecioCompra(new BigDecimal("10")); completo.setFechaAdquisicion(LocalDate.of(2024, 1, 1));
        completo.setVidaUtilAnios(5); completo.setFotoUrl("foto.jpg"); completo.setEtiquetado(true);
        Producto sinPrecio = bien("2", "A/2", "ANA RUIZ");
        sinPrecio.setFechaAdquisicion(LocalDate.of(2024, 1, 1)); sinPrecio.setVidaUtilAnios(5); sinPrecio.setFotoUrl("f.jpg");
        Producto repetida = bien("3", "A/3", "RUIZ ANA");
        repetida.setPrecioCompra(new BigDecimal("10")); repetida.setFechaAdquisicion(LocalDate.of(2024, 1, 1));
        repetida.setVidaUtilAnios(5); repetida.setFotoUrl("f.jpg"); repetida.setEtiquetado(true);

        var lista = CalidadDatosService.porHacer(List.of(completo, sinPrecio, repetida), List.of(), Set.of(),
            Map.of("rfc_municipio", "X", "domicilio_municipio", "X", "firma_elabora", "X", "firma_vobo", "X"));
        var porTitulo = lista.stream().collect(java.util.stream.Collectors.toMap(CalidadDatosService.PorHacer::titulo, x -> x));

        assertEquals(1, porTitulo.get("personas con el nombre repetido").cantidad());
        assertEquals("unir", porTitulo.get("personas con el nombre repetido").abrir());
        assertEquals(1, porTitulo.get("bienes sin precio").cantidad());
        assertEquals("completar:PRECIO", porTitulo.get("bienes sin precio").abrir());
        assertEquals(1, porTitulo.get("bienes sin etiquetar").cantidad());
        assertFalse(porTitulo.containsKey("bienes sin fecha"), "lo que ya está no aparece");
        assertFalse(porTitulo.containsKey("series repetidas"));
    }

    @Test
    void faltanEnConfiguracion_diceQue_conSusPalabras() {
        var faltan = CalidadDatosService.faltanEnConfiguracion(Map.of("rfc_municipio", "X", "firma_vobo", "  ", "titular_area:Tesorería", "ANA"), 3);
        assertEquals(List.of("Domicilio", "Elabora", "Vo. Bo.", "Titular de 2 áreas"), faltan);
        assertTrue(CalidadDatosService.faltanEnConfiguracion(Map.of("rfc_municipio", "X", "domicilio_municipio", "X", "firma_elabora", "X",
            "firma_vobo", "X", "titular_area:Tesorería", "ANA"), 1).isEmpty());
    }

    @Test
    void unir_soloPatrimonio_yConConexion() throws Exception {
        List<Resguardante> variantes = List.of(new Resguardante("A B", List.of(), List.of()));
        entrarComo(Rol.DIRECCION);
        assertThrows(SecurityException.class, () -> service.unirPersonas("A B", variantes, List.of()));
        entrarComo(Rol.ADMIN);
        DatabaseConfig.setOfflineMode(true);
        try {
            assertThrows(java.sql.SQLException.class, () -> service.unirPersonas("A B", variantes, List.of()));
        } finally { DatabaseConfig.setOfflineMode(false); }
        verify(repo, never()).unirPersona(any(), any(), any());
    }

    // ── Vida útil ─────────────────────────────────────────────────────────────

    @Test
    void vidaUtil_seProponeSegunCONAC_soloALosQueNoTienen_ySinValorNoSeToca() throws Exception {
        Producto computo = bien("1", "A/1", null); computo.setClaveArmonizada("1.2.4.1.3.515");
        Producto mueble = bien("2", "A/2", null); mueble.setClaveArmonizada("1.2.4.1.1.511");
        Producto vehiculo = bien("3", "A/3", null); vehiculo.setClaveArmonizada("1.2.4.4.1.541");
        Producto defensa = bien("4", "A/4", null); defensa.setClaveArmonizada("1.2.4.5.1.551");
        Producto yaTiene = bien("5", "A/5", null); yaTiene.setClaveArmonizada("1.2.4.1.3.515"); yaTiene.setVidaUtilAnios(7);
        Producto sinClave = bien("6", "A/6", null);

        Map<Integer, List<Producto>> p = CalidadDatosService.propuestaVidaUtil(List.of(computo, mueble, vehiculo, defensa, yaTiene, sinClave));

        assertEquals(List.of(computo), p.get(3));
        assertEquals(List.of(vehiculo), p.get(5));
        assertEquals(List.of(mueble), p.get(10));
        assertEquals(3, p.size());

        when(repo.asignarVidaUtil(anyInt(), anyList())).thenReturn(1);
        assertEquals(3, service.aplicarVidaUtil(p));
        verify(repo).asignarVidaUtil(3, List.of("1"));
        verify(audit, times(3)).log(eq("producto"), anyString(), anyString(), eq("actualizar"), contains("según la clave CONAC"));
    }

    // ── Series ────────────────────────────────────────────────────────────────

    @Test
    void series_repetidas_sinImportarMayusculasNiEspacios_lasMasRepetidasPrimero() {
        Producto a = bien("1", "A/1", null); a.setNumeroSerie("abc123 ");
        Producto b = bien("2", "A/2", null); b.setNumeroSerie("ABC123");
        Producto c = bien("3", "A/3", null); c.setNumeroSerie("S/N");
        Producto d = bien("4", "A/4", null); d.setNumeroSerie("s/n");
        Producto e = bien("5", "A/5", null); e.setNumeroSerie("S/N");
        Producto unica = bien("6", "A/6", null); unica.setNumeroSerie("UNICA");
        Producto sin = bien("7", "A/7", null);

        var r = CalidadDatosService.seriesRepetidas(List.of(a, b, c, d, e, unica, sin));

        assertEquals(List.of("S/N", "ABC123"), r.stream().map(CalidadDatosService.SerieRepetida::serie).toList());
        assertTrue(r.get(0).noEsSerie());
        assertFalse(r.get(1).noEsSerie());
    }

    // ── Lo que falta ──────────────────────────────────────────────────────────

    @Test
    void falta_unPrecioEnCeroCuentaComoSinPrecio() {
        Producto p = bien("1", "A/1", null);
        assertTrue(CalidadDatosService.falta(p, CalidadDatosService.Dato.PRECIO));
        p.setPrecioCompra(BigDecimal.ZERO);
        assertTrue(CalidadDatosService.falta(p, CalidadDatosService.Dato.PRECIO));
        p.setPrecioCompra(new BigDecimal("10.50"));
        assertFalse(CalidadDatosService.falta(p, CalidadDatosService.Dato.PRECIO));
        assertTrue(CalidadDatosService.falta(p, CalidadDatosService.Dato.FECHA));
        p.setFechaAdquisicion(LocalDate.of(2024, 1, 1));
        assertFalse(CalidadDatosService.falta(p, CalidadDatosService.Dato.FECHA));
        assertEquals(2, CalidadDatosService.faltantes(p), "vida útil y foto");
    }
}
