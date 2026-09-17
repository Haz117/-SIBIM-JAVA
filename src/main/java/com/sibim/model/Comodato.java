package com.sibim.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class Comodato {

    public static final String ESTADO_VIGENTE    = "VIGENTE";
    public static final String ESTADO_VENCIDO    = "VENCIDO";
    public static final String ESTADO_CONCLUIDO  = "CONCLUIDO";
    public static final String ESTADO_RESCINDIDO = "RESCINDIDO";

    private String        id;
    private String        numero;
    private String        productoId;
    private String        productoNombre;
    private String        productoCodigo;
    private String        entidadReceptora;
    private String        contactoNombre;
    private String        contactoCargo;
    private String        domicilio;
    private String        motivo;
    private String        condiciones;
    private LocalDate     fechaInicio;
    private LocalDate     fechaFin;
    private LocalDate     fechaDevolucionReal;
    private String        estado = ESTADO_VIGENTE;
    private LocalDateTime createdAt;
    private String        creadoPorId;
    private String        creadoPorNombre;
    private LocalDateTime updatedAt;

    // ── Computed state ────────────────────────────────────────────────────────

    /** True when the comodato is past its end date and has not been returned. */
    public boolean isVencido() {
        return fechaFin != null
            && LocalDate.now().isAfter(fechaFin)
            && fechaDevolucionReal == null;
    }

    /** Returns "VENCIDO" if isVencido() regardless of the stored estado. */
    public String getEstadoEfectivo() {
        return isVencido() ? ESTADO_VENCIDO : estado;
    }

    public boolean isVigente()    { return ESTADO_VIGENTE.equals(estado) && !isVencido(); }
    public boolean isConcluido()  { return ESTADO_CONCLUIDO.equals(estado); }
    public boolean isRescindido() { return ESTADO_RESCINDIDO.equals(estado); }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }

    public String getNumero()                      { return numero; }
    public void   setNumero(String numero)         { this.numero = numero; }

    public String getProductoId()                  { return productoId; }
    public void   setProductoId(String v)          { this.productoId = v; }

    public String getProductoNombre()              { return productoNombre; }
    public void   setProductoNombre(String v)      { this.productoNombre = v; }

    public String getProductoCodigo()              { return productoCodigo; }
    public void   setProductoCodigo(String v)      { this.productoCodigo = v; }

    public String getEntidadReceptora()            { return entidadReceptora; }
    public void   setEntidadReceptora(String v)    { this.entidadReceptora = v; }

    public String getContactoNombre()              { return contactoNombre; }
    public void   setContactoNombre(String v)      { this.contactoNombre = v; }

    public String getContactoCargo()               { return contactoCargo; }
    public void   setContactoCargo(String v)       { this.contactoCargo = v; }

    public String getDomicilio()                   { return domicilio; }
    public void   setDomicilio(String v)           { this.domicilio = v; }

    public String getMotivo()                      { return motivo; }
    public void   setMotivo(String v)              { this.motivo = v; }

    public String getCondiciones()                 { return condiciones; }
    public void   setCondiciones(String v)         { this.condiciones = v; }

    public LocalDate getFechaInicio()              { return fechaInicio; }
    public void      setFechaInicio(LocalDate v)   { this.fechaInicio = v; }

    public LocalDate getFechaFin()                 { return fechaFin; }
    public void      setFechaFin(LocalDate v)      { this.fechaFin = v; }

    public LocalDate getFechaDevolucionReal()      { return fechaDevolucionReal; }
    public void      setFechaDevolucionReal(LocalDate v) { this.fechaDevolucionReal = v; }

    public String getEstado()                      { return estado; }
    public void   setEstado(String estado)         { this.estado = estado; }

    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public String getCreadoPorId()                 { return creadoPorId; }
    public void   setCreadoPorId(String v)         { this.creadoPorId = v; }

    public String getCreadoPorNombre()             { return creadoPorNombre; }
    public void   setCreadoPorNombre(String v)     { this.creadoPorNombre = v; }

    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comodato c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
