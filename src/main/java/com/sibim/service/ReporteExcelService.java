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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all Excel (.xlsx) exports for SIBIM.
 * Extends ReporteService to reuse the protected Excel helpers:
 * tempFile, createSheet, writeHeader, autosizeColumns, addExcelInfoSheet, FMT, esc.
 * Repos are passed in and forwarded to the super constructor so no duplicate
 * DB connections are opened.
 */
public class ReporteExcelService extends ReporteService {

    ReporteExcelService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        super(productoRepo, movimientoRepo);
    }

    // ──────────────────────── Inventario ────────────────────────────────

    public File exportInventarioExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        if (productos.isEmpty()) return null;
        return exportInventarioExcel(guardExportSize(productos, "bienes"), desde, hasta);
    }

    /** Same Excel report, given an explicit list — bulk action / selection export. */
    public File exportInventarioExcel(List<Producto> productos) throws Exception {
        return exportInventarioExcel(productos, null, null);
    }

    private File exportInventarioExcel(List<Producto> productos, LocalDate desde, LocalDate hasta) throws Exception {
        String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Resguardante", "Stock", "Min", "Max",
                            "Precio Venta", "Valor Total", "Estado", "Proveedor", "Marca", "Modelo",
                            "N° de Serie", "Ubicacion", "Fecha Registro", "Estado Físico", "N° Factura"};
        File file = tempFile("inventario", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Inventario");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Producto p : productos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCodigo());
                r.createCell(2).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(3).setCellValue(p.getArea());
                r.createCell(4).setCellValue(p.getResguardante() != null ? p.getResguardante() : "");
                r.createCell(5).setCellValue(p.getStockActual());
                r.createCell(6).setCellValue(p.getStockMinimo());
                r.createCell(7).setCellValue(p.getStockMaximo());
                r.createCell(8).setCellValue(p.getPrecioVenta() != null ? p.getPrecioVenta().doubleValue() : 0);
                r.createCell(9).setCellValue(p.getValorTotal().doubleValue());
                r.createCell(10).setCellValue(p.getEstado().getEtiqueta());
                r.createCell(11).setCellValue(p.getProveedor() != null ? p.getProveedor() : "");
                r.createCell(12).setCellValue(p.getMarca() != null ? p.getMarca() : "");
                r.createCell(13).setCellValue(p.getModelo() != null ? p.getModelo() : "");
                r.createCell(14).setCellValue(p.getNumeroSerie() != null ? p.getNumeroSerie() : "");
                r.createCell(15).setCellValue(p.getUbicacion() != null ? p.getUbicacion() : "");
                r.createCell(16).setCellValue(p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
                r.createCell(17).setCellValue(p.getEstadoFisico() != null ? p.getEstadoFisico() : "");
                r.createCell(18).setCellValue(p.getNumeroFactura() != null ? p.getNumeroFactura() : "");
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Inventario General", desde, hasta);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ──────────────────────── Movimientos ───────────────────────────────

    public File exportMovimientosExcel(List<Movimiento> movimientos) throws Exception {
        return exportMovimientosExcelImpl(movimientos, null, null);
    }

    public File exportMovimientosExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
        return exportMovimientosExcelImpl(movimientos, desde, hasta);
    }

    private File exportMovimientosExcelImpl(List<Movimiento> movimientos, LocalDate desde, LocalDate hasta) throws Exception {
        String[] headers = {"Producto", "Tipo", "Cantidad", "Stock Anterior", "Stock Nuevo",
                            "Motivo", "Referencia", "Usuario", "Fecha"};
        File file = tempFile("movimientos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Movimientos");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Movimiento m : movimientos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(m.getProductoNombre());
                r.createCell(1).setCellValue(m.getTipo().getEtiqueta());
                r.createCell(2).setCellValue(m.getCantidad());
                r.createCell(3).setCellValue(m.getStockAnterior());
                r.createCell(4).setCellValue(m.getStockNuevo());
                r.createCell(5).setCellValue(m.getMotivo() != null ? m.getMotivo() : "");
                r.createCell(6).setCellValue(m.getReferencia() != null ? m.getReferencia() : "");
                r.createCell(7).setCellValue(m.getUsuarioNombre());
                r.createCell(8).setCellValue(FormatUtils.formatDateTime(m.getCreadoEn()));
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Registro de Movimientos", desde, hasta);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ──────────────────────── Distribución ──────────────────────────────

    public File exportDistribucionExcel() throws Exception {
        List<Producto> productos = guardExportSize(productoRepo.findAll(), "bienes");
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));

        File file = tempFile("distribucion", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: summary per area
            Sheet summary = createSheet(wb, "Resumen por Área");
            String[] sumHeaders = {"Área", "Total Bienes", "Valor Total ($)", "Agotados", "Bajo Stock"};
            writeHeader(summary, sumHeaders, wb);
            int row = 1;
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<Producto> ps = entry.getValue();
                long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
                long bajoStock = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
                BigDecimal valor = ps.stream().map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                Row r = summary.createRow(row++);
                r.createCell(0).setCellValue(entry.getKey());
                r.createCell(1).setCellValue(ps.size());
                r.createCell(2).setCellValue(valor.doubleValue());
                r.createCell(3).setCellValue(agotados);
                r.createCell(4).setCellValue(bajoStock);
            }
            autosizeColumns(summary, sumHeaders.length);

            // Sheet 2: all items grouped by area
            Sheet detail = createSheet(wb, "Detalle por Área");
            String[] detHeaders = {"Área", "Nombre", "Código", "Stock Actual", "Stock Mínimo", "Estado", "Valor Total ($)"};
            writeHeader(detail, detHeaders, wb);
            row = 1;
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                for (Producto p : entry.getValue()) {
                    Row r = detail.createRow(row++);
                    r.createCell(0).setCellValue(entry.getKey());
                    r.createCell(1).setCellValue(p.getNombre());
                    r.createCell(2).setCellValue(p.getCodigo());
                    r.createCell(3).setCellValue(p.getStockActual());
                    r.createCell(4).setCellValue(p.getStockMinimo());
                    r.createCell(5).setCellValue(p.getEstado().getEtiqueta());
                    r.createCell(6).setCellValue(p.getValorTotal().doubleValue());
                }
            }
            autosizeColumns(detail, detHeaders.length);
            addExcelInfoSheet(wb, "Distribución por Área", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ──────────────────────── Alertas ───────────────────────────────────

    public File exportAlertasExcel() throws Exception {
        List<Producto> todos     = guardExportSize(productoRepo.findAll(), "bienes");
        List<Producto> agotados  = todos.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = todos.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        String[] headers = {"Nombre", "Codigo", "Stock Actual", "Stock Minimo", "Estado"};
        File file = tempFile("alertas", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Alertas");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Producto p : agotados) {
                Row r = sheet.createRow(row++);
                fillAlertRow(r, p);
            }
            for (Producto p : bajoStock) {
                Row r = sheet.createRow(row++);
                fillAlertRow(r, p);
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Alertas de Stock", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    private void fillAlertRow(Row r, Producto p) {
        r.createCell(0).setCellValue(p.getNombre());
        r.createCell(1).setCellValue(p.getCodigo());
        r.createCell(2).setCellValue(p.getStockActual());
        r.createCell(3).setCellValue(p.getStockMinimo());
        r.createCell(4).setCellValue(p.getEstado().getEtiqueta());
    }

    // ──────────────────────── Resguardos ────────────────────────────────

    public File exportResguardosExcel(List<Resguardo> resguardos) throws Exception {
        String[] headers = {"Folio", "Resguardante", "Área", "Cargo", "Fecha", "Bienes", "Estado"};
        File file = tempFile("resguardos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Resguardos");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Resguardo r : resguardos) {
                Row rw = sheet.createRow(row++);
                rw.createCell(0).setCellValue(r.getNumero());
                rw.createCell(1).setCellValue(r.getResguardanteNombre() != null ? r.getResguardanteNombre() : "");
                rw.createCell(2).setCellValue(r.getResguardanteArea() != null ? r.getResguardanteArea() : "");
                rw.createCell(3).setCellValue(r.getResguardanteCargo() != null ? r.getResguardanteCargo() : "");
                rw.createCell(4).setCellValue(r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate().format(FMT) : "");
                rw.createCell(5).setCellValue(r.getItems().size());
                rw.createCell(6).setCellValue(r.getEstado());
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Resguardos de Bienes", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ──────────────────────── Comodatos ─────────────────────────────────

    public File exportComodatosExcel(List<Comodato> comodatos) throws Exception {
        String[] headers = {"Folio", "Bien", "Código", "Entidad", "Contacto", "Fecha Inicio", "Fecha Fin", "Estado"};
        File file = tempFile("comodatos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Comodatos");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Comodato c : comodatos) {
                Row rw = sheet.createRow(row++);
                rw.createCell(0).setCellValue(c.getNumero());
                rw.createCell(1).setCellValue(c.getProductoNombre() != null ? c.getProductoNombre() : "");
                rw.createCell(2).setCellValue(c.getProductoCodigo() != null ? c.getProductoCodigo() : "");
                rw.createCell(3).setCellValue(c.getEntidadReceptora() != null ? c.getEntidadReceptora() : "");
                rw.createCell(4).setCellValue(c.getContactoNombre() != null ? c.getContactoNombre() : "");
                rw.createCell(5).setCellValue(c.getFechaInicio() != null ? c.getFechaInicio().format(FMT) : "");
                rw.createCell(6).setCellValue(c.getFechaFin() != null ? c.getFechaFin().format(FMT) : "");
                rw.createCell(7).setCellValue(c.getEstadoEfectivo());
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Comodatos", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ──────────────────────── Préstamos ─────────────────────────────────

    public File exportPrestamosExcel(List<Prestamo> prestamos) throws Exception {
        String[] headers = {"Folio", "Bien", "Código", "Área Origen", "Área Destino",
                            "Responsable", "Fecha Préstamo", "Devolución Prevista", "Estado"};
        File file = tempFile("prestamos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Préstamos");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Prestamo p : prestamos) {
                Row rw = sheet.createRow(row++);
                rw.createCell(0).setCellValue(p.getNumero());
                rw.createCell(1).setCellValue(p.getProductoNombre() != null ? p.getProductoNombre() : "");
                rw.createCell(2).setCellValue(p.getProductoCodigo() != null ? p.getProductoCodigo() : "");
                rw.createCell(3).setCellValue(p.getAreaOrigen() != null ? p.getAreaOrigen() : "");
                rw.createCell(4).setCellValue(p.getAreaDestino() != null ? p.getAreaDestino() : "");
                rw.createCell(5).setCellValue(p.getResponsableNombre() != null ? p.getResponsableNombre() : "");
                rw.createCell(6).setCellValue(p.getFechaPrestamo() != null ? p.getFechaPrestamo().format(FMT) : "");
                rw.createCell(7).setCellValue(p.getFechaDevolucionPrevista() != null ? p.getFechaDevolucionPrevista().format(FMT) : "");
                rw.createCell(8).setCellValue(p.isVencidoCalc() ? Prestamo.ESTADO_VENCIDO : p.getEstado());
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Préstamos de Bienes", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }
}
