-- V8: SMTP settings + scheduled reports configuration
INSERT INTO configuracion (clave, valor, descripcion) VALUES
  ('smtp_host',                 '',      'Servidor SMTP (ej. smtp.gmail.com)'),
  ('smtp_port',                 '587',   'Puerto SMTP (587=TLS, 465=SSL, 25=sin cifrado)'),
  ('smtp_usuario',              '',      'Usuario/correo SMTP'),
  ('smtp_password',             '',      'Contraseña SMTP'),
  ('alertas_correo_destino',    '',      'Correo donde se enviarán las alertas'),
  ('alertas_email_habilitado',  'false', 'Enviar correo automático cuando hay alertas de inventario'),
  ('reportes_habilitado',       'false', 'Generar reportes programados automáticamente'),
  ('reportes_frecuencia',       'MENSUAL','Frecuencia: DIARIO, SEMANAL, MENSUAL'),
  ('reportes_carpeta',          '',      'Carpeta destino para reportes programados'),
  ('reportes_tipos',            'INVENTARIO', 'Tipos separados por coma: INVENTARIO,MOVIMIENTOS,ALERTAS')
ON CONFLICT (clave) DO NOTHING;
