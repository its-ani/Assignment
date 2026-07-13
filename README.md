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
- **Phase 6**: Order Placement & Checkout Transaction (Pending)
- **Phase 7**: State Machine for Order Lifecycle (Pending)
- **Phase 8**: Returns Processing & Refunds (Pending)
- **Phase 9**: Payment Integration & Audit Logs (Pending)
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

