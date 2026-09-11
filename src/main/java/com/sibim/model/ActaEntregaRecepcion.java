package com.sibim.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class ActaEntregaRecepcion {
    private String id;
    private String numero;
    private String adminSaliente;
    private String cargoSaliente;
    private String adminEntrante;
    private String cargoEntrante;
    private LocalDate fechaEntrega;
    private String observaciones;
    private int totalBienes;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private String creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime creadoEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getAdminSaliente() { return adminSaliente; }
    public void setAdminSaliente(String v) { this.adminSaliente = v; }
    public String getCargoSaliente() { return cargoSaliente; }
    public void setCargoSaliente(String v) { this.cargoSaliente = v; }
    public String getAdminEntrante() { return adminEntrante; }
    public void setAdminEntrante(String v) { this.adminEntrante = v; }
    public String getCargoEntrante() { return cargoEntrante; }
    public void setCargoEntrante(String v) { this.cargoEntrante = v; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate v) { this.fechaEntrega = v; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String v) { this.observaciones = v; }
    public int getTotalBienes() { return totalBienes; }
    public void setTotalBienes(int totalBienes) { this.totalBienes = totalBienes; }
    public BigDecimal getValorTotal() { return valorTotal != null ? valorTotal : BigDecimal.ZERO; }
    public void setValorTotal(BigDecimal v) { this.valorTotal = v; }
    public String getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(String v) { this.creadoPorId = v; }
    public String getCreadoPorNombre() { return creadoPorNombre; }
    public void setCreadoPorNombre(String v) { this.creadoPorNombre = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime v) { this.creadoEn = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActaEntregaRecepcion a)) return false;
        return Objects.equals(id, a.id);
    }
    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
