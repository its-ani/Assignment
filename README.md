# E-Commerce Order Management System (OMS)

## Overview

A production-grade, Spring Boot–based **E-Commerce Order Management System** implementing the full transactional lifecycle: authentication & RBAC, product catalog, multi-warehouse inventory, shopping cart, concurrent checkout with optimistic-locking retries, payment simulation, async event pipeline, full order lifecycle management, discount/tax engine, and returns & refunds processing.

All 11 development phases are **complete**. The project ships with **131 integration tests** covering every controller, concurrency scenarios, the async event pipeline, and inventory lifecycle correctness.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.1 (Web, Data JPA, Security, Validation) |
| Database (prod) | MySQL 8.0+ |
| Database (test) | H2 (in-memory) |
| Schema Management | Flyway Migrations |
| Auth | Spring Security + JJWT 0.12.6 (stateless JWT) |
| Retry / Concurrency | Spring Retry (`spring-retry`) |
| API Docs | Springdoc OpenAPI 2.5.0 (Swagger UI) |
| Utilities | Lombok |
| Build | Maven (Maven Wrapper included) |
| Testing | JUnit 5, Spring Boot Test, MockMvc, Awaitility |

---

## Getting Started

### 1. Start MySQL (Docker)
```bash
docker run --name oms-mysql \
  -e MYSQL_DATABASE=oms_db \
  -e MYSQL_ROOT_PASSWORD=password \
  -p 3306:3306 -d mysql:8.0
```

### 2. Environment Variables
The application reads from these environment variables (all have safe development defaults):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/oms_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` | JDBC connection URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | `password` | DB password |
| `JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | JWT signing key (≥256-bit, **override in production**) |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL in milliseconds (24 hours) |

### 3. Run the Application
```bash
./mvnw spring-boot:run
```
The application starts on **port 8080**. Flyway automatically runs all migrations on startup.

### 4. Run Tests
```bash
./mvnw clean test
```
All 131 tests run against an in-memory H2 database under the `test` Spring profile. No external services are required.

### 5. Swagger UI
Once running, browse the interactive API docs at:
```
http://localhost:8080/swagger-ui.html
```

---

## Bootstrap: First Admin Account

On first startup, a `DatabaseInitializer` component seeds a default admin if no users exist:

| Field | Value |
|---|---|
| Email | `admin@ecommerce.com` |
| Password | `AdminPass123!` |
| Role | `ADMIN` |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST Controllers                      │
│  Auth │ Category │ Product │ Warehouse │ Inventory      │
│  Cart │ Checkout │ Order   │ Discount  │ Return         │
└───────────────────────┬─────────────────────────────────┘
                        │ @PreAuthorize (RBAC)
┌───────────────────────▼─────────────────────────────────┐
│                    Service Layer                         │
│  AuthService │ CartService │ CheckoutService            │
│  OrderService │ InventoryService │ ReturnService        │
│  DiscountValidationService │ TaxCalculationService      │
│  RefundCalculationService │ PaymentService              │
│  ReservationCleanupScheduler (background @Scheduled)    │
└───────────────────────┬─────────────────────────────────┘
                        │ JPA Repositories
┌───────────────────────▼─────────────────────────────────┐
│                 Database (MySQL / H2)                    │
│  users │ categories │ products │ warehouses             │
│  inventory_items │ carts │ cart_items │ orders          │
│  order_items │ payments │ discounts │ return_requests   │
│  audit_logs                                             │
└─────────────────────────────────────────────────────────┘
                        │ ApplicationEvent
┌───────────────────────▼─────────────────────────────────┐
│            Async Event Pipeline (AFTER_COMMIT)          │
│  OrderPlacedEvent → audit log + notification simulation │
│  OrderStatusChangedEvent → audit log + notifications    │
└─────────────────────────────────────────────────────────┘
```

---

## Feature Reference

### Phase 1 — Project Scaffold & Domain Model
- Maven project with Spring Boot 3.3.1, Flyway, and H2/MySQL dual-profile setup
- UUID primary keys (stored as `VARCHAR(36)`) for DB portability
- Core domain entities: `User`, `Category`, `Product`, `Warehouse`, `InventoryItem`, `Cart`, `CartItem`, `Order`, `OrderItem`, `Payment`, `Discount`, `ReturnRequest`, `AuditLog`
- Optimistic locking via `@Version` on `InventoryItem`

---

### Phase 2 — Authentication & RBAC

**Roles:**

| Role | Description |
|---|---|
| `CUSTOMER` | End-user shopping: cart, checkout, orders, returns |
| `WAREHOUSE_STAFF` | Inventory management, order fulfillment advancement |
| `ADMIN` | Full system access including user provisioning |

**Key behaviour:**
- Stateless JWT (JJWT) — no session, no refresh tokens
- Public `/auth/register` always creates `CUSTOMER` accounts (privilege-escalation blocked)
- `/auth/admin/register` (ADMIN-only) creates `ADMIN` or `WAREHOUSE_STAFF` accounts
- 24-hour token expiry (configurable via `JWT_EXPIRATION_MS`)

**Endpoints:**

```bash
# Register a customer (public)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"Secret123!"}'

