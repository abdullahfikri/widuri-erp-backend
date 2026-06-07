# Design: Stock Adjustment dengan Pessimistic Lock

**Date:** 2026-06-07  
**Task:** 3.4  
**Branch:** `develop/task-3.4-stock-adjustment`

## Overview

Dua endpoint untuk mengelola stok produk secara manual: stock-in (penambahan) dan stock-out (pengurangan). Menggunakan `@Lock(PESSIMISTIC_WRITE)` untuk mencegah race condition di lingkungan virtual thread. Alasan adjustment disimpan di `sys_audit_log.notes` via PostgreSQL session variable yang dibaca oleh trigger yang sudah ada.

---

## Endpoints

| Method | Path | Status | Keterangan |
|--------|------|--------|------------|
| POST | `/api/products/{id}/stock/in` | 200 | Tambah stok (pembelian, retur) |
| POST | `/api/products/{id}/stock/out` | 200 | Kurangi stok (kerusakan, hilang) |

Response: `ProductResponse` (state produk terbaru termasuk `stockQuantity`).

---

## Data Flow

### Stock-In

```
POST /api/products/{id}/stock/in
  Body: { "quantity": 10, "reason": "Pembelian dari Toko Maju" }

  1. Controller: @Valid on StockAdjustRequest
     → quantity <= 0 atau reason blank → 400 VALIDATION_FAILED

  2. Service: StoreContext.assertBound()
     Integer storeId = StoreContext.STORE_ID.get()

  3. Service (@Transactional):
     ProductModel product = productRepository
             .findByIdAndStoreModelIdForUpdate(id, storeId)
             → @Lock(PESSIMISTIC_WRITE) — baris dikunci sampai commit/rollback
             → 404 jika tidak ada atau beda store

  4. Service: SET LOCAL session variable untuk trigger
     entityManager.createNativeQuery(
         "SELECT set_config('app.stock_notes', :reason, true)")
         .setParameter("reason", request.reason())
         .getSingleResult();

  5. Service: product.addStock(request.quantity())

  6. Service: productRepository.save(product)
     → PostgreSQL trigger log_product_changes() fires:
        INSERT INTO sys_audit_log(changed_field, old_value, new_value, notes, changed_at)
        VALUES ('stock_quantity', old, new, current_setting('app.stock_notes',true), now())

  7. Return 200 + ProductResponse.from(product)
```

### Stock-Out

Identik dengan stock-in, kecuali step 5:

```
  5. Service: product.subtractStock(request.quantity())
     → if stockQuantity < quantity → throw IllegalArgumentException → 422
     → DB CHECK (stock_quantity >= 0) sebagai safety net concurrent
```

---

## DTO

```java
public record StockAdjustRequest(
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Size(max = 500) String reason
)
```

Response: reuse `ProductResponse` yang sudah ada (berisi `stockQuantity` terbaru).

---

## Komponen

### ProductModel — tambah dua method

```java
public void addStock(int delta) {
    this.stockQuantity += delta;
}

public void subtractStock(int quantity) {
    if (this.stockQuantity < quantity) {
        throw new IllegalArgumentException(
                "Insufficient stock: " + stockQuantity + " available, " + quantity + " requested");
    }
    this.stockQuantity -= quantity;
}
```

`stockQuantity` field-nya `@Setter(AccessLevel.PACKAGE)` — method public di dalam class sendiri dapat memodifikasi field langsung.

### ProductRepository — tambah satu query

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.storeModel.id = :storeId")
Optional<ProductModel> findByIdAndStoreModelIdForUpdate(
        @Param("id") Long id,
        @Param("storeId") Integer storeId);
```

### StockAdjustmentService (interface)

```java
public interface StockAdjustmentService {
    ProductResponse adjustIn(Long productId, StockAdjustRequest request);
    ProductResponse adjustOut(Long productId, StockAdjustRequest request);
}
```

### StockAdjustmentServiceImpl

```java
@Service
public class StockAdjustmentServiceImpl implements StockAdjustmentService {
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    // constructor injection

    @Override
    @Transactional  // wajib: set_config dan save harus dalam satu transaksi
    public ProductResponse adjustIn(Long productId, StockAdjustRequest request) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel product = productRepository
                .findByIdAndStoreModelIdForUpdate(productId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        setStockNotes(request.reason());
        product.addStock(request.quantity());
        return ProductResponse.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse adjustOut(Long productId, StockAdjustRequest request) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel product = productRepository
                .findByIdAndStoreModelIdForUpdate(productId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        setStockNotes(request.reason());
        product.subtractStock(request.quantity());
        return ProductResponse.from(productRepository.save(product));
    }

    private void setStockNotes(String reason) {
        entityManager.createNativeQuery(
                "SELECT set_config('app.stock_notes', :reason, true)")
                .setParameter("reason", reason)
                .getSingleResult();
    }
}
```

### StockAdjustmentController

```java
@RestController
@RequestMapping("${app.api-path-prefix}products/{id}/stock")
public class StockAdjustmentController {
    private final StockAdjustmentService stockAdjustmentService;

