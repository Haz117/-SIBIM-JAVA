package com.sibim.db.integration;

import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.model.Comodato;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ComodatoRepository;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.ActaService;
import com.sibim.service.ComodatoService;
import com.sibim.service.PrestamoService;
import com.sibim.service.ResguardoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The documents Patrimonio makes for a bien, from beginning to end against a
 * real database: registered, printed (the PDF and Excel are actually built)
 * and closed. Each one also keeps the others out while it holds the bien.
 */
class FlujoDeDocumentosIntegrationTest extends IntegrationTestBase {

    private static final String TESORERIA = "Tesorería Municipal";
    private static final String CATASTRO  = "Dirección de Catastro";
    private static final String BIEN      = "bien-documentos";

    private final ProductoRepository productos = new ProductoRepository();

    @BeforeEach
    void unBien() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO categories (id, nombre, color) VALUES ('cat-doc', 'Equipo de cómputo', '#3B82F6')")) {
            ps.executeUpdate();
        }
        Producto p = new Producto();
        p.setId(BIEN);
        p.setNombre("Laptop Dell Latitude");
        p.setCodigo("TES-001");
        p.setCategoriaId("cat-doc");
        p.setPrecioCompra(new BigDecimal("18500.00"));
        p.setPrecioVenta(new BigDecimal("18500.00"));
        p.setStockActual(1);
        p.setStockMaximo(1);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(TESORERIA);
        p.setNumeroSerie("SN-12345");
        productos.saveOnline(p);
    }

    @Test
    void resguardo_seAsigna_seImprime_ySeCancela() throws Exception {
        ResguardoService resguardos = new ResguardoService();

        Resguardo r = resguardos.crear("Ana García López", "Cajera", TESORERIA, List.of(partida()), "Sin observaciones");

        assertNotNull(r.getNumero());
        assertEquals("Ana García López", productos.findById(BIEN).orElseThrow().getResguardante(),
            "el resguardo define quién tiene el bien");
        esPdf(resguardos.exportarPdf(resguardos.findById(r.getId())));
        assertEquals(1, resguardos.getByProductoId(BIEN).size());

        IllegalArgumentException repetido = assertThrows(IllegalArgumentException.class,
            () -> resguardos.crear("Luis Pérez", null, TESORERIA, List.of(partida()), null));
        assertTrue(repetido.getMessage().contains(r.getNumero()), repetido.getMessage());

        resguardos.cancelar(r.getId());
        assertFalse(resguardos.findById(r.getId()).isActivo());
        assertEquals(0, resguardos.countActivos());
        assertNotNull(resguardos.crear("Luis Pérez", null, TESORERIA, List.of(partida()), null).getNumero(),
            "cancelado el anterior, el bien se puede reasignar");
    }

    @Test
    void prestamo_seRegistra_seImprime_ySeDevuelve() throws Exception {
        PrestamoService prestamos = new PrestamoService();

        Prestamo p = prestamos.crear(BIEN, CATASTRO, "Luis Pérez", "Director", "Levantamiento en campo",
            LocalDate.now().plusDays(7));

        assertNotNull(p.getNumero());
        assertEquals(TESORERIA, p.getAreaOrigen());
        assertEquals(1, prestamos.countActivos());
        esPdf(prestamos.exportarPdf(p));
        File excel = prestamos.exportarExcel(prestamos.getAll());
        assertTrue(excel.length() > 0);

        assertThrows(IllegalArgumentException.class,
            () -> prestamos.crear(BIEN, CATASTRO, "Otra persona", null, null, LocalDate.now().plusDays(3)),
            "un bien prestado no se presta otra vez");
        assertThrows(IllegalArgumentException.class, this::comodato, "ni se entrega en comodato");

        prestamos.devolver(p.getId(), LocalDate.now());
        assertEquals(Prestamo.ESTADO_DEVUELTO, new PrestamoRepository().findById(p.getId()).getEstado());
        assertEquals(0, prestamos.countActivos());
    }

    @Test
    void comodato_seRegistra_seImprime_seConcluye_yOtroSeRescinde() throws Exception {
        ComodatoService comodatos = new ComodatoService();

        Comodato c = comodato();

        assertNotNull(c.getNumero());
        esPdf(comodatos.exportarPdf(c));
        assertEquals(1, comodatos.getVigentes().size());
        assertThrows(IllegalArgumentException.class,
            () -> new PrestamoService().crear(BIEN, CATASTRO, "Luis Pérez", null, null, LocalDate.now().plusDays(7)),
            "un bien en comodato no se presta");

        comodatos.concluir(c.getId(), LocalDate.now());
        assertEquals(Comodato.ESTADO_CONCLUIDO, new ComodatoRepository().findById(c.getId()).getEstado());

        Comodato segundo = comodato();
        comodatos.rescindir(segundo.getId(), "Incumplimiento de las condiciones");
        assertEquals(Comodato.ESTADO_RESCINDIDO, new ComodatoRepository().findById(segundo.getId()).getEstado());
        assertTrue(comodatos.getVigentes().isEmpty());
    }

    @Test
    void acta_tomaElInventarioDelMomento_ySeImprime() throws Exception {
        ActaService actas = new ActaService();

        ActaEntregaRecepcion acta = actas.generar("Jorge Ramírez", "Director saliente",
            "María Hernández", "Directora entrante", LocalDate.now(), "Entrega por cambio de administración");

        assertNotNull(acta.getNumero());
        assertEquals(1, acta.getTotalBienes());
        assertEquals(0, new BigDecimal("18500.00").compareTo(acta.getValorTotal()));
        esPdf(actas.exportarPdf(acta));
        assertEquals(1, actas.getAll().size());
    }

    private Comodato comodato() throws Exception {
        return new ComodatoService().crear(BIEN, "DIF Estatal", "María Ruiz", "Directora",
            "Av. Juárez 1, Pachuca", "Apoyo a jornada de salud", "Devolver en buen estado",
            LocalDate.now(), LocalDate.now().plusMonths(6));
    }

    private static ResguardoItem partida() {
        ResguardoItem it = new ResguardoItem();
        it.setProductoId(BIEN);
        it.setProductoNombre("Laptop Dell Latitude");
        it.setProductoCodigo("TES-001");
        it.setArea(TESORERIA);
        it.setCantidad(1);
        it.setNumeroSerie("SN-12345");
        it.setValorUnitario(new BigDecimal("18500.00"));
        return it;
    }

    private static void esPdf(File archivo) throws Exception {
        assertNotNull(archivo);
        byte[] bytes = Files.readAllBytes(archivo.toPath());
        assertTrue(bytes.length > 1000, "el PDF tiene contenido");
        assertEquals("%PDF", new String(Arrays.copyOf(bytes, 4), java.nio.charset.StandardCharsets.US_ASCII));
    }
}
