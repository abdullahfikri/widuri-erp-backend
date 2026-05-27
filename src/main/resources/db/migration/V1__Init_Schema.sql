-- V1__Init_Schema.sql
-- ERP Toko Pancing Database Schema (Optimized for PostgreSQL)

-- ==========================================
-- 0. Helper Functions & Triggers
-- ==========================================

-- Trigger function untuk meng-update kolom updated_at secara otomatis
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

-- Sequence untuk SKU Generator
CREATE SEQUENCE sku_sequence START 1;

-- Function untuk mengambil next SKU digit (cth: 001, 002)
CREATE OR REPLACE FUNCTION get_next_sku_seq()
    RETURNS TEXT AS
$$
BEGIN
    RETURN LPAD(nextval('sku_sequence')::TEXT, 3, '0');
END;
$$ LANGUAGE 'plpgsql';

-- ==========================================
-- 1. Master Data Tables
-- ==========================================

-- Master Store
CREATE TABLE m_store
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    address    TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER set_m_store_updated_at
    BEFORE UPDATE
    ON m_store
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Product Group (Parent)
CREATE TABLE m_product_group
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    brand       VARCHAR(100),
    category    VARCHAR(50),
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER set_m_product_group_updated_at
    BEFORE UPDATE
    ON m_product_group
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Product Variant (SKU)
CREATE TABLE m_product
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_group_id BIGINT         NOT NULL,
    store_id         INT            NOT NULL,
    sku              VARCHAR(50)    NOT NULL,
    attributes       JSONB          NOT NULL,
    base_price       DECIMAL(15, 2) NOT NULL CHECK (base_price >= 0),
    label_price      DECIMAL(15, 2) NOT NULL CHECK (label_price >= 0),
    floor_price      DECIMAL(15, 2) NOT NULL CHECK (floor_price >= 0),
    stock_quantity   INT                      DEFAULT 0 CHECK (stock_quantity >= 0),
    min_stock_level  INT                      DEFAULT 5 CHECK (min_stock_level >= 0),
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE m_product
    ADD CONSTRAINT uq_m_product_sku UNIQUE (sku),
    ADD CONSTRAINT fk_m_product_m_product_group FOREIGN KEY (product_group_id) REFERENCES m_product_group (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_m_product_m_store FOREIGN KEY (store_id) REFERENCES m_store (id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_m_product_price_hierarchy CHECK (base_price <= floor_price AND floor_price <= label_price);

CREATE TRIGGER set_m_product_updated_at
    BEFORE UPDATE
    ON m_product
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Indexes for m_product
CREATE INDEX idx_m_product_attributes ON m_product USING GIN (attributes);
CREATE INDEX idx_m_product_group_id ON m_product (product_group_id);
CREATE INDEX idx_m_product_store_id ON m_product (store_id);

-- ==========================================
-- 2. Transaction Tables
-- ==========================================

-- Sales Transaction Header
-- Transaction records are immutable in ERP, therefore only created_at (transaction_date) is needed.
CREATE TABLE t_sales
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id         INT            NOT NULL,
    invoice_number   VARCHAR(50)    NOT NULL,
    total_amount     DECIMAL(15, 2) NOT NULL CHECK (total_amount >= 0),
    payment_method   VARCHAR(20) NOT NULL CHECK (payment_method IN ('Cash', 'QRIS', 'Transfer')),
    transaction_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_sales
    ADD CONSTRAINT uq_t_sales_invoice_number UNIQUE (invoice_number),
    ADD CONSTRAINT fk_t_sales_m_store FOREIGN KEY (store_id) REFERENCES m_store (id) ON DELETE RESTRICT;

CREATE INDEX idx_t_sales_store_id ON t_sales (store_id);
CREATE INDEX idx_t_sales_transaction_date ON t_sales (transaction_date);

-- Sales Transaction Detail
-- Bound to immutable transaction header, therefore created_at/updated_at is not needed.
CREATE TABLE t_sales_detail
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sales_id           BIGINT         NOT NULL,
    product_id         BIGINT         NOT NULL,
    quantity           INT            NOT NULL CHECK (quantity > 0),
    cost_price_at_time DECIMAL(15, 2) NOT NULL CHECK (cost_price_at_time >= 0),
    sold_price_at_time DECIMAL(15, 2) NOT NULL CHECK (sold_price_at_time >= 0),
    subtotal           DECIMAL(15, 2) NOT NULL CHECK (subtotal >= 0)
);

ALTER TABLE t_sales_detail
    ADD CONSTRAINT fk_t_sales_detail_t_sales FOREIGN KEY (sales_id) REFERENCES t_sales (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_t_sales_detail_m_product FOREIGN KEY (product_id) REFERENCES m_product (id) ON DELETE RESTRICT;

CREATE INDEX idx_t_sales_detail_sales_id ON t_sales_detail (sales_id);
CREATE INDEX idx_t_sales_detail_product_id ON t_sales_detail (product_id);

-- ==========================================
-- 3. System Utility Tables
-- ==========================================

-- Audit Log
-- Append-only system log structure. Only changed_at (creation timestamp) is needed.
CREATE TABLE sys_audit_log
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    record_id  BIGINT      NOT NULL,
    action     VARCHAR(10) NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
    old_data   JSONB,
    new_data   JSONB,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- BRIN index is perfect for append-only timestamp data that grows large
CREATE INDEX idx_sys_audit_log_changed_at ON sys_audit_log USING BRIN (changed_at);
-- B-Tree index for looking up logs by record
CREATE INDEX idx_sys_audit_log_record ON sys_audit_log (table_name, record_id);

-- ==========================================
-- 4. Audit Log Triggers
-- ==========================================

-- Fungsi trigger khusus untuk mencatat perubahan harga dan stok produk
CREATE OR REPLACE FUNCTION log_product_changes()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Hanya mencatat jika ada perubahan pada stok atau harga
    IF (OLD.stock_quantity IS DISTINCT FROM NEW.stock_quantity) OR
       (OLD.base_price IS DISTINCT FROM NEW.base_price) OR
       (OLD.label_price IS DISTINCT FROM NEW.label_price) OR
       (OLD.floor_price IS DISTINCT FROM NEW.floor_price)
    THEN
        INSERT INTO sys_audit_log (table_name, record_id, action, old_data, new_data, changed_by)
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
                'system' -- Dalam realitanya bisa dilempar dari application session scope
               );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trg_audit_m_product_changes
    AFTER UPDATE
    ON m_product
    FOR EACH ROW
EXECUTE FUNCTION log_product_changes();
