-- ============================================================
-- V29: En qué computadoras ha entrado cada cuenta
--
-- Una cuenta solo puede entrar SIN internet en una PC donde ya entró antes
-- con conexión (ahí queda guardado su acceso). Esta tabla anota cada entrada
-- con conexión para que Patrimonio vea, antes de que falle la red, qué
-- cuentas todavía no han entrado en ninguna PC.
-- ============================================================

CREATE TABLE IF NOT EXISTS accesos_equipo (
    user_id        TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    equipo         TEXT NOT NULL,
    ultimo_acceso  TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, equipo)
);
