# Implementation: Stock Adjustment with Pessimistic Lock (Task 3.4)

**Branch:** `develop/task-3.4-stock-adjustment`
**Base:** `develop/task-3.3-product-crud`
**Date:** 2026-06-07
**Status:** Complete

## What Was Built

Two endpoints for manual stock management: stock-in (addition) and stock-out (deduction). Uses `@Lock(PESSIMISTIC_WRITE)` to prevent race conditions in a virtual thread environment. Adjustment reasons are stored in `sys_audit_log.notes` via PostgreSQL session variable.

## Commits

| Hash | Message |
|------|---------|
| `TBD` | feat(inventory): add stock adjustment endpoints with pessimistic locking |

## Endpoints

| Method | Path | Status | Notes |
|--------|------|--------|-------|
| POST | `/api/products/{id}/stock/in` | 200 | Add stock with reason |
| POST | `/api/products/{id}/stock/out` | 200 | Deduct stock (validates sufficiency) |

Both return `ProductResponse` with updated `stockQuantity`.

## Files Created (7)

| File | Purpose |
|------|---------|
| `db/migration/V3__Add_stock_adjustment_notes.sql` | Add `notes` column, update `log_product_changes()` trigger |
| `dto/StockAdjustRequest.java` | `quantity` (@Min(1)) + `reason` (@NotBlank @Size(max=500)) |
| `service/StockAdjustmentService.java` | Interface: `adjustIn()`, `adjustOut()` |
| `service/impl/StockAdjustmentServiceImpl.java` | `@Transactional` stock operations with pessimistic lock |
| `controller/StockAdjustmentController.java` | REST controller at `/api/products/{id}/stock` |
| `StockAdjustmentServiceTest.java` | 7 unit tests (Mockito) |
| `StockAdjustmentControllerTest.java` | 10 controller tests (MockMvcTester) |

## Files Modified (2)

| File | Change |
|------|--------|
| `entity/ProductModel.java` | Added `addStock(int)` and `subtractStock(int)` methods |
| `repository/ProductRepository.java` | Added `findByIdAndStoreModelIdForUpdate()` with `@Lock(PESSIMISTIC_WRITE)` |

## Key Implementation Details

### Pessimistic Locking
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.storeModel.id = :storeId")
Optional<ProductModel> findByIdAndStoreModelIdForUpdate(@Param("id") Long id, @Param("storeId") Integer storeId);
```
- Translates to `SELECT ... FOR UPDATE` at DB level
- Row locked until transaction commits/rollbacks
- Mandatory per CLAUDE.md for stock deduction operations
- Separate method from `findByIdAndStoreModelId` to keep non-stock reads lock-free

### Audit Trail via Session Variable
1. `StockAdjustmentService.setStockNotes(reason)` calls `SELECT set_config('app.stock_notes', :reason, true)`
2. This sets a PostgreSQL session-local variable scoped to the current transaction
3. When `productRepository.save()` fires the `log_product_changes()` trigger, it reads `current_setting('app.stock_notes', true)` and writes it to `sys_audit_log.notes`
4. `true` parameter means "return NULL if not set" — safe for price updates not going through StockAdjustmentService

### Stock Operations
- `addStock(delta)`: `stockQuantity += delta` (no upper bound)
- `subtractStock(quantity)`: throws `IllegalArgumentException` if `stockQuantity < quantity`
- Both methods are public and operate directly on the field (allowed by `@Setter(AccessLevel.PACKAGE)`)
- DB `CHECK (stock_quantity >= 0)` provides safety net for concurrent edge cases

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Two separate endpoints | `/in` and `/out` | Explicit intent, different validation |
| Pessimistic lock | `@Lock(PESSIMISTIC_WRITE)` | Required for stock ops in virtual thread env |
| Reason storage | PostgreSQL session variable + trigger | No new tables, persists in audit log |
| Return type | `ProductResponse` | Client gets current stock without extra round trip |
| Dedicated service | `StockAdjustmentService` | Different locking semantics; single responsibility |
| Negative stock guard | Service + DB constraint | Defense in depth; service gives clear message |

## Test Coverage

| Test Class | Tests | Covers |
|-----------|-------|--------|
| `StockAdjustmentServiceTest` | 7 | adjustIn: increment, not found, session variable; adjustOut: decrement, exact→zero, insufficient, not found |
| `StockAdjustmentControllerTest` | 10 | in: valid, quantity=0, negative, blank reason, 404, missing header; out: valid 200, insufficient 422, 404, missing header |

## Design Spec

`docs/superpowers/specs/2026-06-07-stock-adjustment-design.md`
