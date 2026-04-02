🧩 Project Camel ERP: Toko Pancing (Retail Edition)Dokumen ini berisi perencanaan teknis dan arsitektur untuk sistem ERP Toko Pancing. Fokus utama adalah pembelajaran Spring Boot 4, Java 25, PostgreSQL, dan Vue JS 3 dengan implementasi fitur retail dunia nyata.🚀 Tech Stack & Core ConfigRuntime: Standard JVM (Java 25 HotSpot)Framework: Spring Boot 4 / Spring Framework 7Database: PostgreSQL (JSONB for attributes, Triggers for Audit)Frontend: Vue JS 3 (Vite, Pinia, Composition API)Communication: Spring RestClient (Synchronous Fluent API)Concurrency: Virtual Threads (Project Loom)Config: spring.threads.virtual.enabled=true🎯 Business Domain & LogicDomain: Retail Toko Pancing (Varian Tinggi, SKU Banyak).Studi Kasus: Toko retail konvensional dengan fitur tawar-menawar harga.Flexible Pricing:base_price: Harga Modal (HPP).label_price: Harga Display.floor_price: Batas harga tawar terendah yang diizinkan sistem.Audit Trail: Setiap perubahan stok atau harga wajib dicatat otomatis di level database untuk keamanan data.🏗️ Arsitektur: Modular MonolithAplikasi dibagi menjadi modul-modul terisolasi agar siap dikembangkan atau dipisah (e-commerce integration) di masa depan:modules/inventory: Manajemen barang, varian, dan stok.modules/sales: POS, transaksi, dan diskon.modules/finance: Pencatatan HPP dan Laporan Laba/Rugi.modules/integration: Interface untuk sinkronisasi E-commerce (Shopee/Tokopedia).📊 Database Schema (PostgreSQL)-- 1. Master Store (Multi-branch Ready)
CREATE TABLE m_store (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Product Group (Parent)
CREATE TABLE m_product_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Product Variant (SKU)
CREATE TABLE m_product (
    id BIGSERIAL PRIMARY KEY,
    product_group_id BIGINT REFERENCES m_product_group(id),
    store_id INT REFERENCES m_store(id),
    sku VARCHAR(50) UNIQUE NOT NULL,
    attributes JSONB NOT NULL, -- Contoh: {"color": "Hitam", "size": 1}
    base_price DECIMAL(15, 2) NOT NULL,
    label_price DECIMAL(15, 2) NOT NULL,
    floor_price DECIMAL(15, 2) NOT NULL,
    stock_quantity INT DEFAULT 0,
    min_stock_level INT DEFAULT 5,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Sales Transaction
CREATE TABLE t_sales (
    id BIGSERIAL PRIMARY KEY,
    store_id INT REFERENCES m_store(id),
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    payment_method VARCHAR(20), -- Cash, QRIS, Transfer
    transaction_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_sales_detail (
    id BIGSERIAL PRIMARY KEY,
    sales_id BIGINT REFERENCES t_sales(id),
    product_id BIGINT REFERENCES m_product(id),
    quantity INT NOT NULL,
    cost_price_at_time DECIMAL(15, 2) NOT NULL,
    sold_price_at_time DECIMAL(15, 2) NOT NULL,
    subtotal DECIMAL(15, 2) NOT NULL
);

-- 5. Audit Log System
CREATE TABLE sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(50),
    record_id BIGINT,
    action VARCHAR(10), -- INSERT, UPDATE, DELETE
    old_data JSONB,
    new_data JSONB,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- SKU Sequence Generator
CREATE SEQUENCE sku_sequence START 1;
⚙️ Fitur Otomatisasi (Database & Backend)1. SKU Generator Logic:Pola: [BRAND]-[CAT]-[ATTR]-[SEQ]Fungsi Postgres untuk sequence:CREATE OR REPLACE FUNCTION get_next_sku_seq() RETURNS TEXT AS $$
BEGIN
    RETURN LPAD(nextval('sku_sequence')::TEXT, 3, '0');
END;
$$ LANGUAGE plpgsql;
2. Java 25 Scoped Values (Store Context):Digunakan untuk melempar store_id secara aman antar service tanpa mengotori parameter method.3. Pessimistic Locking:Setiap transaksi POS akan mengunci baris stok di database menggunakan SELECT ... FOR UPDATE via Spring Data JPA @Lock(PESSIMISTIC_WRITE).🔌 Integration Interface (Future-Ready)Meskipun belum diimplementasikan, kontrak integrasi e-commerce dirancang menggunakan Sealed Interface di Java 25:public sealed interface EcommerceIntegration permits ShopeeAdapter, TokopediaAdapter {
    void pushStockUpdate(String sku, int currentStock);
    void processIncomingOrder(OrderPayload payload);
}
📅 Next Steps[ ] Setup Project Spring Boot 4 di IDE pilihan.[ ] Konfigurasi docker-compose.yml untuk PostgreSQL 16+ & pgAdmin.[ ] Setup Flyway untuk inisialisasi tabel di atas.[ ] Implementasi REST API untuk Manajemen Produk (Varian).[ ] Build UI POS Responsif dengan Vue JS 3.