# Implementation: Product CRUD + SKU Generation (Task 3.3)

**Branch:** `develop/task-3.3-product-crud`
**Base:** `develop/task-3.2-sku-generator`
**Date:** 2026-06-07
**Status:** Complete

## What Was Built

Full CRUD REST API for products (SKU/variants) at `/api/products`. SKU is auto-generated from ProductGroup data + `skuAttribute` during create and is immutable after. All data is scoped to `store_id` via `X-Store-Id` header.

## Commits

| Hash | Message |
|------|---------|
| `6da8385` | feat(inventory): add Product CRUD with SKU generation |

## Endpoints

| Method | Path | Status | Notes |
|--------|------|--------|-------|
| GET | `/api/products` | 200 | Paginated, scoped by storeId |
| GET | `/api/products/{id}` | 200 | Single product, scoped by storeId |
| POST | `/api/products` | 201 | Create + auto-generate SKU |
| PUT | `/api/products/{id}` | 200 | Update prices/attributes/minStockLevel |
| DELETE | `/api/products/{id}` | 204 | Delete product |

## Files Created (7)

| File | Purpose |
|------|---------|
| `dto/CreateProductRequest.java` | `productGroupId`, `skuAttribute`, `attributes`, 3 prices, optional `stockQuantity` |
| `dto/UpdateProductRequest.java` | `attributes`, 3 prices, optional `minStockLevel` (SKU + group immutable) |
| `dto/ProductResponse.java` | Full DTO with `from(ProductModel)` static factory |
| `repository/ProductRepository.java` | `JpaRepository` with `findByStoreModelId` and `findByIdAndStoreModelId` |
| `service/ProductService.java` | Interface: findAll, findById, create, update, delete |
| `service/impl/ProductServiceImpl.java` | Core logic — see below |
| `controller/ProductController.java` | REST controller matching `ProductGroupController` pattern |

## Files Modified (1)

| File | Change |
|------|--------|
| `entity/ProductModel.java` | Added `updateFields(attributes, basePrice, labelPrice, floorPrice, minStockLevel)` |

## Key Implementation Details

### ProductServiceImpl

**Dependencies:** `ProductRepository`, `ProductGroupRepository`, `SkuGeneratorService`, `EntityManager`

**Create flow:**
1. Load ProductGroup → 404 if not found
2. Validate price hierarchy: `basePrice ≤ floorPrice ≤ labelPrice` → 422
3. SKU fallback: brand/category null → use `group.getName()`
4. Call `skuGeneratorService.generate(brand, category, skuAttribute)`
5. Get `storeId` from `StoreContext.STORE_ID.get()`, resolve `StoreModel` ref via `EntityManager.getReference()`
6. Build `ProductModel` via `@SuperBuilder` (attributes null→emptyMap, stockQuantity null→0)
7. Save → duplicate SKU thrown as `DataIntegrityViolationException` → 409

**Update:**
- Only `attributes`, `basePrice`, `labelPrice`, `floorPrice`, `minStockLevel` can change
- SKU and `productGroupId` are immutable (no setter, no update logic)
- Price validation re-run on every update

**Delete:**
- Scoped to storeId, throws 404 if product not in this store

### Store Scoping
- Every method calls `StoreContext.assertBound()` at entry
- `findById` uses `findByIdAndStoreModelId` — prevents cross-store ID guessing
- `findAll` uses `findByStoreModelId` — only returns current store's products

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Separate Create/Update DTOs | Yes | Different fields per operation |
| SKU immutable after create | Yes | SKU appears in historical transactions |
| Brand/category fallback | `group.getName()` | Avoid 422 for groups without brand/category |
| `stockQuantity` default | 0 | Support products with existing stock |
| `attributes` null → emptyMap | Yes | JSONB requires valid JSON, not null |
| Duplicate SKU handling | Generic `DATA_CONFLICT` (409) | SKU collision near-impossible with sequence |

## Test Coverage

| Test Class | Tests | Type |
|-----------|-------|------|
| `ProductServiceTest` | 18 | Mockito unit tests with ScopedValue binding |
| `ProductControllerTest` | 25 | `@WebMvcTest` + `MockMvcTester` |

### Service Tests Cover:
- findAll: storeId scoping, entity-to-DTO mapping
- findById: found (200), not found (404)
- create: with brand, null brand fallback, null category fallback, group not found, baseGtFloor, floorGtLabel, stockQuantity from request, null stockQuantity→0, null attributes→emptyMap
- update: allowed fields updated, SKU unchanged, not found, invalid price hierarchy
- delete: found (204), not found (404)

### Controller Tests Cover:
- findAll: valid header (200), paged content, missing header (400)
- findById: found (200), not found (404), missing header (400), non-numeric id (400)
- create: valid (201), delegation, group not found (404), invalid prices (422), missing header (400), blank skuAttribute (400), null productGroupId (400), malformed JSON (400)
- update: valid (200), not found (404), invalid prices (422), null prices (400), missing header (400), non-numeric id (400)
- delete: found (204), not found (404), missing header (400), non-numeric id (400)

## Design Spec

`docs/superpowers/specs/2026-06-07-product-crud-sku-design.md`
