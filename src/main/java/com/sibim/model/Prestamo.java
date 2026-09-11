package com.sibim.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class Prestamo {
    public static final String ESTADO_ACTIVO   = "ACTIVO";
    public static final String ESTADO_DEVUELTO = "DEVUELTO";
    public static final String ESTADO_VENCIDO  = "VENCIDO";

    private String id;
    private String numero;
    private String productoId;
    private String productoNombre;
    private String productoCodigo;
    private String areaOrigen;
    private String areaDestino;
    private String responsableNombre;
    private String responsableCargo;
    private String motivo;
    private LocalDate fechaPrestamo;
    private LocalDate fechaDevolucionPrevista;
    private LocalDate fechaDevolucionReal;
    private String estado = ESTADO_ACTIVO;
    private String creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime creadoEn;

    public boolean isVencidoCalc() {
        return ESTADO_ACTIVO.equals(estado)
            && fechaDevolucionPrevista != null
            && LocalDate.now().isAfter(fechaDevolucionPrevista);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getProductoId() { return productoId; }
    public void setProductoId(String v) { this.productoId = v; }
    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String v) { this.productoNombre = v; }
    public String getProductoCodigo() { return productoCodigo; }
    public void setProductoCodigo(String v) { this.productoCodigo = v; }
    public String getAreaOrigen() { return areaOrigen; }
    public void setAreaOrigen(String v) { this.areaOrigen = v; }
    public String getAreaDestino() { return areaDestino; }
    public void setAreaDestino(String v) { this.areaDestino = v; }
    public String getResponsableNombre() { return responsableNombre; }
    public void setResponsableNombre(String v) { this.responsableNombre = v; }
    public String getResponsableCargo() { return responsableCargo; }
    public void setResponsableCargo(String v) { this.responsableCargo = v; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String v) { this.motivo = v; }
    public LocalDate getFechaPrestamo() { return fechaPrestamo; }
    public void setFechaPrestamo(LocalDate v) { this.fechaPrestamo = v; }
    public LocalDate getFechaDevolucionPrevista() { return fechaDevolucionPrevista; }
    public void setFechaDevolucionPrevista(LocalDate v) { this.fechaDevolucionPrevista = v; }
    public LocalDate getFechaDevolucionReal() { return fechaDevolucionReal; }
    public void setFechaDevolucionReal(LocalDate v) { this.fechaDevolucionReal = v; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(String v) { this.creadoPorId = v; }
    public String getCreadoPorNombre() { return creadoPorNombre; }
    public void setCreadoPorNombre(String v) { this.creadoPorNombre = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime v) { this.creadoEn = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Prestamo p)) return false;
        return Objects.equals(id, p.id);
    }
    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
