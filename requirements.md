# Tài liệu Yêu cầu Phần mềm (SRS) — Hệ thống Quản lý Lớp Học Thêm

> Phiên bản: 1.1 — Ngày: 26/07/2026
> Trạng thái: Đã chốt yêu cầu (các câu hỏi mở ở mục 7 đã được trả lời và gộp vào nội dung)
> Phạm vi: Phase 1 (MVP). Các mục đánh dấu **[Phase 2]** chưa làm ở bản đầu.

---

## 1. Tổng quan

### 1.1 Mục đích
Web app quản lý trung tâm học thêm quy mô nhỏ (1 trung tâm, 1 môn học duy nhất): quản lý lớp, học sinh, giảng viên/trợ giảng, điểm danh, điểm số, lịch học và học phí.

### 1.2 Đối tượng sử dụng
Chỉ **nội bộ trung tâm**: Admin, Giảng viên chính, Trợ giảng. Học sinh và phụ huynh **không có tài khoản** ở Phase 1.

### 1.3 Công nghệ
| Thành phần | Lựa chọn |
|---|---|
| Frontend | React |
| Backend | Node.js (REST API) |
| Database | SQL Server (giai đoạn test) → **sẽ migrate sang Supabase (PostgreSQL)** |
| Xác thực | Email + mật khẩu, JWT |

**Ràng buộc kỹ thuật quan trọng:** thiết kế DB và query phải **tránh tính năng riêng của SQL Server** (không dùng stored procedure đặc thù, `NVARCHAR(MAX)` xử lý được bằng `TEXT`, dùng UUID hoặc identity chuẩn, ORM/query builder hỗ trợ cả 2 dialect — khuyến nghị Prisma/Knex) để migrate sang Postgres/Supabase ít đau nhất.

### 1.4 Quy mô
- 4 khối: **6, 7, 8, 9**; mỗi khối có thể mở nhiều lớp (6A, 6B, ...).
- Chỉ **1 môn học duy nhất** → không cần bảng Môn học.
- Quy mô nhỏ: vài chục lớp, vài trăm học sinh.

---

## 2. Vai trò và phân quyền

| Chức năng | Admin | GV chính | Trợ giảng |
|---|:---:|:---:|:---:|
| Tạo/sửa/xóa tài khoản người dùng | ✅ | ❌ | ❌ |
| Tạo/sửa/xóa lớp, gán GV & trợ giảng vào lớp | ✅ | ❌ | ❌ |
| Thêm/sửa hồ sơ học sinh, gán HS vào lớp, chuyển lớp | ✅ | ✅ (lớp mình) | ❌ |
| Xem danh sách HS lớp mình | ✅ | ✅ | ✅ |
| Điểm danh (trong vòng 24h) | ✅ | ✅ | ✅ |
| Sửa điểm danh **sau 24h** | ✅ | ❌ | ❌ |
| Tạo/sửa cột điểm + trọng số | ✅ | ✅ (lớp mình) | ❌ |
| Nhập/sửa điểm | ✅ | ✅ | ✅ |
| Quản lý lịch học (khung giờ, buổi bù, hủy buổi) | ✅ | ✅ (lớp mình) | ❌ |
| Ghi nhận đóng học phí (trạng thái, ngày giờ, số tiền) | ✅ | ✅ | ✅ |
| Điều chỉnh số tiền phải đóng của tháng (trừ khi hủy buổi) | ✅ | ✅ (lớp mình) | ❌ |
| Xem **doanh thu tháng** | ✅ | ✅ (lớp mình dạy) | ❌ |
| Xem báo cáo chuyên cần, bảng điểm | ✅ | ✅ (lớp mình) | ✅ (lớp mình) |
| Dashboard tổng quan toàn trung tâm | ✅ | ❌ | ❌ |

Ghi chú:
- Một lớp có **1 GV chính** và **nhiều trợ giảng** (0..n).
- "Lớp mình" = lớp mà user được gán (GV chính hoặc trợ giảng).
- Tài khoản do **Admin tạo toàn bộ**, không có form đăng ký tự do.

---

## 3. Yêu cầu chức năng

