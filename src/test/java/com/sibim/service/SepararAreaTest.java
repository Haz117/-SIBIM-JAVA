package com.sibim.service;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Configuración › Áreas › «Separar en un área nueva…»: as the Mercado Municipal was
 *  separated from Reglamentos on 13-nov-2026 (35 bienes of one person, 4 of them
 *  without a resguardo) and the Rastro from Servicios Municipales on 7-nov. */
class SepararAreaTest {

    @AfterEach
    void salir() {
        SessionManager.logout();
        Areas.usar(AreaCatalog.PREDETERMINADO);
    }

    private static Producto bien(String codigo, String resguardante) {
        Producto p = new Producto();
        p.setId("id-" + codigo);
        p.setCodigo(codigo);
        p.setNombre("BIEN " + codigo);
        p.setArea("Reglamentos, Comercio y Espectáculos");
        p.setResguardante(resguardante);
        p.setStockActual(1);
        return p;
    }

    private static Resguardo resguardo(String numero, String cargo) {
        Resguardo r = new Resguardo();
        r.setNumero(numero);
        r.setResguardanteCargo(cargo);
        r.setEstado(Resguardo.ESTADO_ACTIVO);
        return r;
    }

    @Test
    void agrupar_unoPorPersona_conSuCargo_enOrdenDeNumero_yLosSinPersonaAlFinal() {
        Producto j10 = bien("RCE/10", "JUAN CARLOS FICTICIO CORTES"), j2 = bien("RCE/2", "Juan Carlos Ficticio Cortés"),
            j41 = bien("RCE/41", "JUAN CARLOS FICTICIO CORTES"),
            compartido = bien("RCE/97", "JULIO CESAR PRUEBA RODRIGUEZ, OSCAR FICTICIO HERNANDEZ"),
            nadie = bien("RCE/50", ""), brenda = bien("RCE/5", "BRENDA MARIANA PRUEBA GARCIA");
        Map<String, Resguardo> vigente = Map.of("id-RCE/10", resguardo("RSG-177", "TESORERO DEL MERCADO"),
            "id-RCE/5", resguardo("RSG-200", "AUXILIAR ADMINISTRATIVO"));

        List<SepararArea.Grupo> g = SepararArea.agrupar(List.of(j10, compartido, j41, nadie, j2, brenda),
            p -> vigente.get(p.getId()));

        assertEquals(3, g.size());
        assertEquals("Juan Carlos Ficticio Cortés", g.get(0).persona(), "el primero por número da el nombre");
        assertEquals("TESORERO DEL MERCADO", g.get(0).cargo(), "el cargo de su resguardo, aunque RCE/41 no tenga");
        assertEquals(List.of("RCE/2", "RCE/10", "RCE/41"), g.get(0).bienes().stream().map(Producto::getCodigo).toList());
        assertEquals("BRENDA MARIANA PRUEBA GARCIA", g.get(1).persona());
        assertEquals("AUXILIAR ADMINISTRATIVO", g.get(1).cargo());
        assertNull(g.get(2).persona(), "dos personas en un nombre, o nadie: sin resguardo");
        assertEquals(List.of("RCE/50", "RCE/97"), g.get(2).bienes().stream().map(Producto::getCodigo).toList());
    }

    @Test
    void prefijoSugerido_lasInicialesOLasPrimerasLetras_sinRepetir() {
        List<String> usados = AreaCatalog.PREDETERMINADO.entradas().stream().map(AreaCatalog.Entrada::prefijo).toList();
        assertEquals("MM", SepararArea.prefijoSugerido("Mercado Municipal", usados));
        assertEquals("RAST", SepararArea.prefijoSugerido("Rastro", usados));
        assertEquals("DMM", SepararArea.prefijoSugerido("Dirección del Mercado de Morelos", usados), "sin «del», «de»");
        assertEquals("MM2", SepararArea.prefijoSugerido("Mercado Municipal", List.of("MM", "TES")));
        assertEquals("", SepararArea.prefijoSugerido("  ", usados));
        assertTrue(AreaCatalog.PREFIJO_VALIDO.matcher(SepararArea.prefijoSugerido("Área de Ñandúes y Pingüinos", usados)).matches());
    }

    @Test
    void aplicar_creaElAreaYPasaCadaGrupoConSuPersona() throws Exception {
        Areas.usar(AreaCatalog.PREDETERMINADO);
        Usuario u = new Usuario();
        u.setId("u-1"); u.setNombre("Patrimonio"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        AreaCatalog.Entrada mercado = new AreaCatalog.Entrada("Mercado Municipal", AreaCatalog.Grupo.DIRECCION,
            "Secretaría General Municipal", "MM");
        List<AreaCatalog.Entrada> creadas = new ArrayList<>();
        List<String> lotes = new ArrayList<>();
        TransferenciaRapidaService transferencias = new TransferenciaRapidaService(null, null, id -> Optional.empty()) {
            @Override public Resultado transferir(List<Producto> bienes, String destino, String motivo, String persona, String cargo) {
                lotes.add(destino + " | " + motivo + " | " + persona + " | " + cargo + " | "
                    + bienes.stream().map(Producto::getCodigo).toList());
                Movimiento m = new Movimiento();
                Resguardo r = persona == null ? null : resguardo("RSG-NUEVO", cargo);
                return new Resultado(bienes.stream().map(b -> m).toList(), List.of(), r);
            }
        };
        List<SepararArea.Grupo> grupos = List.of(
            new SepararArea.Grupo("JUAN CARLOS FICTICIO CORTES", "TESORERO DEL MERCADO", List.of(bien("RCE/01", "x"), bien("RCE/02", "x"))),
            new SepararArea.Grupo(null, null, List.of(bien("RCE/97", ""))));

        SepararArea.Resultado r = SepararArea.aplicar(mercado, grupos,
            SepararArea.motivo("Mercado Municipal", "Reglamentos, Comercio y Espectáculos"), creadas::add, transferencias);

        assertEquals(List.of(mercado), creadas);
        assertTrue(r.areaNueva());
        assertEquals(3, r.bienes());
        assertEquals(1, r.resguardos().size(), "solo quien tiene persona recibe resguardo");
        assertEquals(List.of(
            "Mercado Municipal | Separación de Mercado Municipal de Reglamentos, Comercio y Espectáculos | JUAN CARLOS FICTICIO CORTES | TESORERO DEL MERCADO | [RCE/01, RCE/02]",
            "Mercado Municipal | Separación de Mercado Municipal de Reglamentos, Comercio y Espectáculos | null | null | [RCE/97]"), lotes);
    }

    @Test
    void aplicar_sinBienes_noCreaNada() {
        List<AreaCatalog.Entrada> creadas = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> SepararArea.aplicar(
            new AreaCatalog.Entrada("Mercado Municipal", AreaCatalog.Grupo.DIRECCION, "Secretaría General Municipal", "MM"),
            List.of(), "m", creadas::add, null));
        assertTrue(creadas.isEmpty());
    }
}
