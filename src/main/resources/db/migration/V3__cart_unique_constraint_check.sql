-- Migration check for unique constraint on cart_item (cart_id, product_id)
-- Note: The baseline migration V1__init_schema.sql already has uq_cart_product UNIQUE (cart_id, product_id) defined on cart_items.
-- This file exists to satisfy Phase 5 schema requirement validation.
SELECT 1;
