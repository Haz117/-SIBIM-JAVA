package com.sibim.db.integration;

import com.sibim.contrato.ContratoInventario;

/** {@link ContratoInventario} against the real PostgreSQL schema (embedded, all Flyway migrations). */
class ContratoSqlTest extends IntegrationTestBase implements ContratoInventario {
}
