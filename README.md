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
- **Phase 2**: Authentication & Role-Based Access Control (RBAC) (Completed)
- **Phase 3**: Catalog & Product Information Management (Completed)
- **Phase 4**: Inventory & Warehouse Management (Pending)
- **Phase 5**: Cart Operations & Price Calculation (Pending)
- **Phase 6**: Order Placement & Checkout Transaction (Completed)
- **Phase 7**: State Machine for Order Lifecycle (Completed)
- **Phase 8**: Order Lifecycle & Fulfillment Status Management (Completed)
- **Phase 9**: Returns Processing & Refunds (Pending)
- **Phase 10**: OpenAPI Documentation & End-to-End Tests (Pending)
- **Phase 11**: Production Deployment & Optimization (Pending)


## Authentication & RBAC (Phase 2)

We use **Spring Security** combined with stateless **JSON Web Tokens (JWT)** generated via the **JJWT** (Java JWT) library. 

### Roles & Access Control
- `CUSTOMER`: Default role for all users. Can perform checkout, manage their own cart, view catalog, etc.
- `WAREHOUSE_STAFF`: Responsible for managing inventory levels, packing orders, and marking orders as packed/shipped.
- `ADMIN`: Full system access, including creating and managing warehouse staff and other administrators.

### JWT Properties & Configuration
The JWT properties can be overridden via environment variables in `application.yml`:
- `app.security.jwt.secret` (env variable: `JWT_SECRET`): The signature key used to sign tokens (must be at least 256 bits. Default: `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970`).
- `app.security.jwt.expiration-ms` (env variable: `JWT_EXPIRATION_MS`): Token validity duration. Default: `86400000` (24 hours).

### Bootstrapping the System (First Admin Account)
On startup, a `DatabaseInitializer` component runs and automatically inserts a default Administrator account if no users exist in the database:
- **Email**: `admin@ecommerce.com`
- **Password**: `AdminPass123!`
- **Role**: `ADMIN`

### API Usage Examples (cURL)

#### 1. Register a new Customer Account
Public endpoint. The `role` field is optional and is ignored here (forced to `CUSTOMER` to prevent privilege escalation):
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com",
    "password": "securepassword123"
  }'
```

#### 2. Log in to obtain a JWT
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "securepassword123"
  }'
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": "e9b25102-...",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "role": "CUSTOMER"
  }
}
```

#### 3. Access a Protected Endpoint
To call `/api/v1/auth/me` (returns the currently authenticated user's details):
```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <your_jwt_token>"
```

#### 4. Create an Admin or Warehouse User (Admin Only)
Requires an `ADMIN` role bearer token (e.g. login as `admin@ecommerce.com` to get the token):
```bash
curl -X POST http://localhost:8080/api/v1/auth/admin/register \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Warehouse Supervisor",
    "email": "supervisor@ecommerce.com",
    "password": "workerpassword123",
    "role": "WAREHOUSE_STAFF"
  }'
```

## Catalog & Product Management (Phase 3)

### Features & Security Access
- **Anonymous Storefront Visibility**: Unauthenticated users (and `CUSTOMER`s) have public read access to `GET /api/v1/products` (including search/filters) and `GET /api/v1/categories`.
- **Admin-Only Mutations**: Creating, updating, deleting, or deactivating categories/products is strictly restricted to users with the `ADMIN` role.
- **Category Nesting**: Categories support self-referencing hierarchy checks to block circular parenting chains (results in `400 Bad Request`).
- **Product Soft Delete**: Deleting a product sets `active = false`. Customers/anonymous visitors cannot see inactive products, but `ADMIN` and `WAREHOUSE_STAFF` can retrieve and search them.
- **Category Delete Guards**: Category deletion is blocked (`409 Conflict`) if subcategories or products are linked.

### API Usage Examples (cURL)

#### 1. Create a Category (Admin Only)
```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Electronics"
  }'
```

#### 2. Create a Nested Category (Admin Only)
```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptops",
    "parentCategoryId": "<electronics_category_id>"
  }'
```

#### 3. Create a Product (Admin Only)
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro",
    "description": "Powerful laptop",
    "categoryId": "<laptops_category_id>",
    "price": 1999.99,
    "active": true
  }'
