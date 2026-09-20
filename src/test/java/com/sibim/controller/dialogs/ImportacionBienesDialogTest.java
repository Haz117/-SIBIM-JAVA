package com.sibim.controller.dialogs;

import com.sibim.model.Categoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the CSV import parsing/validation logic in isolation from the JavaFX
 * dialog — this is the code that decides whether a bulk-imported bien is
 * trustworthy data or garbage, so it deserves real coverage. Before this,
 * only a shallow smoke test (dialog loads without throwing) existed for the
 * whole class: see ImportacionBienesDialog#parseCsvFile and its private
 * helpers, made package-private specifically so this test can call them
 * directly instead of driving the full Dialog through TestFX.
 */
class ImportacionBienesDialogTest {

    @TempDir Path tmp;

    private static final List<Categoria> CATEGORIAS = List.of(
        new Categoria("cat-mob", "Mobiliario", null, "#8B5CF6", "🪑", null),
        new Categoria("cat-comp", "Equipo de Cómputo", null, "#10B981", "💻", null)
    );

    private Path csv(String... lines) throws IOException {
        Path f = tmp.resolve("import.csv");
        Files.writeString(f, String.join("\n", lines), StandardCharsets.UTF_8);
        return f;
    }

    // ── parseCsvFile: happy path ─────────────────────────────────────────

    @Test void filaValida_seParseaComoOk() throws Exception {
        Path f = csv(
            "Nombre,Codigo,Categoria,Area,Resguardante,Cantidad,Stock Min,Stock Max,Precio Compra,Precio Venta,Proveedor,Marca,Modelo,Numero Serie,Ubicacion,Descripcion,Fecha Adquisicion",
            "Laptop Dell,LAP-01,Mobiliario,Despacho de Presidencia,Juan Pérez,1,1,3,20000,22000,Dell,Dell,XPS,SN123,Oficina 1,,2024-01-15"
        );
        List<ImportacionBienesDialog.ParsedRow> rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals(1, rows.size());
        assertEquals("ok", rows.get(0).status());
        assertNull(rows.get(0).error());
        assertEquals("Laptop Dell", rows.get(0).producto().getNombre());
        assertEquals("LAP-01", rows.get(0).producto().getCodigo());
        assertEquals("Mobiliario", rows.get(0).producto().getCategoriaNombre());
        assertEquals("Despacho de Presidencia", rows.get(0).producto().getArea());
    }

    @Test void codigoEnBlanco_seAutogeneraConPrefijoImp() throws Exception {
        Path f = csv(
            "Nombre,Codigo,Categoria,Area",
            "Silla,,Mobiliario,Despacho de Presidencia"
        );
        List<ImportacionBienesDialog.ParsedRow> rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("ok", rows.get(0).status());
        assertEquals("IMP-0001", rows.get(0).producto().getCodigo());
    }

    // ── Validaciones que deben RECHAZAR la fila ──────────────────────────

