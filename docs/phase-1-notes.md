# Phase 1 Developer Notes (MySQL Migration)

## Design Decisions and Trade-offs

### 1. Database Selection: MySQL vs. PostgreSQL
- **Decision**: MySQL is selected as the primary relational database. H2 database is used in `MySQL` compatibility mode for testing.
- **Rationale**: Replaces the initial PostgreSQL proposal to align with project constraints.

### 2. Primary Key Strategy: UUID stored as VARCHAR(36)
- **Decision**: UUID (`java.util.UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`) is used for all entity primary keys, stored as `VARCHAR(36)` in the schema.
- **Rationale**:
  - **Security**: Prevents ID enumeration attacks. For instance, customer cart IDs or order numbers are not guessable, preventing information leakage.
  - **Compatibility**: MySQL does not have a native built-in `UUID` column type. Storing it as `VARCHAR(36)` is fully standard, portable, and runs flawlessly on both MySQL and H2 databases.
  - **JPA mapping**: Configured `@Column(columnDefinition = "VARCHAR(36)")` on all UUID fields to prevent Hibernate schema validation errors during bootstrap.
- **Trade-offs**:
  - Higher storage overhead (36 bytes vs 16 bytes for binary, or 8 bytes for integers).
  - Indexing performance in MySQL is slightly slower than sequential integers due to random insertions. We mitigate this in the database through appropriate indexing strategies during later query phases.

### 3. Explicit Foreign Keys
- **Decision**: All foreign keys are explicitly declared at the table-level using `CONSTRAINT ... FOREIGN KEY`.
- **Rationale**: MySQL parses but ignores inline `REFERENCES` constraints. Enforcing foreign key constraints in MySQL requires table-level constraint declarations.

### 4. Lock Strategy: Optimistic Locking on InventoryItem
- **Decision**: Optimistic locking via a `@Version` field of type `Long` (`version` column).
- **Rationale**:
  - In a standard e-commerce platform, inventory checks and updates occur frequently. Pessimistic locking (DB-level `SELECT FOR UPDATE`) holds database locks longer and could bottleneck the system during high-traffic sales.
  - Optimistic locking ensures that write conflicts are handled gracefully in the application tier (retries or failure warnings) instead of hanging connection pools.
- **Trade-offs**:
  - High concurrency on a single hot product's inventory will result in `ObjectOptimisticLockingFailureException`. A retry mechanism (with jitter) will be required in later service layer implementations to handle this smoothly.

### 5. Database Portability and Keyword Resolution
- **Decision**: The `discounts` table column for value was renamed to `discount_value` in the database migration SQL, but remains mapped to the Java field `value`.
- **Rationale**:
  - Using the term `value` in standard SQL causes syntax conflicts in H2's parser due to `VALUE` being a reserved word. 
  - Using a prefix `discount_value` keeps the SQL clean and standard.
- **Decision**: `AuditLog.metadata` is stored as a `TEXT` type.
- **Rationale**:
  - To keep the baseline schema simple, compatible, and clean for unit tests, we utilize standard `TEXT` and will parse the JSON in Java using Jackson.
