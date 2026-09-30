-- V25: the signed PDF of an área's resguardo is stored in the database.
--
-- Until now the file was copied to the uploading PC's ~/.sibim/resguardos and
-- only that local path was saved, so no other PC could open it (and it was
-- lost with that PC). New rows keep the bytes here, protected by the database
-- login and included in backups; pdf_url stays for rows created before V25.
ALTER TABLE area_resguardos ADD COLUMN IF NOT EXISTS pdf BYTEA;
ALTER TABLE area_resguardos ADD COLUMN IF NOT EXISTS pdf_nombre TEXT;
ALTER TABLE area_resguardos ALTER COLUMN pdf_url DROP NOT NULL;
