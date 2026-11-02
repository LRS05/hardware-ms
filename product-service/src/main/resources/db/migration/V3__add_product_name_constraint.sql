ALTER TABLE products
ADD CONSTRAINT uq_products_name
UNIQUE (name);
