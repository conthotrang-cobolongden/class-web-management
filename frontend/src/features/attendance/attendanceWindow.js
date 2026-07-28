const VN_TIME = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  hour: '2-digit',
  minute: '2-digit',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour12: false,
});

function formatInstant(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : VN_TIME.format(date);
}

export function attendanceWindow(roster) {
  if (roster?.can_edit !== false) {
    return { disabled: false, message: '' };
  }

  const reason = roster.edit_lock_reason || 'Ngoài thời gian được phép sửa điểm danh';
  const opensAt = formatInstant(roster.edit_window_opens_at);
  const closesAt = formatInstant(roster.edit_window_closes_at);
  if (opensAt && closesAt) {
    return {
      disabled: true,
      message: `${reason}. Chỉ được thao tác từ ${opensAt} đến ${closesAt}.`,
    };
  }
  if (closesAt) {
    return { disabled: true, message: `${reason}. Hạn chỉnh sửa: ${closesAt}.` };
  }
  return { disabled: true, message: reason };
}
