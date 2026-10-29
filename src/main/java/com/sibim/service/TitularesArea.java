package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * The titular of each área — who answers for its resguardos: the "Nombre del
 * responsable del resguardo" of the ANEXO V.4, a different person from the
 * resguardante. It can't be told from the data (on 29-oct-2026, 18 áreas had
 * several directors or titulars among their resguardantes and 10 had none), so
 * it is typed in Configuración › Áreas. Kept in configuracion, one row per área
 * ("titular_area:&lt;área&gt;"), which needs no change to the areas table.
 */
public final class TitularesArea {

    static final String PREFIJO = "titular_area:";

    private TitularesArea() {}

    /** Every titular typed, by área; an área without one is absent. */
    public static Map<String, String> todos(ConfiguracionRepository config) {
        Map<String, String> out = new HashMap<>();
        try {
            for (var e : config.findAll().entrySet())
                if (e.getKey().startsWith(PREFIJO) && e.getValue() != null && !e.getValue().isBlank())
                    out.put(e.getKey().substring(PREFIJO.length()), e.getValue().trim());
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(TitularesArea.class).warn("Sin titulares de área: {}", e.getMessage());
        }
        return out;
    }

    /** Patrimonio only (ConfiguracionRepository#set); empty clears it. */
    public static void guardar(ConfiguracionRepository config, String area, String titular) throws SQLException {
        config.set(PREFIJO + area, titular == null ? "" : titular.trim());
    }
}
