# API Contract — Hệ thống Quản lý Lớp Học Thêm

> Nguồn chân lý cho backend (`java-backend/`) và frontend (`frontend/`). Bám SRS `requirements.md`.
> Đã chạy thật: Auth + Users. Phần còn lại (Classes → Reports) triển khai theo đúng file này.

## 0. Quy ước chung

- JSON **snake_case**. Lỗi: `{"error": "<tiếng Việt>"}`. Danh sách phân trang: `{"items": [...], "total": n, "page": p, "page_size": s}` (page từ 1, page_size mặc định 20, max 100).
- Ngày: `YYYY-MM-DD`. Giờ: `HH:mm`. Datetime: ISO-8601 UTC. Tiền: số nguyên VND.
- Mọi endpoint (trừ login/health) cần `Authorization: Bearer <JWT>`. 401 khi token sai; 403 khi thiếu quyền.
- **Quyền theo lớp** (NFR-2, chặn ở backend qua `ClassAccessService`):
  - `member` = admin HOẶC được gán vào lớp (GV chính/trợ giảng) — lỗi 403: `"Bạn không được gán vào lớp này"`.
  - `manager` = admin HOẶC GV chính của lớp — lỗi 403: `"Chỉ Admin hoặc giáo viên chính của lớp được thực hiện thao tác này"`.
  - `admin` = chỉ admin (đã chặn sẵn kiểu URL với /api/users, các module khác chặn trong service).
- Status codes: 200 mặc định, 201 tạo mới, 400 dữ liệu sai, 404 không tìm thấy (`"Không tìm thấy <đối tượng>"`), 409 trùng unique.
- Audit (NFR-4, qua `AuditService`): ghi cho hủy buổi, sửa điểm danh sau 24h, điều chỉnh phí, chuyển lớp, sửa/xóa cột điểm & điểm.

## 1. Classes — `/api/classes` (FR-2)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/classes?page&page_size&grade&status&school_year` | đã đăng nhập | Admin: tất cả lớp. GV/TG: chỉ lớp mình. Item: `{id, name, grade, school_year, start_date, end_date, default_monthly_fee, status, note, student_count, main_teacher: {id, full_name} \| null}` |
| POST | `/api/classes` | admin | Body: `{name, grade(6-9), school_year("2026-2027"), start_date, end_date, default_monthly_fee, note?}`. 201 `{class: {...}}` |
| GET | `/api/classes/{id}` | member | `{class: {..., staff: [{user_id, full_name, staff_role}], schedules: [{id, day_of_week, start_time, end_time}]}}` |
| PUT | `/api/classes/{id}` | admin | Partial (như PUT users): name, grade, school_year, start_date, end_date, default_monthly_fee, note, status(`active`/`closed`) |
| PUT | `/api/classes/{id}/staff` | admin | Body: `{main_teacher_id, assistant_ids: [..]}` — thay toàn bộ phân công. Validate: user tồn tại, active, main phải role teacher/admin? → chỉ cần user active, không trùng nhau. Trả về staff mới. |
| PUT | `/api/classes/{id}/schedules` | manager | Body: `[{day_of_week(1=T2..7=CN), start_time, end_time}]` — thay toàn bộ khung tuần. Validate end>start, không trùng (day,start). |
| POST | `/api/classes/{id}/generate-sessions` | manager | Sinh buổi học từ khung tuần trong [start_date, end_date] của lớp (FR-2.3). Idempotent: bỏ qua buổi đã tồn tại (unique class+date+start_time). Trả `{created: n, skipped: n}` |
| GET | `/api/classes/{id}/students` | member | Danh sách HS đang học lớp (enrollment mở): `{items: [{student_id, full_name, dob, gender, parent_phone, status, joined_at, custom_monthly_fee}]}` |

