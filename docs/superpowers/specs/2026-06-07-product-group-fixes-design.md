# Design: ProductGroup CRUD — Code Review Fixes

**Date:** 2026-06-07
**Branch:** develop/task-3.1-product-group-crud
**Scope:** 5 findings from the task 3.1 code review, addressed in order of severity.

---

## Finding 1 — TOCTOU race → wrong error code (CRITICAL)

**Problem:** `create()` and `update()` perform an app-level duplicate check (`existsBy*`) then
call `save()`. Under concurrent virtual-thread requests, two requests can both pass the check.
The losing thread receives a `DataIntegrityViolationException` from the DB, which the global
exception handler maps to `DATA_CONFLICT` — breaking the `DUPLICATE_ENTITY` contract the API
promises.

**Fix:** Wrap `repository.save()` in a try-catch for `DataIntegrityViolationException` and
rethrow as `DuplicateEntityException` in both `create()` and `update()`. The `existsBy*`
fast-path check is retained as an optimization; the try-catch is the safety net.

**Tests added:**
- `create_concurrentDuplicate_throwsDuplicateEntityException`
- `update_concurrentDuplicate_throwsDuplicateEntityException`

---

## Finding 2 — Malformed JSON body → 500 (HIGH)

**Problem:** `HttpMessageNotReadableException` (malformed JSON payload, wrong content-type)
fell through to the generic `Exception` catch-all in `GlobalExceptionHandler`, returning
500 `INTERNAL_ERROR` instead of 400.

**Fix:** Added `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400 `INVALID_INPUT`
in `GlobalExceptionHandler`, matching the pattern of the existing
`MethodArgumentTypeMismatchException` handler.

**Tests added:**
- `create_malformedJson_returns400`
- `update_malformedJson_returns400`

---

## Finding 3 — `delete()` race window and extra round trip (MEDIUM)

**Problem:** `delete()` used `existsById(id)` + `deleteById(id)` — two separate DB queries.
If the entity is deleted between them, `deleteById()` throws `EmptyResultDataAccessException`
→ 500. Also inconsistent with `update()` which uses `findById().orElseThrow()`.

**Fix:** Replaced with `findById(id).orElseThrow(() -> new EntityNotFoundException(...))`
+ `delete(entity)`. One round trip, no race window, consistent with `update()`.

**Test updated:**
- `delete_existingId_callsDeleteById` → `delete_existingId_callsDelete` (verifies `delete(entity)`)

---

## Finding 4 — Duplicate branching logic (LOW)

**Problem:** Both `create()` and `update()` contained the identical brand-null ternary for
duplicate checking. Any change to the duplicate detection logic required two edits.

**Fix:** Extracted `private boolean isDuplicate(String name, String brand, Long excludeId)`.
`create()` passes `excludeId = null`; `update()` passes the actual entity `id`. No new tests
needed — existing duplicate-check tests exercise both branches through the helper.

---

## Finding 5 — Identical DTOs (OPTIONAL)

**Problem:** `CreateProductGroupRequest` and `UpdateProductGroupRequest` were byte-for-byte
identical records. No semantic difference existed between them; the split added maintenance
cost for no benefit.

**Fix:** Replaced both with a single `ProductGroupRequest` record. Deleted the two old files.
Updated `ProductGroupService` interface, `ProductGroupServiceImpl`, `ProductGroupController`,
and all tests to use the unified type.

---

## Files Changed

| File | Change |
|------|--------|
| `service/impl/ProductGroupServiceImpl.java` | Findings 1, 3, 4, 5 |
| `core/GlobalExceptionHandler.java` | Finding 2 |
| `dto/ProductGroupRequest.java` | New (Finding 5) |
| `dto/CreateProductGroupRequest.java` | Deleted (Finding 5) |
| `dto/UpdateProductGroupRequest.java` | Deleted (Finding 5) |
| `service/ProductGroupService.java` | Finding 5 (interface signature) |
| `controller/ProductGroupController.java` | Finding 5 (endpoint signatures) |
| `ProductGroupServiceTest.java` | Findings 1, 3, 5 |
| `ProductGroupControllerTest.java` | Findings 2, 5 |