# Login — returns JWT
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!"}'

# Get current user profile
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <token>"

# Register admin/staff (ADMIN only)
curl -X POST http://localhost:8080/api/v1/auth/admin/register \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob Staff","email":"bob@example.com","password":"Staff123!","role":"WAREHOUSE_STAFF"}'
```

Login response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": { "id": "...", "name": "Alice", "email": "alice@example.com", "role": "CUSTOMER" }
}
```

---

### Phase 3 — Catalog & Product Management

- Hierarchical categories (self-referencing, circular-parent guard → `400 Bad Request`)
- Category deletion blocked if subcategories or products are linked (`409 Conflict`)
- Category names globally unique (case-insensitive)
- Product soft-delete: sets `active = false`; hidden from customers, visible to ADMIN/WAREHOUSE_STAFF
- Product search: keyword, price range, category (with optional recursive descendant inclusion), sort, pagination

**Access:** Public (read), ADMIN (write)

```bash
# Create category (ADMIN)
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Electronics","description":"All electronics"}'

# Create sub-category (ADMIN)
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptops","parentId":"<electronics_id>"}'

# Create product (ADMIN)
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"MacBook Pro","description":"Powerful laptop","categoryId":"<laptops_id>","price":1999.99,"sku":"MBP-M3","active":true}'

# Search products (public)
curl "http://localhost:8080/api/v1/products?categoryId=<id>&includeDescendants=true&keyword=macbook&minPrice=1000&maxPrice=3000&page=0&size=10&sortBy=price&direction=asc"
```

---

### Phase 4 — Warehouse & Inventory Management

- Warehouse CRUD (ADMIN write, ADMIN/WAREHOUSE_STAFF read)
- Warehouse deletion blocked if inventory records are linked (`409 Conflict`)
- Stock visibility by role:
  - **Customers / public**: aggregate `available: true/false` + `totalAvailableQuantity` embedded in product responses
  - **ADMIN / WAREHOUSE_STAFF**: full per-warehouse breakdown (`quantityOnHand`, `quantityReserved`, `quantityAvailable`)
- `quantityAvailable = quantityOnHand - quantityReserved` (derived, never negative)
- Synchronous audit log written in the same transaction for every stock mutation

```bash
# Set stock absolute (ADMIN)
curl -X PUT http://localhost:8080/api/v1/inventory/<product_id>/warehouse/<warehouse_id> \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"quantityOnHand":100}'

# Adjust stock delta (ADMIN)
curl -X PATCH http://localhost:8080/api/v1/inventory/<product_id>/warehouse/<warehouse_id>/adjust \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"delta":-10,"reason":"Damaged goods removal"}'

# View inventory for a product (ADMIN/WAREHOUSE_STAFF)
curl http://localhost:8080/api/v1/inventory/product/<product_id> \
  -H "Authorization: Bearer <staff_token>"

# View inventory in a warehouse (ADMIN/WAREHOUSE_STAFF)
curl http://localhost:8080/api/v1/inventory/warehouse/<warehouse_id> \
  -H "Authorization: Bearer <staff_token>"
```

Product response (public) includes:
```json
{
  "id": "...",
  "name": "Smartphone",
  "price": 599.99,
  "active": true,
  "availability": { "available": true, "totalAvailableQuantity": 90 }
}
```

---

### Phase 5 — Shopping Cart

- Cart is resolved implicitly from JWT (no `cartId` in URLs — prevents cross-user access)
- Lazy cart creation: first `GET /cart` or `POST /cart/items` auto-creates the cart
- Deduplication: adding the same product increments quantity on the existing line item
- Advisory stock check: returns `availabilityWarning` if requested quantity exceeds stock, but still succeeds (hard lock deferred to checkout)
- Unit prices and subtotals computed live from current catalog prices (not stored)
- Setting quantity to `0` removes the line item
- Clearing the cart preserves the cart record (empty items)

