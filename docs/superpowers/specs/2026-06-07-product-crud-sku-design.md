# Design: Product CRUD + SKU Generation

**Date:** 2026-06-07  
**Task:** 3.3  
**Branch:** `develop/task-3.3-product-crud`

## Overview

Internal CRUD API untuk produk (SKU/variant). Diakses oleh modul POS di task-task selanjutnya. SKU di-generate otomatis dari data ProductGroup + `skuAttribute` saat create, dan immutable setelahnya. Semua data produk scoped ke `store_id` dari header `X-Store-Id` via Java Scoped Values.

---

## Endpoints

| Method | Path | Status | Keterangan |
|--------|------|--------|------------|
| GET | `/api/products` | 200 | Paginated list, scoped by storeId |
| GET | `/api/products/{id}` | 200 | Single product by id |
| POST | `/api/products` | 201 | Create product + generate SKU |
| PUT | `/api/products/{id}` | 200 | Update prices, attributes, minStockLevel |
| DELETE | `/api/products/{id}` | 204 | Delete product |

---

## Data Flow

### Create

```
POST /api/products
  1. Controller: @Valid on CreateProductRequest
     → invalid fields → 400 VALIDATION_FAILED

  2. Service: load ProductGroup by productGroupId
     → not found → EntityNotFoundException → 404

  3. Service: validate price hierarchy
     → basePrice ≤ floorPrice ≤ labelPrice
     → violation → IllegalArgumentException → 422

  4. Service: resolve SKU parameters
     brand    = group.getBrand()    != null ? group.getBrand()    : group.getName()
     category = group.getCategory() != null ? group.getCategory() : group.getName()

  5. Service: sku = skuGeneratorService.generate(brand, category, request.skuAttribute())
     → skuAttribute blank after normalize → IllegalArgumentException → 422

  6. Service: Integer storeId = StoreContext.STORE_ID.get()
     StoreModel storeRef = entityManager.getReference(StoreModel.class, storeId)

  7. Service: build ProductModel via @SuperBuilder
     stockQuantity = request.stockQuantity() != null ? request.stockQuantity() : 0
     (stockQuantity adalah @Setter(AccessLevel.PACKAGE) — builder dapat men-set-nya)

  8. Service: productRepository.save(product)
     → duplicate SKU (DataIntegrityViolationException) → 409 DATA_CONFLICT

  9. Return 201 Created + ProductResponse
```

### Update

Hanya `attributes`, `basePrice`, `labelPrice`, `floorPrice`, `minStockLevel` yang bisa diubah. SKU dan `productGroupId` immutable.

```
PUT /api/products/{id}
  1. @Valid on UpdateProductRequest
  2. Service: findById(id) → 404 jika tidak ada
  3. Service: validate price hierarchy → 422
  4. entity.updateFields(attributes, basePrice, labelPrice, floorPrice, minStockLevel)
  5. productRepository.save(entity)
  6. Return 200 + ProductResponse
```

### Delete

```
DELETE /api/products/{id}
  1. Service: findById(id) → 404 jika tidak ada
  2. productRepository.delete(entity)
  3. Return 204 No Content
```

---

## DTOs

### CreateProductRequest

```java
public record CreateProductRequest(
        @NotNull Long productGroupId,
        @NotBlank @Size(max = 50) String skuAttribute,
        Map<String, Object> attributes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal labelPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal floorPrice,
        @Min(0) Integer stockQuantity
)
```

`attributes` nullable — jika null, disimpan sebagai empty map `{}`.  
`stockQuantity` optional — default ke 0 jika null.

### UpdateProductRequest

```java
public record UpdateProductRequest(
        Map<String, Object> attributes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal labelPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal floorPrice,
        @Min(0) Integer minStockLevel
)
```

### ProductResponse

```java
public record ProductResponse(
        Long id,
        Long productGroupId,
        String sku,
        Map<String, Object> attributes,
        BigDecimal basePrice,
        BigDecimal labelPrice,
        BigDecimal floorPrice,
        Integer stockQuantity,
        Integer minStockLevel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(ProductModel model) { ... }
}
```

---

## Components

### ProductRepository

```java
public interface ProductRepository extends JpaRepository<ProductModel, Long> {
    Page<ProductModel> findByStoreModelId(Integer storeId, Pageable pageable);
    Optional<ProductModel> findByIdAndStoreModelId(Long id, Integer storeId);
}
```

`STORE_ID` dari `StoreContext` bertipe `Integer`. `findByIdAndStoreModelId` memastikan `findById` dan `findAll` hanya mengakses produk milik store yang sama (mencegah akses lintas-toko via ID guessing).

### ProductService (interface)

```java
public interface ProductService {
    Page<ProductResponse> findAll(Pageable pageable);
    ProductResponse findById(Long id);
    ProductResponse create(CreateProductRequest request);
    ProductResponse update(Long id, UpdateProductRequest request);
    void delete(Long id);
}
```

### ProductServiceImpl

Dependencies:
- `ProductRepository`
- `ProductGroupRepository` (untuk load ProductGroup saat create)
- `SkuGeneratorService`
- `EntityManager` (untuk `getReference(StoreModel.class, storeId)` tanpa query)

Business rules di service layer:
- Panggil `StoreContext.assertBound()` di awal setiap method yang butuh storeId
- `Integer storeId = StoreContext.STORE_ID.get()`
- Price hierarchy validation: `basePrice ≤ floorPrice ≤ labelPrice`
- Brand/category fallback ke `group.getName()` jika field null
- `attributes` null → simpan sebagai `Collections.emptyMap()`
- `stockQuantity` null → simpan sebagai `0`
- `findById` dan `findAll` menggunakan storeId untuk scope (anti lintas-toko)

