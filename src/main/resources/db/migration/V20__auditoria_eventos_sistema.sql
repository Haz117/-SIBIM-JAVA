-- System events such as failed logins and backups do not always reference
-- a persisted business entity. Keep the audit record without inventing IDs.
ALTER TABLE audit_log ALTER COLUMN entidad_id DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN usuario_nombre DROP NOT NULL;

-- Audit history must survive user deactivation/deletion. Preserve the
-- display name in the row while releasing the optional relational link.
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_usuario_id_fkey;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_usuario_id_fkey
	FOREIGN KEY (usuario_id) REFERENCES users(id) ON DELETE SET NULL;