# Phase 10: Returns & Refunds Dev Notes

## Refund Calculation Formula Derivation

During checkout, an order computes totals as follows:
1. $\text{Subtotal} = \sum (\text{item.unitPrice} \times \text{item.quantity})$
2. $\text{DiscountAmount} = \text{calculated order-wide discount}$
3. $\text{PostDiscountSubtotal} = \text{Subtotal} - \text{DiscountAmount}$
4. $\text{TaxAmount} = \text{TaxRate} \times \text{PostDiscountSubtotal}$
5. $\text{TotalAmount} = \text{PostDiscountSubtotal} + \text{TaxAmount}$

When returning a specific quantity $Q_{ret}$ of a target item with unit price $I_{price}$ and originally ordered quantity $I_{qty}$:
- The item's pre-discount proportion of the original order's pre-discount subtotal is:
  $$\text{Proportion} = \frac{I_{price} \times Q_{ret}}{\text{Subtotal}}$$
- Using this proportion, the refund amount should reflect the item's share of the post-discount subtotal and the corresponding tax:
  $$\text{Refund Amount} = (\text{Subtotal} - \text{DiscountAmount}) \times \text{Proportion} + \text{TaxAmount} \times \text{Proportion}$$
  $$\text{Refund Amount} = (\text{Subtotal} - \text{DiscountAmount} + \text{TaxAmount}) \times \text{Proportion}$$
  $$\text{Refund Amount} = \text{TotalAmount} \times \frac{I_{price} \times Q_{ret}}{\text{Subtotal}}$$

This formula ensures the refund is perfectly proportional, incorporating both order-wide discounts and tax in a single step without having to re-run complex tax or discount calculators.

### Worked Example
Suppose an order is placed:
- Item A: unit price = \$10.00, quantity = 2 (subtotal = \$20.00)
- Item B: unit price = \$30.00, quantity = 1 (subtotal = \$30.00)
- **Order Subtotal** = \$50.00
- **Discount** = \$5.00 (10% flat or percentage)
- **Post-Discount Subtotal** = \$45.00
- **Tax** = \$3.60 (8% of \$45.00)
- **Order Total** = \$48.60

Now, the customer requests to return **1 unit** of Item A ($Q_{ret} = 1$):
- Pre-discount value of returned item = $\$10.00 \times 1 = \$10.00$.
- Proportion of order = $\frac{\$10.00}{\$50.00} = 0.2$ (20%).
- Proportional share of post-discount subtotal = $(\$50.00 - \$5.00) \times 0.2 = \$9.00$.
- Proportional share of tax = $\$3.60 \times 0.2 = \$0.72$.
- **Refund Amount** = $\$9.00 + \$0.72 = \$9.72$.

Using the derived formula:
$$\text{Refund Amount} = \$48.60 \times \frac{\$10.00 \times 1}{\$50.00} = \$48.60 \times 0.2 = \$9.72$$
This matches the calculation exactly.

---

## Partial-Failure Handling Reasoning

The return approval workflow involves:
1. Marking the request state as `APPROVED` (internal database update).
2. Refunding the amount through a third-party gateway simulated by `PaymentService`.
3. Adjusting (increasing) inventory on-hand in the warehouse using `InventoryService`.

Since multiple external systems and data constraints are involved, failure can occur partway (e.g. gateway timeouts, database locks, or inventory constraints).

### Chosen Approach: Transaction Boundary Rollback
We wrap the `decideReturn` execution in Spring's `@Transactional` boundary.
- If payment simulation fails (throwing `PaymentFailedException`), or if the inventory restock fails, the transaction is completely rolled back.
- The return request reverts to `REQUESTED` status, and any database writes are undone.
- The client receives a structured error response (e.g. `402 Payment Required` or `409 Conflict`), allowing the staff member to address the underlying issue and safely retry.
- This design is robust, prevents database-payment-inventory drift, and avoids the complexity of distributed transaction patterns (Sagas) while satisfying the business needs.
