-- ============================================================
-- Schema: Quan ly lop hoc them — SQL Server 2019+
-- Quy uoc: snake_case, PK = INT IDENTITY, datetime luu UTC,
-- text tieng Viet = NVARCHAR, enum = VARCHAR + CHECK.
-- Khong xoa cung du lieu nghiep vu (dung cot trang thai).
-- Ban Postgres tuong duong: schema.postgres.sql
-- ============================================================

-- ---------- 1. users: tai khoan noi bo (admin / GV chinh / tro giang) ----------
CREATE TABLE users (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    full_name       NVARCHAR(100)  NOT NULL,
    email           VARCHAR(255)   NOT NULL,
    password_hash   VARCHAR(255)   NOT NULL,
    role            VARCHAR(20)    NOT NULL,            -- vai tro he thong
    phone           VARCHAR(20)    NULL,
    is_active       BIT            NOT NULL DEFAULT 1,  -- 0 = vo hieu hoa (khong xoa cung)
    created_at      DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('admin', 'teacher', 'assistant'))
);

-- ---------- 2. classes: lop hoc (1 mon duy nhat nen khong co bang mon) ----------
CREATE TABLE classes (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(50)   NOT NULL,            -- vd: 6A
    grade               INT            NOT NULL,            -- khoi 6/7/8/9
    school_year         VARCHAR(9)     NOT NULL,            -- vd: 2026-2027
    start_date          DATE           NOT NULL,            -- ngay bat dau nam hoc (linh hoat, khong khoa cung thang)
    end_date            DATE           NOT NULL,
    default_monthly_fee DECIMAL(12,0)  NOT NULL DEFAULT 0,  -- phi thang co dinh (VND)
    status              VARCHAR(20)    NOT NULL DEFAULT 'active',
    note                NVARCHAR(500)  NULL,
    created_at          DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_classes_name_year UNIQUE (name, school_year),
    CONSTRAINT ck_classes_grade CHECK (grade IN (6, 7, 8, 9)),
    CONSTRAINT ck_classes_status CHECK (status IN ('active', 'closed')),
    CONSTRAINT ck_classes_dates CHECK (end_date > start_date)
);

-- ---------- 3. class_staff: gan GV/tro giang vao lop ----------
CREATE TABLE class_staff (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    class_id    INT          NOT NULL,
    user_id     INT          NOT NULL,
    staff_role  VARCHAR(20)  NOT NULL,   -- main = GV chinh, assistant = tro giang
    assigned_at DATETIME2    NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_class_staff_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_class_staff_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT uq_class_staff UNIQUE (class_id, user_id),
    CONSTRAINT ck_class_staff_role CHECK (staff_role IN ('main', 'assistant'))
);
-- Moi lop chi co dung 1 GV chinh (filtered unique index)
CREATE UNIQUE NONCLUSTERED INDEX ux_class_staff_one_main
    ON class_staff(class_id) WHERE staff_role = 'main';
CREATE NONCLUSTERED INDEX ix_class_staff_user ON class_staff(user_id);

-- ---------- 4. class_schedules: khung gio co dinh theo tuan ----------
CREATE TABLE class_schedules (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    class_id    INT   NOT NULL,
    day_of_week INT   NOT NULL,   -- 1 = Thu 2 ... 7 = Chu nhat
    start_time  TIME  NOT NULL,
    end_time    TIME  NOT NULL,
    CONSTRAINT fk_class_schedules_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT uq_class_schedules UNIQUE (class_id, day_of_week, start_time),
    CONSTRAINT ck_class_schedules_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_class_schedules_time CHECK (end_time > start_time)
);

-- ---------- 5. sessions: buoi hoc (sinh tu lich tuan + buoi bu them tay) ----------
CREATE TABLE sessions (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    class_id              INT           NOT NULL,
    session_date          DATE          NOT NULL,
    start_time            TIME          NOT NULL,
    end_time              TIME          NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'scheduled',
    is_makeup             BIT           NOT NULL DEFAULT 0,   -- 1 = buoi hoc bu
    makeup_for_session_id INT           NULL,                 -- link toi buoi bi huy (neu la buoi bu)
    cancel_reason         NVARCHAR(300) NULL,                 -- bat buoc o tang app khi status = cancelled
    note                  NVARCHAR(300) NULL,
    created_at            DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_sessions_class  FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_sessions_makeup FOREIGN KEY (makeup_for_session_id) REFERENCES sessions(id),
    CONSTRAINT uq_sessions UNIQUE (class_id, session_date, start_time),
    CONSTRAINT ck_sessions_status CHECK (status IN ('scheduled', 'cancelled')),
    CONSTRAINT ck_sessions_time CHECK (end_time > start_time)
);
CREATE NONCLUSTERED INDEX ix_sessions_class_date ON sessions(class_id, session_date);

