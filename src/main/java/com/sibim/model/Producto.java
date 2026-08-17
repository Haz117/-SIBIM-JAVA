package com.sibim.model;

import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.util.ProductoUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Producto {
    private String id;
    private String nombre;
    private String codigo;
    private String descripcion;
    private String categoriaId;
    private String categoriaNombre;
    private String categoriaColor;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private int stockActual;
    private int stockMinimo;
    private int stockMaximo;
    private UnidadMedida unidad;
    private String proveedor;
    private LocalDate fechaVencimiento;
    private String fotoUrl;
    private String ubicacion;
    private String area;
    private String resguardante;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalDate fechaBaja;
    private String motivoBaja;

    public Producto() {}

    /** A bien is "dado de baja" (formally decommissioned) once it has a
     *  baja date — this is a soft-delete: the record and its full movement
     *  history stay in the database for audits, it's just excluded from the
     *  active inventory by default (see ProductoRepository#findAll). */
    public boolean isDadoDeBaja() { return fechaBaja != null; }

    // --- Computed field: estado (not stored in DB) ---
    public EstadoProducto getEstado() {
        return ProductoUtils.computeEstado(stockActual, stockMinimo, fechaVencimiento);
    }

    // --- Computed field: valor total ---
    public BigDecimal getValorTotal() {
        if (precioVenta == null) return BigDecimal.ZERO;
        return precioVenta.multiply(BigDecimal.valueOf(stockActual));
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }

    public String getCategoriaNombre() { return categoriaNombre; }
    public void setCategoriaNombre(String categoriaNombre) { this.categoriaNombre = categoriaNombre; }

    public String getCategoriaColor() { return categoriaColor; }
    public void setCategoriaColor(String categoriaColor) { this.categoriaColor = categoriaColor; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public int getStockActual() { return stockActual; }
    public void setStockActual(int stockActual) { this.stockActual = stockActual; }

    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }

    public int getStockMaximo() { return stockMaximo; }
    public void setStockMaximo(int stockMaximo) { this.stockMaximo = stockMaximo; }

    public UnidadMedida getUnidad() { return unidad; }
    public void setUnidad(UnidadMedida unidad) { this.unidad = unidad; }

    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getResguardante() { return resguardante; }
    public void setResguardante(String resguardante) { this.resguardante = resguardante; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public LocalDate getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDate fechaBaja) { this.fechaBaja = fechaBaja; }

    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }

    @Override
    public String toString() { return nombre + " (" + codigo + ")"; }
}
