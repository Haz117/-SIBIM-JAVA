CREATE TABLE IF NOT EXISTS comodatos (
    id                    VARCHAR(36)  PRIMARY KEY,
    numero                VARCHAR(30)  NOT NULL UNIQUE,
    producto_id           VARCHAR(36)  NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    producto_nombre       VARCHAR(200) NOT NULL,
    producto_codigo       VARCHAR(50),
    entidad_receptora     VARCHAR(300) NOT NULL,
    contacto_nombre       VARCHAR(200) NOT NULL,
    contacto_cargo        VARCHAR(200),
    domicilio             VARCHAR(500),
    motivo                TEXT,
    condiciones           TEXT,
    fecha_inicio          DATE         NOT NULL,
    fecha_fin             DATE,
    fecha_devolucion_real DATE,
    estado                VARCHAR(20)  NOT NULL DEFAULT 'VIGENTE'
                             CHECK (estado IN ('VIGENTE','VENCIDO','CONCLUIDO','RESCINDIDO')),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    creado_por_id         VARCHAR(36),
    creado_por_nombre     VARCHAR(200),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_comodatos_estado ON comodatos(estado);
CREATE INDEX IF NOT EXISTS idx_comodatos_producto ON comodatos(producto_id);
