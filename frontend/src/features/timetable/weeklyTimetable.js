const DAY_LABELS = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];

function parseLocalDate(iso) {
  const [year, month, day] = String(iso).split('-').map(Number);
  return new Date(year, month - 1, day, 12, 0, 0, 0);
}

function toIsoDate(date) {
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function addLocalDays(iso, amount) {
  const date = parseLocalDate(iso);
  date.setDate(date.getDate() + amount);
  return toIsoDate(date);
}

export function startOfWeekLocal(value = new Date()) {
  const date = new Date(value.getFullYear(), value.getMonth(), value.getDate(), 12, 0, 0, 0);
  const mondayOffset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - mondayOffset);
  return toIsoDate(date);
}

export function currentWeekRange(value = new Date()) {
  const from = startOfWeekLocal(value);
  return { from, to: addLocalDays(from, 6) };
}

export function shiftWeekRange(range, amount) {
  const from = addLocalDays(range.from, amount * 7);
  return { from, to: addLocalDays(from, 6) };
}

export function weekDays(range, today = toIsoDate(new Date())) {
  return DAY_LABELS.map((label, index) => {
    const date = addLocalDays(range.from, index);
    return {
      date,
      label,
      shortLabel: index === 6 ? 'CN' : `T${index + 2}`,
      dayNumber: Number(date.slice(8, 10)),
      isToday: date === today,
    };
  });
}

export function groupWeeklySessions(sessions = []) {
  const byDay = new Map();
  for (const session of sessions) {
    const date = session.session_date;
    const time = String(session.start_time).slice(0, 5);
    if (!byDay.has(date)) byDay.set(date, new Map());
    const byTime = byDay.get(date);
    if (!byTime.has(time)) byTime.set(time, []);
    byTime.get(time).push(session);
  }

  return [...byDay.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, byTime]) => ({
      date,
      slots: [...byTime.entries()]
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([time, slotSessions]) => ({
          time,
          sessions: [...slotSessions].sort((left, right) =>
            String(left.class_name || '').localeCompare(String(right.class_name || ''), 'vi')
              || Number(left.id) - Number(right.id)),
        })),
    }));
}

export function timetableBadges(session) {
  const badges = [];
  if (session.status === 'cancelled') badges.push({ label: 'Đã hủy', tone: 'cancelled' });
  if (session.is_makeup) badges.push({ label: 'Buổi bù', tone: 'makeup' });
  return badges;
}
