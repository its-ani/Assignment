# Phase 5 Developer Notes: Customer Cart Management

## Soft Advisory Stock Validation vs. Phase 6 Lock Guarantee

### Current Advisory Design
In Phase 5, we introduce a soft inventory availability check at the time of adding or modifying items in the cart:
- If a customer adds a quantity that exceeds the total available stock in all warehouses (or if the item is entirely out of stock), the cart operation is **not rejected**.
- Instead, the response returns the item successfully but attaches an `availabilityWarning` message (e.g. `"Only X available in stock"`, `"Product is out of stock"`).
- This is designed to give rapid feedback to customers on current inventory levels while they are shopping.

### Deliberate Race Condition
This introduces a deliberate, documented race condition:
- Because the inventory is not locked or reserved at the cart stage, other customers can purchase the stock before this customer completes their checkout.
- Similarly, admin adjustments could reduce the stock in the background.
- Therefore, the stock warning is **advisory only**.

### Looking Ahead to Phase 6 (Checkout)
Phase 6 (Checkout & Order Placement) will be the authoritative gatekeeper for database and transactional correctness:
- When a customer triggers checkout, the system will start an atomic transaction.
- It will acquire pessimistic or optimistic database locks on the inventory records.
- It will re-validate the current inventory authoritatively.
- If stock is insufficient at checkout, the checkout process will fail hard and reject order creation.
- If stock is sufficient, the checkout process will atomically deduct/reserve the stock, preventing other transactions from double-consuming it.

## Key Design & Implementation Decisions

- **Security & Scope Restriction**: Cart endpoints resolve the customer's ID purely from their JWT security context (`SecurityUtils.getCurrentUserId()`). There is no `cartId` exposed in request paths or payloads. This completely eliminates ID-harvesting vulnerability vectors.
- **Implicit Cart Context**: Carts are created transparently on the customer's first cart action (either viewing the cart or adding an item). This eliminates unnecessary UI request cycles.
- **Dynamic Price Evaluation**: Product prices, line totals, and subtotals are computed dynamically on retrieve based on current prices in the catalog database. This guarantees that cart totals update immediately if an admin changes catalog prices, preventing stale-pricing fraud.
- **Zero-Quantity Action**: Updates setting an item's quantity to `0` are routed in the service layer to delete the corresponding `CartItem` record.
