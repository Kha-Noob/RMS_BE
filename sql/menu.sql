-- ═══════════════════════════════════════════════════════════════════════════════
-- Menu Module — Restaurant dishes/drinks sold in POS
-- Separate from Inventory (ingredients/stock/procurement)
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── Table: menu_categories ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS menu_categories (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(255),
    display_order   INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── Table: menu_items ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS menu_items (
    id              BIGSERIAL       PRIMARY KEY,
    category_id     BIGINT          NOT NULL REFERENCES menu_categories(id) ON DELETE RESTRICT,
    name            VARCHAR(150)    NOT NULL,
    description     TEXT,
    price_vnd       DECIMAL(12,2)   NOT NULL CHECK (price_vnd >= 0),
    image_url       VARCHAR(500),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_menu_items_category_id ON menu_items(category_id);
CREATE INDEX IF NOT EXISTS idx_menu_items_status ON menu_items(status);

-- ─── Table: menu_item_variants ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS menu_item_variants (
    id              BIGSERIAL       PRIMARY KEY,
    menu_item_id    BIGINT          NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name            VARCHAR(100)    NOT NULL,
    price_vnd       DECIMAL(12,2)   NOT NULL CHECK (price_vnd >= 0),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_menu_item_variants_menu_item_id ON menu_item_variants(menu_item_id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Seed: Categories
-- ═══════════════════════════════════════════════════════════════════════════════
INSERT INTO menu_categories (id, name, description, display_order, active, created_at, updated_at) VALUES
(1, 'Món chính',        'Các món ăn chính trong thực đơn',       1, TRUE, NOW(), NOW()),
(2, 'Món phụ',          'Các món ăn phụ, khai vị, ăn kèm',      2, TRUE, NOW(), NOW()),
(3, 'Đồ uống',          'Đồ uống, trà, cà phê, bia, nước ngọt',  3, TRUE, NOW(), NOW()),
(4, 'Tráng miệng',      'Kem, chè, bánh ngọt tráng miệng',      4, TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval('menu_categories_id_seq', (SELECT COALESCE(MAX(id), 0) FROM menu_categories));

-- ═══════════════════════════════════════════════════════════════════════════════
-- Seed: Menu Items
-- ═══════════════════════════════════════════════════════════════════════════════
INSERT INTO menu_items (id, category_id, name, description, price_vnd, status, created_at, updated_at) VALUES
-- ── Món chính ──
(1,  1, 'Phở Bò',               'Phở truyền thống với thịt bò tái, nạm, gầu, hành lá và rau thơm',           85000.00,  'ACTIVE', NOW(), NOW()),
(2,  1, 'Bún Chả Hà Nội',       'Bún chả grill than hoa, chả thịt nướng, nước mắm chua ngọt, rau sống',      75000.00,  'ACTIVE', NOW(), NOW()),
(3,  1, 'Cơm Tấm Sườn Nướng',   'Cơm tấm sườn nướng mật ong, bì, chả, đồ chua và nước mắm pha',            80000.00,  'ACTIVE', NOW(), NOW()),
(4,  1, 'Bánh Xèo',             'Bánh xèo giòn rụm, nhân tôm thịt, giá đỗ, ăn kèm rau sống và nước chấm',    65000.00,  'ACTIVE', NOW(), NOW()),
(5,  1, 'Bún Bò Huế',           'Bún bò Huế cay nồng, thịt bò, chả lụa, mọc, rau thơm',                      80000.00,  'ACTIVE', NOW(), NOW()),
(6,  1, 'Cá Kho Tộ',            'Cá basa kho tộ caramel, thịt ba chỉ, ăn với cơm trắng',                     120000.00, 'ACTIVE', NOW(), NOW()),
(7,  1, 'Gà Nướng Sả Ớt',      'Gà nguyên con nướng sả ớt, cơm lam, rau sống',                              180000.00, 'ACTIVE', NOW(), NOW()),
(8,  1, 'Lẩu Thái Hải Sản',    'Lẩu Thái chua cay, tôm, mực, nghêu, sò, đậu hũ, rau nhúng',                 350000.00, 'ACTIVE', NOW(), NOW()),
-- ── Món phụ ──
(9,  2, 'Gỏi Cuốn',             'Gỏi cuốn tôm thịt, bún, rau sống, nước chấm',                                 45000.00,  'ACTIVE', NOW(), NOW()),
(10, 2, 'Nem Rán',              'Nem rán giòn, nhân thịt băm, miến, nấm mèo, chấm nước mắm chua ngọt',        55000.00,  'ACTIVE', NOW(), NOW()),
(11, 2, 'Chả Giò',              'Chả giò chiên giòn, nhân tôm thịt, ăn kèm rau sống',                         50000.00,  'ACTIVE', NOW(), NOW()),
(12, 2, 'Rau Muống Xào Tỏi',    'Rau muống xào tỏi, món ăn kèm thanh mát',                                   40000.00,  'ACTIVE', NOW(), NOW()),
(13, 2, 'Đậu Hũ Chiên Sả Ớt',  'Đậu hũ chiên giòn, sốt sả ớt cay nồng',                                     45000.00,  'ACTIVE', NOW(), NOW()),
-- ── Đồ uống ──
(14, 3, 'Trà Đá',               'Trà đá truyền thống, refreshing sau bữa ăn',                                  15000.00,  'ACTIVE', NOW(), NOW()),
(15, 3, 'Cà Phê Sữa Đá',       'Cà phê Việt Nam pha phin, sữa đặc, đá',                                      35000.00,  'ACTIVE', NOW(), NOW()),
(16, 3, 'Nước Chanh Tươi',      'Nước chanh tươi vắt, đường, đá, giải khát',                                    25000.00,  'ACTIVE', NOW(), NOW()),
(17, 3, 'Bia Sài Gòn',          'Bia Sài Gòn đặc biệt, 330ml',                                                25000.00,  'ACTIVE', NOW(), NOW()),
-- ── Tráng miệng ──
(18, 4, 'Chè Ba Màu',           'Chè ba màu: đậu xanh, đậu đỏ, thạch, nước cốt dừa',                         35000.00,  'ACTIVE', NOW(), NOW()),
(19, 4, 'Kem Chuối',            'Kem chuối dừa, đậu phộng rang, topping socola',                               30000.00,  'ACTIVE', NOW(), NOW()),
(20, 4, 'Bánh Flan Caramel',    'Bánh flan mềm mịn, caramel ngọt thanh',                                      25000.00,  'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval('menu_items_id_seq', (SELECT COALESCE(MAX(id), 0) FROM menu_items));

-- ═══════════════════════════════════════════════════════════════════════════════
-- Seed: Menu Item Variants
-- ═══════════════════════════════════════════════════════════════════════════════
INSERT INTO menu_item_variants (id, menu_item_id, name, price_vnd, active, created_at, updated_at) VALUES
-- Phở Bò variants
(1,  1, 'Phở tái',          85000.00,  TRUE, NOW(), NOW()),
(2,  1, 'Phở chín',         90000.00,  TRUE, NOW(), NOW()),
(3,  1, 'Phở gầu',          95000.00,  TRUE, NOW(), NOW()),
-- Cơm Tấm Sườn Nướng variants
(4,  3, 'Sườn nướng',       80000.00,  TRUE, NOW(), NOW()),
(5,  3, 'Sườn + bì',        90000.00,  TRUE, NOW(), NOW()),
-- Gà Nướng Sả Ớt variants
(6,  7, 'Gà nướng sả ớt',   180000.00, TRUE, NOW(), NOW()),
(7,  7, 'Gà nướng muối ớt', 180000.00, TRUE, NOW(), NOW()),
-- Lẩu Thái Hải Sản variants
(8,  8, 'Lẩu cho 2 người',   350000.00, TRUE, NOW(), NOW()),
(9,  8, 'Lẩu cho 4 người',   550000.00, TRUE, NOW(), NOW()),
-- Cà Phê Sữa Đá variants
(10, 15, 'Cà phê đen',       25000.00,  TRUE, NOW(), NOW()),
(11, 15, 'Cà phê sữa',       35000.00,  TRUE, NOW(), NOW()),
-- Bia Sài Gòn variants
(12, 17, 'Bia Sài Gòn',      25000.00,  TRUE, NOW(), NOW()),
(13, 17, 'Bia Hà Nội',       28000.00,  TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval('menu_item_variants_id_seq', (SELECT COALESCE(MAX(id), 0) FROM menu_item_variants));
