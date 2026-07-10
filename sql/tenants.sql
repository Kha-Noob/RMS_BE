-- Tenants SQL data
INSERT INTO tenants (tenant_id, name, domain, is_active, is_using_system_web, bank_name, bank_account_no, bank_account_name, bank_branch) VALUES
('tenant-1', 'LiteFlow Restaurant Chain', 'liteflow.com', true, true, 'Vietcombank', '1012938475', 'LITEFLOW RESTAURANT GROUP', 'Hanoi Branch'),
('tenant-2', 'External Ads Restaurant', 'externalads.com', true, false, 'Techcombank', '1902837465', 'EXTERNAL ADS RESTAURANT CO', 'HCM City Branch'),
('tenant-3', 'Cooperator 3 Chain', 'cooperator3.com', true, true, 'Vietinbank', '1029384756', 'COOPERATOR 3 RESTAURANT GROUP', 'Hanoi Branch')
ON CONFLICT (tenant_id) DO NOTHING;
