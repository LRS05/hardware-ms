INSERT INTO products (id, status, category, sku, name, description, price, stock, created_at, updated_at)
VALUES
    (1, 'ACTIVE', 'GRAPHIC_CARD', 'GV-N108T GAMING-OC-11G', 'NVIDIA GTX 1080 TI', 'This product NVIDIA GTX 1080 TI is incredible', 1000.00, 50, '2026-10-23 10:05:00', '2026-10-23 10:05:00'),
    (2, 'ACTIVE', 'PROCESSOR', '77490', 'Intel Core i3-4170', 'This product Intel Core i3-4170 is incredible', 200.00, 20, '2026-10-23 10:05:00', '2026-10-23 10:05:00');

ALTER TABLE products ALTER COLUMN id RESTART WITH 3;