**Access:** CUSTOMER only

```bash
# View cart
curl http://localhost:8080/api/v1/cart -H "Authorization: Bearer <customer_token>"

# Add item
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer <customer_token>" \
  -H "Content-Type: application/json" \
  -d '{"productId":"<id>","quantity":2}'

# Update quantity (set to 0 to remove)
curl -X PATCH http://localhost:8080/api/v1/cart/items/<product_id> \
  -H "Authorization: Bearer <customer_token>" \
  -H "Content-Type: application/json" \
  -d '{"quantity":5}'

# Remove single item
curl -X DELETE http://localhost:8080/api/v1/cart/items/<product_id> \
  -H "Authorization: Bearer <customer_token>"

# Clear all items
curl -X DELETE http://localhost:8080/api/v1/cart -H "Authorization: Bearer <customer_token>"
```

---

### Phase 6 — Order Placement & Checkout

- **Pessimistic lock on cart**: `PESSIMISTIC_WRITE` prevents concurrent double-submissions of the same cart
- **Greedy warehouse-split strategy**: fulfils each cart item from the highest-stock warehouse first; splits across warehouses if needed
- **Optimistic locking + retry**: `@Version` on `InventoryItem`; up to 5 retries with exponential backoff via Spring Retry
- **Isolated reservation transactions**: each stock reservation runs in `REQUIRES_NEW` propagation so a retry on one product doesn't roll back others
- **Explicit compensation**: any failure (partial reservation, payment, etc.) after committed reservations triggers stock release via tracked `ReservedItemTracker` list
- **Orphaned reservation safety net**: a background `ReservationCleanupScheduler` runs every 15 minutes to detect and release reservations not backed by active orders (guards against JVM crashes or failed compensation)
- **Payment simulation**: configurable stub (forces failures in tests)
- **Tax**: configurable flat rate via `app.tax-rate` (default 10%)
- **Discount**: optional discount code applied at checkout (see Phase 9)

**Access:** CUSTOMER only

```bash
# Checkout (discount code and shipping address are optional)
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer <customer_token>" \
  -H "Content-Type: application/json" \
  -d '{"discountCode":"SAVE10","shippingAddress":"123 Main St, Springfield"}'
```

Response:
```json
{
  "id": "e9e8efec-...",
  "status": "PLACED",
  "items": [{ "productName": "Laptop", "quantity": 1, "unitPrice": 1000.00, "lineTotal": 1000.00 }],
  "subtotal": 1000.00,
  "discountAmount": 100.00,
  "taxAmount": 90.00,
  "totalAmount": 990.00,
  "paymentStatus": "SUCCESS",
  "createdAt": "2026-07-14T00:00:00Z"
}
```

---

### Phase 7 — Async Event Pipeline

- `OrderPlacedEvent` published on successful checkout
- `OrderStatusChangedEvent` published on every status transition
- Both events are processed by `OrderEventListener` **after transaction commit** (`AFTER_COMMIT`) to avoid coupling business logic with side effects
- Side effects: structured `AuditLog` entries written to DB + customer notification simulation (logged)

---

### Phase 8 — Order Lifecycle & Fulfillment

**Order Status State Machine:**

```
[ PLACED ] ──── Customer / Admin cancel ───────────────────────────────► [ CANCELLED ]
    │                                                                          ▲
    │ (Warehouse Staff / Admin advance)                                        │
    ▼                                                                          │
[ CONFIRMED ] ── Customer / Admin cancel ─────────────────────────────────────┤
    │                                                                          │
    │ (Warehouse Staff / Admin advance)                                        │
    ▼                                                                          │
[ PROCESSING ]                                                                 │
    │                                                                          │
    │ (Warehouse Staff / Admin advance)                                        │
    ▼                                                                          │
[ SHIPPED ] ──── Admin force-cancel ──────────────────────────────────────────┘
    │         ▲ Inventory fulfilled:
    │         │ quantityOnHand -= qty
    │         │ quantityReserved -= qty
    │ (Warehouse Staff / Admin advance)
    ▼
[ DELIVERED ] ──────────────────────────────────────────────────────────► [ RETURNED ]
```

**Inventory operations per status transition:**

| Transition | Inventory Effect |
|---|---|
| Any → `CANCELLED` | `quantityReserved` decremented (reservation released) |
| `PACKED` → `SHIPPED` | `quantityOnHand` **and** `quantityReserved` both decremented (fulfillment) |
| `SHIPPED` → `DELIVERED` | Timestamp set, no inventory change (already fulfilled) |
| All other transitions | No inventory change |

