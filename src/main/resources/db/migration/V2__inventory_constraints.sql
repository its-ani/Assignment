ALTER TABLE inventory_items ADD CONSTRAINT chk_quantity_on_hand CHECK (quantity_on_hand >= 0);
ALTER TABLE inventory_items ADD CONSTRAINT chk_quantity_reserved CHECK (quantity_reserved >= 0);
ALTER TABLE inventory_items ADD CONSTRAINT chk_quantity_reserved_limit CHECK (quantity_reserved <= quantity_on_hand);
