-- Roles SQL data
INSERT INTO roles (id, name) VALUES
(1, 'ADMIN'),
(2, 'MANAGER'),
(3, 'CASHIER'),
(4, 'KITCHEN'),
(5, 'HR'),
(6, 'PROCUREMENT'),
(7, 'WAREHOUSE'),
(8, 'EMPLOYEE')
ON CONFLICT (id) DO NOTHING;