**RBAC for orders:**

| Action | CUSTOMER | WAREHOUSE_STAFF | ADMIN |
|---|---|---|---|
| List / view own orders | ✅ | — | — |
| List / view all orders (`/staff`) | — | ✅ | ✅ |
| Cancel own order (PLACED/CONFIRMED) | ✅ | — | — |
| Advance status | — | ✅ | ✅ |
| Force-cancel at any pre-delivery stage | — | ❌ | ✅ |

- Cross-customer order access returns `404 Not Found` (not `403`) to prevent ID enumeration
- Cancellation releases `quantityReserved` on all associated `InventoryItem` records
- Shipment fulfils inventory: decrements both `quantityOnHand` and `quantityReserved`, keeping `quantityAvailable` consistent
- `customerId` is stripped from order list responses returned to customers

```bash
# My orders (CUSTOMER)
curl "http://localhost:8080/api/v1/orders?status=PLACED&page=0&size=10" \
  -H "Authorization: Bearer <customer_token>"

# Order detail (CUSTOMER)
curl http://localhost:8080/api/v1/orders/<order_id> \
  -H "Authorization: Bearer <customer_token>"

# Cancel own order (CUSTOMER)
curl -X POST http://localhost:8080/api/v1/orders/<order_id>/cancel \
  -H "Authorization: Bearer <customer_token>" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Changed my mind"}'

# All orders - staff view (ADMIN/WAREHOUSE_STAFF)
curl "http://localhost:8080/api/v1/orders/staff?status=CONFIRMED&page=0&size=20" \
  -H "Authorization: Bearer <staff_token>"

# Advance order status (ADMIN/WAREHOUSE_STAFF)
curl -X PATCH http://localhost:8080/api/v1/orders/<order_id>/status \
  -H "Authorization: Bearer <staff_token>" \
  -H "Content-Type: application/json" \
  -d '{"newStatus":"PROCESSING"}'
```

---

### Phase 9 — Discounts & Tax Engine

**Calculation order:**
$$\text{Total} = (\text{Subtotal} - \text{Discount}) + \text{Tax on post-discount subtotal}$$

**Discount validation rules:**
- Code must exist and be `active = true`
- Current timestamp must be within `[validFrom, validUntil]`
- Cart subtotal must meet or exceed `minimumOrderAmount`
- **Anti-abuse**: max **one use per customer** across non-cancelled orders
- Discount amount is capped at subtotal (total cannot go negative)

**Discount types:** `PERCENTAGE` (e.g. 10%) | `FLAT` (e.g. $15 off)

**Tax:** Configurable via `app.tax-rate` (default `0.10`). Applied to post-discount subtotal.

```bash
# Preview discount (CUSTOMER/ADMIN)
curl "http://localhost:8080/api/v1/discounts/validate?code=SAVE10&cartTotal=100.00" \
  -H "Authorization: Bearer <token>"
# → {"code":"SAVE10","type":"PERCENTAGE","value":10.00,"discountAmount":10.00,"eligible":true}

# Create discount (ADMIN)
curl -X POST http://localhost:8080/api/v1/discounts \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "code":"SAVE10","type":"PERCENTAGE","value":10.00,
    "minimumOrderAmount":50.00,"maxUsageCount":100,
    "validFrom":"2024-01-01T00:00:00","validUntil":"2030-12-31T23:59:59","active":true
  }'

# Update discount (ADMIN)
curl -X PUT http://localhost:8080/api/v1/discounts/<discount_id> \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"code":"SAVE15","type":"PERCENTAGE","value":15.00,...}'

# Deactivate discount (ADMIN)
curl -X DELETE http://localhost:8080/api/v1/discounts/<discount_id> \
  -H "Authorization: Bearer <admin_token>"

# List all discounts (ADMIN)
curl "http://localhost:8080/api/v1/discounts?page=0&size=20" \
  -H "Authorization: Bearer <admin_token>"
```

---

### Phase 10 — Returns & Refunds

- Returns can only be initiated by the order's owner and only after the order reaches `DELIVERED`
- Configurable return window: `app.return.window-days` (blocks requests past the deadline)
- **Refund formula** (proportional, discount + tax aware):

$$\text{Refund} = \text{Order Total} \times \frac{\text{Item Unit Price} \times \text{Returned Qty}}{\text{Order Subtotal}}$$

