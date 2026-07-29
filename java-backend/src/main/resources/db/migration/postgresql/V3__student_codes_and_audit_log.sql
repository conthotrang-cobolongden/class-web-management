-- Ma hoc sinh hien thi: giu nguyen khoa chinh id, backfill theo id hien co.
ALTER TABLE students ADD COLUMN student_code varchar(30);

UPDATE students
SET student_code = 'HS' || CASE
    WHEN length(id::text) >= 6 THEN id::text
    ELSE lpad(id::text, 6, '0')
END;

CREATE UNIQUE INDEX ux_students_student_code ON students(student_code);

-- Mo rong audit log cho phan loai, mo ta va su kien dang nhap that bai.
ALTER TABLE audit_logs ADD COLUMN category varchar(30);
ALTER TABLE audit_logs ADD COLUMN description varchar(300);

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

ALTER TABLE audit_logs ALTER COLUMN category SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN record_id DROP NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action CHECK (
    action IN ('create', 'update', 'delete', 'login_success', 'login_failure', 'import')
);

CREATE INDEX ix_audit_logs_category_created
    ON audit_logs(category, created_at DESC);
