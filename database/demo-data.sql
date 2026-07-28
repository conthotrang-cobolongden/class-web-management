-- ============================================================
-- DEMO DATA: bien DB seed thanh du lieu "ca nam hoc 2025-2026"
-- de dashboard/bieu do/danh sach co du lieu day dan.
-- Chay MOT LAN tren DB da co seed goc:
--   sqlcmd -S tcp:localhost,1433 -E -C -I -f 65001 -d ClassManagement -i database\demo-data.sql
-- Noi dung:
--   1) Keo nam hoc ve 2025-08 -> 2026-08 (de co lich su tinh den hom nay)
--   2) Them lop 9A + 50 hoc sinh + enrollments (kem chuyen lop/nghi hoc)
--   3) Sinh buoi hoc tu khung tuan cho ca nam (idempotent)
--   4) Diem danh ngau nhien co trong so cho moi buoi da qua
--   5) Cot diem chuan + diem ngau nhien phan phoi dep
--   6) Khoan phi 12 thang + thanh toan (72% du / 13% mot phan) + vai khoan dieu chinh
-- ============================================================
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;

-- ---------- 1. Keo nam hoc ve qua khu ----------
UPDATE classes SET school_year = '2025-2026', start_date = '2025-08-04', end_date = '2026-08-30';
UPDATE grade_columns SET column_date = DATEADD(year, -1, column_date) WHERE column_date >= '2026-08-01';
UPDATE sessions SET session_date = DATEADD(year, -1, session_date) WHERE session_date >= '2026-08-01';
UPDATE monthly_fees SET fee_year = 2025 WHERE fee_year = 2026 AND fee_month = 8;
UPDATE payments SET paid_at = DATEADD(year, -1, paid_at) WHERE paid_at >= '2026-08-01';
UPDATE students SET enrollment_date = DATEADD(year, -1, enrollment_date) WHERE enrollment_date >= '2026-08-01';
UPDATE enrollments SET joined_at = DATEADD(year, -1, joined_at) WHERE joined_at >= '2026-08-01';
UPDATE enrollments SET left_at = DATEADD(year, -1, left_at) WHERE left_at >= '2026-08-01';

-- ---------- 2. Lop 9A + nhan su + khung tuan ----------
IF NOT EXISTS (SELECT 1 FROM classes WHERE name = N'9A')
BEGIN
    INSERT INTO classes (name, grade, school_year, start_date, end_date, default_monthly_fee, status)
    VALUES (N'9A', 9, '2025-2026', '2025-08-04', '2026-08-30', 650000, 'active');
    DECLARE @c9 INT = SCOPE_IDENTITY();
    INSERT INTO class_staff (class_id, user_id, staff_role) VALUES (@c9, 3, 'main'), (@c9, 5, 'assistant');
    INSERT INTO class_schedules (class_id, day_of_week, start_time, end_time)
    VALUES (@c9, 1, '19:00', '21:00'), (@c9, 5, '19:00', '21:00');
END

