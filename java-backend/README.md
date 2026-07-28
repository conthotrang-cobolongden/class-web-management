# Class Management — Backend Java (Spring Boot)

API quản lý lớp học thêm theo [requirements.md](../requirements.md). Bản Java thay cho backend Node cũ, giữ nguyên **API contract** (đường dẫn, JSON snake_case, thông báo lỗi tiếng Việt) nên frontend React dùng chung được.

## Stack

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ / framework | Java 21, Spring Boot 3.5 |
| DB access | Spring Data JPA (Hibernate) — đổi dialect qua config (NFR-5) |
| Migration | Flyway, thư mục theo vendor: `db/migration/sqlserver` (test) và `db/migration/postgresql` (Supabase) |
| Xác thực | JWT HS256 (jjwt), bcrypt |
| DB test | SQL Server 2022 local, port 1433 |

## Chạy lần đầu

1. **Tạo database + login** — trên máy dev hiện tại **đã có sẵn** (DB `ClassManagement` + login `class_app` từ hồi test bản Node, Flyway sẽ tự baseline bỏ qua V1/V2). Máy mới thì chạy một lần bằng quyền admin của SQL Server:

   ```
   sqlcmd -S tcp:localhost,1433 -E -C -i ..\database\setup.sqlserver.sql
   ```

   > Cần instance bật **Mixed Mode authentication** (SQL + Windows). Nếu chưa bật: SSMS → Server Properties → Security → "SQL Server and Windows Authentication mode", rồi restart service.

2. **Chạy app** (cần JDK 21+ và Maven):

   ```
   mvn spring-boot:run
   ```

   Lần đầu khởi động, Flyway tự chạy `V1__schema.sql` (13 bảng) + `V2__seed.sql` (dữ liệu mẫu), sau đó app tự thay hash placeholder trong seed bằng bcrypt thật.

3. **Đăng nhập thử**: mọi tài khoản seed có mật khẩu `Admin@123`:

   ```
   curl -X POST http://localhost:4000/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@trungtam.vn\",\"password\":\"Admin@123\"}"
   ```

## Cấu hình (biến môi trường)

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `PORT` | `4000` | |
| `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=ClassManagement;...` | Port tĩnh 1433 (kết nối theo instance name `RYAN_SERVER` không dùng được vì SQL Browser tắt) |
| `DB_USER` / `DB_PASSWORD` | `class_app` / `ClassApp@2026!` | Trùng với `setup.sqlserver.sql` |
| `JWT_SECRET` | (secret dev) | **Đổi khi chạy thật** — tối thiểu 32 byte (yêu cầu HS256; secret cũ của bản Node ngắn hơn sẽ bị từ chối lúc khởi động). Dùng secret dev sẽ có cảnh báo WARN trong log. |
| `JWT_EXPIRES_IN` | `12h` | Định dạng Duration của Spring (`12h`, `30m`...) |

## API

**Đầy đủ theo SRS** — 37 endpoint / 8 nhóm: Auth, Users, Classes (kèm nhân sự, khung tuần, sinh buổi học), Students (kèm chuyển lớp, phí ưu đãi), Sessions (hủy/bù), Attendance (khóa 24h), Grades (điểm trọng số + xuất CSV), Fees (sinh phí tháng, điều chỉnh, thanh toán), Reports (chuyên cần, doanh thu, dashboard). Contract chi tiết: [docs/API.md](../docs/API.md). Bảng dưới là nhóm Auth + Users:

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/health` | công khai | Kiểm tra sống |
| POST | `/api/auth/login` | công khai | Đăng nhập → `{token, user}` (FR-1.1) |
| GET | `/api/auth/me` | đã đăng nhập | Tài khoản hiện tại |
| POST | `/api/auth/change-password` | đã đăng nhập | Tự đổi mật khẩu (FR-1.2) |
| GET | `/api/users?page&page_size&role&active` | admin | Danh sách tài khoản, phân trang |
| POST | `/api/users` | admin | Tạo tài khoản (FR-1.2) |
| PUT | `/api/users/{id}` | admin | Sửa từng phần; truyền `password` để reset |
| POST | `/api/users/{id}/deactivate` | admin | Vô hiệu hóa — không xóa cứng (FR-1.3) |
| POST | `/api/users/{id}/activate` | admin | Kích hoạt lại |

Các module tiếp theo (classes, students, sessions, attendance, grades, fees, reports) sẽ thêm sau, theo đúng SRS.

## Migrate sang Supabase (Phase 2)

Đổi 3 biến môi trường là xong phần hạ tầng:

```
DB_URL=jdbc:postgresql://<project>.supabase.co:5432/postgres?sslmode=require
DB_USER=postgres
DB_PASSWORD=...
```

Flyway tự nhận vendor `postgresql` và chạy `V1__schema.sql` + `V2__seed.sql` trong `db/migration/postgresql` (DB trống sẽ có đủ 13 bảng + dữ liệu mẫu, đăng nhập được ngay bằng `admin@trungtam.vn` / `Admin@123` — app tự hash mật khẩu ở lần khởi động đầu). Code JPA không đổi (NFR-5: không dùng tính năng riêng của SQL Server). Nhớ đặt `JWT_SECRET` riêng khi deploy.