## 2. Students — `/api/students` (FR-3)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/students?page&page_size&q&class_id&grade&status` | đã đăng nhập | `q` tìm theo tên (LIKE, không dấu không cần). Admin: tất cả; GV/TG: chỉ HS đang học các lớp mình (kể cả khi không truyền class_id). Item: `{id, full_name, dob, gender, school_name, parent_name, parent_phone, enrollment_date, status, note, current_class: {id, name} \| null}` |
| POST | `/api/students` | admin hoặc GV chính (nếu kèm class_id lớp mình) | Body: `{full_name, dob?, gender?, school_name?, parent_name?, parent_phone?, enrollment_date, status?, note?, class_id?, custom_monthly_fee?}`. Nếu có `class_id` → tạo luôn enrollment (joined_at = enrollment_date). GV chính bắt buộc phải kèm class_id lớp mình. 201 `{student: {...}}` |
| GET | `/api/students/{id}` | admin hoặc staff của lớp HS đang/từng học | `{student: {..., enrollments: [{id, class_id, class_name, joined_at, left_at, leave_reason, transferred_to_class_id, custom_monthly_fee}]}}` |
| PUT | `/api/students/{id}` | admin hoặc GV chính lớp hiện tại | Partial các trường hồ sơ (không đổi lớp ở đây). `status` ∈ active/paused/left. |
| POST | `/api/students/{id}/transfer` | admin hoặc GV chính lớp hiện tại | FR-3.4. Body: `{to_class_id, effective_date}`. Đóng enrollment cũ (left_at=effective_date, leave_reason=transferred, transferred_to_class_id), tạo enrollment mới (joined_at=effective_date). Lớp đích phải active và khác lớp cũ. Audit. `{message: "Đã chuyển lớp"}` |
| POST | `/api/students/{id}/enroll` | admin hoặc GV chính lớp đích | Gán HS chưa có lớp vào lớp: `{class_id, joined_at, custom_monthly_fee?}`. Lỗi 400 nếu đang có enrollment mở. |
| PUT | `/api/students/{id}/custom-fee` | admin hoặc GV chính lớp hiện tại | `{custom_monthly_fee}` (số hoặc null = theo phí lớp) trên enrollment đang mở (FR-6.1). |

## 3. Sessions — `/api/sessions` (FR-2.3, FR-2.4)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/sessions?class_id&from&to&status&page&page_size` | member của class_id (bắt buộc truyền class_id nếu không phải admin) | Item: `{id, class_id, class_name, session_date, start_time, end_time, status, is_makeup, makeup_for_session_id, cancel_reason, note, attendance_count}` |
| POST | `/api/sessions` | manager của class_id | Tạo buổi bù/buổi lẻ: `{class_id, session_date, start_time, end_time, is_makeup?, makeup_for_session_id?, note?}`. 201. |
| PUT | `/api/sessions/{id}` | manager | Partial: session_date, start_time, end_time, note (chỉ buổi `scheduled`). |
| POST | `/api/sessions/{id}/cancel` | manager | `{cancel_reason}` bắt buộc → status=cancelled + audit (FR-2.4). Không hủy buổi đã cancelled. `{message: "Đã hủy buổi học"}` |

## 4. Attendance (FR-4)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/sessions/{id}/attendance` | member | `{session: {...}, items: [{student_id, full_name, status \| null, note, recorded_by_name, recorded_at}]}` — mọi HS đang học lớp tại ngày buổi học (enrollment mở tại session_date), merge với bản ghi điểm danh nếu có. |
| PUT | `/api/sessions/{id}/attendance` | member (TG được điểm danh — FR-4.2) | Bulk upsert: `{items: [{student_id, status(present/absent/late/excused), note?}]}`. **Khóa 24h**: nếu now > (session_date+start_time giờ VN → UTC) + 24h thì chỉ admin, lỗi 403 `"Đã quá 24h, chỉ Admin được sửa điểm danh"` + audit khi admin sửa sau 24h. Buổi cancelled → 400. Ghi recorded_by lần đầu, updated_by khi sửa. `{message: "Đã lưu điểm danh", saved: n}` |
| GET | `/api/attendance/stats?class_id&from&to` | member | FR-4.4: `{items: [{student_id, full_name, present, absent, late, excused, total_sessions, attendance_rate}]}` — trên các buổi scheduled đã qua trong range. |

## 5. Grades (FR-5)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/classes/{id}/grade-columns` | member | `{items: [{id, name, weight, max_score, column_date}]}` (bỏ is_deleted) |
| POST | `/api/classes/{id}/grade-columns` | manager | `{name, weight>0, max_score?=10, column_date?}`. 201. |
| PUT | `/api/grade-columns/{id}` | manager (của lớp cột đó) | Partial: name, weight, max_score, column_date. Audit. |
| DELETE | `/api/grade-columns/{id}` | manager | Soft delete `is_deleted=1` (điểm giữ nguyên — NFR-3). Audit. `{message: "Đã xóa cột điểm"}` |
| GET | `/api/classes/{id}/grades` | member | Ma trận: `{columns: [...], items: [{student_id, full_name, scores: {"<column_id>": <score \| null>}, average: <float 2 số lẻ \| null>}]}`. **Điểm TB có trọng số** (FR-5.3): `Σ(score/max_score*10*weight) / Σ(weight)` trên các cột HS **có điểm**. HS = đang học lớp (enrollment mở). |
| PUT | `/api/grades` | member (TG nhập được — FR-5.2) | Bulk upsert: `{items: [{grade_column_id, student_id, score}]}` — validate 0 ≤ score ≤ max_score của cột (`"Điểm phải trong khoảng 0..<max>"`), các cột phải cùng 1 lớp mà user là member. Audit khi SỬA điểm đã có. `{message: "Đã lưu điểm", saved: n}` |
| GET | `/api/classes/{id}/grades/export` | member | FR-5.4: CSV UTF-8 có BOM (Excel mở được tiếng Việt), `Content-Disposition: attachment; filename="bang-diem-<class>.csv"`. Cột: Họ tên, từng cột điểm, Điểm TB. |

