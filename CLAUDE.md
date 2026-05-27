# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Widuri ERP** — an ERP system for a fishing tackle retail store (*Toko Pancing*). Built with Spring Boot 4, Java 25, PostgreSQL 18, and a planned Vue JS 3 frontend. The backend is a **modular monolith** with four bounded-context modules: `inventory`, `sales`, `finance`, and `integration`.

## Commands

```bash
# Start infrastructure (PostgreSQL 18 + pgAdmin)
docker compose up -d

# Build and run (Spring Boot auto-starts Docker Compose in dev mode)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw package -DskipTests

# Run all tests (requires running PostgreSQL)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ErpApplicationTests

# Run Flyway migrations manually
./mvnw flyway:migrate
```

pgAdmin is accessible at `http://localhost:5050` (credentials in `.env`). The app runs on port 8080.

## Architecture

### Package Structure

Root package: `id.my.mfikriproject.widuri.erp`

Planned module layout under `src/main/java/.../erp/`:
```
core/           # Shared types (e.g., ErrorResponse)
modules/
  inventory/    # Products, variants, stock
  sales/        # POS, transactions
  finance/      # HPP, profit/loss reports
  integration/  # E-commerce sync (Shopee/Tokopedia)
```

**Module isolation rule:** A module must never query another module's tables directly — it must go through the other module's public `Service` methods.

### Database Schema

Table naming conventions:
- `m_` prefix — master/reference data (`m_store`, `m_product_group`, `m_product`)
- `t_` prefix — transaction records (`t_sales`, `t_sales_detail`)
- `sys_` prefix — system utility (`sys_audit_log`)

Key design decisions:
- **`m_product.attributes`** is `JSONB` — product variant attributes (color, size, etc.) are schema-flexible. Use `@JdbcTypeCode(SqlTypes.JSON)` in the JPA entity.
- **Three prices per SKU**: `base_price` (cost/HPP), `label_price` (displayed price), `floor_price` (minimum negotiated price). Sales validation must enforce `sold_price >= floor_price`.
- **Transaction records are immutable** — `t_sales` and `t_sales_detail` have no `updated_at` column.
- **`sys_audit_log` is append-only** — uses a BRIN index on `changed_at` for performance.
- `updated_at` on master tables is maintained by a PostgreSQL trigger (`update_updated_at_column()`), not the application layer.
- SKU generation uses PostgreSQL sequence `sku_sequence` via `get_next_sku_seq()` native query. Pattern: `[BRAND]-[CAT]-[ATTR]-[SEQ]`.

### Key Backend Patterns

- **Virtual Threads**: `spring.threads.virtual.enabled=true` is mandatory — do not disable.
- **Flyway**: Schema is managed entirely by Flyway. Hibernate `ddl-auto` is always `validate`, never `update` or `create`.
- **Pessimistic Locking**: Stock deduction queries on `m_product` must use `@Lock(LockModeType.PESSIMISTIC_WRITE)` to prevent race conditions at the POS.
- **Scoped Values (Java 25)**: `store_id` is propagated via Java Scoped Values (not ThreadLocal) captured from an `X-Store-Id` HTTP header — avoids polluting method signatures across the call stack.
- **Spring RestClient**: Use `RestClient` (not `RestTemplate` or `WebClient`) for any outbound HTTP calls.

### Profiles

- `dev` (default): SQL logging enabled, Flyway `baseline-on-migrate: true`, datasource auto-configured by `spring-boot-docker-compose`.
- `prod`: Datasource injected from env vars `DB_URL`, `DB_USER`, `DB_PASSWORD`.

### Infrastructure

`compose.yaml` defines PostgreSQL 18 and pgAdmin. Credentials are sourced from `.env` (not committed to production). Spring Boot's `spring-boot-docker-compose` dependency auto-manages container lifecycle when running locally.