### ProductController

Mengikuti persis pola `ProductGroupController`:
- `@RequestMapping("${app.api-path-prefix}products")`
- Constructor injection
- `@PageableDefault(size = 20, sort = "sku", direction = ASC)` untuk findAll

### ProductModel — perubahan

Tambahkan satu method ke entity yang sudah ada:

```java
public void updateFields(Map<String, Object> attributes,
                          BigDecimal basePrice, BigDecimal labelPrice, BigDecimal floorPrice,
                          Integer minStockLevel) {
    this.attributes = attributes != null ? attributes : Collections.emptyMap();
    this.basePrice = basePrice;
    this.labelPrice = labelPrice;
    this.floorPrice = floorPrice;
    if (minStockLevel != null) this.minStockLevel = minStockLevel;
}
```

---

## Error Handling

Semua skenario ditangani oleh `GlobalExceptionHandler` yang sudah ada — tidak perlu exception class baru.

| Skenario | Exception | HTTP | Code |
|----------|-----------|------|------|
| ProductGroup tidak ditemukan | `EntityNotFoundException` | 404 | `ENTITY_NOT_FOUND` |
| Product tidak ditemukan | `EntityNotFoundException` | 404 | `ENTITY_NOT_FOUND` |
| Price hierarchy invalid | `IllegalArgumentException` | 422 | `INVALID_INPUT` |
| skuAttribute blank setelah normalize | `IllegalArgumentException` | 422 | `INVALID_INPUT` |
| Duplicate SKU (edge case) | `DataIntegrityViolationException` | 409 | `DATA_CONFLICT` |
| Validasi DTO gagal | `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| Malformed JSON | `HttpMessageNotReadableException` | 400 | `INVALID_INPUT` |
| Path param non-numerik | `MethodArgumentTypeMismatchException` | 400 | `INVALID_INPUT` |

---

## Files to Create

| Path | Keterangan |
|------|------------|
| `modules/inventory/dto/CreateProductRequest.java` | Request DTO create |
| `modules/inventory/dto/UpdateProductRequest.java` | Request DTO update |
| `modules/inventory/dto/ProductResponse.java` | Response DTO + from() |
| `modules/inventory/repository/ProductRepository.java` | JpaRepository |
| `modules/inventory/service/ProductService.java` | Interface |
| `modules/inventory/service/impl/ProductServiceImpl.java` | Implementation |
| `modules/inventory/controller/ProductController.java` | REST controller |

## Files to Modify

| Path | Perubahan |
|------|-----------|
| `modules/inventory/entity/ProductModel.java` | Tambah `updateFields()` |

---

## Tests

### ProductServiceTest (Mockito, @ExtendWith(MockitoExtension.class))

**Create:**
- `create_validRequest_withBrand_generatesSku` — happy path, brand dari group
- `create_validRequest_nullBrand_usesGroupNameFallback` — brand null → pakai group.name
- `create_validRequest_nullCategory_usesGroupNameFallback` — category null → pakai group.name
- `create_groupNotFound_throwsEntityNotFoundException`
- `create_baseGtFloor_throwsIllegalArgumentException`
- `create_floorGtLabel_throwsIllegalArgumentException`
- `create_setsStockQuantityFromRequest`
- `create_nullStockQuantity_defaultsToZero`
- `create_nullAttributes_savesEmptyMap`

**Update:**
- `update_found_updatesAllowedFields`
- `update_found_skuUnchanged` — SKU tidak berubah setelah update
- `update_notFound_throwsEntityNotFoundException`
- `update_invalidPriceHierarchy_throwsIllegalArgumentException`

**Delete:**
- `delete_found_callsDelete`
- `delete_notFound_throwsEntityNotFoundException`

**FindAll:**
- `findAll_usesStoreIdFromScopedValues`
- `findAll_mapsEntitiesToResponse`

### ProductControllerTest (WebMvcTest + MockMvcTester)

- `create_validRequest_returns201WithBody`
- `create_groupNotFound_returns404`
- `create_invalidPrices_returns422`
- `create_missingStoreId_returns400`
- `create_blankSkuAttribute_returns400`
- `update_validRequest_returns200`
- `update_notFound_returns404`
- `update_invalidPrices_returns422`
- `delete_found_returns204`
- `delete_notFound_returns404`
- `findById_found_returns200`
- `findAll_returns200WithPage`

---

## Design Decisions

| Keputusan | Pilihan | Alasan |
|-----------|---------|--------|
| SKU immutable | Ya | SKU muncul di transaksi historis; regenerasi akan create orphan |
| Separate Create/Update DTO | Ya | Field berbeda (create punya productGroupId+skuAttribute, update tidak) |
| Brand/category fallback | Pakai group.name | Hindari 422 untuk ProductGroup tanpa brand/category |
| stockQuantity di create | Optional, default 0 | Mendukung input produk yang sudah ada stoknya |
| Stok update via create saja | Ya | Deduction stok dikelola via dedicated stock-in/out operations |
| Duplicate SKU handler | Generic DATA_CONFLICT | SKU auto-generated, collision mustahil kecuali bug sequence |

## Out of Scope

- Stock deduction / stock-in operations — Task terpisah
- Filter by productGroupId atau SKU search — bisa ditambah nanti
- `@DataJpaTest` untuk repository — defer ke Phase 7
