package com.sibim.repository;

import com.sibim.db.DatabaseConfig;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfiguracionRepository {

    private static final Map<String, String> DEMO_VALUES = new LinkedHashMap<>();
    static {
        DEMO_VALUES.put("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan");
        DEMO_VALUES.put("municipio",           "Ixmiquilpan, Hidalgo");
        DEMO_VALUES.put("responsable",         "Dirección de Bienes Patrimoniales");
        DEMO_VALUES.put("correo_contacto",     "");
    }

    public Map<String, String> findAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return new LinkedHashMap<>(DEMO_VALUES);
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT clave, valor FROM configuracion ORDER BY clave";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.put(rs.getString("clave"), rs.getString("valor"));
        }
        return result;
    }

    public String get(String clave, String defaultValue) {
        try {
            if (DatabaseConfig.isDemoMode()) return DEMO_VALUES.getOrDefault(clave, defaultValue);
            String sql = "SELECT valor FROM configuracion WHERE clave = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String v = rs.getString("valor");
                        return v != null ? v : defaultValue;
                    }
                }
            }
        } catch (SQLException ignored) {}
        return defaultValue;
    }

    public void set(String clave, String valor) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DEMO_VALUES.put(clave, valor); return; }
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?) ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor != null ? valor : "");
            ps.executeUpdate();
        }
    }
}
