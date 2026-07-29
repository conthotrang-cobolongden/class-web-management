import { describe, expect, it } from 'vitest';
import {
  groupWeeklySessions,
  shiftWeekRange,
  startOfWeekLocal,
  timetableBadges,
  weekDays,
} from './weeklyTimetable.js';

describe('weekly timetable utilities', () => {
  it('calculates Monday and seven local calendar labels without UTC drift', () => {
    const monday = startOfWeekLocal(new Date(2026, 6, 29, 23, 30));
    const days = weekDays({ from: monday, to: '2026-08-02' });

    expect(monday).toBe('2026-07-27');
    expect(days).toHaveLength(7);
    expect(days.map((day) => day.date)).toEqual([
      '2026-07-27', '2026-07-28', '2026-07-29', '2026-07-30',
      '2026-07-31', '2026-08-01', '2026-08-02',
    ]);
  });

  it('moves the range by complete weeks', () => {
    expect(shiftWeekRange({ from: '2026-07-27', to: '2026-08-02' }, 1)).toEqual({
      from: '2026-08-03',
      to: '2026-08-09',
    });
  });

  it('groups by day and start time while stacking concurrent classes stably', () => {
    const grouped = groupWeeklySessions([
      session(3, '2026-07-28', '18:00', 'Lớp B'),
      session(1, '2026-07-27', '19:30', 'Lớp C'),
      session(2, '2026-07-28', '18:00', 'Lớp A'),
    ]);

    expect(grouped.map((day) => day.date)).toEqual(['2026-07-27', '2026-07-28']);
    expect(grouped[1].slots[0].time).toBe('18:00');
    expect(grouped[1].slots[0].sessions.map((value) => value.id)).toEqual([2, 3]);
  });

  it('describes cancelled and makeup sessions without inventing statuses', () => {
    expect(timetableBadges({ status: 'cancelled', is_makeup: true })).toEqual([
      { label: 'Đã hủy', tone: 'cancelled' },
      { label: 'Buổi bù', tone: 'makeup' },
    ]);
  });
});

function session(id, date, time, className) {
  return {
    id,
    class_id: id,
    class_name: className,
    session_date: date,
    start_time: time,
    end_time: '20:00',
    status: 'scheduled',
    is_makeup: false,
  };
}
