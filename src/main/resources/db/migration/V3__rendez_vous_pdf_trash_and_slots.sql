ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL REFERENCES utilisateur(id);
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS service_id BIGINT NULL REFERENCES service(id);
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS praticien_id BIGINT NULL REFERENCES utilisateur(id);
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS client_access_token_hash VARCHAR(64) NULL;

UPDATE rendez_vous SET deleted_at = date_suppression
WHERE deleted_at IS NULL AND date_suppression IS NOT NULL;

DO $$
DECLARE constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_attribute a1 ON a1.attrelid = t.oid AND a1.attnum = c.conkey[1]
        JOIN pg_attribute a2 ON a2.attrelid = t.oid AND a2.attnum = c.conkey[2]
        WHERE t.relname = 'rendez_vous' AND c.contype = 'u'
          AND a1.attname = 'date_rdv' AND a2.attname = 'heure_rdv'
    LOOP
        EXECUTE format('ALTER TABLE rendez_vous DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_rendez_vous_active_slot
    ON rendez_vous (date_rdv, heure_rdv) WHERE supprime = false;
