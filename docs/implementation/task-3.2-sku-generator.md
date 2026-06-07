# Implementation: SKU Generator (Task 3.2)

**Branch:** `develop/task-3.2-sku-generator`
**Base:** `origin/main`
**Date:** 2026-06-07
**Status:** Complete

## What Was Built

Internal utility service that generates product SKUs in format `[BRAND]-[CAT]-[ATTR]-[SEQ]`.

## Commits

| Hash | Message |
|------|---------|
| `e866c5d` | feat(inventory): add SKU generator service with unit tests |
| `e632d0a` | fix: address code review findings from REVIEW.md |

## Files Created

| File | Purpose |
|------|---------|
| `repository/SkuRepository.java` | `@Repository` class using `JdbcClient` — calls `SELECT get_next_sku_seq()` |
| `service/SkuGeneratorService.java` | Interface: `String generate(brand, category, attribute)` |
| `service/impl/SkuGeneratorServiceImpl.java` | Normalization (trim, uppercase, whitespace→dash), null/blank rejection |
| `SkuGeneratorServiceTest.java` | 14 unit tests — Mockito, no DB needed |

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Interface + impl | Yes | Consistency with `ProductGroupService` |
| Null/blank handling | Throw `IllegalArgumentException` | Fail fast |
| Repository type | `JdbcClient` class, not `JpaRepository` | Avoids bean conflict with future `ProductRepository` |
| No controller | Internal utility only | Consumed by `ProductServiceImpl` in Task 3.3 |

## Post-Review Fixes

After code review (REVIEW.md), the following were fixed on the same branch:
- **BUG-1:** `normalizeBrand()` now calls `.trim()` before duplicate check
- **BUG-2:** `replace(' ', '-')` → `replaceAll("\\s+", "-")` for Unicode whitespace
- **ARCHITECTURE-1:** Switched `SkuRepository` from `JpaRepository<ProductModel>` to `JdbcClient`
- Added 2 regression tests

## Test Coverage

14 tests covering: happy path, lowercase normalization, space replacement, leading/trailing whitespace trim, boundary sequence values (001, 999), null rejection (brand/category/attribute), blank rejection, repository delegation.

## Design Spec

`docs/superpowers/specs/2026-06-07-sku-generator-design.md`
