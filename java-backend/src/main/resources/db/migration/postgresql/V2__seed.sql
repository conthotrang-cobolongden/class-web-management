-- ============================================================
-- Seed data mau — PostgreSQL/Supabase (Flyway V2, ban port 1:1 tu
-- db/migration/sqlserver/V2__seed.sql: bo N'' prefix, BIT 0/1 -> false/true).
-- Cung so hieu V2 nen baseline-version=2 van bo qua tren DB da co du lieu.
-- password_hash la placeholder: app tu thay bang bcrypt("Admin@123")
-- khi khoi dong (SeedPasswordFixer). Mat khau mac dinh: Admin@123
-- ============================================================

-- ---------- users: 1 admin + 2 GV chinh + 2 tro giang ----------
INSERT INTO users (full_name, email, password_hash, role, phone) VALUES
('Quản trị viên',   'admin@trungtam.vn',  '$2b$10$REPLACE_ME', 'admin',     '0900000001'),
('Nguyễn Văn Toán', 'toan.nv@trungtam.vn','$2b$10$REPLACE_ME', 'teacher',   '0900000002'),
('Trần Thị Hoa',    'hoa.tt@trungtam.vn', '$2b$10$REPLACE_ME', 'teacher',   '0900000003'),
('Lê Minh Trợ',     'tro.lm@trungtam.vn', '$2b$10$REPLACE_ME', 'assistant', '0900000004'),
('Phạm Thu Giang',  'giang.pt@trungtam.vn','$2b$10$REPLACE_ME','assistant', '0900000005');

-- ---------- classes: 4 lop, nam hoc 2026-2027, phi thang co dinh ----------
INSERT INTO classes (name, grade, school_year, start_date, end_date, default_monthly_fee, status) VALUES
('6A', 6, '2026-2027', '2026-08-03', '2027-05-30', 500000, 'active'),
('6B', 6, '2026-2027', '2026-08-03', '2027-05-30', 500000, 'active'),
('7A', 7, '2026-2027', '2026-08-03', '2027-05-30', 550000, 'active'),
('8A', 8, '2026-2027', '2026-08-03', '2027-05-30', 600000, 'active');

-- ---------- class_staff: moi lop 1 GV chinh, lop 6A co 2 tro giang ----------
INSERT INTO class_staff (class_id, user_id, staff_role) VALUES
(1, 2, 'main'),      -- 6A: thay Toan
(1, 4, 'assistant'), -- 6A: tro giang Tro
(1, 5, 'assistant'), -- 6A: tro giang Giang
(2, 2, 'main'),      -- 6B: thay Toan
(2, 5, 'assistant'),
(3, 3, 'main'),      -- 7A: co Hoa
(3, 4, 'assistant'),
(4, 3, 'main');      -- 8A: co Hoa (chua co tro giang)

-- ---------- class_schedules: khung gio co dinh theo tuan ----------
-- day_of_week: 1=T2 2=T3 3=T4 4=T5 5=T6 6=T7 7=CN
INSERT INTO class_schedules (class_id, day_of_week, start_time, end_time) VALUES
(1, 2, '18:00', '20:00'), (1, 4, '18:00', '20:00'),  -- 6A: T3 + T5
(2, 3, '18:00', '20:00'), (2, 5, '18:00', '20:00'),  -- 6B: T4 + T6
(3, 2, '20:00', '22:00'), (3, 4, '20:00', '22:00'),  -- 7A: T3 + T5
(4, 6, '08:00', '10:00'), (4, 7, '08:00', '10:00');  -- 8A: T7 + CN

-- ---------- students: 10 hoc sinh ----------
INSERT INTO students (full_name, dob, gender, school_name, parent_name, parent_phone, enrollment_date, status, note) VALUES
('Nguyễn An Khang',  '2015-03-12', 'male',   'THCS Lê Quý Đôn',    'Nguyễn Văn Bình', '0911000001', '2026-08-03', 'active', NULL),
('Trần Bảo Ngọc',    '2015-07-25', 'female', 'THCS Lê Quý Đôn',    'Trần Văn Cường',  '0911000002', '2026-08-03', 'active', 'Hay quên vở bài tập'),
('Lê Gia Hân',       '2015-01-08', 'female', 'THCS Nguyễn Du',     'Lê Thị Dung',     '0911000003', '2026-08-03', 'active', NULL),
('Phạm Đức Duy',     '2015-11-30', 'male',   'THCS Nguyễn Du',     'Phạm Văn Em',     '0911000004', '2026-08-10', 'active', NULL),
('Hoàng Minh Quân',  '2015-05-17', 'male',   'THCS Trần Phú',      'Hoàng Văn Phúc',  '0911000005', '2026-08-03', 'active', NULL),
('Vũ Ngọc Ánh',      '2015-09-02', 'female', 'THCS Trần Phú',      'Vũ Thị Quỳnh',    '0911000006', '2026-08-03', 'active', NULL),
('Đặng Thu Trang',   '2014-04-21', 'female', 'THCS Lê Quý Đôn',    'Đặng Văn Sơn',    '0911000007', '2026-08-03', 'active', NULL),
('Bùi Quang Huy',    '2014-12-05', 'male',   'THCS Nguyễn Du',     'Bùi Văn Tùng',    '0911000008', '2026-08-03', 'active', NULL),
('Đỗ Khánh Linh',    '2013-06-14', 'female', 'THCS Trần Phú',      'Đỗ Văn Út',       '0911000009', '2026-08-03', 'active', NULL),
('Ngô Tuấn Kiệt',    '2013-10-28', 'male',   'THCS Lê Quý Đôn',    'Ngô Văn Vinh',    '0911000010', '2026-08-03', 'paused', 'Tạm nghỉ từ 15/09 theo phụ huynh báo');

