# SKU Generator — Design Doc

**Date:** 2026-06-07
**Task:** 3.2
**Branch:** `develop/task-3.2-sku-generator`

## Overview

Internal utility service that generates product SKUs in the format `[BRAND]-[CAT]-[ATTR]-[SEQ]` (e.g., `SHIMANO-REEL-SILVER-001`). Consumed by `ProductService` in Task 3.3 during product creation.

## Components

### SkuRepository
- Package: `modules.inventory.repository`
- Extends `JpaRepository<ProductModel, Long>` to leverage Spring Data JPA's `@Query` support
- Single native query: `SELECT get_next_sku_seq()` — calls the PostgreSQL function created in `V1__Init_Schema.sql`

### SkuGeneratorService (interface)
- Package: `modules.inventory.service`
- Single method: `String generate(String brand, String category, String attribute)`

### SkuGeneratorServiceImpl
- Package: `modules.inventory.service.impl`
- Constructor injection of `SkuRepository`
- Normalization: `trim()`, `toUpperCase()`, spaces → `-`
- Validates all three inputs are non-null and non-blank → throws `IllegalArgumentException`
- Combines: `NORMALIZED_BRAND-NORMALIZED_CAT-NORMALIZED_ATTR-SEQ`

### SkuGeneratorServiceTest
- Package: `modules.inventory` (test)
- 13 unit tests using Mockito (`@ExtendWith(MockitoExtension.class)`)
- Covers: happy path, normalization (lowercase, spaces, whitespace trim), all three null/blank rejection paths, delegation to repository, boundary sequence values

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Interface + impl | Yes | Consistency with `ProductGroupService` pattern |
| Null/blank handling | Throw `IllegalArgumentException` | Fail fast, caller must provide valid values |
| Test approach | Service unit test with mocked repository | Fast, no DB needed; native query test deferred to Task 7.1 |
| No REST controller | Internal utility only | Consumed programmatically by `ProductService` in Task 3.3 |

## Out of Scope

- REST endpoint — not needed, internal utility
- `@DataJpaTest` — deferred to Phase 7 testing tasks per roadmap
- Duplicate SKU handling — caller (`ProductService`) handles the `uq_m_product_sku` constraint
