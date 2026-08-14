package com.sibim.model;

import java.time.LocalDateTime;

public class Categoria {
    private String id;
    private String nombre;
    private String descripcion;
    private String color;
    private String icono;
    private LocalDateTime creadoEn;

    // Computed field (not stored in DB)
    private int totalProductos;

    public Categoria() {}

    public Categoria(String id, String nombre, String descripcion,
                     String color, String icono, LocalDateTime creadoEn) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.color = color;
        this.icono = icono;
        this.creadoEn = creadoEn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public int getTotalProductos() { return totalProductos; }
    public void setTotalProductos(int totalProductos) { this.totalProductos = totalProductos; }

    @Override
    public String toString() { return nombre; }
}
