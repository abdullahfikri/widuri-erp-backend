# Widuri ERP — Task Roadmap untuk Junior Programmer

> Ikuti task secara berurutan dalam setiap phase. Tandai `[x]` setiap task yang selesai sebelum lanjut ke task berikutnya.
>
> **Aturan branch:** Satu branch per task. Format: `develop/task-X.Y-nama-singkat`

---

## Status Phase 1 (Selesai)

- [x] **Task 1.1** — Setup Docker Compose (PostgreSQL 18 + pgAdmin)
- [x] **Task 1.2** — Init Spring Boot 4 + Java 25
- [x] **Task 1.3** — Konfigurasi Virtual Threads (`spring.threads.virtual.enabled=true`)
- [x] **Task 1.4** — Flyway schema `V1__Init_Schema.sql`

> **Langkah sebelum lanjut:** Commit dan merge branch `develop/task-1.3-1.4-flyway-schema` ke `main` terlebih dahulu.

---

## Phase 2 — Struktur & Infrastruktur Backend

> **Konsep yang dipelajari:** Package-by-feature, JPA Entity mapping, Global Exception Handler, Scoped Values (Java 25)

---

### Task 2.1 — Buat Struktur Package Domain
**Branch:** `develop/task-2.1-package-structure`

- [ ] Buat folder `modules/inventory/` dengan sub-folder: `entity/`, `repository/`, `service/`, `controller/`, `dto/`
- [ ] Buat folder `modules/sales/` dengan sub-folder yang sama
- [ ] Buat folder `modules/finance/` dengan sub-folder: `service/`, `controller/`, `dto/`
- [ ] Buat folder `modules/integration/` dengan sub-folder: `adapter/`
- [ ] Buat `core/GlobalExceptionHandler.java` dengan `@RestControllerAdvice`

**Konsep penting:**
- *Package-by-feature* lebih baik dari *package-by-layer* untuk proyek yang akan tumbuh besar.
- **Aturan isolasi modul:** `SalesService` boleh memanggil `InventoryService`, tetapi `SalesRepository` **tidak boleh** query tabel inventory secara langsung.

---

### Task 2.2 — JPA Entities (Semua Tabel)
**Branch:** `develop/task-2.2-jpa-entities`

- [ ] Buat entity `Store` untuk tabel `m_store`
- [ ] Buat entity `ProductGroup` untuk tabel `m_product_group`
- [ ] Buat entity `Product` untuk tabel `m_product`, dengan field `attributes` bertipe `Map<String, Object>` menggunakan `@JdbcTypeCode(SqlTypes.JSON)`
- [ ] Buat entity `Sales` untuk tabel `t_sales` — **tanpa field `updatedAt`** (transaksi immutable)
- [ ] Buat entity `SalesDetail` untuk tabel `t_sales_detail` — **tanpa field `createdAt`/`updatedAt`**
- [ ] Buat entity `AuditLog` untuk tabel `sys_audit_log`
- [ ] Verifikasi: jalankan aplikasi, pastikan `ddl-auto: validate` tidak error

