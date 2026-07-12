# E-Commerce Order Management System (OMS)

## Overview
This is a Spring Boot based E-Commerce Order Management System (OMS) designed to handle core transactional flows such as inventory management, cart operations, order checkout processing, payment integration, and post-purchase activities like returns. It is constructed as a multi-phase project, starting with foundational scaffolding and domain entities.

## Tech Stack
- **Java**: Version 17+ (e.g. Java 23)
- **Framework**: Spring Boot 3.3.x (Web, JPA)
- **Database**: MySQL (Development/Production), H2 (In-memory, Testing)
- **Schema Management**: Flyway Migrations
- **Utilities**: Lombok, Springdoc-OpenAPI (Swagger UI)
- **Build Tool**: Maven

## Getting Started

### Local MySQL Setup
To run MySQL locally, you can use Docker:
```bash
docker run --name oms-mysql -e MYSQL_DATABASE=oms_db -e MYSQL_ROOT_PASSWORD=password -p 3306:3306 -d mysql:8.0
```

### Environment Variables
The application uses the following environment variables (with default values for development):
- `DB_URL`: JDBC database URL (default: `jdbc:mysql://localhost:3306/oms_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true`)
- `DB_USERNAME`: Database username (default: `root`)
- `DB_PASSWORD`: Database password (default: `password`)

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
- **Primary Database Engine**: MySQL is selected as the primary relational database.
- **ID Generation Strategy**: UUID is used for all primary keys. Since MySQL does not support a native `UUID` type, they are stored as `VARCHAR(36)` strings. This is highly portable, human-readable, and compatible with both MySQL and H2 databases.
- **Concurrency Locking Strategy**: Leaning towards **Optimistic Locking** on `InventoryItem` using a `@Version` field (`version` column) to support high-throughput checkout updates while avoiding database deadlocks associated with pessimistic locking.
- **AuditLog Metadata**: Stored as a `TEXT` data type containing a serialized JSON string to maximize database compatibility between MySQL and H2 without relying on vendor-specific JSONB dialect configurations.
- **SQL Keyword Resolution**: Renamed the `value` column in the `discounts` table to `discount_value` on the database level, but mapped it to the Java field `value` to avoid parser errors on the reserved SQL keyword `value`.
