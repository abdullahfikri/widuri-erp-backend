-- Hardening for the invoice sequence introduced in V5.
-- V5 is already applied to existing databases, so changes are delivered as a new migration
-- (editing V5 in place would trip Flyway's checksum validation).

-- Pin the search_path and make the security model explicit. Without a pinned search_path
-- the unqualified `sys_invoice_sequence` reference resolves against the caller's runtime
-- search_path, which is a schema-shadowing hazard.
CREATE OR REPLACE FUNCTION get_next_invoice_seq(p_store_id INTEGER, p_date DATE)
    RETURNS INTEGER
    LANGUAGE sql
    SECURITY INVOKER
    SET search_path = public, pg_temp
AS $$
    INSERT INTO sys_invoice_sequence (store_id, seq_date, last_seq)
    VALUES (p_store_id, p_date, 1)
    ON CONFLICT (store_id, seq_date)
    DO UPDATE SET last_seq = sys_invoice_sequence.last_seq + 1
    RETURNING last_seq;
$$;

-- Guard against a non-positive sequence ever being persisted.
ALTER TABLE sys_invoice_sequence
    ADD CONSTRAINT chk_sys_invoice_sequence_last_seq_positive CHECK (last_seq >= 0);

-- The INSERT path always seeds last_seq = 1 explicitly; the DEFAULT 0 is never exercised
-- and is misleading, so remove it.
ALTER TABLE sys_invoice_sequence
    ALTER COLUMN last_seq DROP DEFAULT;