**Konsep penting:**
- Field uang (`basePrice`, `labelPrice`, `floorPrice`) **wajib** menggunakan `BigDecimal`, bukan `double` atau `float`.
- Tabel transaksi (`t_sales`, `t_sales_detail`) sengaja tidak punya `updated_at` — record transaksi tidak boleh diubah setelah dibuat (audit integrity).
- JSONB mapping contoh:
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> attributes;
  ```

---

### Task 2.3 — Scoped Values untuk Store Context
**Branch:** `develop/task-2.3-store-context`

- [ ] Buat `core/StoreContext.java` dengan `ScopedValue<Integer> STORE_ID`
- [ ] Buat `core/StoreContextFilter.java` (implements `Filter`) yang membaca header `X-Store-Id` dari setiap HTTP request dan menyimpannya ke `ScopedValue`
- [ ] Daftarkan filter di Spring (`@Component` atau `FilterRegistrationBean`)
- [ ] Verifikasi: panggil `StoreContext.STORE_ID.get()` dari dalam sebuah Service, nilainya terbaca

**Konsep penting:**
- `ScopedValue` adalah fitur Java 21+ yang lebih aman dari `ThreadLocal` di era Virtual Threads.
- `ThreadLocal` bisa menyebabkan memory leak dan context pollution karena thread bisa di-reuse oleh request lain.
- Dengan `ScopedValue`, nilai otomatis ter-scope ke satu eksekusi dan tidak bisa diubah sembarangan.

---

### Task 2.4 — Global Exception Handler yang Lengkap
**Branch:** `develop/task-2.4-error-handling`

- [ ] Lengkapi `core/ErrorResponse.java` menjadi record dengan field: `code`, `message`, `details` (list), `timestamp`
- [ ] Di `GlobalExceptionHandler`, tambahkan handler untuk:
  - `MethodArgumentNotValidException` → HTTP 400 dengan list field yang error
  - `EntityNotFoundException` (custom) → HTTP 404
  - `IllegalArgumentException` → HTTP 422 (Unprocessable Entity)
  - `Exception` → HTTP 500 generic
- [ ] Buat `core/EntityNotFoundException.java` sebagai custom RuntimeException

**Konsep penting:**
- Java `record` adalah cara modern membuat DTO immutable — lebih ringkas dari class biasa.
- Format error response harus konsisten di seluruh API agar mudah di-handle oleh frontend.

---

## Phase 3 — Modul Inventory

> **Konsep yang dipelajari:** Repository pattern, Service layer, REST API, SKU generation, Pessimistic Locking

---

### Task 3.1 — CRUD Product Group
**Branch:** `develop/task-3.1-product-group-crud`

- [ ] Buat `ProductGroupRepository` extends `JpaRepository<ProductGroup, Long>`
- [ ] Buat `ProductGroupDto` (record) untuk request dan response
- [ ] Buat `ProductGroupService` dengan method: `findAll`, `findById`, `create`, `update`, `delete`
- [ ] Buat `ProductGroupController` dengan endpoint:
  - `GET /api/inventory/product-groups` → 200
  - `GET /api/inventory/product-groups/{id}` → 200 atau 404
  - `POST /api/inventory/product-groups` → 201 Created
  - `PUT /api/inventory/product-groups/{id}` → 200
  - `DELETE /api/inventory/product-groups/{id}` → 204 No Content

**Konsep penting:**
- **Jangan pernah return Entity JPA langsung dari Controller** — selalu konversi ke DTO. Entity mengandung detail implementasi database (lazy loading, dll) yang tidak seharusnya bocor ke client.
- `@Valid` di parameter Controller untuk aktifkan validasi Bean Validation.
- HTTP status code yang tepat: `201 Created` untuk POST sukses, `204 No Content` untuk DELETE sukses.

---

### Task 3.2 — SKU Generator
**Branch:** `develop/task-3.2-sku-generator`

- [ ] Buat `SkuRepository` dengan native query memanggil fungsi PostgreSQL:
  ```java
  @Query(value = "SELECT get_next_sku_seq()", nativeQuery = true)
  String getNextSkuSequence();
  ```
- [ ] Buat `SkuGeneratorService` dengan method `generate(String brand, String category, String attribute)` yang menghasilkan format `[BRAND]-[CAT]-[ATTR]-[SEQ]`
- [ ] Normalisasi input: uppercase, trim spasi, ganti spasi dengan `-`
- [ ] Contoh output: `SHIMANO-REEL-SILVER-001`

**Konsep penting:**
- Native query di Spring Data JPA digunakan saat query tidak bisa diekspresikan dengan JPQL, misalnya memanggil fungsi atau fitur spesifik PostgreSQL.
- Sequence di database lebih aman untuk generate ID secara concurrent daripada logic di aplikasi — tidak ada duplikasi meski ada ribuan request bersamaan.

---

### Task 3.3 — CRUD Product (SKU + JSONB)
**Branch:** `develop/task-3.3-product-crud`

- [ ] Buat `ProductRepository` extends `JpaRepository<Product, Long>`
- [ ] Buat `CreateProductRequest` DTO dengan validasi lengkap
- [ ] Buat `ProductService` — saat create, panggil `SkuGeneratorService` secara otomatis
- [ ] Tambahkan validasi bisnis di Service: `floor_price <= label_price`, `base_price > 0`
- [ ] Buat `ProductController` dengan endpoint CRUD standar di `/api/inventory/products`
- [ ] Tambahkan endpoint search by attribute: `GET /api/inventory/products?color=Hitam`

**Konsep penting:**
- Validasi bisnis (contoh: `floor_price <= label_price`) letaknya di **Service**, bukan di Entity atau Controller.
- JSONB di PostgreSQL memungkinkan search di dalam field JSON menggunakan GIN index yang sudah dibuat di `V1__Init_Schema.sql`.
- Tiga harga dalam satu SKU (`base`, `label`, `floor`) adalah fitur kunci untuk skenario toko retail yang membolehkan tawar-menawar.

---

### Task 3.4 — Stock Adjustment dengan Pessimistic Lock
**Branch:** `develop/task-3.4-stock-management`

- [ ] Tambahkan method di `ProductRepository` dengan `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);
  ```
- [ ] Buat endpoint `PATCH /api/inventory/products/{id}/stock` dengan body `{ "delta": -2 }` (positif = tambah, negatif = kurangi)
- [ ] Validasi stok tidak boleh negatif setelah dikurangi
- [ ] Tulis unit test yang mensimulasikan dua thread concurrent mengakses stok yang sama

**Konsep penting:**
- **Race condition** adalah bug nyata di sistem kasir: kasir A dan B checkout produk yang stoknya 1 secara bersamaan, tanpa lock, keduanya bisa berhasil dan stok jadi -1.
- **Pessimistic Lock** (`SELECT FOR UPDATE`): baris di-lock sampai transaksi selesai. Dipakai saat konflik sering terjadi (POS kasir ramai).
- **Optimistic Lock** (`@Version`): tidak lock, tapi cek versi saat save. Dipakai saat konflik jarang terjadi.
- `@Transactional` **wajib** ada di method Service yang memanggil query dengan lock.

---

## Phase 4 — Modul Sales (POS)

> **Konsep yang dipelajari:** Transactional business logic, immutable records, invoice generation

---

### Task 4.1 — Invoice Number Generator
**Branch:** `develop/task-4.1-invoice-generator`

- [ ] Buat `InvoiceNumberGenerator` service dengan format: `INV-[STOREID]-[YYYYMMDD]-[SEQ]`
- [ ] Contoh output: `INV-01-20260514-0042`
- [ ] Gunakan `AtomicInteger` untuk counter harian yang thread-safe, atau buat sequence PostgreSQL terpisah
- [ ] Reset counter setiap hari baru

**Konsep penting:**
- `AtomicInteger` adalah cara thread-safe untuk increment counter tanpa `synchronized` block — cocok dipakai dengan Virtual Threads.
- Format tanggal gunakan `java.time.LocalDate` (bukan `java.util.Date` yang sudah deprecated).

---

### Task 4.2 — Checkout API (Core POS)
**Branch:** `develop/task-4.2-sales-checkout`

- [ ] Buat `CheckoutRequest` DTO:
  ```json
  {
    "items": [{ "productId": 1, "quantity": 2, "soldPrice": 150000 }],
    "paymentMethod": "QRIS"
  }
  ```
- [ ] Buat `SalesService.checkout()` dengan logika berurutan **dalam satu `@Transactional`**:
  1. Validasi setiap item: `soldPrice >= product.floorPrice` (throw exception jika tidak)
  2. Lock baris product menggunakan `findByIdForUpdate` (dari Task 3.4)
  3. Kurangi `stock_quantity` setiap produk
  4. Hitung `subtotal = soldPrice * quantity` dan `totalAmount` dengan `BigDecimal`
  5. Simpan `Sales` dan list `SalesDetail`
  6. Return `CheckoutResponse` dengan invoice number
- [ ] Buat `SalesController` dengan endpoint `POST /api/sales/checkout`

**Konsep penting:**
- `@Transactional` di method `checkout()` menjamin: jika satu langkah gagal (misalnya stok kurang di item ke-3), seluruh operasi di-rollback. Tidak ada stok yang terpotong sebagian.
- Harga disimpan `_at_time` (snapshot saat transaksi) — bukan FK ke product — karena harga product bisa berubah di masa depan. Riwayat transaksi harus akurat selamanya.
- `paymentMethod` idealnya divalidasi dengan `enum` Java, bukan string bebas.

---

### Task 4.3 — Riwayat Transaksi
**Branch:** `develop/task-4.3-sales-history`

- [ ] Buat endpoint `GET /api/sales` dengan query params: `?from=2026-05-01&to=2026-05-31&page=0&size=20`
- [ ] Gunakan `Pageable` di Repository untuk pagination
- [ ] Ambil `storeId` dari `StoreContext.STORE_ID.get()` — tidak perlu parameter di method
- [ ] Buat endpoint `GET /api/sales/{invoiceNumber}` untuk detail satu transaksi

**Konsep penting:**
- Pagination dengan `Pageable` mencegah query mengembalikan jutaan baris sekaligus.
- Ini adalah contoh nyata manfaat `ScopedValue` dari Task 2.3: method signature tetap bersih tanpa parameter `storeId` yang harus dioper dari Controller ke Service ke Repository.

---

## Phase 5 — Modul Finance

> **Konsep yang dipelajari:** Aggregation query, laporan bisnis

---

### Task 5.1 — Laporan Laba/Rugi
**Branch:** `develop/task-5.1-profit-loss-report`

- [ ] Buat endpoint `GET /api/finance/reports/profit-loss?from=2026-05-01&to=2026-05-31`
- [ ] Query aggregasi dari `t_sales_detail` menggunakan JPQL:
  - `totalRevenue = SUM(sold_price_at_time * quantity)`
  - `totalCost = SUM(cost_price_at_time * quantity)`
  - `grossProfit = totalRevenue - totalCost`
  - `grossMarginPercent = (grossProfit / totalRevenue) * 100`
- [ ] Kembalikan sebagai `ProfitLossReport` DTO

**Konsep penting:**
- Gunakan *interface-based projection* di Spring Data untuk query yang hanya butuh beberapa kolom aggregate — lebih efisien dari load seluruh entity.
- Semua kalkulasi tetap menggunakan `BigDecimal` dengan `RoundingMode.HALF_UP`.

---

### Task 5.2 — Alert Stok Menipis
**Branch:** `develop/task-5.2-low-stock-alert`

- [ ] Buat endpoint `GET /api/finance/reports/low-stock`
- [ ] Query: `WHERE stock_quantity <= min_stock_level` diurutkan dari yang paling kritis
- [ ] Response: list produk beserta persentase stok sisa vs minimum

---

## Phase 6 — Modul Integration (Skeleton)

> **Konsep yang dipelajari:** Sealed Interface (Java 25), Adapter Pattern, RestClient

---

### Task 6.1 — Sealed Interface E-commerce
**Branch:** `develop/task-6.1-ecommerce-interface`

- [ ] Buat `EcommerceIntegration` sealed interface:
  ```java
  public sealed interface EcommerceIntegration
      permits ShopeeAdapter, TokopediaAdapter {
      void pushStockUpdate(String sku, int currentStock);
      void processIncomingOrder(OrderPayload payload);
  }
  ```
- [ ] Buat `ShopeeAdapter` dan `TokopediaAdapter` yang implement interface ini (isi dengan `log.info(...)` dulu, belum real HTTP call)
- [ ] Daftarkan keduanya sebagai Spring `@Component` dengan `@Qualifier`

**Konsep penting:**
- *Sealed interface* lebih aman dari interface biasa: compiler tahu *semua* implementasi yang valid. Jika ada adapter baru, harus didaftarkan di `permits` — tidak bisa ditambahkan sembarangan dari luar.
- *Adapter Pattern* memisahkan logika bisnis dari detail platform eksternal. Jika Shopee ganti API, hanya `ShopeeAdapter` yang diubah — `SalesService` tidak tersentuh.

---

### Task 6.2 — Stock Sync ke E-commerce
**Branch:** `develop/task-6.2-stock-sync`

- [ ] Inject `List<EcommerceIntegration>` di `InventoryService`
- [ ] Setelah checkout berhasil (Task 4.2), panggil `pushStockUpdate` ke semua adapter aktif
- [ ] Gunakan `RestClient` untuk HTTP call di adapter (bukan `RestTemplate` atau `WebClient`):
  ```java
  restClient.post()
      .uri("/stock-update")
      .body(new StockUpdatePayload(sku, newStock))
      .retrieve()
      .toBodilessEntity();
  ```
- [ ] Tangani error dari adapter dengan baik — kegagalan sync e-commerce **tidak boleh** rollback transaksi kasir

**Konsep penting:**
- `RestClient` adalah API baru di Spring Boot 3.2+ untuk HTTP call synchronous — lebih modern dari `RestTemplate`, lebih sederhana dari `WebClient`.
- Sinkronisasi e-commerce adalah operasi "best effort": jika gagal, transaksi kasir tetap valid. Catat kegagalan ke log, jangan throw exception.

---

## Phase 7 — Testing

> **Konsep yang dipelajari:** Integration test, slice test, test isolation

---

### Task 7.1 — Integration Test Repository dengan @DataJpaTest
**Branch:** `develop/task-7.1-jpa-tests`

- [ ] Buat test untuk `SkuGeneratorService`: generate 10 SKU, verifikasi tidak ada duplikat
- [ ] Buat test untuk Pessimistic Lock: dua thread concurrent kurangi stok yang sama — verifikasi stok akhir akurat
- [ ] Buat test untuk audit trigger PostgreSQL: update harga produk, verifikasi `sys_audit_log` terisi

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE) // pakai PostgreSQL nyata, bukan H2
class ProductRepositoryTest {
    // ...
}
```

