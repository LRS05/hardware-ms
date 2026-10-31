ALTER TABLE order_items
ADD CONSTRAINT ck_order_items_quantity_positive
CHECK (quantity > 0);

ALTER TABLE order_items
ADD CONSTRAINT uq_order_items_product
UNIQUE (order_id, product_id);

CREATE UNIQUE INDEX uq_orders_open_user
ON orders (user_id)
WHERE status IN ('ACTIVE', 'PENDING');