-- ---------- 2b. 50 hoc sinh moi (id 11..60) ----------
IF NOT EXISTS (SELECT 1 FROM students WHERE id > 10)
BEGIN
INSERT INTO students (full_name, dob, gender, school_name, parent_name, parent_phone, enrollment_date, status, note) VALUES
(N'Nguyễn Minh Anh',   '2015-02-11', 'female', N'THCS Lê Quý Đôn',  N'Nguyễn Văn An',    '0912000011', '2025-08-04', 'active', NULL),
(N'Trần Quốc Bảo',     '2015-06-24', 'male',   N'THCS Nguyễn Du',   N'Trần Văn Bình',    '0912000012', '2025-08-04', 'active', NULL),
(N'Lê Thảo Chi',       '2015-09-03', 'female', N'THCS Trần Phú',    N'Lê Văn Cường',     '0912000013', '2025-08-04', 'active', NULL),
(N'Phạm Hải Đăng',     '2015-01-17', 'male',   N'THCS Lê Quý Đôn',  N'Phạm Văn Dũng',    '0912000014', '2025-08-04', 'active', NULL),
(N'Hoàng Gia Bách',    '2015-12-08', 'male',   N'THCS Nguyễn Du',   N'Hoàng Văn Em',     '0912000015', '2025-09-08', 'active', NULL),
(N'Vũ Khánh Chi',      '2015-04-27', 'female', N'THCS Trần Phú',    N'Vũ Văn Phong',     '0912000016', '2025-08-04', 'active', NULL),
(N'Đặng Nhật Minh',    '2015-07-15', 'male',   N'THCS Lê Quý Đôn',  N'Đặng Văn Giang',   '0912000017', '2025-08-04', 'active', NULL),
(N'Bùi Phương Linh',   '2015-10-30', 'female', N'THCS Nguyễn Du',   N'Bùi Văn Hải',      '0912000018', '2025-10-06', 'active', NULL),
(N'Đỗ Tuấn Hưng',      '2015-03-21', 'male',   N'THCS Trần Phú',    N'Đỗ Văn Ích',       '0912000019', '2025-08-04', 'active', NULL),
(N'Ngô Bảo Hân',       '2015-08-12', 'female', N'THCS Lê Quý Đôn',  N'Ngô Văn Khang',    '0912000020', '2025-08-04', 'left',   N'Nghỉ từ 03/2026, gia đình chuyển nhà'),
(N'Dương Đức Long',    '2015-05-05', 'male',   N'THCS Nguyễn Du',   N'Dương Văn Lâm',    '0912000021', '2025-08-04', 'active', NULL),
(N'Lý Mai Ngọc',       '2015-11-19', 'female', N'THCS Trần Phú',    N'Lý Văn Minh',      '0912000022', '2025-08-04', 'active', NULL),
(N'Trịnh Quang Nam',   '2015-06-06', 'male',   N'THCS Lê Quý Đôn',  N'Trịnh Văn Ninh',   '0912000023', '2025-08-04', 'active', NULL),
(N'Mai Thanh Ngân',    '2015-02-28', 'female', N'THCS Nguyễn Du',   N'Mai Văn Oanh',     '0912000024', '2025-09-08', 'active', NULL),
(N'Phan Việt Phong',   '2015-09-14', 'male',   N'THCS Trần Phú',    N'Phan Văn Phúc',    '0912000025', '2025-08-04', 'active', NULL),
(N'Võ Diễm Quỳnh',     '2015-12-25', 'female', N'THCS Lê Quý Đôn',  N'Võ Văn Quang',     '0912000026', '2025-08-04', 'active', NULL),
(N'Đinh Thái Sơn',     '2015-01-09', 'male',   N'THCS Nguyễn Du',   N'Đinh Văn Sang',    '0912000027', '2025-08-04', 'active', NULL),
(N'Chu Minh Thư',      '2015-04-18', 'female', N'THCS Trần Phú',    N'Chu Văn Tài',      '0912000028', '2025-08-04', 'active', NULL),
(N'Tạ Anh Tuấn',       '2014-07-07', 'male',   N'THCS Lê Quý Đôn',  N'Tạ Văn Thắng',     '0912000029', '2025-08-04', 'active', NULL),
(N'Lương Hà Vy',       '2014-10-16', 'female', N'THCS Nguyễn Du',   N'Lương Văn Út',     '0912000030', '2025-08-04', 'paused', N'Tạm nghỉ ôn thi trường chuyên'),
(N'Hồ Gia Huy',        '2014-03-29', 'male',   N'THCS Trần Phú',    N'Hồ Văn Vinh',      '0912000031', '2025-08-04', 'active', NULL),
(N'Doãn Khánh An',     '2014-08-08', 'female', N'THCS Lê Quý Đôn',  N'Doãn Văn Xuân',    '0912000032', '2025-08-04', 'active', NULL),
(N'Kiều Đăng Khoa',    '2014-05-23', 'male',   N'THCS Nguyễn Du',   N'Kiều Văn Yên',     '0912000033', '2025-08-04', 'active', NULL),
(N'Thái Ngọc Lan',     '2014-12-01', 'female', N'THCS Trần Phú',    N'Thái Văn Ân',      '0912000034', '2025-08-04', 'active', NULL),
(N'Tống Bảo Lâm',      '2014-06-13', 'male',   N'THCS Lê Quý Đôn',  N'Tống Văn Bằng',    '0912000035', '2025-09-08', 'active', NULL),
(N'Ứng Thu Mai',       '2014-02-20', 'female', N'THCS Nguyễn Du',   N'Ứng Văn Cần',      '0912000036', '2025-08-04', 'active', NULL),
(N'Quách Hồng Phúc',   '2014-09-09', 'male',   N'THCS Trần Phú',    N'Quách Văn Dần',    '0912000037', '2025-08-04', 'active', NULL),
(N'La Tường Vi',       '2014-11-27', 'female', N'THCS Lê Quý Đôn',  N'La Văn Đạt',       '0912000038', '2025-08-04', 'active', NULL),
(N'Ninh Hoàng Việt',   '2014-04-04', 'male',   N'THCS Nguyễn Du',   N'Ninh Văn Giáp',    '0912000039', '2025-08-04', 'active', NULL),
(N'Ông Nhã Uyên',      '2014-01-31', 'female', N'THCS Trần Phú',    N'Ông Văn Hợi',      '0912000040', '2025-08-04', 'active', NULL),
(N'Từ Minh Đức',       '2013-05-15', 'male',   N'THCS Lê Quý Đôn',  N'Từ Văn Kỷ',        '0912000041', '2025-08-04', 'active', NULL),
(N'Hà Bích Ngọc',      '2013-08-26', 'female', N'THCS Nguyễn Du',   N'Hà Văn Luận',      '0912000042', '2025-08-04', 'active', NULL),
(N'Châu Nhật Quang',   '2013-03-12', 'male',   N'THCS Trần Phú',    N'Châu Văn Mão',     '0912000043', '2025-08-04', 'active', NULL),
(N'Lâm Tuệ San',       '2013-10-05', 'female', N'THCS Lê Quý Đôn',  N'Lâm Văn Ngọ',      '0912000044', '2025-08-04', 'active', NULL),
(N'Triệu Công Thành',  '2013-07-22', 'male',   N'THCS Nguyễn Du',   N'Triệu Văn Ổn',     '0912000045', '2025-08-04', 'active', NULL),
(N'Cao Thùy Trang',    '2013-12-18', 'female', N'THCS Trần Phú',    N'Cao Văn Phú',      '0912000046', '2025-08-04', 'active', NULL),
(N'Vương Hữu Trí',     '2013-02-14', 'male',   N'THCS Lê Quý Đôn',  N'Vương Văn Quý',    '0912000047', '2025-08-04', 'active', NULL),
(N'Ngụy Thanh Tú',     '2013-09-01', 'female', N'THCS Nguyễn Du',   N'Ngụy Văn Sửu',     '0912000048', '2025-08-04', 'active', NULL),
(N' Đàm Quốc Việt',    '2013-06-28', 'male',   N'THCS Trần Phú',    N'Đàm Văn Thìn',     '0912000049', '2025-08-04', 'active', NULL),
(N'Ma Hải Yến',        '2013-04-09', 'female', N'THCS Lê Quý Đôn',  N'Ma Văn Tuất',      '0912000050', '2025-08-04', 'active', NULL),
(N'Giang Thiên Ân',    '2012-08-17', 'male',   N'THCS Nguyễn Du',   N'Giang Văn Vượng',  '0912000051', '2025-08-04', 'active', NULL),
(N'Khúc Hoài Băng',    '2012-11-06', 'female', N'THCS Trần Phú',    N'Khúc Văn Xương',   '0912000052', '2025-08-04', 'active', NULL),
(N'Ấu Duy Cường',      '2012-03-25', 'male',   N'THCS Lê Quý Đôn',  N'Ấu Văn Yết',       '0912000053', '2025-08-04', 'active', NULL),
(N'Bạch Linh Đan',     '2012-06-15', 'female', N'THCS Nguyễn Du',   N'Bạch Văn Ất',      '0912000054', '2025-08-04', 'active', NULL),
(N'Uông Trung Hiếu',   '2012-01-02', 'male',   N'THCS Trần Phú',    N'Uông Văn Bính',    '0912000055', '2025-08-04', 'left',   N'Nghỉ từ 06/2026 để du học'),
(N'Sầm Gia Khánh',     '2012-09-20', 'male',   N'THCS Lê Quý Đôn',  N'Sầm Văn Canh',     '0912000056', '2025-08-04', 'active', NULL),
(N'Thi Mỹ Lệ',         '2012-12-11', 'female', N'THCS Nguyễn Du',   N'Thi Văn Đinh',     '0912000057', '2025-08-04', 'active', NULL),
(N'Ưng Phương Nam',    '2012-05-30', 'male',   N'THCS Trần Phú',    N'Ưng Văn Mậu',      '0912000058', '2025-09-08', 'active', NULL),
(N'Yên Tuyết Nhi',     '2012-07-04', 'female', N'THCS Lê Quý Đôn',  N'Yên Văn Kỷ',       '0912000059', '2025-08-04', 'active', NULL),
(N'Ông Thế Vinh',      '2012-10-23', 'male',   N'THCS Nguyễn Du',   N'Ông Văn Tân',      '0912000060', '2025-08-04', 'active', NULL);