```

#### 4. Search Products (Public)
Supports searching by keyword, price range, category filter (recursively compiling descendant categories if `includeDescendants=true`), sorting, and pagination:
```bash
curl -X GET "http://localhost:8080/api/v1/products?categoryId=<electronics_category_id>&includeDescendants=true&keyword=macbook&minPrice=1000.00&maxPrice=3000.00&page=0&size=10&sortBy=price&direction=asc"
```

## Warehouse & Inventory Management (Phase 4)

### Features & Security Access
- **Warehouse CRUD**: Restricted to `ADMIN` for creation, update, and deletion. `WAREHOUSE_STAFF` can read warehouses, while `CUSTOMER` and anonymous callers are forbidden (`403 Forbidden`).
- **Warehouse Delete Guard**: Deletion of a warehouse is blocked (`409 Conflict`) if any inventory item mapping is associated with it.
- **Stock Visibility (Public vs. Private)**: 
  - `CUSTOMER` and anonymous users can view an aggregate availability signal `availability` nested under product response endpoints (e.g. `available: true/false`, and `totalAvailableQuantity`). They cannot see detailed per-warehouse stock breakdowns.
  - `ADMIN` and `WAREHOUSE_STAFF` can retrieve full detailed breakdowns of inventory (physical `quantityOnHand`, pending `quantityReserved`, and derived `quantityAvailable` calculated as `quantityOnHand - quantityReserved`).
- **Inventory Mutations (Admin Only)**:
  - **Absolute Stock Set**: `PUT /api/v1/inventory/{productId}/warehouse/{warehouseId}` sets the exact physical stock quantity.
  - **Relative Stock Adjustment**: `PATCH /api/v1/inventory/{productId}/warehouse/{warehouseId}/adjust` increments or decrements physical stock by a delta.
- **Safety Guards**:
  - Stock updates must never result in a negative `quantityOnHand`.
  - Reserved stock (`quantityReserved`) must never exceed physical stock (`quantityOnHand`).
  - Violation of these guards results in a `409 Conflict` (for relative adjust and absolute sets that violate the constraints).
- **Synchronous Audit Logging**: Every inventory edit (creation, set, or adjust) synchronously writes a log to the `audit_logs` table in the same transaction, recording the before/after values and the authenticated email.

### API Usage Examples (cURL)

#### 1. Create a Warehouse (Admin Only)
```bash
curl -X POST http://localhost:8080/api/v1/warehouses \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Central Distribution Center",
    "location": "123 Industrial Parkway, Chicago, IL"
  }'
```

#### 2. Set Warehouse Stock (Admin Only)
```bash
curl -X PUT http://localhost:8080/api/v1/inventory/<product_uuid>/warehouse/<warehouse_uuid> \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "quantityOnHand": 100
  }'
```

#### 3. Adjust Stock by Delta (Admin Only)
```bash
curl -X PATCH http://localhost:8080/api/v1/inventory/<product_uuid>/warehouse/<warehouse_uuid>/adjust \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "delta": -10,
    "reason": "Damaged stock return"
  }'
```

#### 4. View Product with Public Availability Summary (Public)
```bash
curl -X GET http://localhost:8080/api/v1/products/<product_uuid>
```
Response:
```json
{
  "id": "abc-...",
  "name": "Smartphone",
  "description": "Premium mobile",
  "price": 599.99,
  "active": true,
  "availability": {
    "productId": "abc-...",
    "available": true,
    "totalAvailableQuantity": 90
  }
}
```

## Customer Cart Management (Phase 5)

### Features & Security Access
- **Transparent Cart Identification**: Cart endpoints do not expose or accept a `cartId` path parameter. The active cart is resolved implicitly from the authenticated user's ID inside the JWT token.
- **Implicit Cart Creation**: An active cart is created automatically on the user's first view or addition of a product. No explicit "create cart" call is needed.
- **Role restrictions**: Cart endpoints are strictly CUSTOMER-only. Admin and Warehouse staff will receive `403 Forbidden`.
- **Deduplication of Items**: Adding the same product multiple times increments the quantity on the existing CartItem rather than creating a duplicate row.
- **Advisory Inventory Checking**: Adding/updating cart items performs a soft availability check. If quantity exceeds aggregate warehouse availability, an `availabilityWarning` is attached to the item response, but the action succeeds (as stock changes dynamically). Hard reservations are deferred to Phase 6 checkout.
- **Live Price Computation**: Cart subtotal and item unit prices are computed on the fly on GET based on current product prices (not cached in DB) to reflect real-time pricing changes.
- **Zero-Quantity Removal**: Updating an item's quantity to `0` removes it from the cart.
- **Persistent Empty Carts**: Removing the last item or clearing the cart empties the items list but keeps the active `Cart` record.

### API Usage Examples (cURL)

#### 1. View My Cart (Customer Only)
```bash
curl -X GET http://localhost:8080/api/v1/cart \
  -H "Authorization: Bearer <customer_jwt_token>"