### FR-1. Xác thực & tài khoản
- FR-1.1: Đăng nhập bằng email + mật khẩu, trả về JWT (access token; refresh token nếu cần phiên dài).
- FR-1.2: Admin tạo tài khoản, gán vai trò (`admin` / `teacher` / `assistant`), đặt mật khẩu ban đầu; user tự đổi mật khẩu sau.
- FR-1.3: Admin có thể vô hiệu hóa (deactivate) tài khoản — không xóa cứng để giữ audit.
- FR-1.4: Mật khẩu hash bằng bcrypt/argon2. Mọi API (trừ login) yêu cầu JWT hợp lệ; kiểm tra quyền theo vai trò + lớp được gán ở tầng backend.

### FR-2. Quản lý lớp học
- FR-2.1: CRUD lớp: tên lớp (VD "6A"), khối (6/7/8/9), **khoảng thời gian năm học (ngày bắt đầu – ngày kết thúc, do Admin/GV tự đặt khi tạo lớp — không hard-code tháng)**, phí tháng mặc định, trạng thái (đang hoạt động / đã đóng).
- FR-2.2: Gán 1 GV chính + nhiều trợ giảng cho lớp; thay đổi được.
- FR-2.3: Mỗi lớp có **khung giờ cố định theo tuần** (VD: Thứ 3 + Thứ 5, 18:00–20:00) áp dụng xuyên suốt năm học. Hệ thống tự sinh danh sách buổi học từ khung giờ này.
- FR-2.4: Với buổi lẻ, người có quyền có thể:
  - **Hủy buổi** (lưu lý do) → tháng đó được đánh dấu "có buổi hủy" để admin/GV **trừ tiền thủ công** vào khoản phí tháng (xem FR-6.3).
  - **Tạo buổi bù** vào ngày giờ khác (đánh dấu là buổi bù, có thể liên kết tới buổi bị hủy). Buổi bù điểm danh bình thường.
- FR-2.5: Không hỗ trợ học sinh "học ké" lẻ buổi ở lớp khác. Chỉ có **chuyển hẳn lớp** (FR-3.4).

### FR-3. Quản lý học sinh
- FR-3.1: CRUD hồ sơ học sinh với các trường:
  - Cơ bản: họ tên, ngày sinh, giới tính, trường đang học chính khóa.
  - Liên hệ: tên phụ huynh, SĐT phụ huynh (chỉ text, không phải tài khoản).
  - Học tập: ngày vào học, trạng thái (`đang học` / `tạm nghỉ` / `đã nghỉ`).
  - Ghi chú tự do của GV.
- FR-3.2: Gán học sinh vào lớp (1 HS thuộc 1 lớp tại 1 thời điểm — vì chỉ có 1 môn).
- FR-3.3: Tìm kiếm/lọc học sinh theo tên, lớp, khối, trạng thái.
- FR-3.4: **Chuyển lớp**: chuyển hẳn HS từ lớp A → lớp B.
  - Giữ nguyên toàn bộ lịch sử (điểm, điểm danh, học phí) ở lớp cũ để tra cứu.
  - Lớp mới bắt đầu ghi dữ liệu mới.
  - Lưu lịch sử chuyển: ngày chuyển, từ lớp → đến lớp, người thao tác.

### FR-4. Điểm danh
- FR-4.1: Điểm danh theo từng buổi học, mỗi HS một trạng thái: **Có mặt / Vắng / Muộn / Có phép**.
- FR-4.2: GV chính và trợ giảng điểm danh và sửa được **trong vòng 24h** kể từ giờ bắt đầu buổi học. Sau 24h bản ghi khóa, **chỉ Admin** sửa được.
- FR-4.3: Lưu người điểm danh + thời gian thao tác (audit).
- FR-4.4: Thống kê chuyên cần theo HS và theo lớp: số buổi có mặt/vắng/muộn/có phép, tỷ lệ đi học.

### FR-5. Quản lý điểm
- FR-5.1: **Cột điểm linh hoạt theo lớp**: GV chính tạo cột điểm (tên, trọng số, thang điểm mặc định 10, ngày). VD: "BT tuần 1" ×1, "Kiểm tra giữa kỳ" ×2.
- FR-5.2: GV chính + trợ giảng nhập/sửa điểm từng HS theo cột; hỗ trợ nhập nhanh cả lớp trên 1 màn hình (dạng bảng).
- FR-5.3: Hệ thống tự tính **điểm trung bình có trọng số** = Σ(điểm × trọng số) / Σ(trọng số các cột đã có điểm).
- FR-5.4: Xuất bảng điểm lớp ra **Excel/CSV**.
- FR-5.5: HS chuyển lớp: điểm ở lớp cũ giữ nguyên tra cứu được, không mang sang lớp mới.

