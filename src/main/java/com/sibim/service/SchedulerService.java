package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Prestamo;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.PrestamoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton scheduler that checks once per hour whether it's time to generate
 * scheduled reports (only at 06:00, only if reportes_habilitado=true, only when
 * online). Generated files are copied to the configured destination folder.
 */
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);
    private static final SchedulerService INSTANCE = new SchedulerService();

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sibim-scheduler");
            t.setDaemon(true);
            return t;
        });

    private SchedulerService() {}

    public static SchedulerService getInstance() { return INSTANCE; }

    public void start() {
        executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.HOURS);
        log.info("SchedulerService iniciado — revisión horaria de reportes programados");
    }

    public void stop() {
        executor.shutdownNow();
        log.info("SchedulerService detenido");
    }

    private void tick() {
        try {
            if (LocalTime.now().getHour() != 6) return;
            if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;

            ConfiguracionRepository config = new ConfiguracionRepository();
            if (!"true".equals(config.get("reportes_habilitado", "false"))) return;

            String frecuencia = config.get("reportes_frecuencia", "MENSUAL");
            String carpeta    = config.get("reportes_carpeta",    "");
            String tipos      = config.get("reportes_tipos",      "INVENTARIO");
            String ultimaKey  = "reportes_ultima_ejecucion";
            String ultimaStr  = config.get(ultimaKey, "");

            LocalDate hoy      = LocalDate.now();
            LocalDate ultimaEj = parseDate(ultimaStr);

            if (!esTiempoDeEjecutar(frecuencia, hoy, ultimaEj)) return;

            log.info("Generando reportes programados (frecuencia={}, tipos={})", frecuencia, tipos);
            ReporteService reporteService = new ReporteService();
            File destDir = carpeta.isBlank() ? null : new File(carpeta);

            for (String tipo : tipos.split(",")) {
                tipo = tipo.trim().toUpperCase();
                try {
                    File pdf = switch (tipo) {
                        case "INVENTARIO"  -> reporteService.exportInventarioPdf(null, null);
                        case "MOVIMIENTOS" -> reporteService.exportMovimientosPdf(
                                                 hoy.minusMonths(1), hoy);
                        case "ALERTAS"     -> reporteService.exportAlertasPdf();
                        default            -> null;
                    };
                    if (pdf != null && destDir != null && destDir.isDirectory()) {
                        String nombre = tipo.toLowerCase() + "_" + hoy + ".pdf";
                        Files.copy(pdf.toPath(),
                                   new File(destDir, nombre).toPath(),
                                   StandardCopyOption.REPLACE_EXISTING);
                        log.info("Reporte {} copiado a {}", nombre, destDir);
                    }
                } catch (Exception e) {
                    log.error("Error generando reporte programado de tipo {}", tipo, e);
                }
            }

            try { config.set(ultimaKey, hoy.toString()); } catch (java.sql.SQLException ex) {
                log.warn("No se pudo guardar ultima ejecucion de reportes", ex);
            }
            log.info("Reportes programados completados para {}", hoy);
            TrayService.notify("Reportes generados",
                "Reportes del " + hoy + " guardados en " + (carpeta.isBlank() ? "carpeta temporal" : carpeta));

            // Préstamos vencidos/próximos — daily email reminder
            try {
                PrestamoRepository prestamoRepo = new PrestamoRepository();
                java.util.List<Prestamo> vencidos = prestamoRepo.findVencidos();
                java.util.List<Prestamo> proximos = prestamoRepo.findProximosAVencer(3);
                if (!vencidos.isEmpty() || !proximos.isEmpty())
                    new EmailService().enviarAvisoPrestamos(vencidos, proximos);
            } catch (Exception e) {
                log.error("Error al revisar préstamos para email", e);
            }

            // Resumen semanal — every Monday
            if (hoy.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                try {
                    com.sibim.repository.ProductoRepository prodRepo = new com.sibim.repository.ProductoRepository();
                    com.sibim.repository.ResguardoRepository rsgRepo  = new com.sibim.repository.ResguardoRepository();
                    com.sibim.repository.MovimientoRepository movRepo  = new com.sibim.repository.MovimientoRepository();
                    PrestamoRepository prestamoRepo2 = new PrestamoRepository();

                    java.util.List<com.sibim.model.Producto> agotados   = prodRepo.findAgotados();
                    java.util.List<com.sibim.model.Producto> bajoStock   = prodRepo.findBajoStock();
                    java.util.List<Prestamo> prestVencidos               = prestamoRepo2.findVencidos();
                    java.util.List<Prestamo> prestActivos                = prestamoRepo2.findActivos();
                    java.util.List<com.sibim.model.Resguardo> rsgActivos = rsgRepo.findAll().stream()
                        .filter(r -> com.sibim.model.Resguardo.ESTADO_ACTIVO.equals(r.getEstado())).toList();
                    int movSemana = movRepo.findByDateRange(hoy.minusDays(7), hoy).size();

                    new EmailService().enviarResumenSemanal(agotados, bajoStock, prestVencidos, prestActivos, rsgActivos, movSemana);
                } catch (Exception e) {
                    log.error("Error al enviar resumen semanal", e);
                }
            }

        } catch (Exception e) {
            log.error("Error en SchedulerService.tick()", e);
        }
    }

    static boolean esTiempoDeEjecutar(String frecuencia, LocalDate hoy, LocalDate ultima) {
        if (ultima == null) return true;
        return switch (frecuencia.toUpperCase()) {
            case "DIARIO"   -> hoy.isAfter(ultima);
            case "SEMANAL"  -> hoy.isAfter(ultima.plusWeeks(1).minusDays(1));
            case "MENSUAL"  -> hoy.isAfter(ultima.plusMonths(1).minusDays(1));
            default         -> false;
        };
    }

    static LocalDate parseDate(String s) {
        try { return s != null && !s.isBlank() ? LocalDate.parse(s) : null; }
        catch (Exception e) { return null; }
    }
}
