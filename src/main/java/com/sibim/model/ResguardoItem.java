package com.sibim.model;

import java.math.BigDecimal;
import java.util.Objects;

public class ResguardoItem {
    private String id;
    private String resguardoId;
    private String productoId;
    private String productoNombre;
    private String productoCodigo;
    private String area;
    private int cantidad = 1;
    private String descripcion;
    private String numeroSerie;
    private BigDecimal valorUnitario;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResguardoId() { return resguardoId; }
    public void setResguardoId(String v) { this.resguardoId = v; }
    public String getProductoId() { return productoId; }
    public void setProductoId(String v) { this.productoId = v; }
    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String v) { this.productoNombre = v; }
    public String getProductoCodigo() { return productoCodigo; }
    public void setProductoCodigo(String v) { this.productoCodigo = v; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String v) { this.descripcion = v; }
    public String getNumeroSerie() { return numeroSerie; }
    public void setNumeroSerie(String v) { this.numeroSerie = v; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public void setValorUnitario(BigDecimal v) { this.valorUnitario = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResguardoItem i)) return false;
        return Objects.equals(id, i.id);
    }
    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