    public StockAdjustmentController(StockAdjustmentService stockAdjustmentService) {
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @PostMapping(value = "/in",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> adjustIn(
            @PathVariable Long id,
            @RequestBody @Valid StockAdjustRequest request
    ) {
        return ResponseEntity.ok(stockAdjustmentService.adjustIn(id, request));
    }

    @PostMapping(value = "/out",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> adjustOut(
            @PathVariable Long id,
            @RequestBody @Valid StockAdjustRequest request
    ) {
        return ResponseEntity.ok(stockAdjustmentService.adjustOut(id, request));
    }
}
```

---

## Flyway Migration: V3

```sql
-- V3__Add_stock_adjustment_notes.sql

-- 1. Tambah kolom notes ke sys_audit_log
ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS notes TEXT;

-- 2. Update trigger untuk membaca session variable
CREATE OR REPLACE FUNCTION log_product_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.stock_quantity IS DISTINCT FROM NEW.stock_quantity) OR
       (OLD.base_price IS DISTINCT FROM NEW.base_price) OR
       (OLD.label_price IS DISTINCT FROM NEW.label_price) OR
       (OLD.floor_price IS DISTINCT FROM NEW.floor_price) THEN

        IF (OLD.stock_quantity IS DISTINCT FROM NEW.stock_quantity) THEN
            INSERT INTO sys_audit_log(product_id, changed_field, old_value, new_value, notes, changed_at)
            VALUES (
                NEW.id,
                'stock_quantity',
                OLD.stock_quantity::TEXT,
                NEW.stock_quantity::TEXT,
                current_setting('app.stock_notes', true),  -- null-safe: returns NULL if not set
                NOW()
            );
        END IF;

        -- price changes: notes tidak relevan untuk harga
        IF (OLD.base_price IS DISTINCT FROM NEW.base_price) THEN
            INSERT INTO sys_audit_log(product_id, changed_field, old_value, new_value, changed_at)
            VALUES (NEW.id, 'base_price', OLD.base_price::TEXT, NEW.base_price::TEXT, NOW());
        END IF;
        -- ... (label_price, floor_price serupa)
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

> **Catatan:** `current_setting('app.stock_notes', true)` mengembalikan NULL jika variabel tidak di-set — aman untuk price updates yang tidak melalui StockAdjustmentService.

---

## Error Handling

| Skenario | Exception | HTTP | Code |
|----------|-----------|------|------|
| Product tidak ditemukan / beda store | `EntityNotFoundException` | 404 | `ENTITY_NOT_FOUND` |
| Stok tidak cukup (stock-out) | `IllegalArgumentException` | 422 | `INVALID_INPUT` |
| quantity ≤ 0 atau reason blank | `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| DB `CHECK (stock >= 0)` gagal (concurrent edge case) | `DataIntegrityViolationException` | 409 | `DATA_CONFLICT` |

Tidak perlu handler baru di `GlobalExceptionHandler`.

---

## Files

### Baru
| File | Keterangan |
|------|------------|
| `dto/StockAdjustRequest.java` | Request DTO |
| `service/StockAdjustmentService.java` | Interface |
| `service/impl/StockAdjustmentServiceImpl.java` | Implementation |
| `controller/StockAdjustmentController.java` | REST controller |
| `resources/db/migration/V3__Add_stock_adjustment_notes.sql` | Flyway migration |

### Dimodifikasi
| File | Perubahan |
|------|-----------|
| `entity/ProductModel.java` | Tambah `addStock()` dan `subtractStock()` |
| `repository/ProductRepository.java` | Tambah `findByIdAndStoreModelIdForUpdate()` |

---

## Tests

### StockAdjustmentServiceTest (Mockito + @Transactional)

- `adjustIn_validQuantity_incrementsStockAndSaves`
- `adjustIn_productNotFound_throwsEntityNotFoundException`
- `adjustIn_wrongStore_throwsEntityNotFoundException`
- `adjustIn_setsSessionVariableBeforeSave`
- `adjustOut_validQuantity_decrementsStock`
- `adjustOut_exactCurrentStock_decrementsToZero`
- `adjustOut_insufficientStock_throwsIllegalArgumentException`
- `adjustOut_productNotFound_throwsEntityNotFoundException`

### StockAdjustmentControllerTest (WebMvcTest)

- `in_validRequest_returns200WithUpdatedStock`
- `out_validRequest_returns200WithUpdatedStock`
- `in_quantityZero_returns400`
- `in_quantityNegative_returns400`
- `out_blankReason_returns400`
- `in_productNotFound_returns404`
- `out_insufficientStock_returns422`

---

## Design Decisions

| Keputusan | Pilihan | Alasan |
|-----------|---------|--------|
| Dua endpoint terpisah | in/out | Intent eksplisit, validasi berbeda per operasi |
| Pessimistic lock | `@Lock(PESSIMISTIC_WRITE)` | Mandatory per CLAUDE.md untuk stock deduction |
| Reason storage | PostgreSQL session variable + trigger | Tanpa tabel baru, tetap tersimpan di audit log |
| Return type | `ProductResponse` | Client langsung tahu stok terkini tanpa round trip tambahan |
| Dedicated service | `StockAdjustmentService` (bukan ProductService) | Locking semantics berbeda; single responsibility |
| Negative stock guard | Di `subtractStock()` + DB constraint | Defense in depth; service layer memberi pesan jelas |

## Out of Scope

- Absolute stock set (opname) — bisa ditambah nanti
- Transfer antar toko
- Batch adjustment
- API untuk membaca histori dari sys_audit_log
