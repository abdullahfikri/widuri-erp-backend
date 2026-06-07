# Code Review — branch develop/task-3.2-sku-generator

**Reviewer:** Claude Code (automated multi-angle review)  
**Date:** 2026-06-07  
**Scope:** diff dari `main` ke `develop/task-3.2-sku-generator`

---

## Findings

### [BUG-1] HIGH — normalizeBrand() tidak trim whitespace

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/service/impl/ProductGroupServiceImpl.java`  
**Line:** 90

**Masalah:**  
`normalizeBrand()` hanya mengecek `isBlank()` tapi tidak memanggil `trim()`. Brand seperti `"  Shimano  "` (ada spasi di tepi) lolos `isBlank()` dan dikembalikan apa adanya. Akibatnya:
- `isDuplicate()` mencari `"  Shimano  "` di DB, tidak cocok dengan `"Shimano"` yang sudah ada → tidak terdeteksi sebagai duplikat
- Brand dengan spasi tersebut tersimpan di DB → dua product group dengan brand logis sama

**Kode saat ini:**
```java
private static String normalizeBrand(String brand) {
    return (brand == null || brand.isBlank()) ? null : brand;
}
```

**Fix:**
```java
private static String normalizeBrand(String brand) {
    return (brand == null || brand.isBlank()) ? null : brand.trim();
}
```

**Test yang perlu ditambahkan:**
- `create_brandWithPaddedSpaces_trimsBeforeSaveAndDuplicateCheck`

---

### [BUG-2] MEDIUM — normalize() tidak menangani spasi ganda dan whitespace non-ASCII

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/service/impl/SkuGeneratorServiceImpl.java`  
**Line:** 29

**Masalah (dua sub-issues dalam satu baris):**

**a) Spasi ganda → double dash:**  
`replace(' ', '-')` mengganti setiap karakter spasi secara individual. Input `"Spinning  Reel"` (dua spasi) menghasilkan `"SPINNING--REEL"` (dua dash) — SKU format rusak.

**b) Tab/NBSP lolos filter:**  
`replace(' ', '-')` hanya menangani ASCII space (U+0020). Tab (`\t`), non-breaking space (U+00A0), dan whitespace Unicode lainnya tidak diganti dan masuk ke SKU sebagai karakter tidak valid.

**Kode saat ini:**
```java
return value.trim().toUpperCase().replace(' ', '-');
```

**Fix:**
```java
return value.trim().toUpperCase().replaceAll("\\s+", "-");
```
`\s+` menangani semua whitespace Unicode dan meng-collapse spasi berurutan menjadi satu dash.

**Test yang perlu ditambahkan:**
- `generate_categoryWithMultipleConsecutiveSpaces_collapsesToSingleDash`
- `normalize_tabCharacter_replacedWithDash` (opsional)

---

### [EFFICIENCY-1] LOW — isDuplicate() adalah round trip redundan

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/service/impl/ProductGroupServiceImpl.java`  
**Lines:** 39–41 (di `create()`), 60–62 (di `update()`)

**Masalah:**  
`isDuplicate()` menjalankan SELECT query sebelum `save()`. Tapi `saveAndHandleDuplicate()` sudah menangkap `DataIntegrityViolationException` dan mengkonversinya ke `DuplicateEntityException`. Fast-path ini hanya menambah latency tanpa manfaat correctness:
- `create()`: 2 DB queries per request (SELECT isDuplicate + INSERT)
- `update()`: 3 DB queries per request (SELECT findById + SELECT isDuplicate + UPDATE)

**Fix (opsional):** Hapus blok `if (isDuplicate(...)) throw ...` di kedua method, dan hapus method `isDuplicate()` beserta keempat derived query methods di `ProductGroupRepository`. `saveAndHandleDuplicate()` sudah menjadi satu-satunya guard yang diperlukan.

> Catatan: Ini trade-off. Fast-path memberi error yang sedikit lebih cepat (sebelum write) dan menghindari write ke DB. Tapi mengorbankan 1 extra SELECT pada setiap request sukses. Keputusan di tangan developer.

---

### [ARCHITECTURE-1] LOW — SkuRepository piggyback JpaRepository\<ProductModel\>

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/repository/SkuRepository.java`  
**Line:** 7