-- Enrollment: 6A id 11-18, 6B id 19-28, 7A id 29-38, 8A id 39-48, 9A id 49-60
DECLARE @c9a INT = (SELECT id FROM classes WHERE name = N'9A');
INSERT INTO enrollments (student_id, class_id, joined_at, left_at, leave_reason, transferred_to_class_id, custom_monthly_fee, created_by)
SELECT s.id,
       CASE WHEN s.id BETWEEN 11 AND 18 THEN 1
            WHEN s.id BETWEEN 19 AND 28 THEN 2
            WHEN s.id BETWEEN 29 AND 38 THEN 3
            WHEN s.id BETWEEN 39 AND 48 THEN 4
            ELSE @c9a END,
       s.enrollment_date,
       CASE WHEN s.id = 20 THEN CAST('2026-03-01' AS date)
            WHEN s.id = 55 THEN CAST('2026-06-01' AS date) ELSE NULL END,
       CASE WHEN s.id IN (20, 55) THEN 'quit' ELSE NULL END,
       NULL,
       CASE WHEN s.id IN (13, 31, 52) THEN 400000 ELSE NULL END,
       1
FROM students s WHERE s.id BETWEEN 11 AND 60;

-- Lich su chuyen lop: HS 40 tu 8A -> 7A (02/02/2026), HS 50 tu 9A -> 6B (12/01/2026)
UPDATE enrollments SET left_at = '2026-02-02', leave_reason = 'transferred', transferred_to_class_id = 3
WHERE student_id = 40 AND left_at IS NULL;
INSERT INTO enrollments (student_id, class_id, joined_at, created_by) VALUES (40, 3, '2026-02-02', 1);
UPDATE enrollments SET left_at = '2026-01-12', leave_reason = 'transferred', transferred_to_class_id = 2
WHERE student_id = 50 AND left_at IS NULL;
INSERT INTO enrollments (student_id, class_id, joined_at, created_by) VALUES (50, 2, '2026-01-12', 1);
END

