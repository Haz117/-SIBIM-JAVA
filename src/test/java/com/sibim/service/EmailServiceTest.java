package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock ConfiguracionRepository mockConfig;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mockConfig);
    }

    // ── isHabilitado ─────────────────────────────────────────────────────────

    @Test
    void isHabilitado_allRequiredConfigPresent_returnsTrue() {
        when(mockConfig.get("alertas_email_habilitado", "false")).thenReturn("true");
        when(mockConfig.get("smtp_host", "")).thenReturn("smtp.gmail.com");
        when(mockConfig.get("alertas_correo_destino", "")).thenReturn("dest@example.com");

        assertTrue(service.isHabilitado());
    }

    @Test
    void isHabilitado_emailDisabled_returnsFalse() {
        when(mockConfig.get("alertas_email_habilitado", "false")).thenReturn("false");

        assertFalse(service.isHabilitado());
    }

    @Test
    void isHabilitado_smtpHostBlank_returnsFalse() {
        when(mockConfig.get("alertas_email_habilitado", "false")).thenReturn("true");
        when(mockConfig.get("smtp_host", "")).thenReturn("");

        assertFalse(service.isHabilitado());
    }

    @Test
    void isHabilitado_destinoBlank_returnsFalse() {
        when(mockConfig.get("alertas_email_habilitado", "false")).thenReturn("true");
        when(mockConfig.get("smtp_host", "")).thenReturn("smtp.gmail.com");
        when(mockConfig.get("alertas_correo_destino", "")).thenReturn("");

        assertFalse(service.isHabilitado());
    }

    // ── probarConexion ───────────────────────────────────────────────────────

    @Test
    void probarConexion_blankHost_returnsConfigError() {
        when(mockConfig.get("smtp_host", "")).thenReturn("");
        when(mockConfig.get("smtp_port", "587")).thenReturn("587");
        when(mockConfig.get("smtp_usuario", "")).thenReturn("");
        when(mockConfig.get("smtp_password", "")).thenReturn("");
        when(mockConfig.get("alertas_correo_destino", "")).thenReturn("");

        String result = service.probarConexion();

        assertNotNull(result);
        assertTrue(result.contains("SMTP") || result.contains("smtp") || result.contains("Configura"),
            "Expected config error message, got: " + result);
    }

    @Test
    void probarConexion_blankDestinoOnly_returnsConfigError() {
        when(mockConfig.get("smtp_host", "")).thenReturn("smtp.example.com");
        when(mockConfig.get("smtp_port", "587")).thenReturn("587");
        when(mockConfig.get("smtp_usuario", "")).thenReturn("user@example.com");
        when(mockConfig.get("smtp_password", "")).thenReturn("pass");
        when(mockConfig.get("alertas_correo_destino", "")).thenReturn("");

        String result = service.probarConexion();

        assertNotNull(result, "Should return an error message when destino is blank");
    }
}