## 6. Fees — `/api/fees` (FR-6)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/fees/generate` | admin | FR-6.2: `{year, month}` → sinh khoản phí cho mọi HS active + enrollment mở + lớp active (port từ `backend/src/services/fees.js`). base_amount = custom_monthly_fee ?? default_monthly_fee. Idempotent (unique student+class+year+month). Trong transaction. `{created: n, skipped: n}` |
| GET | `/api/fees?class_id&year&month&status&page&page_size` | member của class_id; admin không cần class_id | Item: `{id, student_id, student_name, class_id, class_name, fee_year, fee_month, base_amount, adjusted_amount, adjustment_reason, status, amount_due, paid_total}`. amount_due = adjusted ?? base; paid_total = Σ payments. **Trợ giảng xem được** (FR-6.5 cho phép thấy trạng thái). |
| PUT | `/api/fees/{id}/adjust` | manager | FR-6.3: `{adjusted_amount, adjustment_reason}` — reason BẮT BUỘC (400 `"Phải ghi lý do điều chỉnh"`). Lưu adjusted_by. Cập nhật lại status theo tổng đã đóng. Audit. |
| POST | `/api/fees/{id}/payments` | member (TG ghi nhận được — FR-6.4) | `{amount>0, method(cash/transfer), paid_at(ISO), note?}` → thêm payment, recorded_by = user. Tự tính lại status: paid ≥ due → `paid`, >0 → `partial`, =0 → `unpaid`. 201 `{payment: {...}, fee_status: "..."}` |
| GET | `/api/fees/{id}/payments` | member | `{items: [{id, amount, method, paid_at, recorded_by_name, note}]}` |

## 7. Reports — `/api/reports` (FR-7)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/reports/attendance?class_id&from&to&threshold` | member | FR-7.1: `{summary: {total_sessions, avg_rate}, items: [<như attendance/stats>], frequent_absentees: [HS có absent ≥ threshold (mặc định 3)]}` |
| GET | `/api/reports/revenue?year&month&class_id?` | admin (toàn bộ) / GV chính (chỉ lớp mình dạy — truyền class_id hoặc trả các lớp mình) | **Trợ giảng 403** (FR-6.5). `{items: [{class_id, class_name, total_due, total_paid, unpaid_count, partial_count, paid_count}], grand_total_paid, grand_total_due}` |
| GET | `/api/reports/dashboard` | admin | FR-7.4: `{active_students, classes_by_grade: {"6": n, ...}, new_students_this_month, left_students, unpaid_fees_this_month}` |

## 8. Frontend routes (React)

| Route | Ai thấy | Trang |
|---|---|---|
| `/` | tất cả | Tổng quan: admin = dashboard (FR-7.4); GV/TG = danh sách lớp mình + lối tắt |
| `/users` | admin | (đã có) |
| `/classes`, `/classes/:id` | tất cả (dữ liệu theo quyền) | Danh sách lớp; chi tiết lớp tabs: Thông tin & Nhân sự / Lịch & Buổi học / Học sinh |
| `/students`, `/students/:id` | tất cả | Danh sách + tìm kiếm; hồ sơ + lịch sử lớp + chuyển lớp |
| `/attendance` | tất cả | Chọn lớp → chọn buổi → bảng điểm danh 4 trạng thái, mobile-first (NFR-1) |
| `/grades` | tất cả | Chọn lớp → ma trận điểm, sửa inline, quản lý cột (manager), nút xuất CSV |
| `/fees` | tất cả | Chọn lớp + tháng → danh sách khoản phí, ghi nhận đóng tiền, điều chỉnh (manager), sinh phí tháng (admin) |
| `/reports` | GV chính + admin (doanh thu); member (chuyên cần) | Tabs: Chuyên cần / Doanh thu |
| `/doi-mat-khau` | tất cả | (đã có) |