-- ---------- 3. Sinh buoi hoc ca nam tu khung tuan (idempotent) ----------
;WITH dates AS (
    SELECT CAST('2025-08-04' AS date) AS d
    UNION ALL SELECT DATEADD(day, 1, d) FROM dates WHERE d < '2026-08-30'
)
INSERT INTO sessions (class_id, session_date, start_time, end_time, status)
SELECT cs.class_id, d.d, cs.start_time, cs.end_time, 'scheduled'
FROM dates d
JOIN class_schedules cs
  ON ((DATEDIFF(day, '2025-01-06', d.d) % 7 + 7) % 7) + 1 = cs.day_of_week  -- 2025-01-06 la Thu 2 -> 1=T2..7=CN
WHERE NOT EXISTS (SELECT 1 FROM sessions s
                  WHERE s.class_id = cs.class_id AND s.session_date = d.d AND s.start_time = cs.start_time)
OPTION (MAXRECURSION 0);

-- ---------- 4. Diem danh cho moi buoi DA QUA (trong so: 85% co mat / 6% muon / 6% vang / 3% co phep) ----------
INSERT INTO attendance (session_id, student_id, status, recorded_by)
SELECT s.id, e.student_id,
       CASE WHEN r.h < 85 THEN 'present' WHEN r.h < 91 THEN 'late'
            WHEN r.h < 97 THEN 'absent' ELSE 'excused' END,
       COALESCE(st.user_id, 1)
