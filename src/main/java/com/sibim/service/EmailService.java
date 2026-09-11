package com.sibim.service;

import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.repository.ConfiguracionRepository;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final ConfiguracionRepository config;

    public EmailService() { this(new ConfiguracionRepository()); }
    public EmailService(ConfiguracionRepository config) { this.config = config; }

    public boolean isHabilitado() {
        return "true".equals(config.get("alertas_email_habilitado", "false"))
            && !config.get("smtp_host", "").isBlank()
            && !config.get("alertas_correo_destino", "").isBlank();
    }

    public void enviarAlertas(List<Producto> agotados, List<Producto> bajoStock, List<Producto> vencidos) {
        if (!isHabilitado() || (agotados.isEmpty() && bajoStock.isEmpty() && vencidos.isEmpty())) return;
        try {
            String host     = config.get("smtp_host", "");
            String port     = config.get("smtp_port", "587");
            String usuario  = config.get("smtp_usuario", "");
            String password = config.get("smtp_password", "");
            String destino  = config.get("alertas_correo_destino", "");

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usuario, password);
                }
            });

            StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif'>");
            html.append("<h2 style='color:#4C1D95'>&#9888; Alertas de Inventario — SIBIM</h2>");
            appendSection(html, "Bienes Agotados (stock = 0)", agotados, "#DC2626");
            appendSection(html, "Existencias Bajas (por debajo del mínimo)", bajoStock, "#D97706");
            appendSection(html, "Garantías por Vencer (próximos 30 días)", vencidos, "#2563EB");
            html.append("</body></html>");

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(usuario));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino));
            msg.setSubject("Alertas de inventario SIBIM — "
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            msg.setContent(html.toString(), "text/html; charset=UTF-8");
            Transport.send(msg);
            log.info("Alerta de inventario enviada a {}", destino);
        } catch (Exception e) {
            log.error("Error al enviar alerta por email", e);
        }
    }

    private static void appendSection(StringBuilder html, String title, List<Producto> items, String color) {
        if (items.isEmpty()) return;
        html.append("<h3 style='color:").append(color).append("'>").append(title)
            .append(" (").append(items.size()).append(")</h3><ul>");
        for (Producto p : items)
            html.append("<li><b>").append(p.getNombre()).append("</b> [").append(p.getCodigo())
                .append("] — ").append(p.getArea()).append("</li>");
        html.append("</ul>");
    }

    public void enviarAvisoPrestamos(List<Prestamo> vencidos, List<Prestamo> proximos) {
        if (!isHabilitado() || (vencidos.isEmpty() && proximos.isEmpty())) return;
        try {
            String host     = config.get("smtp_host", "");
            String port     = config.get("smtp_port", "587");
            String usuario  = config.get("smtp_usuario", "");
            String password = config.get("smtp_password", "");
            String destino  = config.get("alertas_correo_destino", "");

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usuario, password);
                }
            });

            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif'>");
            html.append("<h2 style='color:#15803D'>&#128339; Aviso de Préstamos — SIBIM</h2>");
            if (!vencidos.isEmpty()) {
                html.append("<h3 style='color:#DC2626'>Préstamos Vencidos (").append(vencidos.size()).append(")</h3><ul>");
                for (Prestamo p : vencidos)
                    html.append("<li><b>").append(p.getNumero()).append("</b> — ")
                        .append(p.getProductoNombre())
                        .append(" · Responsable: ").append(p.getResponsableNombre())
                        .append(" · Venció: ").append(p.getFechaDevolucionPrevista() != null ? p.getFechaDevolucionPrevista().format(fmt) : "—")
                        .append("</li>");
                html.append("</ul>");
            }
            if (!proximos.isEmpty()) {
                html.append("<h3 style='color:#D97706'>Próximos a Vencer (").append(proximos.size()).append(")</h3><ul>");
                for (Prestamo p : proximos)
                    html.append("<li><b>").append(p.getNumero()).append("</b> — ")
                        .append(p.getProductoNombre())
                        .append(" · Responsable: ").append(p.getResponsableNombre())
                        .append(" · Vence: ").append(p.getFechaDevolucionPrevista() != null ? p.getFechaDevolucionPrevista().format(fmt) : "—")
                        .append("</li>");
                html.append("</ul>");
            }
            html.append("</body></html>");

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(usuario));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino));
            msg.setSubject("Aviso préstamos SIBIM — "
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            msg.setContent(html.toString(), "text/html; charset=UTF-8");
            Transport.send(msg);
            log.info("Aviso de préstamos enviado a {} ({} vencidos, {} próximos)", destino, vencidos.size(), proximos.size());
        } catch (Exception e) {
            log.error("Error al enviar aviso de préstamos por email", e);
        }
    }

    /** Returns null on success or an error message on failure. */
    public String probarConexion() {
        try {
            String host    = config.get("smtp_host", "");
            String port    = config.get("smtp_port", "587");
            String usuario = config.get("smtp_usuario", "");
            String pass    = config.get("smtp_password", "");
            String dest    = config.get("alertas_correo_destino", "");
            if (host.isBlank() || usuario.isBlank() || dest.isBlank())
                return "Configura el servidor SMTP, usuario y correo destino primero.";
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usuario, pass);
                }
            });
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(usuario));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(dest));
            msg.setSubject("SIBIM — Prueba de configuración SMTP");
            msg.setText("La configuración de correo está funcionando correctamente.");
            Transport.send(msg);
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }
}