### FR-6. Học phí
- FR-6.1: Mô hình: **phí tháng cố định** cho mỗi lớp (đặt ở cấu hình lớp, có thể ghi đè cho từng HS nếu có ưu đãi).
- FR-6.2: Mỗi tháng, mỗi HS đang học có 1 **khoản phí tháng** (fee record): số tiền phải đóng, trạng thái (`chưa đóng` / `đã đóng` / `đóng một phần`). Khoản phí được **hệ thống sinh tự động đầu tháng** theo phí tháng của lớp; Admin/GV chính **điều chỉnh tay được** khi tháng đó có vấn đề (hủy buổi, tạm nghỉ, ưu đãi...).
- FR-6.2b: HS `tạm nghỉ` giữa tháng: khoản phí tháng đó **không tự tính lại** — Admin/GV chính điều chỉnh tay (kèm lý do). HS ở trạng thái `tạm nghỉ`/`đã nghỉ` từ đầu tháng thì không sinh khoản phí tháng mới.
- FR-6.3: Khi tháng có buổi hủy, Admin/GV chính **điều chỉnh thủ công** số tiền phải đóng của tháng đó (lưu số tiền điều chỉnh + lý do). Hệ thống KHÔNG tự trừ.
- FR-6.4: Ghi nhận thanh toán (Admin/GV chính/Trợ giảng đều làm được), mỗi lần đóng lưu:
  - Ngày giờ đóng, người ghi nhận (tài khoản), số tiền thực đóng, hình thức (**tiền mặt / chuyển khoản**), ghi chú.
  - Cho phép đóng thiếu/thừa; hệ thống tự cập nhật trạng thái khoản phí theo tổng đã đóng.
  - **Không cần in phiếu thu.**
- FR-6.5: **Trợ giảng KHÔNG xem được doanh thu** — chỉ thấy trạng thái đóng/chưa đóng của từng HS trong lớp mình. Doanh thu tháng: Admin xem toàn trung tâm, GV chính xem lớp mình dạy.

### FR-7. Báo cáo & Dashboard
- FR-7.1: **Chuyên cần theo lớp**: tỷ lệ đi học theo khoảng thời gian, danh sách HS vắng nhiều (ngưỡng cấu hình được).
- FR-7.2: **Bảng điểm tổng hợp lớp**: xem trên web + xuất Excel/CSV.
- FR-7.3: **Học phí**: danh sách HS chưa đóng/đóng một phần theo tháng; tổng doanh thu tháng (theo quyền ở FR-6.5).
- FR-7.4: **Dashboard tổng quan** (Admin): tổng HS đang học, số lớp theo khối, HS mới trong tháng, HS nghỉ học.

### [Phase 2] — Ngoài phạm vi bản đầu
- Tài khoản học sinh (xem điểm, chuyên cần, lịch học, thông báo).
- Tài khoản phụ huynh + liên kết PH ↔ HS, thông báo cho phụ huynh.
- Thông báo tự động (SMS/Zalo) khi HS vắng hoặc chưa đóng phí.
- Migrate sang Supabase (Postgres + Supabase Auth).

---

## 4. Luồng nghiệp vụ chính

### 4.1 Vòng đời một lớp
1. Admin tạo lớp 6A (khối 6, năm học 2026–2027, phí tháng 500k, lịch T3+T5 18:00–20:00), gán GV chính + trợ giảng.
2. Hệ thống sinh buổi học theo lịch tuần cho năm học.
3. Admin/GV thêm HS vào lớp.
4. Hàng buổi: GV/trợ giảng điểm danh.
5. Hàng tháng: hệ thống sinh khoản phí tháng cho từng HS đang học; GV/trợ giảng ghi nhận đóng tiền khi HS nộp.
6. GV tạo cột điểm, nhập điểm sau mỗi bài kiểm tra.