```
Response:
```json
{
  "id": "cfcb25bc-d6c6-463f-8aac-fde55ccdf60d",
  "status": "ACTIVE",
  "items": [],
  "subtotal": 0.00,
  "itemCount": 0
}
```

#### 2. Add Item to Cart (Customer Only)
```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer <customer_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "<product_uuid>",
    "quantity": 2
  }'
```

#### 3. Update Item Quantity (Customer Only)
Allows setting quantity to 0 to remove the line item.
```bash
curl -X PATCH http://localhost:8080/api/v1/cart/items/<product_uuid> \
  -H "Authorization: Bearer <customer_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 5
  }'
```

#### 4. Remove Item from Cart (Customer Only)
```bash
curl -X DELETE http://localhost:8080/api/v1/cart/items/<product_uuid> \
  -H "Authorization: Bearer <customer_jwt_token>"
```

#### 5. Clear Cart (Customer Only)
Empties all items but preserves the active cart record.
```bash
curl -X DELETE http://localhost:8080/api/v1/cart \
  -H "Authorization: Bearer <customer_jwt_token>"
```

## Order Placement & Checkout (Phase 6)

### Features & Security Access
- **Pessimistic-Lock Idempotency**: Active carts are resolved using a `PESSIMISTIC_WRITE` lock and immediately transitioned to `CHECKED_OUT` status at the start of the checkout transaction to prevent concurrent double-submissions.
- **Warehouse-splitting Greedy Strategy**: Orders are fulfilled using a highest-stock-first strategy across warehouses, splitting a single cart item into multiple order items if necessary to fulfill the requested quantity.
- **Optimistic Locking Retry Loop**: Concurrency on stock reservation is handled using JPA `@Version` optimistic locking with up to 5 automatic retries and exponential backoff.
- **Independent Transaction Isolation**: Individual stock reservations run in isolated transactions (`propagation = Propagation.REQUIRES_NEW`), preventing lock retries on one product from rolling back the entire checkout.
- **Explicit Compensation**: If checkout fails after reservations are committed (e.g. payment failure), reserved stock is explicitly released back to the inventory via compensating actions.
- **Configurable Payment Stub**: Simulates payment collection and forces failures dynamically during testing.
- **Tax Placeholder**: Computes a flat 10% tax rate.

### API Usage Examples (cURL)

#### 1. Checkout Active Cart (Customer Only)
```bash
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer <customer_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{}'
```
Response:
```json
{
  "id": "e9e8efec-3158-454a-94d1-b9f5155e4e46",
  "status": "PLACED",
  "items": [
    {
      "productId": "ab867f53-cf60-446f-b598-38e418bb1f26",
      "productName": "Laptop",
      "warehouseId": "5322efec-3158-454a-94d1-b9f5155e4e46",
      "quantity": 1,
      "unitPrice": 1000.00,
      "lineTotal": 1000.00
    }
  ],
  "subtotal": 1000.00,
  "taxAmount": 100.00,
  "discountAmount": 0.00,
  "totalAmount": 1100.00,
  "paymentStatus": "SUCCESS",
  "createdAt": "2026-07-13T23:31:16Z"
}
```

## Order Lifecycle & Fulfillment (Phase 8)

### Features & Security Access
- **State Machine Transitions**: Enforces a strict status sequence: `PLACED -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`.
- **Pre-packing Cancellation Window**: Customers can self-service cancel orders only while they are in `PLACED` or `CONFIRMED` status. Once `PACKED`, cancellation is blocked.
- **Inventory Reservation Release**: Cancelling an order automatically releases the reserved product stocks across warehouses, decrementing `quantityReserved` on `InventoryItem` records.
- **Role-based Access Control (RBAC)**:
  - `CUSTOMER`: Can query their own orders and details (paginated and filterable). Access to other customer orders returns `404 Not Found` to prevent ID enumeration. Can self-cancel pre-packing.
  - `WAREHOUSE_STAFF`: Can view all orders and advance the status forward (`PLACED -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`). Cannot cancel orders.
  - `ADMIN`: Full access. Can view/list any order, advance statuses, and force-cancel orders at any stage prior to delivery.
- **Async Event Pipeline**: Successful status transitions publish `OrderStatusChangedEvent`. An asynchronous listener intercepts the event post-commit (`AFTER_COMMIT`) to simulate customer notifications and record structured `AuditLog` entries.

### Order Status State Machine Diagram
```text
  [ PLACED ] ---------(Customer / Admin Cancel)---------> [ CANCELLED ]
      |                                                        ^
