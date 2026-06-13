-- V3__Add_stock_adjustment_notes.sql
-- Menambahkan kolom notes ke sys_audit_log dan meng-update trigger log_product_changes()
-- untuk membaca session variable app.stock_notes (di-set oleh StockAdjustmentService saat stock in/out)

-- 1. Tambah kolom notes
ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS notes TEXT;

-- 2. Update trigger log_product_changes() agar menyertakan notes dari session variable
-- current_setting('app.stock_notes', true) mengembalikan NULL jika variable tidak di-set —
-- aman untuk price updates yang tidak melalui StockAdjustmentService
CREATE OR REPLACE FUNCTION log_product_changes()
    RETURNS TRIGGER AS
$$
BEGIN
    IF (OLD.stock_quantity IS DISTINCT FROM NEW.stock_quantity) OR
       (OLD.base_price IS DISTINCT FROM NEW.base_price) OR
       (OLD.label_price IS DISTINCT FROM NEW.label_price) OR
       (OLD.floor_price IS DISTINCT FROM NEW.floor_price)
    THEN
        INSERT INTO sys_audit_log (table_name, record_id, action, old_data, new_data, changed_by, notes)
        VALUES ('m_product',
                NEW.id,
                'UPDATE',
                jsonb_build_object(
                        'stock_quantity', OLD.stock_quantity,
                        'base_price', OLD.base_price,
                        'label_price', OLD.label_price,
                        'floor_price', OLD.floor_price
                ),
                jsonb_build_object(
                        'stock_quantity', NEW.stock_quantity,
                        'base_price', NEW.base_price,
                        'label_price', NEW.label_price,
                        'floor_price', NEW.floor_price
                ),
                'system',
                current_setting('app.stock_notes', true)  -- null-safe: returns NULL if not set
               );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

-- Trigger tetap terpasang (tidak perlu recreate karena hanya function yang berubah)