- Approved returns trigger physical restocking: `quantityOnHand` is incremented at the original fulfillment warehouse
- Full decision transaction: approve/reject + payment refund + inventory restock wrapped in a single DB transaction (failure rolls back to `PENDING` for retry)
- `AuditLog` entry written asynchronously on decision

**Return statuses:** `PENDING` → `APPROVED` / `REJECTED` → (if APPROVED) → `REFUNDED`

```bash
# Submit return (CUSTOMER — order must be DELIVERED)
curl -X POST http://localhost:8080/api/v1/returns \
  -H "Authorization: Bearer <customer_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":"<order_id>",
    "reason":"Product arrived damaged",
    "items":[{"orderItemId":"<item_id>","quantity":1}]
  }'

# My returns (CUSTOMER)
curl "http://localhost:8080/api/v1/returns?page=0&size=20" \
  -H "Authorization: Bearer <customer_token>"

# Return detail (CUSTOMER)
curl http://localhost:8080/api/v1/returns/<return_id> \
  -H "Authorization: Bearer <customer_token>"

# All returns — staff view (ADMIN/WAREHOUSE_STAFF)
curl "http://localhost:8080/api/v1/returns/staff?status=PENDING&page=0&size=20" \
  -H "Authorization: Bearer <staff_token>"

# Approve / reject return (ADMIN/WAREHOUSE_STAFF)
curl -X PATCH http://localhost:8080/api/v1/returns/<return_id>/decision \
  -H "Authorization: Bearer <staff_token>" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","notes":"Damage confirmed, refund approved"}'
```

---

### Phase 11 — Final Polish & Test Reliability

- Replaced all `Thread.sleep`-based async polling in integration tests with **Awaitility** for deterministic, flakiness-free async assertions
- Full test suite: **131 tests across 11 test classes** covering all controllers, concurrency edge cases, and inventory lifecycle correctness
- OpenAPI / Swagger documentation updated with system description and versioning
- All test classes verified to pass reliably with `./mvnw clean test`

---

### Phase 12 — Inventory Lifecycle Fixes

Two critical inventory management bugs were identified and fixed:

1. **Inventory fulfillment on SHIPPED**: When an order transitions to `SHIPPED`, both `quantityOnHand` and `quantityReserved` are now decremented. Previously, neither was adjusted on shipment, causing `quantityReserved` to grow infinitely and `quantityAvailable` to trend toward zero after successful orders.

2. **Orphaned reservation cleanup**: A `ReservationCleanupScheduler` (`@Scheduled`, every 15 minutes, configurable via `reservation.cleanup.interval-ms`) detects and releases `quantityReserved` values not backed by active orders (`PLACED`/`CONFIRMED`/`PACKED`). This guards against JVM crashes or network failures that could leave committed `REQUIRES_NEW` reservations without a corresponding `Order` record.

---

## API Endpoint Summary