**Konsep penting:**
- `@DataJpaTest` hanya load layer JPA — lebih cepat dari `@SpringBootTest` penuh.
- H2 in-memory **tidak bisa dipakai** di proyek ini karena JSONB, native query PostgreSQL, dan trigger tidak didukung H2.
- Tes audit trigger membuktikan bahwa perubahan stok/harga selalu terekam — ini penting untuk compliance dan debugging.

---

### Task 7.2 — Controller Test dengan MockMvc
**Branch:** `develop/task-7.2-controller-tests`

- [ ] Buat test untuk `SalesController`:
  - Positive case: checkout valid → 200 + invoice number
  - Negative case: `soldPrice` di bawah `floorPrice` → 422 Unprocessable Entity
  - Negative case: `productId` tidak ada → 404
- [ ] Buat test untuk `ProductGroupController`: validasi input kosong → 400 dengan list field error

```java
@WebMvcTest(SalesController.class)
class SalesControllerTest {
    @MockitoBean
    SalesService salesService;
    // ...
}
```

**Konsep penting:**
- `@WebMvcTest` hanya load layer Web (Controller, Filter) — sangat cepat.
- `@MockitoBean` mengganti bean Spring yang asli dengan mock — tidak perlu database jalan.
- Test negative case sama pentingnya dengan positive case: pastikan error handling bekerja benar.

