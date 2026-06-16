CREATE TABLE sys_invoice_sequence
(
    store_id INTEGER NOT NULL,
    seq_date DATE    NOT NULL,
    last_seq INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, seq_date),
    CONSTRAINT fk_sys_invoice_sequence_store_id FOREIGN KEY (store_id) REFERENCES m_store (id)
);

CREATE OR REPLACE FUNCTION get_next_invoice_seq(p_store_id INTEGER, p_date DATE)
    RETURNS INTEGER AS $$
    INSERT INTO sys_invoice_sequence (store_id, seq_date, last_seq)
    VALUES (p_store_id, p_date, 1)
    ON CONFLICT (store_id, seq_date)
    DO UPDATE SET last_seq = sys_invoice_sequence.last_seq + 1
    RETURNING last_seq;
$$ LANGUAGE sql;