FROM sessions s
JOIN enrollments e
  ON e.class_id = s.class_id AND e.joined_at <= s.session_date
 AND (e.left_at IS NULL OR e.left_at > s.session_date)
LEFT JOIN class_staff st ON st.class_id = s.class_id AND st.staff_role = 'main'
CROSS APPLY (SELECT ABS(CHECKSUM(NEWID())) % 100 AS h) r
WHERE s.status = 'scheduled' AND s.session_date < CAST(GETDATE() AS date)
  AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.session_id = s.id AND a.student_id = e.student_id);

-- ---------- 5. Cot diem chuan cho moi lop + diem ngau nhien ----------
INSERT INTO grade_columns (class_id, name, weight, max_score, column_date, created_by)
SELECT c.id, v.name, v.w, 10, v.d, COALESCE(st.user_id, 1)
FROM classes c
LEFT JOIN class_staff st ON st.class_id = c.id AND st.staff_role = 'main'
CROSS APPLY (VALUES
    (N'Kiểm tra 15 phút số 1', 1, CAST('2025-09-15' AS date)),
    (N'Kiểm tra giữa kỳ I',    2, CAST('2025-10-20' AS date)),
    (N'Kiểm tra cuối kỳ I',    3, CAST('2025-12-22' AS date)),
    (N'Bài tập về nhà HK2',    1, CAST('2026-01-19' AS date)),
    (N'Kiểm tra 15 phút số 2', 1, CAST('2026-02-09' AS date)),
    (N'Kiểm tra giữa kỳ II',   2, CAST('2026-03-16' AS date)),
    (N'Kiểm tra cuối kỳ II',   3, CAST('2026-05-18' AS date))
) v(name, w, d)
WHERE NOT EXISTS (SELECT 1 FROM grade_columns g WHERE g.class_id = c.id AND g.name = v.name);

-- Diem: phan phoi tam giac 4.0..10.0 (don ve giua ~7) cho HS dang hoc x cot chua co diem
INSERT INTO grades (grade_column_id, student_id, score, entered_by)
SELECT gc.id, e.student_id,
       CAST(((r.a + r.b) / 2 + 40) / 10.0 AS DECIMAL(5,2)),
       gc.created_by
FROM grade_columns gc
JOIN enrollments e ON e.class_id = gc.class_id AND e.left_at IS NULL
CROSS APPLY (SELECT ABS(CHECKSUM(NEWID())) % 61 AS a, ABS(CHECKSUM(NEWID())) % 61 AS b) r
WHERE gc.is_deleted = 0 AND gc.column_date < CAST(GETDATE() AS date)
  AND NOT EXISTS (SELECT 1 FROM grades g WHERE g.grade_column_id = gc.id AND g.student_id = e.student_id);

-- ---------- 6. Khoan phi 12 thang (2025-08 .. 2026-07) ----------
INSERT INTO monthly_fees (student_id, class_id, fee_year, fee_month, base_amount, status)
SELECT e.student_id, e.class_id, m.y, m.m,
       COALESCE(e.custom_monthly_fee, c.default_monthly_fee), 'unpaid'
