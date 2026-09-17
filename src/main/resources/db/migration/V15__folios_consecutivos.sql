CREATE TABLE IF NOT EXISTS folios (
    prefijo  VARCHAR(20) NOT NULL,
    anio     SMALLINT    NOT NULL,
    ultimo   INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (prefijo, anio)
);
