-- V23: contador de intentos fallidos de inicio de sesión compartido por todas
-- las PCs. El contador local (~/.sibim/auth_attempts.properties) se podía
-- evadir borrando el archivo o probando desde otra computadora; este vive en
-- el servidor y usa su reloj para la ventana de bloqueo.
CREATE TABLE IF NOT EXISTS login_attempts (
    username        TEXT PRIMARY KEY,
    intentos        INTEGER     NOT NULL DEFAULT 0,
    ventana_inicio  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
