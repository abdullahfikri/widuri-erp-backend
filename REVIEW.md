# Code Review + Security Review — Task 3.3 (Product CRUD)

**Branch:** `develop/task-3.3-product-crud`  
**Date:** 2026-06-07

---

## SECURITY

### [SEC-1] CRITICAL — IDOR: ProductGroupId tidak divalidasi milik store yang sama

**File:** `src/main/java/.../service/impl/ProductServiceImpl.java:63`

```java
ProductGroupModel group = productGroupRepository.findById(request.productGroupId())
        .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));
```

`findById()` mencari di semua store. Attacker dengan X-Store-Id: 1 bisa mengirim `productGroupId` milik Store 2 — produk dibuat di Store 1 tapi mereferensi ProductGroup milik Store 2.

**Fix:** Ganti dengan query yang memvalidasi kepemilikan store:
```java
// Opsi A — tambahkan query di ProductGroupRepository:
productGroupRepository.findByIdAndStoreId(request.productGroupId(), storeId)
        .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));
```
Atau jika ProductGroupModel tidak punya storeId langsung, validasi setelah load:
```java
if (!group.getStoreModel().getId().equals(storeId)) {
    throw new EntityNotFoundException("ProductGroup not found");
}
```

---

### [SEC-2] HIGH — attributes Map tidak ada batasan ukuran/kedalaman (DoS + stored XSS risk)

**File:** `src/main/java/.../dto/CreateProductRequest.java` dan `UpdateProductRequest.java`

```java
Map<String, Object> attributes,  // tidak ada @Size, tidak ada validasi
```

- Payload 10MB dengan deeply nested JSON menguras heap → OutOfMemoryError
- Nilai string dalam map tidak di-sanitize sebelum disimpan ke JSONB dan dikembalikan di response

**Fix:** Tambahkan validasi ukuran. Untuk membatasi kedalaman JSON, konfigurasikan Jackson `DeserializationFeature.FAIL_ON_TOO_DEEP_NESTING` (Spring Boot 2.7+). Minimal batasi jumlah entries:
```java
@Size(max = 50) Map<String, Object> attributes,
```
Atau tambahkan custom validator yang memeriksa total keys dan panjang nilai.

---

### [SEC-3] MEDIUM — X-Store-Id header tidak dikaitkan dengan identitas user yang terautentikasi

**File:** `src/main/java/.../core/StoreContextFilter.java`

StoreContextFilter hanya memvalidasi bahwa `X-Store-Id` adalah integer positif — tidak memverifikasi bahwa user yang terautentikasi memang berhak atas store tersebut. Siapapun yang tahu store ID lain bisa mengakses datanya.

**Catatan:** Ini adalah architectural gap yang lebih besar — membutuhkan Spring Security + user-store authorization. Daftar sebagai known limitation sampai auth layer diimplementasikan.

---

### [SEC-4] MEDIUM — Tidak ada autentikasi/otorisasi di endpoint

**File:** `src/main/java/.../controller/ProductController.java:17`

Tidak ada `@PreAuthorize`, Spring Security config, atau filter autentikasi yang terlihat. Semua endpoint accessible oleh siapapun yang bisa mengirim request valid.

**Catatan:** Sama seperti SEC-3 — known gap, butuh implementasi auth layer terpisah.

---

## CODE QUALITY

### [BUG-1] MEDIUM — Dead try-catch di create() tidak memberikan nilai apapun

**File:** `src/main/java/.../service/impl/ProductServiceImpl.java:90`

```java
try {
    return ProductResponse.from(productRepository.save(product));
} catch (DataIntegrityViolationException e) {
    throw e;  // ← no-op: identik dengan tidak ada try-catch sama sekali
}
```

Berbeda dengan `ProductGroupServiceImpl.saveAndHandleDuplicate()` yang memeriksa nama constraint dan mengkonversi ke `DuplicateEntityException`. Di sini exception hanya di-rethrow ke `handleDataIntegrity()` → 409 `DATA_CONFLICT` tanpa informasi lebih spesifik.

**Fix opsi A** — Hapus try-catch (biarkan propagate alami):
```java
return ProductResponse.from(productRepository.save(product));
```

**Fix opsi B** — Tambahkan handling spesifik seperti ProductGroupServiceImpl:
```java
private static final String SKU_CONSTRAINT = "uq_m_product_sku";

try {
    return ProductResponse.from(productRepository.save(product));
} catch (DataIntegrityViolationException e) {
    if (e.getCause() instanceof ConstraintViolationException cve
            && SKU_CONSTRAINT.equals(cve.getConstraintName())) {
        throw new DuplicateEntityException("Product with this SKU already exists");
    }
    throw e;
}
```

---

### [BUG-2] LOW — minStockLevel tidak di-set saat create — bergantung pada DB default secara implisit

**File:** `src/main/java/.../service/impl/ProductServiceImpl.java:79`

`ProductModel.builder()` di `create()` tidak memanggil `.minStockLevel(...)`. Nilai default 5 datang dari DB schema, tapi tidak terlihat di kode. Jika `CreateProductRequest` memiliki `minStockLevel`, ini harus diteruskan ke builder. Jika memang tidak boleh di-set saat create, dokumentasikan bahwa nilai default adalah 5.

**Fix:** Sesuaikan dengan keputusan desain:
- Jika client boleh set: tambahkan `minStockLevel` ke `CreateProductRequest` dan `.minStockLevel(request.minStockLevel() != null ? request.minStockLevel() : 5)` di builder
- Jika selalu default: tambahkan komentar `// minStockLevel defaults to 5 via DB column default`

---

### [QUALITY-1] LOW — LazyInitializationException potensial di ProductResponse.from()

**File:** `src/main/java/.../dto/ProductResponse.java` (baris `model.getProductGroupModel().getId()`)

`productGroupModel` adalah `FetchType.LAZY`. Jika `from()` dipanggil di luar transaction (misalnya setelah detach), akan throw `LazyInitializationException`. Saat ini aman karena dipanggil langsung dalam service method, tapi rapuh jika pola pemanggilan berubah.

**Fix:** Pastikan `productGroupModel` selalu loaded sebelum mapping, atau gunakan `@EntityGraph` di repository query.

---

## Prioritas Perbaikan

| ID | Severity | Fix sekarang? |
|----|----------|---------------|
| SEC-1 | CRITICAL | **Ya — sebelum merge** |
| SEC-2 | HIGH | Ya — tambahkan `@Size(max=50)` minimal |
| BUG-1 | MEDIUM | Ya — hapus dead try-catch atau lengkapi handling |
| BUG-2 | LOW | Ya — dokumentasikan atau tambahkan ke request |
| SEC-3/4 | MEDIUM | Noted — butuh auth layer terpisah |
| QUALITY-1 | LOW | Opsional — aman untuk sekarang |

## Verifikasi setelah fix

```bash
./mvnw test
```