**Masalah:**  
```java
public interface SkuRepository extends JpaRepository<ProductModel, Long>
```
`SkuRepository` hanya membutuhkan satu native scalar query — tidak ada entity management ProductModel yang dibutuhkan. Risiko nyata: ketika `ProductRepository` dibuat di Task 3.3 (juga `extends JpaRepository<ProductModel, Long>`), Spring akan gagal start dengan `NoUniqueBeanDefinitionException` karena ada dua JpaRepository untuk entity yang sama.

**Fix:** Ganti dengan `JdbcClient` (Spring 6+) atau inject `EntityManager` langsung:
```java
@Repository
public class SkuRepository {
    private final JdbcClient jdbcClient;

    public SkuRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public String getNextSkuSequence() {
        return jdbcClient.sql("SELECT get_next_sku_seq()")
                .query(String.class)
                .single();
    }
}
```
Update `SkuGeneratorServiceImpl` dan test-nya sesuai.

---

### [DOCUMENTATION-1] LOW — @UniqueConstraint tidak mencerminkan kasus null brand

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/entity/ProductGroupModel.java`  
**Lines:** 18–22

**Masalah:**  
```java
@UniqueConstraint(name = "uq_product_group_name_brand", columnNames = {"name", "brand"})
```
Annotation ini hanya menggambarkan constraint untuk baris di mana `brand IS NOT NULL`. Kasus `brand IS NULL` ditangani oleh partial index terpisah di `V2__Add_product_group_unique_constraints.sql`:
```sql
CREATE UNIQUE INDEX uq_product_group_name_null_brand ON m_product_group (name) WHERE brand IS NULL;
```
Developer yang hanya membaca entity akan salah sangka constraint sudah lengkap.

**Fix:** Tambahkan komentar:
```java
// Constraint ini hanya mencakup baris dengan brand IS NOT NULL.
// Kasus brand IS NULL ditangani oleh partial index uq_product_group_name_null_brand
// di V2__Add_product_group_unique_constraints.sql — lihat juga isDuplicate() di service layer.
@UniqueConstraint(name = "uq_product_group_name_brand", columnNames = {"name", "brand"})
```

---

### [SIMPLIFICATION-1] — Asimetri populasi entity antara create() dan update()

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/service/impl/ProductGroupServiceImpl.java`  
**Lines:** 43–49 (`create()`), 64 (`update()`)

**Masalah:**  
`create()` membangun `ProductGroupModel` via builder inline dengan 4 field. `update()` memanggil `entity.updateFields(name, brand, category, description)`. Menambah field baru ke `ProductGroupRequest` di masa depan memerlukan update di dua tempat berbeda dengan cara yang berbeda.

**Fix:** Tambahkan static factory method di `ProductGroupModel`:
```java
public static ProductGroupModel from(ProductGroupRequest request, String normalizedBrand) {
    return ProductGroupModel.builder()
            .name(request.name())
            .brand(normalizedBrand)
            .category(request.category())
            .description(request.description())
            .build();
}
```
Lalu `create()` cukup: `saveAndHandleDuplicate(ProductGroupModel.from(request, brand))`.

---

### [SIMPLIFICATION-2] — Local variable tidak perlu di create()

**File:** `src/main/java/id/my/mfikriproject/widuri/erp/modules/inventory/controller/ProductGroupController.java`  
**Lines:** 46–49

**Masalah:** `create()` menggunakan pola yang berbeda dari endpoint lain di controller yang sama:
```java
ProductGroupResponse response = productGroupService.create(request);  // local var
return ResponseEntity.status(HttpStatus.CREATED).body(response);
```
Sedangkan `update()` dan `findById()` inline langsung.

**Fix:**
```java
return ResponseEntity.status(HttpStatus.CREATED).body(productGroupService.create(request));
```

---

## Prioritas Perbaikan

| ID | Severity | Bisa di-fix segera? |
|----|----------|---------------------|
| BUG-1 | HIGH | Ya — 1 baris |
| BUG-2 | MEDIUM | Ya — 1 baris |
| ARCHITECTURE-1 | LOW | Sebaiknya sebelum Task 3.3 dibuat |
| EFFICIENCY-1 | LOW | Opsional, trade-off |
| DOCUMENTATION-1 | LOW | Ya — komentar saja |
| SIMPLIFICATION-1 | LOW | Opsional |
| SIMPLIFICATION-2 | LOW | Ya — 1 baris |

## Verifikasi setelah fix

```bash
./mvnw test
```

Semua test yang ada harus tetap pass. BUG-1 dan BUG-2 memerlukan test case baru agar tidak regresi.
