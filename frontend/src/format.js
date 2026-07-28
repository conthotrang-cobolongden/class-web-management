// Tien ich format dung chung cho moi trang

export function fmtMoney(v) {
  if (v == null) return '—';
  return new Intl.NumberFormat('vi-VN').format(Number(v)) + ' ₫';
}

export function fmtDate(iso) {
  if (!iso) return '—';
  const [y, m, d] = String(iso).slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

export function fmtDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function fmtTime(t) {
  return t ? String(t).slice(0, 5) : '—';
}

// day_of_week theo schema: 1=Thu 2 ... 7=Chu nhat
export const DOW_LABELS = { 1: 'Thứ 2', 2: 'Thứ 3', 3: 'Thứ 4', 4: 'Thứ 5', 5: 'Thứ 6', 6: 'Thứ 7', 7: 'CN' };

export const SESSION_STATUS_LABELS = { scheduled: 'Bình thường', cancelled: 'Đã hủy' };
export const STUDENT_STATUS_LABELS = { active: 'Đang học', paused: 'Tạm nghỉ', left: 'Đã nghỉ' };
export const CLASS_STATUS_LABELS = { active: 'Đang hoạt động', closed: 'Đã đóng' };
export const FEE_STATUS_LABELS = { unpaid: 'Chưa đóng', partial: 'Đóng một phần', paid: 'Đã đóng' };
export const ATTENDANCE_STATUS_LABELS = { present: 'Có mặt', absent: 'Vắng', late: 'Muộn', excused: 'Có phép' };
export const PAYMENT_METHOD_LABELS = { cash: 'Tiền mặt', transfer: 'Chuyển khoản' };
