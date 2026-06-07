# Design: PUT /product-groups/{id} — Product Group Update

**Date:** 2026-06-07
**Branch:** develop/task-3.1-product-group-crud

## Summary

Add a full-replacement update endpoint for product groups. The client sends all fields; the server replaces the entity. Duplicate name+brand validation runs the same way as create, but excludes the record being updated.

## Endpoint

```
PUT /api/product-groups/{id}
Content-Type: application/json
X-Store-Id: {storeId}

Body: UpdateProductGroupRequest
Response: 200 OK — ProductGroupResponse
```

## Components Changed

| File | Change |
|---|---|
| `ProductGroupModel` | Add `updateFields(name, brand, category, description)` method to encapsulate mutation — avoids blanket `@Setter` |
| `ProductGroupRepository` | Add `existsByNameAndBrandAndIdNot(name, brand, id)` and `existsByNameAndBrandIsNullAndIdNot(name, id)` derived queries |
| `ProductGroupService` | Change `update` signature to accept `UpdateProductGroupRequest` (was using `CreateProductGroupRequest`) |
| `ProductGroupServiceImpl` | Implement `update()`: find → duplicate check excluding self → mutate → save |
| `ProductGroupController` | Add `@PutMapping("/{id}")` returning `200 OK` with `ProductGroupResponse` |
| `ProductGroupServiceTest` | Add update service-layer test cases |
| `ProductGroupControllerTest` | Add update controller-layer test cases |

## Data Flow

```
PUT /api/product-groups/{id}

1. Controller: @Valid on UpdateProductGroupRequest
   → blank name or field too long → 400 VALIDATION_FAILED

2. Service: findById(id)
   → not found → EntityNotFoundException → 404 ENTITY_NOT_FOUND

3. Service: duplicate check (excluding current id)
   brand != null → existsByNameAndBrandAndIdNot(name, brand, id)
   brand == null → existsByNameAndBrandIsNullAndIdNot(name, id)
   → duplicate found → DuplicateEntityException → 409 DUPLICATE_ENTITY

4. entity.updateFields(name, brand, category, description)

5. repository.save(entity)
   → updated_at maintained by PostgreSQL trigger

6. return ProductGroupResponse → 200 OK
```

## DTOs

**`UpdateProductGroupRequest`** (already exists):
```java
record UpdateProductGroupRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String brand,
    @Size(max = 50) String category,
    @Size(max = 2000) String description
)
```

**Response:** reuses `ProductGroupResponse` (id, name, brand, category, description, createdAt, updatedAt).

## Error Handling

| Scenario | Exception | HTTP |
|---|---|---|
| Non-numeric `{id}` path variable | `MethodArgumentTypeMismatchException` | 400 |
| Missing `X-Store-Id` header | — | 400 MISSING_STORE_ID |
| Blank or invalid fields | `MethodArgumentNotValidException` | 400 VALIDATION_FAILED |
| ID not found | `EntityNotFoundException` | 404 ENTITY_NOT_FOUND |
| Duplicate name+brand | `DuplicateEntityException` | 409 DUPLICATE_ENTITY |

All exceptions are handled by the existing `GlobalExceptionHandler`.

## Tests

### Service layer (`ProductGroupServiceTest`)

- `update_found_withBrand_returnsUpdatedResponse` — happy path with brand
- `update_found_withNullBrand_returnsUpdatedResponse` — happy path with null brand
- `update_notFound_throwsEntityNotFoundException`
- `update_duplicateNameAndBrand_throwsDuplicateEntityException`
- `update_duplicateNameWithNullBrand_throwsDuplicateEntityException`
- `update_persistsCorrectFields` — `ArgumentCaptor` verifies mutated entity is saved

### Controller layer (`ProductGroupControllerTest`)

- `update_validRequest_returns200WithBody`
- `update_notFound_returns404WithCode`
- `update_duplicate_returns409WithCode`
- `update_blankName_returns400WithValidationDetails`
- `update_missingStoreIdHeader_returns400`
- `update_nonNumericId_returns400`

### Side effect: also add to `findById` and `delete`

- `findById_nonNumericId_returns400`
- `delete_nonNumericId_returns400`

These are missing from the existing test files and follow the same `MethodArgumentTypeMismatchException` path.
