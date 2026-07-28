# Local Workflow Permissions, Weekly Timetable, Excel Import, and Audit Design

## Context

The Class Management application runs locally with a React/Vite frontend, a Spring Boot backend, and SQL Server. The existing system already supports class-scoped access, flexible grade columns, student transfers, session-based attendance, fees, and a partial `audit_logs` implementation. This design extends those capabilities without replacing existing routes or changing historical academic and financial records.

## Goals

- Give assistants the approved class-scoped grade-column, transfer, and Excel-import capabilities.
- Restrict assistant attendance edits to the approved session-time window.
- Preserve individual fee and payment workflows for assistants while hiding aggregate financial totals.
- Add a responsive weekly timetable to the overview for every role.
- Add atomic student import from a downloadable Excel template.
- Add a permanent, categorized Admin audit-log page for all data-changing operations and authentication events.
- Add stable, human-readable student codes without replacing internal numeric IDs.

## Non-goals

- Do not replace the internal numeric student primary key.
- Do not rewrite existing academic, attendance, fee, or enrollment history.
- Do not allow audit logs to be edited or deleted from the application.
- Do not log page views, searches, passwords, JWTs, or complete uploaded workbooks.
- Do not use Excel import to update existing students.

## Authorization and Business Rules

### Fees

Admin and main teachers retain their current detailed fee views. An assistant assigned to a class can continue to see per-student fee amounts, payment history, and record payments for that class. The assistant cannot adjust fee amounts.

For assistants, the fee summary shows only the count of students whose fee status is not `paid`. It must not render aggregate total due, total paid, remaining amount, or collection percentage. If a dedicated summary response is introduced, the assistant variant contains only the unpaid-student count. Assistants still receive the approved per-student amounts, so this requirement is presentation redaction rather than a claim that totals cannot be derived from authorized row data.

### Grade columns

Admin, a class's main teacher, and an assistant assigned to that class can create, update, and soft-delete grade columns. Grade entry retains the existing class-member rule. Users unrelated to the class receive `403`.

### Student transfers

An assistant can transfer a student only when assigned to the student's current/source class. The destination may be any active class, including a class to which the assistant is not assigned. Existing validation remains in force: the destination differs from the source, the effective date is valid, and the source enrollment is open.

A transfer closes the source enrollment and creates a destination enrollment. Historical grades, attendance, and fees remain attached to the source class. The transfer audit entry includes student ID/code, source class, destination class, effective date, and actor.

### Attendance edit windows

All times use the existing Vietnam timezone rules.

- Assistant: may create or edit attendance from 30 minutes before session start through 30 minutes after session end, inclusive at both boundaries.
- Main teacher: retains the current 24-hour window measured from session start.
- Admin: remains unrestricted by elapsed time.
- Cancelled sessions remain non-editable for every role.

The backend is authoritative. The attendance response exposes enough permission/window state for the frontend to disable controls and explain the allowed interval. An out-of-window write returns `403` even if a client bypasses the UI.

### Excel import

Admin can import into any active class. Main teachers and assistants can import only into a class to which they are assigned. This import permission does not broaden any unrelated student-edit or fee-adjustment permission.

## Student Codes

Add an immutable, unique `student_code` to `students`. Codes use `HS` followed by a six-digit, zero-padded internal ID, such as `HS000001`. Existing rows are backfilled from their current IDs. New records receive their code immediately after insertion within the same transaction. Numeric IDs remain the database and API relationship keys.

Student codes appear in student lists, student details, search results, Excel import results, and audit descriptions. Search accepts either student code or the existing text criteria.

## Weekly Timetable

### Data source and access

The timetable uses actual `sessions`, not only recurring `class_schedules`, so cancelled and makeup sessions are represented accurately. A new range endpoint accepts `from` and `to` dates and returns only sessions in visible classes:

- Admin sees all classes.
- Main teachers and assistants see assigned classes only.

The default range is Monday through Sunday of the current week. The overview provides previous-week, current-week, and next-week controls. Each event includes only existing session/class data required by the UI: session ID, class ID/name, date, start/end time, status, makeup indicator, and attendance count or class student count where already supported.

Clicking an event navigates to the existing `/classes/{classId}` route.

### Responsive presentation

- Desktop: seven day columns with dynamic chronological rows derived from actual start times. Concurrent sessions stack within the same time cell. Cancelled sessions are muted and labelled; makeup sessions receive a distinct label.
- Tablet: the same timetable remains available in a horizontally scrollable region with the time column kept visible.
- Mobile: a day selector and chronological agenda cards replace the squeezed seven-column table. Every event and class link remains accessible.

