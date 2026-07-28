import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import { ROLE_LABELS, useAuth } from '../../auth.jsx';
import { STATUS } from '../../charts/theme.js';
import {
  ATTENDANCE_STATUS_LABELS,
  DOW_LABELS,
  fmtDate,
  fmtDateTime,
  fmtTime,
} from '../../format.js';
import './attendance.css';

const STATUS_ORDER = ['present', 'absent', 'late', 'excused'];

// Mau nen nut trang thai khi chon — lay dung STATUS cua charts/theme.js
// (1 nguon mau duy nhat), truyen xuong attendance.css qua CSS variables.
const STATUS_VARS = {
  '--st-present': STATUS.good,
  '--st-late': STATUS.warning,
  '--st-absent': STATUS.critical,
  '--st-excused': STATUS.neutral,
};
const LOCK_MS = 24 * 60 * 60 * 1000;

function toISODate(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function addDays(d, n) {
  const x = new Date(d);
  x.setDate(x.getDate() + n);
  return x;
}

// Thu 2 dau tuan (quy uoc VN)
function startOfWeek(d) {
  const x = new Date(d);
  x.setDate(x.getDate() - ((x.getDay() + 6) % 7));
  return x;
}

function thisWeekRange() {
  const mon = startOfWeek(new Date());
  return { from: toISODate(mon), to: toISODate(addDays(mon, 6)) };
}

// session_date -> 1=T2..7=CN (khop DOW_LABELS)
function dowOf(iso) {
  const js = new Date(`${iso}T00:00:00`).getDay();
  return js === 0 ? 7 : js;
}

// Qua 24h ke tu gio bat dau buoi hoc? (tinh theo gio may — chi de canh bao truoc,
// chot chan that la backend tra 403)
function isLocked(session) {
  if (!session) return false;
  const start = new Date(`${session.session_date}T${String(session.start_time).slice(0, 5)}:00`);
  return Date.now() - start.getTime() > LOCK_MS;
}

export default function AttendancePage() {
  const { user: me } = useAuth();
  const today = toISODate(new Date());

  // Buoc 1: lop
  const [classes, setClasses] = useState([]);
  const [classesLoading, setClassesLoading] = useState(false);
  const [classId, setClassId] = useState(null);

  // Buoc 2: buoi hoc trong khoang ngay (mac dinh tuan nay)
  const [range, setRange] = useState(thisWeekRange);
  const [sessions, setSessions] = useState([]);
  const [cancelledCount, setCancelledCount] = useState(0);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [sessionId, setSessionId] = useState(null);

  // Buoc 3: bang diem danh
  const [session, setSession] = useState(null);
  const [rows, setRows] = useState([]);
  const [rosterLoading, setRosterLoading] = useState(false);

  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const classesReqRef = useRef(0);
  const sessionsReqRef = useRef(0);
  const rosterReqRef = useRef(0);
  const noticeTimerRef = useRef();
  const savedRowsRef = useRef([]);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  // ---- Buoc 1: tai danh sach lop cua minh (admin thay tat ca) ----
  useEffect(() => {
    const reqId = ++classesReqRef.current;
    setClassesLoading(true);
    api('/classes?page_size=100')
      .then((data) => {
        if (reqId !== classesReqRef.current) return;
        setClasses(data.items);
        // GV chi day 1 lop -> chon luon cho do 1 cham tren dien thoai
        if (data.items.length === 1) setClassId(data.items[0].id);
      })
      .catch((err) => {
        if (reqId === classesReqRef.current) setError(err.message);
      })
      .finally(() => {
        if (reqId === classesReqRef.current) setClassesLoading(false);
      });
  }, []);

  // ---- Buoc 2: tai buoi hoc theo lop + khoang ngay (co race guard) ----
  const loadSessions = useCallback(async () => {
    if (!classId) return;
    const reqId = ++sessionsReqRef.current;
    setSessionsLoading(true);
    try {
      const params = new URLSearchParams({
        class_id: String(classId),
        from: range.from,
        to: range.to,
        page_size: '100',
      });
      const data = await api(`/sessions?${params}`);
      if (reqId !== sessionsReqRef.current) return; // response cu ve muon -> bo
      // An buoi da huy khoi danh sach chon diem danh
      const visible = data.items.filter((s) => s.status !== 'cancelled');
      visible.sort((a, b) =>
        `${a.session_date} ${a.start_time}`.localeCompare(`${b.session_date} ${b.start_time}`));
      setSessions(visible);
      setCancelledCount(data.items.length - visible.length);
    } catch (err) {
      if (reqId === sessionsReqRef.current) setError(err.message);
    } finally {
      if (reqId === sessionsReqRef.current) setSessionsLoading(false);
    }
  }, [classId, range]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  // ---- Buoc 3: tai roster diem danh cua buoi da chon (co race guard) ----
  const loadRoster = useCallback(async () => {
    if (!sessionId) return;
    const reqId = ++rosterReqRef.current;
    setRosterLoading(true);
    try {
      const data = await api(`/sessions/${sessionId}/attendance`);
      if (reqId !== rosterReqRef.current) return;
      setSession(data.session);
      const nextRows = data.items.map((it) => ({ ...it, note: it.note || '' }));
      savedRowsRef.current = nextRows.map(({ student_id, status, note }) => ({ student_id, status, note }));
      setRows(nextRows);
    } catch (err) {
      if (reqId === rosterReqRef.current) setError(err.message);
    } finally {
      if (reqId === rosterReqRef.current) setRosterLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    loadRoster();
  }, [loadRoster]);
  const hasUnsavedChanges = rows.some((row) => {
    const saved = savedRowsRef.current.find((item) => item.student_id === row.student_id);
    return !saved || saved.status !== row.status || saved.note !== row.note;
  });

  useEffect(() => {
    if (!hasUnsavedChanges) return undefined;
    const handleBeforeUnload = (event) => event.preventDefault();
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges]);

  function confirmDiscardChanges() {
    return !hasUnsavedChanges || window.confirm('Có thay đổi điểm danh chưa lưu. Bạn có muốn bỏ các thay đổi này?');
  }


  function pickClass(id) {
    if (!confirmDiscardChanges()) return;
    setClassId(id);
    setSessionId(null);
    setSession(null);
    setRows([]);
    setError('');
  }

  function pickSession(id) {
    if (id !== sessionId && !confirmDiscardChanges()) return;
    setSessionId(id);
    setError('');
  }

  function shiftWeek(n) {
    setRange((r) => {
      const from = addDays(new Date(`${r.from}T00:00:00`), n * 7);
      return { from: toISODate(from), to: toISODate(addDays(from, 6)) };
    });
  }

  function setStatus(studentId, status) {
    setRows((rs) =>
      rs.map((r) =>
        r.student_id === studentId
          ? { ...r, status: r.status === status ? null : status } // bam lai de bo chon
          : r));
  }

  function setNote(studentId, note) {
    setRows((rs) => rs.map((r) => (r.student_id === studentId ? { ...r, note } : r)));
  }

  function markAllPresent() {
    setRows((rs) => rs.map((r) => ({ ...r, status: 'present' })));
  }

  async function handleSave() {
    const items = rows
      .filter((r) => r.status)
      .map((r) => ({ student_id: r.student_id, status: r.status, note: r.note.trim() || null }));
    if (items.length === 0) {
      setError('Chưa chọn trạng thái điểm danh cho học sinh nào');
      return;
    }
    setError('');
    setSubmitting(true);
    try {
      const data = await api(`/sessions/${sessionId}/attendance`, {
        method: 'PUT',
        body: { items },
      });
      flash(`${data.message} (${data.saved} học sinh)`);
      loadRoster();
      loadSessions(); // cap nhat so HS da diem danh tren danh sach buoi
    } catch (err) {
      // Bao gom loi khoa 24h tu backend: "Đã quá 24h, chỉ Admin được sửa điểm danh"
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const selectedClass = classes.find((c) => c.id === classId);
  const locked = isLocked(session);
  const markedCount = rows.filter((r) => r.status).length;

  return (
    <div style={STATUS_VARS}>
      <div className="page-head">
        <h2>Điểm danh</h2>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      {/* ===== Buoc 1: chon lop ===== */}
      <div className="card stack">
        <h3>1. Chọn lớp</h3>
        {classesLoading && (
          <div className="att-pick-list" aria-hidden="true">
            <div className="skeleton att-skel" />
            <div className="skeleton att-skel" />
          </div>
        )}
        {!classesLoading && classes.length === 0 && (
          <div className="muted">Bạn chưa được gán vào lớp nào</div>
        )}
        {!classesLoading && classes.length > 0 && (
          <div className="att-pick-list">
            {classes.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`btn att-pick ${c.id === classId ? 'att-pick-active' : ''}`}
                onClick={() => pickClass(c.id)}
              >
                <span>
                  <span className="att-pick-title">{c.name}</span>{' '}
                  <span className="att-meta">Khối {c.grade} · {c.school_year}</span>
                </span>
                <span className="att-meta">{c.student_count} HS</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ===== Buoc 2: chon buoi ===== */}
      {classId && (
        <div className="card stack">
          <h3>2. Chọn buổi học — {selectedClass ? selectedClass.name : ''}</h3>
          <div className="filters att-filters">
            <button type="button" className="btn btn-sm" onClick={() => shiftWeek(-1)}>
              ← Tuần trước
            </button>
            <button type="button" className="btn btn-sm" onClick={() => setRange(thisWeekRange())}>
              Tuần này
            </button>
            <button type="button" className="btn btn-sm" onClick={() => shiftWeek(1)}>
              Tuần sau →
            </button>
            <label>
              Từ ngày
              <input
                type="date"
                value={range.from}
                onChange={(e) => e.target.value && setRange({ ...range, from: e.target.value })}
              />
            </label>
            <label>
              Đến ngày
              <input
                type="date"
                value={range.to}
                onChange={(e) => e.target.value && setRange({ ...range, to: e.target.value })}
              />
            </label>
          </div>

          {sessionsLoading && (
            <div className="att-pick-list" aria-hidden="true">
              <div className="skeleton att-skel" />
              <div className="skeleton att-skel" />
              <div className="skeleton att-skel" />
            </div>
          )}
          {!sessionsLoading && sessions.length === 0 && (
            <div className="muted">Không có buổi học nào trong khoảng này</div>
          )}
          {!sessionsLoading && sessions.length > 0 && (
            <div className="att-pick-list">
              {sessions.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className={`btn att-pick ${s.id === sessionId ? 'att-pick-active' : ''} ${
                    s.session_date === today ? 'att-pick-today' : ''
                  }`}
                  onClick={() => pickSession(s.id)}
                >
                  <span>
                    <span className="att-pick-title">
                      {DOW_LABELS[dowOf(s.session_date)]} {fmtDate(s.session_date)}
                    </span>{' '}
                    <span className="att-meta">
                      {fmtTime(s.start_time)}–{fmtTime(s.end_time)}
                    </span>
                  </span>
                  <span className="row-gap">
                    {s.session_date === today && <span className="badge badge-today">Hôm nay</span>}
                    {s.is_makeup && <span className="badge badge-off">Buổi bù</span>}
                    {s.attendance_count > 0 && (
                      <span className="badge badge-ok">Đã điểm danh {s.attendance_count}</span>
                    )}
                  </span>
                </button>
              ))}
            </div>
          )}
          {!sessionsLoading && cancelledCount > 0 && (
            <div className="muted">
              Đã ẩn {cancelledCount} buổi bị hủy (không điểm danh buổi đã hủy)
            </div>
          )}
        </div>
      )}

      {/* ===== Buoc 3: bang diem danh ===== */}
      {sessionId && (
        <div className="card stack">
          <h3>
            3. Điểm danh
            {session && (
              <>
                {' '}— {DOW_LABELS[dowOf(session.session_date)]} {fmtDate(session.session_date)}{' '}
                {fmtTime(session.start_time)}–{fmtTime(session.end_time)}
              </>
            )}
          </h3>

          {locked && me.role !== 'admin' && (
            <div className="alert alert-warn">
              Buổi học đã quá 24h kể từ giờ bắt đầu — chỉ {ROLE_LABELS.admin} được lưu/sửa điểm
              danh. Hệ thống sẽ từ chối khi bạn bấm lưu.
            </div>
          )}
          {locked && me.role === 'admin' && (
            <div className="alert alert-warn">
              Buổi học đã quá 24h — thao tác sửa điểm danh của {ROLE_LABELS.admin} sẽ được ghi
              nhật ký hệ thống.
            </div>
          )}

          {rosterLoading && (
            <div className="att-roster" aria-hidden="true">
              <div className="skeleton att-skel-row" />
              <div className="skeleton att-skel-row" />
              <div className="skeleton att-skel-row" />
            </div>
          )}
          {!rosterLoading && rows.length === 0 && (
            <div className="muted">Lớp chưa có học sinh nào đang học tại ngày buổi học này</div>
          )}

          {!rosterLoading && rows.length > 0 && (
            <>
              <div className="row-gap">
                <button
                  type="button"
                  className="btn btn-sm"
                  disabled={submitting}
                  onClick={markAllPresent}
                >
                  Có mặt tất cả
                </button>
                <span className="muted">Bấm lại một trạng thái để bỏ chọn</span>
              </div>

              <div className="att-roster">
                {rows.map((r) => (
                  <div key={r.student_id} className="att-row">
                    <div className="att-row-head">
                      <span className="att-name">{r.full_name}</span>
                      <span className="att-meta">
                        {r.recorded_by_name
                          ? `${r.recorded_by_name} · ${fmtDateTime(r.recorded_at)}`
                          : 'Chưa điểm danh'}
                      </span>
                    </div>
                    <div className="att-status-group">
                      {STATUS_ORDER.map((st) => (
                        <button
                          key={st}
                          type="button"
                          disabled={submitting}
                          aria-pressed={r.status === st}
                          className={`att-status ${r.status === st ? `on-${st}` : ''}`}
                          onClick={() => setStatus(r.student_id, st)}
                        >
                          {ATTENDANCE_STATUS_LABELS[st]}
                        </button>
                      ))}
                    </div>
                    <div className="att-note">
                      <input
                        value={r.note}
                        placeholder="Ghi chú…"
                        aria-label={`Ghi chú cho ${r.full_name}`}
                        disabled={submitting}
                        onChange={(e) => setNote(r.student_id, e.target.value)}
                      />
                    </div>
                  </div>
                ))}
              </div>

              <div className="att-save-bar">
                <span className="att-count">
                  Đã chọn <strong>{markedCount}/{rows.length}</strong> học sinh
                  {hasUnsavedChanges && <span className="badge badge-warn">Chưa lưu</span>}
                  {markedCount < rows.length ? ' — HS chưa chọn sẽ không được lưu' : ''}
                </span>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={submitting}
                  onClick={handleSave}
                >
                  {submitting ? 'Đang lưu…' : 'Lưu điểm danh'}
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