-- ---------- enrollments ----------
-- HS 1-3 hoc 6A; HS 4 tung hoc 6B roi CHUYEN sang 6A (giu lich su); HS 5-6 hoc 6B;
-- HS 7-8 hoc 7A; HS 9-10 hoc 8A. HS 3 duoc uu dai phi rieng 450k.
INSERT INTO enrollments (student_id, class_id, joined_at, left_at, leave_reason, transferred_to_class_id, custom_monthly_fee, created_by) VALUES
(1, 1, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(2, 1, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(3, 1, '2026-08-03', NULL, NULL, NULL, 450000, 1),
(4, 2, '2026-08-10', '2026-09-05', 'transferred', 1, NULL, 1),  -- lich su: 6B -> chuyen di 05/09
(4, 1, '2026-09-05', NULL, NULL, NULL, NULL, 1),                -- hien tai: 6A
(5, 2, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(6, 2, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(7, 3, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(8, 3, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(9, 4, '2026-08-03', NULL, NULL, NULL, NULL, 1),
(10, 4, '2026-08-03', NULL, NULL, NULL, NULL, 1);

-- ---------- sessions: vai buoi cua lop 6A thang 8/2026 ----------
-- Lich 6A: T3 + T5 18:00-20:00. Buoi 11/08 bi HUY (GV ban), co buoi BU chieu CN 16/08.
INSERT INTO sessions (class_id, session_date, start_time, end_time, status, is_makeup, makeup_for_session_id, cancel_reason) VALUES
(1, '2026-08-04', '18:00', '20:00', 'scheduled', false, NULL, NULL),   -- id 1 (T3)
(1, '2026-08-06', '18:00', '20:00', 'scheduled', false, NULL, NULL),   -- id 2 (T5)
(1, '2026-08-11', '18:00', '20:00', 'cancelled', false, NULL, 'GV bận việc đột xuất'),  -- id 3
(1, '2026-08-13', '18:00', '20:00', 'scheduled', false, NULL, NULL),   -- id 4
(1, '2026-08-16', '15:00', '17:00', 'scheduled', true, 3, NULL);       -- id 5: buoi BU cho buoi 11/08

-- ---------- attendance: diem danh 2 buoi dau cua 6A (HS 1,2,3 — HS 4 sang thang 9 moi vao) ----------
INSERT INTO attendance (session_id, student_id, status, note, recorded_by) VALUES
(1, 1, 'present', NULL, 2),
(1, 2, 'late',    'Đến muộn 15 phút', 4),
(1, 3, 'present', NULL, 4),
(2, 1, 'present', NULL, 2),
(2, 2, 'present', NULL, 2),
(2, 3, 'excused', 'Phụ huynh xin phép từ hôm trước', 2);

-- ---------- grade_columns + grades: lop 6A ----------
INSERT INTO grade_columns (class_id, name, weight, max_score, column_date, created_by) VALUES
(1, 'Bài tập tuần 1',     1, 10, '2026-08-06', 2),   -- id 1
(1, 'Kiểm tra 15 phút',   1, 10, '2026-08-13', 2),   -- id 2
(1, 'Kiểm tra giữa kỳ I', 2, 10, '2026-10-15', 2);   -- id 3 (trong so x2, chua co diem)

INSERT INTO grades (grade_column_id, student_id, score, entered_by) VALUES
(1, 1, 8.5, 4), (1, 2, 7.0, 4), (1, 3, 9.0, 4),
(2, 1, 9.0, 2), (2, 2, 6.5, 2), (2, 3, 8.0, 2);

-- ---------- monthly_fees: thang 8/2026 ----------
-- HS 3 dung phi uu dai 450k (custom_monthly_fee). Khoan cua HS 2 duoc DIEU CHINH
-- giam 62.500 (vi du minh hoa) — luu ly do + nguoi chinh.
INSERT INTO monthly_fees (student_id, class_id, fee_year, fee_month, base_amount, adjusted_amount, adjustment_reason, adjusted_by, status) VALUES
(1, 1, 2026, 8, 500000, NULL,   NULL,                                        NULL, 'paid'),     -- id 1
(2, 1, 2026, 8, 500000, 437500, 'Giảm 1 buổi theo thỏa thuận với phụ huynh', 1,   'partial'),  -- id 2
(3, 1, 2026, 8, 450000, NULL,   NULL,                                        NULL, 'unpaid'),   -- id 3
(5, 2, 2026, 8, 500000, NULL,   NULL,                                        NULL, 'paid'),     -- id 4
(6, 2, 2026, 8, 500000, NULL,   NULL,                                        NULL, 'unpaid');   -- id 5

-- ---------- payments ----------
-- HS 1 dong du 1 lan tien mat; HS 2 moi dong 1 phan (200k/437.5k) -> partial;
-- HS 5 dong du bang chuyen khoan (tro giang ghi nhan).
INSERT INTO payments (monthly_fee_id, amount, method, paid_at, recorded_by, note) VALUES
(1, 500000, 'cash',     '2026-08-05T11:30:00', 2, NULL),
(2, 200000, 'cash',     '2026-08-07T11:45:00', 4, 'Phụ huynh hẹn đóng nốt cuối tháng'),
(4, 500000, 'transfer', '2026-08-04T09:10:00', 5, 'CK qua ngân hàng, đã đối chiếu');
