# 🎯 Rencana Implementasi: Project Widuri ERP (Toko Pancing)

Halo! Dokumen ini adalah panduan detail langkah demi langkah (blueprint) bagi kamu sebagai Junior Programmer untuk memulai dan mengembangkan **Sistem ERP Toko Pancing** berdasarkan arsitektur yang sudah kita sepakati bersama di `GEMINI.md`.

Tech stack kita menggunakan teknologi mutakhir: **Java 25, Spring Boot 4, PostgreSQL, dan Vue JS 3**. Mohon ikuti tahapan-tahapan di bawah ini secara sekunsial (berurutan) karena setiap tahapan dibangun di atas tahap sebelumnya.

---

## Tahap 1: Persiapan Lingkungan & Database (Infrastructure Setup)
Fokus awal kita adalah menyiapkan pondasi database dan aplikasi.

- [ ] **Langkah 1.1: Setup Docker Compose (PostgreSQL)**
  - Buat file `docker-compose.yml` di root direktori untuk PostgreSQL 16+ dan pgAdmin.
  - Jangan ragu untuk menggunakan default port (5432) dan set *volume* agar data tidak hilang ketika container mati.
- [ ] **Langkah 1.2: Inisialisasi Project Spring Boot 4**
  - Gunakan [Spring Initializr](https://start.spring.io) atau IDE favoritmu (IntelliJ/Eclipse/VSCode).
  - Pilih **Java 25**, **Spring Boot 4**, dan tambahkan dependensi: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, dan Validation.
- [ ] **Langkah 1.3: Konfigurasi Virtual Threads**
  - Buka file `application.properties` (atau `application.yml`).
  - Tambahkan konfigurasi ini: `spring.threads.virtual.enabled=true`. Ini sifatnya MANDATORY karena kita akan memaksimalkan Project Loom dari Java.
- [ ] **Langkah 1.4: Setup Flyway & Skema Database**
  - Buat file migrasi pertama di folder `src/main/resources/db/migration` (contoh: `V1__init_schema.sql`).
  - Copy-paste skema DDL yang ada di dokumentasi spesifikasi (`m_store`, `m_product`, `t_sales`, dll) beserta DDL Sequence `sku_sequence` dan `sys_audit_log`.

---

## Tahap 2: Struktur Project & Desain Modular Monolith
Aplikasi kita bukan microservice murni, tapi **Modular Monolith**. Artinya semua ada dalam satu project tapi strukturnya terpisah rapat berdasarkan domain.

- [ ] **Langkah 2.1: Buat Struktur Package Berdasarkan Domain**
  - Di dalam package utama `com.widuri.erp` (atau sesuai penamaanmu), buat 4 package besar:
    - `modules.inventory`
    - `modules.sales`
    - `modules.finance`
    - `modules.integration`
  - *Aturan Main:* Komponen inventory tidak boleh melakukan query langsung ke tabel sales. Mereka harus berkomunikasi menggunakan method public dari Service-nya agar terisolasi.

---

## Tahap 3: Implementasi Backend Core (Java 25 & JPA)
Sekarang kita mulai melakukan koding Java menggunakan fitur-fitur baru Java 25.

- [ ] **Langkah 3.1: Entitas & JSONB Mapping**
  - Buat class-class `@Entity` (JPA Entity) untuk seluruh tabel.
  - Pada field `attributes` di `m_product`, karena tipe datanya JSONB, kamu bisa mengonfigurasinya menggunakan fitur Hibernate yang mendukung `import org.hibernate.annotations.JdbcTypeCode` tipe data JSON.
- [ ] **Langkah 3.2: Scoped Values untuk Store Context**
  - Buat sebuah filter/interceptor yang menangkap *header* HTTP (misal `X-Store-Id`).
  - Masukkan parameter ini ke dalam **Scoped Values** (fitur concurrency Java terkini sebagai pengganti *ThreadLocal*). Ini memastikan `storeId` dapat diakses dari Controller hingga Repository tanpa perlu mengirim parameter eksplisit pada setiap method.
- [ ] **Langkah 3.3: Pessimistic Locking untuk Transaksi Kasir**
  - Di dalam class `ProductRepository` kamu, saat membuat query memotong stok (update stock quantity), WAJIB tambahkan anotasi Spring Data JPA ini: `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  - Ini akan mencegah bug perebutan stok saat ada kasir berbeda checkout barang yang sama.
- [ ] **Langkah 3.4: Integration Sealed Interface**
  - Implementasikan interface kontrak e-commerce dengan syntax terbaru Java di dalam modul `integration`.
  - Contoh:
    ```java
    public sealed interface EcommerceIntegration permits ShopeeAdapter, TokopediaAdapter {
        void pushStockUpdate(String sku, int currentStock);
        void processIncomingOrder(OrderPayload payload);
    }
    ```
    Buat juga dua class adapter (kosongan dulu) yang melakukan `implements` ke interface ini.

---

## Tahap 4: Implementasi Business Logic (API)
Fokus pada alur bisnis utama. Gunakan **Spring RestClient** untuk pemanggilan network keluar nantinya.

- [ ] **Langkah 4.1: SKU Generator**
  - Buat service di `inventory` untuk generate produk.
  - Gunakan logic: Format nama `[BRAND]-[CAT]-[ATTR]-[SEQ]`.
  - Untuk nomer sekuens (SEQ), ambil value tersebut dari sequence *PostgreSQL* `get_next_sku_seq()` menggunakan custom native query di Spring Data JPA.
- [ ] **Langkah 4.2: REST API Transaksi Penjualan (POS)**
  - Buat API Controller di `modules.sales` untuk Post Checkout Keranjang Belanja.
  - Terapkan validasi `floor_price` (Harga akhir/tawar tidak boleh lebih kecil dari `floor_price`). Hitung `subtotal` dengan hati-hati menggunakan tipe data `BigDecimal`.
- [ ] **Langkah 4.3: Audit Log secara Programmatic**
  - Meskipun kita bisa pakai DB trigger untuk murni database, kamu juga direkomendasikan mengimplementasikan `@EntityListeners` atau interseptor JPA yang otomatis memasukkan log lama dan data baru secara JSON ke `sys_audit_log`.

---

## Tahap 5: Pengembangan Frontend (Vue JS 3)
Aplikasi API siap digunakan. Saatnya membuat UI/UX untuk Point of Sales.

- [ ] **Langkah 5.1: Inisialisasi Vite + Vue 3**
  - Masuk ke direktori baru (misal folder `frontend` di root).
  - Jalankan command bawaan NPM: `npm create vite@latest . -- --template vue`.
- [ ] **Langkah 5.2: Instalasi State Management dan Router**
  - Install **Pinia** (State Management) untuk menyimpan data keranjang belanja lokal sementara.
  - Install Vue Router untuk memisahkan halaman `Inventory List` dan `Point of Sales (Kasir)`.
- [ ] **Langkah 5.3: Integrasi API**
  - Gunakan Fetch API standar bawaan browser *(atau axios jika benar-benar butuh)*. Gunakan Vue Composition API (`setup()`, `ref()`, `reactive()`) untuk me-*consume* API backend yang telah kamu buat. 

---

### Tips dan Perhatian Penting (Best Practices)
1. **Selalu Commit Berkala:** Setiap selesai 1 checkbox di atas, lakukan commit `git` dengan format pesan yang rapi (contoh: `feat: add pessimistic lock on inventory`).
2. **PostgreSQL Triggers:** Jangan lupa untuk membuat satu simple test (baik lewat pgAdmin atau integrasi tes Java) untuk membuktikan apakah table audit terekam ketika ada proses UPDATE data barang. 
3. **Minta Code Review:** Jika terjebak di implementasi **Scoped Values** atau **Sealed Interface**, silakan baca *documentation* Spring Boot 4 dan tanyakan langsung ke saya kembali, ya!

Selamat ngoding! Pelan-pelan tapi pasti. Fokus pada kejelasan, keamanan transaksi, dan kualitas kode yang bersih.
