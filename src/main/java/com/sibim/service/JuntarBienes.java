package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.JuntarBienesRepository;
import com.sibim.repository.JuntarBienesRepository.Huella;
import com.sibim.session.SessionManager;
import com.sibim.util.FormatUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Bienes › «Son el mismo bien»: two records of one bien become one. In November 2026 the 136
 *  copies left by the load were removed with SQL scripts run from PowerShell; with this,
 *  Patrimonio resolves the cases it checks in person (two areas with the same desk, the same
 *  laptop in Presidencia and Educación…) from SIBIM, and can undo it. */
public final class JuntarBienes {

    private final JuntarBienesRepository repo;
    private final AuditLogRepository audit;

    public JuntarBienes() { this(new JuntarBienesRepository(), new AuditLogRepository()); }
    JuntarBienes(JuntarBienesRepository repo, AuditLogRepository audit) { this.repo = repo; this.audit = audit; }

    /** One dato the bien that stays takes from the other, because it has nothing there. */
    public record Paso(String etiqueta, String columna, Object valor, String texto) {}

    private record Campo(String etiqueta, String columna, Function<Producto, Object> leer) {}

    private static final List<Campo> CAMPOS = List.of(
        new Campo("Foto", "foto_url", Producto::getFotoUrl),
        new Campo("Costo", "precio_compra", Producto::getPrecioCompra),
        new Campo("Fecha de adquisición", "fecha_adquisicion", Producto::getFechaAdquisicion),
        new Campo("Serie", "numero_serie", Producto::getNumeroSerie),
        new Campo("Marca", "marca", Producto::getMarca),
        new Campo("Modelo", "modelo", Producto::getModelo),
        new Campo("Factura", "numero_factura", Producto::getNumeroFactura),
        new Campo("Folio fiscal", "folio_fiscal", Producto::getFolioFiscal),
        new Campo("Proveedor", "proveedor", Producto::getProveedor),
        new Campo("Vida útil", "vida_util_anios", Producto::getVidaUtilAnios),
        new Campo("Valor residual", "valor_residual", Producto::getValorResidual),
        new Campo("Clave CONAC", "clave_armonizada", Producto::getClaveArmonizada),
        new Campo("Estado físico", "estado_fisico", Producto::getEstadoFisico),
        new Campo("Ubicación", "ubicacion", Producto::getUbicacion),
        new Campo("Color", "color", Producto::getColor),
        new Campo("Tipo de bien", "tipo_bien", Producto::getTipoBien),
        new Campo("No. de motor", "no_motor", Producto::getNoMotor),
        new Campo("Tarjeta de circulación", "no_tarjeta_circulacion", Producto::getNoTarjetaCirculacion),
        new Campo("Póliza de seguro", "no_poliza_seguro", Producto::getNoPolizaSeguro),
        new Campo("Archivo de la factura", "factura_url", Producto::getFacturaUrl));

    /** What {@code queda} takes from {@code sale}: each dato it lacks and the other has, and a
     *  line in its description with the other's numbers, so a label with the old one is found. */
    public static List<Paso> loQuePasa(Producto queda, Producto sale, LocalDate hoy) {
        List<Paso> out = new ArrayList<>();
        for (Campo c : CAMPOS) {
            Object a = c.leer().apply(queda), b = c.leer().apply(sale);
            if (vacio(a) && !vacio(b)) {
                out.add(new Paso(c.etiqueta(), c.columna(), b, texto(b)));
                if ("precio_compra".equals(c.columna())) out.add(new Paso(null, "precio_venta", b, null));
            }
        }
        String ant = inventarioAnterior(sale);
        String desc = Objects.requireNonNullElse(queda.getDescripcion(), "").trim();
        String nota = (ant != null && !desc.toUpperCase(Locale.ROOT).contains(ant.toUpperCase(Locale.ROOT))
                ? "También tenía el número " + ant + " (su registro repetido " + sale.getCodigo() + ", juntado el " + FormatUtils.formatDate(hoy) + ")."
                : "Se juntó con su registro repetido " + sale.getCodigo() + " el " + FormatUtils.formatDate(hoy) + ".");
        out.add(new Paso("Nota en la descripción", "descripcion", desc.isEmpty() ? nota : desc + (desc.endsWith(".") ? " " : ". ") + nota, nota));
        return out;
    }

    /** Which one stays when nobody says: the one with a photo, a resguardante and more data. */
    public static Producto sugerirQueda(Producto a, Producto b) {
        return puntos(b) > puntos(a) ? b : a;
    }