---

## Phase 8 — Frontend Vue JS 3

> **Konsep yang dipelajari:** Vite, Composition API, Pinia, Vue Router

---

### Task 8.1 — Setup Vue 3 + Vite
**Branch:** `develop/task-8.1-frontend-init`

- [ ] Buat folder `frontend/` di root project
- [ ] Init project: `npm create vite@latest . -- --template vue`
- [ ] Install dependencies: `npm install pinia vue-router axios`
- [ ] Buat struktur folder:
  ```
  frontend/src/
    views/       ← halaman utama
    components/  ← komponen reusable
    stores/      ← Pinia stores
    services/    ← abstraksi pemanggilan API
    router/      ← konfigurasi Vue Router
  ```
- [ ] Setup Vue Router dengan dua route awal: `/inventory` dan `/pos`
- [ ] Setup Pinia store kosong untuk cart

---

### Task 8.2 — Halaman Manajemen Inventori
**Branch:** `develop/task-8.2-inventory-ui`

- [ ] Tabel list produk dengan kolom: SKU, nama, stok, harga label, status stok
- [ ] Highlight baris jika stok menipis (`stock_quantity <= min_stock_level`)
- [ ] Form tambah/edit product group
- [ ] Form tambah produk dengan field attributes sebagai dynamic key-value pairs
- [ ] Tombol delete dengan konfirmasi dialog