-- ---------- 6. students: ho so hoc sinh (khong co tai khoan o phase 1) ----------
CREATE TABLE students (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    full_name       NVARCHAR(100) NOT NULL,
    dob             DATE          NULL,
    gender          VARCHAR(10)   NULL,
    school_name     NVARCHAR(150) NULL,   -- truong chinh khoa dang hoc
    parent_name     NVARCHAR(100) NULL,   -- lien he phu huynh: chi la text, chua co tai khoan
    parent_phone    VARCHAR(20)   NULL,
    enrollment_date DATE          NOT NULL,             -- ngay vao hoc trung tam
    status          VARCHAR(20)   NOT NULL DEFAULT 'active',
    note            NVARCHAR(1000) NULL,                -- ghi chu tu do cua GV
    created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_students_gender CHECK (gender IN ('male', 'female', 'other')),
    CONSTRAINT ck_students_status CHECK (status IN ('active', 'paused', 'left'))
);

-- ---------- 7. enrollments: HS thuoc lop nao + lich su chuyen lop ----------
-- Chuyen lop = dong enrollment cu (set left_at + leave_reason) va tao enrollment moi.
-- Du lieu diem/diem danh/hoc phi gan voi lop cu giu nguyen -> tra cuu lich su.
CREATE TABLE enrollments (
    id                      INT           IDENTITY(1,1) PRIMARY KEY,
    student_id              INT           NOT NULL,
    class_id                INT           NOT NULL,
    joined_at               DATE          NOT NULL,
    left_at                 DATE          NULL,          -- NULL = dang hoc lop nay
    leave_reason            VARCHAR(20)   NULL,          -- transferred / quit
    transferred_to_class_id INT           NULL,          -- lop den (khi leave_reason = transferred)
    custom_monthly_fee      DECIMAL(12,0) NULL,          -- ghi de phi thang rieng cho HS nay (uu dai), NULL = theo lop
    created_by              INT           NOT NULL,
    created_at              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_enrollments_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_enrollments_class    FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_enrollments_to_class FOREIGN KEY (transferred_to_class_id) REFERENCES classes(id),
    CONSTRAINT fk_enrollments_creator  FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_enrollments_leave CHECK (leave_reason IN ('transferred', 'quit')),
    CONSTRAINT ck_enrollments_left CHECK (left_at IS NULL OR left_at >= joined_at)
);
-- 1 HS chi dang hoc 1 lop tai 1 thoi diem (chi co 1 mon)
CREATE UNIQUE NONCLUSTERED INDEX ux_enrollments_active
    ON enrollments(student_id) WHERE left_at IS NULL;
CREATE NONCLUSTERED INDEX ix_enrollments_class ON enrollments(class_id);

-- ---------- 8. attendance: diem danh theo buoi ----------
-- Khoa 24h: app so sanh (session_date + start_time) voi gio hien tai;
-- qua 24h chi admin duoc sua. recorded_*/updated_* phuc vu audit.
CREATE TABLE attendance (
    id          INT           IDENTITY(1,1) PRIMARY KEY,
    session_id  INT           NOT NULL,
    student_id  INT           NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    note        NVARCHAR(300) NULL,
    recorded_by INT           NOT NULL,
    recorded_at DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by  INT           NULL,
    updated_at  DATETIME2     NULL,
    CONSTRAINT fk_attendance_session  FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_attendance_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_attendance_recorder FOREIGN KEY (recorded_by) REFERENCES users(id),
    CONSTRAINT fk_attendance_updater  FOREIGN KEY (updated_by)  REFERENCES users(id),
    CONSTRAINT uq_attendance UNIQUE (session_id, student_id),
    CONSTRAINT ck_attendance_status CHECK (status IN ('present', 'absent', 'late', 'excused'))
);
CREATE NONCLUSTERED INDEX ix_attendance_student ON attendance(student_id);

-- ---------- 9. grade_columns: cot diem linh hoat + trong so (theo lop) ----------
CREATE TABLE grade_columns (
    id          INT           IDENTITY(1,1) PRIMARY KEY,
    class_id    INT           NOT NULL,
    name        NVARCHAR(100) NOT NULL,               -- vd: BT tuan 1, KT giua ky
    weight      DECIMAL(5,2)  NOT NULL DEFAULT 1,     -- trong so tinh TB
    max_score   DECIMAL(5,2)  NOT NULL DEFAULT 10,    -- thang diem
    column_date DATE          NULL,
    is_deleted  BIT           NOT NULL DEFAULT 0,     -- soft delete (diem da nhap giu nguyen)
    created_by  INT           NOT NULL,
    created_at  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_grade_columns_class   FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_grade_columns_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_grade_columns_weight CHECK (weight > 0),
    CONSTRAINT ck_grade_columns_max CHECK (max_score > 0)
);
CREATE NONCLUSTERED INDEX ix_grade_columns_class ON grade_columns(class_id);

