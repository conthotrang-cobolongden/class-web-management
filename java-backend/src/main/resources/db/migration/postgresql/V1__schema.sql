-- ============================================================
-- Schema: Quan ly lop hoc them — PostgreSQL 15+ / Supabase
-- Ban tuong duong 1:1 voi db/migration/sqlserver/V1__schema.sql, chi khac dialect:
--   INT IDENTITY        -> integer GENERATED ALWAYS AS IDENTITY
--   BIT                 -> boolean
--   NVARCHAR / MAX      -> varchar / text
--   DATETIME2 + SYSUTCDATETIME() -> timestamptz + now()
--   DECIMAL             -> numeric
--   filtered index (WHERE) -> partial index (cung syntax WHERE)
-- (Flyway migration V1 — giu dong bo voi database/schema.postgres.sql)
-- ============================================================

-- ---------- 1. users ----------
CREATE TABLE users (
    id              integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name       varchar(100)  NOT NULL,
    email           varchar(255)  NOT NULL,
    password_hash   varchar(255)  NOT NULL,
    role            varchar(20)   NOT NULL,
    phone           varchar(20),
    is_active       boolean       NOT NULL DEFAULT true,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('admin', 'teacher', 'assistant'))
);

-- ---------- 2. classes ----------
CREATE TABLE classes (
    id                  integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                varchar(50)   NOT NULL,
    grade               integer       NOT NULL,
    school_year         varchar(9)    NOT NULL,
    start_date          date          NOT NULL,
    end_date            date          NOT NULL,
    default_monthly_fee numeric(12,0) NOT NULL DEFAULT 0,
    status              varchar(20)   NOT NULL DEFAULT 'active',
    note                varchar(500),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_classes_name_year UNIQUE (name, school_year),
    CONSTRAINT ck_classes_grade CHECK (grade IN (6, 7, 8, 9)),
    CONSTRAINT ck_classes_status CHECK (status IN ('active', 'closed')),
    CONSTRAINT ck_classes_dates CHECK (end_date > start_date)
);

-- ---------- 3. class_staff ----------
CREATE TABLE class_staff (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id    integer     NOT NULL,
    user_id     integer     NOT NULL,
    staff_role  varchar(20) NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_staff_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_class_staff_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT uq_class_staff UNIQUE (class_id, user_id),
    CONSTRAINT ck_class_staff_role CHECK (staff_role IN ('main', 'assistant'))
);
CREATE UNIQUE INDEX ux_class_staff_one_main
    ON class_staff(class_id) WHERE staff_role = 'main';
CREATE INDEX ix_class_staff_user ON class_staff(user_id);

-- ---------- 4. class_schedules ----------
CREATE TABLE class_schedules (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id    integer NOT NULL,
    day_of_week integer NOT NULL,
    start_time  time    NOT NULL,
    end_time    time    NOT NULL,
    CONSTRAINT fk_class_schedules_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT uq_class_schedules UNIQUE (class_id, day_of_week, start_time),
    CONSTRAINT ck_class_schedules_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_class_schedules_time CHECK (end_time > start_time)
);

-- ---------- 5. sessions ----------
CREATE TABLE sessions (
    id                    integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id              integer      NOT NULL,
    session_date          date         NOT NULL,
    start_time            time         NOT NULL,
    end_time              time         NOT NULL,
    status                varchar(20)  NOT NULL DEFAULT 'scheduled',
    is_makeup             boolean      NOT NULL DEFAULT false,
    makeup_for_session_id integer,
    cancel_reason         varchar(300),
    note                  varchar(300),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_sessions_class  FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_sessions_makeup FOREIGN KEY (makeup_for_session_id) REFERENCES sessions(id),
    CONSTRAINT uq_sessions UNIQUE (class_id, session_date, start_time),
    CONSTRAINT ck_sessions_status CHECK (status IN ('scheduled', 'cancelled')),
    CONSTRAINT ck_sessions_time CHECK (end_time > start_time)
);
CREATE INDEX ix_sessions_class_date ON sessions(class_id, session_date);

-- ---------- 6. students ----------
CREATE TABLE students (
    id              integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name       varchar(100) NOT NULL,
    dob             date,
    gender          varchar(10),
    school_name     varchar(150),
    parent_name     varchar(100),
    parent_phone    varchar(20),
    enrollment_date date         NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'active',
    note            varchar(1000),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_students_gender CHECK (gender IN ('male', 'female', 'other')),
    CONSTRAINT ck_students_status CHECK (status IN ('active', 'paused', 'left'))
);

-- ---------- 7. enrollments ----------
CREATE TABLE enrollments (
    id                      integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id              integer       NOT NULL,
    class_id                integer       NOT NULL,
    joined_at               date          NOT NULL,
    left_at                 date,
    leave_reason            varchar(20),
    transferred_to_class_id integer,
    custom_monthly_fee      numeric(12,0),
    created_by              integer       NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_enrollments_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_enrollments_class    FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_enrollments_to_class FOREIGN KEY (transferred_to_class_id) REFERENCES classes(id),
    CONSTRAINT fk_enrollments_creator  FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_enrollments_leave CHECK (leave_reason IN ('transferred', 'quit')),
    CONSTRAINT ck_enrollments_left CHECK (left_at IS NULL OR left_at >= joined_at)
);
CREATE UNIQUE INDEX ux_enrollments_active
    ON enrollments(student_id) WHERE left_at IS NULL;