**Konsep penting:**
- Gunakan Composition API (`setup()`, `ref()`, `reactive()`, `computed()`) — bukan Options API.
- Pinia store untuk menyimpan list produk agar tidak re-fetch setiap navigasi.

---

### Task 8.3 — Halaman POS (Kasir)
**Branch:** `develop/task-8.3-pos-ui`

- [ ] Search/scan produk berdasarkan SKU atau nama → tambah ke keranjang
- [ ] Tabel keranjang: nama produk, quantity, input harga jual, subtotal
- [ ] Validasi real-time: tampilkan warning merah jika harga jual di bawah `floor_price`
- [ ] Tombol checkout: pilih metode pembayaran (Cash/QRIS/Transfer) → kirim ke API
- [ ] Tampilkan modal sukses dengan invoice number setelah checkout berhasil
- [ ] Reset keranjang setelah transaksi selesai

**Konsep penting:**
- Pinia cart store menyimpan state keranjang — data tidak hilang saat navigasi antar komponen.
- Validasi `floor_price` harus dilakukan **di frontend** (UX) sekaligus **di backend** (security) — jangan hanya salah satu.

---

## Urutan Prioritas Pengerjaan

```
Phase 2  →  Phase 3  →  Phase 4  →  Phase 7  →  Phase 5  →  Phase 6  →  Phase 8
(Core)      (Inv)       (POS)       (Test)      (Finance)   (Integration) (Frontend)
```

Phase 5, 6, dan 8 bisa dikerjakan paralel setelah Phase 4 selesai.

---

## Aturan Wajib

| # | Aturan |
|---|--------|
| 1 | Satu branch per task. Format: `develop/task-X.Y-nama-singkat` |
| 2 | `ddl-auto: validate` — jangan pernah diubah ke `update`. Perubahan schema = file `V2__....sql` baru |
| 3 | Semua field uang menggunakan `BigDecimal` — tidak ada pengecualian |
| 4 | Jangan return Entity JPA langsung dari Controller — selalu gunakan DTO |
| 5 | `@Transactional` ada di **Service layer**, bukan Controller atau Repository |
| 6 | Di POS, selalu gunakan Pessimistic Lock untuk operasi yang menyentuh stok |
| 7 | Commit setelah setiap task selesai dengan pesan yang deskriptif, contoh: `feat(inventory): add pessimistic lock on stock deduction` |