    @Test void nombreEnBlanco_esRechazada() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", ",X-01,Mobiliario,Despacho de Presidencia");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("error", rows.get(0).status());
        assertEquals("Nombre requerido", rows.get(0).error());
        assertNull(rows.get(0).producto());
    }

    @Test void categoriaDesconocida_esRechazada() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "Bien,X-01,Categoria Inventada,Despacho de Presidencia");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("error", rows.get(0).status());
        assertTrue(rows.get(0).error().contains("Categoría no encontrada"));
    }

    /** Éste es el escenario exacto que rompió Organigrama con el import MLA:
     *  un área que no existe en el organigrama real no debe colarse como un
     *  bien "válido" — debe rechazarse en la importación manual, no fallar
     *  silenciosamente después en el organigrama. */
    @Test void areaDesconocida_esRechazada() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "Bien,X-01,Mobiliario,Departamento Que No Existe");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("error", rows.get(0).status());
        assertTrue(rows.get(0).error().contains("Área no reconocida"));
    }

    @Test void areaEnBlanco_esRechazada() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "Bien,X-01,Mobiliario,");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("error", rows.get(0).status());
        assertEquals("Área requerida", rows.get(0).error());
    }

    @Test void codigoDuplicadoEnElMismoArchivo_esRechazado() throws Exception {
        Path f = csv(
            "Nombre,Codigo,Categoria,Area",
            "Bien 1,DUP-01,Mobiliario,Despacho de Presidencia",
            "Bien 2,DUP-01,Mobiliario,Despacho de Presidencia"
        );
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("ok", rows.get(0).status());
        assertEquals("error", rows.get(1).status());
        assertTrue(rows.get(1).error().contains("Código duplicado"));
    }

    @Test void stockMinimoMayorQueMaximo_esRechazado() throws Exception {
        Path f = csv(
            "Nombre,Codigo,Categoria,Area,Cantidad,Stock Min,Stock Max",
            "Bien,X-01,Mobiliario,Despacho de Presidencia,5,10,3"
        );
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("error", rows.get(0).status());
        assertTrue(rows.get(0).error().contains("Stock mínimo"));
    }

    @Test void columnaNombreFaltante_lanzaExcepcion() throws Exception {
        Path f = csv("Categoria,Area", "Mobiliario,Despacho de Presidencia");
        assertThrows(IOException.class, () -> ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS));
    }

    @Test void columnaCategoriaFaltante_lanzaExcepcion() throws Exception {
        Path f = csv("Nombre,Area", "Bien,Despacho de Presidencia");
        assertThrows(IOException.class, () -> ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS));
    }

    @Test void archivoVacio_lanzaExcepcion() throws Exception {
        Path f = tmp.resolve("vacio.csv");
        Files.writeString(f, "");
        assertThrows(IOException.class, () -> ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS));
    }

    // ── Emparejamiento difuso de área/categoría (acentos, mayúsculas, parcial) ──

    @Test void areaConAcentosYMayusculasDistintas_seReconoce() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "Bien,X-01,Mobiliario,DESPACHO DE PRESIDENCIA");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("ok", rows.get(0).status());
        assertEquals("Despacho de Presidencia", rows.get(0).producto().getArea());
    }

    @Test void areaParcial_seReconocePorCoincidenciaDifusa() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "Bien,X-01,Mobiliario,Presidencia");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("ok", rows.get(0).status());
        assertEquals("Despacho de Presidencia", rows.get(0).producto().getArea());
    }

    @Test void categoriaConTildeFaltante_seReconoce() throws Exception {
        Path f = csv("Nombre,Codigo,Categoria,Area", "PC,X-01,Equipo de Computo,Despacho de Presidencia");
        var rows = ImportacionBienesDialog.parseCsvFile(f.toFile(), CATEGORIAS);
        assertEquals("ok", rows.get(0).status());
        assertEquals("Equipo de Cómputo", rows.get(0).producto().getCategoriaNombre());
    }

    // ── parseCsvLine ──────────────────────────────────────────────────────

    @Test void parseCsvLine_camposSimples() {
        assertArrayEquals(new String[]{"a", "b", "c"}, ImportacionBienesDialog.parseCsvLine("a,b,c"));
    }

    @Test void parseCsvLine_campoConComaEntreComillas() {
        assertArrayEquals(new String[]{"Ciudad, Estado", "b"},
            ImportacionBienesDialog.parseCsvLine("\"Ciudad, Estado\",b"));
    }

    @Test void parseCsvLine_comillaEscapadaDentroDeCampo() {
        assertArrayEquals(new String[]{"Dice \"Hola\""},
            ImportacionBienesDialog.parseCsvLine("\"Dice \"\"Hola\"\"\""));
    }

    // ── normalize ─────────────────────────────────────────────────────────

    @Test void normalize_quitaAcentosMayusculasYPuntuacion() {
        assertEquals("direccion juridica", ImportacionBienesDialog.normalize("Dirección Jurídica."));
    }

    @Test void normalize_null_retornaVacio() {
        assertEquals("", ImportacionBienesDialog.normalize(null));
    }

    // ── parseIntSafe / parseBigDecimalSafe / parseDateSafe ───────────────

    @Test void parseIntSafe_valorInvalido_retornaDefault() {
        assertEquals(7, ImportacionBienesDialog.parseIntSafe("no-es-numero", 7));
    }

    @Test void parseIntSafe_conTextoAlrededor_extraeDigitos() {
        assertEquals(15, ImportacionBienesDialog.parseIntSafe("15 piezas", 0));
    }

    @Test void parseBigDecimalSafe_conSimboloPesoYComas() {
        assertEquals(0, new BigDecimal("12345.67").compareTo(
            ImportacionBienesDialog.parseBigDecimalSafe("$12,345.67", BigDecimal.ZERO)));
    }

    @Test void parseBigDecimalSafe_valorInvalido_retornaDefault() {
        assertEquals(0, BigDecimal.TEN.compareTo(
            ImportacionBienesDialog.parseBigDecimalSafe("no-es-precio", BigDecimal.TEN)));
    }

    @Test void parseDateSafe_formatoIso() {
        assertEquals(LocalDate.of(2024, 1, 15), ImportacionBienesDialog.parseDateSafe("2024-01-15"));
    }

    @Test void parseDateSafe_formatoDiaMesAnio() {
        assertEquals(LocalDate.of(2024, 1, 15), ImportacionBienesDialog.parseDateSafe("15/01/2024"));
    }

    @Test void parseDateSafe_formatoInvalido_retornaNull() {
        assertNull(ImportacionBienesDialog.parseDateSafe("no es una fecha"));
    }
}
