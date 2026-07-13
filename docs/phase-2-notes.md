# Phase 2 Developer Notes: Authentication, Authorization (RBAC), and JWT

This document details the decisions, technical implementations, and integration patterns introduced during Phase 2 of the E-Commerce Order Management System.

## Technical Decisions

### 1. JWT Library Selection: io.jsonwebtoken (JJWT)
- **Choice**: We chose JJWT version `0.12.6` over alternative libraries like `java-jwt` (Auth0).
- **Rationale**: 
  - JJWT `0.12` introduces a fluent builder API that completely deprecates the old patterns used in version `0.9.x`. It separates parsing configuration from token parsing, improving thread safety and reducing configuration overhead.
  - JJWT has built-in integration with Jackson (`jjwt-jackson`), making payload mapping automatic.
  - Keys are safely instantiated via `Keys.hmacShaKeyFor()`, complying with the JWA (JSON Web Algorithms) standard requiring at least a 256-bit key for HMAC-SHA256.

### 2. Privilege Escalation Mitigation & RBAC
- **Scoping Requirement**: Clients registering via the public registration endpoint (`/api/v1/auth/register`) must not be allowed to self-assign advanced privileges (such as `ADMIN` or `WAREHOUSE_STAFF`).
- **Implementation**: 
  - The `AuthService.register()` method explicitly sets the role to `UserRole.CUSTOMER` when processing public registrations, ignoring any `role` value provided in the incoming `RegisterRequest` DTO.
  - Creating `ADMIN` or `WAREHOUSE_STAFF` users must go through the protected `POST /api/v1/auth/admin/register` endpoint. This endpoint is secured using Spring Method Security via `@PreAuthorize("hasRole('ADMIN')")`.
  - Since this method security is enforced at runtime on the controller, any CUSTOMER who tries to call it receives a `403 Forbidden` response.

### 3. Bootstrap Seed Mechanism
- **Scoping Requirement**: Since the endpoint to register admin users is itself admin-only, we need a way to create the initial admin user.
- **Implementation**:
  - We implemented a startup `DatabaseInitializer` implementing `CommandLineRunner`.
  - On application startup, it queries the count of users in the database. If the table is empty (e.g., initial deployment), it seeds a default administrator:
    - **Email**: `admin@ecommerce.com`
    - **Password**: `AdminPass123!` (BCrypt encoded)
    - **Role**: `ADMIN`
  - This allows developers and systems to log in immediately and use the returned token to register other administrators or warehouse staff.

## Database & Testing Gotchas

### H2 MySQL Mode Compatibility
- The project is configured with MySQL locally but uses an in-memory H2 database under the `test` profile (`spring.profiles.active: test`).
- We configured the H2 test datasource as: `jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE`.
- H2 fully supports the `VARCHAR(36)` type used for our UUID primary keys.
- Flyway migrations run successfully against H2 without dialect issues. The Flyway migration file `V1__init_schema.sql` compiles cleanly in both databases since standard MySQL syntax was used (avoiding Postgres-only types like `JSONB`, utilizing `TEXT` for the audit log metadata columns).