-- ---------- 10. grades: diem tung HS theo cot ----------
-- Diem TB co trong so (tinh o tang app/query):
--   SUM(score/max_score*10*weight) / SUM(weight) tren cac cot da co diem
CREATE TABLE grades (
    id              INT          IDENTITY(1,1) PRIMARY KEY,
    grade_column_id INT          NOT NULL,
    student_id      INT          NOT NULL,
    score           DECIMAL(5,2) NOT NULL,
    entered_by      INT          NOT NULL,
    entered_at      DATETIME2    NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      INT          NULL,
    updated_at      DATETIME2    NULL,
    CONSTRAINT fk_grades_column  FOREIGN KEY (grade_column_id) REFERENCES grade_columns(id),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_grades_enterer FOREIGN KEY (entered_by) REFERENCES users(id),
    CONSTRAINT fk_grades_updater FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT uq_grades UNIQUE (grade_column_id, student_id),
    CONSTRAINT ck_grades_score CHECK (score >= 0)
);
CREATE NONCLUSTERED INDEX ix_grades_student ON grades(student_id);

-- ---------- 11. monthly_fees: khoan phi thang cua tung HS ----------
-- So tien phai dong thuc te = COALESCE(adjusted_amount, base_amount).
-- adjusted_amount: dieu chinh TAY (vd thang co buoi huy khong bu, HS tam nghi giua thang)
-- Sinh tu dong dau thang HOAC chot tay deu duoc — schema khong khoa cung (SRS muc 7).
CREATE TABLE monthly_fees (
    id                INT           IDENTITY(1,1) PRIMARY KEY,
    student_id        INT           NOT NULL,
    class_id          INT           NOT NULL,
    fee_year          INT           NOT NULL,
    fee_month         INT           NOT NULL,
    base_amount       DECIMAL(12,0) NOT NULL,        -- phi goc (tu lop hoac custom_monthly_fee)
    adjusted_amount   DECIMAL(12,0) NULL,            -- NULL = khong dieu chinh
    adjustment_reason NVARCHAR(300) NULL,            -- bat buoc o tang app khi co adjusted_amount
    adjusted_by       INT           NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'unpaid',
    created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_monthly_fees_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_monthly_fees_class    FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_monthly_fees_adjuster FOREIGN KEY (adjusted_by) REFERENCES users(id),
    CONSTRAINT uq_monthly_fees UNIQUE (student_id, class_id, fee_year, fee_month),
    CONSTRAINT ck_monthly_fees_month CHECK (fee_month BETWEEN 1 AND 12),
    CONSTRAINT ck_monthly_fees_year CHECK (fee_year BETWEEN 2020 AND 2100),
    CONSTRAINT ck_monthly_fees_status CHECK (status IN ('unpaid', 'partial', 'paid')),
    CONSTRAINT ck_monthly_fees_amounts CHECK (base_amount >= 0 AND (adjusted_amount IS NULL OR adjusted_amount >= 0))
);
CREATE NONCLUSTERED INDEX ix_monthly_fees_class_period ON monthly_fees(class_id, fee_year, fee_month);

-- ---------- 12. payments: tung lan dong tien (cho phep thieu/thua, nhieu lan) ----------
-- Tro giang duoc ghi nhan payment nhung KHONG duoc query tong hop doanh thu
-- (chan o tang API, khong phai tang DB).
CREATE TABLE payments (
    id             INT           IDENTITY(1,1) PRIMARY KEY,
    monthly_fee_id INT           NOT NULL,
    amount         DECIMAL(12,0) NOT NULL,
    method         VARCHAR(20)   NOT NULL,   -- cash / transfer
    paid_at        DATETIME2     NOT NULL,   -- ngay gio dong thuc te
    recorded_by    INT           NOT NULL,   -- ai ghi nhan (audit)
    note           NVARCHAR(300) NULL,
    created_at     DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_payments_fee      FOREIGN KEY (monthly_fee_id) REFERENCES monthly_fees(id),
    CONSTRAINT fk_payments_recorder FOREIGN KEY (recorded_by)    REFERENCES users(id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('cash', 'transfer'))
);
CREATE NONCLUSTERED INDEX ix_payments_fee ON payments(monthly_fee_id);
CREATE NONCLUSTERED INDEX ix_payments_paid_at ON payments(paid_at);

-- ---------- 13. audit_logs: nhat ky thao tac nhay cam (NFR-4) ----------
-- Ghi o tang app cho: sua diem danh sau 24h, dieu chinh hoc phi, chuyen lop, sua/xoa diem...
CREATE TABLE audit_logs (
    id         INT           IDENTITY(1,1) PRIMARY KEY,
    table_name VARCHAR(50)   NOT NULL,
    record_id  INT           NOT NULL,
    action     VARCHAR(20)   NOT NULL,        -- create / update / delete
    old_values NVARCHAR(MAX) NULL,            -- JSON snapshot truoc
    new_values NVARCHAR(MAX) NULL,            -- JSON snapshot sau
    user_id    INT           NOT NULL,
    created_at DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('create', 'update', 'delete'))
);
CREATE NONCLUSTERED INDEX ix_audit_logs_record ON audit_logs(table_name, record_id);
CREATE NONCLUSTERED INDEX ix_audit_logs_user ON audit_logs(user_id);
