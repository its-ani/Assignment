# Phase 4 Developer Notes: Warehouse & Inventory Management

## Implementation Details

- **quantityReserved is 0**: We have implemented the database schema and response fields to return `quantityReserved`. However, in this phase, it defaults to `0` and is not modified by any direct admin stock adjustments. This is intentional groundwork for Phase 6 (Checkout flow) where locking and reserved quantities will be managed during active checkout transactions. This is not a bug.
- **MySQL CHECK constraints**: Flyway migration `V2__inventory_constraints.sql` adds three table-level CHECK constraints. We target MySQL 8.0.16+ which officially supports check constraints. For testing, H2 also correctly parses and validates check constraints. In addition to DB-level constraints, identical validation logic is executed in the Java service layer to return structured error payloads (`409 Conflict`) instead of database-native constraint violation exceptions.
- **Synchronous Auditing**: While order-driven auditing will be fully async in Phase 7, synchronous audit logging inside the active service transaction is implemented here for direct admin-driven stock updates. This keeps direct manual inventory changes simple, reliable, and transactionally atomic.
