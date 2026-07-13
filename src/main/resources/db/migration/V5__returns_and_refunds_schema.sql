ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP NULL;
ALTER TABLE return_requests ADD COLUMN quantity INT NOT NULL DEFAULT 1;
ALTER TABLE return_requests ADD COLUMN refund_amount DECIMAL(38, 2) NULL;
ALTER TABLE return_requests ADD COLUMN rejection_reason VARCHAR(500) NULL;

-- Recreate foreign key constraints with ON DELETE CASCADE to allow automatic cleanup in integration tests
ALTER TABLE return_requests DROP FOREIGN KEY fk_return_order;
ALTER TABLE return_requests DROP FOREIGN KEY fk_return_order_item;

ALTER TABLE return_requests ADD CONSTRAINT fk_return_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
ALTER TABLE return_requests ADD CONSTRAINT fk_return_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE;
