# E-Commerce Order Management System (OMS)

## Overview
This is a Spring Boot based E-Commerce Order Management System (OMS) designed to handle core transactional flows such as inventory management, cart operations, order checkout processing, payment integration, and post-purchase activities like returns. It is constructed as a multi-phase project, starting with foundational scaffolding and domain entities.

## Tech Stack
- **Java**: Version 17+ (e.g. Java 23)
- **Framework**: Spring Boot 3.3.x (Web, JPA)
- **Database**: PostgreSQL (Development/Production), H2 (In-memory, Testing)
- **Schema Management**: Flyway Migrations
- **Utilities**: Lombok, Springdoc-OpenAPI (Swagger UI)
- **Build Tool**: Maven

## Getting Started

### Local PostgreSQL Setup
To run PostgreSQL locally, you can use Docker:
```bash
docker run --name oms-postgres -e POSTGRES_DB=oms_db -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15
```

### Environment Variables
The application uses the following environment variables (with default values for development):
- `DB_URL`: JDBC database URL (default: `jdbc:postgresql://localhost:5432/oms_db`)
- `DB_USERNAME`: Database username (default: `postgres`)
- `DB_PASSWORD`: Database password (default: `postgres`)

### Running the Application
To run the application with the development profile (default):
```bash
./mvnw spring-boot:run
```

### Running Tests
To run tests using the in-memory H2 database under the `test` profile:
```bash
./mvnw test
```

## Phased Roadmap
- **Phase 1**: Project Scaffold, Configuration, and Base Domain Model (Completed)
- **Phase 2**: Authentication & Role-Based Access Control (RBAC) (Pending)
- **Phase 3**: Catalog & Product Information Management (Pending)
- **Phase 4**: Inventory & Warehouse Management (Pending)
- **Phase 5**: Cart Operations & Price Calculation (Pending)
- **Phase 6**: Order Placement & Checkout Transaction (Pending)
- **Phase 7**: State Machine for Order Lifecycle (Pending)
- **Phase 8**: Returns Processing & Refunds (Pending)
- **Phase 9**: Payment Integration & Audit Logs (Pending)
- **Phase 10**: OpenAPI Documentation & End-to-End Tests (Pending)
- **Phase 11**: Production Deployment & Optimization (Pending)

## Assumptions Log (Phase 1)
- **ID Generation Strategy**: UUID is used for all primary keys to guarantee distributed system capability and prevent ID enumeration attacks (e.g., exposing total number of orders/users).
- **Concurrency Locking Strategy**: Leaning towards **Optimistic Locking** on `InventoryItem` using a `@Version` field (`version` column) to support high-throughput checkout updates while avoiding database deadlocks associated with pessimistic locking.
- **AuditLog Metadata**: Stored as a `TEXT` data type containing a serialized JSON string to maximize database compatibility between PostgreSQL and H2 without relying on vendor-specific JSONB dialect configurations.
- **SQL Keyword Resolution**: Renamed the `value` column in the `discounts` table to `discount_value` on the database level, but mapped it to the Java field `value` to avoid H2 parser errors on the reserved SQL keyword `value`.