| Module | Method | Path | Role |
|---|---|---|---|
| **Auth** | POST | `/api/v1/auth/register` | Public |
| | POST | `/api/v1/auth/login` | Public |
| | POST | `/api/v1/auth/admin/register` | ADMIN |
| | GET | `/api/v1/auth/me` | Any authenticated |
| **Categories** | POST | `/api/v1/categories` | ADMIN |
| | PUT | `/api/v1/categories/{id}` | ADMIN |
| | DELETE | `/api/v1/categories/{id}` | ADMIN |
| | GET | `/api/v1/categories/{id}` | Public |
| | GET | `/api/v1/categories` | Public |
| **Products** | POST | `/api/v1/products` | ADMIN |
| | PUT | `/api/v1/products/{id}` | ADMIN |
| | DELETE | `/api/v1/products/{id}` | ADMIN |
| | GET | `/api/v1/products/{id}` | Public |
| | GET | `/api/v1/products` | Public |
| **Warehouses** | POST | `/api/v1/warehouses` | ADMIN |
| | PUT | `/api/v1/warehouses/{id}` | ADMIN |
| | DELETE | `/api/v1/warehouses/{id}` | ADMIN |
| | GET | `/api/v1/warehouses/{id}` | ADMIN / WAREHOUSE_STAFF |
| | GET | `/api/v1/warehouses` | ADMIN / WAREHOUSE_STAFF |
| **Inventory** | PUT | `/api/v1/inventory/{productId}/warehouse/{warehouseId}` | ADMIN |
| | PATCH | `/api/v1/inventory/{productId}/warehouse/{warehouseId}/adjust` | ADMIN |
| | GET | `/api/v1/inventory/product/{productId}` | ADMIN / WAREHOUSE_STAFF |
| | GET | `/api/v1/inventory/warehouse/{warehouseId}` | ADMIN / WAREHOUSE_STAFF |
| **Cart** | GET | `/api/v1/cart` | CUSTOMER |
| | POST | `/api/v1/cart/items` | CUSTOMER |
| | PATCH | `/api/v1/cart/items/{productId}` | CUSTOMER |
| | DELETE | `/api/v1/cart/items/{productId}` | CUSTOMER |
| | DELETE | `/api/v1/cart` | CUSTOMER |
| **Checkout** | POST | `/api/v1/orders/checkout` | CUSTOMER |
| **Orders** | GET | `/api/v1/orders` | CUSTOMER |
| | GET | `/api/v1/orders/{id}` | CUSTOMER |
| | POST | `/api/v1/orders/{id}/cancel` | CUSTOMER |
| | GET | `/api/v1/orders/staff` | ADMIN / WAREHOUSE_STAFF |
| | GET | `/api/v1/orders/staff/{id}` | ADMIN / WAREHOUSE_STAFF |
| | PATCH | `/api/v1/orders/{id}/status` | ADMIN / WAREHOUSE_STAFF |
| **Discounts** | POST | `/api/v1/discounts` | ADMIN |
| | PUT | `/api/v1/discounts/{id}` | ADMIN |
| | DELETE | `/api/v1/discounts/{id}` | ADMIN |
| | GET | `/api/v1/discounts/{id}` | ADMIN |
| | GET | `/api/v1/discounts` | ADMIN |
| | GET | `/api/v1/discounts/validate` | CUSTOMER / ADMIN |
| **Returns** | POST | `/api/v1/returns` | CUSTOMER |
| | GET | `/api/v1/returns` | CUSTOMER |
| | GET | `/api/v1/returns/{id}` | CUSTOMER |
| | GET | `/api/v1/returns/staff` | ADMIN / WAREHOUSE_STAFF |
| | PATCH | `/api/v1/returns/{id}/decision` | ADMIN / WAREHOUSE_STAFF |

---

## Key Design Decisions & Assumptions

### Authentication
- Single access-token flow (no refresh tokens, no MFA, no email verification)
- Default JWT key is hardcoded for local dev; **must** be overridden via `JWT_SECRET` in production
- `CUSTOMER` accounts are the only role creatable via the public registration endpoint

### Catalog
- Descendant category resolution for product search is done in-service (not via SQL CTEs) for H2/MySQL portability
- Inactive product direct lookups: `404` for customers, `200` with `active=false` for staff/admin

### Inventory
- `quantityReserved` is managed exclusively by checkout, cancellation, and shipment fulfillment logic (not directly editable via admin APIs)
- On `SHIPPED`: `quantityOnHand` and `quantityReserved` are both decremented to reflect physical stock departure and reservation fulfillment
- A `ReservationCleanupScheduler` runs every 15 minutes to release orphaned reservations not backed by active orders
- DB-level CHECK constraints (MySQL 8.0.16+ and H2 compatible) enforce non-negative stock invariants
- All direct admin stock mutations write synchronous audit logs within the same transaction

### Checkout & Concurrency
- `PESSIMISTIC_WRITE` lock on the cart prevents concurrent double-checkouts
- `@Version` optimistic locking with `REQUIRES_NEW` propagation isolates per-product retry boundaries
- Any failure (partial reservation, payment, etc.) triggers explicit stock compensation via a tracked `ReservedItemTracker` list (does not rely on rollback, since reservations are in committed sub-transactions)
- Orphaned reservations from JVM crashes or failed compensation are cleaned up by the `ReservationCleanupScheduler` background job

### Orders
- `404 Not Found` (not `403 Forbidden`) returned when a customer tries to access another customer's order — prevents ID enumeration
- `customerId` excluded from customer-facing order list responses via `@JsonInclude(NON_NULL)`
- WAREHOUSE_STAFF cannot cancel orders; only ADMIN can force-cancel

### Discounts & Tax
- One discount code per order maximum
- Tax computed on post-discount subtotal (standard retail convention)
- Per-customer usage tracked against non-cancelled orders (cancelled orders restore eligibility)

### Returns & Refunds
- Returns allowed only after `DELIVERED` status and within the configurable window (`app.return.window-days`)
- Refund, stock restock, and status update are committed in a single transaction; any failure keeps status as `PENDING` for safe retry
