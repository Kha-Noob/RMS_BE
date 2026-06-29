-- Tenants SQL data
INSERT INTO tenants (tenant_id, name, domain, is_active, is_using_system_web) VALUES
('tenant-1', 'LiteFlow Restaurant Chain', 'liteflow.com', true, true),
('tenant-2', 'External Ads Restaurant', 'externalads.com', true, false)
ON CONFLICT (tenant_id) DO NOTHING;
