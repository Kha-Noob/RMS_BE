-- Payroll Runs SQL data
INSERT INTO payroll_runs (id, period, run_by, run_date) VALUES
(1, '2026-05', 'Admin', '2026-05-11'),
(2, '2026-05', 'Admin', '2026-05-12'),
(3, '2026-05', 'Admin', '2026-05-13'),
(4, '2026-05', 'Admin', '2026-05-14'),
(5, '2026-05', 'Admin', '2026-05-15')
ON CONFLICT (id) DO NOTHING;
