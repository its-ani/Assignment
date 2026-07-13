# Phase 6 Development Notes: Concurrency and Transaction Tuning

This document details the engineering decisions, retry tuning, and database connection pool debugging encountered during Phase 6 checkout implementation.

---

## 1. Concurrency Tuning & Retry Configuration

To support resilient checkout requests under high database contention, we used Spring Retry's `@Retryable` annotation.
- **Exceptions Targeted**: `org.springframework.dao.OptimisticLockingFailureException` (specifically caught on JPA version conflicts).
- **Max Attempts**: $N = 5$ attempts.
- **Backoff Strategy**: Exponential backoff starting at 50ms, doubling with each retry, up to a maximum delay of 500ms.
- **contention Limit Handling**: If all 5 retries fail due to version conflicts, a custom `@Recover` handler throws an `InventoryContentionException`, returning a clean `503 Service Unavailable` error to the client instead of leaking DB exceptions.

---

## 2. Surprising Behaviors & Debugging Findings

### A. Hikari Connection Pool Starvation Deadlock
* **The Problem**: During our initial concurrency test execution (firing 10 checkouts concurrently), all 10 threads timed out waiting for a database connection:
  `Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30005ms]`
* **The Root Cause**: 
  - Each checkout request runs in a thread and starts an outer transaction (T1), acquiring a database connection from the pool.
  - Inside T1, the thread invokes `InventoryReservationService.reserveProductStock` which is annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
  - `REQUIRES_NEW` suspends T1 and requests a **second connection** to start transaction T2.
  - Because the Hikari pool was limited to 10 connections, and all 10 were held by the suspended T1 transactions, there were no free connections in the pool for the T2 transactions.
  - This caused a connection deadlock where all threads were suspended waiting for a pool connection that could never be released.
* **The Resolution**: We increased the test profile pool size to 30 in `application-test.yml` (`maximum-pool-size: 30`, `minimum-idle: 10`), satisfying the HikariCP nested transaction formula:
  $$\text{Pool Size} \ge \text{Tn} \times (\text{Tx} - 1) + 1 = 10 \times (2 - 1) + 1 = 11$$
  Setting it to 30 provides plenty of connections, enabling all concurrent checkouts to complete immediately in parallel.

### B. Spring Retry Recovery Routing Error
* **The Problem**: During tests where stock was depleted (e.g. 5 units available, but thread requests 1 unit and sees 0), we observed:
  `org.springframework.retry.ExhaustedRetryException: Cannot locate recovery method`
  This resulted in HTTP 500 responses instead of the expected HTTP 422 `InsufficientStockException`.
* **The Root Cause**: When any `@Recover` method is present in a Spring bean, Spring Retry wraps the bean method in an advisor. If *any* exception (even those not configured for retry, such as `InsufficientStockException`) is thrown, Spring Retry intercepts it and attempts to find a matching `@Recover` method signature. Because we only defined `recover(OptimisticLockingFailureException, ...)`, it threw the `Cannot locate recovery method` exception.
* **The Resolution**: We added a generic recovery method signature that catches and rethrows `Throwable`:
  ```java
  @Recover
  public List<ReservationDetail> recover(Throwable e, UUID productId, int quantity) throws Throwable {
      throw e;
  }
  ```
  Since `InsufficientStockException` matches `Throwable`, Spring Retry correctly routes and rethrows it, allowing our global controller advice to map it to the expected HTTP 422 response.

### C. Persistent Database Test Contamination
* **The Problem**: When running `mvn test` after a failed run, subsequent test runs (e.g., `ProductControllerIntegrationTest` and `InventoryControllerIntegrationTest`) failed with `403 Access Denied` and duplicate key errors on `'admin@test.com'`.
* **The Root Cause**: `CheckoutControllerIntegrationTest` is not `@Transactional` (as it runs concurrent asynchronous HTTP threads via MockMvc) and commits orders, payments, and users to the database. These records remained in the persistent MySQL test database. When other test classes called `userRepository.deleteAll()`, it failed due to the RESTRICT foreign key constraint on the `orders` table, blocking subsequent test setups.
* **The Resolution**:
  - We added an `@AfterEach tearDown()` method to `CheckoutControllerIntegrationTest` to ensure that it cleans all tables in the correct order (payments $\rightarrow$ order items $\rightarrow$ orders $\rightarrow$ carts $\rightarrow$ users) at the end of each test case.
  - We updated `AuthControllerIntegrationTest.java` to re-seed the default admin user `admin@ecommerce.com` if deleted by other tests.
  - All test runs now execute cleanly on a clean database state.