### 4.2 Hủy buổi / học bù
1. GV chính hủy buổi T3 tuần sau, ghi lý do.
2. Tùy GV: (a) tạo buổi bù CN cùng tuần — không ảnh hưởng học phí; hoặc (b) không bù — admin/GV chính điều chỉnh giảm tiền tháng đó cho HS của lớp (thao tác tay, có lưu lý do).

### 4.3 Chuyển lớp
1. Admin/GV chính chọn HS ở lớp 6A → chuyển sang 6B, ghi ngày hiệu lực.
2. Từ ngày đó HS xuất hiện trong danh sách điểm danh/điểm/học phí của 6B; dữ liệu cũ ở 6A đóng băng, tra cứu được trong hồ sơ HS.

---

## 5. Yêu cầu phi chức năng

| Mã | Yêu cầu |
|---|---|
| NFR-1 | Giao diện tiếng Việt, responsive (GV dùng điện thoại điểm danh tại lớp). |
| NFR-2 | Phân quyền kiểm tra ở backend (không chỉ ẩn nút ở UI). |
| NFR-3 | Không xóa cứng dữ liệu nghiệp vụ (soft delete / trạng thái) — phục vụ tra cứu lịch sử và audit. |
| NFR-4 | Các thao tác nhạy cảm (sửa điểm danh sau 24h, điều chỉnh học phí, chuyển lớp) đều lưu audit: ai, lúc nào, giá trị cũ/mới. |
| NFR-5 | Schema và query tương thích cả SQL Server và PostgreSQL (phục vụ migrate Supabase). |
| NFR-6 | Quy mô mục tiêu: ≤ 50 lớp, ≤ 1.000 HS, ≤ 30 tài khoản — không cần tối ưu hiệu năng đặc biệt, nhưng danh sách phải phân trang. |
| NFR-7 | Múi giờ cố định Asia/Ho_Chi_Minh (lưu UTC trong DB, hiển thị giờ VN). |

---

## 6. Thực thể dữ liệu chính (mức khái niệm)

- **users** (id, họ tên, email, hash mật khẩu, vai trò, trạng thái)
- **classes** (id, tên, khối, năm học, phí tháng mặc định, trạng thái)
- **class_staff** (class_id, user_id, vai trò trong lớp: chính/trợ giảng)
- **class_schedules** (class_id, thứ trong tuần, giờ bắt đầu/kết thúc)
- **sessions** — buổi học (id, class_id, ngày, giờ, trạng thái: bình thường/hủy/bù, lý do, liên kết buổi gốc nếu là buổi bù)
- **students** (id, họ tên, ngày sinh, giới tính, trường chính khóa, tên + SĐT phụ huynh, ngày vào học, trạng thái, ghi chú)
- **enrollments** (student_id, class_id, ngày vào lớp, ngày rời lớp, lý do rời: chuyển lớp/nghỉ) — lịch sử chuyển lớp nằm ở đây
- **attendance** (session_id, student_id, trạng thái, người ghi, thời gian ghi)
- **grade_columns** (class_id, tên cột, trọng số, thang điểm, ngày)
- **grades** (grade_column_id, student_id, điểm, người nhập, thời gian)
- **monthly_fees** (student_id, class_id, tháng/năm, số tiền gốc, số tiền sau điều chỉnh, lý do điều chỉnh, trạng thái)
- **payments** (monthly_fee_id, số tiền, hình thức, ngày giờ, người ghi nhận, ghi chú)
- **audit_logs** (bảng, bản ghi, hành động, giá trị cũ/mới, user, thời gian)

---

## 7. Các quyết định đã chốt (26/07/2026)

1. **Phí tháng**: hệ thống sinh sẵn tự động đầu tháng theo phí lớp; GV/Admin linh động điều chỉnh tay khi tháng có vấn đề. (→ FR-6.2)
2. **HS tạm nghỉ giữa tháng**: điều chỉnh phí bằng tay, hệ thống không tự tính lại. (→ FR-6.2b)
3. **Năm học**: không cố định — Admin/GV đặt range ngày bắt đầu/kết thúc khi tạo lớp, hệ thống sinh buổi học trong range đó. (→ FR-2.1, FR-2.3)
4. **Phiếu thu**: không cần in. (→ FR-6.4)
