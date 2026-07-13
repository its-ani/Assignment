# Phase 3 Development Notes: Catalog Management

This document details the architectural decisions, design choices, and implementation notes for Phase 3.

## Architectural & Design Choices

### 1. Specification vs. Derived-Query Choice for Product Search
For the product search API, we had to support many combinable and optional filters (e.g., keyword search across name and description, price range bounds, category filtering with subcategories, and active-only flag).
- **Why we chose JPA Specifications**:
  Derived query methods (e.g., `findByNameContainingAndPriceBetween...`) suffer from combinatorial explosion when parameters are optional. If we have 5 optional filters, we would have to implement up to $2^5 = 32$ different query methods.
  `Specification` allows us to programmatically construct queries at runtime using a clean, reusable builder (`ProductSpecification.java`), combining predicates only when arguments are actually provided. It is easily extensible when new criteria are added in later phases.

### 2. Category Nesting & Cycle Detection
Categories support nesting via a self-referencing relationship (`parentCategory`). To prevent cycle loops (which would break tree structures and cause stack overflows in recursive operations), we enforce a strict ancestor check during write operations:
- When a category with ID $C$ is created or updated with a proposed parent ID $P$, the system:
  1. Verifies that $C \neq P$ (a category cannot be its own parent).
  2. Traverses the parent chain starting from $P$ upward to the root.
  3. If $C$ is encountered as an ancestor of $P$, the operation is rejected immediately (returns `400 Bad Request`).
This check is simple, relies on cheap database reads (typically $\leq 3$ levels deep), and prevents corrupted hierarchy trees.

### 3. Soft-Delete Tradeoffs for Products
Instead of physically deleting product rows from the database (hard delete), products are "soft-deleted" by setting `active = false` inside `ProductService.java`.
- **Tradeoffs Considered**:
  - *Hard Delete*: Simple database maintenance. However, it breaks referential integrity if the product is ordered by a customer (we would have orders pointing to non-existent product IDs or would be forced to block product deletion if it has orders).
  - *Soft Delete*: Maintains referential integrity for future Order Items, Audit Logs, and Carts. However, it requires all search and retrieval queries to filter out inactive items.
- **Access Rules Implementation**:
  - Public searches (`GET /api/v1/products`) filter out inactive products by default.
  - Public/Customer direct lookups (`GET /api/v1/products/{id}`) of inactive products return `404 Not Found`.
  - Admins and Warehouse Staff can search inactive products (via `activeOnly=false`) and directly fetch inactive products via ID. This allows them to manage inventory, view history, or reactivate items.

### 4. Category Deletion Safety Guard
We block category deletion (return `409 Conflict`) if:
1. It has associated child categories (subcategories).
2. It has associated products.
This choice prioritizes safety over developer convenience (such as cascading delete or `ON DELETE SET NULL`), preventing accidental orphaned products or cascading deletions of entire category trees.
