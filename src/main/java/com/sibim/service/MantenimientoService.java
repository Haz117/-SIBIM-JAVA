package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores preventive maintenance alerts per producto in the configuracion table.
 * Key format: "mant_{productoId}_{index}" → "descripcion|fecha_iso"
 * Index key: "mant_{productoId}_count" → integer
 */
public class MantenimientoService {

    private static final Logger log = LoggerFactory.getLogger(MantenimientoService.class);

    public record Alerta(int index, String descripcion, LocalDate fecha, boolean completada) {}

    private final ConfiguracionRepository config;

    public MantenimientoService() { this(new ConfiguracionRepository()); }
    public MantenimientoService(ConfiguracionRepository config) { this.config = config; }

    public List<Alerta> getAlertas(String productoId) {
        List<Alerta> list = new ArrayList<>();
        try {
            int count = Integer.parseInt(config.get("mant_" + productoId + "_count", "0"));
            for (int i = 0; i < count; i++) {
                String val = config.get("mant_" + productoId + "_" + i, null);
                if (val == null || val.isBlank()) continue;
                String[] parts = val.split("\\|", 3);
                if (parts.length < 2) continue;
                String desc = parts[0];
                LocalDate fecha = null;
                try { fecha = LocalDate.parse(parts[1]); } catch (Exception ignored) {}
                boolean completada = parts.length >= 3 && "1".equals(parts[2]);
                list.add(new Alerta(i, desc, fecha, completada));
            }
        } catch (Exception e) {
            log.warn("Error cargando alertas de mantenimiento para {}", productoId, e);
        }
        return list;
    }

    public void agregarAlerta(String productoId, String descripcion, LocalDate fecha) {
        try {
            int count = Integer.parseInt(config.get("mant_" + productoId + "_count", "0"));
            config.set("mant_" + productoId + "_" + count, descripcion + "|" + fecha.toString());
            config.set("mant_" + productoId + "_count", String.valueOf(count + 1));
        } catch (Exception e) {
            log.error("Error guardando alerta de mantenimiento", e);
        }
    }

    public void marcarCompletada(String productoId, int index) {
        try {
            String key = "mant_" + productoId + "_" + index;
            String val = config.get(key, null);
            if (val == null) return;
            String[] parts = val.split("\\|", 3);
            String nuevaVal = parts[0] + "|" + (parts.length > 1 ? parts[1] : "") + "|1";
            config.set(key, nuevaVal);
        } catch (Exception e) {
            log.error("Error marcando alerta como completada", e);
        }
    }

    public void eliminarAlerta(String productoId, int index) {
        try {
            config.set("mant_" + productoId + "_" + index, null);
        } catch (Exception e) {
            log.error("Error eliminando alerta de mantenimiento", e);
        }
    }

    /** Returns all alertas across all productos that are pending and due within `days` days. */
    public List<String[]> getProximasGlobal(int days) {
        List<String[]> result = new ArrayList<>();
        try {
            LocalDate limite = LocalDate.now().plusDays(days);
            config.findAll().forEach((k, v) -> {
                if (!k.startsWith("mant_") || k.endsWith("_count")) return;
                String[] parts = v.split("\\|", 3);
                if (parts.length < 2) return;
                boolean completada = parts.length >= 3 && "1".equals(parts[2]);
                if (completada) return;
                try {
                    LocalDate fecha = LocalDate.parse(parts[1]);
                    if (!fecha.isAfter(limite)) {
                        // extract productoId from key: mant_{productoId}_{index}
                        String[] kParts = k.split("_", 3);
                        String productoId = kParts.length >= 2 ? kParts[1] : "?";
                        result.add(new String[]{ productoId, parts[0], parts[1] });
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.warn("Error obteniendo alertas próximas globales", e);
        }
        return result;
    }
}
