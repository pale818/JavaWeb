-- Categories
INSERT INTO categories (name, description) VALUES ('Electronics', 'Gadgets and devices');
INSERT INTO categories (name, description) VALUES ('Books', 'Fiction and non-fiction');
INSERT INTO categories (name, description) VALUES ('Clothing', 'Apparel and accessories');

-- Products
INSERT INTO products (name, description, price, stock_quantity, category_id) VALUES ('Smartphone', 'Latest model with 5G', 699.99, 50, 1);
INSERT INTO products (name, description, price, stock_quantity, category_id) VALUES ('Laptop', 'High performance for gaming', 1200.00, 20, 1);
INSERT INTO products (name, description, price, stock_quantity, category_id) VALUES ('Java Programming', 'Learn Spring Boot 3', 45.50, 100, 2);
INSERT INTO products (name, description, price, stock_quantity, category_id) VALUES ('T-Shirt', '100% Cotton', 19.99, 200, 3);