The timetable appears below the overview greeting and before existing statistics, charts, or assigned-class cards. Existing overview content remains intact.

## Excel Template and Import

### User flow

1. Open the Students page.
2. Select one permitted active destination class.
3. Download that class's `.xlsx` template or upload a completed template.
4. The backend validates the entire workbook without writing data.
5. If any row fails, the response lists row, column, and reason, and no student or enrollment is created.
6. If every row passes, students and enrollments are created in a single transaction.
7. The UI reports the number imported and generated student-code range/list.

### Template columns

| Column | Requirement |
| --- | --- |
| Họ và tên | Required |
| Ngày sinh | Required for Excel import; `dd/MM/yyyy` |
| Giới tính | Optional; Nam/Nữ/Khác mapped to existing values |
| Trường đang học | Optional |
| Tên phụ huynh | Optional |
| SĐT phụ huynh | Required for Excel import |
| Ngày vào học | Required; `dd/MM/yyyy` |
| Học phí riêng | Optional; blank uses class default |
| Ghi chú | Optional |

The selected destination class is not a workbook column. New imported students use the existing active default status.

### Duplicate detection

Normalize names by trimming, collapsing repeated whitespace, and comparing case-insensitively. Normalize parent phone numbers by removing spaces, dots, and hyphens. A row is a duplicate when normalized full name, date of birth, and normalized parent phone match either an existing student or an earlier workbook row.

Any duplicate is a validation error and rejects the entire import. Import creates new records only and never silently updates a match.

### Audit behavior

A successful import writes one batch audit entry with original filename, destination class, actor, count, and generated student codes, plus the normal student-creation audit entry for each created student. The workbook contents are not stored. A rejected import makes no data-change audit entry because nothing was committed.

## Audit Log

### Storage

Extend the existing audit model through vendor-specific Flyway migrations for SQL Server and PostgreSQL. Add a normalized `category` suitable for indexed filtering. Allow `user_id` and `record_id` to be null for authentication failures that have neither a resolved user nor domain record, and expand action values beyond the current create/update/delete constraint to cover authentication and import events.

Categories are:

- `authentication`
- `students`
- `classes`
- `sessions`
- `attendance`
- `grades`
- `fees`
- `users`
- `imports`

Every successful state-changing operation writes an audit record in the same transaction as the business change. Authentication success/failure is recorded separately because authentication is not part of a domain transaction. Failed-login metadata may contain the submitted email and a generic reason but never a password, password hash, or token.

### Admin API and page

Only Admin can access the audit endpoint and `/logs` route. The endpoint is server-paginated and supports category, actor, action, date range, and text filters. Results include timestamp, actor summary when available, category, action, target description, and before/after JSON.

Desktop uses an information-dense table. Selecting a row opens a detail drawer for before/after values. Mobile uses record cards with expandable details. The page is read-only and provides no deletion or mutation endpoint.

## Error Handling

- Permission violations return `403`.
- Invalid input and workbook validation failures return `400` with safe, field-specific details.
- Missing records return `404`.
- Attendance lock errors state the permitted interval.
- Workbook errors identify row, column, and reason without returning stack traces.
- Audit serialization or persistence failure causes the associated domain transaction to fail instead of committing an unlogged change.

## Compatibility and Data Integrity

Existing route URLs and existing request bodies remain valid. New response fields are additive. Existing numeric IDs and foreign keys remain unchanged. Student transfer remains transactional. Excel import is atomic. Grade-column deletion remains soft deletion. Both SQL Server and PostgreSQL migrations receive equivalent schema changes.

## Testing Strategy

Backend tests cover role matrices for fees, grade columns, transfers, imports, logs, and timetable visibility. Attendance clock tests cover just before, exactly at, inside, exactly at the closing boundary, and just after the assistant window, plus the unchanged teacher/Admin behavior. Tests verify that aggregate fee totals are absent from assistant responses.

Excel tests cover a valid workbook, every required-field/date/value failure, duplicate workbook rows, database duplicates, unauthorized classes, and complete rollback. Student-code tests cover backfill format, uniqueness, new-record generation, and search.

Audit tests cover every category, actor and anonymous authentication events, filtering/pagination, transaction rollback, and secret redaction. Frontend tests cover permission-driven controls, fee-summary redaction, timetable layouts/navigation, attendance locked states, import errors, and audit-log filters. Final verification runs the complete backend suite, frontend suite, and production frontend build.