CREATE INDEX ix_enrollments_class ON enrollments(class_id);

-- ---------- 8. attendance ----------
CREATE TABLE attendance (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  integer      NOT NULL,
    student_id  integer      NOT NULL,
    status      varchar(20)  NOT NULL,
    note        varchar(300),
    recorded_by integer      NOT NULL,
    recorded_at timestamptz  NOT NULL DEFAULT now(),
    updated_by  integer,
    updated_at  timestamptz,
    CONSTRAINT fk_attendance_session  FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_attendance_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_attendance_recorder FOREIGN KEY (recorded_by) REFERENCES users(id),
    CONSTRAINT fk_attendance_updater  FOREIGN KEY (updated_by)  REFERENCES users(id),
    CONSTRAINT uq_attendance UNIQUE (session_id, student_id),
    CONSTRAINT ck_attendance_status CHECK (status IN ('present', 'absent', 'late', 'excused'))
);
CREATE INDEX ix_attendance_student ON attendance(student_id);

-- ---------- 9. grade_columns ----------
CREATE TABLE grade_columns (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id    integer      NOT NULL,
    name        varchar(100) NOT NULL,
    weight      numeric(5,2) NOT NULL DEFAULT 1,
    max_score   numeric(5,2) NOT NULL DEFAULT 10,
    column_date date,
    is_deleted  boolean      NOT NULL DEFAULT false,
    created_by  integer      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_grade_columns_class   FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_grade_columns_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_grade_columns_weight CHECK (weight > 0),
    CONSTRAINT ck_grade_columns_max CHECK (max_score > 0)
);
CREATE INDEX ix_grade_columns_class ON grade_columns(class_id);

-- ---------- 10. grades ----------
CREATE TABLE grades (
    id              integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grade_column_id integer      NOT NULL,
    student_id      integer      NOT NULL,
    score           numeric(5,2) NOT NULL,
    entered_by      integer      NOT NULL,
    entered_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      integer,
    updated_at      timestamptz,
    CONSTRAINT fk_grades_column  FOREIGN KEY (grade_column_id) REFERENCES grade_columns(id),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_grades_enterer FOREIGN KEY (entered_by) REFERENCES users(id),
    CONSTRAINT fk_grades_updater FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT uq_grades UNIQUE (grade_column_id, student_id),
    CONSTRAINT ck_grades_score CHECK (score >= 0)
);
CREATE INDEX ix_grades_student ON grades(student_id);

-- ---------- 11. monthly_fees ----------
CREATE TABLE monthly_fees (
    id                integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id        integer       NOT NULL,
    class_id          integer       NOT NULL,
    fee_year          integer       NOT NULL,
    fee_month         integer       NOT NULL,
    base_amount       numeric(12,0) NOT NULL,
    adjusted_amount   numeric(12,0),
    adjustment_reason varchar(300),
    adjusted_by       integer,
    status            varchar(20)   NOT NULL DEFAULT 'unpaid',
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_monthly_fees_student  FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_monthly_fees_class    FOREIGN KEY (class_id)   REFERENCES classes(id),
    CONSTRAINT fk_monthly_fees_adjuster FOREIGN KEY (adjusted_by) REFERENCES users(id),
    CONSTRAINT uq_monthly_fees UNIQUE (student_id, class_id, fee_year, fee_month),
    CONSTRAINT ck_monthly_fees_month CHECK (fee_month BETWEEN 1 AND 12),
    CONSTRAINT ck_monthly_fees_year CHECK (fee_year BETWEEN 2020 AND 2100),
    CONSTRAINT ck_monthly_fees_status CHECK (status IN ('unpaid', 'partial', 'paid')),
    CONSTRAINT ck_monthly_fees_amounts CHECK (base_amount >= 0 AND (adjusted_amount IS NULL OR adjusted_amount >= 0))
);
CREATE INDEX ix_monthly_fees_class_period ON monthly_fees(class_id, fee_year, fee_month);

-- ---------- 12. payments ----------
CREATE TABLE payments (
    id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monthly_fee_id integer       NOT NULL,
    amount         numeric(12,0) NOT NULL,
    method         varchar(20)   NOT NULL,
    paid_at        timestamptz   NOT NULL,
    recorded_by    integer       NOT NULL,
    note           varchar(300),
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_payments_fee      FOREIGN KEY (monthly_fee_id) REFERENCES monthly_fees(id),
    CONSTRAINT fk_payments_recorder FOREIGN KEY (recorded_by)    REFERENCES users(id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('cash', 'transfer'))
);
CREATE INDEX ix_payments_fee ON payments(monthly_fee_id);
CREATE INDEX ix_payments_paid_at ON payments(paid_at);

-- ---------- 13. audit_logs ----------
CREATE TABLE audit_logs (
    id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name varchar(50) NOT NULL,
    record_id  integer     NOT NULL,
    action     varchar(20) NOT NULL,
    old_values text,
    new_values text,
    user_id    integer     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('create', 'update', 'delete'))
);
CREATE INDEX ix_audit_logs_record ON audit_logs(table_name, record_id);
CREATE INDEX ix_audit_logs_user ON audit_logs(user_id);
