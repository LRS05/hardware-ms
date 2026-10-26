INSERT INTO orders (id, user_id, status, total_price, created_at, updated_at)
VALUES
    (1, 1, 'COMPLETED', 2000.00, '2026-10-23 10:05:00', '2026-10-23 10:05:00'),
    (2, 1, 'ACTIVE', 2000.00, '2026-10-23 10:05:00', '2026-10-23 10:05:00');

INSERT INTO order_items (id, order_id, product_id, product_name, price, quantity)
VALUES
    (1, 1, 1, 'NVIDIA GTX 1080-TI', 2000.00, 2),
    (2, 2, 1, 'NVIDIA GTX 1080-TI', 2000.00, 2);

ALTER TABLE orders ALTER COLUMN id RESTART WITH 3;
ALTER TABLE order_items ALTER COLUMN id RESTART WITH 3;
