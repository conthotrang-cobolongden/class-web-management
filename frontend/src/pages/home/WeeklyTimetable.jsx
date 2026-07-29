import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import {
  currentWeekRange,
  groupWeeklySessions,
  shiftWeekRange,
  timetableBadges,
  weekDays,
} from '../../features/timetable/weeklyTimetable.js';
import './WeeklyTimetable.css';

function shortTime(value) {
  return String(value || '').slice(0, 5);
}

function shortDate(value) {
  const [year, month, day] = String(value).split('-');
  return `${day}/${month}/${year}`;
}

function SessionCard({ session, compact = false }) {
  const badges = timetableBadges(session);
  return (
    <Link
      className={`tt-event${session.status === 'cancelled' ? ' is-cancelled' : ''}`}
      to={`/classes/${session.class_id}`}
      aria-label={`${session.class_name}, ${shortTime(session.start_time)} đến ${shortTime(session.end_time)}`}
    >
      <span className="tt-event-time">
        {shortTime(session.start_time)}–{shortTime(session.end_time)}
      </span>
      <strong>{session.class_name}</strong>
      {!compact && badges.length > 0 && (
        <span className="tt-badges">
          {badges.map((badge) => (
            <span key={badge.tone} className={`tt-badge is-${badge.tone}`}>{badge.label}</span>
          ))}
        </span>
      )}
      {!compact && session.attendance_count > 0 && (
        <span className="tt-event-meta">Đã điểm danh {session.attendance_count} HS</span>
      )}
    </Link>
  );
}

export default function WeeklyTimetable() {
  const [range, setRange] = useState(currentWeekRange);
  const [activeDate, setActiveDate] = useState(() => currentWeekRange().from);
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const requestRef = useRef(0);

  const days = useMemo(() => weekDays(range), [range]);
  const grouped = useMemo(() => groupWeeklySessions(sessions), [sessions]);
  const dayGroups = useMemo(
    () => new Map(grouped.map((day) => [day.date, day.slots])),
    [grouped],
  );
  const times = useMemo(
    () => [...new Set(sessions.map((session) => shortTime(session.start_time)))].sort(),
    [sessions],
  );

  async function loadWeek() {
    const requestId = ++requestRef.current;
    setLoading(true);
    setError('');
    try {
      const data = await api(`/sessions/week?from=${range.from}&to=${range.to}`);
      if (requestId === requestRef.current) setSessions(Array.isArray(data) ? data : []);
    } catch (err) {
      if (requestId === requestRef.current) setError(err.message);
    } finally {
      if (requestId === requestRef.current) setLoading(false);
    }
  }

  useEffect(() => {
    loadWeek();
  }, [range.from, range.to]);

  useEffect(() => {
    const today = currentWeekRange();
    setActiveDate(range.from === today.from ? weekDays(range).find((day) => day.isToday)?.date || range.from : range.from);
  }, [range.from, range.to]);

  const activeSlots = dayGroups.get(activeDate) || [];

  return (
    <section className="card tt-shell" aria-labelledby="weekly-timetable-title">
      <div className="tt-heading">
        <div>
          <h3 id="weekly-timetable-title">Thời khóa biểu tuần này</h3>
          <p>Lịch học theo lớp · chọn một buổi để mở lớp</p>
        </div>
        <div className="tt-range" aria-live="polite">
          {shortDate(range.from)} – {shortDate(range.to)}
        </div>
      </div>

      <div className="tt-controls" aria-label="Điều hướng tuần">
        <button type="button" className="btn btn-sm" onClick={() => setRange((value) => shiftWeekRange(value, -1))}>
          ← Tuần trước
        </button>
        <button type="button" className="btn btn-sm" onClick={() => setRange(currentWeekRange())}>
          Tuần này
        </button>
        <button type="button" className="btn btn-sm" onClick={() => setRange((value) => shiftWeekRange(value, 1))}>
          Tuần sau →
        </button>
      </div>

      {error && (
        <div className="alert tt-alert">
          <span>{error}</span>
          <button type="button" className="btn btn-sm" onClick={loadWeek}>Thử lại</button>
        </div>
      )}

      {loading && (
        <div className="tt-loading" aria-label="Đang tải thời khóa biểu">
          <div className="skeleton" />
          <div className="skeleton" />
          <div className="skeleton" />
        </div>
      )}

      {!loading && !error && sessions.length === 0 && (
        <div className="tt-empty">
          <strong>Tuần này chưa có buổi học</strong>
          <span className="muted">Dùng nút điều hướng để xem tuần khác.</span>
        </div>
      )}

      {!loading && !error && sessions.length > 0 && (
        <>
          <div className="tt-desktop">
            <div className="tt-scroll">
              <div className="tt-grid">
                <div className="tt-corner">Giờ</div>
                {days.map((day) => (
                  <div key={day.date} className={`tt-day-head${day.isToday ? ' is-today' : ''}`}>
                    <strong>{day.label}</strong>
                    <span>{shortDate(day.date).slice(0, 5)}</span>
                  </div>
                ))}
                {times.map((time) => (
                  <div className="tt-row" key={time}>
                    <div className="tt-time">{time}</div>
                    {days.map((day) => {
                      const slot = (dayGroups.get(day.date) || []).find((value) => value.time === time);
                      return (
                        <div className={`tt-cell${day.isToday ? ' is-today' : ''}`} key={day.date}>
                          {slot?.sessions.map((session) => (
                            <SessionCard key={session.id} session={session} compact />
                          ))}
                        </div>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="tt-mobile">
            <div className="tt-day-pills" role="tablist" aria-label="Chọn ngày trong tuần">
              {days.map((day) => (
                <button
                  key={day.date}
                  type="button"
                  role="tab"
                  aria-selected={activeDate === day.date}
                  className={`${activeDate === day.date ? 'is-active' : ''}${day.isToday ? ' is-today' : ''}`}
                  onClick={() => setActiveDate(day.date)}
                >
                  <span>{day.shortLabel}</span>
                  <strong>{day.dayNumber}</strong>
                </button>
              ))}
            </div>
            <div className="tt-agenda" role="tabpanel">
              {activeSlots.length === 0 ? (
                <div className="tt-empty-day">Không có buổi học trong ngày này.</div>
              ) : (
                activeSlots.flatMap((slot) => slot.sessions).map((session) => (
                  <SessionCard key={session.id} session={session} />
                ))
              )}
            </div>
          </div>
        </>
      )}
    </section>
  );
}
