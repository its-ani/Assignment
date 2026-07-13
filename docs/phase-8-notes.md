# Phase 8 Dev Notes: Order Lifecycle & Fulfillment Status Management

This phase implements strict order status progression, customer self-service cancellation, role-based order visibility, and async order status-change event processing.

## 1. Transition Map Design Rationale
Instead of scattered `if/else` logic throughout the code, we defined a static mapping:
```java
private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
        OrderStatus.PLACED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
        OrderStatus.CONFIRMED, Set.of(OrderStatus.PACKED, OrderStatus.CANCELLED),
        OrderStatus.PACKED, Set.of(OrderStatus.SHIPPED),
        OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED, Set.of(),
        OrderStatus.CANCELLED, Set.of(),
        OrderStatus.RETURNED, Set.of()
);
```
### Rationale:
- **Maintainability**: The entire state machine of the order lifecycle is visible in a single place. If a new state transition is introduced, it only needs to be added to this map.
- **Strict Validation**: Standard state updates validate current and target states directly against the map. Invalid transitions fail early with a clean `InvalidOrderStatusTransitionException` (mapped to `409 Conflict`).
- **Role Isolation**: 
  - `WAREHOUSE_STAFF` are restricted to the forward happy path (`PLACED -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`) and cannot perform cancellations.
  - `CUSTOMER` can cancel their own order only while in the pre-packing phase (`PLACED` or `CONFIRMED`).
  - `ADMIN` inherits full progression rights, along with the ability to perform a force-cancel at any stage before the order is delivered or returned.

## 2. Async Testing Gotchas
### Database Pollution and Foreign Key Constraint Errors:
Integration tests run sequentially on a shared database state (H2 test database).
- **The Issue**: Our order lifecycle integration tests create orders and order items referencing user accounts (the customer, warehouse staff, admin). 
- **The Consequence**: If the database is not properly cleaned up *after* each test in the test class, when subsequent integration tests from other test classes run their setups (e.g., `userRepository.deleteAll()`), they encounter foreign key violations because historical orders in our tests still reference those users.
- **The Resolution**: We implemented a complete teardown sequence calling `.deleteAll()` on all participating repositories (including `Payment`, `Order`, `OrderItem`, `Cart`, `CartItem`, `AuditLog`, `InventoryItem`, `Warehouse`, `Product`, `Category`, `User`) in an `@AfterEach` block. This guarantees a clean database state for all test suites.

### Async Audit Log Verification:
The async listener `@Async` runs in a background thread task executor. To verify that audit logs are written correctly:
- We implemented a polling loop in integration tests that waits for the `AuditLog` entry to appear, checking every 100 milliseconds for up to 3 seconds. This prevents race conditions where the test assert executes before the async worker commits the log record.
