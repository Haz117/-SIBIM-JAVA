package com.sibim.util;

import com.sibim.db.DatabaseConfig;
import com.sibim.service.ProductoService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.*;

class MensajeErrorTest {

    /** SIBIM's own sentences reach the person as they are, wrapped or not. */
    @Test
    void losMensajesDeSibim_pasanTalCual() {
        assertEquals("El número ya lo usa otro bien", MensajeError.de(new IllegalStateException("El número ya lo usa otro bien")));
        assertEquals("Escribe el motivo", MensajeError.de(new ProductoService.ValidationException("Escribe el motivo")));
        assertEquals("La versión 1.0.9 no trae la firma de TI: no se instala",
            MensajeError.de(new IOException("La versión 1.0.9 no trae la firma de TI: no se instala")));
        assertEquals("Escribe el motivo",
            MensajeError.de(new RuntimeException(new IllegalArgumentException("Escribe el motivo"))));
        assertEquals(DatabaseConfig.SIN_CONEXION, MensajeError.de(new SQLException(DatabaseConfig.SIN_CONEXION, "08001")));
    }

    /** What the driver, the pool or the network say becomes a sentence. */
    @Test
    void loTecnico_seTraduce() {
        assertEquals(MensajeError.SIN_CONEXION, MensajeError.de(new SQLTransientConnectionException(
            "HikariPool-1 - Connection is not available, request timed out after 8000ms.")));
        assertEquals(MensajeError.SIN_CONEXION, MensajeError.de(new RuntimeException(
            new SQLException("I/O error", "08006"))));
        assertEquals(MensajeError.REPETIDO, MensajeError.de(new SQLException(
            "ERROR: duplicate key value violates unique constraint \"products_codigo_key\"", "23505")));
        assertEquals(MensajeError.LIGADO, MensajeError.de(new SQLException("ERROR: update or delete violates foreign key", "23503")));
        assertEquals(MensajeError.SIN_PERMISO, MensajeError.de(new SQLException("ERROR: permission denied for table products", "42501")));
        assertEquals(MensajeError.SIN_CONEXION, MensajeError.de(new java.net.ConnectException("Connection refused: connect")));
        assertEquals(MensajeError.ARCHIVO, MensajeError.de(new java.nio.file.AccessDeniedException("C:\\x\\reporte.xlsx")));
        assertEquals(MensajeError.GENERICO, MensajeError.de(new NullPointerException()));
        assertEquals(MensajeError.GENERICO, MensajeError.de(new RuntimeException("java.lang.NullPointerException: x")));
        assertEquals(MensajeError.GENERICO, MensajeError.de(null));
    }

    /** A trigger's RAISE EXCEPTION is SIBIM speaking: its first line, without "ERROR:". */
    @Test
    void elAvisoDeUnTrigger_seQuedaSuPrimeraLinea() {
        assertEquals("El número RSG/12 ya se usó y no se repite",
            MensajeError.de(new SQLException("ERROR: El número RSG/12 ya se usó y no se repite\n  Where: PL/pgSQL function x", "P0001")));
    }
}
