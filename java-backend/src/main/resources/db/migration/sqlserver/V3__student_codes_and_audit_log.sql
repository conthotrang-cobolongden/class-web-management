-- Ma hoc sinh hien thi: giu nguyen khoa chinh id, backfill theo id hien co.
ALTER TABLE students ADD student_code VARCHAR(30) NULL;
GO

UPDATE students
SET student_code = CONCAT(
    'HS',
    CASE
        WHEN LEN(CAST(id AS VARCHAR(20))) >= 6 THEN CAST(id AS VARCHAR(20))
        ELSE RIGHT(REPLICATE('0', 6) + CAST(id AS VARCHAR(20)), 6)
    END
);

CREATE UNIQUE NONCLUSTERED INDEX ux_students_student_code ON students(student_code);

-- Mo rong audit log cho phan loai, mo ta va su kien dang nhap that bai.
ALTER TABLE audit_logs ADD category VARCHAR(30) NULL;
ALTER TABLE audit_logs ADD description NVARCHAR(300) NULL;
GO

UPDATE audit_logs
SET category = CASE table_name
    WHEN 'users' THEN 'users'
    WHEN 'students' THEN 'students'
    WHEN 'enrollments' THEN 'students'
    WHEN 'classes' THEN 'classes'
    WHEN 'class_staff' THEN 'classes'
    WHEN 'class_schedules' THEN 'sessions'
    WHEN 'sessions' THEN 'sessions'
    WHEN 'attendance' THEN 'attendance'
    WHEN 'grade_columns' THEN 'grades'
    WHEN 'grades' THEN 'grades'
    WHEN 'monthly_fees' THEN 'fees'
    WHEN 'payments' THEN 'fees'
    ELSE 'classes'
END;

ALTER TABLE audit_logs ALTER COLUMN category VARCHAR(30) NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN record_id INT NULL;
ALTER TABLE audit_logs ALTER COLUMN user_id INT NULL;

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
GO
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action CHECK (
    action IN ('create', 'update', 'delete', 'login_success', 'login_failure', 'import')
);

CREATE NONCLUSTERED INDEX ix_audit_logs_category_created
    ON audit_logs(category, created_at DESC);
