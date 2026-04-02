# 🐪 Widuri ERP (Toko Pancing - Retail Edition)

> ⚠️ **Status Project: Sedang Dalam Tahap Pengembangan (Work In Progress)**

Sistem ERP ini dirancang khusus untuk toko retail konvensional (Toko Pancing) yang menangani varian barang (SKU) dalam jumlah banyak, fleksibilitas harga tawar-menawar, serta fitur pencatatan log audit level database. Aplikasi ini direncanakan menggunakan arsitektur **Modular Monolith** untuk menjamin skalabilitas terukur ke depannya.

## 🚀 Tech Stack Utama

- **Runtime:** Java 25 HotSpot
- **Framework:** Spring Boot 4 / Spring Framework 7
- **Database:** PostgreSQL (JSONB, Triggers)
- **Frontend / UI:** Vue JS 3 (Vite, Pinia, Composition API)
- **Concurrency:** Virtual Threads (Project Loom)

## 📦 Pembagian Modul
Projek ini dibangun menggunakan arsitektur Modular Monolith yang terbagi pada:
- `modules/inventory` - Manajemen barang, varian, dan stok.
- `modules/sales` - POS, transaksi tawar-menawar, dan sistem diskon.
- `modules/finance` - Pencatatan Harga Pokok Penjualan (HPP) dan P/L.
- `modules/integration` - Interface adapter sinkronisasi *E-commerce* (Shopee/Tokopedia).

## 💡 Fitur Kunci
- **SKU Generator Otomatis** (Pattern: `[BRAND]-[CAT]-[ATTR]-[SEQ]`) menggunakan function PostgreSQL.
- **Audit Trail Bawaan** via *database triggers* dan/atau JPA Interceptors untuk mencatat *old vs new data* (JSONB).
- **Flexible Pricing** meliputi: Base Price (HPP), Label Price (Display), dan Floor Price (Batas terbawah saat tawar menawar).
- **Pessimistic Locking** tingkat database saat update transaksi *inventory*.

---

*Lihat [issue.md](issue.md) untuk detail perencanaan tugas (Task Board) saat ini.*
