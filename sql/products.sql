-- Products SQL data (Recreate Table DDL + Inserts)
-- Recreate Table containing category_id column
DROP TABLE IF EXISTS products CASCADE;
CREATE TABLE products (
    id bigserial PRIMARY KEY,
    name varchar(255) NOT NULL,
    description text,
    image_path varchar(255),
    category_id bigint REFERENCES categories(id),
    is_active boolean DEFAULT true NOT NULL,
    tenant_id varchar(36)
);

INSERT INTO products (id, name, description, image_path, category_id, is_active, tenant_id) VALUES
(1, 'Cơm Tấm Sườn Bì Chả', 'Cơm tấm đặc sản miền Nam ăn kèm sườn nướng mật ong ngon tuyệt cú mèo.', 'https://images.unsplash.com/photo-1509722747041-616f39b57569?w=500', 1, true, 'tenant-1'),
(2, 'Bún Chả Hà Nội', 'Bún chả nướng than hoa thơm nức mũi ăn kèm nước chấm đu đủ xanh chua ngọt.', 'https://images.unsplash.com/photo-1596797038530-2c107229654b?w=500', 1, true, 'tenant-1'),
(3, 'Gỏi Cuốn Tôm Thịt', 'Gỏi cuốn nhân tôm tươi và thịt heo, hành hẹ ăn cùng tương đậu phộng.', 'https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=500', 1, true, 'tenant-1'),
(4, 'Bánh Mì Đặc Biệt', 'Bánh mì giòn rụm kẹp pate gan, chả lụa, thịt xá xíu, sốt bơ trứng.', 'https://images.unsplash.com/photo-1509722747041-616f39b57569?w=500', 1, true, 'tenant-1'),
(5, 'Cơm Chiên Dương Châu', 'Cơm chiên lạp xưởng, hạt sen, tôm tươi và rau củ thái lựu giòn ngọt.', 'https://images.unsplash.com/photo-1608897013039-887f21d8c804?w=500', 1, true, 'tenant-1'),
(6, 'Cà Phê Sữa Đá', 'Cà phê Robusta Đắk Lắk pha phin truyền thống thơm lừng quyện cùng sữa đặc.', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500', 2, true, 'tenant-1'),
(7, 'Trà Đào Cam Sả', 'Trà đào thơm nức quyện sả tươi và lát cam vàng thanh lọc cơ thể.', 'https://images.unsplash.com/photo-1497515114629-f71d768fd07c?w=500', 2, true, 'tenant-1'),
(8, 'Sinh Tố Bơ Sáp', 'Sinh tố bơ Đắk Nông dẻo mịn béo ngậy xay cùng sữa tươi hạt điều.', 'https://images.unsplash.com/photo-1553530666-ba11a7da3888?w=500', 2, true, 'tenant-1'),
(9, 'Nước Ép Dưa Hấu', 'Nước ép dưa hấu tươi mát nguyên chất giải nhiệt cho những ngày nóng bức.', 'https://images.unsplash.com/photo-1589733901241-5e5148685df5?w=500', 2, true, 'tenant-1'),
(10, 'Trà Sữa Matcha Trân Châu', 'Trà sữa Matcha Nhật Bản béo ngậy kèm trân châu đen dai giòn ngọt lịm.', 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=500', 2, true, 'tenant-1'),
(11, 'Chè Khúc Bạch', 'Chè khúc bạch thanh mát với thạch phô mai dẻo bùi, nhãn tươi và hạnh nhân.', 'https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?w=500', 3, true, 'tenant-1'),
(12, 'Bánh Flan Ca Cao', 'Bánh caramen mịn màng ăn kèm đá bào và sốt cacao thơm đắng nhẹ.', 'https://images.unsplash.com/photo-1541795795328-f073b763494e?w=500', 3, true, 'tenant-1'),
(13, 'Rau Câu Dừa Trái', 'Rau câu nước dừa xiêm thanh ngọt tự nhiên mát lịm giòn giòn.', 'https://images.unsplash.com/photo-1551024601-bec78aea704b?w=500', 3, true, 'tenant-1'),
(14, 'Chè Thái Sầu Riêng', 'Chè Thái trái cây thập cẩm kèm múi sầu riêng RI6 béo ngậy thơm phức.', 'https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?w=500', 3, true, 'tenant-1'),
(15, 'Khoai Tây Chiên Bơ Tỏi', 'Khoai tây chiên giòn lắc bơ tỏi và sốt phô mai cay.', 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=500', 4, true, 'tenant-1'),
(16, 'Súp Măng Tây Cua', 'Súp cua biển thịt băm nấu cùng măng tây trắng thanh mát, bổ dưỡng.', 'https://images.unsplash.com/photo-1547592165-e1d17fed6005?w=500', 4, true, 'tenant-1'),
(17, 'Salad Ức Gà Áp Chảo', 'Rau xà lách hỗn hợp kèm ức gà áp chảo và sốt dầu giấm balsamic.', 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500', 4, true, 'tenant-1'),
(18, 'Phở Bò Đặc Biệt', 'Phở bò chín, tái, gầu, gân, bò viên nước dùng ngọt lịm hầm từ xương bò 24h.', 'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=500', 5, true, 'tenant-1'),
(19, 'Bún Bò Huế Cổ Đô', 'Bún bò huế đậm đà hương mắm ruốc, sả, giò heo, chả cua, thịt nạm.', 'https://images.unsplash.com/photo-1625398407796-82650a8c135f?w=500', 5, true, 'tenant-1'),
(20, 'Mì Quảng Tôm Thịt', 'Mì quảng tôm thịt heo, trứng cút ăn kèm bánh đa nướng giòn rụm.', 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=500', 5, true, 'tenant-1'),
(21, 'Bánh Canh Cua Bột Gạo', 'Bánh canh cua biển đặc sệt gạch cua thơm béo ngọt lịm.', 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=500', 5, true, 'tenant-1'),
(22, 'Sườn Cừu Nướng Sốt Thảo Mộc', 'Sườn cừu áp chảo nướng sốt hương thảo rosemary kèm khoai tây nghiền.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 6, true, 'tenant-1'),
(23, 'Bò Mỹ Nướng Đá', 'Bò Mỹ nướng trực tiếp trên phiến đá nóng kèm sốt tiêu xanh Phú Quốc.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 6, true, 'tenant-1'),
(24, 'Gà Nướng Mắc Khén', 'Gà ta nướng lá chanh mắc khén Tây Bắc thơm lừng cay nhẹ.', 'https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=500', 6, true, 'tenant-1'),
(25, 'Lẩu Thái Hải Sản Cay', 'Nồi lẩu Thái chua cay thơm cốt dừa kèm tôm hùm, mực, ngao, nấm kim châm.', 'https://images.unsplash.com/photo-1547928576-a4a3323dce9d?w=500', 7, true, 'tenant-1'),
(26, 'Lẩu Riêu Cua Sườn Sụn', 'Lẩu riêu cua đồng nguyên chất sườn sụn, bắp bò, giò tai, đậu hũ chiên.', 'https://images.unsplash.com/photo-1547928576-a4a3323dce9d?w=500', 7, true, 'tenant-1'),
(27, 'Lẩu Nấm Chim Bồ Câu', 'Lẩu nấm tươi thiên nhiên bổ dưỡng ăn cùng chim bồ câu hầm hạt sen.', 'https://images.unsplash.com/photo-1547928576-a4a3323dce9d?w=500', 7, true, 'tenant-1'),
(28, 'Nem Chua Rán Hà Nội', 'Nem chua bọc bột chiên xù giòn tan ăn kèm tương ớt cay nồng.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 8, true, 'tenant-1'),
(29, 'Bánh Tráng Trộn Tây Ninh', 'Bánh tráng trộn khô bò, mực xé, trứng cút, xoài xanh, rau răm, tắc tươi.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 8, true, 'tenant-1'),
(30, 'Chân Gà Sả Tắc Cay', 'Chân gà rút xương ngâm sả tắc ớt chua ngọt giòn sần sật cực bén.', 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500', 8, true, 'tenant-1')
ON CONFLICT (id) DO NOTHING;
