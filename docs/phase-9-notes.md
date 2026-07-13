# Phase 9 Dev Notes: Discount Codes & Tax Calculation

## Concurrency, Transaction, & Compensation Boundaries Audit

As part of Phase 9, we integrated discount code management and proper tax calculation into the checkout flow. A primary constraint of this phase was to ensure that the core concurrency guarantees, retry transaction boundaries, and compensation logic implemented in Phase 6 were **fully preserved and untouched**.

### Untouched Code & Logic (Phase 6 Concurrency & Robustness)
- **Cart Locking**: The fetching of the active cart using `cartRepository.findActiveCartWithWriteLock(customerId)` remains unchanged, maintaining the pessimistic write lock preventing concurrent checkouts for the same user.
- **Checked Out State Transition**: The cart status transition to `CHECKED_OUT` (to prevent double-submission) remains immediately after fetching the cart.
- **Inventory Reservation**: The loop calling `inventoryReservationService.reserveProductStock(...)` (which runs in `REQUIRES_NEW` transactions) remains fully intact.
- **Payment Charges**: `paymentService.charge(order)` is called inside a nested try-catch block as before.
- **Compensation / Rollback Logic**: The `catch (Exception ex)` block on checkout failure remains untouched. It iterates through all successfully reserved inventory items and calls `inventoryReservationService.releaseReservation(...)` to prevent stock leaks, then rethrows the exception to roll back the order and payment transaction.
- **Event Publishing**: Publishing the `OrderPlacedEvent` on successful order placement remains intact.

### Modified Code & Logic (Additive Totals-Computation Only)
- **Dependency Injection**: Added final fields `discountValidationService` and `taxCalculationService` (injected via Lombok's `@RequiredArgsConstructor`). Removed the unused `@Value("${app.tax-rate}")` placeholder.
- **Totals Computation**:
  - We calculate the raw subtotal from cart items.
  - If a discount code is provided, we validate it using `discountValidationService.validate(code, subtotal, customerId)`.
  - Discount amount is computed based on type (`PERCENTAGE` or `FLAT`), and is capped at the subtotal to prevent negative totals.
  - Tax is computed on the **post-discount** amount using `taxCalculationService.calculateTax(...)`.
  - Order total is calculated as: `total = subtotal - discountAmount + taxAmount`.
  - The `Order` builder now populates the correct `taxAmount`, `discountAmount`, `totalAmount`, and the newly added `discountCode`.

---

## File and Line References (Before vs After)

| Component / Logic | Old Location (CheckoutServiceImpl.java) | New Location (CheckoutServiceImpl.java) | Status / Notes |
| :--- | :--- | :--- | :--- |
| Inject fields | Lines 28-36 | Lines 28-38 | **Untouched** fields. Added two new service interfaces. |
| Tax rate property | Line 38-39 | *Removed* | **Replaced** with `TaxCalculationService`. |
| Active cart lock & check | Lines 60-77 | Lines 57-74 | **Untouched** |
| Inventory reservation loop | Lines 82-91 | Lines 79-88 | **Untouched** |
| Totals computation math | Lines 93-103 | Lines 90-111 | **Modified** to apply discount validation, cap amount, and post-discount tax. |
| Order persistence | Lines 104-114 | Lines 112-124 | **Modified** to set `discountCode`. |
| Payment charge & ref | Lines 115-139 | Lines 125-149 | **Untouched** |
| Compensation rollback loop | Lines 166-178 | Lines 176-188 | **Untouched** |

This partition confirms that all concurrency and transactional properties established in Phase 6 have been preserved.