    private static int puntos(Producto p) {
        int n = (!vacio(p.getFotoUrl()) ? 10 : 0) + (!vacio(p.getResguardante()) ? 5 : 0);
        for (Campo c : CAMPOS) if (!vacio(c.leer().apply(p))) n++;
        return n;
    }

    /** Joins them: {@code sale} leaves, {@code queda} takes its data, photos and history.
     *  @param motivo why they are the same bien, for Auditoría (may be empty) */
    public Huella juntar(Producto queda, Producto sale, String motivo) throws SQLException, ProductoService.ValidationException {
        if (!SessionManager.isAdmin())
            throw new ProductoService.ValidationException("Solo Patrimonio puede juntar bienes");
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            throw new ProductoService.ValidationException("Juntar bienes necesita conexión con la base de datos");
        if (Objects.equals(queda.getId(), sale.getId()))
            throw new ProductoService.ValidationException("Elige dos bienes distintos");
        List<Paso> pasos = loQuePasa(queda, sale, LocalDate.now());
        Map<String, Object> poner = new LinkedHashMap<>();
        for (Paso p : pasos) poner.put(p.columna(), p.valor());
        String nota = " · era el registro repetido " + sale.getCodigo() + ", juntado con " + queda.getCodigo();
        Huella h = repo.juntar(queda.getId(), sale.getId(), poner, nota);
        ProductosEnMemoria.invalidar();
        String pasaron = pasos.stream().map(Paso::etiqueta).filter(Objects::nonNull)
            .filter(e -> !e.startsWith("Nota")).reduce((x, y) -> x + ", " + y).orElse("ningún dato");
        String m = motivo == null || motivo.isBlank() ? "" : " Motivo: " + motivo.trim() + ".";
        audit.log("producto", queda.getId(), queda.getNombre(), "actualizar",
            "Se juntó con su registro repetido " + sale.getCodigo() + " · número " + queda.getCodigo() + " · Pasó: " + pasaron
                + (h.filasDeHistorial() > 0 ? " · " + FormatUtils.plural(h.filasDeHistorial(), "registro de historial", "registros de historial") : "")
                + (h.pasoElResguardo() ? " · su lugar en el resguardo" : "") + "." + m);
        audit.log("producto", sale.getId(), sale.getNombre(), "eliminar",
            "Repetido: es el mismo bien que " + queda.getCodigo() + " (" + Objects.requireNonNullElse(queda.getResguardante(), "sin resguardante")
                + ", " + Objects.requireNonNullElse(queda.getArea(), "") + ") · número " + sale.getCodigo()
                + (sale.getPrecioCompra() != null ? " · " + FormatUtils.formatCurrency(sale.getPrecioCompra()) : "") + "." + m);
        return h;
    }

    /** Puts both records back as they were. */
    public void deshacer(Huella h) throws SQLException {
        repo.deshacer(h);
        ProductosEnMemoria.invalidar();
        audit.log("producto", h.saleId(), h.saleCodigo(), "crear",
            "Se deshizo juntar " + h.saleCodigo() + " con otro bien: volvió como estaba");
    }

    /** What the inventory writes when it has nothing: it isn't a dato to keep. */
    private static final Set<String> NADA = Set.of("SIN SERIE", "SIN MARCA", "SIN MODELO", "SIN DATO", "SIN DOCUMENTO",
        "SIN FACTURA", "S/N", "SN", "N/A", "NA", "S/M", "-", "--");

    private static boolean vacio(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank() || NADA.contains(s.trim().toUpperCase(Locale.ROOT)) || CalidadDatosService.noEsSerie(s);
        if (v instanceof BigDecimal b) return b.signum() == 0;
        return false;
    }

    private static String texto(Object v) {
        if (v instanceof BigDecimal b) return FormatUtils.formatCurrency(b);
        if (v instanceof LocalDate d) return FormatUtils.formatDate(d);
        if (v instanceof Integer i) return i + (i == 1 ? " año" : " años");
        String s = String.valueOf(v).trim();
        if (s.startsWith("http") || s.contains("/") && s.length() > 60) return "sí";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    static String inventarioAnterior(Producto p) {
        String d = p.getDescripcion();
        if (d == null) return null;
        int i = d.indexOf("Inventario anterior:");
        if (i < 0) return null;
        String resto = d.substring(i + "Inventario anterior:".length()).trim();
        int punto = resto.indexOf('.');
        String ant = (punto >= 0 ? resto.substring(0, punto) : resto).trim();
        return ant.isEmpty() ? null : ant;
    }
}
