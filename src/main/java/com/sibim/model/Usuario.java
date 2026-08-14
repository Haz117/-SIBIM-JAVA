package com.sibim.model;

import com.sibim.model.enums.Rol;
import java.time.LocalDateTime;

public class Usuario {
    private String id;
    private String username;
    private String passwordHash;
    private String nombre;
    private String cargo;
    private Rol rol;
    private String area;
    private LocalDateTime creadoEn;

    public Usuario() {}

    public Usuario(String id, String username, String passwordHash,
                   String nombre, String cargo, Rol rol, String area, LocalDateTime creadoEn) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.cargo = cargo;
        this.rol = rol;
        this.area = area;
        this.creadoEn = creadoEn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public boolean esAdmin() { return rol == Rol.ADMIN; }
    public boolean esSecretario() { return rol == Rol.SECRETARIO; }
    public boolean esDireccion() { return rol == Rol.DIRECCION; }

    @Override
    public String toString() { return nombre + " (" + username + ")"; }
}