FROM (VALUES (2025,8),(2025,9),(2025,10),(2025,11),(2025,12),
             (2026,1),(2026,2),(2026,3),(2026,4),(2026,5),(2026,6),(2026,7)) m(y, m)
CROSS JOIN enrollments e
JOIN classes c ON c.id = e.class_id
JOIN students stu ON stu.id = e.student_id
WHERE e.joined_at <= EOMONTH(DATEFROMPARTS(m.y, m.m, 1))                -- da vao lop trong thang do
  AND (e.left_at IS NULL OR e.left_at >= DATEFROMPARTS(m.y, m.m, 1))   -- chua roi lop truoc thang do
  AND NOT EXISTS (SELECT 1 FROM monthly_fees f
                  WHERE f.student_id = e.student_id AND f.class_id = e.class_id
                    AND f.fee_year = m.y AND f.fee_month = m.m);

-- Vai khoan dieu chinh giam (thang co buoi huy khong bu) + luu ly do
UPDATE monthly_fees
SET adjusted_amount = ROUND(base_amount * 0.875, -3),
    adjustment_reason = N'Giảm 1 buổi do nghỉ lễ, không học bù',
    adjusted_by = 1
WHERE fee_year = 2025 AND fee_month = 9 AND student_id % 11 = 3 AND adjusted_amount IS NULL;

-- ---------- 7. Thanh toan: thang cu 72% du / 13% mot phan; thang 7/2026 40% du / 10% mot phan ----------
INSERT INTO payments (monthly_fee_id, amount, method, paid_at, recorded_by, note)
SELECT f.id,
       CASE WHEN r.h < CASE WHEN f.fee_year = 2026 AND f.fee_month = 7 THEN 40 ELSE 72 END
            THEN COALESCE(f.adjusted_amount, f.base_amount)                                   -- dong du
            ELSE ROUND(COALESCE(f.adjusted_amount, f.base_amount) * (40 + r.h % 31) / 100.0, -3) END,  -- mot phan
       CASE WHEN r.h % 2 = 0 THEN 'cash' ELSE 'transfer' END,
       DATEADD(hour, 8 + r.h % 11, CAST(DATEFROMPARTS(f.fee_year, f.fee_month, 3 + r.h % 12) AS datetime2)),
       COALESCE(st.user_id, 1),
       NULL
FROM monthly_fees f
LEFT JOIN class_staff st ON st.class_id = f.class_id AND st.staff_role = 'main'
CROSS APPLY (SELECT ABS(CHECKSUM(NEWID())) % 100 AS h) r
WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.monthly_fee_id = f.id)
  AND r.h < CASE WHEN f.fee_year = 2026 AND f.fee_month = 7 THEN 50 ELSE 85 END;  -- phan con lai: chua dong

-- ---------- 8. Tinh lai trang thai moi khoan phi theo tong da dong ----------
UPDATE f SET status =
       CASE WHEN p.total IS NULL OR p.total = 0 THEN 'unpaid'
            WHEN p.total >= COALESCE(f.adjusted_amount, f.base_amount) THEN 'paid'
            ELSE 'partial' END
FROM monthly_fees f
LEFT JOIN (SELECT monthly_fee_id, SUM(amount) AS total FROM payments GROUP BY monthly_fee_id) p
  ON p.monthly_fee_id = f.id;

-- ---------- Tong ket ----------
SELECT 'classes' AS tbl, COUNT(*) AS rows FROM classes
UNION ALL SELECT 'students', COUNT(*) FROM students
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'sessions', COUNT(*) FROM sessions
UNION ALL SELECT 'attendance', COUNT(*) FROM attendance
UNION ALL SELECT 'grade_columns', COUNT(*) FROM grade_columns
UNION ALL SELECT 'grades', COUNT(*) FROM grades
UNION ALL SELECT 'monthly_fees', COUNT(*) FROM monthly_fees
UNION ALL SELECT 'payments', COUNT(*) FROM payments;
