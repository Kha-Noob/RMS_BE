-- Tenants SQL data
INSERT INTO tenants (tenant_id, name, domain, is_active) VALUES
('tenant-1', 'LiteFlow Restaurant Chain', 'liteflow.com', true)
ON CONFLICT (tenant_id) DO NOTHING;
