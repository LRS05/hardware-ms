ALTER TABLE products
ADD CONSTRAINT ck_products_stock_nonnegative
CHECK (stock >= 0);