(Warehouse / Admin)                                            |
      v                                                        |
[ CONFIRMED ] -------(Customer / Admin Cancel)-----------------+
      |
(Warehouse / Admin)
      v
  [ PACKED ] 
      |
(Warehouse / Admin)
      v
 [ SHIPPED ] ---------(Admin Force Cancel Override)------------+
      |
(Warehouse / Admin)
      v
[ DELIVERED ]
```

### API Usage Examples (cURL)

#### 1. List Own Orders (Customer Only)
```bash
curl -X GET "http://localhost:8080/api/v1/orders?status=PLACED&page=0&size=10" \
  -H "Authorization: Bearer <customer_jwt_token>"
```

#### 2. Self-Service Cancel Order (Customer Only)
```bash
curl -X POST http://localhost:8080/api/v1/orders/<order_id>/cancel \
  -H "Authorization: Bearer <customer_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Wrong item selected"}'
```

#### 3. Update Order Status (Warehouse Staff / Admin Only)
```bash
curl -X PATCH http://localhost:8080/api/v1/orders/<order_id>/status \
  -H "Authorization: Bearer <staff_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"newStatus": "PACKED"}'
```

## Assumptions Log

### Phase 1 Assumptions
- **Primary Database Engine**: MySQL is selected as the primary relational database.
- **ID Generation Strategy**: UUID is used for all primary keys. Since MySQL does not support a native `UUID` type, they are stored as `VARCHAR(36)` strings. This is highly portable, human-readable, and compatible with both MySQL and H2 databases.
- **Concurrency Locking Strategy**: Leaning towards **Optimistic Locking** on `InventoryItem` using a `@Version` field (`version` column) to support high-throughput checkout updates while avoiding database deadlocks associated with pessimistic locking.
- **AuditLog Metadata**: Stored as a `TEXT` data type containing a serialized JSON string to maximize database compatibility between MySQL and H2 without relying on vendor-specific JSONB dialect configurations.
- **SQL Keyword Resolution**: Renamed the `value` column in the `discounts` table to `discount_value` on the database level, but mapped it to the Java field `value` to avoid parser errors on the reserved SQL keyword `value`.

### Phase 2 Assumptions
- **No Refresh Tokens**: A single access token flow with a 24-hour expiration duration is used. Refresh tokens, MFA, and SSO are out of scope for the current design.
- **No Email Verification / Password Reset**: Users are activated immediately upon registration. Verification emails and self-service password recovery are out of scope.
- **Role Escalation Prevention**: The public `/auth/register` endpoint only creates `CUSTOMER` accounts. Creating `ADMIN` and `WAREHOUSE_STAFF` users requires calling `/auth/admin/register`, which is restricted to authenticated users with `ROLE_ADMIN`.
- **JWT Key Deployment**: The default development key is hardcoded for convenience during local development and automated testing, but must be overridden via the `JWT_SECRET` environment variable in production settings.

### Phase 3 Assumptions
- **Nesting Descendants Search Resolution**: When filtering products by category ID, subcategories are recursively evaluated at the service layer rather than utilizing vendor-specific recursively nested SQL queries (CTE) for maximum portability between MySQL and H2.
- **Visibility Rules for Soft-deleted Products**: Direct product lookups by ID of inactive products return `404 Not Found` for customers, but `200 OK` (returning details showing `active=false`) for admins/warehouse staff to allow viewing and editing historical logs.
- **Category Name Uniqueness Constraint**: Category names are enforced to be unique globally (case-insensitive check) to prevent confusion at different tree levels.

### Phase 4 Assumptions
- **Database CHECK Constraint Compatibility**: Added follow-up Flyway migration `V2__inventory_constraints.sql` defining database-level CHECK constraints. This assumes standard check constraints are supported by the target MySQL server (supported in MySQL 8.0.16+) and H2 database for testing.
- **Direct Inventory Auditing Synchronicity**: Direct manual stock sets/adjustments write synchronous audit logs inside the same database transaction. This guarantees instant consistency of direct admin edits. The asynchronous event-driven audit pipeline will be built in Phase 7 to record checkout and fulfillment event lifecycles.
- **quantityReserved Initialization**: The `quantityReserved` field is initialized to `0` and is not modifiable by direct admin APIs in this phase. It will be managed exclusively via checkout lock mechanisms added in Phase 6.
- **Simple Warehouses**: Location uses a descriptive text/address string rather than geo-coordinates. Spatial query functions are out of scope.

### Phase 5 Assumptions
- **No cartId in Endpoints**: Carts are fetched and mutated transparently based on the customer's JWT authentication token rather than exposure via a path parameter (e.g., `/api/v1/cart/items` instead of `/api/v1/cart/{cartId}/items`). This prevents cross-tenant access and ID harvesting attacks.
- **Implicit Cart Creation**: When a customer performs their first cart-related action (retrieving the cart or adding an item), an active cart is implicitly created. Customers do not need to call a separate endpoint to create their cart.
- **Live Price and Subtotal Computations**: Subtotals and product unit prices are evaluated dynamically on read from current catalog prices. They are not stored/cached in the cart database record. This ensures the cart always reflects current store pricing.
- **Advisory Stock Checking**: Stock level validation performed at cart add/update is soft/advisory. If stock is unavailable, an `availabilityWarning` is returned, but the item is still allowed in the cart. Hard inventory checks and lock reservations are deferred to Phase 6 checkout.
- **Zero Quantity Item Removal**: Mutating a cart item quantity to `0` is equivalent to deletion. The line item is completely removed from the cart database record.
- **Persistent Empty Carts**: Clearing a cart or removing the last item empties the items list but preserves the active cart record, allowing for simpler state tracking for future checkouts.

### Phase 6 Assumptions
- **Pessimistic Lock Cart Resolution**: We resolve the customer's active cart with a `PESSIMISTIC_WRITE` lock to block rapid duplicate checkouts of the same cart from creating double orders.
- **Isolation of Lock Retries**: Stock reservation is wrapped in `REQUIRES_NEW` propagation. This ensures retry attempts due to database contention are isolated and do not trigger a costly rollback/retry of the entire checkout transaction.
- **Explicit Compensation for Stock**: Since reservation transactions commit independently, we explicitly catch payment failures or other checkout errors and run compensation logic to release reserved stock.
- **Simulated Payment Gateway**: Payments are processed synchronously via a configurable mock service. Successful payments advance order status to `PLACED`, leaving the advance to `CONFIRMED` to the downstream fulfillment state machine (Phase 8).
- **Tax Rate Placeholder**: Flat 10% tax rate is applied to the subtotal. Real discount logic is deferred to Phase 9.

### Phase 8 Assumptions
- **404 vs 403 on Cross-Customer Detail Views**: To prevent information leakage (e.g., confirming the existence of a valid order ID to non-owners), we return `404 Not Found` (instead of `403 Forbidden`) if a customer attempts to query or cancel an order that belongs to another customer.
- **ADMIN Override Power**: Administrators are given broader capabilities to cancel orders at any stage prior to delivery (`PLACED`, `CONFIRMED`, `PACKED`, `SHIPPED`). Doing so releases reserved warehouse stock. Warehouse staff cannot perform cancellations.
- **Selective customerId Serialization**: The `customerId` field in `OrderSummaryResponse` is serialized only for staff and administrator queries. In order summary lists returned to customers, the `customerId` is set to `null` and excluded in output serialization via Jackson `@JsonInclude(NON_NULL)`.




