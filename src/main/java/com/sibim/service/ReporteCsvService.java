package com.sibim.service;

import com.sibim.model.Comodato;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all CSV exports for SIBIM.
 * Extends ReporteService to reuse the protected helpers:
 * tempFile, FMT, esc.
 * Repos are passed in and forwarded to the super constructor so no duplicate
 * DB connections are opened.
 */
public class ReporteCsvService extends ReporteService {

    ReporteCsvService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        super(productoRepo, movimientoRepo);
    }

    // ──────────────────────── Inventario ────────────────────────────────

    public File exportInventarioCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = guardExportSize((desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll(), "bienes");
        if (productos.isEmpty()) return null;
        return exportInventarioCsv(productos);
    }

    /** Same CSV report, given an explicit list — see the Excel overload in ReporteExcelService. */
    public File exportInventarioCsv(List<Producto> productos) throws Exception {
        File file = tempFile("inventario", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Codigo,Categoria,Area,Resguardante,Stock,Stock Min,Stock Max,Precio Compra,Precio Venta,Valor Total,Estado,Proveedor,Marca,Modelo,N° de Serie,Ubicacion,Fecha Registro");
            for (Producto p : productos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%.2f,%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getCategoriaNombre()), esc(p.getArea()), esc(p.getResguardante()),
                    p.getStockActual(), p.getStockMinimo(), p.getStockMaximo(),
                    p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO,
                    p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ZERO,
                    p.getValorTotal(),
                    p.getEstado().getEtiqueta(),
                    esc(p.getProveedor()),
                    esc(p.getMarca()), esc(p.getModelo()), esc(p.getNumeroSerie()),
                    esc(p.getUbicacion()),
                    p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
            }
        }
        return file;
    }

    // ──────────────────────── Movimientos ───────────────────────────────

    public File exportMovimientosCsv(List<Movimiento> movimientos) throws Exception {
        File file = tempFile("movimientos", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Producto,Tipo,Cantidad,Stock Anterior,Stock Nuevo,Motivo,Referencia,Usuario,Fecha");
            for (Movimiento m : movimientos) {
                pw.printf("\"%s\",\"%s\",%d,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(m.getProductoNombre()), m.getTipo().getEtiqueta(),
                    m.getCantidad(), m.getStockAnterior(), m.getStockNuevo(),
                    esc(m.getMotivo()), esc(m.getReferencia()),
                    esc(m.getUsuarioNombre()), FormatUtils.formatDateTime(m.getCreadoEn()));
            }
        }
        return file;
    }

    public File exportMovimientosCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
        return exportMovimientosCsv(movimientos);
    }

    // ──────────────────────── Distribución ──────────────────────────────

    public File exportDistribucionCsv() throws Exception {
        List<Producto> productos = guardExportSize(productoRepo.findAll(), "bienes");
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));

        File file = tempFile("distribucion", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Área,Total Bienes,Valor Total,Agotados,Bajo Stock");
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<Producto> ps = entry.getValue();
                long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
                long bajoStock = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
                BigDecimal valor = ps.stream().map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                pw.printf("\"%s\",%d,%.2f,%d,%d%n",
                    esc(entry.getKey()), ps.size(), valor, agotados, bajoStock);
            }
        }
        return file;
    }

    // ──────────────────────── Alertas ───────────────────────────────────

    public File exportAlertasCsv() throws Exception {
        List<Producto> todos     = guardExportSize(productoRepo.findAll(), "bienes");
        List<Producto> agotados  = todos.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = todos.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        File file = tempFile("alertas", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Estado,Nombre,Codigo,Stock Actual,Stock Minimo,Area,Proveedor");
            for (Producto p : agotados)
                pw.printf("\"Agotado\",\"%s\",\"%s\",%d,%d,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()), p.getStockActual(), p.getStockMinimo(),
                    esc(p.getArea()), esc(p.getProveedor()));
            for (Producto p : bajoStock)
                pw.printf("\"Bajo Stock\",\"%s\",\"%s\",%d,%d,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()), p.getStockActual(), p.getStockMinimo(),
                    esc(p.getArea()), esc(p.getProveedor()));
        }
        return file;
    }

    // ──────────────────────── Resguardos ────────────────────────────────

    public File exportResguardosCsv(List<Resguardo> resguardos) throws Exception {
        File file = tempFile("resguardos", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Folio,Resguardante,Área,Cargo,Fecha,Bienes,Estado");
            for (Resguardo r : resguardos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\"%n",
                    esc(r.getNumero()), esc(r.getResguardanteNombre()),
                    esc(r.getResguardanteArea()), esc(r.getResguardanteCargo()),
                    r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate().format(FMT) : "",
                    r.getItems().size(), esc(r.getEstado()));
            }
        }
        return file;
    }

    // ──────────────────────── Comodatos ─────────────────────────────────

    public File exportComodatosCsv(List<Comodato> comodatos) throws Exception {
        File file = tempFile("comodatos", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Folio,Bien,Código,Entidad,Contacto,Fecha Inicio,Fecha Fin,Estado");
            for (Comodato c : comodatos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(c.getNumero()), esc(c.getProductoNombre()), esc(c.getProductoCodigo()),
                    esc(c.getEntidadReceptora()), esc(c.getContactoNombre()),
                    c.getFechaInicio() != null ? c.getFechaInicio().format(FMT) : "",
                    c.getFechaFin() != null ? c.getFechaFin().format(FMT) : "",
                    esc(c.getEstadoEfectivo()));
            }
        }
        return file;
    }

    // ──────────────────────── Préstamos ─────────────────────────────────

    public File exportPrestamosCsv(List<Prestamo> prestamos) throws Exception {
        File file = tempFile("prestamos", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Folio,Bien,Código,Área Origen,Área Destino,Responsable,Fecha Préstamo,Devolución Prevista,Estado");
            for (Prestamo p : prestamos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(p.getNumero()), esc(p.getProductoNombre()), esc(p.getProductoCodigo()),
                    esc(p.getAreaOrigen()), esc(p.getAreaDestino()), esc(p.getResponsableNombre()),
                    p.getFechaPrestamo() != null ? p.getFechaPrestamo().format(FMT) : "",
                    p.getFechaDevolucionPrevista() != null ? p.getFechaDevolucionPrevista().format(FMT) : "",
                    esc(p.isVencidoCalc() ? Prestamo.ESTADO_VENCIDO : p.getEstado()));
            }
        }
        return file;
    }
}
