# Code Review + Security Review — Task 3.4 (Stock Adjustment)

**Branch:** `develop/task-3.4-stock-adjustment`
**Date:** 2026-06-07

---

## [BUG-1] CRITICAL — NullPointerException di addStock() dan subtractStock()

**File:** `src/main/java/.../entity/ProductModel.java:73,77`

```java
public void addStock(int delta) {
    this.stockQuantity += delta;          // NPE jika stockQuantity null (unboxing)
}
public void subtractStock(int quantity) {
    if (this.stockQuantity < quantity) {  // NPE jika stockQuantity null (unboxing)
```

`stockQuantity` bertipe `Integer` (nullable). DB schema di V1 mendefinisikan:
```sql
stock_quantity INT DEFAULT 0  -- tidak ada NOT NULL!
```
Jika ada baris dengan `stock_quantity = NULL` (produk lama atau insert manual), kedua method throw `NullPointerException` — bukan error yang informatif.

**Fix opsi A** — Tambahkan null guard di method:
```java
public void addStock(int delta) {
    this.stockQuantity = (this.stockQuantity != null ? this.stockQuantity : 0) + delta;
}
public void subtractStock(int quantity) {
    int current = this.stockQuantity != null ? this.stockQuantity : 0;
    if (current < quantity) {
        throw new IllegalArgumentException(
                "Insufficient stock: " + current + " available, " + quantity + " requested");
    }
    this.stockQuantity = current - quantity;
}
```

**Fix opsi B** (lebih permanen) — Tambahkan `NOT NULL DEFAULT 0` ke kolom via migration:
```sql
ALTER TABLE m_product ALTER COLUMN stock_quantity SET NOT NULL;
ALTER TABLE m_product ALTER COLUMN stock_quantity SET DEFAULT 0;
```
Dan ubah field entity ke `int` (primitive).

---

## [BUG-2] MEDIUM — Integer overflow: addStock() tidak punya batas atas

**File:** `src/main/java/.../entity/ProductModel.java:74` dan `src/main/java/.../dto/StockAdjustRequest.java`

`addStock()` tidak memvalidasi upper bound. Kolom DB adalah `INT` (max ~2.1 miliar). Request hanya punya `@Min(1)` tanpa `@Max`.

**Fix:** Tambahkan `@Max(1_000_000)` di `StockAdjustRequest.quantity` (sesuaikan dengan kebutuhan bisnis), atau tambahkan guard di `addStock()`:
```java
if (this.stockQuantity + delta > Integer.MAX_VALUE) {
    throw new IllegalArgumentException("Stock quantity would overflow");
}
```

---

## [QUALITY-1] LOW — Audit log tidak menyimpan identitas user

**File:** `src/main/resources/db/migration/V3__Add_stock_adjustment_notes.sql:36`

```sql
'system',  -- changed_by hardcoded
current_setting('app.stock_notes', true)
```

Audit trail menyimpan `reason` (alasan adjustment) tapi tidak menyimpan siapa yang melakukan adjustment. Semua stock changes tercatat sebagai `changed_by = 'system'`. Untuk retail, ini kehilangan akuntabilitas — tidak tahu kasir/admin mana yang adjust stok.

**Catatan:** Ini adalah architectural gap yang butuh auth layer terpisah. Logged sebagai known limitation.

---

## Temuan yang Aman (tidak perlu tindakan)

- **Urutan V3 migration aman**: kolom `notes` ditambahkan di baris 6 SEBELUM trigger diperbarui di baris 11 — tidak ada runtime error.
- **setStockNotes transaction scope aman**: `@Transactional` di `adjustIn()`/`adjustOut()` memastikan `set_config('app.stock_notes', ..., true)` (is_local=true) dan `save()` dalam satu transaksi DB yang sama.
- **SQL injection aman**: `set_config` menggunakan parameterized query (`:reason`), bukan string concatenation.

---

## Prioritas

| ID | Severity | Fix sekarang? |
|----|----------|---------------|
| BUG-1 | CRITICAL | **Ya — sebelum merge** |
| BUG-2 | MEDIUM | Ya — tambah `@Max` di DTO |
| QUALITY-1 | LOW | Known limitation, butuh auth layer |

## Verifikasi setelah fix

```bash
./mvnw test -Dtest="StockAdjustmentServiceTest,StockAdjustmentControllerTest"
```

Test case tambahan yang perlu dibuat:
- `adjustIn_nullStockQuantity_doesNotThrowNPE`
- `adjustOut_nullStockQuantity_treatsAsZero`
