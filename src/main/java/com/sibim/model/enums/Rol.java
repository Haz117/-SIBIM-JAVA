package com.sibim.model.enums;

public enum Rol {
    ADMIN("admin", "Administrador"),
    SECRETARIO("secretario", "Secretario"),
    DIRECCION("direccion", "Direccion");

    private final String codigo;
    private final String etiqueta;

    Rol(String codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }

    public static Rol fromCodigo(String codigo) {
        for (Rol r : values()) {
            if (r.codigo.equalsIgnoreCase(codigo)) return r;
        }
        throw new IllegalArgumentException("Rol desconocido: " + codigo);
    }

    @Override
    public String toString() { return etiqueta; }
}
